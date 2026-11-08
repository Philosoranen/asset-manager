package com.assetmanager.value;

import static org.junit.Assert.assertEquals;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ItemValuesTest
{
	@Test
	public void theCofferTakesTradeablesWorthEnoughButNotBonds()
	{
		assertEquals(105_000, new ItemValues(1, "Thing", 100_000, 100_000, 60_000, 100_000, true).getCofferValue());
		assertEquals(0, new ItemValues(2, "Cheap", 9_000, 9_000, 100, 9_000, true).getCofferValue());
		assertEquals(0, new ItemValues(3, "Quest thing", 100_000, 0, 0, 100_000, false).getCofferValue());
		// "You cannot trade that item to Death."
		assertEquals(0, new ItemValues(ItemID.OSRS_BOND, "Old school bond", 10_000_000, 10_000_000, 0, 0, true).getCofferValue());
	}
}
