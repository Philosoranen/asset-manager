package com.assetmanager.value;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How long the player is willing to wait for Grand Exchange offers to fill when flipping. The quicker, the worse the
 * prices: an instant buy pays what sellers are asking, a patient one waits at what sellers have been accepting. More
 * patience also lets more trade, up to the buy limit.
 */
@Getter
@RequiredArgsConstructor
public enum Patience
{
	INSTANT("Instantly", 0.25, GePrice.INSTANT),
	HOUR("Within an hour", 1, GePrice.MIDPOINT),
	FEW_HOURS("A few hours", 4, GePrice.MARKET),
	OVERNIGHT("Overnight", 12, GePrice.MARKET);

	private final String displayName;
	/** Hours of GE trading the player waits for. */
	private final double hours;
	/**
	 * Where in the spread offers go: INSTANT buys at what buyers have been paying and sells at what sellers accept;
	 * MARKET the other way round, waiting for the better price.
	 */
	private final GePrice price;

	/** How many of an item trade on the GE in this time, from how many trade an hour; -1 if unknown. */
	public long tradesIn(long perHour)
	{
		return perHour < 0 ? -1 : (long) Math.floor(perHour * hours);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
