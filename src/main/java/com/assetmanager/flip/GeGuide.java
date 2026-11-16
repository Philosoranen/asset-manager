package com.assetmanager.flip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Value;

/**
 * A flip the player chose to buy on the GE, frozen as it was when they chose it, and how far the buying has got. The
 * GE's 8 offer slots are watched: what a buy offer for one of the flip's items has bought counts while it's in its
 * slot, and once it's collected (the slot empties or holds a new offer) it's added to what's done. So the flip doesn't
 * change as coins go into offers, and what's left to buy is what hasn't been ordered yet.
 */
public final class GeGuide
{
	/** The GE's offer slots. */
	public static final int SLOTS = 8;

	/** One item to buy: how many, at what price each. */
	@Value
	public static class Item
	{
		int itemId;
		/** Lowercase, as the flips have it. */
		String name;
		int quantity;
		long price;
	}

	/** What a GE slot holds. */
	@Value
	public static class Slot
	{
		public static final Slot EMPTY = new Slot(-1, false, false, 0, 0, 0);

		int itemId;
		boolean buy;
		/** Still buying; a finished or cancelled offer has bought all it will. */
		boolean open;
		int bought;
		int total;
		long price;

		/** Whether {@code next} is this offer, further along, rather than a new one. */
		boolean sameOffer(Slot next)
		{
			return next.itemId == itemId && next.buy == buy && next.total == total && next.price == price && next.bought >= bought;
		}
	}

	/** Where the flip cashes out, and the shop when it's a shop. */
	@Getter
	private final Flip.Dest dest;
	/** The shop it's sold to; null if none. */
	@Getter
	private final String shop;
	/** Most profitable first, as the flip listed them. */
	@Getter
	private final List<Item> items;
	/** Bought and collected (or already carried when the flip was chosen), by item. */
	private final Map<Integer, Integer> done;
	private final Slot[] slots = new Slot[SLOTS];

	public GeGuide(Flip.Dest dest, String shop, List<Item> items, Map<Integer, Integer> done)
	{
		this.dest = dest;
		this.shop = shop;
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
		this.done = new HashMap<>(done);
	}

	/** A GE-bought go as a guide; {@code carried} is how many of each item the player already has with them. */
	public static GeGuide of(FlipRun run, Map<Integer, Integer> carried)
	{
		List<Item> items = new ArrayList<>();
		Map<Integer, Integer> done = new HashMap<>();
		for (Flip f : run.getFlips())
		{
			items.add(new Item(f.getItemId(), f.getItem(), f.getQuantity(), f.getFirstCost()));
			int have = Math.min(f.getQuantity(), carried.getOrDefault(f.getItemId(), 0));
			if (have > 0)
			{
				done.put(f.getItemId(), have);
			}
		}
		return new GeGuide(run.getDest(), run.getShop() == null ? null : run.getShop().getName(), items, done);
	}

	/** The guide as saved between sessions; the slots are seen again on login. */
	public static class Saved
	{
		Flip.Dest dest;
		String shop;
		List<Item> items;
		Map<Integer, Integer> done;
	}

	public Saved save()
	{
		Saved s = new Saved();
		s.dest = dest;
		s.shop = shop;
		s.items = new ArrayList<>(items);
		s.done = new HashMap<>(done);
		return s;
	}

	/** A saved guide; null if it's missing anything. */
	public static GeGuide load(Saved s)
	{
		if (s == null || s.dest == null || s.items == null || s.items.isEmpty())
		{
			return null;
		}
		return new GeGuide(s.dest, s.shop, s.items, s.done == null ? Collections.emptyMap() : s.done);
	}

	/**
	 * A slot's offer changed. With {@code count} off (the burst of offers sent on login, or the slots as they were when
	 * the guide began) it's only noted, so nothing is counted twice.
	 */
	public void setSlot(int slot, Slot now, boolean count)
	{
		if (slot < 0 || slot >= SLOTS)
		{
			return;
		}
		Slot was = slots[slot];
		if (count && was != null && was.buy && item(was.itemId) != null && !was.sameOffer(now))
		{
			// Collected, or replaced by a new offer: what it bought is in the inventory or bank now
			done.merge(was.itemId, was.bought, Integer::sum);
		}
		slots[slot] = now;
	}

	/** The slot's offer as last seen; null if not seen yet. */
	public Slot getSlot(int slot)
	{
		return slots[slot];
	}

	/** The planned item, or null if it isn't one of the flip's. */
	public Item item(int itemId)
	{
		for (Item i : items)
		{
			if (i.itemId == itemId)
			{
				return i;
			}
		}
		return null;
	}

	/** How many have been bought, collected or not. */
	public int bought(int itemId)
	{
		int n = done.getOrDefault(itemId, 0);
		for (Slot s : slots)
		{
			if (s != null && s.buy && s.itemId == itemId)
			{
				n += s.bought;
			}
		}
		return n;
	}

	/** How many have been bought or are in an open buy offer. */
	public int ordered(int itemId)
	{
		int n = done.getOrDefault(itemId, 0);
		for (Slot s : slots)
		{
			if (s != null && s.buy && s.itemId == itemId)
			{
				n += s.open ? s.total : s.bought;
			}
		}
		return n;
	}

	/** How many still need a buy offer. */
	public int toOrder(int itemId)
	{
		Item i = item(itemId);
		return i == null ? 0 : Math.max(0, i.quantity - ordered(itemId));
	}

	/** The next item to put a buy offer in for, most profitable first; null when everything has been ordered. */
	public Item next()
	{
		for (Item i : items)
		{
			if (toOrder(i.itemId) > 0)
			{
				return i;
			}
		}
		return null;
	}

	public boolean isAllBought()
	{
		for (Item i : items)
		{
			if (bought(i.itemId) < i.quantity)
			{
				return false;
			}
		}
		return true;
	}

	/** A slot holding a finished buy of one of the flip's items, waiting to be collected; -1 if none. */
	public int toCollect()
	{
		for (int n = 0; n < SLOTS; n++)
		{
			Slot s = slots[n];
			if (s != null && s.buy && !s.open && s.bought > 0 && item(s.itemId) != null)
			{
				return n;
			}
		}
		return -1;
	}

	/** An empty slot to put the next offer in; -1 if none is free or the slots aren't known yet. */
	public int emptySlot()
	{
		for (int n = 0; n < SLOTS; n++)
		{
			if (slots[n] != null && slots[n].itemId <= 0)
			{
				return n;
			}
		}
		return -1;
	}

	/** What the collected items cost, at the flip's prices: coins already spent on it. */
	public long spentOnCollected()
	{
		long total = 0;
		for (Item i : items)
		{
			total += done.getOrDefault(i.itemId, 0) * i.price;
		}
		return total;
	}

	/** The name shown for the guide: the shop, or the first item. */
	public String getName()
	{
		return shop != null ? shop : items.isEmpty() ? "" : items.get(0).name;
	}
}
