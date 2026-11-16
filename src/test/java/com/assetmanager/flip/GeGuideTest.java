package com.assetmanager.flip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class GeGuideTest
{
	private static final int RUNE = 1;
	private static final int ADDY = 2;

	private static GeGuide guide(Map<Integer, Integer> done)
	{
		GeGuide g = new GeGuide(Flip.Dest.SHOP, "Bob's Brilliant Axes", Arrays.asList(
			new GeGuide.Item(RUNE, "rune axe", 100, 5000),
			new GeGuide.Item(ADDY, "adamant axe", 50, 1000)), done);
		for (int n = 0; n < GeGuide.SLOTS; n++)
		{
			g.setSlot(n, GeGuide.Slot.EMPTY, false);
		}
		return g;
	}

	private static GeGuide.Slot buying(int item, int bought, int total, long price)
	{
		return new GeGuide.Slot(item, true, true, bought, total, price);
	}

	private static GeGuide.Slot bought(int item, int bought, int total, long price)
	{
		return new GeGuide.Slot(item, true, false, bought, total, price);
	}

	@Test
	public void ordersEachItemInTurn()
	{
		GeGuide g = guide(Collections.emptyMap());
		assertEquals(RUNE, g.next().getItemId());
		assertEquals(100, g.toOrder(RUNE));
		assertEquals(0, g.emptySlot());

		g.setSlot(0, buying(RUNE, 0, 100, 5000), true);
		assertEquals(0, g.toOrder(RUNE));
		assertEquals(0, g.bought(RUNE));
		assertEquals(ADDY, g.next().getItemId());
		assertEquals(1, g.emptySlot());
	}

	@Test
	public void partOrderedLeavesTheRest()
	{
		GeGuide g = guide(Collections.emptyMap());
		g.setSlot(0, buying(RUNE, 10, 40, 5000), true);
		assertEquals(60, g.toOrder(RUNE));
		assertEquals(10, g.bought(RUNE));
	}

	@Test
	public void collectingKeepsWhatWasBought()
	{
		GeGuide g = guide(Collections.emptyMap());
		g.setSlot(0, buying(RUNE, 0, 100, 5000), true);
		g.setSlot(0, buying(RUNE, 60, 100, 5000), true);
		g.setSlot(0, bought(RUNE, 100, 100, 5000), true);
		assertEquals(0, g.toCollect());
		g.setSlot(0, GeGuide.Slot.EMPTY, true);
		assertEquals(100, g.bought(RUNE));
		assertEquals(0, g.toOrder(RUNE));
		assertEquals(-1, g.toCollect());
		assertEquals(100 * 5000L, g.spentOnCollected());
	}

	@Test
	public void cancellingFreesTheRest()
	{
		GeGuide g = guide(Collections.emptyMap());
		g.setSlot(0, buying(RUNE, 30, 100, 5000), true);
		g.setSlot(0, bought(RUNE, 30, 100, 5000), true);
		assertEquals(70, g.toOrder(RUNE));
		g.setSlot(0, GeGuide.Slot.EMPTY, true);
		assertEquals(30, g.bought(RUNE));
		assertEquals(70, g.toOrder(RUNE));
	}

	@Test
	public void newOfferInTheSameSlotCountsTheOldOne()
	{
		GeGuide g = guide(Collections.emptyMap());
		g.setSlot(0, bought(RUNE, 40, 40, 5000), true);
		// Collected and a new offer put in before the slot was seen empty
		g.setSlot(0, buying(RUNE, 0, 60, 5000), true);
		assertEquals(40, g.bought(RUNE));
		assertEquals(0, g.toOrder(RUNE));
	}

	@Test
	public void slotsSeenAgainAreNotCountedTwice()
	{
		GeGuide g = guide(Collections.emptyMap());
		g.setSlot(0, bought(RUNE, 40, 40, 5000), true);
		// The burst on login: the slot looks empty, then is sent again
		g.setSlot(0, GeGuide.Slot.EMPTY, false);
		g.setSlot(0, bought(RUNE, 40, 40, 5000), false);
		assertEquals(40, g.bought(RUNE));
	}

	@Test
	public void otherItemsAndSellsAreIgnored()
	{
		GeGuide g = guide(Collections.emptyMap());
		g.setSlot(0, buying(99, 5, 10, 1), true);
		g.setSlot(1, new GeGuide.Slot(RUNE, false, true, 5, 10, 6000), true);
		g.setSlot(0, GeGuide.Slot.EMPTY, true);
		g.setSlot(1, GeGuide.Slot.EMPTY, true);
		assertEquals(0, g.bought(RUNE));
		assertEquals(100, g.toOrder(RUNE));
	}

	@Test
	public void carriedItemsCountWhenStarting()
	{
		GeGuide g = guide(Map.of(RUNE, 100, ADDY, 50));
		assertNull(g.next());
		assertTrue(g.isAllBought());
	}

	@Test
	public void savesAndLoads()
	{
		GeGuide g = guide(Collections.emptyMap());
		g.setSlot(0, bought(RUNE, 100, 100, 5000), true);
		g.setSlot(0, GeGuide.Slot.EMPTY, true);
		GeGuide loaded = GeGuide.load(g.save());
		assertEquals(100, loaded.bought(RUNE));
		assertEquals(ADDY, loaded.next().getItemId());
		assertFalse(loaded.isAllBought());
		assertEquals("Bob's Brilliant Axes", loaded.getName());
	}
}
