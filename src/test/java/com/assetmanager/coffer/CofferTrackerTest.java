package com.assetmanager.coffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import org.junit.Test;

public class CofferTrackerTest
{
	// Text of the real coffer window (September 2026) with an empty coffer
	private static final String TITLE = "Sacrifice items to Death's Coffer";
	private static final String INFO = "You may sacrifice unwanted items here. Their value will go into Death's Coffer,"
		+ " a pot of money from which you can pay item reclamation fees.";

	@Test
	public void emptyCofferIsZero()
	{
		assertEquals(Long.valueOf(0), CofferTracker.findBalance(Arrays.asList(TITLE, "Coffer: Empty", INFO, "1", "5", "X", "All")));
	}

	@Test
	public void readsAmounts()
	{
		assertEquals(Long.valueOf(1_234_567), CofferTracker.findBalance(Arrays.asList(TITLE, "Coffer: 1,234,567 coins")));
		assertEquals(Long.valueOf(2_500), CofferTracker.findBalance(Arrays.asList("Coffer: 2,500")));
		assertEquals(Long.valueOf(1_500_000), CofferTracker.findBalance(Arrays.asList("Coffer: 1.5M")));
		assertEquals(Long.valueOf(250_000), CofferTracker.findBalance(Arrays.asList("Coffer: 250K")));
	}

	@Test
	public void ignoresOtherText()
	{
		assertNull(CofferTracker.findBalance(Arrays.asList(TITLE, INFO, "Items must be worth at least 10,000 coins")));
	}

	@Test
	public void chatReadsBalanceNotCharge()
	{
		assertEquals(Long.valueOf(8_784_598), CofferTracker.chatBalance(
			"Death charges you 500,000 x Coins. You have 8,784,598 x Coins left in Death's Coffer."));
	}
}
