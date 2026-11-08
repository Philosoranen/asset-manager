package com.assetmanager.flip;

import com.assetmanager.AccountMode;
import com.assetmanager.shop.SellRules;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.shop.ShopOffer;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.Unlocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The best flips of every kind in one list. Shop-bought ones come from {@link BestBuys}; this adds what's worth buying
 * on the Grand Exchange to sell to a shop, put in Death's coffer or alch.
 * <p>
 * Every flip is one go: a shop visit, or one round of GE offers in the time the player will wait (no more than the buy
 * limit). They're ranked by what that makes.
 */
public final class Flips
{
	/** Alchs in an hour: one every 5 ticks. */
	public static final long ALCHS_PER_HOUR = 1200;

	/** Each shop sale only has to beat what the item cost on the GE. */
	private static final SellRules UNLIMITED = new SellRules(1, ShopCatalog.NO_WORLD_LIMIT, 0);

	private Flips()
	{
	}

	/**
	 * Where an account can cash out flips. Ironmen can't use the GE, and the coffer is the only flip that makes sense
	 * for them; ultimate ironmen can't use the coffer, so they alch.
	 */
	public static List<Flip.Dest> destinations(AccountMode mode)
	{
		if (mode.canUseGrandExchange())
		{
			return List.of(Flip.Dest.values());
		}
		return List.of(mode.canUseCoffer() ? Flip.Dest.COFFER : Flip.Dest.ALCH);
	}

	/** The player's choice of where to cash out, if their account can; otherwise the first place it can. */
	public static Flip.Dest destination(AccountMode mode, Flip.Dest chosen)
	{
		List<Flip.Dest> can = destinations(mode);
		return can.contains(chosen) ? chosen : can.get(0);
	}

	/** What items cost on the GE and what they're worth elsewhere. Gathered on the client thread. */
	public interface GeMarket
	{
		/** The items with live GE prices. */
		Collection<Integer> items();

		/** What one costs on the GE, waiting as long as the player will; 0 if its price can't be relied on. */
		long buy(int itemId);

		/** About how many trade in the time the player will wait; -1 if unknown. */
		long trades(int itemId);

		/** The GE buy limit per 4 hours, or 0 if unknown. */
		int buyLimit(int itemId);

		/** The item's name in lowercase, as the shop data has it; null if unknown. */
		String name(int itemId);

		/** The item's store value, which shops pay a % of. */
		int storeValue(int itemId);

		/** Coins added to the coffer per item, or 0 if it isn't accepted. */
		long coffer(int itemId);

		/** Coins from alching one, less the nature rune. */
		long alch(int itemId);

		/** Whether it can be alched; shops won't buy items that can't. */
		boolean alchable(int itemId);
	}

	/**
	 * Whatever's worth buying on the GE to cash out at {@code dest}. Selling back on the GE isn't a flip this finds.
	 *
	 * @param dest      where the player wants to cash out: a shop, the coffer or alching
	 * @param unlocks   what the player has unlocked, for the shops they can sell to
	 * @param maxWorlds the most worlds to hop selling to a shop, or ShopCatalog.NO_WORLD_LIMIT
	 * @param coins     coins to spend
	 * @param alchCasts how many alchs to plan for
	 */
	public static List<Flip> fromGe(GeMarket m, Flip.Dest dest, ShopCatalog catalog, Unlocks unlocks, ShopPerks perks,
		int maxWorlds, long coins, long alchCasts)
	{
		if (dest == Flip.Dest.GE)
		{
			return Collections.emptyList();
		}
		Map<Shop, Unlocks.Missing> missing = new IdentityHashMap<>();
		for (Shop shop : catalog.getShops())
		{
			missing.put(shop, unlocks.missing(shop, null));
		}
		Predicate<Shop> noted = shop -> missing.get(shop).isMet() && shop.getTalk() == null;
		Predicate<Shop> unnoted = shop -> missing.get(shop).isMet() && shop.getTalk() != null;
		List<Flip> out = new ArrayList<>();
		for (int id : m.items())
		{
			long cost = m.buy(id);
			long trades = m.trades(id);
			// Items that don't trade can't be bought
			if (cost <= 0 || trades < 1)
			{
				continue;
			}
			long cap = trades;
			ShopFlip.Limit why = ShopFlip.Limit.VOLUME;
			int limit = m.buyLimit(id);
			if (limit > 0 && limit < cap)
			{
				cap = limit;
				why = ShopFlip.Limit.BUY_LIMIT;
			}
			if (coins / cost < cap)
			{
				cap = coins / cost;
				why = ShopFlip.Limit.COINS;
			}
			if (cap < 1)
			{
				continue;
			}
			Flip best = null;

			long coffer = m.coffer(id);
			if (dest == Flip.Dest.COFFER && coffer > cost)
			{
				best = flip(Flip.Dest.COFFER, m, id, cap, cost, coffer, why, trades);
			}

			long alch = m.alch(id);
			if (dest == Flip.Dest.ALCH && alch > cost)
			{
				best = better(best, alchCasts < cap
					? flip(Flip.Dest.ALCH, m, id, alchCasts, cost, alch, ShopFlip.Limit.CASTS, trades)
					: flip(Flip.Dest.ALCH, m, id, cap, cost, alch, why, trades));
			}

			String name = m.name(id);
			int value = m.storeValue(id);
			// Shops won't buy items that can't be alched, though some have a store value to protect them on death
			if (dest == Flip.Dest.SHOP && name != null && value > 0 && m.alchable(id))
			{
				best = better(best, toShop(m, catalog, perks, noted, id, name, value, cost, cap, why, maxWorlds, trades, missing));
				// Traders bought from by talking only take unnoted items, so it's an inventory a trip
				long trip = Math.min(cap, BestBuys.INVENTORY_SIZE);
				best = better(best, toShop(m, catalog, perks, unnoted, id, name, value, cost, trip,
					trip < cap ? ShopFlip.Limit.SPACE : why, maxWorlds, trades, missing));
			}

			if (best != null)
			{
				out.add(best);
			}
		}
		return out;
	}

	/**
	 * Selling up to {@code cap} bought on the GE to the best of the {@code usable} shops, hopping no more than
	 * {@code maxWorlds}; null if none pays more than the GE charges.
	 */
	private static Flip toShop(GeMarket m, ShopCatalog catalog, ShopPerks perks, Predicate<Shop> usable, int id, String name,
		int value, long cost, long cap, ShopFlip.Limit why, int maxWorlds, long trades, Map<Shop, Unlocks.Missing> missing)
	{
		// Most items no shop pays more for than the GE charges, which one sale finds out
		ShopOffer probe = catalog.bestOffer(name, value, 1, true, perks, cost, UNLIMITED, usable);
		if (probe == null || probe.getQuantity() == 0)
		{
			return null;
		}
		long qty = Math.min(cap, Integer.MAX_VALUE);
		ShopOffer offer = catalog.bestOffer(name, value, (int) qty, true, perks, cost, UNLIMITED, usable);
		if (offer != null && maxWorlds != ShopCatalog.NO_WORLD_LIMIT && offer.getWorlds() > maxWorlds)
		{
			qty = (long) offer.getPerWorld() * maxWorlds;
			why = ShopFlip.Limit.WORLDS;
			offer = catalog.bestOffer(name, value, (int) qty, true, perks, cost, UNLIMITED, usable);
		}
		if (offer == null || offer.getQuantity() == 0)
		{
			return null;
		}
		List<String> unchecked = missing.get(offer.getShop()).getUnchecked();
		return new Flip(Flip.Source.GE, Flip.Dest.SHOP, name, id, null, offer, offer.getQuantity(), cost * offer.getQuantity(),
			offer.getTotal(), cost, offer.getFirstUnit(), why, unchecked.isEmpty() ? Collections.emptyList() : new ArrayList<>(unchecked),
			trades, false, 1);
	}

	private static Flip flip(Flip.Dest dest, GeMarket m, int id, long qty, long cost, long unit, ShopFlip.Limit why, long trades)
	{
		String name = m.name(id);
		return new Flip(Flip.Source.GE, dest, name == null ? "item " + id : name, id, null, null, (int) Math.min(qty, Integer.MAX_VALUE),
			cost * qty, unit * qty, cost, unit, why, Collections.emptyList(), trades, false, 1);
	}

	private static Flip better(Flip a, Flip b)
	{
		if (b == null)
		{
			return a;
		}
		return a == null || b.getProfit() > a.getProfit() ? b : a;
	}

	/** A shop deal as a flip. */
	public static Flip fromShop(BestBuys.Deal d)
	{
		ShopFlip f = d.getFlip();
		Flip.Dest dest = d.getKind() == BestBuys.Kind.COFFER ? Flip.Dest.COFFER : d.getKind() == BestBuys.Kind.ALCH ? Flip.Dest.ALCH
			: Flip.Dest.GE;
		return new Flip(Flip.Source.SHOP, dest, d.getItem(), d.getItemId(), d.getShop(), null, f.getQuantity(), f.getCost(),
			f.getRevenue(), f.getFirstPrice(), f.getGeUnit(), f.getLimit(), d.getUnchecked(), d.getTradesPerHour(), d.isPriceRises(),
			f.getWorlds());
	}

	/**
	 * The best flips, best first by {@code sort}: for each item, the best way to flip it from each source (a shop and
	 * the GE have their own limits, so both can be worth doing).
	 *
	 * @param minGain only flips making at least this much
	 * @param limit   at most this many
	 */
	public static List<Flip> rank(Collection<Flip> flips, long minGain, int limit, FlipSort sort)
	{
		Comparator<Flip> order = sort.best();
		Map<String, Flip> best = new HashMap<>();
		for (Flip f : flips)
		{
			if (f.getProfit() < Math.max(1, minGain))
			{
				continue;
			}
			best.merge(f.getItemId() + ":" + f.getSource(), f, (a, b) -> order.compare(b, a) < 0 ? b : a);
		}
		List<Flip> out = new ArrayList<>(best.values());
		out.sort(order);
		return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
	}
}
