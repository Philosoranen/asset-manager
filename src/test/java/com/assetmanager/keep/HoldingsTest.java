package com.assetmanager.keep;

import static org.junit.Assert.assertEquals;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class HoldingsTest
{
	private static final int PLATEBODY = 1127;

	private static Map<Integer, Integer> of(int id, int n)
	{
		return Collections.singletonMap(id, n);
	}

	@Test
	public void carriedOnesAreSpareFirst()
	{
		KeepRules rules = new KeepRules();
		rules.set(PLATEBODY, 1);
		// Keeping 1: a banked one is kept, so the one carried (to a shop, say) is spare, with 2 banked
		Holdings h = new Holdings().with(Holdings.Place.INVENTORY, of(PLATEBODY, 1)).with(Holdings.Place.BANK, of(PLATEBODY, 3));
		assertEquals(1, h.spare(Holdings.Place.INVENTORY, PLATEBODY, rules));
		assertEquals(2, h.spare(Holdings.Place.BANK, PLATEBODY, rules));
		assertEquals(3, (int) h.spare(rules).get(PLATEBODY));

		// With none in the bank, the carried one is the kept one
		h = new Holdings().with(Holdings.Place.INVENTORY, of(PLATEBODY, 1)).with(Holdings.Place.BANK, of(PLATEBODY, 0));
		assertEquals(0, h.spare(Holdings.Place.INVENTORY, PLATEBODY, rules));

		// Wearing one counts too, and worn items are never spare
		h = new Holdings().with(Holdings.Place.WORN, of(PLATEBODY, 1)).with(Holdings.Place.INVENTORY, of(PLATEBODY, 2));
		assertEquals(0, h.spare(Holdings.Place.WORN, PLATEBODY, rules));
		assertEquals(2, h.spare(Holdings.Place.INVENTORY, PLATEBODY, rules));
	}

	@Test
	public void seedVaultCountsAfterTheBank()
	{
		int seed = 5295;
		KeepRules rules = new KeepRules();
		rules.set(seed, 10);
		Holdings h = new Holdings().with(Holdings.Place.BANK, of(seed, 4)).with(Holdings.Place.SEED_VAULT, of(seed, 20));
		assertEquals(0, h.spare(Holdings.Place.BANK, seed, rules));
		assertEquals(14, h.spare(Holdings.Place.SEED_VAULT, seed, rules));

		// No rule: everything but what's worn is spare
		Map<Integer, Integer> spare = new Holdings().with(Holdings.Place.INVENTORY, new HashMap<>(of(seed, 3))).spare(new KeepRules());
		assertEquals(3, (int) spare.get(seed));
	}

	@Test
	public void keepAllMeansNothingSpare()
	{
		KeepRules rules = new KeepRules();
		rules.set(1704, KeepRules.KEEP_ALL);
		Holdings h = new Holdings().with(Holdings.Place.BANK, of(1704, 12));
		assertEquals(0, h.spare(Holdings.Place.BANK, 1704, rules));
		assertEquals(Collections.emptyMap(), h.spare(rules));
	}
}
