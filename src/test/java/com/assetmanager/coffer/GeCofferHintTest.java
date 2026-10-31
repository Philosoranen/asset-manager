package com.assetmanager.coffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import com.assetmanager.value.ItemValues;
import org.junit.Test;

public class GeCofferHintTest
{
	// Guide price 100k: the coffer gives 105k each
	private static final ItemValues ITEM = new ItemValues(1, "Thing", 100_000, 100_000, 60_000, 100_000, true);

	@Test
	public void cofferBeatsAnOfferAtTheGuidePrice()
	{
		GeCofferHint h = GeCofferHint.of(ITEM, 100_000, 3, 1_000_000);
		assertEquals(3, h.getUnits());
		assertEquals(315_000, h.getCofferValue());
		assertEquals(294_000, h.getSaleValue()); // after 2% tax
		assertEquals(21_000, h.getExtra());
	}

	@Test
	public void onlyAsManyAsFillTheCoffer()
	{
		GeCofferHint h = GeCofferHint.of(ITEM, 100_000, 10, 200_000);
		assertEquals(2, h.getUnits());
		assertEquals(10, h.getQuantity());
	}

	@Test
	public void nothingWhenTheCofferIsFull()
	{
		assertNull(GeCofferHint.of(ITEM, 100_000, 3, 0));
	}

	@Test
	public void nothingWhenTheOfferPaysMore()
	{
		assertNull(GeCofferHint.of(ITEM, 108_000, 3, 1_000_000)); // 105,840 after tax
	}

	@Test
	public void nothingForItemsTheCofferRefuses()
	{
		ItemValues cheap = new ItemValues(2, "Cheap", 9_000, 9_000, 100, 9_000, true);
		assertNull(GeCofferHint.of(cheap, 5_000, 3, 1_000_000));
	}

	@Test
	public void readsThePriceEachOffTheOfferScreen()
	{
		// The price each and the total, in whatever order the screen lists them
		assertEquals(1_234, GeCofferOverlay.offerPrice(Arrays.asList(12_340L, 1_234L), 10));
		assertEquals(5_000, GeCofferOverlay.offerPrice(Arrays.asList(5_000L, 5_000L), 1));
		// No total that matches: can't tell which is which
		assertEquals(0, GeCofferOverlay.offerPrice(Arrays.asList(12_340L, 999L), 10));
		assertEquals(0, GeCofferOverlay.offerPrice(Arrays.asList(1_234L, 12_340L), 0));
	}
}
