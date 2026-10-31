package com.assetmanager.keep;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * How many of each item the player wants to hold on to. Asset Manager only suggests doing something
 * with the rest. Keyed by canonical item id.
 * <p>
 * Variants of an item with different doses or charges (see {@link Variants}) share one rule, stored under one of
 * them: keeping 4 prayer potions keeps any 4, the ones with the most doses first.
 */
public class KeepRules
{
	private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)([kmb])?");

	/** Keep every one of this item: never suggest it. */
	public static final int KEEP_ALL = -1;

	private final Map<Integer, Integer> rules = new LinkedHashMap<>();
	/** Each variant's group, most doses or charges first; items with no variants aren't in it. */
	private Map<Integer, List<Integer>> variants = Collections.emptyMap();

	/** The items sharing a rule with this one, most doses or charges first: just itself if it has no variants. */
	public List<Integer> members(int itemId)
	{
		return variants.getOrDefault(itemId, Collections.singletonList(itemId));
	}

	/** The id an item's rule is stored under. */
	private int ruleId(int itemId)
	{
		for (int id : members(itemId))
		{
			if (rules.containsKey(id))
			{
				return id;
			}
		}
		return itemId;
	}

	/**
	 * Sets which items are variants of each other. Variants that had rules of their own are merged into one, keeping
	 * the most any of them kept.
	 *
	 * @return whether rules were merged
	 */
	public boolean setVariants(Map<Integer, List<Integer>> groups)
	{
		variants = groups;
		boolean merged = false;
		for (List<Integer> group : new HashSet<>(groups.values()))
		{
			int keep = 0;
			int kept = -1;
			for (int id : group)
			{
				Integer k = rules.get(id);
				if (k == null)
				{
					continue;
				}
				if (kept >= 0)
				{
					rules.remove(id);
					merged = true;
				}
				else
				{
					kept = id;
				}
				keep = keep == KEEP_ALL || k == KEEP_ALL ? KEEP_ALL : Math.max(keep, k);
			}
			if (kept >= 0)
			{
				rules.put(kept, keep);
			}
		}
		return merged;
	}

	/** How many to keep, 0 if there's no rule. */
	public int get(int itemId)
	{
		return rules.getOrDefault(ruleId(itemId), 0);
	}

	public boolean has(int itemId)
	{
		return rules.containsKey(ruleId(itemId));
	}

	/** Sets how many to keep; 0 removes the rule. */
	public void set(int itemId, int keep)
	{
		int id = ruleId(itemId);
		if (keep == 0)
		{
			rules.remove(id);
		}
		else
		{
			rules.put(id, keep < 0 ? KEEP_ALL : keep);
		}
	}

	public Map<Integer, Integer> asMap()
	{
		return Collections.unmodifiableMap(rules);
	}

	/** Quantity left over after keeping what the rule says. */
	public int spare(int itemId, int quantity)
	{
		int keep = get(itemId);
		return keep == KEEP_ALL ? 0 : Math.max(0, quantity - keep);
	}

	/**
	 * How many of an item can go while still keeping what the rule says.
	 *
	 * @param owned how many the player has in total, of all its variants
	 */
	public int canPart(int itemId, int owned)
	{
		int keep = get(itemId);
		return keep == KEEP_ALL ? 0 : Math.max(0, owned - keep);
	}

	/** Spare quantities for every item in the map. */
	public Map<Integer, Integer> spare(Map<Integer, Integer> quantities)
	{
		Map<Integer, Integer> out = new LinkedHashMap<>();
		quantities.forEach((id, qty) -> out.put(id, spare(id, qty)));
		return out;
	}

	/** Stored as "id:keep,id:keep", with -1 meaning keep all. */
	public String encode()
	{
		return rules.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
	}

	public static KeepRules decode(String s)
	{
		KeepRules r = new KeepRules();
		if (s == null || s.isEmpty())
		{
			return r;
		}
		for (String entry : s.split(","))
		{
			String[] parts = entry.split(":");
			if (parts.length != 2)
			{
				continue;
			}
			try
			{
				r.set(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
			}
			catch (NumberFormatException ignored)
			{
				// skip a corrupt entry rather than losing the rest
			}
		}
		return r;
	}

	/**
	 * Parses what the player typed: a number, optionally with k, m or b like the game's quantity prompts
	 * ("10k", "1.5m"), or "all". Blank or 0 removes the rule.
	 *
	 * @return the keep amount, or null if it isn't valid
	 */
	public static Integer parseInput(String input)
	{
		String s = input == null ? "" : input.trim().toLowerCase().replace(",", "").replace(" ", "");
		if (s.isEmpty())
		{
			return 0;
		}
		if (s.equals("all") || s.equals("a"))
		{
			return KEEP_ALL;
		}
		Matcher m = AMOUNT.matcher(s);
		if (!m.matches())
		{
			return null;
		}
		BigDecimal n = new BigDecimal(m.group(1));
		if (m.group(2) != null)
		{
			n = n.multiply(BigDecimal.TEN.pow("kmb".indexOf(m.group(2)) * 3 + 3));
		}
		// Whole items only, and no more than a stack can hold
		if (n.stripTrailingZeros().scale() > 0 || n.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0)
		{
			return null;
		}
		return n.intValue();
	}
}
