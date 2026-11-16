package com.assetmanager.shop;

import com.assetmanager.AssetManagerConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * In a shop, while there's something to sell: outlines the quantity button to select next for the fewest clicks (see
 * {@link ShopSellOverlay#nextQuantity()}). The items to click at it are outlined by {@link ShopSellOverlay}.
 */
@Singleton
public class ShopSellGuideOverlay extends Overlay
{
	private final Client client;
	private final ShopSellOverlay sells;
	private final AssetManagerConfig config;

	@Setter
	private Supplier<Shop> openShop = () -> null;

	@Inject
	ShopSellGuideOverlay(Client client, ShopSellOverlay sells, AssetManagerConfig config)
	{
		this.client = client;
		this.sells = sells;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.shopSellCounts() || !config.shopSellGuide() || openShop.get() == null)
		{
			return null;
		}
		int next = sells.nextQuantity();
		if (next <= 0 || next == sells.selectedQuantity())
		{
			return null;
		}
		for (int n = 0; n < ShopSellOverlay.QUANTITIES.length; n++)
		{
			if (ShopSellOverlay.QUANTITIES[n] == next)
			{
				Widget button = client.getWidget(ShopSellOverlay.QUANTITY_BUTTONS[n]);
				if (button != null && !button.isHidden())
				{
					Rectangle b = button.getBounds();
					g.setColor(ShopSellOverlay.GUIDE);
					g.setStroke(new BasicStroke(2));
					g.drawRect(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
					g.setFont(FontManager.getRunescapeSmallFont());
					FontMetrics fm = g.getFontMetrics();
					String text = "Sell " + next + "s";
					int x = b.x + (b.width - fm.stringWidth(text)) / 2;
					g.setColor(Color.BLACK);
					g.drawString(text, x + 1, b.y - 3);
					g.setColor(ShopSellOverlay.GUIDE);
					g.drawString(text, x, b.y - 4);
				}
			}
		}
		return null;
	}
}
