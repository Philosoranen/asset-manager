package com.assetmanager.bank;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class BankViewsTest
{
	@Test
	public void sectionsSeparatedByBlankRows()
	{
		assertEquals(Arrays.asList(
			10, 11, 12, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			20, 21, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			30), BankViews.buildLayout(Arrays.asList(Arrays.asList(10, 11, 12), Arrays.asList(20, 21), Arrays.asList(30))));
	}

	@Test
	public void noPaddingForEmptySections()
	{
		assertEquals(Arrays.asList(20, 21), BankViews.buildLayout(Arrays.asList(Collections.emptyList(), Arrays.asList(20, 21), Collections.emptyList())));
		assertEquals(Collections.emptyList(), BankViews.buildLayout(Collections.singletonList(Collections.emptyList())));
	}

	@Test
	public void anItemOnlyAppearsOnce()
	{
		assertEquals(Arrays.asList(1, 2), BankViews.buildLayout(Collections.singletonList(Arrays.asList(1, 2, 1))));
	}
}
