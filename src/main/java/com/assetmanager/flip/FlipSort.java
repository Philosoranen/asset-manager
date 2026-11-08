package com.assetmanager.flip;

import java.util.Comparator;

/** How the Flipping tab orders flips. */
public enum FlipSort
{
	/** Most coins made: for flipping to make money. */
	PROFIT("Total profit", Comparator.comparingLong(Flip::getProfit)),
	/**
	 * Most made for each coin spent: for Death's coffer, where the coins spent are gone, so getting the most out of
	 * them matters more than the total.
	 */
	RETURN("Return (%)", Comparator.comparingDouble(Flip::getReturnPercent));

	private final String displayName;
	/** Least good first. */
	private final Comparator<Flip> order;

	FlipSort(String displayName, Comparator<Flip> order)
	{
		this.displayName = displayName;
		this.order = order;
	}

	/** Best first, then the other measure, then by name so the order is stable. */
	public Comparator<Flip> best()
	{
		Comparator<Flip> other = (this == PROFIT ? RETURN : PROFIT).order;
		return order.reversed().thenComparing(other.reversed()).thenComparing(Flip::getItem);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
