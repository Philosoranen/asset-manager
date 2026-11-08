package com.assetmanager.flip;

import com.assetmanager.AssetManagerConfig;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.ui.PriceTooltip;
import com.assetmanager.value.ItemValuator;
import com.assetmanager.value.ItemValues;
import com.assetmanager.value.LivePrices;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * While a shop is open, works out which shop it is, finds the items it sells that could be resold on the
 * Grand Exchange or put in Death's coffer for a profit, and shows how many to buy when one is hovered.
 */
@Slf4j
@Singleton
public class ShopFlipper
{
	private static final Color GOOD = new Color(0x4CAF50);
	private static final Color NORMAL = new Color(0xEEEEEE);
	private static final Color MUTED = new Color(0x9E9E9E);
	private static final int INVENTORY_SIZE = 28;

	private final Client client;
	private final ItemManager itemManager;
	private final ItemValuator itemValuator;
	private final LivePrices livePrices;
	private final TooltipManager tooltipManager;
	private final AssetManagerConfig config;

	@Setter
	private ShopCatalog shops;
	@Setter
	private int glovesTier;
	/** Whether the account can sell on the GE. */
	@Setter
	private volatile boolean canUseGe = true;
	/**
	 * Coins Death's coffer should take: how far it is below the target, Long.MAX_VALUE for no limit, or 0 when it's full
	 * or the account can't use it.
	 */
	@Setter
	private volatile long cofferRoom;

	/** The open shop, if it's in the shop data. */
	@Getter
	private volatile Shop openShop;
	@Getter
	private volatile int openShopGlovesTier;

	/** What's worth buying in the open shop, and where to take it, by item id. */
	@Getter
	private volatile Map<Integer, Buy> flips = Collections.emptyMap();

	/** A shop item worth buying: to sell on the GE, to put in Death's coffer, or both. */
	@Value
	public static class Buy
	{
		/** Selling on the GE, or null if it isn't worth it. */
		ShopFlip ge;
		/** Putting in Death's coffer, or null if it isn't worth it. */
		ShopFlip coffer;

		/** Whichever makes more. */
		public ShopFlip best()
		{
			return isCofferBest() ? coffer : ge;
		}

		public boolean isCofferBest()
		{
			return ge == null || coffer != null && coffer.getProfit() > ge.getProfit();
		}
	}
	private String lastTitle;

	@Inject
	ShopFlipper(Client client, ItemManager itemManager, ItemValuator itemValuator, LivePrices livePrices,
		TooltipManager tooltipManager, AssetManagerConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.itemValuator = itemValuator;
		this.livePrices = livePrices;
		this.tooltipManager = tooltipManager;
		this.config = config;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Widget items = client.getWidget(InterfaceID.Shopmain.ITEMS);
		if (shops == null || items == null || items.isHidden())
		{
			flips = Collections.emptyMap();
			openShop = null;
			lastTitle = null;
			return;
		}

		String title = shopTitle();
		Shop shop = shops.findByName(title);
		openShop = shop;
		openShopGlovesTier = glovesTier;
		if (!title.equals(lastTitle))
		{
			lastTitle = title;
			log.debug("Shop opened: \"{}\" ({})", title, shop == null ? "not in shop data" : "matched " + shop.getName());
		}
		boolean geFlips = config.shopFlips() && canUseGe;
		long room = config.shopCofferBuys() ? cofferRoom : 0;
		if (!geFlips && room <= 0 || shop == null || items.getDynamicChildren() == null)
		{
			flips = Collections.emptyMap();
			return;
		}

		int freeSlots = INVENTORY_SIZE;
		long coins = 0;
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (Item i : inv.getItems())
			{
				if (i.getId() > 0)
				{
					freeSlots--;
					if (i.getId() == ItemID.COINS)
					{
						coins += i.getQuantity();
					}
				}
			}
		}
		// With no coins on hand the player will fetch some, so don't limit by them
		long budget = coins > 0 ? coins : Long.MAX_VALUE;

		Map<Integer, Buy> found = new HashMap<>();
		for (Widget w : items.getDynamicChildren())
		{
			int itemId = w.getItemId();
			if (itemId <= 0 || w.isHidden())
			{
				continue;
			}
			ItemValues v = itemValuator.get(itemId);
			Shop.Item entry = shop.getItems().get(v.getName().toLowerCase());
			if (!v.isTradeable() || entry == null || entry.isNs())
			{
				continue;
			}
			boolean gloves = shop.getGlovesTier() != null && glovesTier >= shop.getGlovesTier() && entry.getGs() != null;
			int sellRate = gloves ? entry.getGs() : entry.getSm() != null ? entry.getSm() : shop.getSellMultiplier();
			int delta = entry.getD() != null ? entry.getD() : shop.getDelta();

			boolean stackable = itemManager.getItemComposition(itemId).isStackable();
			int space = stackable ? (freeSlots > 0 || (inv != null && inv.count(itemId) > 0) ? Integer.MAX_VALUE : 0) : freeSlots;

			// Some shops charge a set price rather than their usual % of value
			int value = entry.getSp() != null && !gloves && sellRate > 0
				? (int) ((entry.getSp() * 1000L + sellRate - 1) / sellRate) : v.getStorePrice();
			ShopFlip ge = null;
			if (geFlips)
			{
				// With live prices, don't buy more than the GE trades in an hour
				long perHour = livePrices.tradesPerHour(itemManager.canonicalize(itemId));
				long geVolume = perHour < 0 ? Long.MAX_VALUE : perHour;
				ge = worthIt(ShopFlipAdvisor.plan(value, w.getItemQuantity(), entry.getS(), sellRate, delta,
					v.getGeNetSell(), 1, space, budget, geVolume));
			}
			ShopFlip coffer = null;
			long unit = v.getCofferValue();
			if (room > 0 && unit > 0)
			{
				// Enough to fill the coffer, the last one going a little over
				long need = room == Long.MAX_VALUE ? Long.MAX_VALUE : (room + unit - 1) / unit;
				coffer = worthIt(ShopFlipAdvisor.plan(value, w.getItemQuantity(), entry.getS(), sellRate, delta,
					unit, 1, space, budget, need, ShopFlip.Limit.COFFER));
			}
			if (ge != null || coffer != null)
			{
				found.put(itemId, new Buy(ge, coffer));
			}
		}
		flips = found;
	}

	/** The flip, if it makes at least the player's minimum. */
	private ShopFlip worthIt(ShopFlip flip)
	{
		return flip != null && flip.getProfit() >= config.minFlipProfit() ? flip : null;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		Map<Integer, Buy> current = flips;
		if (current.isEmpty() || client.isMenuOpen())
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length == 0)
		{
			return;
		}
		MenuEntry entry = entries[entries.length - 1];
		Widget w = entry.getWidget();
		if (entry.getParam1() != InterfaceID.Shopmain.ITEMS || w == null)
		{
			return;
		}
		Buy buy = current.get(w.getItemId());
		if (buy != null)
		{
			tooltipManager.add(new Tooltip(tooltip(w.getItemId(), buy)));
		}
	}

	private PanelComponent tooltip(int itemId, Buy buy)
	{
		PanelComponent p = new PanelComponent();
		p.setBackgroundColor(PriceTooltip.SOLID_BACKGROUND);
		p.setPreferredSize(new Dimension(180, 0));
		// The better one first
		ShopFlip first = buy.best();
		for (ShopFlip f : new ShopFlip[]{first, first == buy.getGe() ? buy.getCoffer() : buy.getGe()})
		{
			if (f == null)
			{
				continue;
			}
			boolean coffer = f == buy.getCoffer();
			if (p.getChildren().size() > 0)
			{
				p.getChildren().add(LineComponent.builder().left(" ").build());
			}
			p.getChildren().add(TitleComponent.builder().text(coffer ? "Put in Death's coffer" : "Flip on the GE").color(GOOD).build());
			p.getChildren().add(line("Buy", QuantityFormatter.formatNumber(f.getQuantity()), NORMAL));
			String each = f.getFirstPrice() == f.getLastPrice()
				? QuantityFormatter.formatNumber(f.getFirstPrice())
				: QuantityFormatter.formatNumber(f.getFirstPrice()) + " – " + QuantityFormatter.formatNumber(f.getLastPrice());
			p.getChildren().add(line("Shop price each", each, NORMAL));
			p.getChildren().add(line(coffer ? "Coffer adds each" : "GE sells for each", QuantityFormatter.formatNumber(f.getGeUnit()),
				NORMAL));
			p.getChildren().add(line("Profit", QuantityFormatter.formatNumber(f.getProfit()), GOOD));
			p.getChildren().add(LineComponent.builder().left("Limited by " + f.getLimit().getDescription()).leftColor(MUTED).build());
			if (!coffer)
			{
				long perHour = livePrices.tradesPerHour(itemManager.canonicalize(itemId));
				if (perHour >= 0)
				{
					p.getChildren().add(LineComponent.builder()
						.left("Trades on the GE: about " + QuantityFormatter.formatNumber(perHour) + " an hour")
						.leftColor(MUTED)
						.build());
				}
				p.getChildren().add(LineComponent.builder()
					.left("GE: " + itemValuator.getGePriceDescription() + ", after tax")
					.leftColor(MUTED)
					.build());
			}
		}
		return p;
	}

	private static LineComponent line(String left, String right, Color rightColor)
	{
		return LineComponent.builder().left(left).leftColor(NORMAL).right(right).rightColor(rightColor).build();
	}

	/** The shop's name from its window title. */
	private String shopTitle()
	{
		Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
		String t = firstText(frame);
		return t == null ? "" : t;
	}

	private static String firstText(Widget w)
	{
		if (w == null)
		{
			return null;
		}
		if (w.getText() != null && !Text.removeTags(w.getText()).trim().isEmpty())
		{
			return Text.removeTags(w.getText()).trim();
		}
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					String t = firstText(c);
					if (t != null)
					{
						return t;
					}
				}
			}
		}
		return null;
	}
}
