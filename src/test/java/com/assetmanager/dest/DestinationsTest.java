package com.assetmanager.dest;

import static org.junit.Assert.assertEquals;
import com.assetmanager.CofferSuggestionMode;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopOffer;
import com.assetmanager.value.ItemValues;
import com.assetmanager.value.SaleSuggestion;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DestinationsTest
{
	private static SaleSuggestion sale(int id, SaleSuggestion.Type type, long total, long alternative, Shop shop)
	{
		ItemValues v = new ItemValues(id, "Item " + id, 1, 1, 1, 1, true);
		ShopOffer offer = shop == null ? null : new ShopOffer(shop, 1, total, total, 600, false, 1, 1, false, false, 0);
		return new SaleSuggestion(v, 1, type, total, alternative, "GE sell", offer);
	}

	private static Shop shop(String name)
	{
		Shop s = new Shop();
		s.setName(name);
		s.setLocation("");
		return s;
	}

	@Test
	public void mostValuableFirstAndOneRowPerShop()
	{
		Shop varrock = shop("Varrock General Store");
		Shop ali = shop("Ali's Discount Wares");
		List<SaleSuggestion> sales = Arrays.asList(
			sale(1, SaleSuggestion.Type.ALCH, 1_000, 900, null),
			sale(2, SaleSuggestion.Type.ALCH, 5_000, 4_000, null),
			sale(3, SaleSuggestion.Type.GE, 20_000, 0, null),
			sale(4, SaleSuggestion.Type.SHOP, 300, 100, varrock),
			sale(5, SaleSuggestion.Type.SHOP, 900, 850, ali),
			sale(6, SaleSuggestion.Type.SHOP, 700, 600, varrock));
		Map<Integer, Long> kept = new HashMap<>();
		kept.put(7, 10L);
		kept.put(8, 50L);
		Destinations d = Destinations.build(null, CofferSuggestionMode.PLAN, sales, Collections.emptyMap(), kept, Collections.emptyMap());

		assertEquals(Arrays.asList(2, 1), d.get(Destination.Kind.ALCH).getItemIds());
		assertEquals(6_000, d.get(Destination.Kind.ALCH).getValue());
		assertEquals(1_100, d.get(Destination.Kind.ALCH).getGain());
		assertEquals(Collections.singletonList(3), d.get(Destination.Kind.GE).getItemIds());

		// Varrock gains 300 (200 + 100), Ali's 50, so Varrock comes first; its items most valuable first
		List<Destination> shops = d.getShops();
		assertEquals("Varrock General Store", shops.get(0).getName());
		assertEquals(Arrays.asList(6, 4), shops.get(0).getItemIds());
		assertEquals(300, shops.get(0).getGain());
		assertEquals("Ali's Discount Wares", shops.get(1).getName());

		// Everything, in row order; kept items aren't in it
		assertEquals(Arrays.asList(2, 1, 3, 6, 4, 5), d.get(Destination.Kind.ALL).getItemIds());
		assertEquals(Arrays.asList(8, 7), d.get(Destination.Kind.KEPT).getItemIds());
		assertEquals(0, d.get(Destination.Kind.COFFER).getCount());
	}
}
