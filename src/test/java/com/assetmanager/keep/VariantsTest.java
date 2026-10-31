package com.assetmanager.keep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class VariantsTest
{
	private static final int PRAYER_4 = 2434;
	private static final int PRAYER_3 = 139;
	private static final int PRAYER_1 = 143;
	private static final int GLORY_6 = 11978;
	private static final int GLORY_1 = 1706;
	private static final int GLORY = 1704;
	private static final int GLORY_T4 = 10354;
	private static final int WHIP = 4151;

	private static Map<Integer, String> names()
	{
		Map<Integer, String> n = new HashMap<>();
		n.put(PRAYER_4, "Prayer potion(4)");
		n.put(PRAYER_3, "Prayer potion(3)");
		n.put(PRAYER_1, "Prayer potion(1)");
		n.put(GLORY_6, "Amulet of glory(6)");
		n.put(GLORY_1, "Amulet of glory(1)");
		n.put(GLORY, "Amulet of glory");
		n.put(GLORY_T4, "Amulet of glory(t4)");
		n.put(WHIP, "Abyssal whip");
		return n;
	}

	@Test
	public void groupsDosesAndCharges()
	{
		Map<Integer, List<Integer>> groups = Variants.group(names());
		assertEquals(Arrays.asList(PRAYER_4, PRAYER_3, PRAYER_1), groups.get(PRAYER_1));
		// Uncharged last
		assertEquals(Arrays.asList(GLORY_6, GLORY_1, GLORY), groups.get(GLORY));
		// Trimmed glories are their own thing, and so is anything with no variants
		assertFalse(groups.containsKey(GLORY_T4));
		assertFalse(groups.containsKey(WHIP));
		assertEquals("Amulet of glory(t)", Variants.base("Amulet of glory(t4)"));
	}

	@Test
	public void variantsShareOneRuleAndTheFullestAreKept()
	{
		KeepRules rules = new KeepRules();
		rules.setVariants(Variants.group(names()));
		// Keep 3 prayer potions, chosen from a (3)
		rules.set(PRAYER_3, 3);
		assertEquals(3, rules.get(PRAYER_1));
		assertEquals(1, rules.asMap().size());

		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(PRAYER_4, 2);
		bank.put(PRAYER_3, 1);
		bank.put(PRAYER_1, 4);
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(PRAYER_3, 1);
		Holdings h = new Holdings().with(Holdings.Place.INVENTORY, inv).with(Holdings.Place.BANK, bank);
		// Kept: both (4)s, then the (3) carried; the banked (3) and the (1)s are spare
		assertEquals(0, h.spare(Holdings.Place.BANK, PRAYER_4, rules));
		assertEquals(0, h.spare(Holdings.Place.INVENTORY, PRAYER_3, rules));
		assertEquals(1, h.spare(Holdings.Place.BANK, PRAYER_3, rules));
		assertEquals(4, h.spare(Holdings.Place.BANK, PRAYER_1, rules));
		assertEquals(8, h.total(PRAYER_1, rules));
		assertEquals(5, rules.canPart(PRAYER_1, 8));
	}

	@Test
	public void separateRulesForVariantsAreMerged()
	{
		KeepRules rules = new KeepRules();
		rules.set(GLORY_6, 1);
		rules.set(GLORY_1, 2);
		assertTrue(rules.setVariants(Variants.group(names())));
		assertEquals(1, rules.asMap().size());
		assertEquals(2, rules.get(GLORY));
	}
}
