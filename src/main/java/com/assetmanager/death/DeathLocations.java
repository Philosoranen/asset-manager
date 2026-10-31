package com.assetmanager.death;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a death costs something other than a gravestone: boss areas with their own item retrieval service,
 * by map region (for instances, the region they're copied from).
 */
public final class DeathLocations
{
	private static final Map<Integer, DeathScenario> BY_REGION = new HashMap<>();

	static
	{
		add(DeathScenario.VORKATH, 9023);
		add(DeathScenario.ZULRAH, 9007, 9008);
		add(DeathScenario.ALCHEMICAL_HYDRA, 5536);
		add(DeathScenario.GROTESQUE_GUARDIANS, 6727);
		add(DeathScenario.NEX, 11601);
		add(DeathScenario.NIGHTMARE, 15515);
		add(DeathScenario.HESPORI, 5021);
		add(DeathScenario.HALLOWED_SEPULCHRE, 8797, 9050, 9308, 10074, 10077);
		add(DeathScenario.THEATRE_OF_BLOOD, 12611, 12612, 12613, 13122, 13123, 13125);
		add(DeathScenario.TOMBS_OF_AMASCUT, 14160, 14162, 14164, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700);
	}

	private DeathLocations()
	{
	}

	private static void add(DeathScenario scenario, int... regions)
	{
		for (int r : regions)
		{
			BY_REGION.put(r, scenario);
		}
	}

	/** What dying in a region costs: its boss's retrieval service, or a gravestone. */
	public static DeathScenario at(int regionId)
	{
		return BY_REGION.getOrDefault(regionId, DeathScenario.GRAVESTONE);
	}
}
