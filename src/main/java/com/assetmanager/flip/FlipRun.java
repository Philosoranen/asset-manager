package com.assetmanager.flip;

import com.assetmanager.shop.Shop;
import java.util.List;
import lombok.Value;
import lombok.With;

/**
 * One go at flipping: one or more items bought in one place and cashed out in another. Items bought from, or sold to,
 * the same shop share a go, since every hop and trip serves them all; that's what makes some shops worth visiting.
 */
@Value
public class FlipRun
{
	Flip.Source source;
	Flip.Dest dest;
	/** The shop items are bought from or sold to; null when there's none (GE to alching or the coffer). */
	Shop shop;
	/** What's flipped, most profitable first. */
	@With
	List<Flip> flips;
	/** Worlds to hop over at the shop; 1 if not hopping or there's no shop. */
	int worlds;
	/** About how long the go takes, in seconds. */
	double seconds;
	/** Other shops where the go would be just the same; this one is the closest of them. */
	@With
	int equalShops;

	public long getCost()
	{
		return flips.stream().mapToLong(Flip::getCost).sum();
	}

	public long getRevenue()
	{
		return flips.stream().mapToLong(Flip::getRevenue).sum();
	}

	public long getProfit()
	{
		return getRevenue() - getCost();
	}

	/** Profit as a percentage of what it all costs. */
	public double getReturnPercent()
	{
		long cost = getCost();
		return cost <= 0 ? 0 : getProfit() * 100.0 / cost;
	}

	/** About what it makes an hour, done over and over. */
	public double getGpPerHour()
	{
		return getProfit() * 3600.0 / Math.max(1, seconds);
	}

	public int getQuantity()
	{
		return flips.stream().mapToInt(Flip::getQuantity).sum();
	}

	/** The row's name: the shop, or the item when there's no shop. */
	public String getName()
	{
		return shop != null ? shop.getName() : flips.get(0).getItem();
	}

	/** A run of one flip, with no shop to hop at. */
	static FlipRun single(Flip f, double seconds)
	{
		return new FlipRun(f.getSource(), f.getDest(), f.getShop(), List.of(f), Math.max(1, f.getBuyWorlds()), seconds, 0);
	}
}
