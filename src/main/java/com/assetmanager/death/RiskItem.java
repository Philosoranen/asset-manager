package com.assetmanager.death;

import lombok.Value;

/** An item stack the player is carrying (worn or in inventory). */
@Value
public class RiskItem
{
	int itemId;
	String name;
	int quantity;
	boolean stackable;
	/** Per-unit value used to decide which items are kept on death. */
	long protectionValue;
	/** Per-unit value used to calculate reclaim fees (GE guide price). */
	long feeValue;
}
