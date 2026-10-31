package com.assetmanager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Whether the player has an item, for config options that can be detected. */
@Getter
@RequiredArgsConstructor
public enum Ownership
{
	AUTO("Detect"),
	YES("Have it"),
	NO("Don't have it");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
