package com.assetmanager.coffer;

import java.util.List;
import lombok.Value;

@Value
public class CofferPlan
{
	@Value
	public static class Step
	{
		CofferCandidate candidate;
		int quantity;

		public long getCofferValue()
		{
			return candidate.getCofferUnit() * quantity;
		}
	}

	/** Every banked item worth sacrificing, best first. */
	List<CofferCandidate> candidates;
	/** Items to sacrifice to reach the target, best first. */
	List<Step> steps;
	/** Coins to deposit after sacrificing the planned items. */
	long coinsToDeposit;
	/** Coins still missing after all items and bank coins are used. */
	long remainingShortfall;
	long target;
	long shortfall;
}
