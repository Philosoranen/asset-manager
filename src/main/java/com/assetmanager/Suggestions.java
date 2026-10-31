package com.assetmanager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/** Item ids Asset Manager currently has a suggestion for, in display order. Immutable. */
@Getter
public class Suggestions
{
	public enum Kind
	{
		COFFER,
		ALCH,
		SHOP,
		GE,
	}

	public static final Suggestions NONE = new Suggestions(Collections.emptyList(), Collections.emptyList(),
		Collections.emptyList(), Collections.emptyList());

	private final Set<Integer> coffer;
	private final Set<Integer> alch;
	private final Set<Integer> shop;
	private final Set<Integer> ge;

	public Suggestions(List<Integer> cofferIds, List<Integer> alchIds, List<Integer> shopIds)
	{
		this(cofferIds, alchIds, shopIds, Collections.emptyList());
	}

	/** An item only gets one suggestion: coffer first, then alch, then shop, then the GE. */
	public Suggestions(List<Integer> cofferIds, List<Integer> alchIds, List<Integer> shopIds, List<Integer> geIds)
	{
		coffer = Collections.unmodifiableSet(new LinkedHashSet<>(cofferIds));
		alch = without(alchIds, coffer);
		shop = without(shopIds, coffer, alch);
		ge = without(geIds, coffer, alch, shop);
	}

	@SafeVarargs
	private static Set<Integer> without(List<Integer> ids, Set<Integer>... earlier)
	{
		Set<Integer> s = new LinkedHashSet<>(ids);
		for (Set<Integer> e : earlier)
		{
			s.removeAll(e);
		}
		return Collections.unmodifiableSet(s);
	}

	/** Which suggestion an item has, or null if none. */
	public Kind getIcon(int canonicalId)
	{
		return coffer.contains(canonicalId) ? Kind.COFFER
			: alch.contains(canonicalId) ? Kind.ALCH
			: shop.contains(canonicalId) ? Kind.SHOP
			: ge.contains(canonicalId) ? Kind.GE
			: null;
	}

	public boolean contains(int canonicalId)
	{
		return getIcon(canonicalId) != null;
	}

	public boolean isEmpty()
	{
		return coffer.isEmpty() && alch.isEmpty() && shop.isEmpty() && ge.isEmpty();
	}
}
