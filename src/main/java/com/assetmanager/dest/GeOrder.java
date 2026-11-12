package com.assetmanager.dest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How the Grand Exchange bank view orders its items; picked on the GE row of the side panel. */
@Getter
@RequiredArgsConstructor
public enum GeOrder
{
	/** Most coins first. */
	VALUE("By value", "Most valuable first"),
	/**
	 * The stacks the GE takes off the player's hands soonest first, to free bank spaces: the fewest hours of normal
	 * trading needed to sell the whole spare quantity. Needs live GE prices for trade volumes.
	 */
	QUICKEST("Quickest", "Quickest to sell first");

	/** Short, for the button. */
	private final String label;
	private final String description;

	@Override
	public String toString()
	{
		return description;
	}
}
