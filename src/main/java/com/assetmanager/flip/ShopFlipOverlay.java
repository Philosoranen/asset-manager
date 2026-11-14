package com.assetmanager.flip;

import com.assetmanager.ui.SuggestionOverlay;
import java.awt.Color;
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
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * Marks shop items worth buying with where to take them, a Grand Exchange icon or Death's coffer (whichever makes more),
 * and how many to buy from what the shop holds now.
 */
@Singleton
public class ShopFlipOverlay extends Overlay
{
	private static final int ICON_SIZE = 12;
	private static final Color COUNT = new Color(0x4CAF50);

	private final Client client;
	private final ShopFlipper flipper;
	private volatile Image icon;
	private final Image cofferIcon;

	@Inject
	ShopFlipOverlay(Client client, ShopFlipper flipper, SpriteManager spriteManager)
	{
		this.client = client;
		this.flipper = flipper;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		spriteManager.getSpriteAsync(SpriteID.Mapfunction.GRAND_EXCHANGE, 0,
			img -> icon = img == null ? null : ImageUtil.resizeImage(img, ICON_SIZE, ICON_SIZE));
		// The plugin's icon, as the coffer suggestions elsewhere use
		cofferIcon = ImageUtil.resizeImage(ImageUtil.loadImageResource(getClass(), "/com/assetmanager/icon.png"), ICON_SIZE,
			ICON_SIZE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Map<Integer, ShopFlipper.Buy> flips = flipper.getFlips();
		Image geIcon = icon;
		if (flips.isEmpty())
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
			ShopFlipper.Buy buy = w.isHidden() ? null : flips.get(w.getItemId());
			if (buy == null)
			{
				continue;
			}
			Rectangle b = w.getBounds();
			// Skip items scrolled out of view
			if (!visible.contains(b.x + b.width / 2, b.y + b.height / 2))
			{
				continue;
			}
			Image img = buy.isCofferBest() ? cofferIcon : geIcon;
			if (img != null)
			{
				// Below the shop's stock count, like the icons on items elsewhere
				g.drawImage(img, b.x + b.width - ICON_SIZE, b.y + SuggestionOverlay.ICON_TOP, null);
			}
			String count = "buy " + QuantityFormatter.quantityToStackSize(buy.best().getQuantity());
			g.setFont(FontManager.getRunescapeSmallFont());
			int y = b.y + b.height;
			g.setColor(Color.BLACK);
			g.drawString(count, b.x + 1, y + 1);
			g.setColor(COUNT);
			g.drawString(count, b.x, y);
		}
		return null;
	}
}
