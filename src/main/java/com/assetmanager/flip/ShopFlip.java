package com.assetmanager.flip;

import lombok.Value;

/** How many of a shop's item to buy and sell on (on the GE, to Death's coffer or by alching), and the profit it makes. */
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
		/** More than the GE trades in the time the player will wait. */
		VOLUME("what the GE trades in that time"),
		/** As many as can be alched in an hour. */
		CASTS("an hour of alching"),
		/** The GE's buy limit (per 4 hours). */
		BUY_LIMIT("GE buy limit"),
		/** More would take hopping more worlds than the player's limit. */
		WORLDS("worlds you'd hop"),
		/** Hopping more worlds would make more, but less an hour. */
		QUICKER("hopping more makes less an hour"),
		/** Enough to fill Death's coffer to the target. */
		COFFER("room in your coffer"),
		/** As many different items as the player will flip through the GE at once. */
		GE_SLOTS("items per GE flip");

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
	/** Coins from selling them all on the GE after tax, the coffer value, or what alching them makes. */
	long revenue;
	long firstPrice;
	long lastPrice;
	/** What one is worth where it's going: the GE sale after tax, the coffer value, or alching less the rune. */
	long geUnit;
	/** What stopped the quantity going higher. */
	Limit limit;
	/** Worlds to buy them on, a few on each before hopping (1 if the price doesn't rise or the shop is in front of you). */
	int worlds;

	/** About how many to buy on each world. */
	public int getPerWorld()
	{
		return (quantity + worlds - 1) / Math.max(1, worlds);
	}

	public long getProfit()
	{
		return revenue - cost;
	}
}
