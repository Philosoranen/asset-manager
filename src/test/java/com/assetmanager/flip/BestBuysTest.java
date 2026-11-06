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
			return Long.MAX_VALUE;
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
		assertTrue(BestBuys.find(BestBuys.Kind.COFFER, shops, p, beginner, PERKS, Long.MAX_VALUE, 1, 5).isEmpty());

		Unlocks hunterOnly = player(Map.of("HUNTER", 46), Collections.emptySet());
		List<BestBuys.Deal> deals = BestBuys.find(BestBuys.Kind.COFFER, shops, p, hunterOnly, PERKS, Long.MAX_VALUE, 1, 5);
		assertEquals(1, deals.size());
		assertEquals("mixed hide base", deals.get(0).getItem());

		Unlocks both = player(Map.of("HUNTER", 50), Set.of("THE_SLUG_MENACE"));
		assertEquals(2, BestBuys.find(BestBuys.Kind.COFFER, shops, p, both, PERKS, Long.MAX_VALUE, 1, 5).size());
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

		List<BestBuys.Deal> deals = BestBuys.find(BestBuys.Kind.COFFER, Collections.singletonList(armoury), p,
			player(Collections.emptyMap(), Collections.emptySet()), PERKS, Long.MAX_VALUE, 1, 5);
		assertEquals(1, deals.size());
		assertEquals("initiate harness m", deals.get(0).getItem());
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

		List<BestBuys.Deal> deals = BestBuys.find(BestBuys.Kind.GE, Collections.singletonList(shop), p,
			player(Collections.emptyMap(), Collections.emptySet()), PERKS, Long.MAX_VALUE, 1, 5);
		assertEquals(1, deals.size());
		assertEquals(Collections.singletonList("Combined Attack + Strength of 130"), deals.get(0).getUnchecked());
	}

	@Test
	public void bestShopPerItemMostGainedFirst()
	{
		Shop cheap = shop("Cheap", 1000, 0, null, Map.of("a", item(1, 5), "b", item(2, 5)));
		Shop dear = shop("Dear", 1300, 0, null, Map.of("a", item(1, 5)));
		FakePrices p = new FakePrices();
		p.value.put(1, 100);
		p.value.put(2, 100);
		p.ge.put(1, 200L);
		p.ge.put(2, 150L);

		List<BestBuys.Deal> deals = BestBuys.find(BestBuys.Kind.GE, Arrays.asList(dear, cheap), p, Unlocks.EVERYTHING, PERKS,
			Long.MAX_VALUE, 1, 5);
		assertEquals(2, deals.size());
		assertEquals("a", deals.get(0).getItem());
		assertEquals("Cheap", deals.get(0).getShop().getName());
		assertEquals(5 * 100, deals.get(0).getGain());
		assertEquals("b", deals.get(1).getItem());
	}

	@Test
	public void cofferDealsRankByPercentAndMustBeAffordable()
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

		List<BestBuys.Deal> rich = BestBuys.find(BestBuys.Kind.COFFER, shops, p, Unlocks.EVERYTHING, PERKS, Long.MAX_VALUE, 1, 5);
		assertEquals("bagged willow tree", rich.get(0).getItem());
		assertEquals(4_400, rich.get(0).getProfitEach());
		assertEquals(44.0, rich.get(0).getPercent(), 0.001);
		assertEquals("condensed gold", rich.get(1).getItem());

		// With 1m coins, condensed gold is out of reach and willows are bought only as far as the coins go
		List<BestBuys.Deal> poor = BestBuys.find(BestBuys.Kind.COFFER, shops, p, Unlocks.EVERYTHING, PERKS, 1_000_000, 1, 5);
		assertEquals(1, poor.size());
		assertEquals(20, poor.get(0).getFlip().getQuantity());
		List<BestBuys.Deal> broke = BestBuys.find(BestBuys.Kind.COFFER, shops, p, Unlocks.EVERYTHING, PERKS, 25_000, 1, 5);
		assertEquals(2, broke.get(0).getFlip().getQuantity());
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
