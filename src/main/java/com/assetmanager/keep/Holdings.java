package com.assetmanager.keep;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where the player's items are, by canonical item id, and which of them are spare after their keep rules.
 * <p>
 * Kept items are counted from what's worn first, then the inventory, the bank and the seed vault, so keeping
 * one rune platebody that's in the inventory leaves every banked one spare. Worn items are never spare. Variants
 * with different doses or charges count together, the fullest kept first.
 */
public class Holdings
{
	@Getter
	@RequiredArgsConstructor
	public enum Place
	{
		WORN("worn"),
		INVENTORY("inventory"),
		BANK("bank"),
		SEED_VAULT("seed vault");

		private final String displayName;
	}

	public static final Holdings NONE = new Holdings();

	private final Map<Place, Map<Integer, Integer>> counts = new EnumMap<>(Place.class);

	/** Adds a place's items; null (not seen yet) adds nothing. */
	public Holdings with(Place place, Map<Integer, Integer> items)
	{
		if (items != null)
		{
			counts.put(place, Collections.unmodifiableMap(new LinkedHashMap<>(items)));
		}
		return this;
	}

	public boolean has(Place place)
	{
		return counts.containsKey(place);
	}

	public int count(Place place, int itemId)
	{
		return counts.getOrDefault(place, Collections.emptyMap()).getOrDefault(itemId, 0);
	}

	/** How many the player has everywhere. */
	public int total(int itemId)
	{
		int n = 0;
		for (Place p : Place.values())
		{
			n += count(p, itemId);
		}
		return n;
	}

	/** Every item held anywhere, worn included. */
	public Set<Integer> allItemIds()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		counts.values().forEach(items -> ids.addAll(items.keySet()));
		return ids;
	}

	/** Every item held anywhere that could be spare (not just worn). */
	public Set<Integer> itemIds()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		counts.forEach((place, items) ->
		{
			if (place != Place.WORN)
			{
				ids.addAll(items.keySet());
			}
		});
		return ids;
	}

	/** How many of an item and its variants the player has everywhere. */
	public int total(int itemId, KeepRules rules)
	{
		int n = 0;
		for (int id : rules.members(itemId))
		{
			n += total(id);
		}
		return n;
	}

	/**
	 * How many at a place are spare. Variants share their rule: the ones with the most doses or charges are kept first,
	 * each from what's worn first, then the inventory, the bank and the seed vault.
	 */
	public int spare(Place place, int itemId, KeepRules rules)
	{
		int keep = rules.get(itemId);
		if (place == Place.WORN || keep == KeepRules.KEEP_ALL)
		{
			return 0;
		}
		int toKeep = keep;
		for (int id : rules.members(itemId))
		{
			for (Place p : Place.values())
			{
				int here = count(p, id);
				int kept = Math.min(here, toKeep);
				toKeep -= kept;
				if (id == itemId && p == place)
				{
					return here - kept;
				}
			}
		}
		return count(place, itemId);
	}

	/** Spare quantity of each item, everywhere together. */
	public Map<Integer, Integer> spare(KeepRules rules)
	{
		Map<Integer, Integer> out = new LinkedHashMap<>();
		for (int id : itemIds())
		{
			int n = 0;
			for (Place p : Place.values())
			{
				n += spare(p, id, rules);
			}
			if (n > 0)
			{
				out.put(id, n);
			}
		}
		return out;
	}

	/** Spare items that aren't in the bank, by where they are, for "3 more in your seed vault" notes. */
	public Map<Place, Integer> spareOutsideBank(int itemId, KeepRules rules)
	{
		Map<Place, Integer> out = new EnumMap<>(Place.class);
		for (Place p : new Place[]{Place.INVENTORY, Place.SEED_VAULT})
		{
			int n = spare(p, itemId, rules);
			if (n > 0)
			{
				out.put(p, n);
			}
		}
		return out;
	}
}
