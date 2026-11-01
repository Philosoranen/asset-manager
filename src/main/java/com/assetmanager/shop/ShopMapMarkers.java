package com.assetmanager.shop;

import com.assetmanager.dest.Destination;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * Marks the shops Asset Manager suggests on the world map; hover one for what to sell there, click it to centre on it.
 * The shop being found sticks to the edge of the map, pointing the way, while it's off screen.
 */
@Singleton
public class ShopMapMarkers
{
	private static final int ICON_SIZE = 15;

	private final WorldMapPointManager worldMapPointManager;
	private final List<WorldMapPoint> points = new ArrayList<>();
	private volatile BufferedImage icon;
	private List<String> shown = new ArrayList<>();
	/** The shop being found, if any. */
	private String focused;
	private List<Destination> lastShops = new ArrayList<>();
	private WorldPoint lastFrom;

	@Inject
	ShopMapMarkers(WorldMapPointManager worldMapPointManager, SpriteManager spriteManager)
	{
		this.worldMapPointManager = worldMapPointManager;
		spriteManager.getSpriteAsync(SpriteID.Mapfunction.GENERAL_STORE, 0,
			img -> icon = img == null ? null : ImageUtil.resizeImage(img, ICON_SIZE, ICON_SIZE));
	}

	/** Shows these shops, at the branch nearest {@code from}; an empty list clears them. */
	public void update(List<Destination> shops, WorldPoint from)
	{
		lastShops = shops;
		lastFrom = from;
		BufferedImage img = icon;
		List<String> wanted = new ArrayList<>();
		List<WorldMapPoint> next = new ArrayList<>();
		for (Destination d : shops)
		{
			WorldPoint at = d.getShop() == null ? null : d.getShop().nearestPoint(from);
			if (at == null || img == null)
			{
				continue;
			}
			String tooltip = d.getName() + ": " + d.getCount() + " item" + (d.getCount() == 1 ? "" : "s")
				+ (d.getGain() > 0 ? ", +" + QuantityFormatter.quantityToStackSize(d.getGain()) + " gp" : "");
			boolean finding = d.getName().equals(focused);
			wanted.add(tooltip + "@" + at + (finding ? " finding" : ""));
			next.add(WorldMapPoint.builder()
				.worldPoint(at)
				.image(img)
				.name(d.getName())
				.tooltip(tooltip)
				.jumpOnClick(true)
				.snapToEdge(finding)
				.build());
		}
		// Only touch the map when something changed
		if (Objects.equals(wanted, shown))
		{
			return;
		}
		clear();
		points.addAll(next);
		points.forEach(worldMapPointManager::add);
		shown = wanted;
	}

	/** Which shop is being found (its marker sticks to the map's edge), or null. */
	public void setFocused(String shop)
	{
		focused = shop;
		update(lastShops, lastFrom);
	}

	public void clear()
	{
		points.forEach(worldMapPointManager::remove);
		points.clear();
		shown = new ArrayList<>();
	}
}
