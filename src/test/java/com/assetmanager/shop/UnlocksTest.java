package com.assetmanager.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class UnlocksTest
{
	private static Shop shop(Shop.Requirements req, boolean members)
	{
		Shop s = new Shop();
		s.setName("Test");
		s.setMembers(members);
		s.setReq(req);
		return s;
	}

	@Test
	public void listsWhatsMissingInPlainEnglish()
	{
		Shop.Requirements req = new Shop.Requirements();
		req.setSkills(Map.of("HUNTER", 46));
		req.setQuests(Collections.singletonList("CHILDREN_OF_THE_SUN"));
		req.setStarted(Collections.singletonList("THE_GIANT_DWARF"));
		req.setDiaries(Collections.singletonList("KARAMJA_EASY"));
		req.setQuestPoints(50);
		Unlocks u = new Unlocks(Map.of("HUNTER", 40), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
			3, 10, false);

		Unlocks.Missing m = u.missing(shop(req, true), null);

		assertFalse(m.isMet());
		assertEquals(Arrays.asList("Members world", "46 Hunter", "Children Of The Sun", "Start The Giant Dwarf", "Karamja Easy diary",
			"50 quest points"), m.getUnmet());
	}

	@Test
	public void startedOrFinishedBothCountAsStarted()
	{
		Shop.Requirements req = new Shop.Requirements();
		req.setStarted(Collections.singletonList("THE_GIANT_DWARF"));
		Unlocks started = new Unlocks(Collections.emptyMap(), Collections.emptySet(), Set.of("THE_GIANT_DWARF"),
			Collections.emptySet(), 3, 0, true);
		Unlocks finished = new Unlocks(Collections.emptyMap(), Set.of("THE_GIANT_DWARF"), Collections.emptySet(),
			Collections.emptySet(), 3, 0, true);

		assertTrue(started.missing(shop(req, true), null).isMet());
		assertTrue(finished.missing(shop(req, true), null).isMet());
	}

	@Test
	public void unknownProgressMeetsEverythingButKeepsUncheckedNotes()
	{
		Shop.Requirements req = new Shop.Requirements();
		req.setSkills(Map.of("SLAYER", 99));
		req.setOther(Collections.singletonList("Seal of Passage"));

		Unlocks.Missing m = Unlocks.EVERYTHING.missing(shop(req, true), null);

		assertTrue(m.isMet());
		assertEquals(Collections.singletonList("Seal of Passage"), m.getUnchecked());
	}
}
