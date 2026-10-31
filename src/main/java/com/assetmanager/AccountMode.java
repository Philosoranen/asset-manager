package com.assetmanager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountMode
{
	AUTO("Detect automatically"),
	STANDARD("Standard"),
	IRONMAN("Ironman (any non-ultimate)"),
	ULTIMATE_IRONMAN("Ultimate ironman");

	private final String displayName;

	/** Maps the value of the IRONMAN varbit to an account mode. */
	static AccountMode fromVarbit(int value)
	{
		switch (value)
		{
			case 0:
				return STANDARD;
			case 2:
				return ULTIMATE_IRONMAN;
			default:
				// 1 ironman, 3 hardcore, 4 group, 5 hardcore group, 6 unranked group
				return IRONMAN;
		}
	}

	public boolean canUseGrandExchange()
	{
		return this == STANDARD;
	}

	public boolean hasIronFeeDiscount()
	{
		return this == IRONMAN;
	}

	public boolean canUseCoffer()
	{
		return this != ULTIMATE_IRONMAN;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
