package com.assetmanager.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class ShopStockTest
{
	private static final String SEDDU = "Seddu's Adventurer's Store";
	private static final String SKIRT = "rune plateskirt";
	/** Rune plateskirts: normal stock 1, one back every 12,000 ticks (2 hours). */
	private static final int RESTOCK = 12_000;
	private static final long HOUR = 3_600_000;

	@Test
	public void timesHowLongTheStockTakesToGetBackToNormal()
	{
		ShopStock stock = new ShopStock();
		// Opened on world 420 with 4 in stock: 3 over normal, so up to 6 hours
		assertTrue(stock.observe(SEDDU, SKIRT, 420, 4, 1, RESTOCK, true, false, 0));
		ShopStock.Seen s = stock.overstocked(SEDDU, 0).get(0);
		assertEquals(3, s.getOverstock());
		assertEquals(6 * HOUR, s.getNormalAt());
		// They were left by others
		assertEquals(3, s.getOthers());

		// Nothing to time once it's back to normal
		assertTrue(stock.observe(SEDDU, SKIRT, 420, 1, 1, RESTOCK, false, false, 1));
		assertTrue(stock.overstocked(SEDDU, 1).isEmpty());
	}

	@Test
	public void thePlayersOwnSalesArentOthers()
	{
		ShopStock stock = new ShopStock();
		stock.observe(SEDDU, SKIRT, 420, 1, 1, RESTOCK, true, false, 0);
		// Sold 2 there
		stock.observe(SEDDU, SKIRT, 420, 3, 1, RESTOCK, false, false, 1);
		ShopStock.Seen s = stock.overstocked(SEDDU, 1).get(0);
		assertEquals(2, s.getOverstock());
		assertEquals(0, s.getOthers());
		// Back 2 hours later: one has gone, and the one left is still theirs
		assertTrue(stock.observe(SEDDU, SKIRT, 420, 2, 1, RESTOCK, true, false, 2 * HOUR + 1));
		assertEquals(0, stock.overstocked(SEDDU, 2 * HOUR + 1).get(0).getOthers());
		// ...but if someone else has sold 2 more since, those are theirs
		stock.observe(SEDDU, SKIRT, 420, 4, 1, RESTOCK, true, false, 2 * HOUR + 2);
		assertEquals(2, stock.overstocked(SEDDU, 2 * HOUR + 2).get(0).getOthers());
	}

	@Test
	public void busyOnceOthersHaveOverstockedSeveralWorlds()
	{
		ShopStock stock = new ShopStock();
		stock.observe(SEDDU, SKIRT, 301, 2, 1, RESTOCK, true, false, 0);
		assertTrue(stock.busy(0).isEmpty());
		stock.observe(SEDDU, SKIRT, 302, 4, 1, RESTOCK, true, false, 0);
		stock.observe(SEDDU, SKIRT, 303, 3, 1, RESTOCK, true, false, 0);
		List<ShopStock.Busy> busy = stock.busy(0);
		assertEquals(1, busy.size());
		assertEquals(3, busy.get(0).getWorlds());
		// Until only one world is still overstocked: world 303 clears after 4 hours, leaving 302
		assertEquals(4 * HOUR, busy.get(0).getUntil());
		assertTrue(stock.busy(4 * HOUR).isEmpty());
	}

	@Test
	public void forgetsWhatsBackToNormal()
	{
		ShopStock stock = new ShopStock();
		stock.observe(SEDDU, SKIRT, 301, 2, 1, RESTOCK, true, false, 0);
		ShopStock reloaded = new ShopStock(stock.all(), stock.cycles(), 2 * HOUR);
		assertTrue(reloaded.all().isEmpty());
	}

	@Test
	public void learnsTheCycleFromARestockItSees()
	{
		ShopStock stock = new ShopStock();
		stock.observe(SEDDU, SKIRT, 420, 4, 1, RESTOCK, true, false, 0);
		assertFalse(stock.overstocked(SEDDU, 0).get(0).isExact());
		// Half an hour later one goes while the player watches: the next goes 2 hours after that
		stock.observe(SEDDU, SKIRT, 420, 3, 1, RESTOCK, false, false, HOUR / 2);
		ShopStock.Seen s = stock.overstocked(SEDDU, HOUR / 2).get(0);
		assertTrue(s.isExact());
		assertEquals(HOUR / 2 + 4 * HOUR, s.getNormalAt());

		// Sold 1 just before the next cycle ends: it's gone within a minute, not in 2 hours
		stock.observe(SEDDU, SKIRT, 420, 1, 1, RESTOCK, false, false, HOUR / 2 + 5 * HOUR);
		stock.observe(SEDDU, SKIRT, 420, 2, 1, RESTOCK, true, false, HOUR / 2 + 6 * HOUR - 30_000);
		assertEquals(HOUR / 2 + 6 * HOUR, stock.overstocked(SEDDU, HOUR / 2 + 6 * HOUR - 30_000).get(0).getNormalAt());
	}

	@Test
	public void buyingOneIsntARestock()
	{
		ShopStock stock = new ShopStock();
		stock.observe(SEDDU, SKIRT, 420, 4, 1, RESTOCK, true, false, 0);
		stock.observe(SEDDU, SKIRT, 420, 3, 1, RESTOCK, false, true, HOUR / 2);
		assertFalse(stock.overstocked(SEDDU, HOUR / 2).get(0).isExact());
	}
}
