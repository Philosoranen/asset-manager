package com.assetmanager.shop;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * Helps the player find a shop, like the clue scroll plugin does for clues: centres the world map on it when the map
 * is opened, and points the hint arrow at it until the player gets there. Nothing is clicked or opened for them.
 */
@Singleton
public class ShopFinder
{
	/** Close enough to count as there: the arrow's job is done. */
	private static final int ARRIVED_TILES = 8;

	private final Client client;
	private final ClientThread clientThread;

	/** The shop being found, or null. */
	@Getter
	private volatile String shop;
	private volatile WorldPoint target;
	/** Called on the client thread with the shop when the player arrives. */
	@Setter
	private Consumer<String> onArrived = s -> {};

	@Inject
	ShopFinder(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	/** Starts finding a shop at {@code at}. Client thread. */
	public void find(String name, WorldPoint at)
	{
		clear();
		shop = name;
		target = at;
		centreMap();
		pointArrow();
	}

	/** Stops finding the shop, and takes the hint arrow away if it's still ours. Client thread. */
	public void clear()
	{
		WorldPoint t = target;
		if (t != null && client.hasHintArrow() && t.equals(client.getHintArrowPoint()))
		{
			client.clearHintArrow();
		}
		shop = null;
		target = null;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.WORLDMAP && target != null)
		{
			// The map sets its own position as it opens, so centre it just after
			clientThread.invokeLater(this::centreMap);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		WorldPoint t = target;
		if (t == null || client.getLocalPlayer() == null)
		{
			return;
		}
		WorldPoint here = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
		if (here != null && here.getPlane() == t.getPlane() && here.distanceTo2D(t) <= ARRIVED_TILES)
		{
			String arrived = shop;
			clear();
			onArrived.accept(arrived);
			return;
		}
		// Something else (a quest, say) may have used the arrow meanwhile; take it back once it's free
		pointArrow();
	}

	private void centreMap()
	{
		WorldMap map = client.getWorldMap();
		WorldPoint t = target;
		if (map != null && t != null)
		{
			map.setWorldMapPositionTarget(t);
		}
	}

	private void pointArrow()
	{
		WorldPoint t = target;
		if (t != null && !client.hasHintArrow())
		{
			client.setHintArrow(t);
		}
	}
}
