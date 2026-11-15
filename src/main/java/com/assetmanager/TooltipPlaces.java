package com.assetmanager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Where Asset Manager's hover tooltips show. */
@Getter
@RequiredArgsConstructor
public enum TooltipPlaces
{
	BOTH("Bank, shops and inventory", true, true),
	BANK_AND_SHOPS("Bank and shops", true, false),
	INVENTORY("Inventory", false, true),
	NONE("Nowhere", false, false);

	private final String displayName;
	/** The bank, seed vault and shop items. */
	private final boolean bankAndShops;
	/** The inventory, including while the bank is open. */
	private final boolean inventory;

	@Override
	public String toString()
	{
		return displayName;
	}
}
