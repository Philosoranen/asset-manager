package com.assetmanager.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Setter;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.QuantityFormatter;

/** Shows how many deaths the coffer covers in the current gear. */
public class CofferInfoBox extends InfoBox
{
	@Setter
	private PanelState state;
	@Setter
	private int warnDeaths;

	public CofferInfoBox(BufferedImage image, Plugin plugin)
	{
		super(image, plugin);
	}

	@Override
	public String getText()
	{
		long deaths = state == null ? -1 : state.getDeathsCovered();
		if (deaths < 0)
		{
			return "?";
		}
		return deaths == Long.MAX_VALUE ? "Free" : String.valueOf(deaths);
	}

	@Override
	public Color getTextColor()
	{
		long deaths = state == null ? -1 : state.getDeathsCovered();
		if (deaths < 0)
		{
			return Color.GRAY;
		}
		return deaths < warnDeaths ? Color.RED : Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		if (state == null || !state.isCofferKnown())
		{
			return "Death's coffer balance unknown</br>Check your coffer at Death's Office or set it in the Asset Manager panel";
		}
		String tip = "Death's coffer: " + QuantityFormatter.formatNumber(state.getCofferBalance()) + " gp";
		if (state.getEstimate() != null)
		{
			tip += "</br>Cost per death (" + state.getEstimate().getScenario() + "): "
				+ QuantityFormatter.formatNumber(state.getEstimate().getScenarioFee()) + " gp";
		}
		return tip;
	}

	@Override
	public boolean render()
	{
		return state != null && state.getAccountMode().canUseCoffer();
	}
}
