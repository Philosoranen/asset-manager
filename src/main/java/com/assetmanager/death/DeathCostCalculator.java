package com.assetmanager.death;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Works out which carried items would be kept on death and what reclaiming the rest costs.
 * Pure logic with no client access so it can be unit tested.
 */
public final class DeathCostCalculator
{
	static final long GRAVESTONE_FEE_CAP = 500_000;
	static final long FREE_RECLAIM_THRESHOLD = 100_000;

	private DeathCostCalculator()
	{
	}

	/**
	 * @param items        everything worn and in the inventory
	 * @param keepCount    items kept on death (3 normally, +1 with Protect Item, 0 when skulled)
	 * @param ironDiscount whether the account gets the ironman 50% fee discount
	 * @param scenario     where the death happens
	 */
	public static DeathCostEstimate estimate(List<RiskItem> items, int keepCount, boolean ironDiscount, DeathScenario scenario)
	{
		int[] keptQty = pickKept(items, keepCount);

		List<RiskItem> kept = new ArrayList<>();
		List<RiskItem> lost = new ArrayList<>();
		for (int i = 0; i < items.size(); i++)
		{
			RiskItem item = items.get(i);
			if (keptQty[i] > 0)
			{
				kept.add(withQuantity(item, keptQty[i]));
			}
			int remaining = item.getQuantity() - keptQty[i];
			if (remaining > 0)
			{
				lost.add(withQuantity(item, remaining));
			}
		}

		List<Long> feeItemValues = feeItemValues(lost);
		long valueAtRisk = feeItemValues.stream().mapToLong(Long::longValue).sum();
		long gravestone = gravestoneFee(feeItemValues, ironDiscount);
		long office = deathsOfficeFee(feeItemValues, ironDiscount);

		long scenarioFee;
		switch (scenario.getFeeType())
		{
			case GRAVESTONE:
				scenarioFee = gravestone;
				break;
			case DEATHS_OFFICE:
				scenarioFee = office;
				break;
			case TOA:
				// The wiki only says the fee varies with GE value up to 500k; approximate with the
				// Death's Office rate, capped.
				scenarioFee = Math.min(deathsOfficeFee(feeItemValues, false), scenario.getFee());
				break;
			case FLAT:
			default:
				scenarioFee = lost.isEmpty() ? 0 : scenario.getFee();
				break;
		}

		return new DeathCostEstimate(kept, lost, valueAtRisk, gravestone, office, scenario, scenarioFee, ironDiscount, keepCount);
	}

	/** Returns how many units of each item are kept, choosing the most valuable units first. */
	private static int[] pickKept(List<RiskItem> items, int keepCount)
	{
		int[] keptQty = new int[items.size()];
		if (keepCount <= 0)
		{
			return keptQty;
		}

		// Each unit of a stack counts separately, but no item can contribute more than keepCount units.
		List<long[]> units = new ArrayList<>();
		for (int i = 0; i < items.size(); i++)
		{
			RiskItem item = items.get(i);
			int n = Math.min(item.getQuantity(), keepCount);
			for (int u = 0; u < n; u++)
			{
				units.add(new long[]{i, item.getProtectionValue()});
			}
		}
		units.sort(Comparator.comparingLong((long[] u) -> u[1]).reversed());
		for (int k = 0; k < Math.min(keepCount, units.size()); k++)
		{
			keptQty[(int) units.get(k)[0]]++;
		}
		return keptQty;
	}

	/** Fees are charged per item: a whole stack counts as one item, each non-stacking item separately. */
	private static List<Long> feeItemValues(List<RiskItem> lost)
	{
		List<Long> values = new ArrayList<>();
		for (RiskItem item : lost)
		{
			if (item.isStackable())
			{
				values.add(item.getFeeValue() * item.getQuantity());
			}
			else
			{
				values.addAll(Collections.nCopies(item.getQuantity(), item.getFeeValue()));
			}
		}
		return values;
	}

	static long gravestoneFee(List<Long> itemValues, boolean ironDiscount)
	{
		long total = 0;
		for (long v : itemValues)
		{
			total += gravestoneItemFee(v);
		}
		total = Math.min(total, GRAVESTONE_FEE_CAP);
		return ironDiscount ? total / 2 : total;
	}

	static long gravestoneItemFee(long value)
	{
		if (value >= 10_000_000)
		{
			return 100_000;
		}
		if (value >= 1_000_000)
		{
			return 10_000;
		}
		if (value >= FREE_RECLAIM_THRESHOLD)
		{
			return 1_000;
		}
		return 0;
	}

	static long deathsOfficeFee(List<Long> itemValues, boolean ironDiscount)
	{
		long total = 0;
		for (long v : itemValues)
		{
			if (v >= FREE_RECLAIM_THRESHOLD)
			{
				// 5%, or 2.5% for ironmen
				total += ironDiscount ? v * 25 / 1000 : v * 5 / 100;
			}
		}
		return total;
	}

	private static RiskItem withQuantity(RiskItem item, int quantity)
	{
		return new RiskItem(item.getItemId(), item.getName(), quantity, item.isStackable(),
			item.getProtectionValue(), item.getFeeValue());
	}
}
