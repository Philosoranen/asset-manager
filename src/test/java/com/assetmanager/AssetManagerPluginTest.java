package com.assetmanager;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AssetManagerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AssetManagerPlugin.class);
		RuneLite.main(args);
	}
}
