package com.assetmanager.ui;

import com.assetmanager.AssetManagerConfig;
import com.assetmanager.Suggestions;
import com.assetmanager.bank.BankViews;
import com.assetmanager.dest.Destination;
import com.assetmanager.keep.Holdings;
import com.assetmanager.keep.KeepRules;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;

/**
 * Draws a small coffer, alchemy, shop or GE icon in the corner of items Asset Manager has a suggestion for, in the
 * bank, inventory and seed vault. A stack that's all being kept (say the one rune platebody you keep, in your
 * inventory) gets no icon. While an Asset Manager bank view is open, items with a keep rule show how many are kept.
 */
@Singleton
public class SuggestionOverlay extends WidgetItemOverlay
{
	private static final int ICON_SIZE = 12;
	/** How far down the item the icon goes: below the stack size's line of text. */
	public static final int ICON_TOP = 10;
	private static final Color KEEP_COLOR = new Color(0xFF9800);

	private final ItemManager itemManager;
	private final AssetManagerConfig config;
	private final BankViews bankViews;
	private final BufferedImage cofferIcon;
	private volatile Image alchIcon;
	private volatile Image shopIcon;
	private volatile Image geIcon;

	@Setter
	private volatile Suggestions suggestions = Suggestions.NONE;
	@Setter
	private volatile KeepRules keepRules = new KeepRules();
	@Setter
	private volatile Holdings holdings = Holdings.NONE;

	@Inject
	SuggestionOverlay(ItemManager itemManager, SpriteManager spriteManager, AssetManagerConfig config, BankViews bankViews)
	{
		this.itemManager = itemManager;
		this.config = config;
		this.bankViews = bankViews;
		cofferIcon = ImageUtil.resizeImage(ImageUtil.loadImageResource(getClass(), "/com/assetmanager/icon.png"), ICON_SIZE, ICON_SIZE);
		spriteManager.getSpriteAsync(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY, 0,
			img -> alchIcon = img == null ? null : ImageUtil.resizeImage(img, ICON_SIZE, ICON_SIZE));
		spriteManager.getSpriteAsync(SpriteID.Mapfunction.GENERAL_STORE, 0,
			img -> shopIcon = img == null ? null : ImageUtil.resizeImage(img, ICON_SIZE, ICON_SIZE));
		spriteManager.getSpriteAsync(SpriteID.Mapfunction.GRAND_EXCHANGE, 0,
			img -> geIcon = img == null ? null : ImageUtil.resizeImage(img, ICON_SIZE, ICON_SIZE));
		showOnBank();
		showOnInventory();
		showOnInterfaces(InterfaceID.SEED_VAULT);
	}

	/** Where an item widget's stack is. */
	static Holdings.Place place(int widgetId)
	{
		if (widgetId == InterfaceID.Bankmain.ITEMS)
		{
			return Holdings.Place.BANK;
		}
		return widgetId >>> 16 == InterfaceID.SEED_VAULT ? Holdings.Place.SEED_VAULT : Holdings.Place.INVENTORY;
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		int id = itemManager.canonicalize(itemId);
		Rectangle b = widgetItem.getCanvasBounds();
		KeepRules rules = keepRules;
		Holdings.Place place = place(widgetItem.getWidget().getId());
		// Nothing spare here: the kept ones get no icon
		Image icon = rules.has(id) && holdings.spare(place, id, rules) == 0 ? null
			: icon(iconKind(suggestions, id, place == Holdings.Place.BANK ? bankViews.showingKind() : null));
		if (icon != null)
		{
			// Right edge, below the quantity text (which can run the whole width) and above any label at the bottom
			g.drawImage(icon, b.x + b.width - ICON_SIZE, b.y + ICON_TOP, null);
		}

		// A small padlock rather than "keep N", which ran into the next item; hovering shows how many are kept. In the Kept
		// view every item is kept, so there it's left off.
		if (rules.has(id) && widgetItem.getWidget().getId() == InterfaceID.Bankmain.ITEMS && bankViews.isShowing()
			&& bankViews.showingKind() != Destination.Kind.KEPT)
		{
			g.drawImage(LOCK, b.x, b.y + b.height - LOCK.getHeight(), null);
		}
	}

	private static final int LOCK_WIDTH = 7;
	private static final int LOCK_HEIGHT = 9;
	/** Drawn once: this runs for every item on every frame. */
	private static final BufferedImage LOCK = lockImage();

	private static BufferedImage lockImage()
	{
		BufferedImage img = new BufferedImage(LOCK_WIDTH + 2, LOCK_HEIGHT + 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		drawLock(g, 1, 1);
		g.dispose();
		return img;
	}

	/** A padlock {@code LOCK_WIDTH} by {@code LOCK_HEIGHT}, outlined so it shows on any item. */
	static void drawLock(Graphics2D g, int x, int y)
	{
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		Stroke stroke = g.getStroke();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Shackle, then body, each with a dark outline
		g.setStroke(new BasicStroke(3f));
		g.setColor(Color.BLACK);
		g.drawArc(x + 1, y, LOCK_WIDTH - 2, 7, 0, 180);
		g.setStroke(new BasicStroke(1.4f));
		g.setColor(KEEP_COLOR);
		g.drawArc(x + 1, y, LOCK_WIDTH - 2, 7, 0, 180);
		g.setStroke(stroke);
		g.setColor(Color.BLACK);
		g.fillRect(x - 1, y + 3, LOCK_WIDTH + 2, LOCK_HEIGHT - 2);
		g.setColor(KEEP_COLOR);
		g.fillRect(x, y + 4, LOCK_WIDTH, LOCK_HEIGHT - 4);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
	}

	/** Which icon an item gets: its suggestion, or in a bank view for one destination, only that one. */
	static Suggestions.Kind iconKind(Suggestions s, int id, Destination.Kind view)
	{
		if (view != null)
		{
			switch (view)
			{
				case COFFER:
					return s.getCoffer().contains(id) ? Suggestions.Kind.COFFER : null;
				case ALCH:
					return s.getAlch().contains(id) ? Suggestions.Kind.ALCH : null;
				case GE:
					return s.getGe().contains(id) ? Suggestions.Kind.GE : null;
				case SHOP:
					return s.getShop().contains(id) ? Suggestions.Kind.SHOP : null;
				default:
			}
		}
		return s.getIcon(id);
	}

	private Image icon(Suggestions.Kind kind)
	{
		if (kind == null)
		{
			return null;
		}
		switch (kind)
		{
			case COFFER:
				return config.cofferIcons() ? cofferIcon : null;
			case ALCH:
				return config.alchIcons() ? alchIcon : null;
			case SHOP:
				return config.shopIcons() ? shopIcon : null;
			default:
				return config.geIcons() ? geIcon : null;
		}
	}
}
