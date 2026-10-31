package com.assetmanager.death;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DeathCostCalculatorTest
{
	private static RiskItem item(String name, int qty, boolean stackable, long value)
	{
		return new RiskItem(name.hashCode(), name, qty, stackable, value, value);
	}

	private static final List<RiskItem> GEAR = Arrays.asList(
		item("Twisted bow", 1, false, 1_200_000_000L),
		item("Ancestral robe top", 1, false, 90_000_000L),
		item("Occult necklace", 1, false, 800_000L),
		item("Dragon arrow", 500, true, 1_500L),   // stack worth 750k
		item("Shark", 20, false, 900L),
		item("Rune pouch", 1, false, 0L)
	);

	@Test
	public void keepsThreeMostValuableAndChargesGraveTiers()
	{
		DeathCostEstimate e = DeathCostCalculator.estimate(GEAR, 3, false, DeathScenario.GRAVESTONE);
		// Kept: tbow, ancestral top, occult. Lost: arrows stack (750k -> 1k), sharks (free), pouch (free).
		assertEquals(3, e.getKeptItems().size());
		assertEquals(1_000, e.getGravestoneFee());
		assertEquals(750_000 + 20 * 900, e.getValueAtRisk());
		// Death's Office: 5% of the arrow stack only.
		assertEquals(37_500, e.getDeathsOfficeFee());
	}

	@Test
	public void skulledLosesEverythingAndGraveFeeIsCapped()
	{
		DeathCostEstimate e = DeathCostCalculator.estimate(GEAR, 0, false, DeathScenario.GRAVESTONE);
		// 100k + 100k + 1k (occult) + 1k (arrows) = 202k, under the cap
		assertEquals(202_000, e.getGravestoneFee());

		List<RiskItem> many = Arrays.asList(item("Megarare", 6, false, 50_000_000L));
		assertEquals(500_000, DeathCostCalculator.estimate(many, 0, false, DeathScenario.GRAVESTONE).getGravestoneFee());
		assertEquals(250_000, DeathCostCalculator.estimate(many, 0, true, DeathScenario.GRAVESTONE).getGravestoneFee());
	}

	@Test
	public void stackUnitsCountSeparatelyForKeptItems()
	{
		List<RiskItem> items = Arrays.asList(item("Blood rune", 1000, true, 400L), item("Shark", 1, false, 900L));
		DeathCostEstimate e = DeathCostCalculator.estimate(items, 3, false, DeathScenario.GRAVESTONE);
		// Keeps shark + 2 blood runes; 998 runes lost.
		assertEquals(998, e.getLostItems().get(0).getQuantity());
	}

	@Test
	public void ironmanOfficeRateIsHalved()
	{
		List<RiskItem> items = Arrays.asList(item("Bandos chestplate", 1, false, 10_000_000L));
		assertEquals(500_000, DeathCostCalculator.estimate(items, 0, false, DeathScenario.DEATHS_OFFICE).getScenarioFee());
		assertEquals(250_000, DeathCostCalculator.estimate(items, 0, true, DeathScenario.DEATHS_OFFICE).getScenarioFee());
	}

	@Test
	public void flatBossFeeOnlyWhenSomethingIsLost()
	{
		assertEquals(100_000, DeathCostCalculator.estimate(GEAR, 3, false, DeathScenario.VORKATH).getScenarioFee());
		List<RiskItem> three = GEAR.subList(0, 3);
		assertEquals(0, DeathCostCalculator.estimate(three, 3, false, DeathScenario.VORKATH).getScenarioFee());
	}
}
