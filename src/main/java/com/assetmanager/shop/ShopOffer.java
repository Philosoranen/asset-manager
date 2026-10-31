package com.assetmanager.shop;

import lombok.Value;
import lombok.With;

/**
 * What a shop would pay for a whole stack of an item. The player sells on each world while each sale beats the next
 * best way to cash out (GE or alching), then hops to a fresh world.
 */
@Value
public class ShopOffer
{
	@With
	Shop shop;
	/** The whole stack if it's worth selling here, otherwise 0. */
	int quantity;
	/** Coins for the first item sold. */
	long firstUnit;
	/** Coins for the whole stack, before the cost of hopping. */
	long total;
	/** The rate paid for the first item, in tenths of a percent. */
	int rate;
	boolean usesKaramjaGloves;
	/** How many to sell on each world before hopping. */
	int perWorld;
	/** Worlds to sell the whole stack on (1 if the price doesn't drop, or doesn't drop enough to matter). */
	int worlds;
	/** A trader paying a set number of coins (usually through dialogue) rather than a shop %. */
	boolean fixedPrice;
	/** The price needs an activated Ring of Charos. */
	boolean usesCharos;
	/** Other shops that would pay about the same; this one is the closest of them. */
	@With
	int equalShops;
}
