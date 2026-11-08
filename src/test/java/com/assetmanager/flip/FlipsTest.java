package com.assetmanager.flip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.assetmanager.AccountMode;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.Unlocks;
import com.google.gson.Gson;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class FlipsTest
{
	private static class FakeMarket implements Flips.GeMarket
	{
		final Map<Integer, Long> buy = new HashMap<>();
		final Map<Integer, Long> trades = new HashMap<>();
		final Map<Integer, Integer> limit = new HashMap<>();
		final Map<Integer, String> name = new HashMap<>();
		final Map<Integer, Integer> value = new HashMap<>();
		final Map<Integer, Long> coffer = new HashMap<>();
		final Map<Integer, Long> alch = new HashMap<>();
		final Set<Integer> unalchable = new HashSet<>();
		double hours = 1;

		FakeMarket add(int id, String n, long cost, long traded, int buyLimit, int storeValue)
		{
			buy.put(id, cost);
			trades.put(id, traded);
			limit.put(id, buyLimit);
			name.put(id, n);
			value.put(id, storeValue);
			return this;
		}

		@Override
		public Collection<Integer> items()
		{
			return buy.keySet();
		}

		@Override
		public long buy(int itemId)
		{
			return buy.get(itemId);
		}

		@Override
		public long trades(int itemId)
		{
			return trades.get(itemId);
		}

		@Override
		public int buyLimit(int itemId)
		{
			return limit.get(itemId);
		}

		@Override
		public String name(int itemId)
		{
			return name.get(itemId);
		}

		@Override
		public int storeValue(int itemId)
		{
			return value.get(itemId);
		}

		@Override
		public long coffer(int itemId)
		{
			return coffer.getOrDefault(itemId, 0L);
		}

		@Override
		public long alch(int itemId)
		{
			return alch.getOrDefault(itemId, 0L);
		}

		@Override
		public boolean alchable(int itemId)
		{
			return !unalchable.contains(itemId);
		}

		@Override
		public double geHours()
		{
			return hours;
		}
	}

	/** A shop buying at {@code buyRate}, its price dropping by delta per item sold. */
	private static Shop buyer(String name, int buyRate, int delta, String item)
	{
		Shop s = new Shop();
		s.setName(name);
		s.setLocation("");
		s.setBuyMultiplier(buyRate);
		s.setSellMultiplier(1000);
		s.setDelta(delta);
		Shop.Item i = new Shop.Item();
		i.setS(0);
		s.setItems(Map.of(item, i));
		return s;
	}

	private static ShopCatalog catalog(Shop... shops)
	{
		return new ShopCatalog(List.of(shops), "test");
	}

	@Test
	public void geToAShopWhosePriceNeverDropsStopsAtTheBuyLimit()
	{
		// Like rune cannonballs: the stall pays 50% of value (400) and never less; the GE sells them for 350
		ShopCatalog shops = catalog(buyer("Cannonball Stall", 500, 0, "rune cannonball"));
		FakeMarket m = new FakeMarket().add(1, "rune cannonball", 350, 50_000, 11_000, 800);

		List<FlipRun> runs = Flips.fromGe(m, Flip.Dest.SHOP, shops, Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, Flips.ALCHS_PER_HOUR, 8);

		assertEquals(1, runs.size());
		Flip f = first(runs);
		assertEquals(Flip.Dest.SHOP, f.getDest());
		assertEquals(11_000, f.getQuantity());
		assertEquals(ShopFlip.Limit.BUY_LIMIT, f.getLimit());
		assertEquals(11_000L * 50, f.getProfit());
		assertEquals("Cannonball Stall", f.getShop().getName());
	}

	@Test
	public void geToShopStopsAtTheWorldsThePlayerWouldHop()
	{
		// Pays 500 then 1% of value less per item, so 9 sell per world before the price is under the GE's 410
		ShopCatalog shops = catalog(buyer("Dropper", 500, 10, "widget"));
		FakeMarket m = new FakeMarket().add(1, "widget", 410, 1_000, 0, 1_000);

		Flip f = first(Flips.fromGe(m, Flip.Dest.SHOP, shops, Unlocks.EVERYTHING, ShopPerks.NONE, 2, Long.MAX_VALUE, 0, 8));

		assertEquals(ShopFlip.Limit.WORLDS, f.getLimit());
		assertEquals(18, f.getQuantity());
		assertEquals(2, f.getSellOffer().getWorlds());
	}

	@Test
	public void shopsThePlayerCantUseAreSkipped()
	{
		Shop stall = buyer("Cannonball Stall", 500, 0, "rune cannonball");
		Shop.Requirements req = new Shop.Requirements();
		req.setSkills(Map.of("SAILING", 50));
		stall.setReq(req);
		FakeMarket m = new FakeMarket().add(1, "rune cannonball", 350, 50_000, 11_000, 800);
		Unlocks lowSailing = new Unlocks(Map.of("SAILING", 49), Collections.emptySet(), Collections.emptySet(),
			Collections.emptySet(), 100, 100, true);

		assertTrue(Flips.fromGe(m, Flip.Dest.SHOP, catalog(stall), lowSailing, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 8).isEmpty());
	}

	@Test
	public void shopsWontBuyItemsThatCantBeAlched()
	{
		// Event rewards have a store value only to protect them on death
		Shop store = buyer("General Store", 400, 30, "other");
		store.setGeneral(true);
		FakeMarket m = new FakeMarket().add(1, "grid master altar icon scroll", 64_800, 1, 0, 2_500_000);
		m.unalchable.add(1);

		assertTrue(Flips.fromGe(m, Flip.Dest.SHOP, catalog(store), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 8).isEmpty());
	}

	@Test
	public void tradersOnlyTakeAnInventoryOfUnnotedItems()
	{
		// The silk merchant pays 60 but only for unnoted silk; a stall pays 15 (50% of 30) for any amount, noted
		Shop merchant = new Shop();
		merchant.setName("Silk merchant");
		merchant.setLocation("");
		merchant.setTalk("Silk merchant");
		merchant.setBuyMultiplier(1000);
		Shop.Item silk = new Shop.Item();
		silk.setP(60);
		merchant.setItems(Map.of("silk", silk));
		Shop stall = buyer("Silk Stall", 500, 0, "silk");
		FakeMarket m = new FakeMarket().add(1, "silk", 11, 9_152, 18_000, 30);

		// Each shop is a go of its own; the stall makes more in one, selling noted
		List<FlipRun> runs = Flips.rank(Flips.fromGe(m, Flip.Dest.SHOP, catalog(merchant, stall), Unlocks.EVERYTHING, ShopPerks.NONE,
			10, Long.MAX_VALUE, 0, 8), 1, 10, FlipSort.PROFIT);
		assertEquals(2, runs.size());
		Flip f = first(runs);
		assertEquals("Silk Stall", f.getShop().getName());
		assertEquals(9_152, f.getQuantity());
		assertEquals(9_152L * 4, f.getProfit());
		assertEquals("Silk merchant", runs.get(1).getShop().getName());

		// With only the merchant, it's one inventory
		f = first(Flips.fromGe(m, Flip.Dest.SHOP, catalog(merchant), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 8));
		assertEquals(BestBuys.INVENTORY_SIZE, f.getQuantity());
		assertEquals(ShopFlip.Limit.SPACE, f.getLimit());
	}

	@Test
	public void requirementsTheShopHasThatCantBeCheckedAreShown()
	{
		Shop lunderwin = buyer("Lunderwin", 1000, 0, "cabbage");
		Shop.Requirements req = new Shop.Requirements();
		req.setOther(List.of("A diamond to get to her"));
		lunderwin.setReq(req);
		FakeMarket m = new FakeMarket().add(1, "cabbage", 36, 6_000, 6_000, 100);

		Flip f = first(Flips.fromGe(m, Flip.Dest.SHOP, catalog(lunderwin), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 8));
		assertEquals(List.of("A diamond to get to her"), f.getUnchecked());
	}

	@Test
	public void cofferFlipsDontStopWhenTheCofferIsFull()
	{
		// The player chose the coffer, so it's filled however full it already is
		FakeMarket m = new FakeMarket().add(1, "dragon thing", 100_000, 500, 100, 0);
		m.coffer.put(1, 110_000L);

		Flip f = first(Flips.fromGe(m, Flip.Dest.COFFER, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 8));
		assertEquals(Flip.Dest.COFFER, f.getDest());
		assertEquals(100, f.getQuantity());
		assertEquals(ShopFlip.Limit.BUY_LIMIT, f.getLimit());
	}

	@Test
	public void onlyTheChosenWayToCashOutIsListed()
	{
		// Alching would make more, but the player wants coffer flips
		FakeMarket m = new FakeMarket().add(1, "dragon thing", 100_000, 500, 100, 0);
		m.coffer.put(1, 110_000L);
		m.alch.put(1, 200_000L);

		List<FlipRun> coffer = Flips.fromGe(m, Flip.Dest.COFFER, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8);
		assertEquals(1, coffer.size());
		assertEquals(Flip.Dest.COFFER, coffer.get(0).getDest());
		assertTrue(Flips.fromGe(m, Flip.Dest.SHOP, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8).isEmpty());
		assertTrue(Flips.fromGe(m, Flip.Dest.GE, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8).isEmpty());
	}

	@Test
	public void ironmenOnlyFlipToTheCofferAndUltimatesToAlching()
	{
		assertEquals(List.of(Flip.Dest.ALCH, Flip.Dest.GE, Flip.Dest.SHOP, Flip.Dest.COFFER), Flips.destinations(AccountMode.STANDARD));
		assertEquals(List.of(Flip.Dest.COFFER), Flips.destinations(AccountMode.IRONMAN));
		assertEquals(List.of(Flip.Dest.ALCH), Flips.destinations(AccountMode.ULTIMATE_IRONMAN));
		assertEquals(Flip.Dest.COFFER, Flips.destination(AccountMode.IRONMAN, Flip.Dest.ALCH));
		assertEquals(Flip.Dest.ALCH, Flips.destination(AccountMode.STANDARD, Flip.Dest.ALCH));
	}

	@Test
	public void returnSortPutsTheBestUseOfEachCoinFirst()
	{
		// A big flip at 5% against a small one at 50%
		FlipRun big = FlipRun.single(new Flip(Flip.Source.SHOP, Flip.Dest.COFFER, "big", 1, null, null, 100, 1_000_000, 1_050_000,
			10_000, 10_500, ShopFlip.Limit.STOCK, Collections.emptyList(), Long.MAX_VALUE, false, 1), 60);
		FlipRun small = FlipRun.single(new Flip(Flip.Source.SHOP, Flip.Dest.COFFER, "small", 2, null, null, 2, 20_000, 30_000,
			10_000, 15_000, ShopFlip.Limit.STOCK, Collections.emptyList(), Long.MAX_VALUE, false, 1), 60);

		assertEquals(List.of(big, small), Flips.rank(List.of(small, big), 500, 10, FlipSort.PROFIT));
		assertEquals(List.of(small, big), Flips.rank(List.of(big, small), 500, 10, FlipSort.RETURN));
		assertEquals(50.0, small.getReturnPercent(), 0.001);
	}

	@Test
	public void gpPerHourPutsQuickGosFirst()
	{
		// Makes 1m but takes 5 hours, against 300k in an hour
		FlipRun slow = run(1, 1_000_000, 5 * 3600);
		FlipRun quick = run(2, 300_000, 3600);

		assertEquals(List.of(slow, quick), Flips.rank(List.of(quick, slow), 1, 10, FlipSort.PROFIT));
		assertEquals(List.of(quick, slow), Flips.rank(List.of(slow, quick), 1, 10, FlipSort.GP_PER_HOUR));
		assertEquals(200_000, slow.getGpPerHour(), 0.001);
	}

	@Test
	public void geGosTakeAtLeastTheGesWait()
	{
		FakeMarket m = new FakeMarket().add(1, "rune thing", 1_000, 40, 10_000, 0);
		m.alch.put(1, 1_200L);
		m.hours = 4;
		// 40 alchs are 2 minutes, but buying them takes the 4 hours the player waits
		FlipRun r = Flips.fromGe(m, Flip.Dest.ALCH, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8).get(0);
		assertEquals(4 * 3600, r.getSeconds(), 0.001);

		// Alching a big stack takes longer than the wait
		m.trades.put(1, 1_000L);
		m.hours = 0.25;
		r = Flips.fromGe(m, Flip.Dest.ALCH, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8).get(0);
		assertEquals(1_000 * FlipTime.ALCH_SECONDS, r.getSeconds(), 0.001);

		// A round stopped by the buy limit can't be repeated for 4 hours
		m.limit.put(1, 100);
		m.hours = 1;
		r = Flips.fromGe(m, Flip.Dest.ALCH, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8).get(0);
		assertEquals(ShopFlip.Limit.BUY_LIMIT, r.getFlips().get(0).getLimit());
		assertEquals(4 * 3600, r.getSeconds(), 0.001);
	}

	@Test
	public void aShopBuyingSeveralItemsIsOneGo()
	{
		// The stall pays 50% of value for both, never less, and hopping isn't needed
		Shop stall = buyer("Stall", 500, 0, "a");
		Shop.Item b = new Shop.Item();
		b.setS(0);
		stall.setItems(Map.of("a", stall.getItems().get("a"), "b", b));
		FakeMarket m = new FakeMarket().add(1, "a", 350, 100, 0, 800).add(2, "b", 100, 100, 0, 400);

		List<FlipRun> runs = Flips.fromGe(m, Flip.Dest.SHOP, catalog(stall), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 8);
		assertEquals(1, runs.size());
		FlipRun r = runs.get(0);
		assertEquals(2, r.getFlips().size());
		assertEquals(100L * 50 + 100L * 100, r.getProfit());
		// "b" returns more for each coin, so it's bought first when coins are short
		assertEquals("b", r.getFlips().get(0).getItem());

		r = Flips.fromGe(m, Flip.Dest.SHOP, catalog(stall), Unlocks.EVERYTHING, ShopPerks.NONE, 10, 100L * 100 + 350 * 10, 0, 8).get(0);
		assertEquals(110, r.getQuantity());
		Flip a = r.getFlips().stream().filter(f -> f.getItem().equals("a")).findFirst().get();
		assertEquals(10, a.getQuantity());
		assertEquals(ShopFlip.Limit.COINS, a.getLimit());
	}

	@Test
	public void aGoSellsNoMoreItemsThanTheGeHasSlotsFor()
	{
		// A general store paying more than the GE charges for 12 items: only the 8 making the most are bought
		Shop store = buyer("General Store", 500, 0, "other");
		store.setGeneral(true);
		FakeMarket m = new FakeMarket();
		for (int id = 1; id <= 12; id++)
		{
			m.add(id, "item " + id, 300, 100, 0, 1_000 + id * 100);
		}

		FlipRun r = Flips.fromGe(m, Flip.Dest.SHOP, catalog(store), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 8).get(0);
		assertEquals(8, r.getFlips().size());
		assertTrue(r.getFlips().stream().noneMatch(f -> f.getItemId() <= 4));
		assertEquals(3, Flips.fromGe(m, Flip.Dest.SHOP, catalog(store), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 0, 3)
			.get(0).getFlips().size());
	}

	@Test
	public void onlyTheBestGeneralStoreIsListed()
	{
		// Two general stores paying differently for the same things, and a specialty shop
		Shop good = buyer("Good General Store", 600, 0, "other");
		good.setGeneral(true);
		Shop poor = buyer("Poor General Store", 550, 0, "other");
		poor.setGeneral(true);
		Shop stall = buyer("Stall", 500, 0, "b");
		FakeMarket m = new FakeMarket().add(1, "a", 300, 100, 0, 1_000).add(2, "b", 100, 100, 0, 400);

		List<FlipRun> runs = Flips.rank(Flips.fromGe(m, Flip.Dest.SHOP, catalog(good, poor, stall), Unlocks.EVERYTHING, ShopPerks.NONE,
			10, Long.MAX_VALUE, 0, 8), 1, 10, FlipSort.PROFIT);
		assertEquals(2, runs.size());
		assertEquals("Good General Store", runs.get(0).getShop().getName());
		assertEquals("Stall", runs.get(1).getShop().getName());
	}

	@Test
	public void shopsWhereTheGoIsTheSameAreMerged()
	{
		Shop near = buyer("Near", 500, 0, "a");
		near.setAt(new int[][]{{3200, 3200}});
		Shop far = buyer("Far", 500, 0, "a");
		far.setAt(new int[][]{{2000, 2000}});
		FakeMarket m = new FakeMarket().add(1, "a", 350, 100, 0, 800);
		ShopPerks here = new ShopPerks(0, false, false, new WorldPoint(3210, 3210, 0));

		List<FlipRun> runs = Flips.fromGe(m, Flip.Dest.SHOP, catalog(far, near), Unlocks.EVERYTHING, here, 10, Long.MAX_VALUE, 0, 8);
		assertEquals(1, runs.size());
		assertEquals("Near", runs.get(0).getShop().getName());
		assertEquals(1, runs.get(0).getEqualShops());
	}

	@Test
	public void geFlipsAreLimitedByWhatTradesCoinsAndAlchs()
	{
		FakeMarket m = new FakeMarket().add(1, "rune thing", 1_000, 40, 10_000, 0);
		m.alch.put(1, 1_200L);
		Flip f = first(Flips.fromGe(m, Flip.Dest.ALCH, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8));
		assertEquals(Flip.Dest.ALCH, f.getDest());
		assertEquals(40, f.getQuantity());
		assertEquals(ShopFlip.Limit.VOLUME, f.getLimit());

		f = first(Flips.fromGe(m, Flip.Dest.ALCH, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, 25_500, 1_200, 8));
		assertEquals(25, f.getQuantity());
		assertEquals(ShopFlip.Limit.COINS, f.getLimit());

		f = first(Flips.fromGe(m, Flip.Dest.ALCH, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 10, 8));
		assertEquals(10, f.getQuantity());
		assertEquals(ShopFlip.Limit.CASTS, f.getLimit());
	}

	@Test
	public void itemsThatDontTradeOrCostNothingAreLeftOut()
	{
		FakeMarket m = new FakeMarket().add(1, "dead item", 1_000, 0, 100, 0).add(2, "no price", 0, 100, 100, 0);
		m.alch.put(1, 5_000L);
		m.alch.put(2, 5_000L);
		assertTrue(Flips.fromGe(m, Flip.Dest.ALCH, catalog(), Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 100, 8).isEmpty());
	}

	@Test
	public void rankDropsSmallGosAndKeepsTheBest()
	{
		List<FlipRun> runs = List.of(run(1, 5_000, 60), run(2, 20_000, 60), run(3, 100, 60));

		List<FlipRun> ranked = Flips.rank(runs, 500, 10, FlipSort.PROFIT);
		assertEquals(2, ranked.size());
		assertEquals("item 2", ranked.get(0).getName());
		assertEquals(1, Flips.rank(runs, 500, 1, FlipSort.PROFIT).size());
	}

	@Test
	public void shopDealsCanGoToAlchingCappedAtAnHour()
	{
		Shop s = new Shop();
		s.setName("Cheap Shop");
		s.setLocation("");
		s.setSellMultiplier(500);
		Shop.Item i = new Shop.Item();
		i.setId(7);
		i.setS(-1);
		s.setItems(Map.of("cheap ring", i));
		BestBuys.Prices p = new BestBuys.Prices()
		{
			@Override
			public long coffer(int itemId)
			{
				return 0;
			}

			@Override
			public long ge(int itemId)
			{
				return 0;
			}

			@Override
			public long geVolume(int itemId)
			{
				return Long.MAX_VALUE;
			}

			@Override
			public long alch(int itemId)
			{
				return 700;
			}

			@Override
			public long alchCasts()
			{
				return Flips.ALCHS_PER_HOUR;
			}

			@Override
			public int storeValue(int itemId)
			{
				return 1_000;
			}

			@Override
			public boolean stackable(int itemId)
			{
				return true;
			}
		};
		BestBuys.Deal d = BestBuys.deal(BestBuys.Kind.ALCH, s, "cheap ring", i, p, Unlocks.EVERYTHING, ShopPerks.NONE, Long.MAX_VALUE);
		assertEquals(1_200, d.getFlip().getQuantity());
		assertEquals(ShopFlip.Limit.CASTS, d.getFlip().getLimit());
		Flip f = Flips.fromShop(d);
		assertEquals(Flip.Dest.ALCH, f.getDest());
		assertEquals(1_200L * 200, f.getProfit());
		assertNull(f.getSellOffer());
	}

	@Test
	public void allBundledShopsAndManyItemsAreQuick()
	{
		ShopCatalog shops = ShopCatalog.load(new Gson());
		FakeMarket m = new FakeMarket();
		int id = 0;
		for (Shop s : shops.getShops())
		{
			for (String name : s.getItems().keySet())
			{
				// Priced so one in ten sells to a shop for more than the GE charges, as only a few really do
				m.add(id, name, id % 10 == 0 ? 100 : 1_000, 1_000, 5_000, 200 + id % 300);
				m.alch.put(id, (long) (id % 120));
				id++;
			}
		}
		long start = System.nanoTime();
		List<FlipRun> flips = Flips.fromGe(m, Flip.Dest.SHOP, shops, Unlocks.EVERYTHING, ShopPerks.NONE, 10, Long.MAX_VALUE, 1_200, 8);
		long ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("fromGe over " + id + " items: " + ms + " ms, " + flips.size() + " flips");
		assertTrue("took " + ms + " ms", ms < 2_000);
	}

	private static FlipRun run(int id, long profit, double seconds)
	{
		return FlipRun.single(new Flip(Flip.Source.GE, Flip.Dest.ALCH, "item " + id, id, null, null, 1, 0, profit, 0, profit,
			ShopFlip.Limit.VOLUME, Collections.emptyList(), Long.MAX_VALUE, false, 1), seconds);
	}

	/** The first flip of the first go. */
	private static Flip first(List<FlipRun> runs)
	{
		return runs.get(0).getFlips().get(0);
	}
}
