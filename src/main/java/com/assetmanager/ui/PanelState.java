package com.assetmanager.ui;

import com.assetmanager.AccountMode;
import com.assetmanager.coffer.CofferPlan;
import com.assetmanager.death.DeathCostEstimate;
import com.assetmanager.dest.Destinations;
import com.assetmanager.dest.GeOrder;
import com.assetmanager.flip.Flip;
import com.assetmanager.flip.FlipRun;
import com.assetmanager.flip.FlipSort;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.ShopStock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Everything the side panel shows, calculated on the client thread and handed to the EDT. */
@Value
public class PanelState
{
	AccountMode accountMode;
	long cofferBalance;
	boolean cofferKnown;
	String cofferSource;
	Instant cofferUpdatedAt;
	boolean skulled;
	boolean protectItem;
	/** Null when not logged in. */
	DeathCostEstimate estimate;
	/** Null until the bank has been opened once. */
	CofferPlan plan;
	/** Coffer balance the top-up plan aims for. */
	long cofferTarget;
	/** Most expensive death cost seen for the planned scenario in the last 14 days. */
	long peakFee;
	long minCofferTarget;
	/** How many keep rules the player has. */
	int keepRuleCount;
	/** Karamja gloves, Ring of Charos and Wilderness choice used for shop suggestions. */
	ShopPerks perks;
	long bankCoins;
	int targetDeaths;
	/** Where spare items should go; null until the bank has been opened once. */
	Destinations destinations;
	/** When the worlds the player has seen each shop overstocked on are back to normal, by shop name. */
	Map<String, Long> restockedBy;
	/** Shop items others have overstocked on several worlds, which aren't suggested until they restock. */
	List<ShopStock.Busy> busyShops;
	/** The best flips, best first by flipSort; empty if none, turned off or not worked out yet. */
	List<FlipRun> flips;
	/** Whether flips are turned on in the settings. */
	boolean flipsEnabled;
	/** Whether GE-bought flips are included: the account can use the GE and live prices are on and loaded. */
	boolean geFlips;
	/** Where the flips cash out: the player's choice, if their account can. */
	Flip.Dest flipDest;
	/** How the flips are ordered. */
	FlipSort flipSort;
	/** Coins in the inventory, which is what the flips spend. */
	long flipCoins;
	/** How the Grand Exchange bank view is ordered. */
	GeOrder geOrder;
	/** Whether GE trade volumes are loaded (live prices on), which ordering by quickest to sell needs. */
	boolean geVolumes;
	/** Whether selling to a shop hops no more worlds than the go's most profitable item takes. */
	boolean evenHops;

	/** Deaths the coffer covers at the selected scenario; -1 if unknown, Long.MAX_VALUE if free. */
	public long getDeathsCovered()
	{
		if (!cofferKnown || estimate == null)
		{
			return -1;
		}
		long fee = estimate.getScenarioFee();
		return fee <= 0 ? Long.MAX_VALUE : cofferBalance / fee;
	}
}
