package com.assetmanager.flip;

import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopOffer;
import java.util.List;
import lombok.Value;
import lombok.With;

/**
 * Something to buy in one place and cash out in another: from a shop or the GE, to the GE, a shop, Death's coffer or
 * alching.
 */
@Value
public class Flip
{
	public enum Source
	{
		SHOP("Shop"),
		GE("GE");

		private final String label;

		Source(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/** Where a flip cashes out; the player picks one on the Flipping tab. */
	public enum Dest
	{
		ALCH("Alch", "Alching"),
		GE("GE", "The GE"),
		SHOP("Shop", "Shops"),
		COFFER("Coffer", "Death's coffer");

		private final String label;
		private final String displayName;

		Dest(String label, String displayName)
		{
			this.label = label;
			this.displayName = displayName;
		}

		public String getLabel()
		{
			return label;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	Source source;
	Dest dest;
	/** The item's name, lowercase. */
	String item;
	int itemId;
	/** The shop it's bought from, when bought from a shop. */
	Shop buyShop;
	/** The shop it's sold to, when sold to a shop. */
	ShopOffer sellOffer;
	int quantity;
	/** What buying them all costs. */
	long cost;
	/** What they all fetch: sold, put in the coffer or alched. */
	long revenue;
	/** What the first one costs. */
	long firstCost;
	/** What the first one fetches. */
	long firstValue;
	/** What stopped the quantity going higher. */
	@With
	ShopFlip.Limit limit;
	/** Requirements the plugin couldn't check, which the player should make sure of. */
	List<String> unchecked;
	/** About how many trade on the GE in the time the player will wait; Long.MAX_VALUE if unknown or not used. */
	long geTrades;
	/** A shop's price rises with each one bought. */
	boolean priceRises;
	/** Worlds to buy them from a shop on, a few on each before hopping; 1 if not hopping or not bought from a shop. */
	int buyWorlds;

	/** About how many to buy from the shop on each world. */
	public int getPerWorld()
	{
		return (quantity + buyWorlds - 1) / Math.max(1, buyWorlds);
	}

	public long getProfit()
	{
		return revenue - cost;
	}

	/** What the first one makes. */
	public long getProfitEach()
	{
		return firstValue - firstCost;
	}

	/** Profit on the first one, as a percentage of what it costs. */
	public double getPercent()
	{
		return firstCost <= 0 ? 0 : getProfitEach() * 100.0 / firstCost;
	}

	/** Profit on the whole go, as a percentage of what it all costs. */
	public double getReturnPercent()
	{
		return cost <= 0 ? 0 : getProfit() * 100.0 / cost;
	}

	/** The shop to go to, where it's bought or sold; null if none. */
	public Shop getShop()
	{
		return buyShop != null ? buyShop : sellOffer != null ? sellOffer.getShop() : null;
	}
}
