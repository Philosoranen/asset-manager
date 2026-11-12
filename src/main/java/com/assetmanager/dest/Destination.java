package com.assetmanager.dest;

import com.assetmanager.keep.Holdings;
import com.assetmanager.shop.Shop;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Somewhere spare items can go, and which ones: shown as one row in the side panel and one view in the bank. */
@Value
public class Destination
{
	public enum Kind
	{
		ALL,
		COFFER,
		ALCH,
		GE,
		SHOP,
		KEPT,
	}

	Kind kind;
	String name;
	/** Item ids, most valuable first (for GE, possibly quickest to sell first: see {@link GeOrder}). */
	List<Integer> itemIds;
	/** Coins from sending them all here (for KEPT, what they're worth). */
	long value;
	/** How much more that is than the next best way, or 0. */
	long gain;
	/** The shop, for SHOP: for a group of shops paying the same, the closest one when it was planned. */
	Shop shop;
	/** For SHOP: other shops that pay about the same, this being the closest. */
	int equalShops;
	/** For SHOP: worlds to sell on. */
	int worlds;
	/** For SHOP: whether any item needs Karamja gloves / an activated Ring of Charos for this price. */
	boolean needsGloves;
	boolean needsCharos;
	/** Items that are only spare outside the bank, so the bank view can't show them, counted by where they are. */
	Map<Holdings.Place, Integer> elsewhere;

	public int getCount()
	{
		return itemIds.size();
	}
}
