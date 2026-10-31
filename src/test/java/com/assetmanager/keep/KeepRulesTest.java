package com.assetmanager.keep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class KeepRulesTest
{
	@Test
	public void spareQuantities()
	{
		KeepRules r = new KeepRules();
		r.set(1127, 1);                    // keep one rune platebody for a clue
		r.set(1712, KeepRules.KEEP_ALL);   // collect glories
		assertEquals(4, r.spare(1127, 5));
		assertEquals(0, r.spare(1127, 1));
		assertEquals(0, r.spare(1712, 20));
		assertEquals(7, r.spare(999, 7));
	}

	@Test
	public void encodesAndDecodes()
	{
		KeepRules r = new KeepRules();
		r.set(1127, 1);
		r.set(1712, KeepRules.KEEP_ALL);
		KeepRules back = KeepRules.decode(r.encode());
		assertEquals(1, back.get(1127));
		assertEquals(KeepRules.KEEP_ALL, back.get(1712));
		assertEquals(0, KeepRules.decode("garbage,5:x,7:2").get(5));
		assertEquals(2, KeepRules.decode("garbage,5:x,7:2").get(7));
	}

	@Test
	public void parsesInput()
	{
		assertEquals(Integer.valueOf(3), KeepRules.parseInput(" 3 "));
		assertEquals(Integer.valueOf(1_000), KeepRules.parseInput("1,000"));
		assertEquals(Integer.valueOf(KeepRules.KEEP_ALL), KeepRules.parseInput("All"));
		assertEquals(Integer.valueOf(0), KeepRules.parseInput(""));
		assertNull(KeepRules.parseInput("lots"));
		assertNull(KeepRules.parseInput("-2"));
	}

	@Test
	public void zeroRemovesRule()
	{
		KeepRules r = new KeepRules();
		r.set(1127, 1);
		r.set(1127, 0);
		assertEquals(false, r.has(1127));
	}

	@Test
	public void canPartWithOnlyTheSpares()
	{
		KeepRules r = new KeepRules();
		r.set(1127, 1);
		r.set(1704, KeepRules.KEEP_ALL);
		// Keeping 1 rune platebody: with 1 owned none can go, with 4 owned three can
		assertEquals(0, r.canPart(1127, 1));
		assertEquals(3, r.canPart(1127, 4));
		// Keeping every glory
		assertEquals(0, r.canPart(1704, 20));
		// No rule
		assertEquals(5, r.canPart(4151, 5));
	}

	@Test
	public void parsesAmountsLikeTheGame()
	{
		assertEquals(Integer.valueOf(5), KeepRules.parseInput("5"));
		assertEquals(Integer.valueOf(10_000), KeepRules.parseInput("10k"));
		assertEquals(Integer.valueOf(10_000), KeepRules.parseInput("10K"));
		assertEquals(Integer.valueOf(1_500_000), KeepRules.parseInput("1.5m"));
		assertEquals(Integer.valueOf(2_000_000_000), KeepRules.parseInput("2b"));
		assertEquals(Integer.valueOf(1_000), KeepRules.parseInput("1,000"));
		assertEquals(Integer.valueOf(KeepRules.KEEP_ALL), KeepRules.parseInput("all"));
		assertEquals(Integer.valueOf(0), KeepRules.parseInput(""));
		// Not whole items, too many, or not a number
		assertEquals(null, KeepRules.parseInput("1.5"));
		assertEquals(null, KeepRules.parseInput("3b"));
		assertEquals(null, KeepRules.parseInput("lots"));
	}
}
