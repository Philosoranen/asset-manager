package com.assetmanager.flip;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/** Draws a Grand Exchange icon on shop items that can be flipped for a profit. */
@Singleton
public class ShopFlipOverlay extends Overlay
{
	private static final int ICON_SIZE = 12;

	private final Client client;
	private final ShopFlipper flipper;
	private volatile Image icon;

	@Inject
	ShopFlipOverlay(Client client, ShopFlipper flipper, SpriteManager spriteManager)
	{
		this.client = client;
		this.flipper = flipper;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		spriteManager.getSpriteAsync(SpriteID.Mapfunction.GRAND_EXCHANGE, 0,
			img -> icon = img == null ? null : ImageUtil.resizeImage(img, ICON_SIZE, ICON_SIZE));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Map<Integer, ShopFlip> flips = flipper.getFlips();
		Image img = icon;
		if (flips.isEmpty() || img == null)
		{
			return null;
		}
		Widget items = client.getWidget(InterfaceID.Shopmain.ITEMS);
		if (items == null || items.isHidden() || items.getDynamicChildren() == null)
		{
			return null;
		}
		Rectangle visible = items.getBounds();
		for (Widget w : items.getDynamicChildren())
		{
			if (w.isHidden() || !flips.containsKey(w.getItemId()))
			{
				continue;
			}
			Rectangle b = w.getBounds();
			// Skip items scrolled out of view
			if (!visible.contains(b.x + b.width / 2, b.y + b.height / 2))
			{
				continue;
			}
			g.drawImage(img, b.x + b.width - ICON_SIZE, b.y, null);
		}
		return null;
	}
}
