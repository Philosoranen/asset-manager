package com.assetmanager.shop;

import lombok.Value;

/** A visit to one shop to sell several stacks, hopping worlds as the stacks need. */
@Value
public class ShopTrip
{
	String shop;
	/** Worlds to sell on: as many as the stack needing the most. */
	int worlds;
	/** What the hops between them are worth to the player. */
	long hopCost;
	/** How much more the trip makes than cashing the stacks out the other way, after the hops. */
	long gain;
}
