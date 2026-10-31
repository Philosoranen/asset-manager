package com.assetmanager.flip;

import lombok.Value;

/** How many of a shop's item to buy and sell on the GE, and the profit it makes. */
@Value
public class ShopFlip
{
	public enum Limit
	{
		/** The shop ran out. */
		STOCK("shop stock"),
		/** The shop's price rose until it wasn't profitable. */
		PRICE("price rises as stock drops"),
		/** No more room in the inventory. */
		SPACE("inventory space"),
		/** Not enough coins in the inventory. */
		COINS("coins in inventory"),
		/** More than the GE trades in an hour. */
		VOLUME("what the GE trades in an hour");

		private final String description;

		Limit(String description)
		{
			this.description = description;
		}

		public String getDescription()
		{
			return description;
		}
	}

	int quantity;
	long cost;
	/** Coins from selling them all on the GE, after tax. */
	long revenue;
	long firstPrice;
	long lastPrice;
	/** Coins from selling one on the GE, after tax. */
	long geUnit;
	/** What stopped the quantity going higher. */
	Limit limit;

	public long getProfit()
	{
		return revenue - cost;
	}
}
