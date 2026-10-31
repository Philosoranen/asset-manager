package com.assetmanager.coffer;

import com.assetmanager.value.ItemValues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Decides which banked items are better sacrificed to Death's coffer than sold or alched,
 * and builds a plan to reach a target coffer balance.
 */
public final class CofferPlanner
{
	private CofferPlanner()
	{
	}

	/**
	 * @param bank           banked items (canonical id -> values) with quantities; coins must be excluded
	 * @param quantities     quantity per item id
	 * @param canUseGe       false for ironmen
	 * @param natureRuneCost cost of a nature rune, deducted from alch value
	 * @param minGainPercent only suggest items where the coffer beats the alternative by at least this much
	 */
	public static List<CofferCandidate> findCandidates(Map<Integer, ItemValues> bank, Map<Integer, Integer> quantities,
		boolean canUseGe, int natureRuneCost, int minGainPercent)
	{
		List<CofferCandidate> candidates = new ArrayList<>();
		for (Map.Entry<Integer, ItemValues> e : bank.entrySet())
		{
			ItemValues v = e.getValue();
			int qty = quantities.getOrDefault(e.getKey(), 0);
			long coffer = v.getCofferValue();
			if (qty <= 0 || coffer <= 0)
			{
				continue;
			}

			long alch = v.getHighAlchNet(natureRuneCost);
			long ge = canUseGe ? v.getGeNetSell() : 0;
			CofferCandidate c = ge >= alch
				? new CofferCandidate(v, qty, coffer, ge, "GE sell")
				: new CofferCandidate(v, qty, coffer, alch, "High alch");

			if (c.getGainRatio() * 100 >= minGainPercent)
			{
				candidates.add(c);
			}
		}
		candidates.sort(Comparator.comparingDouble(CofferCandidate::getGainRatio).reversed()
			.thenComparing(Comparator.comparingLong(CofferCandidate::getCofferTotal).reversed()));
		return candidates;
	}

	/**
	 * Greedily picks the most efficient items until the target is met, then falls back to bank coins
	 * (deposited 1:1).
	 */
	public static CofferPlan plan(List<CofferCandidate> candidates, long balance, long target, long bankCoins)
	{
		long shortfall = Math.max(0, target - balance);
		long needed = shortfall;
		List<CofferPlan.Step> steps = new ArrayList<>();
		for (CofferCandidate c : candidates)
		{
			if (needed <= 0)
			{
				break;
			}
			// Round up so the target is actually reached.
			long units = Math.min(c.getQuantity(), (needed + c.getCofferUnit() - 1) / c.getCofferUnit());
			steps.add(new CofferPlan.Step(c, (int) units));
			needed -= units * c.getCofferUnit();
		}
		long coins = Math.min(Math.max(0, needed), bankCoins);
		needed -= coins;
		return new CofferPlan(candidates, steps, coins, Math.max(0, needed), target, shortfall);
	}
}
