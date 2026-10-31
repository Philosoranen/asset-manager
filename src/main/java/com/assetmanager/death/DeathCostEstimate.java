package com.assetmanager.death;

import java.util.List;
import lombok.Value;

@Value
public class DeathCostEstimate
{
	/** Items (with quantities) that would be kept on death. */
	List<RiskItem> keptItems;
	/** Items (with quantities) that would need to be reclaimed. */
	List<RiskItem> lostItems;
	/** Guide price value of everything that would need reclaiming. */
	long valueAtRisk;
	long gravestoneFee;
	long deathsOfficeFee;
	DeathScenario scenario;
	/** Fee for the selected scenario. */
	long scenarioFee;
	boolean ironDiscount;
	int keepCount;
}
