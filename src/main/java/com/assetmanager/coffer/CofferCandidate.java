package com.assetmanager.coffer;

import com.assetmanager.value.ItemValues;
import lombok.Value;

/** A banked item that could be sacrificed to Death's coffer, compared against its next best use. */
@Value
public class CofferCandidate
{
	ItemValues values;
	int quantity;
	/** Coins added to the coffer per unit sacrificed. */
	long cofferUnit;
	/** Coins per unit from the best alternative (GE sale, alching, ...). */
	long alternativeUnit;
	/** Short label for the alternative, e.g. "GE sell" or "High alch". */
	String alternativeLabel;

	public long getCofferTotal()
	{
		return cofferUnit * quantity;
	}

	/**
	 * How much more (as a fraction) the coffer gives than the best alternative.
	 * Items with no alternative value are treated as infinitely better.
	 */
	public double getGainRatio()
	{
		if (alternativeUnit <= 0)
		{
			return Double.POSITIVE_INFINITY;
		}
		return (double) (cofferUnit - alternativeUnit) / alternativeUnit;
	}
}
