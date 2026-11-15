package com.assetmanager.ui;

import com.assetmanager.InfoBoxMode;
import lombok.Value;

/** The Death's coffer settings, shown and changed on the panel's coffer card. */
@Value
public class CofferSettings
{
	public static final CofferSettings DEFAULT = new CofferSettings(5, 500_000, 2, InfoBoxMode.ALWAYS);

	int targetDeaths;
	int minCofferTarget;
	int warnDeaths;
	InfoBoxMode infoBox;
}
