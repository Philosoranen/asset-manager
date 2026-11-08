package com.assetmanager.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class ShopCatalogTest
{
	private static Shop shop(String name, boolean general, int mult, int delta, Integer glovesTier, Integer glovesMult, Map<String, Shop.Item> items)
	{
		Shop s = new Shop();
		s.setName(name);
		s.setLocation("");
		s.setGeneral(general);
		s.setBuyMultiplier(mult);
		s.setDelta(delta);
		s.setGlovesTier(glovesTier);
		s.setGlovesBuyMultiplier(glovesMult);
		s.setItems(items);
		return s;
	}

	private static Shop.Item item(Integer gloves)
	{
		Shop.Item i = new Shop.Item();
		i.setG(gloves);
		return i;
	}

	@Test
	public void priceDropsAsShopFillsUpAndStopsAtTenPercent()
	{
		// 60% dropping 3% per item: 600, 570, ... 120, then 100 (the floor) for the rest
		assertEquals(600 + 570 + 540, ShopCatalog.totalFor(1000, 3, 600, 30));
		long all = ShopCatalog.totalFor(1000, 30, 600, 30);
		long expected = 0;
		for (int k = 0; k < 30; k++)
		{
			expected += Math.max(100, 600 - 30 * k);
		}
		assertEquals(expected, all);
		// No drop at all
		assertEquals(500L * 10_000, ShopCatalog.totalFor(1000, 10_000, 500, 0));
	}

	@Test
	public void hopWhenTheNextSaleNoLongerBeatsTheAlternative()
	{
		// 60% dropping 3% per item, vs 500 each elsewhere: 600, 570, 540, 510 beat it, 480 doesn't, so 4 per world
		assertEquals(4, ShopCatalog.perWorld(1000, 600, 30, 501, 100));
		ShopCatalog c = new ShopCatalog(Collections.singletonList(
			shop("General Store", true, 600, 30, null, null, Collections.emptyMap())), "test");
		ShopOffer o = c.bestOffer("Thing", 1000, 10, true, ShopPerks.NONE, 500, 1, 10);
		assertEquals(4, o.getPerWorld());
		assertEquals(3, o.getWorlds());
		assertEquals(2 * (600 + 570 + 540 + 510) + 600 + 570, o.getTotal());
		assertEquals(10, o.getQuantity());

		// The whole stack or nothing: hopping at most 2 worlds isn't enough for 10
		assertEquals(0, c.bestOffer("Thing", 1000, 10, true, ShopPerks.NONE, 500, 1, 2).getQuantity());

		// No limit: 100 at 4 per world is 25 worlds
		ShopOffer unlimited = c.bestOffer("Thing", 1000, 100, true, ShopPerks.NONE, 500, 1, ShopCatalog.NO_WORLD_LIMIT);
		assertEquals(100, unlimited.getQuantity());
		assertEquals(25, unlimited.getWorlds());

		// Not even the first sale beats 650 elsewhere
		assertEquals(0, c.bestOffer("Thing", 1000, 10, true, ShopPerks.NONE, 650, 1, 10).getQuantity());

		// Void Knight General Store: 40% dropping only 0.3%, so a stack of 20 sells on one world
		assertEquals(20, ShopCatalog.perWorld(1000, 400, 3, 301, 20));
	}

	@Test
	public void specialtyShopsOnlyBuyWhatTheyStock()
	{
		Map<String, Shop.Item> amulets = new HashMap<>();
		amulets.put("amulet of power", item(1000));
		ShopCatalog c = new ShopCatalog(Arrays.asList(
			shop("Davon's Amulet Store", false, 900, 20, 1, null, amulets),
			shop("General Store", true, 400, 30, null, null, Collections.emptyMap())), "test");

		ShopOffer noGloves = c.bestOffer("Amulet of power", 3525, 1, true, new ShopPerks(0, false), 0, 1, 10);
		assertEquals("Davon's Amulet Store", noGloves.getShop().getName());
		assertEquals(3172, noGloves.getFirstUnit());
		assertFalse(noGloves.isUsesKaramjaGloves());

		ShopOffer gloves = c.bestOffer("Amulet of power", 3525, 1, true, new ShopPerks(1, false), 0, 1, 10);
		assertEquals(3525, gloves.getFirstUnit());
		assertTrue(gloves.isUsesKaramjaGloves());

		// Not stocked by Davon's, so only the general store buys it
		assertEquals("General Store", c.bestOffer("Amulet of glory", 17_625, 1, true, new ShopPerks(3, false), 0, 1, 10).getShop().getName());
		// General stores don't buy untradeables
		assertNull(c.bestOffer("Quest thing", 100, 1, false, new ShopPerks(0, false), 0, 1, 10));
	}

	@Test
	public void generalStoreGlovesNeedTier3()
	{
		ShopCatalog c = new ShopCatalog(Collections.singletonList(
			shop("Karamja General Store", true, 400, 30, 3, 600, Collections.emptyMap())), "test");
		assertEquals(400, c.bestOffer("Rune platebody", 65_000, 1, true, new ShopPerks(1, false), 0, 1, 10).getRate());
		assertEquals(600, c.bestOffer("Rune platebody", 65_000, 1, true, new ShopPerks(3, false), 0, 1, 10).getRate());
	}

	@Test
	public void maxWorldsDecidesBetweenShops()
	{
		// Ali's: 50% with no drop. Aubury's: 60% dropping 0.1% per rune, so 1000 runes need hopping.
		Shop ali = shop("Ali's Discount Wares (Catalytic runes)", false, 500, 0, null, null, Collections.singletonMap("death rune", item(null)));
		Shop aubury = shop("Aubury's Rune Shop", false, 600, 1, null, null, Collections.singletonMap("death rune", item(null)));
		ShopCatalog c = new ShopCatalog(Arrays.asList(aubury, ali), "test");
		// Against 80 each elsewhere, Aubury's sells 151 per world over 7 worlds for more than Ali's; hops are free
		ShopOffer o = c.bestOffer("Death rune", 180, 1000, true, ShopPerks.NONE, 80, new SellRules(1, 10, 0), Collections.emptySet());
		assertEquals("Aubury's Rune Shop", o.getShop().getName());
		assertEquals(7, o.getWorlds());
		// ...but not when the player won't hop that many worlds
		o = c.bestOffer("Death rune", 180, 1000, true, ShopPerks.NONE, 80, new SellRules(1, 5, 0), Collections.emptySet());
		assertEquals("Ali's Discount Wares (Catalytic runes)", o.getShop().getName());
		assertEquals(1, o.getWorlds());
	}

	@Test
	public void findsShopsByWindowTitle()
	{
		ShopCatalog c = ShopCatalog.load(new Gson());
		assertEquals("Davon's Amulet Store", c.findByName("Davon's Amulet Store.").getName());
		assertEquals("Bandit Duty Free", c.findByName("bandit duty free").getName());
		assertNull(c.findByName("Not a real shop"));
	}

	@Test
	public void sellOnlyShopsDontBuy()
	{
		ShopCatalog c = ShopCatalog.load(new Gson());
		// The Makeover Mage sells yin yang amulets but doesn't buy them; general stores still do
		ShopOffer o = c.bestOffer("Yin yang amulet", 100, 1, true, new ShopPerks(0, false), 0, 1, 10);
		assertTrue(o == null || !o.getShop().getName().equals("Makeover Mage"));
	}

	@Test
	public void furs()
	{
		ShopCatalog c = ShopCatalog.load(new Gson());
		// Baraek pays a fixed 120 for grey wolf fur (value 50), far above the fur stalls' 95%
		ShopOffer wolf = c.bestOffer("Grey wolf fur", 50, 10, true, new ShopPerks(0, false), 0, 1, 10);
		assertEquals("Baraek", wolf.getShop().getName());
		assertTrue(wolf.isFixedPrice());
		assertEquals(1200, wolf.getTotal());
		// With an activated Ring of Charos he pays 150
		ShopOffer charos = c.bestOffer("Grey wolf fur", 50, 10, true, new ShopPerks(0, true), 0, 1, 10);
		assertEquals(1500, charos.getTotal());
		assertTrue(charos.isUsesCharos());
		assertFalse(wolf.isUsesCharos());
		// Fritz the Glassblower: 20 for molten glass, 25 with the ring
		assertEquals(25, c.bestOffer("Molten glass", 3, 1, true, new ShopPerks(0, true), 0, 1, 10).getFirstUnit());
		// Baraek doesn't buy kyatt fur, so a 95% fur stall is best
		ShopOffer kyatt = c.bestOffer("Kyatt fur", 160, 3, true, new ShopPerks(0, false), 0, 1, 10);
		assertEquals(950, kyatt.getRate());
		assertTrue(kyatt.getShop().getName().contains("Fur"));
	}

	@Test
	public void canAvoidWildernessShops()
	{
		Shop bandits = shop("Bandit Duty Free", true, 600, 30, null, null, Collections.emptyMap());
		bandits.setWilderness(true);
		ShopCatalog c = new ShopCatalog(Arrays.asList(bandits,
			shop("General Store", true, 400, 30, null, null, Collections.emptyMap())), "test");

		assertEquals("Bandit Duty Free", c.bestOffer("Rune platebody", 65_000, 1, true, ShopPerks.NONE, 0, 1, 10).getShop().getName());
		ShopOffer safe = c.bestOffer("Rune platebody", 65_000, 1, true, new ShopPerks(0, false, true), 0, 1, 10);
		assertEquals("General Store", safe.getShop().getName());

		// The bundled data marks the Wilderness shops, but not Ferox Enclave, which is a safe zone
		ShopCatalog bundled = ShopCatalog.load(new Gson());
		assertTrue(bundled.findByName("Bandit Duty Free").isWilderness());
		assertTrue(bundled.findByName("Battle Runes").isWilderness());
		assertFalse(bundled.findByName("The Old Nite").isWilderness());
		assertFalse(bundled.findByName("Edgeville General Store").isWilderness());
	}

	@Test
	public void bundledDataUsesAliForRunes()
	{
		ShopCatalog c = ShopCatalog.load(new Gson());
		assertNotNull(c.getGenerated());
		// Without hopping, Ali's (no price drop) beats shops that pay more for the first few on each world
		for (String rune : new String[]{"Death rune", "Nature rune", "Fire rune"})
		{
			ShopOffer o = c.bestOffer(rune, 100, 1000, true, ShopPerks.NONE, 40, new SellRules(1, 1, 0), Collections.emptySet());
			assertTrue(rune + " -> " + o.getShop().getName(), o.getShop().getName().startsWith("Ali's Discount Wares"));
		}
	}

	@Test
	public void closestOfShopsPayingTheSame()
	{
		// Alice's (Port Phasmatys), Richard's, Sarah's and Vanessa's farming shops all pay the same
		ShopCatalog c = ShopCatalog.load(new Gson());
		ShopOffer falador = c.bestOffer("Plant cure", 25, 5, true, new ShopPerks(0, false, false, new WorldPoint(3013, 3355, 0)), 0, 1, 10);
		assertEquals("Sarah's Farming shop", falador.getShop().getName());
		assertTrue(falador.getEqualShops() >= 3);
		ShopOffer catherby = c.bestOffer("Plant cure", 25, 5, true, new ShopPerks(0, false, false, new WorldPoint(2809, 3440, 0)), 0, 1, 10);
		assertEquals("Vanessa's Farming shop", catherby.getShop().getName());
	}

	@Test
	public void obliBeatsTheKaramjaGeneralStore()
	{
		ShopCatalog bundled = ShopCatalog.load(new Gson());
		ShopCatalog c = new ShopCatalog(Arrays.asList(bundled.findByName("Karamja General Store"),
			bundled.findByName("Obli's General Store")), "test");
		// Standing at Musa Point, with gloves 3: both start at 60%, but Obli's drops 2% per item to Karamja's 3%
		ShopPerks musaPoint = new ShopPerks(3, false, false, new WorldPoint(2905, 3150, 0));
		assertEquals("Obli's General Store", c.bestOffer("Rune platebody", 65_000, 5, true, musaPoint, 0, 1, 10).getShop().getName());
		// Without gloves, Obli's pays 50% to Karamja's 40%
		ShopPerks noGloves = new ShopPerks(0, false, false, new WorldPoint(2905, 3150, 0));
		assertEquals("Obli's General Store", c.bestOffer("Rune platebody", 65_000, 1, true, noGloves, 0, 1, 10).getShop().getName());
	}

	@Test
	public void shopsPayingTheSameAreGrouped()
	{
		ShopCatalog c = ShopCatalog.load(new Gson());
		// Most general stores pay 40% dropping 3%: any of them will do
		assertEquals("General store", c.findByName("Rimmington General Store").getGroup());
		assertEquals("General store", c.findByName("Warrens General Store").getGroup());
		assertTrue(c.groupMembers("General store").size() > 30);
		// The Void Knight store drops only 0.3%, so it's a group of its own kind
		assertNotEquals("General store", c.findByName("Void Knight General Store").getGroup());
		assertEquals(c.findByName("Sarah's Farming shop").getGroup(), c.findByName("Vanessa's Farming shop").getGroup());
		// A shop like no other keeps its name
		assertEquals("Bandit Duty Free", c.findByName("Bandit Duty Free").getGroup());
		// The nearest of a group from Rimmington is Rimmington's
		assertEquals("Rimmington General Store", c.nearestInGroup("General store", new WorldPoint(2950, 3215, 0), false).getName());
		// From Catherby it's a members store, unless the player is on a free-to-play world
		WorldPoint catherby = new WorldPoint(2809, 3440, 0);
		assertTrue(c.nearestInGroup("General store", catherby, false).isMembers());
		assertFalse(c.nearestInGroup("General store", catherby, true).isMembers());
	}

	@Test
	public void membersShopsAreSkippedOnAFreeWorld()
	{
		Shop members = shop("Members Store", true, 600, 30, null, null, Collections.emptyMap());
		members.setMembers(true);
		ShopCatalog c = new ShopCatalog(Arrays.asList(members,
			shop("General Store", true, 400, 30, null, null, Collections.emptyMap())), "test");

		assertEquals("Members Store", c.bestOffer("Rune platebody", 65_000, 1, true, ShopPerks.NONE, 0, 1, 10).getShop().getName());
		ShopPerks f2p = new ShopPerks(0, false, false, null, Collections.emptySet(), true);
		assertEquals("General Store", c.bestOffer("Rune platebody", 65_000, 1, true, f2p, 0, 1, 10).getShop().getName());

		// The bundled data knows which shops are members-only
		ShopCatalog bundled = ShopCatalog.load(new Gson());
		assertFalse(bundled.findByName("Lumbridge General Store").isMembers());
		assertTrue(bundled.findByName("Arhein's General Goods").isMembers());
	}

	@Test
	public void sticksWithTheShopAlreadySuggested()
	{
		// Three shops paying the same: the player is near Near, but Far was suggested before they set off
		Shop near = shop("Near", true, 400, 0, null, null, Collections.emptyMap());
		near.setAt(new int[][]{{3200, 3200}});
		Shop far = shop("Far", true, 400, 0, null, null, Collections.emptyMap());
		far.setAt(new int[][]{{1600, 3600}});
		Shop here = shop("Here", true, 400, 0, null, null, Collections.emptyMap());
		here.setAt(new int[][]{{2000, 3000}});
		ShopCatalog c = new ShopCatalog(Arrays.asList(near, far, here), "test");
		WorldPoint walking = new WorldPoint(3100, 3200, 0);
		assertEquals("Near", c.bestOffer("Thing", 1000, 1, true, new ShopPerks(0, false, false, walking), 0, 1, 10).getShop().getName());
		ShopPerks onTheWay = new ShopPerks(0, false, false, walking, Collections.singleton("Far"));
		assertEquals("Far", c.bestOffer("Thing", 1000, 1, true, onTheWay, 0, 1, 10).getShop().getName());
		// ...unless they're standing at another that pays the same
		ShopPerks atHere = new ShopPerks(0, false, false, new WorldPoint(2005, 3000, 0), Collections.singleton("Far"));
		assertEquals("Here", c.bestOffer("Thing", 1000, 1, true, atHere, 0, 1, 10).getShop().getName());
	}

	@Test
	public void betterShopWinsHoweverFar()
	{
		Shop near = shop("Near", true, 400, 0, null, null, Collections.emptyMap());
		near.setAt(new int[][]{{3200, 3200}});
		Shop far = shop("Far", true, 600, 0, null, null, Collections.emptyMap());
		far.setAt(new int[][]{{1600, 3600}});
		ShopCatalog c = new ShopCatalog(Arrays.asList(near, far), "test");
		ShopPerks here = new ShopPerks(0, false, false, new WorldPoint(3200, 3201, 0));
		assertEquals("Far", c.bestOffer("Thing", 1000, 1, true, here, 0, 1, 10).getShop().getName());
		// Even paying slightly less, the near one doesn't win
		near.setBuyMultiplier(595);
		assertEquals("Far", c.bestOffer("Thing", 1000, 1, true, here, 0, 1, 10).getShop().getName());
		// ...only when they pay the same
		near.setBuyMultiplier(600);
		ShopOffer o = c.bestOffer("Thing", 1000, 1, true, here, 0, 1, 10);
		assertEquals("Near", o.getShop().getName());
		assertEquals(1, o.getEqualShops());
	}

	@Test
	public void howManyToSellCountsWhatTheShopAlreadyHolds()
	{
		// The Void Knight General Store pays 40% dropping 0.3% per item. An item worth 1,000 in shops and 379
		// elsewhere sells while the shop pays at least 380: 400, 397, ... 382, then 379 isn't worth it
		ShopCatalog c = ShopCatalog.load(new Gson());
		Shop store = c.findByName("Void Knight General Store");
		ShopCatalog.Rate rate = ShopCatalog.rateFor(store, "potato seed", true, ShopPerks.NONE);
		assertEquals(7, ShopCatalog.sellNow(1_000, rate, 0, 380, 500));
		// Someone else sold 5 there already: only 2 more
		assertEquals(2, ShopCatalog.sellNow(1_000, rate, 5, 380, 500));
		// After the 7th, it's time to hop
		assertEquals(0, ShopCatalog.sellNow(1_000, rate, 7, 380, 500));
		// Never more than you have
		assertEquals(3, ShopCatalog.sellNow(1_000, rate, 0, 380, 3));
	}
}
