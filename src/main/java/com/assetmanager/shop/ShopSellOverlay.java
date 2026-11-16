package com.assetmanager.shop;

import com.assetmanager.AssetManagerConfig;
import com.assetmanager.keep.Holdings;
import com.assetmanager.keep.KeepRules;
import com.assetmanager.value.ItemValuator;
import com.assetmanager.value.ItemValues;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * In a shop, labels each item in the player's inventory with how many to sell on this world: as many as each still
 * pays more than the GE or alching, given what the shop already holds (including what other players have sold it).
 * When that's none but a fresh world would buy some, it says to hop.
 */
@Slf4j
@Singleton
public class ShopSellOverlay extends WidgetItemOverlay
{
	private static final Color SELL = new Color(0x4CAF50);
	private static final Color HOP = new Color(0xFF9800);
	private static final Pattern SELL_CLICK = Pattern.compile("^Sell (\\d+)$");
	/** How long a sale can take to show before it's taken as not having gone through: a few ticks. */
	private static final long PENDING_MILLIS = 2_400;
	/** What to click next, as the GE guide's highlights. */
	static final Color GUIDE = new Color(0x00E5FF);
	private static final Color TAG_BG = new Color(0, 0, 0, 215);

	/** What the counts depend on, from the last recalculation. */
	@Value
	public static class Context
	{
		ShopPerks perks;
		int minGainPerItem;
		int natureRuneCost;
		boolean canUseGe;
		KeepRules keepRules;
		Holdings holdings;
	}

	private final Client client;
	private final ItemManager itemManager;
	private final ItemValuator itemValuator;
	private final AssetManagerConfig config;

	@Setter
	private volatile Context context;
	/** The plan and the button selected, as of the client cycle they were worked out in. */
	private int nextCycle = -1;
	private int next;
	private int selectedCycle = -1;
	private int selected;
	/** The quantity selected, and its varbit, as last logged. */
	private int lastSelected = -2;
	private int lastSelectedVarbit = -2;
	/** Sales clicked that the game hasn't shown yet, by item; client thread only. */
	private final Map<Integer, Pending> pending = new HashMap<>();
	/** The open shop's item container, once found, and which shop it's for. */
	private int shopContainerId = -1;
	private String shopContainerShop;
	@Setter
	private Supplier<Shop> openShop = () -> null;

	@Inject
	ShopSellOverlay(Client client, ItemManager itemManager, ItemValuator itemValuator, AssetManagerConfig config)
	{
		this.config = config;
		this.client = client;
		this.itemManager = itemManager;
		this.itemValuator = itemValuator;
		showOnInterfaces(InterfaceID.Shopside.ITEMS >>> 16);
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		Context ctx = context;
		Shop shop = openShop.get();
		if (ctx == null || shop == null || widgetItem.getWidget().getId() != InterfaceID.Shopside.ITEMS)
		{
			return;
		}
		int id = itemManager.canonicalize(itemId);
		if (id == ItemID.COINS)
		{
			return;
		}
		Advice a = advice(ctx, shop, id);
		if (a == null)
		{
			return;
		}
		if (a.getSell() > 0)
		{
			Rectangle b = widgetItem.getCanvasBounds();
			tag(g, b, a.isAll() ? "Sell all" : "Sell " + QuantityFormatter.quantityToStackSize(a.getSell()), SELL);
			// The fewest clicks: everything's 50s first, then 10s, 5s and 1s. Outline what to click at the quantity selected
			int quantity = config.shopSellGuide() ? selectedQuantity() : -1;
			if (quantity > 0 && quantity == nextQuantity() && a.getSell() >= quantity)
			{
				g.setColor(GUIDE);
				g.setStroke(new BasicStroke(2));
				g.drawRect(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
				String clicks = "x" + a.getSell() / quantity;
				g.setFont(FontManager.getRunescapeSmallFont());
				FontMetrics fm = g.getFontMetrics();
				int x = b.x + b.width - fm.stringWidth(clicks);
				int y = b.y + b.height - fm.getAscent() - 3;
				g.setColor(Color.BLACK);
				g.drawString(clicks, x + 1, y + 1);
				g.setColor(GUIDE);
				g.drawString(clicks, x, y);
			}
		}
		else
		{
			tag(g, widgetItem.getCanvasBounds(), "Hop", HOP);
		}
	}

	/** The shop's quantity buttons, largest first, and their widgets. */
	static final int[] QUANTITIES = {50, 10, 5, 1};
	static final int[] QUANTITY_BUTTONS = {InterfaceID.Shopmain.QUANTITY50, InterfaceID.Shopmain.QUANTITY10,
		InterfaceID.Shopmain.QUANTITY5, InterfaceID.Shopmain.QUANTITY1};

	/**
	 * The quantity to sell at next for the fewest clicks: the largest button at or under some item's "Sell" count.
	 * Selling everything's 50s, then 10s, 5s and 1s never sells more than suggested. -1 if nothing's to sell.
	 */
	public int nextQuantity()
	{
		// Asked for every item, every frame: worked out once a frame
		int cycle = client.getGameCycle();
		if (cycle != nextCycle)
		{
			nextCycle = cycle;
			next = workOutNextQuantity();
		}
		return next;
	}

	private int workOutNextQuantity()
	{
		Context ctx = context;
		Shop shop = openShop.get();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (ctx == null || shop == null || inv == null)
		{
			return -1;
		}
		int most = 0;
		Set<Integer> done = new HashSet<>();
		for (Item i : inv.getItems())
		{
			int id = i.getId() <= 0 ? -1 : itemManager.canonicalize(i.getId());
			if (id > 0 && id != ItemID.COINS && done.add(id))
			{
				Advice a = advice(ctx, shop, id);
				if (a != null)
				{
					most = Math.max(most, a.getSell());
				}
			}
		}
		for (int q : QUANTITIES)
		{
			if (most >= q)
			{
				return q;
			}
		}
		return -1;
	}

	/**
	 * The quantity button selected: the one drawn differently from the other three. -1 if that can't be told.
	 */
	public int selectedQuantity()
	{
		int cycle = client.getGameCycle();
		if (cycle != selectedCycle)
		{
			selectedCycle = cycle;
			selected = workOutSelectedQuantity();
		}
		return selected;
	}

	private int workOutSelectedQuantity()
	{
		List<List<Integer>> looks = new ArrayList<>();
		for (int id : QUANTITY_BUTTONS)
		{
			Widget w = client.getWidget(id);
			if (w == null || w.isHidden())
			{
				return -1;
			}
			List<Integer> look = new ArrayList<>();
			sprites(w, look);
			looks.add(look);
		}
		int odd = -1;
		for (int n = 0; n < looks.size(); n++)
		{
			int same = 0;
			for (List<Integer> other : looks)
			{
				same += other.equals(looks.get(n)) ? 1 : 0;
			}
			if (same == 1)
			{
				if (odd >= 0)
				{
					return -1;
				}
				odd = n;
			}
		}
		int quantity = odd < 0 ? -1 : QUANTITIES[odd];
		int varbit = client.getVarbitValue(VarbitID.SHOP_QUANTITY);
		if (quantity != lastSelected || varbit != lastSelectedVarbit)
		{
			lastSelected = quantity;
			lastSelectedVarbit = varbit;
			log.debug("Shop quantity selected: {} (SHOP_QUANTITY varbit {})", quantity, varbit);
		}
		return quantity;
	}

	/** The pictures a button's drawn with, which is what changes when it's selected. */
	private static void sprites(Widget w, List<Integer> out)
	{
		if (w == null)
		{
			return;
		}
		out.add(w.getSpriteId());
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					sprites(c, out);
				}
			}
		}
	}

	/** How many of a stack to sell here: some, or none but a fresh world would buy some. */
	@Value
	static class Advice
	{
		/** How many to sell on this world; 0 means hop. */
		int sell;
		/** Selling them all: the whole spare stack. */
		boolean all;
	}

	/**
	 * How many of the item in the inventory to sell to the open shop on this world; null if none here or on a fresh
	 * world, as each sale wouldn't beat the GE or alching.
	 */
	private Advice advice(Context ctx, Shop shop, int id)
	{
		ItemValues v = itemValuator.get(id);
		ShopCatalog.Rate rate = ShopCatalog.rateFor(shop, v.getName().toLowerCase(), v.isTradeable(), ctx.getPerks());
		if (rate == null)
		{
			return null;
		}
		KeepRules rules = ctx.getKeepRules();
		int have = held(id, rules, ctx);
		if (have <= 0)
		{
			return null;
		}
		long alt = Math.max(ctx.isCanUseGe() ? v.getGeNetSell() : 0, v.getHighAlchNet(ctx.getNatureRuneCost()));
		long minUnit = alt + Math.max(1, ctx.getMinGainPerItem());
		int now = ShopCatalog.sellNow(v.getStorePrice(), rate, shopStock(id), minUnit, have);
		if (now > 0)
		{
			return new Advice(now, now >= have);
		}
		return ShopCatalog.sellNow(v.getStorePrice(), rate, rate.getNormalStock(), minUnit, have) > 0 ? new Advice(0, false) : null;
	}

	/**
	 * Stops a Sell click selling more of an item than its tag says: "Sell 5" when it says "Sell 3", or any sale when it
	 * says "Hop". The click is cancelled with a chat message; nothing is sent to the game. Items without a tag aren't
	 * stopped.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Context ctx = context;
		Shop shop = openShop.get();
		if (ctx == null || shop == null || !config.capShopSales() || event.getParam1() != InterfaceID.Shopside.ITEMS)
		{
			return;
		}
		Matcher m = SELL_CLICK.matcher(Text.removeTags(event.getMenuOption()));
		Widget w = m.matches() ? event.getWidget() : null;
		if (w == null || w.getItemId() <= 0)
		{
			return;
		}
		int id = itemManager.canonicalize(w.getItemId());
		Advice a = advice(ctx, shop, id);
		int selling = Math.min(Integer.parseInt(m.group(1)), Math.max(0, inventoryCount(id) - unseenInInventory(id)));
		Pending was = pending(id);
		log.debug("Shop sell click {}: selling {}, advice {}, inventory {}, stock {} ({}), pending {}", id, selling, a,
			inventoryCount(id), stock(id), shopContainer() != null ? "data" : "window", was == null ? 0 : was.clicked);
		if (a == null || a.isAll() || selling <= a.getSell())
		{
			// Allowed: counted until the game shows it, so a quick next click is judged on what's left
			if (selling > 0)
			{
				Pending p = pending(id);
				if (p == null)
				{
					p = new Pending(inventoryCount(id), stock(id));
					pending.put(id, p);
				}
				p.clicked += selling;
				p.at = System.currentTimeMillis();
			}
			return;
		}
		event.consume();
		String name = itemValuator.get(id).getName();
		chat(a.getSell() == 0
			? "Blocked: selling " + name + " here no longer beats the GE or alching. Hop to a fresh world."
			: "Blocked: sell " + a.getSell() + " more " + name + " here at most (Sell 1), then hop.");
	}

	/**
	 * Sales clicked but not yet shown by the game. The inventory and the shop's stock show a sale at different moments,
	 * so each is checked on its own: a sale counts as shown in the inventory once that many have gone from it, and in
	 * the shop once its stock has gone up by that many.
	 */
	private static class Pending
	{
		final int inventoryBase;
		final int stockBase;
		int clicked;
		long at;

		Pending(int inventoryBase, int stockBase)
		{
			this.inventoryBase = inventoryBase;
			this.stockBase = stockBase;
		}
	}

	/** The item's unshown sales; null if none, or the last click was long enough ago that it didn't go through. */
	private Pending pending(int id)
	{
		Pending p = pending.get(id);
		if (p != null && (System.currentTimeMillis() - p.at > PENDING_MILLIS
			|| unseen(p.clicked, p.inventoryBase - inventoryCount(id)) == 0 && unseenInShop(p, id) == 0))
		{
			pending.remove(id);
			return null;
		}
		return p;
	}

	private static int unseen(int clicked, int shown)
	{
		return Math.max(0, clicked - Math.max(0, shown));
	}

	/** Sold but still showing in the inventory. */
	private int unseenInInventory(int id)
	{
		Pending p = pending(id);
		return p == null ? 0 : unseen(p.clicked, p.inventoryBase - inventoryCount(id));
	}

	/** How many to count as in the inventory: spare ones (not kept), less sales the inventory doesn't show yet. */
	private int held(int id, KeepRules rules, Context ctx)
	{
		int count = inventoryCount(id);
		int spare = rules.has(id) ? Math.min(count, ctx.getHoldings().spare(Holdings.Place.INVENTORY, id, rules)) : count;
		return spare - unseenInInventory(id);
	}

	/** The shop's stock of the item, with sales it doesn't show yet. */
	private int shopStock(int id)
	{
		Pending p = pending(id);
		return stock(id) + (p == null ? 0 : unseenInShop(p, id));
	}

	/**
	 * Sold but not in the shop's stock yet. The shop's own item data arrives in the same tick as the inventory's, so
	 * once the inventory shows a sale the shop's stock has it too, whatever other players sell meanwhile. Only if that
	 * data hasn't been found is the shop's window watched, which redraws a little later.
	 */
	private int unseenInShop(Pending p, int id)
	{
		return shopContainer() != null ? unseen(p.clicked, p.inventoryBase - inventoryCount(id)) : unseen(p.clicked, stock(id) - p.stockBase);
	}

	/** The open shop's own item data; null if it hasn't been found. */
	private ItemContainer shopContainer()
	{
		Shop shop = openShop.get();
		if (shop == null || shopContainerId < 0 || !shop.getName().equals(shopContainerShop))
		{
			return null;
		}
		return client.getItemContainer(shopContainerId);
	}

	/**
	 * Finds the open shop's item data: a container that changes while the shop is open, other than the player's own,
	 * holding what the shop's window shows.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		Shop shop = openShop.get();
		int cid = event.getContainerId();
		if (shop == null || cid == InventoryID.INV || cid == InventoryID.WORN || cid == InventoryID.BANK)
		{
			return;
		}
		Widget items = client.getWidget(InterfaceID.Shopmain.ITEMS);
		if (items == null || items.isHidden() || items.getDynamicChildren() == null)
		{
			return;
		}
		ItemContainer c = event.getItemContainer();
		for (Widget w : items.getDynamicChildren())
		{
			// Shops list items they're out of, which the data may leave out; match on one in stock
			if (w.getItemId() > 0 && !w.isHidden() && w.getItemQuantity() > 0)
			{
				if (c.count(w.getItemId()) > 0)
				{
					shopContainerId = cid;
					shopContainerShop = shop.getName();
				}
				return;
			}
		}
	}

	/** How many of an item, noted or not, are in the inventory. */
	private int inventoryCount(int canonicalId)
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		int n = 0;
		if (inv != null)
		{
			for (Item i : inv.getItems())
			{
				if (i.getId() > 0 && itemManager.canonicalize(i.getId()) == canonicalId)
				{
					n += i.getQuantity();
				}
			}
		}
		return n;
	}

	private void chat(String text)
	{
		String message = new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("Asset Manager: ").append(ChatColorType.NORMAL)
			.append(text).build();
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}

	/**
	 * A solid tag across the bottom of the item, so the count reads over any item's picture. Every tag is the same
	 * size, the slot's width, in the small font, with the text centred.
	 */
	public static void tag(Graphics2D g, Rectangle b, String text, Color color)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int h = fm.getAscent() + 3;
		int x = b.x;
		int y = b.y + b.height - h + 2;
		g.setColor(TAG_BG);
		g.fillRect(x, y, b.width, h);
		g.setColor(color);
		g.drawRect(x, y, b.width - 1, h - 1);
		g.drawString(text, x + (b.width - fm.stringWidth(text)) / 2, y + fm.getAscent() + 1);
	}

	/** How many of an item the open shop holds: from its item data, or failing that its window. */
	private int stock(int canonicalId)
	{
		ItemContainer shopItems = shopContainer();
		if (shopItems != null)
		{
			int n = 0;
			for (Item i : shopItems.getItems())
			{
				if (i.getId() > 0 && itemManager.canonicalize(i.getId()) == canonicalId)
				{
					n += i.getQuantity();
				}
			}
			return n;
		}
		Widget items = client.getWidget(InterfaceID.Shopmain.ITEMS);
		if (items == null || items.getDynamicChildren() == null)
		{
			return 0;
		}
		for (Widget w : items.getDynamicChildren())
		{
			if (w.getItemId() > 0 && !w.isHidden() && itemManager.canonicalize(w.getItemId()) == canonicalId)
			{
				return w.getItemQuantity();
			}
		}
		return 0;
	}
}
