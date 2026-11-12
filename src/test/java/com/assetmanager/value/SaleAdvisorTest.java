package com.assetmanager.value;

import static org.junit.Assert.assertEquals;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.SellRules;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.shop.ShopPerks;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SaleAdvisorTest
{
	private final Map<Integer, ItemValues> bank = new HashMap<>();
	private final Map<Integer, Integer> spare = new HashMap<>();

	private void add(int id, String name, int wiki, int ha, int storeValue, int qty)
	{
		bank.put(id, new ItemValues(id, name, wiki, wiki, ha, storeValue, true));
		spare.put(id, qty);
	}

	private static ShopCatalog generalStore(int rate)
	{
		Shop s = new Shop();
		s.setName("Bandit Duty Free");
		s.setLocation("Wilderness");
		s.setGeneral(true);
		s.setBuyMultiplier(rate);
		s.setDelta(0);
		s.setItems(Collections.emptyMap());
		return new ShopCatalog(Collections.singletonList(s), "test");
	}

	private static ShopCatalog droppingStore(int rate, int delta)
	{
		Shop s = new Shop();
		s.setName("Karamja General Store");
		s.setLocation("Karamja");
		s.setGeneral(true);
		s.setBuyMultiplier(rate);
		s.setDelta(delta);
		s.setItems(Collections.emptyMap());
		return new ShopCatalog(Collections.singletonList(s), "test");
	}

	@Test
	public void shopsNotWorthATripAreSkipped()
	{
		add(1, "Rune platebody", 30_000, 38_400, 64_000, 1);
		// Gains 200 over alching on one world, less than the 2,000 a world needs
		SaleAdvisor.Result r = SaleAdvisor.suggest(bank, spare, true, 200, 100, 10_000, droppingStore(600, 30),
			ShopPerks.NONE, new SellRules(1, 10, 2_000));
		assertEquals(SaleSuggestion.Type.ALCH, r.getSales().get(0).getType());

		// With 20 different items it's worth it
		for (int id = 2; id <= 20; id++)
		{
			add(id, "Rune thing " + id, 30_000, 38_400, 64_000, 1);
		}
		r = SaleAdvisor.suggest(bank, spare, true, 200, 100, 10_000, droppingStore(600, 30),
			ShopPerks.NONE, new SellRules(1, 10, 2_000));
		assertEquals(20, r.getSales().stream().filter(x -> x.getType() == SaleSuggestion.Type.SHOP).count());
	}

	@Test
	public void mainsGetAlchWhenItBeatsTheGe()
	{
		// Alch 38,400 - 200 = 38,200 vs GE 37,000 - 740 tax = 36,260
		add(1, "Rune platebody", 37_000, 38_400, 64_000, 3);
		List<SaleSuggestion> s = SaleAdvisor.suggest(bank, spare, true, 200, 100, Long.MAX_VALUE, null, ShopPerks.NONE, new SellRules(1, 10, 0)).getSales();
		assertEquals(1, s.size());
		assertEquals(SaleSuggestion.Type.ALCH, s.get(0).getType());
		assertEquals(1_940 * 3, s.get(0).getGain());
	}

	@Test
	public void shopWinsWhenItPaysMore()
	{
		// Shop at 65% of 64,000 = 41,600 beats alch 38,200 and GE 36,260
		add(1, "Rune platebody", 37_000, 38_400, 64_000, 2);
		List<SaleSuggestion> s = SaleAdvisor.suggest(bank, spare, true, 200, 100, Long.MAX_VALUE, generalStore(650), ShopPerks.NONE, new SellRules(1, 10, 0)).getSales();
		assertEquals(SaleSuggestion.Type.SHOP, s.get(0).getType());
		assertEquals((41_600 - 38_200) * 2, s.get(0).getGain());
		assertEquals("High alch", s.get(0).getAlternativeLabel());
	}

	@Test
	public void ironmenOnlyGetShopsThatBeatAlching()
	{
		add(1, "Rune platebody", 37_000, 38_400, 64_000, 1);
		// Alching is an ironman's default, so no alch suggestion
		assertEquals(0, SaleAdvisor.suggest(bank, spare, false, 200, 100, Long.MAX_VALUE, null, ShopPerks.NONE, new SellRules(1, 10, 0)).getSales().size());
		// ...but a shop paying more is suggested
		assertEquals(SaleSuggestion.Type.SHOP, SaleAdvisor.suggest(bank, spare, false, 200, 100, Long.MAX_VALUE, generalStore(650), ShopPerks.NONE, new SellRules(1, 10, 0)).getSales().get(0).getType());
	}

	@Test
	public void shopWinningOnlyByTheNatureRuneIsStillSuggested()
	{
		// A 60% shop pays exactly the alch value, so it beats alching by the nature rune (200 here)
		add(1, "Rune platebody", 30_000, 38_400, 64_000, 1);
		SaleSuggestion s = SaleAdvisor.suggest(bank, spare, true, 200, 100, Long.MAX_VALUE, generalStore(600), ShopPerks.NONE, new SellRules(1, 10, 0)).getSales().get(0);
		assertEquals(SaleSuggestion.Type.SHOP, s.getType());
		assertEquals(200, s.getGain());
		// ...unless the player asks for a bigger gain per item, then alching it is
		assertEquals(SaleSuggestion.Type.ALCH, SaleAdvisor.suggest(bank, spare, true, 200, 100, Long.MAX_VALUE, generalStore(600), ShopPerks.NONE, new SellRules(500, 10, 0)).getSales().get(0).getType());
	}

	@Test
	public void keptItemsAreSkipped()
	{
		add(1, "Rune platebody", 37_000, 38_400, 64_000, 0);
		assertEquals(0, SaleAdvisor.suggest(bank, spare, true, 200, 100, Long.MAX_VALUE, null, ShopPerks.NONE, new SellRules(1, 10, 0)).getSales().size());
	}

	@Test
	public void spareStacksWorthSellingGoToTheGe()
	{
		// Dragon bones: GE 2,500 - 50 tax beats alching (0 after the rune) by far
		add(1, "Dragon bones", 2_500, 0, 1, 100);
		// A cheap stack under the minimum sale
		add(2, "Bronze arrow", 5, 1, 1, 50);
		// Worth more alched, so it's an alch suggestion instead
		add(3, "Rune platebody", 37_000, 38_400, 64_000, 1);
		List<SaleSuggestion> s = SaleAdvisor.suggest(bank, spare, true, 200, 100, 10_000, null, ShopPerks.NONE, new SellRules(1, 10, 0)).getSales();
		assertEquals(2, s.size());
		SaleSuggestion bones = s.stream().filter(x -> x.getType() == SaleSuggestion.Type.GE).findFirst().get();
		assertEquals("Dragon bones", bones.getValues().getName());
		assertEquals(2_450 * 100, bones.getTotal());
		// Ironmen can't use the GE
		assertEquals(0, SaleAdvisor.suggest(bank, spare, false, 200, 100, 10_000, null, ShopPerks.NONE, new SellRules(1, 10, 0)).getSales().size());
	}

	@Test
	public void geTaxSkipsCheapItems()
	{
		assertEquals(49, new ItemValues(1, "Cheap", 49, 49, 0, 1, true).getGeNetSell());
		assertEquals(49, new ItemValues(1, "Taxed", 50, 50, 0, 1, true).getGeNetSell());
		assertEquals(98, new ItemValues(1, "Taxed", 100, 100, 0, 1, true).getGeNetSell());
	}

	@Test
	public void gePriceModes()
	{
		// high = what buyers paid, low = what sellers accepted
		assertEquals(1_100, GePrice.MARKET.pick(1_100, 1_000));
		assertEquals(1_050, GePrice.MIDPOINT.pick(1_100, 1_000));
		assertEquals(1_000, GePrice.INSTANT.pick(1_100, 1_000));
		// Only one side traded recently
		assertEquals(1_000, GePrice.MARKET.pick(0, 1_000));
		assertEquals(1_100, GePrice.INSTANT.pick(1_100, 0));
	}

	@Test
	public void oneOddBuyDoesntSetThePrice()
	{
		// Plant cure: someone paid 4,500 once, but sellers take about 100
		assertEquals(100, GePrice.MARKET.pick(4_500, 100));
		assertEquals(100, GePrice.MIDPOINT.pick(4_500, 100));
		// A normal spread is fine
		assertEquals(190, GePrice.MARKET.pick(190, 100));
	}

	@Test
	public void hopsAreSharedByEveryStackOnTheTrip()
	{
		// 27 different items, 10 of each: one of each sells per world, so it's 10 worlds, not 27 or 270
		SellRules rules = new SellRules(1, ShopCatalog.NO_WORLD_LIMIT, 1);
		for (int id = 1; id <= 27; id++)
		{
			add(id, "Item " + id, 30_000, 38_400, 64_000, 10);
		}
		SaleAdvisor.Result r = SaleAdvisor.suggest(bank, spare, true, 200, 100, 10_000, droppingStore(600, 30), ShopPerks.NONE, rules);
		assertEquals(27, r.getSales().stream().filter(x -> x.getType() == SaleSuggestion.Type.SHOP).count());
		assertEquals(10, r.getTrips().get("Karamja General Store").getWorlds());
		// 270 sales each beating alching by 200
		assertEquals(270 * 200, r.getTrips().get("Karamja General Store").getGain());
	}

	@Test
	public void bigStacksNeedingManyHopsForLittleAreAlched()
	{
		// 60% store dropping 3% each: only the first sale on each world beats alching (by the 200 nature rune), so 20
		// platebodies take 20 worlds for 200 each
		add(1, "Rune platebody", 30_000, 38_400, 64_000, 20);
		SaleAdvisor.Result r = SaleAdvisor.suggest(bank, spare, true, 200, 25, 10_000, droppingStore(600, 30),
			ShopPerks.NONE, new SellRules(1, 50, 1_000));
		assertEquals(SaleSuggestion.Type.ALCH, r.getSales().get(0).getType());
		// ...unless the player is happy hopping for 200 a world
		r = SaleAdvisor.suggest(bank, spare, true, 200, 25, 10_000, droppingStore(600, 30),
			ShopPerks.NONE, new SellRules(1, 50, 200));
		assertEquals(SaleSuggestion.Type.SHOP, r.getSales().get(0).getType());
	}

	@Test
	public void aStackWorthHoppingForStaysWorthItAsItSells()
	{
		// 65% store dropping 3%: 2 per world beat alching, gaining 3,400 + 1,480 a world
		for (int left = 20; left >= 2; left -= 2)
		{
			bank.clear();
			spare.clear();
			add(1, "Rune platebody", 30_000, 38_400, 64_000, left);
			SaleAdvisor.Result r = SaleAdvisor.suggest(bank, spare, true, 200, 25, 10_000, droppingStore(650, 30),
				ShopPerks.NONE, new SellRules(1, 50, 2_000));
			assertEquals("with " + left + " left", SaleSuggestion.Type.SHOP, r.getSales().get(0).getType());
		}
	}

	@Test
	public void theStackNeedingTheMostWorldsIsDroppedFirst()
	{
		// One stack gains a lot on one world; the other needs 20 worlds for 200 each. Together they average under
		// 1,000 a world, but the first is worth a trip on its own.
		add(1, "Rune platebody", 30_000, 38_400, 64_000, 20);
		add(2, "Rune chainbody", 30_000, 30_000, 64_000, 1);
		SaleAdvisor.Result r = SaleAdvisor.suggest(bank, spare, true, 200, 25, 10_000, droppingStore(600, 30),
			ShopPerks.NONE, new SellRules(1, 50, 1_000));
		assertEquals(SaleSuggestion.Type.ALCH, type(r, 1));
		assertEquals(SaleSuggestion.Type.SHOP, type(r, 2));
		assertEquals(1, r.getTrips().get("Karamja General Store").getWorlds());
	}

	@Test
	public void itemsAlchingForALittleMoreStillGetASuggestion()
	{
		// Alch 10,000 - 200 = 9,800 vs GE 9,900 - 198 = 9,702: alching wins by 98, under the 100 minimum
		add(1, "Big stack", 9_900, 10_000, 1, 100);
		add(2, "Small stack", 9_900, 10_000, 1, 1);
		SaleAdvisor.Result r = SaleAdvisor.suggest(bank, spare, true, 200, 100, 50_000, null, ShopPerks.NONE, new SellRules(1, 10, 0));
		// Worth selling on the GE in bulk rather than casting 100 alchs for 98 each
		assertEquals(SaleSuggestion.Type.GE, type(r, 1));
		assertEquals(0, r.getSales().stream().filter(x -> x.getValues().getItemId() == 1).findFirst().get().getGain());
		// Too small for the GE, but it alchs for more, so alch it
		assertEquals(SaleSuggestion.Type.ALCH, type(r, 2));
	}

	private static SaleSuggestion.Type type(SaleAdvisor.Result r, int id)
	{
		return r.getSales().stream().filter(x -> x.getValues().getItemId() == id).findFirst().get().getType();
	}
}
