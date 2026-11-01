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
 * GE and alching, then hops to a fresh world; hopping is quick, so a trip hops as many worlds as its stacks need. A
 * shop is only suggested if the trip gains enough to be worth it; otherwise its stacks go another way (another shop,
 * or alching or the GE).
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
		// Each stack goes where it gains most; stacks on trips not worth making then move elsewhere, which can make
		// another trip worth it
		for (int round = 0; ; round++)
		{
			List<SaleSuggestion> out = evaluate(values, spare, canUseGe, natureRuneCost, minAlchGain, minGeSale, shops, perks, rules,
				skip);
			Map<String, List<SaleSuggestion>> byShop = new LinkedHashMap<>();
			for (SaleSuggestion x : out)
			{
				if (x.getType() == SaleSuggestion.Type.SHOP)
				{
					// A group of shops paying the same is one trip: any of them will do
					byShop.computeIfAbsent(x.getShopOffer().getShop().getGroup(), k -> new ArrayList<>()).add(x);
				}
			}

			boolean changed = false;
			Map<String, ShopTrip> trips = new HashMap<>();
			for (Map.Entry<String, List<SaleSuggestion>> e : byShop.entrySet())
			{
				ShopTrip trip = plan(e.getKey(), e.getValue());
				if (trip.getGain() < rules.getMinGainPerTrip())
				{
					for (SaleSuggestion x : e.getValue())
					{
						skip.computeIfAbsent(x.getValues().getItemId(), k -> new HashSet<>()).add(e.getKey());
					}
					changed = true;
				}
				trips.put(e.getKey(), trip);
			}
			if (!changed || round >= MAX_ROUNDS)
			{
				out.sort(Comparator.comparingLong(SaleSuggestion::getTotal).reversed());
				return new Result(out, trips, skip);
			}
		}
	}

	/** A trip selling every stack: as many worlds as the stack needing the most, gaining what they all gain together. */
	static ShopTrip plan(String shop, List<SaleSuggestion> stacks)
	{
		int worlds = 1;
		long gain = 0;
		for (SaleSuggestion x : stacks)
		{
			worlds = Math.max(worlds, x.getShopOffer().getWorlds());
			gain += x.getGain();
		}
		return new ShopTrip(shop, worlds, gain);
	}

	private static List<SaleSuggestion> evaluate(Map<Integer, ItemValues> values, Map<Integer, Integer> spare, boolean canUseGe,
		int natureRuneCost, int minAlchGain, long minGeSale, ShopCatalog shops, ShopPerks perks, SellRules rules,
		Map<Integer, Set<String>> skip)
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
				skip.getOrDefault(e.getKey(), Collections.emptySet()));

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
