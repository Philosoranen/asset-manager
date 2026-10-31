package com.assetmanager.keep;

import com.assetmanager.AssetManagerConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * Stops the player from alching, selling to a shop or selling on the GE the items they've said to keep.
 * The click is cancelled with a chat message; nothing is sent to the game.
 * <p>
 * On the GE the quantity is chosen after picking the item, so a sell offer's Confirm is blocked while its
 * quantity would dip into the kept ones; {@link GeKeepOverlay} shows why and how many to enter.
 */
@Singleton
public class KeepGuard
{
	private static final Pattern ALCHEMY = Pattern.compile("^(High|Low) Level Alchemy\\b");
	private static final Pattern SHOP_SELL = Pattern.compile("^Sell (\\d+)$");
	/** GE_NEWOFFER_TYPE for a sell offer. */
	private static final int GE_SELL = 1;

	/** A GE sell offer being set up for an item with a keep rule. */
	@Value
	public static class GeSale
	{
		String name;
		/** How many are being kept, or KeepRules.KEEP_ALL. */
		int keep;
		/** The most that can be sold while keeping them. */
		int canSell;
		/** The quantity entered in the offer. */
		int quantity;

		public boolean isTooMany()
		{
			return quantity > canSell;
		}

		public String keeping()
		{
			return keep == KeepRules.KEEP_ALL ? "all your " + name : QuantityFormatter.formatNumber(keep) + " x " + name;
		}
	}

	private final Client client;
	private final ItemManager itemManager;
	private final AssetManagerConfig config;

	@Setter
	private volatile KeepRules rules = new KeepRules();
	/** Where the player's items are; the inventory and equipment are read live. */
	@Setter
	private volatile Holdings holdings = Holdings.NONE;

	@Inject
	KeepGuard(Client client, ItemManager itemManager, AssetManagerConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.protectKept())
		{
			return;
		}
		String option = Text.removeTags(event.getMenuOption());
		String target = Text.removeTags(event.getMenuTarget());
		int component = event.getParam1();
		String verb;
		int removing;
		Matcher sell = SHOP_SELL.matcher(option);
		if ("Cast".equals(option) && ALCHEMY.matcher(target).find() && component == InterfaceID.Inventory.ITEMS)
		{
			verb = "alch";
			removing = 1;
		}
		else if (sell.matches() && component == InterfaceID.Shopside.ITEMS)
		{
			verb = "sell";
			removing = Integer.parseInt(sell.group(1));
		}
		else if ("Offer".equals(option) && component == InterfaceID.GeOffersSide.ITEMS)
		{
			verb = "offer";
			removing = 1;
		}
		else if ("Confirm".equals(option) && component >>> 16 == InterfaceID.GeOffers.SETUP >>> 16)
		{
			GeSale sale = geSale();
			if (sale != null && sale.isTooMany())
			{
				event.consume();
				chat("Blocked: you're keeping " + sale.keeping() + "."
					+ (sale.getCanSell() > 0 ? " Enter " + QuantityFormatter.formatNumber(sale.getCanSell()) + " or fewer." : "")
					+ " Change it with Keep in the bank.");
			}
			return;
		}
		else
		{
			return;
		}

		int itemId = itemId(event);
		if (itemId <= 0)
		{
			return;
		}
		int canonical = itemManager.canonicalize(itemId);
		KeepRules r = rules;
		int keep = r.get(canonical);
		if (keep == 0)
		{
			return;
		}
		removing = Math.min(removing, count(InventoryID.INV, canonical));
		int owned = owned(r, canonical);
		int canPart = r.canPart(canonical, owned);
		String name = name(r, canonical);
		String keeping = keep == KeepRules.KEEP_ALL ? "all your " + name : QuantityFormatter.formatNumber(keep) + " x " + name;

		if (removing > canPart)
		{
			event.consume();
			String allowed = canPart == 0 ? "" : " You can " + verb + " " + QuantityFormatter.formatNumber(canPart) + " more.";
			chat("Blocked: you're keeping " + keeping + "." + allowed + " Change it with Keep in the bank.");
		}
	}

	/** The GE sell offer being set up, if it's for an item with a keep rule; null otherwise. */
	public GeSale geSale()
	{
		if (!config.protectKept())
		{
			return null;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden() || client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != GE_SELL)
		{
			return null;
		}
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (itemId <= 0)
		{
			return null;
		}
		int canonical = itemManager.canonicalize(itemId);
		KeepRules r = rules;
		int keep = r.get(canonical);
		if (keep == 0)
		{
			return null;
		}
		int owned = owned(r, canonical);
		return new GeSale(name(r, canonical), keep, r.canPart(canonical, owned),
			client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY));
	}

	private int itemId(MenuOptionClicked event)
	{
		if (event.getItemId() > 0)
		{
			return event.getItemId();
		}
		Widget w = event.getWidget();
		return w == null ? -1 : w.getItemId();
	}

	/** The item's name, or the name all its variants share. */
	private String name(KeepRules r, int canonical)
	{
		String name = itemManager.getItemComposition(canonical).getName();
		return r.members(canonical).size() > 1 ? Variants.base(name) : name;
	}

	/** How many of an item and its variants the player has: worn, carried and stored away. */
	private int owned(KeepRules r, int canonical)
	{
		int n = 0;
		for (int id : r.members(canonical))
		{
			n += count(InventoryID.INV, id) + count(InventoryID.WORN, id) + inBank(id);
		}
		return n;
	}

	/** How many are stored away: in the bank and the seed vault. */
	private int inBank(int canonical)
	{
		Holdings h = holdings;
		return h.count(Holdings.Place.BANK, canonical) + h.count(Holdings.Place.SEED_VAULT, canonical);
	}

	private int count(int containerId, int canonical)
	{
		ItemContainer c = client.getItemContainer(containerId);
		if (c == null)
		{
			return 0;
		}
		int n = 0;
		for (Item i : c.getItems())
		{
			if (i.getId() > 0 && itemManager.canonicalize(i.getId()) == canonical)
			{
				n += i.getQuantity();
			}
		}
		return n;
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Asset Manager: ").append(ChatColorType.NORMAL).append(message).build(), null);
	}
}
