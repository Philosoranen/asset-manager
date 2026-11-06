package com.assetmanager.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * What a player has unlocked, for checking shop requirements: real skill levels, quests, diaries. Read on the client
 * thread (see UnlockReader) and then only read, so it can be shared with other threads.
 */
@Value
public class Unlocks
{
	/** For when the player's progress hasn't been read: every requirement counts as met. */
	public static final Unlocks EVERYTHING = new Unlocks(null, null, null, null, Integer.MAX_VALUE, Integer.MAX_VALUE, true);

	/** Real levels by RuneLite skill name; null means everything is met. */
	Map<String, Integer> skills;
	/** Finished quests by RuneLite quest name. */
	Set<String> finished;
	/** Started (but not finished) quests. */
	Set<String> started;
	/** Finished diaries, as AREA_TIER. */
	Set<String> diaries;
	int combat;
	int questPoints;
	boolean members;

	/** What's still needed for a shop item: the shop's and the item's own requirements. */
	public Missing missing(Shop shop, Shop.Item item)
	{
		Missing m = new Missing();
		if (shop.isMembers() && !members)
		{
			m.unmet.add("Membership");
		}
		check(shop.getReq(), m);
		if (item != null)
		{
			check(item.getReq(), m);
		}
		return m;
	}

	private void check(Shop.Requirements req, Missing m)
	{
		if (req == null)
		{
			return;
		}
		m.unchecked.addAll(req.getOther());
		if (skills == null)
		{
			return;
		}
		req.getSkills().forEach((skill, level) ->
		{
			if (skills.getOrDefault(skill, 1) < level)
			{
				m.unmet.add(level + " " + title(skill));
			}
		});
		for (String q : req.getQuests())
		{
			if (!finished.contains(q))
			{
				m.unmet.add(title(q));
			}
		}
		for (String q : req.getStarted())
		{
			if (!finished.contains(q) && !started.contains(q))
			{
				m.unmet.add("Start " + title(q));
			}
		}
		for (String d : req.getDiaries())
		{
			if (!diaries.contains(d))
			{
				m.unmet.add(title(d) + " diary");
			}
		}
		if (combat < req.getCombat())
		{
			m.unmet.add("Combat level " + req.getCombat());
		}
		if (questPoints < req.getQuestPoints())
		{
			m.unmet.add(req.getQuestPoints() + " quest points");
		}
	}

	/** "THE_SLUG_MENACE" -> "The Slug Menace", "KARAMJA_EASY" -> "Karamja Easy". */
	static String title(String enumName)
	{
		StringBuilder sb = new StringBuilder();
		for (String word : enumName.toLowerCase().split("_"))
		{
			if (!word.isEmpty())
			{
				sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return sb.toString();
	}

	/** Requirements a player hasn't met, and ones the plugin can't check. */
	@Value
	public static class Missing
	{
		List<String> unmet = new ArrayList<>();
		List<String> unchecked = new ArrayList<>();

		public boolean isMet()
		{
			return unmet.isEmpty();
		}

		public List<String> getUnmet()
		{
			return Collections.unmodifiableList(unmet);
		}

		public List<String> getUnchecked()
		{
			return Collections.unmodifiableList(unchecked);
		}
	}
}
