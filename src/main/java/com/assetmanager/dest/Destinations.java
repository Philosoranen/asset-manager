package com.assetmanager.dest;

import com.assetmanager.CofferSuggestionMode;
import com.assetmanager.Suggestions;
import com.assetmanager.coffer.CofferCandidate;
import com.assetmanager.coffer.CofferPlan;
import com.assetmanager.keep.Holdings;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopOffer;
import com.assetmanager.shop.ShopTrip;
import com.assetmanager.value.SaleSuggestion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Sorts Asset Manager's suggestions into destinations: Death's coffer, high alching, the GE and each shop, with
 * every list ordered from most to least valuable.
 */
@Value
public class Destinations
{
	public static final Destinations NONE = new Destinations(Suggestions.NONE, Collections.emptyList());

	/** Which item goes where, for icons. */
	Suggestions suggestions;
	/** All, coffer, alch, GE, then shops (most gained first), then kept. */
	List<Destination> list;

	public Destination get(Destination.Kind kind)
	{
		return list.stream().filter(d -> d.getKind() == kind).findFirst().orElse(null);
	}

	public List<Destination> getShops()
	{
		return list.stream().filter(d -> d.getKind() == Destination.Kind.SHOP).collect(Collectors.toList());
	}

	/**
	 * @param plan  the coffer plan, or null if there is none
	 * @param sales alch, shop and GE suggestions
	 * @param trips       shop trips, by shop name
	 * @param kept        value of each kept item in the bank, by id
	 * @param outsideBank where items are that are only spare outside the bank
	 */
	public static Destinations build(CofferPlan plan, CofferSuggestionMode cofferMode, List<SaleSuggestion> sales,
		Map<String, ShopTrip> trips, Map<Integer, Long> kept, Map<Integer, Holdings.Place> outsideBank)
	{
		return build(plan, cofferMode, sales, trips, kept, outsideBank, GeOrder.VALUE, id -> -1);
	}

	/**
	 * @param geOrder        how to order the GE items
	 * @param tradesPerHour  about how many of an item trade on the GE an hour, or -1 if unknown; for
	 *                       {@link GeOrder#QUICKEST}
	 */
	public static Destinations build(CofferPlan plan, CofferSuggestionMode cofferMode, List<SaleSuggestion> sales,
		Map<String, ShopTrip> trips, Map<Integer, Long> kept, Map<Integer, Holdings.Place> outsideBank,
		GeOrder geOrder, IntToLongFunction tradesPerHour)
	{
		// Coffer: the planned top-up, or everything worth sacrificing
		Map<Integer, long[]> coffer = new LinkedHashMap<>();
		if (plan != null)
		{
			if (cofferMode == CofferSuggestionMode.ALL)
			{
				for (CofferCandidate c : plan.getCandidates())
				{
					coffer.put(c.getValues().getItemId(), new long[]{c.getCofferTotal(), (c.getCofferUnit() - c.getAlternativeUnit()) * c.getQuantity()});
				}
			}
			else
			{
				for (CofferPlan.Step s : plan.getSteps())
				{
					CofferCandidate c = s.getCandidate();
					coffer.put(c.getValues().getItemId(), new long[]{s.getCofferValue(), (c.getCofferUnit() - c.getAlternativeUnit()) * s.getQuantity()});
				}
			}
		}
		List<Integer> cofferIds = sortedIds(coffer, v -> v[0]);

		List<SaleSuggestion> byValue = new ArrayList<>(sales);
		byValue.sort(Comparator.comparingLong(SaleSuggestion::getTotal).reversed());
		Suggestions s = new Suggestions(cofferIds, ids(byValue, SaleSuggestion.Type.ALCH), ids(byValue, SaleSuggestion.Type.SHOP),
			ids(byValue, SaleSuggestion.Type.GE));

		List<Destination> list = new ArrayList<>();
		long cofferValue = coffer.values().stream().mapToLong(v -> v[0]).sum();
		long cofferGain = coffer.values().stream().mapToLong(v -> Math.max(0, v[1])).sum();
		Function<List<Integer>, Map<Holdings.Place, Integer>> elsewhere = ids ->
		{
			Map<Holdings.Place, Integer> counts = new EnumMap<>(Holdings.Place.class);
			ids.forEach(id -> Optional.ofNullable(outsideBank.get(id)).ifPresent(p -> counts.merge(p, 1, Integer::sum)));
			return counts;
		};
		list.add(simple(Destination.Kind.COFFER, "Death's coffer", new ArrayList<>(s.getCoffer()), cofferValue, cofferGain, elsewhere));
		list.add(fromSales(Destination.Kind.ALCH, "High alch", byValue, SaleSuggestion.Type.ALCH, s.getAlch(), elsewhere));
		List<SaleSuggestion> geSales = byValue;
		if (geOrder == GeOrder.QUICKEST)
		{
			geSales = new ArrayList<>(byValue);
			geSales.sort(quickestFirst(tradesPerHour));
		}
		list.add(fromSales(Destination.Kind.GE, "Grand Exchange", geSales, SaleSuggestion.Type.GE, s.getGe(), elsewhere));

		// One destination per shop, or group of shops paying the same, the most gained first
		Map<String, List<SaleSuggestion>> byShop = new LinkedHashMap<>();
		for (SaleSuggestion x : byValue)
		{
			if (x.getType() == SaleSuggestion.Type.SHOP && s.getShop().contains(x.getValues().getItemId()))
			{
				byShop.computeIfAbsent(x.getShopOffer().getShop().getGroup(), k -> new ArrayList<>()).add(x);
			}
		}
		List<Destination> shops = new ArrayList<>();
		for (List<SaleSuggestion> group : byShop.values())
		{
			Shop shop = group.get(0).getShopOffer().getShop();
			List<Integer> shopIds = ids(group, SaleSuggestion.Type.SHOP);
			ShopTrip trip = trips.get(shop.getGroup());
			shops.add(new Destination(Destination.Kind.SHOP, shop.getGroup(), shopIds,
				group.stream().mapToLong(SaleSuggestion::getTotal).sum(),
				trip != null ? trip.getGain() : group.stream().mapToLong(SaleSuggestion::getGain).sum(),
				shop,
				group.stream().mapToInt(x -> x.getShopOffer().getEqualShops()).max().orElse(0),
				trip != null ? trip.getWorlds() : 1,
				group.stream().map(SaleSuggestion::getShopOffer).anyMatch(ShopOffer::isUsesKaramjaGloves),
				group.stream().map(SaleSuggestion::getShopOffer).anyMatch(ShopOffer::isUsesCharos),
				elsewhere.apply(shopIds)));
		}
		shops.sort(Comparator.comparingLong(Destination::getGain).reversed());
		list.addAll(shops);

		// Everything, in the same order as the rows
		Set<Integer> all = new LinkedHashSet<>();
		long allValue = 0;
		long allGain = 0;
		for (Destination d : list)
		{
			all.addAll(d.getItemIds());
			allValue += d.getValue();
			allGain += d.getGain();
		}
		list.add(0, simple(Destination.Kind.ALL, "Everything", new ArrayList<>(all), allValue, allGain, elsewhere));

		list.add(simple(Destination.Kind.KEPT, "Kept", sortedIds(kept, v -> v), kept.values().stream().mapToLong(Long::longValue).sum(), 0, elsewhere));
		return new Destinations(s, Collections.unmodifiableList(list));
	}

	private static Destination simple(Destination.Kind kind, String name, List<Integer> ids, long value, long gain,
		Function<List<Integer>, Map<Holdings.Place, Integer>> elsewhere)
	{
		return new Destination(kind, name, ids, value, gain, null, 0, 1, false, false, elsewhere.apply(ids));
	}

	private static Destination fromSales(Destination.Kind kind, String name, List<SaleSuggestion> byValue, SaleSuggestion.Type type,
		Set<Integer> ids, Function<List<Integer>, Map<Holdings.Place, Integer>> elsewhere)
	{
		List<SaleSuggestion> here = byValue.stream()
			.filter(x -> x.getType() == type && ids.contains(x.getValues().getItemId()))
			.collect(Collectors.toList());
		return simple(kind, name, here.stream().map(x -> x.getValues().getItemId()).collect(Collectors.toList()),
			here.stream().mapToLong(SaleSuggestion::getTotal).sum(),
			kind == Destination.Kind.GE ? 0 : here.stream().mapToLong(SaleSuggestion::getGain).sum(), elsewhere);
	}

	/**
	 * The stacks that sell soonest first: the fewest hours of normal GE trading to sell the whole quantity. Items that
	 * didn't trade in the last day come after them, then items with unknown volumes; each most valuable first. The
	 * sort is stable, so ties keep their value order.
	 */
	static Comparator<SaleSuggestion> quickestFirst(IntToLongFunction tradesPerHour)
	{
		return Comparator.comparingDouble(x ->
		{
			long perHour = tradesPerHour.applyAsLong(x.getValues().getItemId());
			return perHour < 0 ? Double.MAX_VALUE : perHour == 0 ? Double.MAX_VALUE / 2 : (double) x.getQuantity() / perHour;
		});
	}

	private static List<Integer> ids(List<SaleSuggestion> sales, SaleSuggestion.Type type)
	{
		return sales.stream().filter(x -> x.getType() == type).map(x -> x.getValues().getItemId()).collect(Collectors.toList());
	}

	private static <V> List<Integer> sortedIds(Map<Integer, V> values, ToLongFunction<V> value)
	{
		return values.entrySet().stream()
			.sorted(Comparator.comparingLong((Map.Entry<Integer, V> e) -> value.applyAsLong(e.getValue())).reversed())
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}
}
