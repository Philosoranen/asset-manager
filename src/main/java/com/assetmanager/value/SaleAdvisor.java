package com.assetmanager.value;

import com.assetmanager.shop.SellRules;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.shop.ShopOffer;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.ShopTrip;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Works out the best way to cash out each spare stack: high alching, the GE, or a shop. Each stack goes one way.
 * <p>
 * Shops are planned as trips. On each world the player sells every stack for that shop while each sale beats the
 * GE and alching, then hops to a fresh world, and each hop costs what the player says a hop is worth. A trip hops
 * as many worlds as gains the most: a stack that needs more worlds than that goes another way (another shop, or
 * alching or the GE). A shop is only suggested if the trip gains enough to be worth it.
 * <p>
 * Otherwise alching is suggested when it beats the GE by the minimum gain, and for mains the GE for stacks worth at
 * least the minimum sale. Ironmen alch by default, so for them only shops that beat alching are suggested.
 */
public final class SaleAdvisor
{
	/** Rounds of moving stacks off trips that aren't worth it; each round can shift them to other shops. */
	private static final int MAX_ROUNDS = 50;

	@Value
	public static class Result
	{
		public static final Result NONE = new Result(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());

		/** Most valuable first. */
		List<SaleSuggestion> sales;
		/** Planned shop trips, by shop name. */
		Map<String, ShopTrip> trips;
		/** Shops each item isn't going to, because the trip wasn't worth it for that stack. */
		Map<Integer, Set<String>> skippedShops;
	}

	private SaleAdvisor()
	{
	}

	/**
	 * @param values         item values by canonical id
	 * @param spare          spare quantity per item id, after keep rules
	 * @param canUseGe       false for ironmen
	 * @param natureRuneCost cost of a nature rune, deducted from alch value
	 * @param minAlchGain    only suggest alching when it beats the GE by at least this much per item
	 * @param minGeSale      only suggest the GE for stacks worth at least this much there, after tax
	 * @param shops          shop prices, or null to ignore shops
	 * @param perks          what the player has that affects shop prices
	 * @param rules          when selling to a shop is worth it
	 */
	public static Result suggest(Map<Integer, ItemValues> values, Map<Integer, Integer> spare, boolean canUseGe,
		int natureRuneCost, int minAlchGain, long minGeSale, ShopCatalog shops, ShopPerks perks, SellRules rules)
	{
		return suggest(values, spare, canUseGe, natureRuneCost, minAlchGain, minGeSale, shops, perks, rules, Collections.emptyMap());
	}

	/**
	 * As above.
	 *
	 * @param avoid shops not to send each item to, such as those others have overstocked it at on every world
	 */
	public static Result suggest(Map<Integer, ItemValues> values, Map<Integer, Integer> spare, boolean canUseGe,
		int natureRuneCost, int minAlchGain, long minGeSale, ShopCatalog shops, ShopPerks perks, SellRules rules,
		Map<Integer, Set<String>> avoid)
	{
		Map<Integer, Set<String>> skip = new HashMap<>();
		avoid.forEach((id, names) -> skip.put(id, new HashSet<>(names)));
		// Every stack on a trip shares its hops, so at first each stack goes where it gains most as if hops were free;
		// then trips are planned, and stacks that don't fit them move, paying only for the hops they'd add to a trip
		Map<String, Integer> planned = null;
		for (int round = 0; ; round++)
		{
			List<SaleSuggestion> out = evaluate(values, spare, canUseGe, natureRuneCost, minAlchGain, minGeSale, shops, perks, rules,
				skip, planned);
			Map<String, List<SaleSuggestion>> byShop = new LinkedHashMap<>();
			for (SaleSuggestion x : out)
			{
				if (x.getType() == SaleSuggestion.Type.SHOP)
				{
					byShop.computeIfAbsent(x.getShopOffer().getShop().getName(), k -> new ArrayList<>()).add(x);
				}
			}

			boolean changed = false;
			Map<String, ShopTrip> trips = new HashMap<>();
			for (Map.Entry<String, List<SaleSuggestion>> e : byShop.entrySet())
			{
				ShopTrip trip = plan(e.getKey(), e.getValue(), rules.getHopCost());
				// Stacks needing more worlds than the trip goes somewhere else; so does everything on a trip not worth it
				for (SaleSuggestion x : e.getValue())
				{
					if (x.getShopOffer().getWorlds() > trip.getWorlds() || trip.getGain() < rules.getMinGainPerTrip())
					{
						skip.computeIfAbsent(x.getValues().getItemId(), k -> new HashSet<>()).add(e.getKey());
						changed = true;
					}
				}
				trips.put(e.getKey(), trip);
			}
			planned = new HashMap<>();
			for (ShopTrip trip : trips.values())
			{
				if (trip.getGain() >= rules.getMinGainPerTrip())
				{
					planned.put(trip.getShop(), trip.getWorlds());
				}
			}
			if (!changed || round >= MAX_ROUNDS)
			{
				out.sort(Comparator.comparingLong(SaleSuggestion::getTotal).reversed());
				return new Result(out, trips, skip);
			}
		}
	}

	/**
	 * The number of worlds for a trip that gains the most: each stack needing no more than that many worlds sells
	 * there, and each world after the first costs {@code hopCost}.
	 */
	static ShopTrip plan(String shop, List<SaleSuggestion> stacks, long hopCost)
	{
		int bestWorlds = 1;
		long bestGain = Long.MIN_VALUE;
		Set<Integer> options = new HashSet<>();
		for (SaleSuggestion x : stacks)
		{
			options.add(x.getShopOffer().getWorlds());
		}
		for (int worlds : options)
		{
			long gain = -(worlds - 1) * hopCost;
			for (SaleSuggestion x : stacks)
			{
				if (x.getShopOffer().getWorlds() <= worlds)
				{
					gain += x.getGain();
				}
			}
			if (gain > bestGain || gain == bestGain && worlds < bestWorlds)
			{
				bestGain = gain;
				bestWorlds = worlds;
			}
		}
		return new ShopTrip(shop, bestWorlds, (bestWorlds - 1) * hopCost, bestGain);
	}

	private static List<SaleSuggestion> evaluate(Map<Integer, ItemValues> values, Map<Integer, Integer> spare, boolean canUseGe,
		int natureRuneCost, int minAlchGain, long minGeSale, ShopCatalog shops, ShopPerks perks, SellRules rules,
		Map<Integer, Set<String>> skip, Map<String, Integer> planned)
	{
		List<SaleSuggestion> out = new ArrayList<>();
		for (Map.Entry<Integer, ItemValues> e : values.entrySet())
		{
			ItemValues v = e.getValue();
			int qty = spare.getOrDefault(e.getKey(), 0);
			// Untradeables have no GE price to compare against, and are often needed (quest items, etc.).
			if (qty <= 0 || !v.isTradeable())
			{
				continue;
			}

			long geUnit = canUseGe ? v.getGeNetSell() : 0;
			long alchUnit = v.getHighAlchNet(natureRuneCost);
			long altUnit = Math.max(geUnit, alchUnit);
			long ge = geUnit * qty;
			long alch = alchUnit * qty;
			ShopOffer offer = shops == null ? null : shops.bestOffer(v.getName(), v.getStorePrice(), qty, true, perks, altUnit, rules,
				skip.getOrDefault(e.getKey(), Collections.emptySet()), planned);

			// Often a shop only wins by the cost of a nature rune per item, which is why it's worth it
			if (offer != null && offer.getQuantity() > 0)
			{
				out.add(new SaleSuggestion(v, qty, SaleSuggestion.Type.SHOP, offer.getTotal(), altUnit * qty,
					geUnit >= alchUnit ? "GE sell" : "High alch", offer));
			}
			// Otherwise, for mains, alching when it beats the GE, or the GE for a stack worth selling
			else if (canUseGe && alch - ge >= (long) Math.max(1, minAlchGain) * qty)
			{
				out.add(new SaleSuggestion(v, qty, SaleSuggestion.Type.ALCH, alch, ge, "GE sell", null));
			}
			else if (canUseGe && ge > 0 && ge >= alch && ge >= minGeSale)
			{
				out.add(new SaleSuggestion(v, qty, SaleSuggestion.Type.GE, ge, alch, "High alch", null));
			}
		}
		return out;
	}
}
