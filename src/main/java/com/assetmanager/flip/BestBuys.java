package com.assetmanager.flip;

import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.Unlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * The best things to buy from shops anywhere, for Death's coffer, to resell on the GE or to alch, out of the shops the
 * player has the requirements for. Each deal is one visit: buying from one world until the shop runs out, the price rises
 * too far, or the inventory is full. The player can hop worlds to repeat it.
 */
public final class BestBuys
{
	/** Unstackable items bought per visit: a full inventory. */
	static final int INVENTORY_SIZE = 28;
	/** Most bought per visit of a stackable item a shop never runs out of; it has to stop somewhere. */
	static final int MAX_STACK = 10_000;

	private BestBuys()
	{
	}

	public enum Kind
	{
		/** Sacrificed to Death's coffer at 105% of the guide price. */
		COFFER,
		/** Sold on the Grand Exchange, after tax. */
		GE,
		/** Cast High Level Alchemy on, less the nature rune. */
		ALCH
	}

	/** What an item is worth, by id. Gathered on the client thread, so planning can run elsewhere. */
	public interface Prices
	{
		/** Coins added to the coffer per item, or 0 if it isn't accepted. */
		long coffer(int itemId);

		/** Coins from selling one on the GE after tax, or 0 if it can't be. */
		long ge(int itemId);

		/** About how many the GE trades in the time the player will wait, or Long.MAX_VALUE if unknown (live prices off). */
		long geVolume(int itemId);

		/** Coins from alching one, less the nature rune, or 0 if it isn't worth alching. */
		default long alch(int itemId)
		{
			return 0;
		}

		/** How many alchs to plan for. */
		default long alchCasts()
		{
			return Long.MAX_VALUE;
		}

		/** The item's store value, which shop prices are a % of. */
		int storeValue(int itemId);

		boolean stackable(int itemId);
	}

	@Value
	public static class Deal
	{
		Kind kind;
		Shop shop;
		/** The item's name as the shop data has it (lowercase). */
		String item;
		int itemId;
		ShopFlip flip;
		/** Requirements the plugin couldn't check, which the player should make sure of. */
		List<String> unchecked;
		/** For GE deals, about how many trade in the time the player will wait; Long.MAX_VALUE if unknown or not for the GE. */
		long tradesPerHour;

		/** What one visit gains: every item bought, as the price rises. */
		public long getGain()
		{
			return flip.getProfit();
		}

		/** What the first one bought gains: the coffer value or GE sale, less the shop's price at full stock. */
		public long getProfitEach()
		{
			return flip.getGeUnit() - flip.getFirstPrice();
		}

		/** Profit on the first one bought, as a percentage of what it costs. */
		public double getPercent()
		{
			return flip.getFirstPrice() <= 0 ? 0 : getProfitEach() * 100.0 / flip.getFirstPrice();
		}

		/** Whether the shop's price rises with each one bought. */
		public boolean isPriceRises()
		{
			return flip.getLastPrice() > flip.getFirstPrice();
		}
	}

	/**
	 * The best deals of a kind, one per item (from whichever shop gains most per visit, the closest if several do).
	 * Coffer deals are ranked by their percentage return, since filling the coffer is about getting the most out of
	 * the coins spent; GE deals by what a visit gains, since flipping is about money made per trip. Deals the player
	 * can't afford even one of are left out.
	 *
	 * @param coins   coins to spend per visit
	 * @param minGain only deals gaining at least this much per visit
	 * @param limit   at most this many deals
	 */
	public static List<Deal> find(Kind kind, List<Shop> shops, Prices prices, Unlocks unlocks, ShopPerks perks,
		long coins, long minGain, int limit)
	{
		return find(kind, shops, prices, unlocks, perks, coins, minGain, limit, 1);
	}

	/**
	 * As above, hopping up to {@code maxWorlds} worlds (0 for no limit) to buy a few on each while the price is low,
	 * rather than buying deep on one.
	 */
	public static List<Deal> find(Kind kind, List<Shop> shops, Prices prices, Unlocks unlocks, ShopPerks perks,
		long coins, long minGain, int limit, int maxWorlds)
	{
		Map<Integer, Deal> best = new HashMap<>();
		for (Shop shop : shops)
		{
			if (perks.isAvoidWilderness() && shop.isWilderness() || shop.getSellMultiplier() <= 0 && !hasOwnRates(shop))
			{
				continue;
			}
			shop.getItems().forEach((name, item) ->
			{
				Deal deal = deal(kind, shop, name, item, prices, unlocks, perks, coins, maxWorlds);
				if (deal == null || deal.getGain() < Math.max(1, minGain))
				{
					return;
				}
				Deal current = best.get(deal.getItemId());
				if (current == null || better(deal, current, perks))
				{
					best.put(deal.getItemId(), deal);
				}
			});
		}
		List<Deal> out = new ArrayList<>(best.values());
		Comparator<Deal> order = kind == Kind.COFFER
			? Comparator.comparingDouble(Deal::getPercent).reversed().thenComparing(Comparator.comparingLong(Deal::getGain).reversed())
			: Comparator.comparingLong(Deal::getGain).reversed();
		out.sort(order.thenComparing(Deal::getItem));
		return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
	}

	private static boolean hasOwnRates(Shop shop)
	{
		return shop.getItems().values().stream().anyMatch(i -> i.getSm() != null || i.getSp() != null);
	}

	private static boolean better(Deal a, Deal b, ShopPerks perks)
	{
		if (a.getGain() != b.getGain())
		{
			return a.getGain() > b.getGain();
		}
		return a.getShop().distanceTo(perks.getLocation()) < b.getShop().distanceTo(perks.getLocation());
	}

	/** One visit's worth of an item from a shop, or null if it isn't worth buying or the player can't. */
	static Deal deal(Kind kind, Shop shop, String name, Shop.Item item, Prices prices, Unlocks unlocks, ShopPerks perks,
		long coins)
	{
		return deal(kind, shop, name, item, prices, unlocks, perks, coins, 1);
	}

	/** As above, hopping up to {@code maxWorlds} worlds (0 for no limit). */
	static Deal deal(Kind kind, Shop shop, String name, Shop.Item item, Prices prices, Unlocks unlocks, ShopPerks perks,
		long coins, int maxWorlds)
	{
		Integer id = item.getId();
		// Only items the shop keeps in stock can be bought
		if (id == null || item.isNs() || item.getS() == 0)
		{
			return null;
		}
		long unit = kind == Kind.COFFER ? prices.coffer(id) : kind == Kind.ALCH ? prices.alch(id) : prices.ge(id);
		if (unit <= 0)
		{
			return null;
		}
		boolean gloves = shop.getGlovesTier() != null && perks.getGlovesTier() >= shop.getGlovesTier() && item.getGs() != null;
		int rate = gloves ? item.getGs() : item.getSm() != null ? item.getSm() : shop.getSellMultiplier();
		if (rate <= 0)
		{
			return null;
		}
		int delta = item.getD() != null ? item.getD() : shop.getDelta();
		// A set price is turned into the value that gives it at the shop's rate, so it rises with stock like any other
		int value = item.getSp() != null && !gloves ? (int) ((item.getSp() * 1000L + rate - 1) / rate) : prices.storeValue(id);
		if (value <= 0)
		{
			return null;
		}
		Unlocks.Missing missing = unlocks.missing(shop, item);
		if (!missing.isMet())
		{
			return null;
		}
		int space = prices.stackable(id) ? MAX_STACK : INVENTORY_SIZE;
		long volume = kind == Kind.GE ? prices.geVolume(id) : Long.MAX_VALUE;
		long cap;
		ShopFlip.Limit capLimit;
		switch (kind)
		{
			case ALCH:
				cap = prices.alchCasts();
				capLimit = ShopFlip.Limit.CASTS;
				break;
			case GE:
				cap = volume;
				capLimit = ShopFlip.Limit.VOLUME;
				break;
			default:
				cap = Long.MAX_VALUE;
				capLimit = ShopFlip.Limit.VOLUME;
		}
		// Bought by talking, sometimes one per conversation: hopping isn't suggested there
		int worlds = shop.getTalk() != null ? 1 : maxWorlds;
		ShopFlip flip = ShopFlipAdvisor.planHopping(value, item.getS(), rate, delta, unit, 1, space, coins, cap, capLimit, worlds);
		if (flip == null)
		{
			return null;
		}
		return new Deal(kind, shop, name, id, flip,
			missing.getUnchecked().isEmpty() ? Collections.emptyList() : new ArrayList<>(missing.getUnchecked()), volume);
	}
}
