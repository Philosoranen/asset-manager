package com.assetmanager.flip;

import com.assetmanager.AssetManagerConfig;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopStock;
import com.assetmanager.shop.ShopStockTracker;
import com.assetmanager.shop.ShopTimes;
import com.assetmanager.ui.PriceTooltip;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows the open shop's rates under its window: what it sells for, what it buys for and how much the price
 * changes per item of stock, as a percentage of each item's value. Also which items are overstocked on this world
 * and how long until they're back to normal.
 */
@Singleton
public class ShopRatesOverlay extends Overlay
{
	private static final Color TITLE = new Color(0xFF9800);
	private static final Color NORMAL = new Color(0xEEEEEE);
	private static final Color MUTED = new Color(0x9E9E9E);
	private static final int WIDTH = 170;

	private final Client client;
	private final ShopFlipper flipper;
	private final AssetManagerConfig config;
	private final ShopStockTracker stockTracker;
	private final PanelComponent panel = new PanelComponent();
	private int lastHeight;

	@Inject
	ShopRatesOverlay(Client client, ShopFlipper flipper, AssetManagerConfig config, ShopStockTracker stockTracker)
	{
		this.stockTracker = stockTracker;
		this.client = client;
		this.flipper = flipper;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		panel.setPreferredSize(new Dimension(WIDTH, 0));
		panel.setBackgroundColor(PriceTooltip.SOLID_BACKGROUND);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Shop shop = flipper.getOpenShop();
		Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
		if (shop == null || frame == null || frame.isHidden())
		{
			return null;
		}
		panel.getChildren().clear();
		if (config.shopRates())
		{
			addRates(shop);
		}
		addRestock(shop);
		if (panel.getChildren().isEmpty())
		{
			return null;
		}

		// Just below the shop window, or above it if there's no room
		Rectangle b = frame.getBounds();
		int y = b.y + b.height + 4;
		if (y + lastHeight > client.getCanvasHeight())
		{
			y = Math.max(0, b.y - lastHeight - 4);
		}
		panel.setPreferredLocation(new Point(b.x, y));
		Dimension d = panel.render(g);
		if (d != null)
		{
			lastHeight = d.height;
		}
		return null;
	}

	/** Items overstocked on this world and when they're back to normal, and items others are overstocking everywhere. */
	private void addRestock(Shop shop)
	{
		long now = System.currentTimeMillis();
		ShopStock stock = stockTracker.get();
		boolean titled = false;
		for (ShopStock.Seen s : stock.overstocked(shop.getName(), now))
		{
			if (s.getWorld() == client.getWorld())
			{
				if (!titled)
				{
					panel.getChildren().add(TitleComponent.builder().text("Overstocked on this world").color(TITLE).build());
					titled = true;
				}
				panel.getChildren().add(line(ShopTimes.itemName(s.getItem()) + " +" + s.getOverstock(),
					(s.isExact() ? "" : "up to ") + ShopTimes.duration(s.getNormalAt() - now)));
			}
		}
		if (titled)
		{
			panel.getChildren().add(note("Time until back to normal stock. It's exact once you've seen one restock here."));
		}
		for (ShopStock.Busy busy : stock.busy(now))
		{
			if (busy.getShop().equals(shop.getName()))
			{
				panel.getChildren().add(note(ShopTimes.itemName(busy.getItem()) + ": others overstocked it on "
					+ busy.getWorlds() + " worlds you checked"));
			}
		}
	}

	private void addRates(Shop shop)
	{
		boolean gloves = shop.getGlovesTier() != null && flipper.getOpenShopGlovesTier() >= shop.getGlovesTier();
		ShopRates rates = ShopRates.of(shop, gloves);

		panel.getChildren().add(TitleComponent.builder().text(shop.getName()).color(TITLE).build());
		panel.getChildren().add(line("Sells for", rates.getSell() > 0 ? percent(rates.getSell()) : "doesn't sell"));
		panel.getChildren().add(line("Buys for", rates.getBuy() > 0 ? percent(rates.getBuy()) : "doesn't buy"));
		panel.getChildren().add(line("Change per item", percent(rates.getDelta())));
		if (shop.isGeneral() && rates.getBuy() > 0)
		{
			panel.getChildren().add(note("Buys any tradeable item"));
		}
		if (rates.isVaries())
		{
			panel.getChildren().add(note("Some items have their own rates"));
		}
		if (rates.isGloves())
		{
			panel.getChildren().add(note("With your Karamja gloves"));
		}
		else if (shop.getGlovesTier() != null)
		{
			panel.getChildren().add(note("Better with Karamja gloves " + shop.getGlovesTier() + "+"));
		}
	}

	private static LineComponent line(String left, String right)
	{
		return LineComponent.builder().left(left).leftColor(NORMAL).right(right).rightColor(NORMAL).build();
	}

	private static LineComponent note(String text)
	{
		return LineComponent.builder().left(text).leftColor(MUTED).build();
	}

	/** 1305 tenths of a percent -> "130.5%" (or "130%" when whole). */
	static String percent(int tenths)
	{
		return tenths % 10 == 0 ? (tenths / 10) + "%" : (tenths / 10) + "." + Math.abs(tenths % 10) + "%";
	}
}
