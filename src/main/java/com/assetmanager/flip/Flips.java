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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.Value;
import lombok.With;
import net.runelite.api.coords.WorldPoint;

/**
 * The best flips of every kind in one list. Shop-bought ones come from {@link BestBuys}; this adds what's worth buying
 * on the Grand Exchange to sell to a shop, put in Death's coffer or alch.
 * <p>
 * Every flip is one go: a shop visit, or one round of GE offers in the time the player will wait (no more than the buy
 * limit). They're ranked by what that makes, or what it makes an hour (see {@link FlipTime}).
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

		/** Whether it's a members item, which can't be traded or used on a free-to-play world. */
		default boolean members(int itemId)
		{
			return false;
		}

		/** Hours the player will wait for offers to fill. */
		default double geHours()
		{
			return 1;
		}
	}

	/**
	 * Whatever's worth buying on the GE to cash out at {@code dest}. Selling back on the GE isn't a flip this finds.
	 * Alching and the coffer are a go per item; selling to shops is a go per shop, with everything it pays more for
	 * than the GE charges sold together, on the same hops.
	 *
	 * @param dest      where the player wants to cash out: a shop, the coffer or alching
	 * @param unlocks   what the player has unlocked, for the shops they can sell to
	 * @param maxWorlds the most worlds to hop selling to a shop, or ShopCatalog.NO_WORLD_LIMIT
	 * @param coins     coins to spend
	 * @param alchCasts how many alchs to plan for
	 * @param maxItems  the most different items to buy on the GE for one shop go (it has 8 offer slots)
	 */
	public static List<FlipRun> fromGe(GeMarket m, Flip.Dest dest, ShopCatalog catalog, Unlocks unlocks, ShopPerks perks,
		int maxWorlds, long coins, long alchCasts, int maxItems)
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
		Predicate<Shop> usable = shop -> missing.get(shop).isMet();
		Predicate<Shop> noted = shop -> missing.get(shop).isMet() && shop.getTalk() == null;
		Predicate<Shop> unnoted = shop -> missing.get(shop).isMet() && shop.getTalk() != null;
		double geHours = m.geHours();
		List<FlipRun> out = new ArrayList<>();
		// What each shop pays more for than the GE charges
		Map<Shop, List<Sale>> sales = new IdentityHashMap<>();
		for (int id : m.items())
		{
			long cost = m.buy(id);
			long trades = m.trades(id);
			// Items that don't trade can't be bought, nor members items on a free-to-play world
			if (cost <= 0 || trades < 1 || !unlocks.isMembers() && m.members(id))
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
			double wait = FlipTime.geWaitSeconds(geHours, why);

			long coffer = m.coffer(id);
			if (dest == Flip.Dest.COFFER && coffer > cost)
			{
				out.add(FlipRun.single(flip(Flip.Dest.COFFER, m, id, cap, cost, coffer, why, trades),
					Math.max(FlipTime.TRIP_SECONDS, wait)));
			}

			long alch = m.alch(id);
			if (dest == Flip.Dest.ALCH && alch > cost)
			{
				Flip f = alchCasts < cap
					? flip(Flip.Dest.ALCH, m, id, alchCasts, cost, alch, ShopFlip.Limit.CASTS, trades)
					: flip(Flip.Dest.ALCH, m, id, cap, cost, alch, why, trades);
				out.add(FlipRun.single(f, Math.max(f.getQuantity() * FlipTime.ALCH_SECONDS, wait)));
			}

			String name = m.name(id);
			int value = m.storeValue(id);
			// Shops won't buy items that can't be alched, though some have a store value to protect them on death
			if (dest == Flip.Dest.SHOP && name != null && value > 0 && m.alchable(id))
			{
				// Most items no shop pays more for than the GE charges, which one sale finds out
				ShopOffer probe = catalog.bestOffer(name, value, 1, true, perks, cost, UNLIMITED, usable);
				if (probe == null || probe.getQuantity() == 0)
				{
					continue;
				}
				Sale sale = new Sale(id, name, value, cost, why, trades, null, why);
				addSales(sales, catalog, perks, noted, sale, cap, why, maxWorlds);
				// Traders bought from by talking only take unnoted items, so it's an inventory a trip
				long trip = Math.min(cap, BestBuys.INVENTORY_SIZE);
				addSales(sales, catalog, perks, unnoted, sale, trip, trip < cap ? ShopFlip.Limit.SPACE : why, maxWorlds);
			}
		}
		// In the catalog's order, so the result is the same every time
		for (Shop shop : catalog.getShops())
		{
			List<Sale> list = sales.get(shop);
			FlipRun run = list == null ? null : sellRun(shop, list, catalog, perks, coins, geHours, missing.get(shop), maxItems);
			if (run != null)
			{
				out.add(run);
			}
		}
		return mergeEqual(out, perks.getLocation());
	}

	/** An item bought on the GE to sell to a shop. */
	@Value
	private static class Sale
	{
		int itemId;
		String name;
		int value;
		/** What one costs on the GE. */
		long cost;
		/** What stopped the GE buying going higher. */
		ShopFlip.Limit geLimit;
		long trades;
		/** What the shop pays for the stack; set per shop. */
		@With
		ShopOffer offer;
		/** What stopped the quantity going higher; set per shop. */
		@With
		ShopFlip.Limit limit;
	}

	/**
	 * Adds a sale of up to {@code qty} to each of the {@code usable} shops paying more than the GE charges, hopping no
	 * more than {@code maxWorlds}.
	 */
	private static void addSales(Map<Shop, List<Sale>> sales, ShopCatalog catalog, ShopPerks perks, Predicate<Shop> usable,
		Sale sale, long qty, ShopFlip.Limit why, int maxWorlds)
	{
		int n = (int) Math.min(qty, Integer.MAX_VALUE);
		for (ShopOffer offer : catalog.offers(sale.getName(), sale.getValue(), n, true, perks, sale.getCost(), UNLIMITED, usable))
		{
			if (offer.getQuantity() == 0)
			{
				continue;
			}
			ShopFlip.Limit limit = why;
			if (maxWorlds != ShopCatalog.NO_WORLD_LIMIT && offer.getWorlds() > maxWorlds)
			{
				offer = offerAt(catalog, perks, sale, offer.getShop(), offer.getPerWorld() * maxWorlds);
				limit = ShopFlip.Limit.WORLDS;
			}
			if (offer != null && offer.getQuantity() > 0)
			{
				sales.computeIfAbsent(offer.getShop(), s -> new ArrayList<>()).add(sale.withOffer(offer).withLimit(limit));
			}
		}
	}

	/** What one shop pays for {@code qty}; null if it won't buy them. */
	private static ShopOffer offerAt(ShopCatalog catalog, ShopPerks perks, Sale sale, Shop shop, long qty)
	{
		List<ShopOffer> offers = catalog.offers(sale.getName(), sale.getValue(), (int) Math.min(qty, Integer.MAX_VALUE), true,
			perks, sale.getCost(), UNLIMITED, s -> s == shop);
		return offers.isEmpty() ? null : offers.get(0);
	}

	/**
	 * A go selling to one shop: the {@code maxItems} items making the most that the coins reach, then the best returns
	 * first while the coins last (and, for a trader taking only unnoted items, the inventory). Null if nothing's worth it.
	 */
	private static FlipRun sellRun(Shop shop, List<Sale> sales, ShopCatalog catalog, ShopPerks perks, long coins, double geHours,
		Unlocks.Missing missing, int maxItems)
	{
		// The GE has only so many offer slots, so it's the items making the most
		sales.sort(Comparator.comparingLong(Flips::saleProfit).reversed().thenComparing(Sale::getName));
		List<Sale> chosen = new ArrayList<>();
		for (Sale sale : sales)
		{
			if (chosen.size() >= maxItems)
			{
				break;
			}
			if (sale.getCost() <= coins)
			{
				chosen.add(sale);
			}
		}
		sales = chosen;
		sales.sort(Comparator.comparingDouble(Flips::saleReturn).reversed().thenComparing(Sale::getName));
		boolean talk = shop.getTalk() != null;
		long coinsLeft = coins;
		long slotsLeft = talk ? BestBuys.INVENTORY_SIZE : Long.MAX_VALUE;
		List<Flip> flips = new ArrayList<>();
		int worlds = 1;
		long clicks = 0;
		double wait = 0;
		for (Sale sale : sales)
		{
			ShopOffer offer = sale.getOffer();
			ShopFlip.Limit limit = sale.getLimit();
			long fit = Math.min(offer.getQuantity(), Math.min(coinsLeft / sale.getCost(), slotsLeft));
			if (fit < 1)
			{
				continue;
			}
			if (fit < offer.getQuantity())
			{
				limit = fit == slotsLeft ? ShopFlip.Limit.SPACE : ShopFlip.Limit.COINS;
				offer = offerAt(catalog, perks, sale, shop, fit);
				if (offer == null || offer.getQuantity() == 0)
				{
					continue;
				}
			}
			int qty = offer.getQuantity();
			coinsLeft -= sale.getCost() * qty;
			slotsLeft -= qty;
			worlds = Math.max(worlds, offer.getWorlds());
			clicks += talk ? 1 : FlipTime.clicks(qty, offer.getWorlds());
			wait = Math.max(wait, FlipTime.geWaitSeconds(geHours, sale.getGeLimit()));
			List<String> unchecked = missing.getUnchecked();
			flips.add(new Flip(Flip.Source.GE, Flip.Dest.SHOP, sale.getName(), sale.getItemId(), null, offer, qty, sale.getCost() * qty,
				offer.getTotal(), sale.getCost(), offer.getFirstUnit(), limit,
				unchecked.isEmpty() ? Collections.emptyList() : new ArrayList<>(unchecked), sale.getTrades(), false, 1));
		}
		if (flips.isEmpty())
		{
			return null;
		}
		flips.sort(Comparator.comparingLong(Flip::getProfit).reversed().thenComparing(Flip::getItem));
		double active = FlipTime.TRIP_SECONDS + (worlds - 1) * FlipTime.HOP_SECONDS
			+ clicks * (talk ? FlipTime.TALK_SECONDS : FlipTime.CLICK_SECONDS);
		return new FlipRun(Flip.Source.GE, Flip.Dest.SHOP, shop, flips, worlds, Math.max(active, wait), 0);
	}

	/** What selling the whole stack makes. */
	private static long saleProfit(Sale s)
	{
		return s.getOffer().getTotal() - s.getCost() * s.getOffer().getQuantity();
	}

	/** What selling the whole stack makes for each coin it costs on the GE. */
	private static double saleReturn(Sale s)
	{
		long cost = s.getCost() * s.getOffer().getQuantity();
		return (s.getOffer().getTotal() - cost) / (double) cost;
	}

	/**
	 * Gos that are just the same at different shops (stores sharing a stock and rates, often), as one: the closest
	 * shop's, counting the others.
	 */
	static List<FlipRun> mergeEqual(List<FlipRun> runs, WorldPoint from)
	{
		List<FlipRun> out = new ArrayList<>(runs.size());
		Map<String, FlipRun> best = new LinkedHashMap<>();
		Map<String, Integer> count = new HashMap<>();
		for (FlipRun run : runs)
		{
			if (run.getShop() == null)
			{
				out.add(run);
				continue;
			}
			StringBuilder key = new StringBuilder().append(run.getSource()).append(run.getDest()).append(':').append(run.getProfit())
				.append(':').append(run.getCost()).append(':').append(run.getWorlds()).append(':').append(run.getShop().getTalk() != null);
			for (Flip f : run.getFlips())
			{
				key.append(':').append(f.getItemId()).append('x').append(f.getQuantity());
			}
			String k = key.toString();
			count.merge(k, 1, Integer::sum);
			best.merge(k, run, (a, b) -> b.getShop().distanceTo(from) < a.getShop().distanceTo(from) ? b : a);
		}
		best.forEach((k, run) -> out.add(count.get(k) > 1 ? run.withEqualShops(count.get(k) - 1) : run));
		return out;
	}

	private static Flip flip(Flip.Dest dest, GeMarket m, int id, long qty, long cost, long unit, ShopFlip.Limit why, long trades)
	{
		String name = m.name(id);
		return new Flip(Flip.Source.GE, dest, name == null ? "item " + id : name, id, null, null, (int) Math.min(qty, Integer.MAX_VALUE),
			cost * qty, unit * qty, cost, unit, why, Collections.emptyList(), trades, false, 1);
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
	 * The best gos, best first by {@code sort}. Only the best general store is kept: they all buy the same things, so
	 * the rest are worse ways to do the same.
	 *
	 * @param minGain only gos making at least this much
	 * @param limit   at most this many
	 */
	public static List<FlipRun> rank(Collection<FlipRun> runs, long minGain, int limit, FlipSort sort)
	{
		List<FlipRun> sorted = new ArrayList<>(runs);
		sorted.sort(sort.best());
		List<FlipRun> out = new ArrayList<>();
		boolean generalStore = false;
		for (FlipRun r : sorted)
		{
			boolean general = r.getShop() != null && r.getShop().isGeneral();
			if (r.getProfit() < Math.max(1, minGain) || general && generalStore)
			{
				continue;
			}
			generalStore |= general;
			out.add(r);
		}
		return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
	}
}
