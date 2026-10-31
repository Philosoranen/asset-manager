package com.assetmanager.value;

import lombok.Value;

/**
 * Snapshot of everything we know about the worth of a single unit of an item.
 * All prices are per unit, in coins.
 */
@Value
public class ItemValues
{
	/** Death's coffer only accepts items whose guide price is at least this much. */
	public static final int COFFER_MIN_GUIDE_PRICE = 10_000;
	/** Grand Exchange sale tax, in percent. */
	public static final int GE_TAX_PERCENT = 2;
	/** Maximum GE tax charged on a single item. */
	public static final long GE_TAX_CAP = 5_000_000;
	/** Items sold for less than this each aren't taxed. */
	public static final int GE_TAX_FREE_BELOW = 50;

	int itemId;
	String name;
	/** Jagex's official Grand Exchange guide price. Used by Death's coffer and death fees. */
	int guidePrice;
	/** What one sells for on the GE before tax: the live price if enabled, else RuneLite's average, else the guide price. */
	int wikiPrice;
	int haPrice;
	/** The item's store value, which shops base their prices on. */
	int storePrice;
	boolean tradeable;

	/** Coins added to the coffer when one unit is sacrificed: 105% of the guide price, or 0 if not accepted. */
	public long getCofferValue()
	{
		if (!tradeable || guidePrice < COFFER_MIN_GUIDE_PRICE)
		{
			return 0;
		}
		return (long) guidePrice * 105 / 100;
	}

	/** Coins received from selling one unit on the GE at the real price, after tax. */
	public long getGeNetSell()
	{
		if (!tradeable || wikiPrice <= 0)
		{
			return 0;
		}
		return afterTax(wikiPrice);
	}

	/** Coins received for one item sold on the GE at this price, after tax. */
	public static long afterTax(long price)
	{
		long tax = price < GE_TAX_FREE_BELOW ? 0 : Math.min(price * GE_TAX_PERCENT / 100, GE_TAX_CAP);
		return price - tax;
	}

	/** Coins from casting High Level Alchemy on one unit, after paying for the nature rune. */
	public long getHighAlchNet(int natureRuneCost)
	{
		return Math.max(0, haPrice - natureRuneCost);
	}

	/**
	 * The value the game uses to pick which items are kept on death: the higher of the
	 * GE guide price and the alchemy value.
	 */
	public long getProtectionValue()
	{
		return Math.max(guidePrice, haPrice);
	}
}
