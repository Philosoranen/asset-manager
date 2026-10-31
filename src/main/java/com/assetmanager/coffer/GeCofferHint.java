package com.assetmanager.coffer;

import com.assetmanager.value.ItemValues;
import lombok.Value;

/** A GE sell offer that would put more into a short Death's coffer if the items were sacrificed instead. */
@Value
public class GeCofferHint
{
	String name;
	/** How many to sacrifice: the offer's quantity, or fewer if that's enough to fill the coffer. */
	int units;
	int quantity;
	/** Coins the coffer gets for those units. */
	long cofferValue;
	/** Coins this offer gets for those units, after tax. */
	long saleValue;
	long shortfall;

	/**
	 * @param price     the offer's price per item
	 * @param shortfall how far the coffer is below its target; nothing is suggested once it's full, as coins from
	 *                  the sale are worth more than coins locked in the coffer
	 */
	public static GeCofferHint of(ItemValues v, long price, int quantity, long shortfall)
	{
		long cofferUnit = v.getCofferValue();
		long saleUnit = ItemValues.afterTax(price);
		if (cofferUnit <= 0 || quantity <= 0 || price <= 0 || shortfall <= 0 || cofferUnit <= saleUnit)
		{
			return null;
		}
		int units = (int) Math.min(quantity, (shortfall + cofferUnit - 1) / cofferUnit);
		return new GeCofferHint(v.getName(), units, quantity, units * cofferUnit, units * saleUnit, shortfall);
	}

	public long getExtra()
	{
		return cofferValue - saleValue;
	}
}
