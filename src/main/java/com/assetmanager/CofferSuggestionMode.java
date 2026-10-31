package com.assetmanager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CofferSuggestionMode
{
	PLAN("Top-up plan"),
	ALL("All worth sacrificing");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
