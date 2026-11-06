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
 * Grand Exchange for a profit, and shows how many to buy when one is hovered.
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

	/** The open shop, if it's in the shop data. */
	@Getter
	private volatile Shop openShop;
	@Getter
	private volatile int openShopGlovesTier;

	/** Profitable flips in the open shop, by item id. */
	@Getter
	private volatile Map<Integer, ShopFlip> flips = Collections.emptyMap();
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
		if (!config.shopFlips() || shop == null || items.getDynamicChildren() == null)
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

		Map<Integer, ShopFlip> found = new HashMap<>();
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

			// With live prices, don't buy more than the GE trades in an hour
			LivePrices.Volume hour = livePrices.volume(itemManager.canonicalize(itemId));
			long geVolume = hour == null ? Long.MAX_VALUE : (long) hour.getHigh() + hour.getLow();
			// Some shops charge a set price rather than their usual % of value
			int value = entry.getSp() != null && !gloves && sellRate > 0
				? (int) ((entry.getSp() * 1000L + sellRate - 1) / sellRate) : v.getStorePrice();
			ShopFlip flip = ShopFlipAdvisor.plan(value, w.getItemQuantity(), entry.getS(), sellRate, delta,
				v.getGeNetSell(), 1, space, budget, geVolume);
			if (flip != null && flip.getProfit() >= config.minFlipProfit())
			{
				found.put(itemId, flip);
			}
		}
		flips = found;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		Map<Integer, ShopFlip> current = flips;
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
		ShopFlip flip = current.get(w.getItemId());
		if (flip != null)
		{
			tooltipManager.add(new Tooltip(tooltip(w.getItemId(), flip)));
		}
	}

	private PanelComponent tooltip(int itemId, ShopFlip f)
	{
		PanelComponent p = new PanelComponent();
		p.setBackgroundColor(PriceTooltip.SOLID_BACKGROUND);
		p.setPreferredSize(new Dimension(180, 0));
		p.getChildren().add(TitleComponent.builder().text("Flip on the GE").color(GOOD).build());
		p.getChildren().add(line("Buy", QuantityFormatter.formatNumber(f.getQuantity()), NORMAL));
		String each = f.getFirstPrice() == f.getLastPrice()
			? QuantityFormatter.formatNumber(f.getFirstPrice())
			: QuantityFormatter.formatNumber(f.getFirstPrice()) + " – " + QuantityFormatter.formatNumber(f.getLastPrice());
		p.getChildren().add(line("Shop price each", each, NORMAL));
		p.getChildren().add(line("GE sells for each", QuantityFormatter.formatNumber(f.getGeUnit()), NORMAL));
		p.getChildren().add(line("Profit", QuantityFormatter.formatNumber(f.getProfit()), GOOD));
		p.getChildren().add(LineComponent.builder().left("Limited by " + f.getLimit().getDescription()).leftColor(MUTED).build());

		LivePrices.Volume vol = livePrices.volume(itemManager.canonicalize(itemId));
		if (vol != null)
		{
			p.getChildren().add(LineComponent.builder()
				.left("Traded on the GE last hour: " + QuantityFormatter.formatNumber((long) vol.getHigh() + vol.getLow()))
				.leftColor(MUTED)
				.build());
		}
		p.getChildren().add(LineComponent.builder()
			.left("GE: " + itemValuator.getGePriceDescription() + ", after tax")
			.leftColor(MUTED)
			.build());
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
