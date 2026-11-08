package com.assetmanager.value;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Optional real-time Grand Exchange prices from the OSRS Wiki (prices.runescape.wiki), refreshed every
 * 5 minutes while enabled. RuneLite's own prices are a single average refreshed every 30 minutes; these give
 * the separate buy and sell prices and how much actually trades.
 */
@Slf4j
@Singleton
public class LivePrices
{
	private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/";
	// The wiki asks for a descriptive User-Agent
	private static final String USER_AGENT = "Asset Manager RuneLite plugin";
	private static final long REFRESH_MINUTES = 5;
	/** The last day's trading changes slowly, so it's fetched less often. */
	private static final long DAY_REFRESH_MILLIS = TimeUnit.HOURS.toMillis(1);
	/** Item names, values and buy limits hardly ever change: once a day. */
	private static final long MAPPING_REFRESH_MILLIS = TimeUnit.DAYS.toMillis(1);

	/** What the wiki knows about an item that isn't a price. */
	@Value
	public static class Info
	{
		String name;
		/** The item's store value. */
		int value;
		int highAlch;
		/** GE buy limit per 4 hours, or 0 if unknown. */
		int limit;
		/**
		 * Whether it can be alched. Items that can't (event rewards and the like) often have a store value only so
		 * they're protected on death, and shops won't buy them.
		 */
		boolean alchable;
		/** Whether it's a members item. */
		boolean members;
	}

	@Value
	public static class Price
	{
		/** Recent instant-buy price (what buyers pay), or 0 if none traded recently. */
		int high;
		/** Recent instant-sell price (what sellers get), or 0 if none traded recently. */
		int low;
	}

	@Value
	public static class Volume
	{
		/** Items bought at the instant-buy price in the last hour. */
		int high;
		/** Items sold at the instant-sell price in the last hour. */
		int low;
		/** Average instant-buy price over the last hour, or 0 if none. */
		int avgHigh;
		/** Average instant-sell price over the last hour, or 0 if none. */
		int avgLow;
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final ScheduledExecutorService executor;

	private volatile Map<Integer, Price> prices = Collections.emptyMap();
	private volatile Map<Integer, Volume> volumes = Collections.emptyMap();
	/** Items traded in the last day, by id; null until loaded. */
	private volatile Map<Integer, Long> dayVolumes;
	private volatile long dayFetchedAt;
	private volatile Map<Integer, Info> mapping = Collections.emptyMap();
	private volatile long mappingFetchedAt;
	private ScheduledFuture<?> task;
	private Runnable onUpdate = () -> {};

	@Inject
	LivePrices(OkHttpClient http, Gson gson, ScheduledExecutorService executor)
	{
		this.http = http;
		this.gson = gson;
		this.executor = executor;
	}

	public void setOnUpdate(Runnable onUpdate)
	{
		this.onUpdate = onUpdate;
	}

	public synchronized void start()
	{
		if (task == null)
		{
			task = executor.scheduleWithFixedDelay(this::refresh, 0, REFRESH_MINUTES, TimeUnit.MINUTES);
		}
	}

	public synchronized void stop()
	{
		if (task != null)
		{
			task.cancel(false);
			task = null;
		}
		prices = Collections.emptyMap();
		volumes = Collections.emptyMap();
		dayVolumes = null;
		dayFetchedAt = 0;
		mapping = Collections.emptyMap();
		mappingFetchedAt = 0;
	}

	public boolean isAvailable()
	{
		return !prices.isEmpty();
	}

	/**
	 * The recent price to sell at, or 0 if unknown. The latest trade on each side can be a one-off, so the
	 * lower of it and the last hour's average is used, and the buy side is ignored if nobody bought in the last
	 * hour.
	 */
	public int sellPrice(int itemId, GePrice mode)
	{
		Price p = prices.get(itemId);
		if (p == null)
		{
			return 0;
		}
		Volume hour = volumes.get(itemId);
		int high = p.getHigh();
		int low = p.getLow();
		if (hour != null)
		{
			high = hour.getHigh() == 0 ? 0 : lower(high, hour.getAvgHigh());
			low = lower(low, hour.getAvgLow());
		}
		return mode.pick(high, low);
	}

	/**
	 * What buying one costs, waiting as long as the player will, or 0 if the price can't be relied on: nothing traded
	 * in the last hour, or the spread is so wide that the item barely trades. The latest trade on each side can be a
	 * one-off, so the higher of it and the last hour's average is used.
	 */
	public int buyPrice(int itemId, Patience patience)
	{
		Price p = prices.get(itemId);
		Volume hour = volumes.get(itemId);
		if (p == null || hour == null || hour.getHigh() + hour.getLow() == 0)
		{
			return 0;
		}
		int high = Math.max(p.getHigh(), hour.getAvgHigh());
		int low = Math.max(p.getLow(), hour.getAvgLow());
		if (high <= 0 || low <= 0 || (long) high > (long) low * GePrice.MAX_SPREAD)
		{
			return 0;
		}
		switch (patience.getPrice())
		{
			case INSTANT:
				// Paying what buyers have been paying fills right away
				return high;
			case MIDPOINT:
				return (int) (((long) high + low + 1) / 2);
			default:
				// Offering what sellers have been accepting fills in time
				return Math.max(low, Math.min(high, low + 1));
		}
	}

	/** The items with live prices. */
	public Collection<Integer> items()
	{
		return prices.keySet();
	}

	/** Names, values and buy limits by id; empty until loaded. */
	public Info info(int itemId)
	{
		return mapping.get(itemId);
	}

	/** The lower of two prices, ignoring unknown (0) ones. */
	private static int lower(int a, int b)
	{
		return a <= 0 ? b : b <= 0 ? a : Math.min(a, b);
	}

	/** The recent instant-buy price, or 0 if unknown. */
	public int instantBuy(int itemId)
	{
		Price p = prices.get(itemId);
		return p == null ? 0 : p.getHigh();
	}

	/** Items traded in the last hour, or null if unknown. */
	public Volume volume(int itemId)
	{
		return volumes.get(itemId);
	}

	/**
	 * About how many of an item trade on the GE an hour, averaged over the last day so a busy or quiet hour doesn't
	 * skew it; from the last hour alone until the day's figures have loaded. Items missing from the wiki's figures
	 * didn't trade at all. -1 if unknown (live prices off or not loaded yet).
	 */
	public long tradesPerHour(int itemId)
	{
		return tradesPerHour(volumes.get(itemId), !volumes.isEmpty(), dayVolumes, itemId);
	}

	static long tradesPerHour(Volume hour, boolean hourLoaded, Map<Integer, Long> day, int itemId)
	{
		if (day != null)
		{
			return day.getOrDefault(itemId, 0L) / 24;
		}
		if (!hourLoaded)
		{
			return -1;
		}
		return hour == null ? 0 : (long) hour.getHigh() + hour.getLow();
	}

	private void refresh()
	{
		fetch("latest", data ->
		{
			Map<Integer, Price> map = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : data.entrySet())
			{
				JsonObject o = e.getValue().getAsJsonObject();
				int high = intOf(o, "high");
				int low = intOf(o, "low");
				if (high > 0 || low > 0)
				{
					map.put(Integer.parseInt(e.getKey()), new Price(high, low));
				}
			}
			prices = map;
			log.debug("Loaded {} live prices", map.size());
			onUpdate.run();
		});
		fetch("1h", data ->
		{
			Map<Integer, Volume> map = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : data.entrySet())
			{
				JsonObject o = e.getValue().getAsJsonObject();
				map.put(Integer.parseInt(e.getKey()), new Volume(intOf(o, "highPriceVolume"), intOf(o, "lowPriceVolume"),
					intOf(o, "avgHighPrice"), intOf(o, "avgLowPrice")));
			}
			volumes = map;
		});
		if (System.currentTimeMillis() - mappingFetchedAt >= MAPPING_REFRESH_MILLIS)
		{
			fetchMapping();
		}
		if (System.currentTimeMillis() - dayFetchedAt >= DAY_REFRESH_MILLIS)
		{
			fetch("24h", data ->
			{
				Map<Integer, Long> map = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : data.entrySet())
				{
					JsonObject o = e.getValue().getAsJsonObject();
					map.put(Integer.parseInt(e.getKey()), (long) intOf(o, "highPriceVolume") + intOf(o, "lowPriceVolume"));
				}
				dayVolumes = map;
				dayFetchedAt = System.currentTimeMillis();
				onUpdate.run();
			});
		}
	}

	private void fetchMapping()
	{
		Request request = new Request.Builder().url(BASE + "mapping").header("User-Agent", USER_AGENT).build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Live price request failed: mapping", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("Live price request failed: mapping {}", response.code());
						return;
					}
					Map<Integer, Info> map = new HashMap<>();
					for (JsonElement e : gson.fromJson(body.charStream(), JsonArray.class))
					{
						JsonObject o = e.getAsJsonObject();
						if (o.has("id") && o.has("name"))
						{
							map.put(o.get("id").getAsInt(), new Info(o.get("name").getAsString(), intOf(o, "value"),
								intOf(o, "highalch"), intOf(o, "limit"), o.has("highalch"),
								o.has("members") && o.get("members").getAsBoolean()));
						}
					}
					mapping = map;
					mappingFetchedAt = System.currentTimeMillis();
					onUpdate.run();
				}
				catch (RuntimeException e)
				{
					log.debug("Unreadable live price response: mapping", e);
				}
			}
		});
	}

	private void fetch(String endpoint, Consumer<JsonObject> onData)
	{
		Request request = new Request.Builder().url(BASE + endpoint).header("User-Agent", USER_AGENT).build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Live price request failed: {}", endpoint, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("Live price request failed: {} {}", endpoint, response.code());
						return;
					}
					JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
					onData.accept(json.getAsJsonObject("data"));
				}
				catch (RuntimeException e)
				{
					log.debug("Unreadable live price response: {}", endpoint, e);
				}
			}
		});
	}

	private static int intOf(JsonObject o, String key)
	{
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
	}
}
