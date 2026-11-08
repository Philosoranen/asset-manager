package com.assetmanager.flip;

import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.Unlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import lombok.Value;

/**
 * The best shops to buy from, for Death's coffer, to resell on the GE or to alch, out of the shops the player has the
 * requirements for. Each go is one visit: buying everything worth it, a few on each world while the price is low,
 * until the shop runs out, the price rises too far, or the inventory or coins run out.
 */
public final class BestBuys
{
	/** Unstackable items bought per visit: a full inventory. */
	static final int INVENTORY_SIZE = 28;
	/** Most bought per visit of a stackable item a shop never runs out of; it has to stop somewhere. */
	static final int MAX_STACK = 10_000;
	/** Most worlds tried when looking for the quickest go; past this, hopping takes longer than it's worth. */
	private static final long MAX_FAST_WORLDS = 1024;

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

		/** Hours the player will wait for GE offers to fill. */
		default double geHours()
		{
			return 1;
		}
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
	 * A go at each shop worth visiting: everything worth buying there, bought together so every hop and trip serves
	 * them all. Each shop's go shares the coins, the inventory and (for alching) an hour of casts between its items.
	 * Shops where the go would be just the same are merged into the closest. Gos the player can't afford even one item
	 * of are left out.
	 *
	 * @param coins     coins to spend per go
	 * @param maxWorlds the most worlds to hop, or ShopCatalog.NO_WORLD_LIMIT
	 * @param fastest   hop only as many worlds as makes the most an hour, rather than as many as makes the most
	 * @param maxItems  the most different items to sell on the GE in one go (it has 8 offer slots)
	 */
	public static List<FlipRun> runs(Kind kind, List<Shop> shops, Prices prices, Unlocks unlocks, ShopPerks perks,
		long coins, int maxWorlds, boolean fastest, int maxItems)
	{
		List<FlipRun> out = new ArrayList<>();
		for (Shop shop : shops)
		{
			if (perks.isAvoidWilderness() && shop.isWilderness() || shop.getSellMultiplier() <= 0 && !hasOwnRates(shop))
			{
				continue;
			}
			List<Leg> legs = new ArrayList<>();
			shop.getItems().forEach((name, item) ->
			{
				Leg leg = leg(kind, shop, name, item, prices, unlocks, perks);
				if (leg != null && leg.getUnit() - leg.price(0) >= 1 && leg.price(0) <= coins)
				{
					legs.add(leg);
				}
			});
			if (!legs.isEmpty())
			{
				FlipRun run = bestRun(kind, shop, legs, prices, coins, maxWorlds, fastest, kind == Kind.GE ? maxItems : Integer.MAX_VALUE);
				if (run != null)
				{
					out.add(run);
				}
			}
		}
		return Flips.mergeEqual(out, perks.getLocation());
	}

	private static boolean hasOwnRates(Shop shop)
	{
		return shop.getItems().values().stream().anyMatch(i -> i.getSm() != null || i.getSp() != null);
	}

	/** The go at a shop: on as many worlds as allowed, or, if {@code fastest}, as many as make the most an hour. */
	static FlipRun bestRun(Kind kind, Shop shop, List<Leg> legs, Prices prices, long coins, int maxWorlds, boolean fastest,
		int maxItems)
	{
		// Buying by talking is sometimes one per conversation, so hopping isn't suggested; a shop that never runs out
		// has nothing to hop for
		boolean hops = shop.getTalk() == null && legs.stream().anyMatch(l -> l.getNormalStock() >= 0);
		long allowed = !hops ? 1 : maxWorlds <= 0 ? Long.MAX_VALUE : maxWorlds;
		if (!fastest || allowed == 1)
		{
			return plan(kind, shop, legs, prices, coins, allowed, maxItems);
		}
		FlipRun best = null;
		long bestWorlds = 1;
		long top = Math.min(allowed, MAX_FAST_WORLDS);
		for (long w = 1; ; w = Math.min(top, w < 4 ? w + 1 : w * 3 / 2))
		{
			FlipRun run = plan(kind, shop, legs, prices, coins, w, maxItems);
			if (run != null && (best == null || run.getGpPerHour() > best.getGpPerHour()))
			{
				best = run;
				bestWorlds = w;
			}
			if (w == top)
			{
				break;
			}
		}
		if (best == null || bestWorlds >= allowed)
		{
			return best;
		}
		// Stopped short of the player's limit because more hopping would make less an hour
		List<Flip> flips = new ArrayList<>();
		for (Flip f : best.getFlips())
		{
			flips.add(f.getLimit() == ShopFlip.Limit.WORLDS || f.getLimit() == ShopFlip.Limit.STOCK && bestWorlds > 1
				? f.withLimit(ShopFlip.Limit.QUICKER) : f);
		}
		return best.withFlips(flips);
	}

	/** One item's progress while planning a go. */
	private static class Buying
	{
		final Leg leg;
		long bought;
		long cost;
		long first;
		long last;
		long nextPrice;
		ShopFlip.Limit limit;

		Buying(Leg leg)
		{
			this.leg = leg;
		}

		long nextProfit()
		{
			return leg.getUnit() - nextPrice;
		}

		/** Works out the next one's price; false, with the reason, if no more can be bought. */
		boolean advance(long worlds)
		{
			long depth = bought / worlds;
			if (leg.getNormalStock() >= 0 && depth >= leg.getNormalStock())
			{
				// Every world's stock is bought: more worlds would mean more
				limit = worlds == 1 || worlds == Long.MAX_VALUE ? ShopFlip.Limit.STOCK : ShopFlip.Limit.WORLDS;
				return false;
			}
			if (leg.isStackable() && bought >= MAX_STACK)
			{
				limit = ShopFlip.Limit.SPACE;
				return false;
			}
			if (bought >= leg.getCap())
			{
				limit = leg.getCapLimit();
				return false;
			}
			nextPrice = leg.price(depth);
			return true;
		}
	}

	/**
	 * Buying at a shop over {@code worlds} worlds (Long.MAX_VALUE for as many as it takes), a few of each item on each
	 * world, round the worlds in turn. Each step buys whichever item's next one makes the most, until none makes
	 * anything or the coins, inventory or alchs run out, buying no more than {@code maxItems} different items. Null if
	 * nothing's bought.
	 */
	static FlipRun plan(Kind kind, Shop shop, List<Leg> legs, Prices prices, long coins, long worlds, int maxItems)
	{
		PriorityQueue<Buying> next = new PriorityQueue<>(Comparator.comparingLong(Buying::nextProfit).reversed()
			.thenComparing(b -> b.leg.getName()));
		List<Buying> all = new ArrayList<>();
		for (Leg leg : legs)
		{
			Buying b = new Buying(leg);
			all.add(b);
			if (b.advance(worlds))
			{
				next.add(b);
			}
		}
		int slots = INVENTORY_SIZE;
		long left = coins;
		long casts = kind == Kind.ALCH ? prices.alchCasts() : Long.MAX_VALUE;
		long total = 0;
		int items = 0;
		ShopFlip.Limit stopped = ShopFlip.Limit.PRICE;
		while (!next.isEmpty())
		{
			if (total >= casts)
			{
				stopped = ShopFlip.Limit.CASTS;
				break;
			}
			Buying b = next.peek();
			if (b.nextProfit() < 1)
			{
				break;
			}
			next.poll();
			long price = b.nextPrice;
			boolean needsSlot = !b.leg.isStackable() || b.bought == 0;
			if (needsSlot && slots <= 0)
			{
				b.limit = ShopFlip.Limit.SPACE;
				continue;
			}
			if (price > left)
			{
				b.limit = ShopFlip.Limit.COINS;
				continue;
			}
			if (b.bought == 0 && items >= maxItems)
			{
				b.limit = ShopFlip.Limit.GE_SLOTS;
				continue;
			}
			if (b.bought == 0)
			{
				items++;
			}
			if (needsSlot)
			{
				slots--;
			}
			left -= price;
			b.cost += price;
			if (b.bought == 0)
			{
				b.first = price;
			}
			b.last = Math.max(b.last, price);
			b.bought++;
			total++;
			if (b.advance(worlds))
			{
				next.add(b);
			}
		}
		for (Buying b : next)
		{
			b.limit = stopped;
		}

		List<Flip> flips = new ArrayList<>();
		int used = 1;
		long talks = 0;
		long clicks = 0;
		for (Buying b : all)
		{
			if (b.bought == 0)
			{
				continue;
			}
			Leg leg = b.leg;
			int bought = (int) b.bought;
			int onWorlds = (int) Math.min(worlds, b.bought);
			used = Math.max(used, onWorlds);
			talks += (b.bought + leg.getPerTalk() - 1) / leg.getPerTalk();
			clicks += FlipTime.clicks(b.bought, onWorlds);
			ShopFlip f = new ShopFlip(bought, b.cost, leg.getUnit() * bought, b.first, b.last, leg.getUnit(), b.limit, onWorlds);
			flips.add(Flips.fromShop(new Deal(kind, shop, leg.getName(), leg.getItemId(), f, leg.getUnchecked(), leg.getVolume())));
		}
		if (flips.isEmpty())
		{
			return null;
		}
		flips.sort(Comparator.comparingLong(Flip::getProfit).reversed().thenComparing(Flip::getItem));
		double active = FlipTime.TRIP_SECONDS + (shop.getTalk() != null ? talks * FlipTime.TALK_SECONDS
			: (used - 1) * FlipTime.HOP_SECONDS + clicks * FlipTime.CLICK_SECONDS);
		if (kind == Kind.ALCH)
		{
			active += total * FlipTime.ALCH_SECONDS;
		}
		double wait = kind == Kind.GE ? prices.geHours() * 3600 : 0;
		Flip.Dest dest = flips.get(0).getDest();
		return new FlipRun(Flip.Source.SHOP, dest, shop, flips, used, Math.max(active, wait), 0);
	}

	/** An item a shop sells and what it's worth where it's going, ready to plan buying it. */
	@Value
	static class Leg
	{
		String name;
		int itemId;
		/** Store value, or what gives the shop's set price. */
		int value;
		/** What the shop charges at normal stock, in tenths of a percent. */
		int rate;
		int delta;
		/** The shop's normal stock, or -1 if it never runs out. */
		int normalStock;
		/** What one is worth where it's going. */
		long unit;
		boolean stackable;
		/** Most to buy: what the GE trades, alchs in an hour, or Long.MAX_VALUE. */
		long cap;
		ShopFlip.Limit capLimit;
		/** For the GE, about how many trade in the time the player will wait; Long.MAX_VALUE otherwise. */
		long volume;
		/** Bought per conversation, from a trader bought from by talking. */
		int perTalk;
		List<String> unchecked;

		/** What the next one costs once {@code depth} have been bought on a world. */
		long price(long depth)
		{
			return ShopFlipAdvisor.price(value, normalStock < 0 ? rate : (int) Math.min(Integer.MAX_VALUE, rate + delta * depth));
		}
	}

	/** An item to plan buying from a shop, or null if it isn't worth anything where it's going or the player can't. */
	static Leg leg(Kind kind, Shop shop, String name, Shop.Item item, Prices prices, Unlocks unlocks, ShopPerks perks)
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
		int[] bundle = item.getB();
		int perTalk = bundle != null && bundle.length == 2 && bundle[0] > 0 ? bundle[0] : 1;
		return new Leg(name, id, value, rate, delta, item.getS() < 0 ? -1 : item.getS(), unit, prices.stackable(id), cap, capLimit,
			volume, perTalk, missing.getUnchecked().isEmpty() ? Collections.emptyList() : new ArrayList<>(missing.getUnchecked()));
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
		Leg leg = leg(kind, shop, name, item, prices, unlocks, perks);
		if (leg == null)
		{
			return null;
		}
		int space = leg.isStackable() ? MAX_STACK : INVENTORY_SIZE;
		// Bought by talking, sometimes one per conversation: hopping isn't suggested there
		int worlds = shop.getTalk() != null ? 1 : maxWorlds;
		ShopFlip flip = ShopFlipAdvisor.planHopping(leg.getValue(), leg.getNormalStock(), leg.getRate(), leg.getDelta(),
			leg.getUnit(), 1, space, coins, leg.getCap(), leg.getCapLimit(), worlds);
		if (flip == null)
		{
			return null;
		}
		return new Deal(kind, shop, name, leg.getItemId(), flip, leg.getUnchecked(), leg.getVolume());
	}
}
