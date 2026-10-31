package com.assetmanager.value;

import com.assetmanager.shop.ShopOffer;
import lombok.Value;

/** A banked item and the best way to cash it out: high alching, a shop, or the GE. */
@Value
public class SaleSuggestion
{
	public enum Type
	{
		ALCH,
		SHOP,
		GE,
	}

	ItemValues values;
	/** Spare quantity, after the player's keep rules. */
	int quantity;
	Type type;
	/** Coins from the suggested option for the whole quantity. */
	long total;
	/** Coins from the next best option. */
	long alternativeTotal;
	/** e.g. "GE sell", "High alch" or "Shop". */
	String alternativeLabel;
	/** The shop to sell to, for SHOP suggestions. */
	ShopOffer shopOffer;

	public long getGain()
	{
		return total - alternativeTotal;
	}
}
