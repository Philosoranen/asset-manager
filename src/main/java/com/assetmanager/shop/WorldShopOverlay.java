package com.assetmanager.shop;

import com.assetmanager.AssetManagerConfig;
import com.assetmanager.ui.PriceTooltip;
import com.assetmanager.ui.SolidTooltip;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.World;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.Text;

/**
 * In the world switcher, for the shop the player has open or last opened: an outline on the flag of each world where
 * it was seen overstocked, shaded from red (just sold to) through orange and yellow to green as the overstock goes
 * (from what was seen there and each item's restock time, assuming nobody else has sold to it since), and a green one
 * on the other worlds the player can hop to.
 * Only the items in the inventory count, if the shop has been seen overstocked in any of them; otherwise all do.
 * Hovering a shaded flag shows what's overstocked there now and when it's due back to normal.
 */
@Slf4j
@Singleton
public class WorldShopOverlay extends Overlay
{
	private static final Color CHECK = new Color(0x4CAF50);
	private static final Color NORMAL = new Color(0xEEEEEE);
	private static final Color MUTED = new Color(0x9E9E9E);
	private static final Pattern WORLD_NUMBER = Pattern.compile("^\\d{3,4}$");
	private static final Pattern SKILL_TOTAL = Pattern.compile("(\\d{3,4}) skill total");
	/** World types a normal account can hop to for shopping; any other type leaves a world out. */
	private static final Set<WorldType> PLAIN = EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL);

	private final Client client;
	private final AssetManagerConfig config;
	private final ShopStockTracker stockTracker;
	private final TooltipManager tooltipManager;
	private final ItemManager itemManager;

	@Setter
	private Supplier<Shop> openShop = () -> null;

	/** The shop last opened, which the marks are for once it's closed. */
	private String lastShop;
	/** Rows found last frame, logged when it changes. */
	private int lastRows = -1;

	@Inject
	WorldShopOverlay(Client client, AssetManagerConfig config, ShopStockTracker stockTracker, TooltipManager tooltipManager,
		ItemManager itemManager)
	{
		this.itemManager = itemManager;
		this.client = client;
		this.config = config;
		this.stockTracker = stockTracker;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Shop open = openShop.get();
		if (open != null)
		{
			lastShop = open.getName();
		}
		Widget switcher = client.getWidget(InterfaceID.Worldswitcher.UNIVERSE);
		if (!config.worldShopMarks() || switcher == null || switcher.isHidden())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		String shop = lastShop != null ? lastShop : latestShop(stockTracker.get());
		if (shop == null)
		{
			return null;
		}
		// Only the items being carried, if the shop's been seen overstocked in any of them; otherwise all of them
		List<ShopStock.Seen> overstocked = stockTracker.get().overstocked(shop, now);
		Set<String> carried = carriedNames();
		boolean onlyCarried = overstocked.stream().anyMatch(s -> carried.contains(s.getItem()));
		Map<Integer, List<ShopStock.Seen>> full = new HashMap<>();
		for (ShopStock.Seen s : overstocked)
		{
			if (!onlyCarried || carried.contains(s.getItem()))
			{
				full.computeIfAbsent(s.getWorld(), w -> new ArrayList<>()).add(s);
			}
		}
		Map<Integer, World> worlds = new HashMap<>();
		World[] known = client.getWorldList();
		if (known != null)
		{
			for (World w : known)
			{
				worlds.put(w.getId(), w);
			}
		}
		World here = worlds.get(client.getWorld());
		boolean members = here == null || here.getTypes().contains(WorldType.MEMBERS);

		Widget scroll = client.getWidget(InterfaceID.Worldswitcher.SCROLLAREA);
		Rectangle visible = (scroll != null && !scroll.isHidden() ? scroll : switcher).getBounds();
		// The rows are drawn by the game: everything under the switcher, wherever it sits
		List<Widget> all = new ArrayList<>();
		collect(switcher, all);
		List<Widget> sprites = new ArrayList<>();
		Map<Integer, Widget> numbers = new HashMap<>();
		for (Widget w : all)
		{
			if (w.getSpriteId() >= 0)
			{
				sprites.add(w);
			}
			// A row's world is its leftmost number; the player count is a number too
			if (worldNumber(w) != null)
			{
				Rectangle b = w.getBounds();
				int row = b.y + b.height / 2;
				Widget other = numbers.get(row);
				if (other == null || b.x < other.getBounds().x)
				{
					numbers.put(row, w);
				}
			}
		}
		if (numbers.size() != lastRows)
		{
			lastRows = numbers.size();
			log.debug("World switcher: {} rows, {} pictures, {} widgets; marking for {}", numbers.size(), sprites.size(), all.size(),
				shop);
		}
		Point mouse = client.getMouseCanvasPosition();
		g.setStroke(new BasicStroke(2));
		for (Widget w : numbers.values())
		{
			int world = worldNumber(w);
			Widget flag = flagFor(w, sprites);
			if (flag == null)
			{
				continue;
			}
			Rectangle b = flag.getBounds();
			if (!visible.contains(b.x + b.width / 2, b.y + b.height / 2))
			{
				continue;
			}
			// Full until everything seen overstocked there is due back to normal, counting only what was seen: what
			// others sell there since can't be known
			List<ShopStock.Seen> seen = full.get(world);
			if (seen != null)
			{
				// Red when just sold to, through orange and yellow, to green as it's due back to normal
				double left = left(seen, now);
				Color color = shade(left);
				g.setColor(color);
				g.drawRect(b.x, b.y, b.width - 1, b.height - 1);
				if (mouse != null && b.contains(mouse.getX(), mouse.getY()) && !client.isMenuOpen())
				{
					tooltipManager.add(SolidTooltip.of(tooltip(shop, world, seen, now, left, color)));
				}
			}
			else if (canHop(worlds.get(world), members))
			{
				g.setColor(CHECK);
				g.drawRect(b.x, b.y, b.width - 1, b.height - 1);
			}
		}
		return null;
	}

	/** The names of what's in the inventory, as the shop data has them. */
	private Set<String> carriedNames()
	{
		Set<String> out = new HashSet<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (Item i : inv.getItems())
			{
				if (i.getId() > 0)
				{
					out.add(itemManager.getItemComposition(itemManager.canonicalize(i.getId())).getName().toLowerCase());
				}
			}
		}
		return out;
	}

	/** How much of the overstock last seen on a world is still there: 1 just after, 0 once it's back to normal. */
	static double left(List<ShopStock.Seen> seen, long now)
	{
		long was = 0;
		long still = 0;
		for (ShopStock.Seen s : seen)
		{
			was += s.getOverstock();
			still += s.overstockAt(now);
		}
		return was <= 0 ? 0 : Math.min(1, (double) still / was);
	}

	/** Red for all of it left, through orange and yellow, to green for none. */
	static Color shade(double left)
	{
		return Color.getHSBColor((float) ((1 - left) / 3), 0.8f, 0.95f);
	}

	private static PanelComponent tooltip(String shop, int world, List<ShopStock.Seen> seen, long now, double left, Color color)
	{
		PanelComponent p = new PanelComponent();
		p.setBackgroundColor(PriceTooltip.SOLID_BACKGROUND);
		p.setPreferredSize(new Dimension(180, 0));
		p.getChildren().add(TitleComponent.builder().text(shop + ", world " + world).color(color).build());
		p.getChildren().add(LineComponent.builder().left("Recovered").leftColor(NORMAL)
			.right(Math.round((1 - left) * 100) + "%").rightColor(color).build());
		for (ShopStock.Seen s : seen)
		{
			p.getChildren().add(LineComponent.builder()
				.left(ShopTimes.itemName(s.getItem()) + " +" + s.overstockAt(now)).leftColor(NORMAL)
				.right((s.isExact() ? "" : "up to ") + ShopTimes.duration(s.getNormalAt() - now)).rightColor(NORMAL)
				.build());
		}
		p.getChildren().add(LineComponent.builder().left("Overstock left now and time until back to normal, if nobody else has sold any since").leftColor(MUTED).build());
		return p;
	}

	/**
	 * Whether the player can hop to a world to shop: the same membership as the one they're on, with no special rules
	 * (PvP, Deadman, seasonal...), and a skill total they meet. Unknown worlds count, as the list hasn't loaded.
	 */
	private boolean canHop(World w, boolean members)
	{
		if (w == null)
		{
			return true;
		}
		EnumSet<WorldType> types = w.getTypes();
		if (types.contains(WorldType.MEMBERS) != members || !PLAIN.containsAll(types))
		{
			return false;
		}
		if (types.contains(WorldType.SKILL_TOTAL) && w.getActivity() != null)
		{
			Matcher m = SKILL_TOTAL.matcher(w.getActivity());
			return !m.find() || client.getTotalLevel() >= Integer.parseInt(m.group(1));
		}
		return true;
	}

	/** The world a row's number text says; null if it isn't one. */
	private static Integer worldNumber(Widget w)
	{
		if (w.isHidden() || w.getText() == null)
		{
			return null;
		}
		String t = Text.removeTags(w.getText()).trim();
		return WORLD_NUMBER.matcher(t).matches() ? Integer.parseInt(t) : null;
	}

	/** The shop the player most recently saw stock at; null if none. */
	private static String latestShop(ShopStock stock)
	{
		ShopStock.Seen latest = null;
		for (ShopStock.Seen s : stock.all())
		{
			if (latest == null || s.getAt() > latest.getAt())
			{
				latest = s;
			}
		}
		return latest == null ? null : latest.getShop();
	}

	/** Every visible widget under {@code w}. */
	private static void collect(Widget w, List<Widget> out)
	{
		if (w == null || w.isHidden())
		{
			return;
		}
		out.add(w);
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					collect(c, out);
				}
			}
		}
	}

	/** The flag on a world number's row: the nearest picture to its right, no wider than a flag. */
	private static Widget flagFor(Widget number, List<Widget> rows)
	{
		Rectangle n = number.getBounds();
		int middle = n.y + n.height / 2;
		Widget best = null;
		for (Widget w : rows)
		{
			if (w.isHidden() || w.getSpriteId() < 0 || w == number)
			{
				continue;
			}
			Rectangle b = w.getBounds();
			if (b.x >= n.x + n.width - 2 && b.width <= 40 && middle >= b.y && middle <= b.y + b.height
				&& (best == null || b.x < best.getBounds().x))
			{
				best = w;
			}
		}
		return best;
	}
}
