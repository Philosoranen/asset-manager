package com.assetmanager.death;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DeathLocationsTest
{
	@Test
	public void bossAreasUseTheirRetrievalService()
	{
		assertEquals(DeathScenario.VORKATH, DeathLocations.at(9023));
		assertEquals(DeathScenario.ALCHEMICAL_HYDRA, DeathLocations.at(5536));
		// Lumbridge
		assertEquals(DeathScenario.GRAVESTONE, DeathLocations.at(12850));
	}
}
