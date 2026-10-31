package com.assetmanager.value;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Which live Grand Exchange price to value items at when selling. */
@Getter
@RequiredArgsConstructor
public enum GePrice
{
	/** The price buyers have been paying: what you'll usually get by listing and waiting a little. */
	MARKET("List at market price", "live market price"),
	MIDPOINT("Midpoint", "live midpoint price"),
	/** The price sellers have been accepting: what you'll get by selling right away. */
	INSTANT("Sell instantly", "live instant-sell price");

	/**
	 * When buyers pay more than this many times what sellers accept, the item barely trades and the buy price
	 * is likely a one-off, so the sell price is used instead.
	 */
	static final int MAX_SPREAD = 2;

	private final String displayName;
	/** How the price is described in tooltips. */
	private final String description;

	/**
	 * @param high the recent instant-buy price, or 0 if unknown
	 * @param low  the recent instant-sell price, or 0 if unknown
	 * @return the price to use, or 0 if neither is known
	 */
	public int pick(int high, int low)
	{
		if (high <= 0 || low <= 0)
		{
			return Math.max(high, low);
		}
		if ((long) high > (long) low * MAX_SPREAD)
		{
			return low;
		}
		switch (this)
		{
			case MARKET:
				return high;
			case MIDPOINT:
				return (int) (((long) high + low) / 2);
			default:
				return low;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
