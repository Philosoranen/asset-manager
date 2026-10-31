package com.assetmanager.ui;

import static org.junit.Assert.assertEquals;
import com.assetmanager.Suggestions;
import com.assetmanager.dest.Destination;
import java.util.Collections;
import org.junit.Test;

public class SuggestionOverlayTest
{
	@Test
	public void viewsOnlyShowTheirOwnIcon()
	{
		int vambs = 1065;
		Suggestions s = new Suggestions(Collections.emptyList(), Collections.singletonList(vambs), Collections.emptyList(),
			Collections.emptyList());
		assertEquals(Suggestions.Kind.ALCH, SuggestionOverlay.iconKind(s, vambs, null));
		assertEquals(Suggestions.Kind.ALCH, SuggestionOverlay.iconKind(s, vambs, Destination.Kind.ALL));
		assertEquals(Suggestions.Kind.ALCH, SuggestionOverlay.iconKind(s, vambs, Destination.Kind.ALCH));
		assertEquals(null, SuggestionOverlay.iconKind(s, vambs, Destination.Kind.SHOP));
	}
}
