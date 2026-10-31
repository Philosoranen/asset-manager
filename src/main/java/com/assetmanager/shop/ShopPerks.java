package com.assetmanager.shop;

import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** What the player has, or is willing to do, that changes which shops they can use and what they pay. */
@Value
@AllArgsConstructor
public class ShopPerks
{
	public static final ShopPerks NONE = new ShopPerks(0, false);

	/** Karamja gloves tier (0 for none); gloves 2 and 4 give the same shop prices as 1 and 3. */
	int glovesTier;
	/** Owns an activated Ring of Charos, which gets better prices from Baraek and Fritz the Glassblower. */
	boolean charos;
	/** Leave out shops in the Wilderness. */
	boolean avoidWilderness;
	/** Where the player is, to pick the closest of shops paying the same; null if unknown. */
	WorldPoint location;

	public ShopPerks(int glovesTier, boolean charos)
	{
		this(glovesTier, charos, false);
	}

	public ShopPerks(int glovesTier, boolean charos, boolean avoidWilderness)
	{
		this(glovesTier, charos, avoidWilderness, null);
	}
}
