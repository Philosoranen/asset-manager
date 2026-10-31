package com.assetmanager.shop;

import com.assetmanager.keep.Holdings;
import com.assetmanager.keep.KeepRules;
import com.assetmanager.value.ItemValuator;
import com.assetmanager.value.ItemValues;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * In a shop, labels each item in the player's inventory with how many to sell on this world: as many as each still
 * pays more than the GE or alching, given what the shop already holds (including what other players have sold it).
 * When that's none but a fresh world would buy some, it says to hop.
 */
@Singleton
public class ShopSellOverlay extends WidgetItemOverlay
{
	private static final Color SELL = new Color(0x4CAF50);
	private static final Color HOP = new Color(0xFF9800);

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

	@Setter
	private volatile Context context;
	@Setter
	private Supplier<Shop> openShop = () -> null;

	@Inject
	ShopSellOverlay(Client client, ItemManager itemManager, ItemValuator itemValuator)
	{
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
		ItemValues v = itemValuator.get(id);
		ShopCatalog.Rate rate = ShopCatalog.rateFor(shop, v.getName().toLowerCase(), v.isTradeable(), ctx.getPerks());
		if (rate == null)
		{
			return;
		}
		KeepRules rules = ctx.getKeepRules();
		int have = Math.min(widgetItem.getQuantity(), rules.has(id) ? ctx.getHoldings().spare(Holdings.Place.INVENTORY, id, rules)
			: widgetItem.getQuantity());
		if (have <= 0)
		{
			return;
		}
		long alt = Math.max(ctx.isCanUseGe() ? v.getGeNetSell() : 0, v.getHighAlchNet(ctx.getNatureRuneCost()));
		long minUnit = alt + Math.max(1, ctx.getMinGainPerItem());
		int now = ShopCatalog.sellNow(v.getStorePrice(), rate, stock(id), minUnit, have);
		String text;
		Color color;
		if (now > 0)
		{
			text = now >= have ? "sell all" : "sell " + now;
			color = SELL;
		}
		else if (ShopCatalog.sellNow(v.getStorePrice(), rate, rate.getNormalStock(), minUnit, have) > 0)
		{
			text = "hop";
			color = HOP;
		}
		else
		{
			return;
		}
		Rectangle b = widgetItem.getCanvasBounds();
		g.setFont(FontManager.getRunescapeSmallFont());
		int y = b.y + b.height;
		g.setColor(Color.BLACK);
		g.drawString(text, b.x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, b.x, y);
	}

	/** How many of an item the open shop holds. */
	private int stock(int canonicalId)
	{
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
