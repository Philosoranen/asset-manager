package com.assetmanager.flip;

import com.assetmanager.AssetManagerConfig;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * At the GE, while a flip is being bought (see {@link GeGuider}): an outline on what to click next. An empty slot's
 * Buy button, the price and quantity buttons until they match, then Confirm, and Collect when an offer for the flip
 * has finished. What's left to buy is on the side panel.
 */
@Singleton
public class GeGuideOverlay extends Overlay
{
	private static final Color NEXT = new Color(0x00E5FF);
	private static final Color GOOD = new Color(0x4CAF50);

	private final Client client;
	private final GeGuider guider;
	private final AssetManagerConfig config;

	@Inject
	GeGuideOverlay(Client client, GeGuider guider, AssetManagerConfig config)
	{
		this.client = client;
		this.guider = guider;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GeGuide guide = guider.getGuide();
		Widget ge = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		if (guide == null || ge == null || ge.isHidden())
		{
			return null;
		}
		g.setFont(FontManager.getRunescapeSmallFont());

		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		Widget index = client.getWidget(InterfaceID.GeOffers.INDEX);
		GeGuider.Fill fill = guider.fill();
		if (fill != null)
		{
			// The chatbox has its own line to click when that's on
			Widget chat = guider.isFillLineShown() ? null : client.getWidget(InterfaceID.Chatbox.MES_TEXT);
			if (chat != null && !chat.isHidden())
			{
				Rectangle b = chat.getBounds();
				// Only shown without the line to click: say what to pick or type
				String hint = fill.getAsk() == GeGuider.Ask.SEARCH ? "Pick " + fill.getItem().getName()
					: "Enter " + QuantityFormatter.formatNumber(fill.getValue()) + (fill.getAsk() == GeGuider.Ask.PRICE ? " gp" : "");
				label(g, hint, b.x + b.width / 2, b.y - 4, NEXT);
			}
		}
		else if (setup != null && !setup.isHidden())
		{
			guideSetup(g, guide, setup);
		}
		else if (index != null && !index.isHidden())
		{
			guideIndex(g, guide);
		}
		return null;
	}

	/** The offer screen: the price and quantity until they're right, then Confirm. */
	private void guideSetup(Graphics2D g, GeGuide guide, Widget setup)
	{
		if (client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == 1)
		{
			return;
		}
		GeGuide.Item item = guider.setupItem(guide);
		GeGuide.Item next = guide.next();
		Rectangle s = setup.getBounds();
		if (item == null || guide.toOrder(item.getItemId()) == 0)
		{
			if (next != null)
			{
				label(g, "Search for " + next.getName(), s.x + s.width / 2, s.y + 14, NEXT);
			}
			return;
		}
		int qty = guide.toOrder(item.getItemId());
		boolean qtyRight = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) == qty;
		// The price is a long varp, since prices can go past 2.1b
		boolean priceRight = client.getVarpLongValue(VarPlayerID.GE_NEWOFFER_PRICE) == item.getPrice();
		// Matched by menu option, or failing that by the "..." label: the quantity's is left, the price's right
		List<Widget> dots = new ArrayList<>();
		findText(setup, "...", dots);
		dots.sort((a, b) -> Integer.compare(a.getBounds().x, b.getBounds().x));
		Widget qtyButton = find(setup, GeGuideOverlay::isQuantityButton);
		Widget priceButton = find(setup, GeGuideOverlay::isPriceButton);
		if (qtyButton == null && dots.size() == 2)
		{
			qtyButton = dots.get(0);
		}
		if (priceButton == null && dots.size() == 2)
		{
			priceButton = dots.get(1);
		}
		List<String> missed = new ArrayList<>();
		String qtyText = "Enter " + QuantityFormatter.formatNumber(qty);
		if (!qtyRight && !outline(g, qtyButton, qtyText, NEXT))
		{
			missed.add("Quantity: " + QuantityFormatter.formatNumber(qty));
		}
		String priceText = "Enter " + QuantityFormatter.formatNumber(item.getPrice()) + " gp";
		if (!priceRight && !outline(g, priceButton, priceText, NEXT))
		{
			missed.add("Price: " + QuantityFormatter.formatNumber(item.getPrice()) + " gp");
		}
		if (!missed.isEmpty())
		{
			// The button wasn't found, so say what to enter instead
			label(g, String.join(", ", missed), s.x + s.width / 2, s.y + s.height + 14, NEXT);
		}
		if (qtyRight && priceRight)
		{
			outline(g, client.getWidget(InterfaceID.GeOffers.SETUP_CONFIRM), "Confirm", GOOD);
		}
	}

	/** The offers: Collect for a finished one of the flip's, and an empty slot's Buy button for the next item. */
	private void guideIndex(Graphics2D g, GeGuide guide)
	{
		if (guide.toCollect() >= 0)
		{
			outline(g, client.getWidget(InterfaceID.GeOffers.COLLECTALL), "Collect", GOOD);
		}
		GeGuide.Item next = guide.next();
		int slot = guide.emptySlot();
		if (next == null || slot < 0)
		{
			return;
		}
		Widget box = client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot);
		Widget buy = find(box, a -> a.equalsIgnoreCase("Create Buy offer"));
		outline(g, buy != null ? buy : box, "Buy " + next.getName(), NEXT);
	}

	/** Outlines and labels a widget; false if it isn't showing. */
	private static boolean outline(Graphics2D g, Widget w, String text, Color color)
	{
		if (w == null || w.isHidden())
		{
			return false;
		}
		Rectangle b = w.getBounds();
		Composite old = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
		g.setColor(color);
		g.fill(b);
		g.setComposite(old);
		g.setStroke(new BasicStroke(2));
		g.drawRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2);
		label(g, text, b.x + b.width / 2, b.y - 4, color);
		return true;
	}

	/** Every visible widget under {@code w} showing {@code text}. */
	private static void findText(Widget w, String text, List<Widget> out)
	{
		if (w == null || w.isHidden())
		{
			return;
		}
		if (w.getText() != null && text.equals(Text.removeTags(w.getText()).trim()))
		{
			out.add(w);
		}
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					findText(c, text, out);
				}
			}
		}
	}

	/** The "..." button to type a quantity: "Enter quantity", or the like. */
	private static boolean isQuantityButton(String action)
	{
		String a = action.toLowerCase();
		return a.contains("quantity") && (a.contains("enter") || a.contains("set") || a.contains("custom"));
	}

	/** The "..." button to type a price: "Enter price", or the like, but not the guide price or a percentage. */
	private static boolean isPriceButton(String action)
	{
		String a = action.toLowerCase();
		return a.contains("price") && !a.contains("%") && !a.contains("guide") && !a.contains("market")
			&& (a.contains("enter") || a.contains("set") || a.contains("custom"));
	}

	/** The first visible widget under {@code w} with a menu action (colour tags removed) matching {@code action}. */
	static Widget find(Widget w, Predicate<String> action)
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
				if (a != null && action.test(Text.removeTags(a)))
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
					Widget found = find(c, action);
					if (found != null)
					{
						return found;
					}
				}
			}
		}
		return null;
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
