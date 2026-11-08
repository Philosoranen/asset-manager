package com.assetmanager.flip;

import java.util.Comparator;

/** How the Flipping tab orders flips. */
public enum FlipSort
{
	/**
	 * Most made for the time it takes: hops, trips, conversations, alchs and waiting for the GE. What a big go makes
	 * doesn't matter if it takes all day.
	 */
	GP_PER_HOUR("GP per hour", Comparator.comparingDouble(FlipRun::getGpPerHour)),
	/** Most coins made in one go, however long it takes. */
	PROFIT("Total profit", Comparator.comparingLong(FlipRun::getProfit)),
	/**
	 * Most made for each coin spent: for Death's coffer, where the coins spent are gone, so getting the most out of
	 * them matters more than the total.
	 */
	RETURN("Return (%)", Comparator.comparingDouble(FlipRun::getReturnPercent));

	private final String displayName;
	/** Least good first. */
	private final Comparator<FlipRun> order;

	FlipSort(String displayName, Comparator<FlipRun> order)
	{
		this.displayName = displayName;
		this.order = order;
	}

	/** Best first, then the next measure, then by name so the order is stable. */
	public Comparator<FlipRun> best()
	{
		Comparator<FlipRun> other = (this == GP_PER_HOUR ? PROFIT : GP_PER_HOUR).order;
		return order.reversed().thenComparing(other.reversed()).thenComparing(FlipRun::getName);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
