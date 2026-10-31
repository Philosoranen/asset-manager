package com.assetmanager.keep;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

/**
 * On the GE's offer screen, when a sell offer's quantity would dip into the items the player is keeping: greys out
 * the Confirm button (its click is blocked by {@link KeepGuard}), outlines the "..." quantity button and says how
 * many to enter.
 */
@Singleton
public class GeKeepOverlay extends Overlay
{
	private static final Color WARN = new Color(0xFF9800);
	private static final Color BLOCKED = new Color(0xF44336);
	private static final String ENTER_QUANTITY = "Enter quantity";

	private final Client client;
	private final KeepGuard guard;

	@Inject
	GeKeepOverlay(Client client, KeepGuard guard)
	{
		this.client = client;
		this.guard = guard;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		KeepGuard.GeSale sale = guard.geSale();
		if (sale == null || !sale.isTooMany())
		{
			return null;
		}
		g.setFont(FontManager.getRunescapeSmallFont());

		Widget confirm = client.getWidget(InterfaceID.GeOffers.SETUP_CONFIRM);
		if (confirm != null && !confirm.isHidden())
		{
			Rectangle b = confirm.getBounds();
			Composite old = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
			g.setColor(Color.BLACK);
			g.fill(b);
			g.setComposite(old);
			g.setColor(BLOCKED);
			g.setStroke(new BasicStroke(2));
			g.draw(b);
			centered(g, "Keeping " + (sale.getKeep() == KeepRules.KEEP_ALL ? "all" : QuantityFormatter.formatNumber(sale.getKeep())), b, BLOCKED);

			String warning = sale.getCanSell() == 0
				? "You're keeping " + sale.keeping() + "."
				: "You're keeping " + sale.keeping() + ": sell " + QuantityFormatter.formatNumber(sale.getCanSell()) + " at most.";
			label(g, warning, b.x + b.width / 2, b.y - 6, BLOCKED);
		}

		Widget dots = sale.getCanSell() > 0 ? findAction(client.getWidget(InterfaceID.GeOffers.SETUP), ENTER_QUANTITY) : null;
		if (dots != null)
		{
			Rectangle b = dots.getBounds();
			g.setColor(WARN);
			g.setStroke(new BasicStroke(2));
			g.drawRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
			label(g, "Enter " + QuantityFormatter.formatNumber(sale.getCanSell()), b.x + b.width / 2, b.y - 4, WARN);
		}
		return null;
	}

	/** The first visible widget under {@code w} with the given menu action. */
	private static Widget findAction(Widget w, String action)
	{
		if (w == null || w.isHidden())
		{
			return null;
		}
		String[] actions = w.getActions();
		if (actions != null)
		{
			for (String a : actions)
			{
				if (a != null && a.equalsIgnoreCase(action))
				{
					return w;
				}
			}
		}
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					Widget found = findAction(c, action);
					if (found != null)
					{
						return found;
					}
				}
			}
		}
		return null;
	}

	private static void centered(Graphics2D g, String text, Rectangle b, Color color)
	{
		FontMetrics fm = g.getFontMetrics();
		shadowed(g, text, b.x + (b.width - fm.stringWidth(text)) / 2, b.y + (b.height + fm.getAscent()) / 2 - 1, color);
	}

	/** Text centered on x with its baseline at y. */
	private static void label(Graphics2D g, String text, int x, int y, Color color)
	{
		shadowed(g, text, x - g.getFontMetrics().stringWidth(text) / 2, y, color);
	}

	private static void shadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}
}
