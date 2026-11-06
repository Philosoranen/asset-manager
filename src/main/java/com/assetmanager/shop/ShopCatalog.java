package com.assetmanager.shop;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Every shop that buys items for coins, and the best price one would pay for an item.
 * <p>
 * A shop pays {@code value * rate}, where the rate drops by {@code delta} for each item it holds above its
 * normal stock, down to a minimum of 10%. Prices assume the shop is at its normal stock and that the player
 * hops to a fresh world (where the price is back to normal) once selling another would no longer be worth it.
 */
@Slf4j
public class ShopCatalog
{
	/** Shops never pay less than 10% of an item's value. */
	static final int MIN_RATE = 100;
	/** maxWorlds for a player happy to hop as many worlds as it takes, over as many sessions as it takes. */
	public static final int NO_WORLD_LIMIT = 0;
	/** Standing this close to a shop counts as being at it. */
	static final int AT_SHOP_TILES = 20;

	@Getter
	private final String generated;
	private final List<Shop> shops;

	/** Shops that list each item (by lowercased name), and general stores, which buy any tradeable item. */
	private final Map<String, List<Shop>> listing = new HashMap<>();
	private final List<Shop> generalStores = new ArrayList<>();
	private final Map<Shop, Integer> order = new IdentityHashMap<>();

	public ShopCatalog(List<Shop> shops, String generated)
	{
		this.shops = shops;
		this.generated = generated;
		for (Shop shop : shops)
		{
			// Gson reads maps as sorted trees, which are slow to look up in; this is looked up in a lot
			shop.setItems(new HashMap<>(shop.getItems()));
			order.put(shop, order.size());
			if (shop.isGeneral())
			{
				generalStores.add(shop);
			}
			for (String item : shop.getItems().keySet())
			{
				listing.computeIfAbsent(item, k -> new ArrayList<>()).add(shop);
			}
		}
		groupShops(shops);
	}

	/**
	 * Groups the shops that pay exactly the same: the same kind of shop with the same rates, buying the same items on
	 * the same terms (general stores buy anything, so only items with terms of their own count). Each group is named
	 * for its kind of shop, like "General store"; a shop like no other keeps its own name.
	 */
	private void groupShops(List<Shop> shops)
	{
		Map<String, List<Shop>> bySignature = new LinkedHashMap<>();
		for (Shop shop : shops)
		{
			if (shop.getBuyMultiplier() > 0 && shop.getType() != null)
			{
				bySignature.computeIfAbsent(signature(shop), k -> new ArrayList<>()).add(shop);
			}
		}
		// Kinds of shop with more than one group (general stores at 40% and at 55%, say), largest first
		Map<String, List<List<Shop>>> byType = new HashMap<>();
		for (List<Shop> group : bySignature.values())
		{
			if (group.size() > 1)
			{
				byType.computeIfAbsent(group.get(0).getType(), k -> new ArrayList<>()).add(group);
			}
		}
		Set<String> taken = new HashSet<>();
		for (List<List<Shop>> groups : byType.values())
		{
			groups.sort(Comparator.comparingInt((List<Shop> g) -> g.size()).reversed());
			Shop main = groups.get(0).get(0);
			for (int i = 0; i < groups.size(); i++)
			{
				List<Shop> group = groups.get(i);
				Shop first = group.get(0);
				String label = first.getType();
				if (i > 0)
				{
					// Tell it apart from the main group by its rates, or else (or if another group has those rates too)
					// by where its shops are
					String byRates = label + " (" + percent(first.getBuyMultiplier()) + ", " + percent(first.getDelta()) + " less each)";
					boolean sameRates = first.getBuyMultiplier() == main.getBuyMultiplier() && first.getDelta() == main.getDelta();
					label = sameRates || taken.contains(byRates) ? label + " (" + places(group) + ")" : byRates;
				}
				String unique = label;
				for (int n = 2; !taken.add(unique); n++)
				{
					unique = label + " " + n;
				}
				for (Shop shop : group)
				{
					shop.setGroup(unique);
				}
			}
		}
	}

	private static String signature(Shop shop)
	{
		StringBuilder sb = new StringBuilder()
			.append(shop.getType()).append('|').append(shop.getBuyMultiplier()).append('|').append(shop.getDelta())
			.append('|').append(shop.getGlovesTier()).append('|').append(shop.getGlovesBuyMultiplier())
			.append('|').append(shop.isWilderness()).append('|').append(shop.isGeneral());
		new TreeMap<>(shop.getItems()).forEach((name, item) ->
		{
			String terms = item.getM() + "," + item.getD() + "," + item.isNb() + "," + item.getP() + "," + item.getG()
				+ "," + item.getCm() + "," + item.getCp();
			// General stores buy anything, so only items with terms of their own tell them apart
			if (!shop.isGeneral() || !"null,null,false,null,null,null,null".equals(terms))
			{
				sb.append('|').append(name).append('=').append(terms);
			}
		});
		return sb.toString();
	}

	/** "Kastori, Hosidius" or "Kastori, Hosidius, ..." */
	private static String places(List<Shop> group)
	{
		List<String> names = new ArrayList<>();
		for (Shop s : group)
		{
			String place = s.getLocation() == null || s.getLocation().isEmpty() ? s.getName() : s.getLocation();
			if (!names.contains(place))
			{
				names.add(place);
			}
		}
		return names.size() <= 2 ? String.join(", ", names) : names.get(0) + ", " + names.get(1) + ", ...";
	}

	/** 400 tenths of a percent -> "40%", 3 -> "0.3%". */
	private static String percent(int tenths)
	{
		return tenths % 10 == 0 ? tenths / 10 + "%" : tenths / 10 + "." + Math.abs(tenths % 10) + "%";
	}

	/** Every shop, in the catalog's order. */
	public List<Shop> getShops()
	{
		return Collections.unmodifiableList(shops);
	}

	/** The shops in a group (or just the one shop, by its name), in the catalog's order. */
	public List<Shop> groupMembers(String group)
	{
		List<Shop> out = new ArrayList<>();
		for (Shop shop : shops)
		{
			if (shop.getGroup().equals(group))
			{
				out.add(shop);
			}
		}
		return out;
	}

	/** The shop in a group closest to {@code from}, or null if there's none. */
	public Shop nearestInGroup(String group, WorldPoint from)
	{
		return groupMembers(group).stream().min(Comparator.comparingInt(s -> s.distanceTo(from))).orElse(null);
	}

	/** The shops that might buy an item, in the catalog's order: those listing it, and general stores. */
	private List<Shop> candidates(String key)
	{
		List<Shop> listed = listing.getOrDefault(key, Collections.emptyList());
		List<Shop> out = new ArrayList<>(listed.size() + generalStores.size());
		out.addAll(listed);
		for (Shop s : generalStores)
		{
			if (!s.getItems().containsKey(key))
			{
				out.add(s);
			}
		}
		out.sort(Comparator.comparingInt(order::get));
		return out;
	}

	private static class Data
	{
		String generated;
		List<Shop> shops;
	}

	public static ShopCatalog load(Gson gson)
	{
		try (InputStream in = ShopCatalog.class.getResourceAsStream("/com/assetmanager/shops.json");
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Data data = gson.fromJson(reader, Data.class);
			log.debug("Loaded {} shops (wiki data from {})", data.shops.size(), data.generated);
			return new ShopCatalog(data.shops, data.generated);
		}
		catch (Exception e)
		{
			log.warn("Unable to load shop data", e);
			return new ShopCatalog(Collections.emptyList(), null);
		}
	}

	/** Finds a shop by the name shown on its window, ignoring case and punctuation. Null if unknown. */
	public Shop findByName(String displayName)
	{
		String want = normalize(displayName);
		for (Shop shop : shops)
		{
			if (normalize(shop.getName()).equals(want))
			{
				return shop;
			}
		}
		return null;
	}

	static String normalize(String name)
	{
		return name == null ? "" : name.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	/**
	 * The shop paying the most for a whole stack of an item, or null if no shop buys it.
	 * <p>
	 * On each world the player sells while each sale pays at least {@code alternativeUnit + minGainPerItem}, then
	 * hops to a fresh world where the price is back to normal. A stack goes to a shop whole or not at all: the offer's
	 * quantity is 0 if even the first sale doesn't beat the alternative, or it needs more than {@code maxWorlds}
	 * worlds. Shops are compared by what they gain over the alternative. Hopping is quick, and every stack on a trip
	 * sells on each world at once, so hops aren't counted against a shop.
	 *
	 * @param alternativeUnit coins for one from the next best way to cash out (GE or alching), or 0 for none
	 * @param skipShops       names of shops not to consider
	 */
	public ShopOffer bestOffer(String name, int storeValue, int quantity, boolean tradeable, ShopPerks perks,
		long alternativeUnit, SellRules rules, Set<String> skipShops)
	{
		if (storeValue <= 0 || quantity <= 0)
		{
			return null;
		}
		long minUnit = alternativeUnit + Math.max(1, rules.getMinGainPerItem());
		String key = name.toLowerCase();
		List<ShopOffer> offers = new ArrayList<>();
		// Many shops (most general stores) pay the same rates, so each distinct rate is worked out once
		Map<Long, ShopOffer> byRate = new HashMap<>();
		for (Shop shop : candidates(key))
		{
			if (perks.isAvoidWilderness() && shop.isWilderness() || skipShops.contains(shop.getName())
				|| skipShops.contains(shop.getGroup()))
			{
				continue;
			}
			Rate r = rateFor(shop, key, tradeable, perks);
			if (r == null)
			{
				continue;
			}
			ShopOffer offer;
			if (r.getFixedPrice() != null)
			{
				int each = r.getFixedPrice();
				// A fixed price never drops, so everything sells on one world
				offer = new ShopOffer(shop, each >= minUnit ? quantity : 0, each, (long) each * quantity, 0, false, quantity, 1, true,
					r.isCharos(), 0);
			}
			else
			{
				long rateKey = ((long) r.getRate() << 32) | (r.getDelta() & 0xFFFFFFFFL);
				ShopOffer same = byRate.get(rateKey);
				offer = same != null && same.isUsesKaramjaGloves() == r.isGloves() && same.isUsesCharos() == r.isCharos()
					? same.withShop(shop)
					: rateOffer(shop, r, storeValue, quantity, minUnit, rules.getMaxWorlds());
				byRate.putIfAbsent(rateKey, offer);
			}
			offers.add(offer);
		}
		return pick(offers, alternativeUnit * quantity, perks.getLocation(), perks.getPreferredShops());
	}

	/** As above, with no shops skipped. */
	public ShopOffer bestOffer(String name, int storeValue, int quantity, boolean tradeable, ShopPerks perks,
		long alternativeUnit, int minGainPerItem, int maxWorlds)
	{
		return bestOffer(name, storeValue, quantity, tradeable, perks, alternativeUnit, new SellRules(minGainPerItem, maxWorlds, 0),
			Collections.emptySet());
	}

	/**
	 * The best offer. Shops gaining exactly as much count as paying the same; shops that always pay the same are grouped
	 * (see groupShops), but others can tie for an item too. Gains are compared rather than what the shops pay. Of
	 * those paying the same, the one the player is standing at wins; then one already suggested, so the suggestion
	 * doesn't change on the way there; then the closest. A shop paying even a little less never wins by being closer.
	 */
	static ShopOffer pick(List<ShopOffer> offers, long alternativeTotal, WorldPoint from,
		Set<String> preferred)
	{
		long top = offers.stream().mapToLong(o -> score(o, alternativeTotal)).max().orElse(Long.MIN_VALUE);
		List<ShopOffer> same = new ArrayList<>();
		for (ShopOffer o : offers)
		{
			long s = score(o, alternativeTotal);
			if (s == top)
			{
				same.add(o);
			}
		}
		if (same.isEmpty())
		{
			return null;
		}
		Comparator<ShopOffer> byDistance = Comparator.comparingInt(o -> o.getShop().distanceTo(from));
		ShopOffer closest = same.stream().min(byDistance).get();
		if (closest.getShop().distanceTo(from) > AT_SHOP_TILES)
		{
			closest = same.stream().filter(o -> preferred.contains(o.getShop().getGroup())).min(byDistance).orElse(closest);
		}
		return closest.withEqualShops(same.size() - 1);
	}

	/**
	 * How good an offer is: offers worth using by what they gain over the alternative; the rest below them all, by
	 * their first price, for display.
	 */
	static long score(ShopOffer o, long alternativeTotal)
	{
		return o.getQuantity() == 0 ? -1 - Long.MAX_VALUE / 2 + o.getFirstUnit() : o.getTotal() - alternativeTotal;
	}

	/** What a shop pays for an item, at its normal stock. */
	@Value
	public static class Rate
	{
		/** Tenths of a percent of the item's value; unused for a fixed price. */
		int rate;
		/** Change in rate per item held above normal stock. */
		int delta;
		/** The shop's normal stock of the item: 0 for an item a general store doesn't stock. */
		int normalStock;
		/** Coins per item for traders paying a fixed price, or null. */
		Integer fixedPrice;
		boolean gloves;
		boolean charos;
	}

	/**
	 * What a shop pays for an item, with the player's perks; null if it doesn't buy it.
	 *
	 * @param name the item's name, lowercased
	 */
	public static Rate rateFor(Shop shop, String name, boolean tradeable, ShopPerks perks)
	{
		Shop.Item item = shop.getItems().get(name);
		if (shop.getBuyMultiplier() <= 0 || item != null && item.isNb() || item == null && !(shop.isGeneral() && tradeable))
		{
			return null;
		}
		int normal = item == null ? 0 : Math.max(0, item.getS());
		if (item != null && item.getP() != null)
		{
			boolean charos = perks.isCharos() && item.getCp() != null;
			return new Rate(0, 0, normal, charos ? item.getCp() : item.getP(), false, charos);
		}
		boolean gloves = shop.getGlovesTier() != null && perks.getGlovesTier() >= shop.getGlovesTier();
		int rate;
		int delta;
		if (item != null)
		{
			rate = item.getM() != null ? item.getM() : shop.getBuyMultiplier();
			delta = item.getD() != null ? item.getD() : shop.getDelta();
			if (gloves && item.getG() != null)
			{
				rate = item.getG();
			}
			else
			{
				gloves = false;
			}
		}
		else
		{
			rate = shop.getBuyMultiplier();
			delta = shop.getDelta();
			if (gloves && shop.getGlovesBuyMultiplier() != null)
			{
				rate = shop.getGlovesBuyMultiplier();
			}
			else
			{
				gloves = false;
			}
		}
		boolean charos = perks.isCharos() && item != null && item.getCm() != null;
		if (charos)
		{
			rate = item.getCm();
		}
		return new Rate(rate, delta, normal, null, gloves, charos);
	}

	/**
	 * How many to sell to a shop now, with {@code stock} of the item already in it, before the next sale would pay less
	 * than {@code minUnit}. Other players' sales count: a shop already holding extra pays less.
	 */
	public static int sellNow(int value, Rate r, int stock, long minUnit, int have)
	{
		if (r.getFixedPrice() != null)
		{
			return r.getFixedPrice() >= minUnit ? have : 0;
		}
		return perWorld(value, r.getRate() - r.getDelta() * (stock - r.getNormalStock()), r.getDelta(), minUnit, have);
	}

	private static ShopOffer rateOffer(Shop shop, Rate r, int storeValue, int quantity, long minUnit, int maxWorlds)
	{
		int rate = r.getRate();
		int delta = r.getDelta();
		boolean gloves = r.isGloves();
		boolean charos = r.isCharos();

		int perWorld = perWorld(storeValue, rate, delta, minUnit, quantity);
		if (perWorld == 0)
		{
			// Not even the first sale beats the alternative: priced on one world, for comparison only
			return new ShopOffer(shop, 0, price(storeValue, rate), totalFor(storeValue, quantity, rate, delta), rate, gloves,
				quantity, 1, false, charos, 0);
		}
		int worlds = (quantity + perWorld - 1) / perWorld;
		long total = (long) (quantity / perWorld) * totalFor(storeValue, perWorld, rate, delta)
			+ totalFor(storeValue, quantity % perWorld, rate, delta);
		boolean tooManyWorlds = maxWorlds != NO_WORLD_LIMIT && worlds > maxWorlds;
		return new ShopOffer(shop, tooManyWorlds ? 0 : quantity, price(storeValue, rate), total, rate, gloves, perWorld, worlds,
			false, charos, 0);
	}

	/**
	 * How many to sell on one world before the next sale would pay less than {@code minUnit}: 0 if even the first
	 * doesn't, or all of them if the price never drops that far.
	 */
	static int perWorld(int value, int rate, int delta, long minUnit, int quantity)
	{
		if (price(value, rate) < minUnit)
		{
			return 0;
		}
		if (delta <= 0 || price(value, MIN_RATE) >= minUnit)
		{
			return quantity;
		}
		// The price reaches the floor within (rate - MIN_RATE) / delta sales, and the floor is too low, so this ends
		int n = 1;
		while (n < quantity && price(value, rate - delta * n) >= minUnit)
		{
			n++;
		}
		return n;
	}

	static long price(int value, int rate)
	{
		return (long) value * Math.max(MIN_RATE, rate) / 1000;
	}

	/** Coins for selling {@code quantity} items one after another on one world, as the rate drops by delta per item. */
	static long totalFor(int value, int quantity, int rate, int delta)
	{
		long total = 0;
		int sold = 0;
		while (sold < quantity && delta > 0 && rate - delta * sold > MIN_RATE)
		{
			total += price(value, rate - delta * sold);
			sold++;
		}
		// Whatever's left sells at a constant rate (either no drop, or the 10% floor).
		int rest = quantity - sold;
		total += (long) rest * price(value, delta > 0 ? MIN_RATE : rate);
		return total;
	}
}
