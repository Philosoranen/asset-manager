package com.assetmanager.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;

/**
 * A tooltip that keeps its panel's solid background, with an outline. RuneLite's tooltip overlay replaces a
 * {@link PanelComponent}'s background with the see-through overlay color, which made long tooltips hard to read over
 * busy interfaces like shops. Wrapping the panel hides it from that.
 */
public final class SolidTooltip implements LayoutableRenderableEntity
{
	private static final Color OUTLINE = new Color(0x1E1A16);

	private final PanelComponent panel;
	private final Rectangle bounds = new Rectangle();

	private SolidTooltip(PanelComponent panel)
	{
		this.panel = panel;
	}

	public static Tooltip of(PanelComponent panel)
	{
		return new Tooltip(new SolidTooltip(panel));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (panel.getBounds().width == 0)
		{
			// A panel sizes its background from what its contents measured last time, so a new one would get a
			// background only as big as its padding. Measure it once with nothing drawn first.
			Graphics2D measure = (Graphics2D) g.create();
			measure.setClip(0, 0, 0, 0);
			panel.render(measure);
			measure.dispose();
		}
		Dimension d = panel.render(g);
		if (d != null)
		{
			Rectangle b = panel.getBounds();
			g.setColor(OUTLINE);
			g.drawRect(b.x, b.y, b.width - 1, b.height - 1);
			bounds.setBounds(b);
		}
		return d;
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		panel.setPreferredLocation(position);
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		panel.setPreferredSize(dimension);
	}
}
