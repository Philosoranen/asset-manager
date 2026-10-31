package com.assetmanager.shop;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** Wording for shop restock times. */
public final class ShopTimes
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE");

	private ShopTimes()
	{
	}

	/** "6h 0m", "12m", or "under a minute". */
	public static String duration(long millis)
	{
		long minutes = (millis + 59_999) / 60_000;
		if (minutes <= 1)
		{
			return millis <= 60_000 ? "under a minute" : "1m";
		}
		return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + "m";
	}

	/** A clock time, with the day if it isn't today: "9:40 PM", "Thu 3:15 AM". */
	public static String clock(long epochMillis)
	{
		ZonedDateTime t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
		String time = t.format(TIME);
		return t.toLocalDate().equals(LocalDate.now()) ? time : t.format(DAY) + " " + time;
	}

	/** "rune plateskirt" as shown in game: "Rune plateskirt". */
	public static String itemName(String lowercased)
	{
		return lowercased.isEmpty() ? lowercased : Character.toUpperCase(lowercased.charAt(0)) + lowercased.substring(1);
	}
}
