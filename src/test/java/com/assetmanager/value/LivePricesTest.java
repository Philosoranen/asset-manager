package com.assetmanager.value;

import static org.junit.Assert.assertEquals;
import java.util.Map;
import org.junit.Test;

public class LivePricesTest
{
	private static final LivePrices.Volume HOUR = new LivePrices.Volume(30, 20, 0, 0);

	@Test
	public void unknownUntilLoaded()
	{
		assertEquals(-1, LivePrices.tradesPerHour(null, false, null, 1));
	}

	@Test
	public void lastHourUntilTheDayLoads()
	{
		assertEquals(50, LivePrices.tradesPerHour(HOUR, true, null, 1));
		// The wiki leaves out items nobody traded: none sold, not unknown
		assertEquals(0, LivePrices.tradesPerHour(null, true, null, 1));
	}

	@Test
	public void averagedOverTheDay()
	{
		assertEquals(10, LivePrices.tradesPerHour(HOUR, true, Map.of(1, 240L), 1));
		// Under one an hour rounds down to none
		assertEquals(0, LivePrices.tradesPerHour(HOUR, true, Map.of(1, 20L), 1));
		assertEquals(0, LivePrices.tradesPerHour(HOUR, true, Map.of(2, 240L), 1));
	}
}
