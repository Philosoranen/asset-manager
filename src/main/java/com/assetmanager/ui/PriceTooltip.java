package com.assetmanager.ui;

import com.assetmanager.AccountMode;
import com.assetmanager.AssetManagerConfig;
import com.assetmanager.keep.Holdings;
import com.assetmanager.keep.KeepRules;
import com.assetmanager.shop.SellRules;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.shop.ShopOffer;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.value.ItemValuator;
import com.assetmanager.value.ItemValues;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * When hovering an item in the bank or inventory, shows what it's worth each way side by side:
 * GE (after tax), high alch (after the nature rune), Death's coffer and the best shop. The best way to
 * cash it out is highlighted, and the coffer is highlighted if it beats that.
 */
@Singleton
public class PriceTooltip
{
	private static final Color BEST = new Color(0x4CAF50);
	private static final Color NORMAL = new Color(0xEEEEEE);
	private static final Color MUTED = new Color(0x9E9E9E);
	private static final int WIDTH = 190;
	/** RuneLite's usual overlay color, but solid so tooltips are readable over busy interfaces. */
	public static final Color SOLID_BACKGROUND = ColorUtil.colorWithAlpha(ComponentConstants.STANDARD_BACKGROUND_COLOR, 255);

	/** What the tooltip needs from the plugin's latest calculation. */
	@Value
	public static class Context
	{
		AccountMode mode;
		int natureRuneCost;
		ShopPerks perks;
		SellRules sellRules;
		/** Shops each item isn't going to, because the trip wasn't worth it for that stack. */
		Map<Integer, Set<String>> skippedShops;
		/** Items planned to go to a shop. */
		Set<Integer> toShop;
		ShopCatalog shops;
		KeepRules keepRules;
		Holdings holdings;
	}

	private final Client client;
	private final TooltipManager tooltipManager;
	private final ItemValuator itemValuator;
	private final AssetManagerConfig config;
	private final ItemManager itemManager;

	private volatile Context context;
	/** Built tooltips by item id and quantity; cleared whenever the context changes. */
	private final Map<Long, PanelComponent> cache = new HashMap<>();

	@Inject
	PriceTooltip(Client client, TooltipManager tooltipManager, ItemValuator itemValuator, AssetManagerConfig config, ItemManager itemManager)
	{
		this.itemManager = itemManager;
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.itemValuator = itemValuator;
		this.config = config;
	}

	public void setContext(Context context)
	{
		this.context = context;
		cache.clear();
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		Context ctx = context;
		if (ctx == null || !config.priceTooltip() || client.isMenuOpen())
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length == 0)
		{
			return;
		}
		MenuEntry entry = entries[entries.length - 1];
		int component = entry.getParam1();
		if (component != InterfaceID.Bankmain.ITEMS && component != InterfaceID.Bankside.ITEMS
			&& component != InterfaceID.Inventory.ITEMS && component >>> 16 != InterfaceID.SEED_VAULT)
		{
			return;
		}
		Widget w = entry.getWidget();
		if (w == null || w.getItemId() <= 0 || w.getItemId() == ItemID.COINS)
		{
			return;
		}
		// Placeholders have a quantity of 0; show single-item values for them
		int stack = Math.max(1, w.getItemQuantity());
		int qty = stack;
		int id = itemManager.canonicalize(w.getItemId());
		if (ctx.getKeepRules().has(id))
		{
			// Only the spare ones; a stack that's all being kept gets no tooltip
			int spare = ctx.getHoldings().spare(SuggestionOverlay.place(component), id, ctx.getKeepRules());
			if (spare == 0 && w.getItemQuantity() > 0)
			{
				return;
			}
			qty = Math.max(1, Math.min(stack, spare));
		}
		int shown = qty;
		long key = ((long) w.getItemId() << 32) | ((long) shown << 1) | (shown < stack ? 1 : 0);
		PanelComponent panel = cache.computeIfAbsent(key, k -> build(ctx, w.getItemId(), shown, shown < stack));
		if (panel != null)
		{
			tooltipManager.add(new Tooltip(panel));
		}
	}

	/** @param spareOnly whether {@code qty} is only the spare part of the stack */
	private PanelComponent build(Context ctx, int itemId, int qty, boolean spareOnly)
	{
		ItemValues v = itemValuator.get(itemId);
		boolean ge = ctx.getMode().canUseGrandExchange() && v.isTradeable();
		long geUnit = ge ? v.getGeNetSell() : 0;
		long haUnit = v.getHighAlchNet(ctx.getNatureRuneCost());
		long dcUnit = ctx.getMode().canUseCoffer() ? v.getCofferValue() : 0;
		long altUnit = Math.max(geUnit, haUnit);
		ShopOffer shop = ctx.getShops() == null ? null
			: ctx.getShops().bestOffer(v.getName(), v.getStorePrice(), qty, v.isTradeable(), ctx.getPerks(), altUnit,
				ctx.getSellRules(), ctx.getSkippedShops().getOrDefault(v.getItemId(), Collections.emptySet()));
		// Only counts when this stack is on a shop trip worth making
		boolean shopWorthIt = shop != null && shop.getQuantity() > 0 && ctx.getToShop().contains(v.getItemId());
		long shopTotal = shopWorthIt ? shop.getTotal() : 0;

		long bestCash = Math.max(Math.max(geUnit * qty, haUnit * qty), shopTotal);

		PanelComponent panel = new PanelComponent();
		panel.setBackgroundColor(SOLID_BACKGROUND);
		panel.setPreferredSize(new Dimension(WIDTH, 0));
		panel.getChildren().add(TitleComponent.builder()
			.text((spareOnly ? QuantityFormatter.formatNumber(qty) + " spare " : qty > 1 ? QuantityFormatter.formatNumber(qty) + " x " : "")
				+ v.getName())
			.color(NORMAL)
			.build());
		if (qty > 1)
		{
			panel.getChildren().add(LineComponent.builder().left("Worth for the whole stack:").leftColor(MUTED).build());
		}

		List<LineComponent> rows = new ArrayList<>();
		rows.add(ge
			? priceLine("GE", geUnit, geUnit * qty, qty, geUnit * qty == bestCash && bestCash > 0)
			: line("GE", ctx.getMode().canUseGrandExchange() ? "untradeable" : "ironman", MUTED, MUTED));
		rows.add(priceLine("High alch", haUnit, haUnit * qty, qty, haUnit * qty == bestCash && bestCash > 0));
		if (ctx.getMode().canUseCoffer())
		{
			rows.add(dcUnit > 0
				? priceLine("Coffer", dcUnit, dcUnit * qty, qty, dcUnit * qty > bestCash)
				: line("Coffer", v.isTradeable() ? "under 10k" : "untradeable", MUTED, MUTED));
		}
		if (shopWorthIt)
		{
			rows.add(priceLine("Shop", shop.getFirstUnit(), shopTotal, qty, shopTotal == bestCash));
		}
		else if (shop != null && shop.getFirstUnit() > 0)
		{
			rows.add(line("Shop", QuantityFormatter.formatNumber(shop.getFirstUnit()) + " each", MUTED, MUTED));
		}
		else
		{
			rows.add(line("Shop", "none buy it", MUTED, MUTED));
		}
		panel.getChildren().addAll(rows);
		if (ge && v.getWikiPrice() > 0)
		{
			panel.getChildren().add(LineComponent.builder()
				.left("GE: " + QuantityFormatter.formatNumber(v.getWikiPrice()) + " each before tax, " + itemValuator.getGePriceDescription())
				.leftColor(MUTED)
				.build());
		}

		if (shop != null && (shopWorthIt || shop.getFirstUnit() > 0))
		{
			String where = shop.getShop().getName() + (shop.isUsesKaramjaGloves() ? " (gloves)" : "")
				+ (shop.isUsesCharos() ? " (Ring of Charos)" : "")
				+ (shop.isFixedPrice() ? " (fixed price)" : "")
				+ (shop.getEqualShops() > 0 ? " (closest of " + (shop.getEqualShops() + 1) + " paying the same)" : "");
			if (shopWorthIt && shop.getWorlds() > 1)
			{
				where += ", " + shop.getPerWorld() + " per world on " + shop.getWorlds() + " worlds";
			}
			if (!shopWorthIt)
			{
				where += shop.getQuantity() > 0 ? ": not worth the trip or the hops" : " doesn't beat " + (geUnit >= haUnit ? "the GE" : "alching");
			}
			panel.getChildren().add(LineComponent.builder().left(where).leftColor(MUTED).build());
		}
		int keep = ctx.getKeepRules().get(v.getItemId());
		if (keep != 0)
		{
			panel.getChildren().add(LineComponent.builder()
				.left(keep == KeepRules.KEEP_ALL ? "Keeping all" : "Keeping " + QuantityFormatter.formatNumber(keep))
				.leftColor(MUTED)
				.build());
		}
		return panel;
	}

	private static LineComponent priceLine(String label, long unit, long total, int qty, boolean best)
	{
		Color c = best ? BEST : NORMAL;
		return line(label, QuantityFormatter.formatNumber(qty > 1 ? total : unit), c, c);
	}

	private static LineComponent line(String label, String value, Color labelColor, Color valueColor)
	{
		return LineComponent.builder().left(label).leftColor(labelColor).right(value).rightColor(valueColor).build();
	}
}
