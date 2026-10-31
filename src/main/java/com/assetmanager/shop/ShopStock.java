package com.assetmanager.shop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Which shops the player has seen overstocked, on which worlds, and when each is back to normal.
 * <p>
 * A shop's stock moves one item back towards normal every so many ticks (its restock time, from the wiki), so an
 * item 3 over its normal stock with a 12,000 tick restock is back to normal within 6 hours. When in that cycle the
 * next one goes isn't shown, so until it's known the timer assumes a whole cycle (the worst case). Once the player
 * sees the stock drop by one without buying it, that's the end of a cycle, and the timer for that item on that world
 * is exact from then on. Only what the player has seen is known: there's no way to see a shop on another world
 * without going there.
 * <p>
 * When the player opens a shop, whatever overstock they didn't leave there themselves was left by others. Once an
 * item is found overstocked by others on {@link #BUSY_WORLDS} worlds, other players are likely selling it on every
 * world, so it's busy until those worlds have restocked.
 */
public class ShopStock
{
	public static final long TICK_MILLIS = 600;
	/** Worlds an item has to be found overstocked by others on to call it busy everywhere. */
	public static final int BUSY_WORLDS = 2;
	/** How long a learned restock cycle is trusted; worlds restart with weekly game updates. */
	static final long CYCLE_KEPT_MILLIS = 7L * 24 * 3_600_000;

	@Value
	public static class Seen
	{
		String shop;
		/** Lowercased item name, as in the shop data. */
		String item;
		int world;
		int restockTicks;
		/** How far over its normal stock the item was when last seen, and when. */
		int overstock;
		long at;
		/** How much of that was left by other players: the overstock found when the player opened the shop. */
		int others;
		long othersAt;
		/** When the player saw a restock cycle end for this item on this world, or 0 if they haven't. */
		long cycleAt;

		/** Whether the timer is exact rather than the worst case. */
		public boolean isExact()
		{
			return cycleAt > 0;
		}

		private long interval()
		{
			return restockTicks * TICK_MILLIS;
		}

		/** When the first item goes after {@code t}: a whole cycle later if the cycle isn't known. */
		private long firstDropAfter(long t)
		{
			if (cycleAt <= 0)
			{
				return t + interval();
			}
			return cycleAt + (Math.floorDiv(t - cycleAt, interval()) + 1) * interval();
		}

		/** When the stock is back to normal. */
		public long getNormalAt()
		{
			return overstock == 0 ? at : firstDropAfter(at) + (overstock - 1) * interval();
		}

		/** When the stock left by others has gone. */
		public long getOthersGoneAt()
		{
			return others == 0 ? othersAt : firstDropAfter(othersAt) + (others - 1) * interval();
		}

		/** How far over normal the stock is by {@code now}. */
		int overstockAt(long now)
		{
			long first = firstDropAfter(at);
			return now < first ? overstock : (int) Math.max(0, overstock - ((now - first) / interval() + 1));
		}
	}

	/** Shop items others are selling on every world. */
	@Value
	public static class Busy
	{
		String shop;
		String item;
		/** Worlds the player found it overstocked on by others. */
		int worlds;
		/** When fewer than BUSY_WORLDS of them are still overstocked. */
		long until;
	}

	private final Map<String, Seen> seen = new HashMap<>();
	/** When a restock cycle was seen ending, by shop, item and world; kept after the stock is back to normal. */
	private final Map<String, Long> cycles = new HashMap<>();

	public ShopStock()
	{
	}

	public ShopStock(Collection<Seen> saved, Map<String, Long> savedCycles, long now)
	{
		for (Seen s : saved)
		{
			seen.put(key(s.getShop(), s.getItem(), s.getWorld()), s);
		}
		cycles.putAll(savedCycles);
		prune(now);
	}

	private static String key(String shop, String item, int world)
	{
		return shop + "|" + item + "|" + world;
	}

	/**
	 * Records an item's stock in an open shop.
	 *
	 * @param opened true for the first look after opening the shop on this world, when any overstock the player
	 *               didn't leave there themselves was left by others
	 * @param bought whether the player just bought this item, which also lowers the stock
	 * @return whether anything changed
	 */
	public boolean observe(String shop, String item, int world, int stock, int normalStock, int restockTicks, boolean opened,
		boolean bought, long now)
	{
		if (normalStock < 0 || restockTicks <= 0)
		{
			return false;
		}
		String key = key(shop, item, world);
		Seen old = seen.get(key);
		int over = Math.max(0, stock - normalStock);
		boolean cycleSeen = false;
		if (!opened && !bought && old != null && old.getOverstock() - over == 1)
		{
			// One went while the player watched: a restock cycle just ended
			cycles.put(key, now);
			cycleSeen = true;
		}
		long cycleAt = cycles.getOrDefault(key, 0L);
		int others;
		long othersAt;
		if (old == null)
		{
			others = opened ? over : 0;
			othersAt = now;
		}
		else if (opened)
		{
			// What's left of the player's own sales from last time is theirs; the rest is new from others
			int own = Math.max(0, old.overstockAt(now) - Math.max(0, old.getOthers() - elapsed(old.getOthersAt(), now, restockTicks)));
			others = Math.max(0, over - own);
			othersAt = now;
		}
		else
		{
			others = old.getOthers();
			othersAt = old.getOthersAt();
		}
		if (over == 0)
		{
			return seen.remove(key) != null || cycleSeen;
		}
		if (old != null && old.getOverstock() == over && old.getOthers() == others && old.getCycleAt() == cycleAt
			&& old.overstockAt(now) == over)
		{
			return false;
		}
		seen.put(key, new Seen(shop, item, world, restockTicks, over, now, others, othersAt, cycleAt));
		return true;
	}

	private static int elapsed(long since, long now, int restockTicks)
	{
		return (int) ((now - since) / (restockTicks * TICK_MILLIS));
	}

	/** Drops what's back to normal and cycles too old to trust; returns whether anything was dropped. */
	public boolean prune(long now)
	{
		boolean cyclesDropped = cycles.values().removeIf(t -> now - t > CYCLE_KEPT_MILLIS);
		return seen.values().removeIf(s -> s.getNormalAt() <= now) | cyclesDropped;
	}

	/** Everything still overstocked on worlds the player has seen, soonest back to normal first. */
	public List<Seen> overstocked(String shop, long now)
	{
		List<Seen> out = new ArrayList<>();
		for (Seen s : seen.values())
		{
			if (s.getShop().equals(shop) && s.getNormalAt() > now)
			{
				out.add(s);
			}
		}
		out.sort(Comparator.comparingLong(Seen::getNormalAt));
		return out;
	}

	/** Shop items found overstocked by others on several worlds, which likely aren't worth a trip until they restock. */
	public List<Busy> busy(long now)
	{
		Map<String, List<Long>> gone = new HashMap<>();
		for (Seen s : seen.values())
		{
			if (s.getOthers() > 0 && s.getOthersGoneAt() > now)
			{
				gone.computeIfAbsent(s.getShop() + "|" + s.getItem(), k -> new ArrayList<>()).add(s.getOthersGoneAt());
			}
		}
		List<Busy> out = new ArrayList<>();
		gone.forEach((key, times) ->
		{
			if (times.size() >= BUSY_WORLDS)
			{
				times.sort(Collections.reverseOrder());
				String[] parts = key.split("\\|", 2);
				out.add(new Busy(parts[0], parts[1], times.size(), times.get(BUSY_WORLDS - 1)));
			}
		});
		out.sort(Comparator.comparing(Busy::getShop).thenComparing(Busy::getItem));
		return out;
	}

	public Collection<Seen> all()
	{
		return Collections.unmodifiableCollection(seen.values());
	}

	public Map<String, Long> cycles()
	{
		return Collections.unmodifiableMap(cycles);
	}
}
