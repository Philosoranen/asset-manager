package com.assetmanager.shop;

import com.assetmanager.AssetManagerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Filepath;

/**
 * Reads the stock of the open shop each tick and keeps {@link ShopStock} up to date, saved per RuneScape profile so
 * the timers survive logging out. Only reads what the shop window shows.
 */
@Slf4j
@Singleton
public class ShopStockTracker
{
	private static final String KEY = "shopStock";
	private static final String KEY_CYCLES = "shopRestockCycles";

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	/** The plugin's data directory, from RuneLite; null if it couldn't be had. */
	@Setter
	private Filepath directory;
	/** The profile's save file; null until a profile is loaded. */
	private Filepath file;

	@Setter
	private Supplier<Shop> openShop = () -> null;
	/** Called on the client thread when which shops are overstocked, or busy, changes. */
	@Setter
	private Runnable onChange = () -> {};
	/** Saved at most this often while a shop is open, and when it closes. */
	private static final int SAVE_TICKS = 50;

	private volatile ShopStock stock = new ShopStock();
	/** The shop and world being looked at, to tell when the player opens a shop afresh. */
	private String visit;
	private boolean unsaved;
	private int ticksSinceSave;
	/** Which shop items are overstocked on which worlds, and which are busy, as of the last change reported. */
	private Object lastShape;
	/** The player's inventory last tick, to tell a purchase from a restock. */
	private Map<Integer, Integer> lastInventory = Collections.emptyMap();

	@Inject
	ShopStockTracker(Client client, ItemManager itemManager, ConfigManager configManager, Gson gson,
		ScheduledExecutorService executor)
	{
		this.executor = executor;
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.gson = gson;
	}

	public ShopStock get()
	{
		return stock;
	}

	/** What's saved: the overstock seen, and the restock cycles learned. */
	private static class Saved
	{
		List<ShopStock.Seen> seen;
		Map<String, Long> cycles;
	}

	/**
	 * Loads the profile's stock from its file. It used to be kept in the profile's settings, but with every world's
	 * shops it grew too big for RuneLite to sync, and a restart brought back an old synced copy; that's moved over once.
	 */
	public void load()
	{
		file = fileFor(configManager.getRSProfileKey());
		Saved saved = file == null ? null : readFile(file);
		if (saved == null)
		{
			saved = readSettings();
			if (saved != null && file != null)
			{
				write(file, gson.toJson(saved));
			}
		}
		configManager.unsetRSProfileConfiguration(AssetManagerConfig.GROUP, KEY);
		configManager.unsetRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_CYCLES);
		stock = new ShopStock(saved == null || saved.seen == null ? Collections.emptyList() : saved.seen,
			saved == null || saved.cycles == null ? Collections.emptyMap() : saved.cycles, System.currentTimeMillis());
		visit = null;
		unsaved = false;
	}

	/** The profile's file in the plugin's directory; null before a profile is known or without a directory. */
	private Filepath fileFor(String profileKey)
	{
		if (profileKey == null || directory == null)
		{
			return null;
		}
		return directory.joinSegment("shop-stock-" + profileKey.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
	}

	private Saved readFile(Filepath f)
	{
		try
		{
			if (!f.isFile())
			{
				return null;
			}
			try (Reader r = f.openBufferedReader())
			{
				return gson.fromJson(r, Saved.class);
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.debug("Ignoring unreadable shop stock file {}", f, e);
			return null;
		}
	}

	/** The stock as it used to be saved, in the profile's settings; null if there's none. */
	private Saved readSettings()
	{
		String json = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY);
		String cyclesJson = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_CYCLES);
		if (json == null && cyclesJson == null)
		{
			return null;
		}
		Saved s = new Saved();
		try
		{
			s.seen = json == null ? null : gson.fromJson(json, new TypeToken<List<ShopStock.Seen>>()
			{
			}.getType());
			s.cycles = cyclesJson == null ? null : gson.fromJson(cyclesJson, new TypeToken<Map<String, Long>>()
			{
			}.getType());
		}
		catch (JsonParseException e)
		{
			log.debug("Ignoring unreadable shop stock settings", e);
		}
		return s;
	}

	/** Saves now, if anything's changed: when the plugin stops. Client thread. */
	public void flush()
	{
		if (unsaved)
		{
			save();
		}
	}

	private void save()
	{
		unsaved = false;
		ticksSinceSave = 0;
		if (file == null)
		{
			return;
		}
		Saved s = new Saved();
		s.seen = new ArrayList<>(stock.all());
		s.cycles = new HashMap<>(stock.cycles());
		write(file, gson.toJson(s));
	}

	/** Writes off the client thread. */
	private void write(Filepath f, String json)
	{
		executor.execute(() ->
		{
			try
			{
				f.getParent().createDirectories();
				f.write(json);
			}
			catch (IOException e)
			{
				log.warn("Couldn't save shop stock to {}", f, e);
			}
		});
	}

	private Map<Integer, Integer> inventory()
	{
		Map<Integer, Integer> counts = new HashMap<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (Item i : inv.getItems())
			{
				if (i.getId() > 0)
				{
					counts.merge(itemManager.canonicalize(i.getId()), i.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		long now = System.currentTimeMillis();
		ShopStock s = stock;
		boolean changed = s.prune(now);

		Widget items = client.getWidget(InterfaceID.Shopmain.ITEMS);
		Shop shop = openShop.get();
		if (shop == null || items == null || items.isHidden() || items.getDynamicChildren() == null)
		{
			visit = null;
			lastInventory = Collections.emptyMap();
			if (unsaved)
			{
				save();
			}
		}
		else
		{
			String here = shop.getName() + "|" + client.getWorld();
			boolean opened = !here.equals(visit);
			visit = here;
			Map<Integer, Integer> inventory = inventory();
			for (Widget w : items.getDynamicChildren())
			{
				if (w.getItemId() <= 0 || w.isHidden())
				{
					continue;
				}
				String name = itemManager.getItemComposition(w.getItemId()).getName().toLowerCase();
				Shop.Item entry = shop.getItems().get(name);
				if (entry != null && entry.getR() != null)
				{
					int id = itemManager.canonicalize(w.getItemId());
					boolean bought = inventory.getOrDefault(id, 0) > lastInventory.getOrDefault(id, 0);
					changed |= s.observe(shop.getName(), name, client.getWorld(), w.getItemQuantity(), entry.getS(), entry.getR(),
						opened, bought, now);
				}
			}
			lastInventory = inventory;
		}
		if (changed)
		{
			unsaved = true;
			// Stock counts change every sale; only what the plan depends on needs a recalculation
			Object shape = shape(s, now);
			if (!shape.equals(lastShape))
			{
				lastShape = shape;
				onChange.run();
			}
		}
		if (unsaved && ++ticksSinceSave >= SAVE_TICKS)
		{
			save();
		}
	}

	private static Object shape(ShopStock s, long now)
	{
		Set<String> keys = new HashSet<>();
		for (ShopStock.Seen seen : s.all())
		{
			keys.add(seen.getShop() + "|" + seen.getItem() + "|" + seen.getWorld());
		}
		Set<String> busy = new HashSet<>();
		for (ShopStock.Busy b : s.busy(now))
		{
			busy.add(b.getShop() + "|" + b.getItem());
		}
		return Arrays.asList(keys, busy);
	}
}
