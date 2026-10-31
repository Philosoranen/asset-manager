package com.assetmanager.death;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where the player dies, which determines how lost items are reclaimed and what it costs.
 * Fees are from the OSRS Wiki (Gravestone, Death's Office and Item retrieval service pages).
 */
@Getter
@RequiredArgsConstructor
public enum DeathScenario
{
	GRAVESTONE("Gravestone (reclaimed in time)", FeeType.GRAVESTONE, 0),
	DEATHS_OFFICE("Death's Office (grave expired)", FeeType.DEATHS_OFFICE, 0),
	ALCHEMICAL_HYDRA("Alchemical Hydra", FeeType.FLAT, 100_000),
	GROTESQUE_GUARDIANS("Grotesque Guardians", FeeType.FLAT, 50_000),
	HALLOWED_SEPULCHRE("Hallowed Sepulchre", FeeType.FLAT, 25_000),
	HESPORI("Hespori", FeeType.FLAT, 25_000),
	NEX("Nex", FeeType.FLAT, 100_000),
	NIGHTMARE("The Nightmare", FeeType.FLAT, 60_000),
	PHOSANIS_NIGHTMARE("Phosani's Nightmare", FeeType.FLAT, 60_000),
	THEATRE_OF_BLOOD("Theatre of Blood", FeeType.FLAT, 100_000),
	TOMBS_OF_AMASCUT("Tombs of Amascut (estimate)", FeeType.TOA, 500_000),
	VORKATH("Vorkath", FeeType.FLAT, 100_000),
	ZULRAH("Zulrah (50+ kc)", FeeType.FLAT, 100_000);

	enum FeeType
	{
		GRAVESTONE,
		DEATHS_OFFICE,
		FLAT,
		TOA,
	}

	private final String displayName;
	private final FeeType feeType;
	/** Flat fee, or the fee cap for variable fees. */
	private final long fee;

	@Override
	public String toString()
	{
		return displayName;
	}
}
