package com.assetmanager.flip;

/**
 * Rough estimates of how long a flip takes, so flips can be compared by what they make an hour. A go's active time is
 * the player's own: getting to the shop and back, hopping, clicking, talking and alching. The GE fills offers while
 * they do the rest, so a go takes whichever is longer: that, or the wait for the GE.
 */
public final class FlipTime
{
	/** Hopping to a fresh world and opening the shop again. */
	static final double HOP_SECONDS = 15;
	/** One conversation with a trader who sells by talking: about 1,000 an hour. */
	static final double TALK_SECONDS = 3.6;
	/** Getting to a shop or Death's coffer and back to a bank, once a go. */
	static final double TRIP_SECONDS = 60;
	/** One Buy or Sell click in a shop's window. */
	static final double CLICK_SECONDS = 1.2;
	/** Most a shop's window buys or sells in one click (Buy 50). */
	static final int PER_CLICK = 50;
	/** One high alch: 5 ticks, 1,200 an hour. */
	static final double ALCH_SECONDS = 3;
	/** The GE's buy limit resets every 4 hours. */
	static final double BUY_LIMIT_HOURS = 4;

	private FlipTime()
	{
	}

	/** Clicks to buy or sell {@code quantity} in a shop's window over {@code worlds} worlds: at least one on each. */
	static long clicks(long quantity, int worlds)
	{
		return Math.max(worlds, (quantity + PER_CLICK - 1) / PER_CLICK);
	}

	/**
	 * Seconds waiting for GE offers to fill: the time the player will wait, or the buy limit's 4 hours when that's what
	 * stopped the buying, since another round can't start sooner.
	 */
	static double geWaitSeconds(double geHours, ShopFlip.Limit limit)
	{
		return (limit == ShopFlip.Limit.BUY_LIMIT ? Math.max(geHours, BUY_LIMIT_HOURS) : geHours) * 3600;
	}

	/** "1h 5m", "12m", "45s". */
	public static String describe(double seconds)
	{
		long s = Math.round(seconds);
		if (s < 60)
		{
			return Math.max(1, s) + "s";
		}
		long m = (s + 30) / 60;
		return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + "m";
	}
}
