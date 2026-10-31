package com.assetmanager.flip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.assetmanager.shop.ShopCatalog;
import com.google.gson.Gson;
import org.junit.Test;

public class ShopRatesTest
{
	private final ShopCatalog shops = ShopCatalog.load(new Gson());

	@Test
	public void voidKnightGeneralStore()
	{
		ShopRates r = ShopRates.of(shops.findByName("Void Knight General Store"), false);
		assertEquals(1300, r.getSell());
		assertEquals(400, r.getBuy());
		assertEquals(3, r.getDelta());
		assertEquals("0.3%", ShopRatesOverlay.percent(r.getDelta()));
		assertEquals("130%", ShopRatesOverlay.percent(r.getSell()));
	}

	@Test
	public void karamjaGlovesChangeDavonsRates()
	{
		ShopRates without = ShopRates.of(shops.findByName("Davon's Amulet Store"), false);
		ShopRates with = ShopRates.of(shops.findByName("Davon's Amulet Store"), true);
		assertEquals(900, without.getBuy());
		assertEquals(1200, without.getSell());
		assertFalse(without.isGloves());
		assertEquals(1000, with.getBuy());
		assertEquals(1100, with.getSell());
		assertTrue(with.isGloves());
	}

	@Test
	public void sellOnlyShop()
	{
		assertEquals(0, ShopRates.of(shops.findByName("Makeover Mage"), false).getBuy());
	}
}
