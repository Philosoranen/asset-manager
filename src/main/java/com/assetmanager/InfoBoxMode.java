package com.assetmanager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** When the deaths-covered infobox shows. */
@Getter
@RequiredArgsConstructor
public enum InfoBoxMode
{
	ALWAYS("Always"),
	BELOW_WARNING("Below warning"),
	NEVER("Never");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
