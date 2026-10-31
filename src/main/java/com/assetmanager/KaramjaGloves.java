package com.assetmanager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KaramjaGloves
{
	AUTO("Detect from diary", -1),
	NONE("None", 0),
	GLOVES_1_2("Gloves 1 or 2", 1),
	GLOVES_3_4("Gloves 3 or 4", 3);

	private final String displayName;
	/** Tier used for shop prices; gloves 2 and 4 give the same shop prices as 1 and 3. */
	private final int tier;

	@Override
	public String toString()
	{
		return displayName;
	}
}
