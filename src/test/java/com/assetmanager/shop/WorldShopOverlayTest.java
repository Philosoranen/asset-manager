package com.assetmanager.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.util.List;
import org.junit.Test;

public class WorldShopOverlayTest
{
	private static final long MINUTE = 60_000;

	/** {@code over} left at time 0, one going every 100 ticks (a minute). */
	private static ShopStock.Seen seen(String item, int over)
	{
		return new ShopStock.Seen("Shop", item, 302, 100, over, 0, 0, 0, 0);
	}

	@Test
	public void howMuchOfTheOverstockIsLeft()
	{
		List<ShopStock.Seen> seen = List.of(seen("a", 6), seen("b", 2));
		assertEquals(1, WorldShopOverlay.left(seen, 0), 0.001);
		// Two minutes on: one of each has gone, then a second of each
		assertEquals(6 / 8.0, WorldShopOverlay.left(seen, MINUTE + 1), 0.001);
		assertEquals(4 / 8.0, WorldShopOverlay.left(seen, 2 * MINUTE + 1), 0.001);
		assertEquals(0, WorldShopOverlay.left(seen, 10 * MINUTE), 0.001);
	}

	@Test
	public void redToGreen()
	{
		Color full = WorldShopOverlay.shade(1);
		Color half = WorldShopOverlay.shade(0.5);
		Color none = WorldShopOverlay.shade(0);
		assertTrue(full.getRed() > full.getGreen());
		// Yellowish halfway, green at the end
		assertTrue(half.getRed() > 150 && half.getGreen() > 150);
		assertTrue(none.getGreen() > none.getRed());
	}
}
