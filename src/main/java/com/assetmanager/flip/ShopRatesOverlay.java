package com.assetmanager.flip;

import com.assetmanager.AssetManagerConfig;
import com.assetmanager.shop.Shop;
import com.assetmanager.ui.PriceTooltip;
import com.assetmanager.ui.SolidTooltip;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.Text;

/**
 * The open shop's rates in its title bar, either side of its name: what it sells and buys for, and how much the price
 * changes per item of stock, as a percentage of each item's value. Hovering them shows them in full with any notes
 * (Karamja gloves, items with their own rates). Nothing covers the shop's items. How overstocked an item is shows in
 * its tooltip instead (see {@link ShopFlipper}).
 */
@Singleton
public class ShopRatesOverlay extends Overlay
{
	/** The game's own orange, as the shop's title. */
	private static final Color LABEL = new Color(0xFF981F);
	private static final Color VALUE = Color.WHITE;
	private static final Color NORMAL = new Color(0xEEEEEE);
	private static final Color MUTED = new Color(0x9E9E9E);
	/** Space kept clear of the title, the frame's edge, and its close button. */
	private static final int GAP = 10;
	private static final int CLOSE_BUTTON = 30;

	private final Client client;
	private final ShopFlipper flipper;
	private final AssetManagerConfig config;
	private final TooltipManager tooltipManager;

	@Inject
	ShopRatesOverlay(Client client, ShopFlipper flipper, AssetManagerConfig config, TooltipManager tooltipManager)
	{
		this.client = client;
		this.flipper = flipper;
		this.config = config;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Shop shop = flipper.getOpenShop();
		Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
		if (!config.shopRates() || shop == null || frame == null || frame.isHidden())
		{
			return null;
		}
		Widget title = titleWidget(frame);
		if (title == null)
		{
			return null;
		}
		boolean gloves = shop.getGlovesTier() != null && flipper.getOpenShopGlovesTier() >= shop.getGlovesTier();
		ShopRates rates = ShopRates.of(shop, gloves);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		Rectangle f = frame.getBounds();
		Rectangle t = title.getBounds();
		String name = Text.removeTags(title.getText());
		// The title's text is centred in its widget
		int nameWidth = g.getFontMetrics(FontManager.getRunescapeBoldFont()).stringWidth(name);
		int centre = t.x + t.width / 2;
		int leftFrom = f.x + GAP;
		int leftTo = centre - nameWidth / 2 - GAP;
		int rightFrom = centre + nameWidth / 2 + GAP;
		int rightTo = f.x + f.width - CLOSE_BUTTON - GAP;
		int baseline = t.y + (t.height + fm.getAscent()) / 2 - 1;

		String sells = rates.getSell() > 0 ? percent(rates.getSell()) : "-";
		String buys = rates.getBuy() > 0 ? percent(rates.getBuy()) : "-";
		String change = percent(rates.getDelta());
		// Fullest wording that fits each side
		String[][] lefts = {{"Sells for ", sells, "  Buys for ", buys}, {"Sells ", sells, "  Buys ", buys}, {"S ", sells, " B ", buys}};
		String[][] rights = {{"Change per item ", change}, {"Change ", change}, {"+/- ", change}};
		Rectangle hover = new Rectangle();
		String[] left = fitting(fm, lefts, leftTo - leftFrom);
		if (left != null)
		{
			int x = leftTo - width(fm, left);
			draw(g, left, x, baseline);
			hover.setBounds(x, t.y, width(fm, left), t.height);
		}
		String[] right = fitting(fm, rights, rightTo - rightFrom);
		if (right != null)
		{
			draw(g, right, rightFrom, baseline);
			Rectangle r = new Rectangle(rightFrom, t.y, width(fm, right), t.height);
			if (hover.isEmpty())
			{
				hover.setBounds(r);
			}
			else
			{
				hover.add(r);
			}
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && !hover.isEmpty() && hover.contains(mouse.getX(), mouse.getY()) && !client.isMenuOpen())
		{
			tooltipManager.add(SolidTooltip.of(tooltip(shop, rates)));
		}
		return null;
	}

	/** The rates in full, with notes. */
	private static PanelComponent tooltip(Shop shop, ShopRates rates)
	{
		PanelComponent p = new PanelComponent();
		p.setBackgroundColor(PriceTooltip.SOLID_BACKGROUND);
		p.setPreferredSize(new Dimension(190, 0));
		p.getChildren().add(TitleComponent.builder().text(shop.getName()).color(LABEL).build());
		p.getChildren().add(line("Sells for", rates.getSell() > 0 ? percent(rates.getSell()) : "doesn't sell"));
		p.getChildren().add(line("Buys for", rates.getBuy() > 0 ? percent(rates.getBuy()) : "doesn't buy"));
		p.getChildren().add(line("Change per item", percent(rates.getDelta())));
		List<String> notes = new ArrayList<>();
		notes.add("Of each item's value. Prices move by the change per item for each one over or under the shop's"
			+ " normal stock.");
		if (shop.isGeneral() && rates.getBuy() > 0)
		{
			notes.add("Buys any tradeable item");
		}
		if (rates.isVaries())
		{
			notes.add("Some items have their own rates");
		}
		if (rates.isGloves())
		{
			notes.add("With your Karamja gloves");
		}
		else if (shop.getGlovesTier() != null)
		{
			notes.add("Better with Karamja gloves " + shop.getGlovesTier() + "+");
		}
		notes.add("Hover an item for how overstocked it is");
		for (String n : notes)
		{
			p.getChildren().add(LineComponent.builder().left(n).leftColor(MUTED).build());
		}
		return p;
	}

	/** The widget holding the shop's name: the frame's first with text. */
	private static Widget titleWidget(Widget w)
	{
		if (w == null || w.isHidden())
		{
			return null;
		}
		if (w.getText() != null && !Text.removeTags(w.getText()).trim().isEmpty())
		{
			return w;
		}
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					Widget found = titleWidget(c);
					if (found != null)
					{
						return found;
					}
				}
			}
		}
		return null;
	}

	/** The first wording that fits in {@code space}; null if none does. */
	private static String[] fitting(FontMetrics fm, String[][] options, int space)
	{
		for (String[] o : options)
		{
			if (width(fm, o) <= space)
			{
				return o;
			}
		}
		return null;
	}

	private static int width(FontMetrics fm, String[] parts)
	{
		return fm.stringWidth(String.join("", parts));
	}

	/** Label, value, label, value...: labels in the game's orange, values in white. */
	private static void draw(Graphics2D g, String[] parts, int x, int y)
	{
		FontMetrics fm = g.getFontMetrics();
		for (int n = 0; n < parts.length; n++)
		{
			g.setColor(Color.BLACK);
			g.drawString(parts[n], x + 1, y + 1);
			g.setColor(n % 2 == 0 ? LABEL : VALUE);
			g.drawString(parts[n], x, y);
			x += fm.stringWidth(parts[n]);
		}
	}

	private static LineComponent line(String left, String right)
	{
		return LineComponent.builder().left(left).leftColor(NORMAL).right(right).rightColor(NORMAL).build();
	}

	/** 1305 tenths of a percent -> "130.5%" (or "130%" when whole). */
	static String percent(int tenths)
	{
		return tenths % 10 == 0 ? (tenths / 10) + "%" : (tenths / 10) + "." + Math.abs(tenths % 10) + "%";
	}
}
