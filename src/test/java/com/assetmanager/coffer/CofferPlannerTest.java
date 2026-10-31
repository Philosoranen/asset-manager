package com.assetmanager.coffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.assetmanager.value.ItemValues;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CofferPlannerTest
{
	private final Map<Integer, ItemValues> bank = new HashMap<>();
	private final Map<Integer, Integer> qty = new HashMap<>();

	private void add(int id, String name, int guide, int wiki, int ha, int quantity)
	{
		bank.put(id, new ItemValues(id, name, guide, wiki, ha, ha * 10 / 6, true));
		qty.put(id, quantity);
	}

	@Test
	public void ranksByGainOverBestAlternative()
	{
		add(1, "Guide inflated", 100_000, 80_000, 10_000, 5);  // coffer 105k vs GE 78.4k
		add(2, "Fairly priced", 100_000, 100_000, 10_000, 5);  // coffer 105k vs GE 98k
		add(3, "Alch item", 20_000, 20_000, 30_000, 5);        // coffer 21k vs alch ~29.8k -> excluded
		add(4, "Too cheap", 9_999, 9_999, 0, 100);             // below 10k minimum

		List<CofferCandidate> c = CofferPlanner.findCandidates(bank, qty, true, 200, 0);
		assertEquals(2, c.size());
		assertEquals("Guide inflated", c.get(0).getValues().getName());
		assertEquals(105_000, c.get(0).getCofferUnit());
		assertEquals("GE sell", c.get(0).getAlternativeLabel());
	}

	@Test
	public void ironmenCompareAgainstAlchOnly()
	{
		add(1, "Dragon item", 100_000, 90_000, 60_000, 1);
		List<CofferCandidate> c = CofferPlanner.findCandidates(bank, qty, false, 200, 0);
		assertEquals("High alch", c.get(0).getAlternativeLabel());
		assertEquals(59_800, c.get(0).getAlternativeUnit());
	}

	@Test
	public void planRoundsUpAndFallsBackToCoins()
	{
		add(1, "Item", 100_000, 90_000, 0, 3); // 105k each, 315k total
		List<CofferCandidate> c = CofferPlanner.findCandidates(bank, qty, true, 200, 0);

		CofferPlan small = CofferPlanner.plan(c, 50_000, 200_000, 0);
		assertEquals(150_000, small.getShortfall());
		assertEquals(2, small.getSteps().get(0).getQuantity());

		CofferPlan big = CofferPlanner.plan(c, 0, 1_000_000, 500_000);
		assertEquals(3, big.getSteps().get(0).getQuantity());
		assertEquals(500_000, big.getCoinsToDeposit());
		assertEquals(185_000, big.getRemainingShortfall());

		assertTrue(CofferPlanner.plan(c, 2_000_000, 1_000_000, 0).getSteps().isEmpty());
	}
}
