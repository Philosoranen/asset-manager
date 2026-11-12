package com.assetmanager.shop;

import lombok.Value;

/** When selling to a shop is worth it, from the player's settings. */
@Value
public class SellRules
{
	/** Each sale must beat the next best way to cash out (GE or alching) by this much. */
	int minGainPerItem;
	/** The most worlds to hop at one shop, or ShopCatalog.NO_WORLD_LIMIT. */
	int maxWorlds;
	/**
	 * A shop is only suggested if everything to sell there gains at least this much for each world visited: hopping
	 * takes time, and alching or the GE doesn't need it.
	 */
	int minGainPerWorld;
}
