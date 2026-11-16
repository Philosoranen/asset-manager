package com.assetmanager.shop;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShopSlotsTest
{
	private static Shop stocking(int items)
	{
		Map<String, Shop.Item> stock = new HashMap<>();
		for (int i = 0; i < items; i++)
		{
			stock.put("item " + i, new Shop.Item());
		}
		Shop s = new Shop();
		s.setItems(stock);
		return s;
	}

	@Test
	public void stockedItemsNeedNoSlot()
	{
		Shop s = stocking(25);
		assertFalse(s.takesSlot("Item 3"));
		assertTrue(s.takesSlot("rune scimitar"));
		assertEquals(15, s.freeSlots());
	}

	@Test
	public void worldsCoverEveryItemsSlots()
	{
		Shop s = stocking(25);
		// 20 items needing 200 worlds each, 15 to a world
		assertEquals(267, s.worldsFor(200, 20 * 200));
		// Few enough to fit: the item needing the most decides
		assertEquals(200, s.worldsFor(200, 10 * 200));
	}

	@Test
	public void fullShopStillSellsOneAWorld()
	{
		assertEquals(1, stocking(45).freeSlots());
	}
}
