package com.assetmanager.flip;

/**
 * Works out how many of a shop's item to buy to resell on the Grand Exchange.
 * <p>
 * A shop charges {@code value * (sellRate + delta * (normalStock - stock))}: its price rises by delta for each
 * item bought below its normal stock. Buying stops when the next one wouldn't make at least the minimum profit,
 * or the shop, inventory space or coins run out.
 */
public final class ShopFlipAdvisor
{
	/** Shops never charge less than 10% of an item's value. */
	private static final int MIN_RATE = 100;

	private ShopFlipAdvisor()
	{
	}

	/**
	 * @param value         the item's store value
	 * @param stock         how many the shop has right now
	 * @param normalStock   the shop's normal stock, or -1 if it never runs out
	 * @param sellRate      what the shop charges at normal stock, in tenths of a percent
	 * @param delta         change in rate per item of stock, in tenths of a percent
	 * @param geUnit        coins from selling one on the GE, after tax
	 * @param minProfitEach only buy while each one makes at least this much
	 * @param maxSpace      how many fit in the inventory
	 * @param coins         coins available to spend
	 * @return the flip, or null if not even one is worth buying
	 */
	public static ShopFlip plan(int value, int stock, int normalStock, int sellRate, int delta, long geUnit,
		int minProfitEach, int maxSpace, long coins)
	{
		return plan(value, stock, normalStock, sellRate, delta, geUnit, minProfitEach, maxSpace, coins, Long.MAX_VALUE);
	}

	/**
	 * As above, buying no more than the GE trades in an hour ({@code geVolume}, Long.MAX_VALUE if unknown), so they
	 * can actually be sold. Items that trade less than once an hour aren't worth buying at all.
	 */
	public static ShopFlip plan(int value, int stock, int normalStock, int sellRate, int delta, long geUnit,
		int minProfitEach, int maxSpace, long coins, long geVolume)
	{
		boolean infinite = normalStock < 0;
		int bought = 0;
		long cost = 0;
		long first = 0;
		long last = 0;
		ShopFlip.Limit limit;
		while (true)
		{
			if (!infinite && bought >= stock)
			{
				limit = ShopFlip.Limit.STOCK;
				break;
			}
			if (bought >= maxSpace)
			{
				limit = ShopFlip.Limit.SPACE;
				break;
			}
			if (bought >= geVolume)
			{
				limit = ShopFlip.Limit.VOLUME;
				break;
			}
			long price = price(value, infinite ? sellRate : sellRate + delta * (normalStock - (stock - bought)));
			if (geUnit - price < Math.max(1, minProfitEach))
			{
				limit = ShopFlip.Limit.PRICE;
				break;
			}
			if (cost + price > coins)
			{
				limit = ShopFlip.Limit.COINS;
				break;
			}
			if (bought == 0)
			{
				first = price;
			}
			last = price;
			cost += price;
			bought++;
		}
		if (bought == 0)
		{
			return null;
		}
		return new ShopFlip(bought, cost, geUnit * bought, first, last, geUnit, limit);
	}

	static long price(int value, int rate)
	{
		return (long) value * Math.max(MIN_RATE, rate) / 1000;
	}
}
