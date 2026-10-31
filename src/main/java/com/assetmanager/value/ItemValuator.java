package com.assetmanager.value;

import com.assetmanager.AssetManagerConfig;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

/**
 * Looks up item values from RuneLite's price data, or live OSRS Wiki prices when enabled.
 * Must be called on the client thread.
 */
@Singleton
public class ItemValuator
{
	private final ItemManager itemManager;
	private final LivePrices livePrices;
	private final AssetManagerConfig config;
	private final Map<Integer, ItemValues> cache = new HashMap<>();

	@Inject
	ItemValuator(ItemManager itemManager, LivePrices livePrices, AssetManagerConfig config)
	{
		this.itemManager = itemManager;
		this.livePrices = livePrices;
		this.config = config;
	}

	/** Prices move slowly; clear this periodically so long sessions pick up new data. */
	public void clearCache()
	{
		cache.clear();
	}

	public ItemValues get(int itemId)
	{
		int canonical = itemManager.canonicalize(itemId);
		return cache.computeIfAbsent(canonical, this::lookup);
	}

	/** What a nature rune costs to buy: the live instant-buy price if known. */
	public int getNatureRuneCost()
	{
		int live = livePrices.instantBuy(ItemID.NATURERUNE);
		return live > 0 ? live : get(ItemID.NATURERUNE).getWikiPrice();
	}

	/** Where GE sale prices come from, for tooltips. */
	public String getGePriceDescription()
	{
		return livePrices.isAvailable() ? config.gePrice().getDescription() : "RuneLite's 30-min average price";
	}

	private ItemValues lookup(int itemId)
	{
		ItemComposition comp = itemManager.getItemComposition(itemId);
		int guide = toInt(itemManager.getItemPriceWithSource(itemId, false));
		// Live price if enabled, otherwise RuneLite's average of recent trades
		int wiki = livePrices.sellPrice(itemId, config.gePrice());
		if (wiki <= 0)
		{
			wiki = toInt(itemManager.getItemPriceWithSource(itemId, true));
		}
		if (wiki <= 0)
		{
			wiki = guide;
		}
		return new ItemValues(itemId, comp.getName(), guide, wiki, comp.getHaPrice(), comp.getPrice(), comp.isTradeable());
	}

	/** Newer RuneLite versions return prices as a long; no single item is worth more than max cash. */
	private static int toInt(long price)
	{
		return (int) Math.min(price, Integer.MAX_VALUE);
	}
}
