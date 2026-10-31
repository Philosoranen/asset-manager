package com.assetmanager.keep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Items that are the same thing with a different number of doses or charges, like Prayer potion(4) to (1), or
 * Amulet of glory(6) to the uncharged Amulet of glory. Trimmed and other lettered versions, like Amulet of glory(t6),
 * are their own group.
 */
public final class Variants
{
	/** "Prayer potion(4)", "Amulet of glory(t6)", "Black mask (10)". */
	private static final Pattern NUMBERED = Pattern.compile("^(.*?)\\s*\\(([a-z]*)(\\d+)\\)$", Pattern.CASE_INSENSITIVE);

	private Variants()
	{
	}

	/** The name shared by every variant: "Prayer potion", "Amulet of glory(t)". */
	public static String base(String name)
	{
		Matcher m = NUMBERED.matcher(name.trim());
		if (!m.matches())
		{
			return name.trim();
		}
		return m.group(2).isEmpty() ? m.group(1) : m.group(1) + "(" + m.group(2) + ")";
	}

	/** Doses or charges, from the name; 0 for none (an uncharged amulet). */
	static int number(String name)
	{
		Matcher m = NUMBERED.matcher(name.trim());
		return m.matches() ? Integer.parseInt(m.group(3)) : 0;
	}

	/**
	 * Groups items by their shared name. Each group lists its items with the most doses or charges first, which are the
	 * ones kept first; items with no other variants aren't included.
	 *
	 * @param names item names by id
	 */
	public static Map<Integer, List<Integer>> group(Map<Integer, String> names)
	{
		Map<String, List<Integer>> byBase = new HashMap<>();
		names.forEach((id, name) -> byBase.computeIfAbsent(base(name).toLowerCase(), k -> new ArrayList<>()).add(id));
		Map<Integer, List<Integer>> out = new HashMap<>();
		for (List<Integer> ids : byBase.values())
		{
			if (ids.size() < 2)
			{
				continue;
			}
			ids.sort(Comparator.comparingInt((Integer id) -> number(names.get(id))).reversed().thenComparing(id -> id));
			List<Integer> members = Collections.unmodifiableList(ids);
			for (int id : ids)
			{
				out.put(id, members);
			}
		}
		return out;
	}
}
