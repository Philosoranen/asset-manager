package com.assetmanager.flip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.Unlocks;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class BestBuysTest
{
	/** Prices for tests: coffer and GE values, store values and stackables by id. */
	private static class FakePrices implements BestBuys.Prices
	{
		final Map<Integer, Long> coffer = new HashMap<>();
		final Map<Integer, Long> ge = new HashMap<>();
		final Map<Integer, Integer> value = new HashMap<>();
		final Set<Integer> stackable = new HashSet<>();
		final Map<Integer, Long> volume = new HashMap<>();
		final Set<Integer> members = new HashSet<>();

		@Override
		public long coffer(int itemId)
		{
			return coffer.getOrDefault(itemId, 0L);
		}

		@Override
		public long ge(int itemId)
		{
			return ge.getOrDefault(itemId, 0L);
		}

		@Override
		public long geVolume(int itemId)
		{
			return volume.getOrDefault(itemId, Long.MAX_VALUE);
		}

		@Override
		public int storeValue(int itemId)
		{
			return value.getOrDefault(itemId, 0);
		}

		@Override
		public boolean stackable(int itemId)
		{
			return stackable.contains(itemId);
		}

		@Override
		public boolean members(int itemId)
		{
			return members.contains(itemId);
		}
	}

	private static Shop shop(String name, int sellRate, int delta, Shop.Requirements req, Map<String, Shop.Item> items)
	{
		Shop s = new Shop();
		s.setName(name);
		s.setLocation("");
		s.setSellMultiplier(sellRate);
		s.setDelta(delta);
		s.setReq(req);
		s.setItems(items);
		return s;
	}

	private static Shop.Item item(int id, int stock)
	{
		Shop.Item i = new Shop.Item();
		i.setId(id);
		i.setS(stock);
		return i;
	}

	private static Unlocks player(Map<String, Integer> skills, Set<String> quests)
	{
		return new Unlocks(skills, quests, Collections.emptySet(), Collections.emptySet(), 100, 100, true);
	}

	private static final ShopPerks PERKS = ShopPerks.NONE;

	@Test
	public void cofferDealBuysWhileTheCofferAddsMoreThanTheShopCharges()
	{
		// 10k at full stock, rising 3% of value per item bought; the coffer adds 12k each
		Shop garden = shop("Garden Centre", 1000, 30, null, Map.of("bagged willow tree", item(1, 20)));
		FakePrices p = new FakePrices();
		p.value.put(1, 10_000);
		p.coffer.put(1, 12_000L);

		BestBuys.Deal d = BestBuys.deal(BestBuys.Kind.COFFER, garden, "bagged willow tree", garden.getItems().get("bagged willow tree"),
			p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE);

		assertNotNull(d);
		// 10,000, 10,300, ... 11,800 (seven items); the eighth would cost 12,100
		assertEquals(7, d.getFlip().getQuantity());
		assertEquals(ShopFlip.Limit.PRICE, d.getFlip().getLimit());
		assertEquals(7 * 12_000 - (10_000 + 10_300 + 10_600 + 10_900 + 11_200 + 11_500 + 11_800), d.getGain());
	}

	@Test
	public void membersShopsAndItemsAreSkippedOnAFreeWorld()
	{
		Unlocks f2p = new Unlocks(null, null, null, null, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
		Shop garden = shop("Garden Centre", 1000, 30, null, Map.of("bagged willow tree", item(1, 20)));
		Shop.Item tree = garden.getItems().get("bagged willow tree");
		FakePrices p = new FakePrices();
		p.value.put(1, 10_000);
		p.coffer.put(1, 12_000L);

		assertNotNull(BestBuys.deal(BestBuys.Kind.COFFER, garden, "bagged willow tree", tree, p, f2p, PERKS, Long.MAX_VALUE));
		p.members.add(1);
		assertNull(BestBuys.deal(BestBuys.Kind.COFFER, garden, "bagged willow tree", tree, p, f2p, PERKS, Long.MAX_VALUE));
		p.members.clear();
		garden.setMembers(true);
		assertNull(BestBuys.deal(BestBuys.Kind.COFFER, garden, "bagged willow tree", tree, p, f2p, PERKS, Long.MAX_VALUE));
		assertNotNull(BestBuys.deal(BestBuys.Kind.COFFER, garden, "bagged willow tree", tree, p, Unlocks.EVERYTHING, PERKS,
			Long.MAX_VALUE));
	}

	@Test
	public void hoppingBuysAFewOnEachWorldWhileThePriceIsLow()
	{
		// As above, but over 10 worlds: 10 at 10,000, 10 at 10,300, then 8 at 10,600 fill the inventory
		Shop garden = shop("Garden Centre", 1000, 30, null, Map.of("bagged willow tree", item(1, 20)));
		FakePrices p = new FakePrices();
		p.value.put(1, 10_000);
		p.coffer.put(1, 12_000L);

		BestBuys.Deal d = BestBuys.deal(BestBuys.Kind.COFFER, garden, "bagged willow tree", garden.getItems().get("bagged willow tree"),
			p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 10);

		assertEquals(28, d.getFlip().getQuantity());
		assertEquals(ShopFlip.Limit.SPACE, d.getFlip().getLimit());
		assertEquals(10, d.getFlip().getWorlds());
		assertEquals(3, d.getFlip().getPerWorld());
		assertEquals(10 * 2_000 + 10 * 1_700 + 8 * 1_400, d.getGain());
	}

	@Test
	public void hoppingStopsWhenEveryWorldsStockIsBought()
	{
		Shop shop = shop("Small Shop", 1000, 30, null, Map.of("thing", item(1, 2)));
		FakePrices p = new FakePrices();
		p.value.put(1, 10_000);
		p.coffer.put(1, 20_000L);
		p.stackable.add(1);

		BestBuys.Deal d = BestBuys.deal(BestBuys.Kind.COFFER, shop, "thing", shop.getItems().get("thing"), p, Unlocks.EVERYTHING,
			PERKS, Long.MAX_VALUE, 3);
		assertEquals(6, d.getFlip().getQuantity());
		assertEquals(ShopFlip.Limit.WORLDS, d.getFlip().getLimit());

		// Bought by talking: no hopping
		shop.setTalk("Shopkeeper");
		d = BestBuys.deal(BestBuys.Kind.COFFER, shop, "thing", shop.getItems().get("thing"), p, Unlocks.EVERYTHING, PERKS,
			Long.MAX_VALUE, 3);
		assertEquals(2, d.getFlip().getQuantity());
		assertEquals(ShopFlip.Limit.STOCK, d.getFlip().getLimit());
		assertEquals(1, d.getFlip().getWorlds());
	}

	@Test
	public void setPriceIsUsedInsteadOfValue()
	{
		// Diango sells the disk for 21,000 though its value is 120; the coffer would only add ~12.8k
		Shop.Item disk = item(981, 7);
		disk.setSp(21_000);
		Shop diango = shop("Diango's Toy Store", 1500, 0, null, Map.of("disk of returning", disk));
		FakePrices p = new FakePrices();
		p.value.put(981, 120);
		p.coffer.put(981, 12_800L);

		assertNull(BestBuys.deal(BestBuys.Kind.COFFER, diango, "disk of returning", disk, p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE));

		p.coffer.put(981, 22_000L);
		BestBuys.Deal d = BestBuys.deal(BestBuys.Kind.COFFER, diango, "disk of returning", disk, p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE);
		assertNotNull(d);
		assertEquals(21_000, d.getFlip().getFirstPrice());
	}

	@Test
	public void unstackableItemsStopAtAFullInventory()
	{
		Shop stonemason = shop("Keldagrim Stonemason", 1300, 0, null, Map.of("marble block", item(8786, -1)));
		FakePrices p = new FakePrices();
		p.value.put(8786, 250_000);
		p.coffer.put(8786, 354_000L);

		BestBuys.Deal d = BestBuys.deal(BestBuys.Kind.COFFER, stonemason, "marble block", stonemason.getItems().get("marble block"),
			p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE);

		assertEquals(BestBuys.INVENTORY_SIZE, d.getFlip().getQuantity());
		assertEquals(ShopFlip.Limit.SPACE, d.getFlip().getLimit());
	}

	/** Each shop's go, hopping up to 5 worlds, most profitable first. */
	private static List<FlipRun> runs(BestBuys.Kind kind, List<Shop> shops, FakePrices p, Unlocks unlocks, long coins)
	{
		return Flips.rank(BestBuys.runs(kind, shops, p, unlocks, PERKS, coins, 5, false, 8), 1, 10, FlipSort.PROFIT);
	}

	@Test
	public void shopsNeedingWhatThePlayerHasntDoneAreSkipped()
	{
		Shop.Requirements slugMenace = new Shop.Requirements();
		slugMenace.setQuests(Collections.singletonList("THE_SLUG_MENACE"));
		Shop.Requirements hunter = new Shop.Requirements();
		hunter.setSkills(Map.of("HUNTER", 46));
		Shop armoury = shop("Proselyte Temple Knight Armoury", 1000, 0, slugMenace, Map.of("proselyte harness f", item(9676, 30)));
		Shop furs = shop("Pellem's Fur Store", 1300, 20, hunter, Map.of("mixed hide base", item(29110, 10)));
		FakePrices p = new FakePrices();
		p.value.put(9676, 25_000);
		p.coffer.put(9676, 30_900L);
		p.value.put(29110, 9_000);
		p.coffer.put(29110, 14_800L);
		List<Shop> shops = Arrays.asList(armoury, furs);

		Unlocks beginner = player(Map.of("HUNTER", 10), Collections.emptySet());
		assertTrue(runs(BestBuys.Kind.COFFER, shops, p, beginner, Long.MAX_VALUE).isEmpty());

		Unlocks hunterOnly = player(Map.of("HUNTER", 46), Collections.emptySet());
		List<FlipRun> runs = runs(BestBuys.Kind.COFFER, shops, p, hunterOnly, Long.MAX_VALUE);
		assertEquals(1, runs.size());
		assertEquals("mixed hide base", runs.get(0).getFlips().get(0).getItem());

		Unlocks both = player(Map.of("HUNTER", 50), Set.of("THE_SLUG_MENACE"));
		assertEquals(2, runs(BestBuys.Kind.COFFER, shops, p, both, Long.MAX_VALUE).size());
	}

	@Test
	public void itemRequirementsAddToTheShops()
	{
		Shop.Requirements slugMenace = new Shop.Requirements();
		slugMenace.setQuests(Collections.singletonList("THE_SLUG_MENACE"));
		Shop.Item proselyte = item(9676, 30);
		proselyte.setReq(slugMenace);
		Shop.Item initiate = item(5575, 30);
		Shop armoury = shop("Armoury", 1000, 0, null, Map.of("proselyte harness f", proselyte, "initiate harness m", initiate));
		FakePrices p = new FakePrices();
		p.value.put(9676, 25_000);
		p.coffer.put(9676, 30_900L);
		p.value.put(5575, 20_000);
		p.coffer.put(5575, 21_000L);

		List<FlipRun> runs = runs(BestBuys.Kind.COFFER, Collections.singletonList(armoury), p,
			player(Collections.emptyMap(), Collections.emptySet()), Long.MAX_VALUE);
		assertEquals(1, runs.size());
		assertEquals(1, runs.get(0).getFlips().size());
		assertEquals("initiate harness m", runs.get(0).getFlips().get(0).getItem());
	}

	@Test
	public void requirementsThePluginCantCheckAreShownNotHidden()
	{
		Shop.Requirements guild = new Shop.Requirements();
		guild.setOther(Collections.singletonList("Combined Attack + Strength of 130"));
		Shop shop = shop("Warrior Guild Armoury", 1000, 0, guild, Map.of("rune defender", item(8850, 5)));
		FakePrices p = new FakePrices();
		p.value.put(8850, 1000);
		p.ge.put(8850, 5000L);

		List<FlipRun> runs = runs(BestBuys.Kind.GE, Collections.singletonList(shop), p,
			player(Collections.emptyMap(), Collections.emptySet()), Long.MAX_VALUE);
		assertEquals(1, runs.size());
		assertEquals(Collections.singletonList("Combined Attack + Strength of 130"), runs.get(0).getFlips().get(0).getUnchecked());
	}

	@Test
	public void everythingWorthBuyingAtAShopIsOneGo()
	{
		Shop cheap = shop("Cheap", 1000, 0, null, Map.of("a", item(1, 5), "b", item(2, 5)));
		Shop dear = shop("Dear", 1300, 0, null, Map.of("a", item(1, 5)));
		FakePrices p = new FakePrices();
		p.value.put(1, 100);
		p.value.put(2, 100);
		p.ge.put(1, 200L);
		p.ge.put(2, 150L);
		p.stackable.add(1);
		p.stackable.add(2);

		List<FlipRun> runs = runs(BestBuys.Kind.GE, Arrays.asList(dear, cheap), p, Unlocks.EVERYTHING, Long.MAX_VALUE);
		assertEquals(2, runs.size());
		FlipRun best = runs.get(0);
		assertEquals("Cheap", best.getShop().getName());
		assertEquals(2, best.getFlips().size());
		// 5 a world on each of 5 worlds, of each item
		assertEquals(25 * 100 + 25 * 50, best.getProfit());
		assertEquals("a", best.getFlips().get(0).getItem());
		assertEquals(5, best.getWorlds());
		assertEquals("Dear", runs.get(1).getShop().getName());
	}

	@Test
	public void itemsAtAShopShareTheInventoryAndCoins()
	{
		// Unstackables: 28 slots between them, the better one first (4 a world on 5 worlds)
		Shop shop = shop("Shop", 1000, 0, null, Map.of("good", item(1, 4), "ok", item(2, 20)));
		FakePrices p = new FakePrices();
		p.value.put(1, 100);
		p.value.put(2, 100);
		p.coffer.put(1, 300L);
		p.coffer.put(2, 150L);

		FlipRun r = runs(BestBuys.Kind.COFFER, Collections.singletonList(shop), p, Unlocks.EVERYTHING, Long.MAX_VALUE).get(0);
		assertEquals(28, r.getQuantity());
		assertEquals(20, r.getFlips().get(0).getQuantity());
		assertEquals(8, r.getFlips().get(1).getQuantity());
		assertEquals(ShopFlip.Limit.SPACE, r.getFlips().get(1).getLimit());

		// 2,500 coins buy 25
		r = runs(BestBuys.Kind.COFFER, Collections.singletonList(shop), p, Unlocks.EVERYTHING, 2_500).get(0);
		assertEquals(25, r.getQuantity());
		assertEquals(ShopFlip.Limit.COINS, r.getFlips().get(1).getLimit());
	}

	@Test
	public void cofferRunsRankByPercentAndMustBeAffordable()
	{
		// Condensed gold gains more per visit, but bagged willows return a higher % of what they cost
		Shop stonemason = shop("Keldagrim Stonemason", 1000, 0, null, Map.of("condensed gold", item(1, 10)));
		Shop garden = shop("Garden Centre", 1000, 0, null, Map.of("bagged willow tree", item(2, 20)));
		FakePrices p = new FakePrices();
		p.value.put(1, 10_000_000);
		p.coffer.put(1, 10_500_000L);
		p.value.put(2, 10_000);
		p.coffer.put(2, 14_400L);
		List<Shop> shops = Arrays.asList(stonemason, garden);

		List<FlipRun> rich = Flips.rank(BestBuys.runs(BestBuys.Kind.COFFER, shops, p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 1,
			false, 8), 1, 10, FlipSort.RETURN);
		assertEquals("Garden Centre", rich.get(0).getShop().getName());
		assertEquals(44.0, rich.get(0).getReturnPercent(), 0.001);
		assertEquals("Keldagrim Stonemason", rich.get(1).getShop().getName());

		// With 1m coins, condensed gold is out of reach and willows are bought only as far as the coins go
		List<FlipRun> poor = BestBuys.runs(BestBuys.Kind.COFFER, shops, p, Unlocks.EVERYTHING, PERKS, 1_000_000, 1, false, 8);
		assertEquals(1, poor.size());
		assertEquals(20, poor.get(0).getQuantity());
		List<FlipRun> broke = BestBuys.runs(BestBuys.Kind.COFFER, shops, p, Unlocks.EVERYTHING, PERKS, 25_000, 1, false, 8);
		assertEquals(2, broke.get(0).getQuantity());
	}

	@Test
	public void cheapShopItemsWorthTheCofferMinimumAreFound()
	{
		// Costs 150 at the shop, but its guide price is over Death's 10k minimum
		Shop gnome = shop("Gianne's Restaurant", 1000, 10, null, Map.of("premade worm hole", item(2233, 3)));
		FakePrices p = new FakePrices();
		p.value.put(2233, 150);
		p.coffer.put(2233, 12_000L);

		List<FlipRun> runs = BestBuys.runs(BestBuys.Kind.COFFER, Collections.singletonList(gnome), p, Unlocks.EVERYTHING, PERKS,
			Long.MAX_VALUE, 1, false, 8);
		assertEquals(1, runs.size());
		assertEquals(150, runs.get(0).getFlips().get(0).getFirstCost());
		assertEquals(3, runs.get(0).getQuantity());
	}

	@Test
	public void geRunsBuyOnlyWhatSellsInTheWait()
	{
		Shop shop = shop("Shop", 1000, 0, null, Map.of("slow", item(1, 20), "dead", item(2, 20), "busy", item(3, 20)));
		FakePrices p = new FakePrices();
		for (int id = 1; id <= 3; id++)
		{
			p.value.put(id, 1000);
			p.ge.put(id, 5000L);
			p.stackable.add(id);
		}
		p.volume.put(1, 3L);
		p.volume.put(2, 0L);
		p.volume.put(3, 500L);

		FlipRun r = BestBuys.runs(BestBuys.Kind.GE, Collections.singletonList(shop), p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 1,
			false, 8).get(0);
		assertEquals(2, r.getFlips().size());
		assertEquals("busy", r.getFlips().get(0).getItem());
		assertEquals(20, r.getFlips().get(0).getQuantity());
		assertEquals(500, r.getFlips().get(0).getGeTrades());
		assertEquals("slow", r.getFlips().get(1).getItem());
		assertEquals(3, r.getFlips().get(1).getQuantity());
		assertEquals(ShopFlip.Limit.VOLUME, r.getFlips().get(1).getLimit());
		// Selling takes the hour the player waits
		assertEquals(3600, r.getSeconds(), 0.001);
	}

	@Test
	public void shopBuysForTheGeTakeNoMoreItemsThanItHasSlotsFor()
	{
		Map<String, Shop.Item> stock = new HashMap<>();
		FakePrices p = new FakePrices();
		for (int id = 1; id <= 10; id++)
		{
			stock.put("item " + id, item(id, 5));
			p.value.put(id, 100);
			p.ge.put(id, 100L + id * 10);
			p.stackable.add(id);
		}
		Shop shop = shop("Shop", 1000, 0, null, stock);

		FlipRun r = BestBuys.runs(BestBuys.Kind.GE, Collections.singletonList(shop), p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 1,
			false, 8).get(0);
		assertEquals(8, r.getFlips().size());
		// The two making least are left out
		assertTrue(r.getFlips().stream().noneMatch(f -> f.getItemId() <= 2));
		// The coffer isn't the GE, so there's no limit there
		p.coffer.putAll(p.ge);
		r = BestBuys.runs(BestBuys.Kind.COFFER, Collections.singletonList(shop), p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 1,
			false, 8).get(0);
		assertEquals(10, r.getFlips().size());
	}

	@Test
	public void tradersTakeAConversationPerBundle()
	{
		// Like Wyson: 2 woad leaves for 20 a conversation, unstackable, never runs out
		Shop wyson = shop("Wyson the gardener", 1000, 0, null, Map.of("woad leaf", item(1793, -1)));
		wyson.setTalk("Wyson the gardener");
		Shop.Item leaf = wyson.getItems().get("woad leaf");
		leaf.setSp(10);
		leaf.setB(new int[]{2, 20});
		FakePrices p = new FakePrices();
		p.value.put(1793, 1);
		p.ge.put(1793, 50L);

		FlipRun r = BestBuys.runs(BestBuys.Kind.GE, Collections.singletonList(wyson), p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 0,
			true, 8).get(0);
		assertEquals(28, r.getQuantity());
		assertEquals(1, r.getWorlds());
		// A trip and 14 conversations, but the GE's hour is longer
		assertEquals(3600, r.getSeconds(), 0.001);

		// To the coffer there's no GE wait: 28 bought in 14 conversations
		p.coffer.put(1793, 12_000L);
		r = BestBuys.runs(BestBuys.Kind.COFFER, Collections.singletonList(wyson), p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 0,
			true, 8).get(0);
		assertEquals(FlipTime.TRIP_SECONDS + 14 * FlipTime.TALK_SECONDS, r.getSeconds(), 0.001);
	}

	@Test
	public void forTheMostAnHourHopFewerWorldsAndBuyDeeper()
	{
		// A stackable with 10 in stock, its price rising 5% of value per one bought, and no limit on worlds
		Shop shop = shop("Rune Shop", 1000, 50, null, Map.of("rune", item(1, 10)));
		FakePrices p = new FakePrices();
		p.value.put(1, 100);
		p.coffer.put(1, 200L);
		p.stackable.add(1);

		FlipRun most = BestBuys.runs(BestBuys.Kind.COFFER, Collections.singletonList(shop), p, Unlocks.EVERYTHING, PERKS,
			Long.MAX_VALUE, 0, false, 8).get(0);
		FlipRun quickest = BestBuys.runs(BestBuys.Kind.COFFER, Collections.singletonList(shop), p, Unlocks.EVERYTHING, PERKS,
			Long.MAX_VALUE, 0, true, 8).get(0);
		// Hopping without limit, each of the 10,000 is bought on a fresh world
		assertEquals(BestBuys.MAX_STACK, most.getWorlds());
		assertTrue(quickest.getWorlds() < most.getWorlds());
		assertTrue(quickest.getGpPerHour() > most.getGpPerHour());
		assertTrue(quickest.getProfit() < most.getProfit());
		assertEquals(ShopFlip.Limit.QUICKER, quickest.getFlips().get(0).getLimit());
	}

	@Test
	public void allBundledShopsAreQuickEvenFindingTheFastestHops()
	{
		ShopCatalog catalog = ShopCatalog.load(new Gson());
		FakePrices p = new FakePrices();
		for (Shop shop : catalog.getShops())
		{
			for (Shop.Item item : shop.getItems().values())
			{
				Integer id = item.getId();
				if (id != null)
				{
					// Everything worth twice its value, and half of it stackable: far more than really is
					p.value.put(id, 100 + id % 1000);
					p.coffer.put(id, 2L * (100 + id % 1000));
					if (id % 2 == 0)
					{
						p.stackable.add(id);
					}
				}
			}
		}
		long start = System.nanoTime();
		List<FlipRun> runs = BestBuys.runs(BestBuys.Kind.COFFER, catalog.getShops(), p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE,
			ShopCatalog.NO_WORLD_LIMIT, true, 8);
		long ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("runs over " + catalog.getShops().size() + " shops: " + ms + " ms, " + runs.size() + " runs");
		assertTrue("took " + ms + " ms", ms < 5_000);
	}

	@Test
	public void bundledDataHasIdsAndRequirements()
	{
		ShopCatalog catalog = ShopCatalog.load(new Gson());
		Shop armoury = catalog.findByName("Proselyte Temple Knight Armoury");
		assertNotNull(armoury.getReq());
		assertTrue(armoury.getReq().getQuests().contains("THE_SLUG_MENACE"));
		Shop.Item disk = catalog.findByName("Diango's Toy Store").getItems().get("disk of returning");
		assertEquals(Integer.valueOf(21_000), disk.getSp());
		assertNotNull(disk.getId());
	}
}
