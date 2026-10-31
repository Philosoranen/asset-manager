package com.assetmanager.flip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ShopFlipAdvisorTest
{
	@Test
	public void buysUntilThePriceRiseEatsTheProfit()
	{
		// Value 1000, shop sells at 100% at its normal stock of 10, +2% per item bought below that.
		// GE pays 1,050 after tax: profitable while the price is under 1,050, i.e. 1000, 1020, 1040.
		ShopFlip f = ShopFlipAdvisor.plan(1000, 10, 10, 1000, 20, 1050, 1, 28, Long.MAX_VALUE);
		assertEquals(3, f.getQuantity());
		assertEquals(1000 + 1020 + 1040, f.getCost());
		assertEquals(3 * 1050 - (1000 + 1020 + 1040), f.getProfit());
		assertEquals(ShopFlip.Limit.PRICE, f.getLimit());
		assertEquals(1040, f.getLastPrice());
	}

	@Test
	public void startsDearerWhenStockIsAlreadyLow()
	{
		// 8 of 10 in stock: the first costs 104%
		assertEquals(1040, ShopFlipAdvisor.plan(1000, 8, 10, 1000, 20, 5000, 1, 28, Long.MAX_VALUE).getFirstPrice());
	}

	@Test
	public void limitedByStockSpaceAndCoins()
	{
		assertEquals(ShopFlip.Limit.STOCK, ShopFlipAdvisor.plan(100, 5, 5, 1000, 0, 500, 1, 28, Long.MAX_VALUE).getLimit());
		ShopFlip space = ShopFlipAdvisor.plan(100, 50, 50, 1000, 0, 500, 1, 3, Long.MAX_VALUE);
		assertEquals(3, space.getQuantity());
		assertEquals(ShopFlip.Limit.SPACE, space.getLimit());
		ShopFlip coins = ShopFlipAdvisor.plan(100, 50, 50, 1000, 0, 500, 1, 28, 250);
		assertEquals(2, coins.getQuantity());
		assertEquals(ShopFlip.Limit.COINS, coins.getLimit());
	}

	@Test
	public void infiniteStockNeverRunsOut()
	{
		ShopFlip f = ShopFlipAdvisor.plan(100, 0, -1, 1000, 0, 500, 1, 1000, Long.MAX_VALUE);
		assertEquals(1000, f.getQuantity());
		assertEquals(ShopFlip.Limit.SPACE, f.getLimit());
	}

	@Test
	public void nothingWhenTheGePaysLess()
	{
		assertNull(ShopFlipAdvisor.plan(1000, 10, 10, 1000, 20, 990, 1, 28, Long.MAX_VALUE));
	}

	@Test
	public void neverBuysMoreThanTheGeTradesInAnHour()
	{
		ShopFlip f = ShopFlipAdvisor.plan(100, 50, 50, 1000, 0, 500, 1, 28, Long.MAX_VALUE, 9);
		assertEquals(9, f.getQuantity());
		assertEquals(ShopFlip.Limit.VOLUME, f.getLimit());
		// Nothing traded: nothing to flip
		assertEquals(null, ShopFlipAdvisor.plan(100, 50, 50, 1000, 0, 500, 1, 28, Long.MAX_VALUE, 0));
	}
}
