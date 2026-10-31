package com.assetmanager.shop;

import com.assetmanager.AssetManagerConfig;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Stops the player selling for less than they could because they forgot to put something on: selling to a shop
 * that pays more with Karamja gloves they own but aren't wearing, or talking to Baraek or Fritz with furs or molten
 * glass while their activated Ring of Charos is in the bank. The first click is cancelled with a chat message;
 * clicking again soon after goes ahead. Nothing is sent to the game.
 */
@Singleton
public class PerkGuard
{
	private static final Pattern SHOP_SELL = Pattern.compile("^Sell \\d+$");
	/** A second click within this long goes ahead. */
	private static final long OVERRIDE_MILLIS = 10_000;
	/** NPCs who pay more with an activated Ring of Charos, and the shop data they're in. */
	private static final Map<String, String> CHAROS_TRADERS = Map.of("Baraek", "Baraek", "Fritz", "Fritz the Glassblower");

	private final Client client;
	private final ItemManager itemManager;
	private final AssetManagerConfig config;

	@Setter
	private volatile ShopCatalog shops;
	/** The open shop, from the shop window's title. */
	@Setter
	private volatile Supplier<Shop> openShop = () -> null;
	/** Karamja gloves tier the player has earned (0 for none). */
	@Setter
	private volatile int glovesTier;
	/** Whether the player owns an activated Ring of Charos. */
	@Setter
	private volatile boolean charos;

	private String lastBlocked;
	private long lastBlockedAt;

	@Inject
	PerkGuard(Client client, ItemManager itemManager, AssetManagerConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.wearShopPerks())
		{
			return;
		}
		String option = Text.removeTags(event.getMenuOption());
		String target = Text.removeTags(event.getMenuTarget());
		String warning = null;
		if (SHOP_SELL.matcher(option).matches() && event.getParam1() == InterfaceID.Shopside.ITEMS)
		{
			warning = glovesWarning(event.getItemId());
		}
		else if ("Talk-to".equals(option) && CHAROS_TRADERS.containsKey(target))
		{
			warning = charosWarning(CHAROS_TRADERS.get(target));
		}
		if (warning == null)
		{
			return;
		}
		// Clicking the same thing again soon after means they want to go ahead anyway
		String what = option + "|" + target;
		long now = System.currentTimeMillis();
		if (what.equals(lastBlocked) && now - lastBlockedAt < OVERRIDE_MILLIS)
		{
			lastBlocked = null;
			return;
		}
		lastBlocked = what;
		lastBlockedAt = now;
		event.consume();
		chat(warning + " Click again to go ahead anyway.");
	}

	private String glovesWarning(int itemId)
	{
		Shop shop = openShop.get();
		if (shop == null || shop.getGlovesTier() == null || itemId <= 0)
		{
			return null;
		}
		int needed = shop.getGlovesTier();
		if (glovesTier < needed || wornGlovesTier() >= needed)
		{
			return null;
		}
		String name = itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName();
		Shop.Item item = shop.getItems().get(name.toLowerCase());
		Integer with = item != null ? item.getG() : shop.isGeneral() ? shop.getGlovesBuyMultiplier() : null;
		if (with == null)
		{
			return null;
		}
		int without = item != null && item.getM() != null ? item.getM() : shop.getBuyMultiplier();
		return "Put on your Karamja gloves " + (needed >= 3 ? "3 or 4" : "") + " first: " + shop.getName() + " pays "
			+ with / 10 + "% for " + name + " with them, " + without / 10 + "% without.";
	}

	private String charosWarning(String shopName)
	{
		if (!charos || isWorn(ItemID.RING_OF_CHAROS_UNLOCKED) || shops == null)
		{
			return null;
		}
		Shop shop = shops.findByName(shopName);
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (shop == null || inv == null)
		{
			return null;
		}
		for (Item i : inv.getItems())
		{
			if (i.getId() <= 0)
			{
				continue;
			}
			String name = itemManager.getItemComposition(itemManager.canonicalize(i.getId())).getName();
			Shop.Item item = shop.getItems().get(name.toLowerCase());
			if (item != null && (item.getCp() != null || item.getCm() != null))
			{
				return "Put on your Ring of Charos (a) first: " + shopName + " pays more for " + name + " with it.";
			}
		}
		return null;
	}

	/** 1 for Karamja gloves 1 or 2, 3 for 3 or 4, 0 for none. */
	private int wornGlovesTier()
	{
		if (isWorn(ItemID.ATJUN_GLOVES_HARD) || isWorn(ItemID.ATJUN_GLOVES_ELITE))
		{
			return 3;
		}
		return isWorn(ItemID.ATJUN_GLOVES_EASY) || isWorn(ItemID.ATJUN_GLOVES_MED) ? 1 : 0;
	}

	private boolean isWorn(int itemId)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		return worn != null && worn.contains(itemId);
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Asset Manager: ").append(ChatColorType.NORMAL).append(message).build(), null);
	}
}
