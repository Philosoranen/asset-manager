package com.assetmanager.bank;

import com.assetmanager.dest.Destination;
import com.assetmanager.dest.Destinations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.util.Text;

/**
 * Temporary bank views of where Asset Manager suggests spare items go, opened from the side panel: one per
 * destination (Death's coffer, high alching, the GE, each shop, kept items, or everything). Items are laid out most
 * valuable first, and the Everything view leaves a blank row between destinations. Nothing is added to the bank's
 * tab list: each view is a virtual tag that's only on screen while it's open. Opening one with the bank closed shows
 * it the next time the bank opens.
 * <p>
 * Must be used on the client thread.
 */
@Singleton
public class BankViews
{
	private static final int ITEMS_PER_ROW = 8;
	private static final String TAG_PREFIX = "asset manager - ";
	/** The permanent tab older versions added to the tab list. */
	private static final String LEGACY_TAG = "asset manager";

	private final Client client;
	private final TagManager tagManager;
	private final BankTagsService bankTags;
	private final ConfigManager configManager;
	private final ItemManager itemManager;

	private volatile Destinations destinations = Destinations.NONE;
	/** Tags registered so far, by destination key. */
	private final Map<String, String> tags = new HashMap<>();
	/** Asked for while the bank was closed. */
	private String pending;
	private String open;
	private List<Integer> openLayout;

	@Inject
	BankViews(Client client, TagManager tagManager, BankTagsService bankTags, ConfigManager configManager, ItemManager itemManager)
	{
		this.client = client;
		this.tagManager = tagManager;
		this.bankTags = bankTags;
		this.configManager = configManager;
		this.itemManager = itemManager;
	}

	/** Identifies a destination across recalculations. */
	public static String key(Destination d)
	{
		return d.getKind() == Destination.Kind.SHOP ? "shop:" + d.getName() : d.getKind().name();
	}

	public void register()
	{
		removeLegacyTab();
	}

	public void unregister()
	{
		close();
		for (String tag : tags.values())
		{
			tagManager.unregisterTag(tag);
			configManager.unsetConfiguration(BankTagsPlugin.CONFIG_GROUP, layoutKey(tag));
		}
		tags.clear();
		pending = null;
	}

	/**
	 * Shows a destination's items in the bank now, or the next time it opens.
	 *
	 * @return whether the bank was open
	 */
	public boolean show(Destination d)
	{
		String key = key(d);
		tags.computeIfAbsent(key, k ->
		{
			String tag = Text.standardize(TAG_PREFIX + d.getName());
			tagManager.registerTag(tag, itemId -> contains(k, itemManager.canonicalize(itemId)));
			return tag;
		});
		if (!isBankOpen())
		{
			pending = key;
			return false;
		}
		open(key);
		return true;
	}

	public void close()
	{
		if (open != null && tags.get(open).equals(bankTags.getActiveTag()))
		{
			bankTags.closeBankTag();
		}
		open = null;
		pending = null;
	}

	/** Opens a view asked for while the bank was closed, and notices when the player leaves one. */
	public void onGameTick()
	{
		if (!isBankOpen())
		{
			return;
		}
		if (pending != null)
		{
			String key = pending;
			pending = null;
			open(key);
		}
		else if (open != null && !tags.get(open).equals(bankTags.getActiveTag()))
		{
			open = null;
		}
	}

	public void update(Destinations destinations)
	{
		this.destinations = destinations;
		// Refresh the open view when its contents change, e.g. after withdrawing everything in a section
		if (open != null && tags.get(open).equals(bankTags.getActiveTag()) && !layout(open).equals(openLayout))
		{
			open(open);
		}
	}

	/** Whether an Asset Manager view is on screen. */
	public boolean isShowing()
	{
		return open != null && isBankOpen() && tags.get(open).equals(bankTags.getActiveTag());
	}

	/** The kind of Asset Manager view on screen, or null if none is. */
	public Destination.Kind showingKind()
	{
		String key = open;
		if (key == null || !isShowing())
		{
			return null;
		}
		return key.startsWith("shop:") ? Destination.Kind.SHOP : Destination.Kind.valueOf(key);
	}

	private void open(String key)
	{
		String tag = tags.get(key);
		List<Integer> layout = layout(key);
		if (layout.isEmpty())
		{
			configManager.unsetConfiguration(BankTagsPlugin.CONFIG_GROUP, layoutKey(tag));
		}
		else
		{
			configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, layoutKey(tag),
				layout.stream().map(String::valueOf).collect(Collectors.joining(",")));
		}
		bankTags.openBankTag(tag, 0);
		open = key;
		openLayout = layout;
	}

	private Destination find(String key)
	{
		for (Destination d : destinations.getList())
		{
			if (key(d).equals(key))
			{
				return d;
			}
		}
		return null;
	}

	private boolean contains(String key, int itemId)
	{
		Destination d = find(key);
		return d != null && d.getItemIds().contains(itemId);
	}

	/** The Everything view is split into destinations; the rest are one list. */
	private List<List<Integer>> sections(String key)
	{
		Destination d = find(key);
		if (d == null)
		{
			return Collections.emptyList();
		}
		if (d.getKind() != Destination.Kind.ALL)
		{
			return Collections.singletonList(d.getItemIds());
		}
		List<List<Integer>> sections = new ArrayList<>();
		for (Destination part : destinations.getList())
		{
			if (part.getKind() != Destination.Kind.ALL && part.getKind() != Destination.Kind.KEPT)
			{
				sections.add(part.getItemIds());
			}
		}
		return sections;
	}

	private List<Integer> layout(String key)
	{
		return buildLayout(sections(key));
	}

	/** Each section's items in order, with the rest of its row and one more row left blank between sections. */
	static List<Integer> buildLayout(List<? extends List<Integer>> sections)
	{
		List<Integer> layout = new ArrayList<>();
		HashSet<Integer> seen = new HashSet<>();
		for (List<Integer> section : sections)
		{
			List<Integer> fresh = section.stream().filter(seen::add).collect(Collectors.toList());
			if (fresh.isEmpty())
			{
				continue;
			}
			if (!layout.isEmpty())
			{
				while (layout.size() % ITEMS_PER_ROW != 0)
				{
					layout.add(-1);
				}
				for (int i = 0; i < ITEMS_PER_ROW; i++)
				{
					layout.add(-1);
				}
			}
			layout.addAll(fresh);
		}
		return layout;
	}

	private boolean isBankOpen()
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return items != null && !items.isHidden();
	}

	private static String layoutKey(String tag)
	{
		return BankTagsPlugin.TAG_LAYOUT_PREFIX + Text.standardize(tag);
	}

	/**
	 * Older versions added a permanent "asset manager" tab. Bank Tags only holds tabs in memory while the bank is
	 * open, so the tab list is edited in config directly.
	 */
	private void removeLegacyTab()
	{
		String csv = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG);
		List<String> tabs = csv == null || csv.isEmpty() ? new ArrayList<>() : new ArrayList<>(Text.fromCSV(csv));
		if (tabs.remove(LEGACY_TAG))
		{
			configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG, Text.toCSV(tabs));
		}
		configManager.unsetConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_ICON_PREFIX + LEGACY_TAG);
		configManager.unsetConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_LAYOUT_PREFIX + LEGACY_TAG);
	}
}
