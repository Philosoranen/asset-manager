package com.assetmanager.coffer;

import com.assetmanager.AssetManagerConfig;
import com.assetmanager.ui.PriceTooltip;
import com.assetmanager.value.ItemValuator;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
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
 * On the GE's offer screen, while the coffer is short of its target: says when sacrificing the item being sold would
 * add more to Death's coffer than the offer gets. Only informs; the offer isn't blocked.
 */
@Singleton
public class GeCofferOverlay extends Overlay
{
	private static final int GE_SELL = 1;
	private static final Pattern COINS = Pattern.compile("^([\\d,]+) coins?$");
	private static final int PAD = 5;
	private static final Color TITLE = new Color(0x4CAF50);
	private static final Color TEXT = new Color(0xEEEEEE);
	private static final Color MUTED = new Color(0x9E9E9E);

	private final Client client;
	private final ItemValuator itemValuator;
	private final AssetManagerConfig config;

	/** How far the coffer is below its target; 0 when full or the account can't use it. */
	@Setter
	private volatile long shortfall;
	@Setter
	private volatile boolean cofferKnown;

	@Inject
	GeCofferOverlay(Client client, ItemValuator itemValuator, AssetManagerConfig config)
	{
		this.client = client;
		this.itemValuator = itemValuator;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** The hint for the sell offer being set up, or null. */
	GeCofferHint hint()
	{
		if (!config.geCofferHint() || shortfall <= 0)
		{
			return null;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden() || client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != GE_SELL)
		{
			return null;
		}
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (itemId <= 0)
		{
			return null;
		}
		int quantity = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
		List<Long> amounts = new ArrayList<>();
		coinAmounts(setup, amounts);
		long price = offerPrice(amounts, quantity);
		return price <= 0 ? null : GeCofferHint.of(itemValuator.get(itemId), price, quantity, shortfall);
	}

	/**
	 * The price each, from the "N coins" amounts on the offer screen. RuneLite has no variable for it since GE prices
	 * went past max cash, and the screen shows both the price each and the total, so it's the amount that times the
	 * quantity is also shown. 0 if that can't be told.
	 */
	static long offerPrice(List<Long> amounts, int quantity)
	{
		if (quantity <= 0)
		{
			return 0;
		}
		long best = 0;
		for (long a : amounts)
		{
			if (a > 0 && (best == 0 || a < best) && (quantity == 1 || amounts.contains(a * quantity)))
			{
				best = a;
			}
		}
		return best;
	}

	/** Every "N coins" amount shown under {@code w}. */
	private static void coinAmounts(Widget w, List<Long> amounts)
	{
		if (w == null || w.isHidden())
		{
			return;
		}
		Matcher m = COINS.matcher(Text.removeTags(w.getText() == null ? "" : w.getText()));
		if (m.find())
		{
			try
			{
				amounts.add(Long.parseLong(m.group(1).replace(",", "")));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					coinAmounts(c, amounts);
				}
			}
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GeCofferHint h = hint();
		if (h == null)
		{
			return null;
		}
		List<String> lines = new ArrayList<>();
		List<Color> colors = new ArrayList<>();
		add(lines, colors, "Death's coffer pays more for these", TITLE);
		add(lines, colors, "Sacrificing " + (h.getUnits() == h.getQuantity() ? "them" : QuantityFormatter.formatNumber(h.getUnits()))
			+ " adds " + gp(h.getCofferValue()) + " to your coffer;", TEXT);
		add(lines, colors, "this offer gets " + gp(h.getSaleValue()) + " for "
			+ (h.getUnits() == h.getQuantity() ? "them" : "those") + " after tax (" + gp(h.getExtra()) + " less).", TEXT);
		add(lines, colors, cofferKnown
			? "Your coffer is " + gp(h.getShortfall()) + " short of your target."
			: "Talk to Death to check your coffer's balance.", MUTED);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int width = 0;
		for (String l : lines)
		{
			width = Math.max(width, fm.stringWidth(l));
		}
		int lineHeight = fm.getHeight();
		Dimension box = new Dimension(width + PAD * 2, lineHeight * lines.size() + PAD * 2);

		Rectangle ge = outermost(client.getWidget(InterfaceID.GeOffers.SETUP)).getBounds();
		int x = ge.x + (ge.width - box.width) / 2;
		// Above the GE window if there's room, otherwise below it
		int y = ge.y - box.height - 4 >= 0 ? ge.y - box.height - 4 : ge.y + ge.height + 4;
		x = Math.max(0, Math.min(x, client.getCanvasWidth() - box.width));
		y = Math.max(0, Math.min(y, client.getCanvasHeight() - box.height));

		g.setColor(PriceTooltip.SOLID_BACKGROUND);
		g.fillRect(x, y, box.width, box.height);
		g.setColor(TITLE);
		g.setStroke(new BasicStroke(1));
		g.drawRect(x, y, box.width - 1, box.height - 1);
		int baseline = y + PAD + fm.getAscent();
		for (int i = 0; i < lines.size(); i++)
		{
			g.setColor(Color.BLACK);
			g.drawString(lines.get(i), x + PAD + 1, baseline + 1);
			g.setColor(colors.get(i));
			g.drawString(lines.get(i), x + PAD, baseline);
			baseline += lineHeight;
		}
		return null;
	}

	private static void add(List<String> lines, List<Color> colors, String line, Color color)
	{
		lines.add(line);
		colors.add(color);
	}

	private static String gp(long coins)
	{
		return QuantityFormatter.quantityToStackSize(coins) + " gp";
	}

	private static Widget outermost(Widget w)
	{
		while (w.getParent() != null)
		{
			w = w.getParent();
		}
		return w;
	}
}
