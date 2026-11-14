package com.assetmanager;

import com.assetmanager.dest.GeOrder;
import com.assetmanager.flip.Flip;
import com.assetmanager.flip.FlipSort;
import com.assetmanager.value.GePrice;
import com.assetmanager.value.Patience;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(AssetManagerConfig.GROUP)
public interface AssetManagerConfig extends Config
{
	String GROUP = "assetmanager";

	@ConfigItem(
		keyName = "accountMode",
		name = "Account type",
		description = "Ironmen can't use the GE and get 50% off death fees. Ultimate ironmen can't use the coffer.",
		position = 0
	)
	default AccountMode accountMode()
	{
		return AccountMode.AUTO;
	}

	@ConfigSection(
		name = "Death's coffer",
		description = "Death cost, coffer target and which items to sacrifice",
		position = 1
	)
	String cofferSection = "coffer";

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "targetDeaths",
		name = "Target deaths covered",
		description = "The top-up plan aims to cover this many deaths in your current gear",
		section = cofferSection,
		position = 0
	)
	default int targetDeaths()
	{
		return 5;
	}

	@Range(min = 0, max = 100_000_000)
	@ConfigItem(
		keyName = "minCofferTarget",
		name = "Minimum coffer target (gp)",
		description = "The top-up plan always aims for at least this much. 500k is the most a gravestone reclaim can cost."
			+ " The target is otherwise your target deaths times the cost of your most expensive setup in the last 14 days.",
		section = cofferSection,
		position = 1
	)
	default int minCofferTarget()
	{
		return 500_000;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "warnDeaths",
		name = "Warn below deaths",
		description = "The infobox turns red when the coffer covers fewer deaths than this",
		section = cofferSection,
		position = 2
	)
	default int warnDeaths()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "showInfoBox",
		name = "Show infobox",
		description = "Show an infobox with how many deaths your coffer covers",
		section = cofferSection,
		position = 3
	)
	default boolean showInfoBox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "cofferSuggestions",
		name = "Coffer suggestions",
		description = "Top-up plan: only the items needed to reach your target deaths covered."
			+ " All: every item worth more in the coffer than sold or alched.",
		section = cofferSection,
		position = 4
	)
	default CofferSuggestionMode cofferSuggestions()
	{
		return CofferSuggestionMode.PLAN;
	}

	@Range(min = -100, max = 1000)
	@ConfigItem(
		keyName = "minGainPercent",
		name = "Min. gain over alternative (%)",
		description = "Only suggest sacrificing items whose coffer value beats selling or alching by at least this much",
		section = cofferSection,
		position = 5
	)
	default int minGainPercent()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "geCofferHint",
		name = "Compare GE offers",
		description = "While your coffer is short, say so on the GE's offer screen when sacrificing the item you're"
			+ " selling would add more to the coffer than the offer gets",
		section = cofferSection,
		position = 6
	)
	default boolean geCofferHint()
	{
		return true;
	}

	@ConfigSection(
		name = "Where to sell spare items",
		description = "Each spare stack goes to the first of these that's worth it: a shop, then high alching, then the GE",
		position = 2
	)
	String sellingSection = "selling";

	@Range(min = 1, max = 1_000_000)
	@ConfigItem(
		keyName = "minShopGainPerItem",
		name = "Shop: min. gain per item (gp)",
		description = "On each world, sell to a shop while each sale beats alching and the GE by at least this much, then"
			+ " hop. Shops pay less for each one they hold, so this sets how many sell per world."
			+ " Often a shop only wins by the cost of a nature rune.",
		section = sellingSection,
		position = 0
	)
	default int minShopGain()
	{
		return 1;
	}

	@Range(min = 0, max = 100_000_000)
	@ConfigItem(
		keyName = "minGainPerWorld",
		name = "Shop: min. gain per world (gp)",
		description = "Only suggest a shop when what you'd sell there beats alching and the GE by at least this much for"
			+ " each world you'd sell on, counting the first. Raise it to hop less and alch or use the GE more."
			+ " A stack worth hopping for stays worth it as you sell it.",
		section = sellingSection,
		position = 1
	)
	default int minGainPerWorld()
	{
		return 1_000;
	}

	@Range(min = 0, max = 10_000)
	@ConfigItem(
		keyName = "shopMaxWorlds",
		name = "Shop: max worlds to hop",
		description = "The most worlds you'd hop to sell one item, or to buy one for a flip, however much it gains."
			+ " A stack needing more is left for another shop, alching or the GE."
			+ " 0 = no limit: hop as much as it takes, over as many sessions as you like.",
		section = sellingSection,
		position = 2
	)
	default int shopMaxWorlds()
	{
		return 10;
	}

	@Range(min = 1, max = 1_000_000)
	@ConfigItem(
		keyName = "minAlchGainOverGe",
		name = "Alch: min. gain over GE per item (gp)",
		description = "For items no shop is worth it for, alch rather than sell on the GE when alching makes at least this"
			+ " much more per item, after the nature rune and GE tax. Small stacks that alch for more are alched anyway."
			+ " Not used for ironmen, who alch by default.",
		section = sellingSection,
		position = 3
	)
	default int minAlchGain()
	{
		return 25;
	}

	@Range(min = 1, max = Integer.MAX_VALUE)
	@ConfigItem(
		keyName = "minGeSale",
		name = "GE: min. stack value (gp)",
		description = "Only suggest selling a spare stack on the GE when it's worth at least this much there, after tax."
			+ " Smaller stacks are alched if that makes more, otherwise left alone.",
		section = sellingSection,
		position = 4
	)
	default int minGeSale()
	{
		return 10_000;
	}

	@ConfigSection(
		name = "Shops",
		description = "Which shops to use, gloves and rings, and what to show in shops",
		position = 3
	)
	String shopSection = "shops";

	@ConfigItem(
		keyName = "avoidWilderness",
		name = "Avoid Wilderness shops",
		description = "Never suggest selling to shops in the Wilderness, like Bandit Duty Free."
			+ " Ferox Enclave is a safe zone and still counts. Also on the side panel, under Shops.",
		section = shopSection,
		position = 0
	)
	default boolean avoidWilderness()
	{
		return false;
	}

	@ConfigItem(
		keyName = "karamjaGloves",
		name = "Karamja gloves",
		description = "Karamja gloves improve prices at some Karamja shops (wear them when selling)."
			+ " Detected from your Karamja diary rewards unless set here.",
		section = shopSection,
		position = 1
	)
	default KaramjaGloves karamjaGloves()
	{
		return KaramjaGloves.AUTO;
	}

	@ConfigItem(
		keyName = "ringOfCharos",
		name = "Ring of Charos (a)",
		description = "An activated Ring of Charos gets better prices from Baraek (furs) and Fritz the Glassblower (molten glass)."
			+ " Detected from your bank, inventory and equipment unless set here.",
		section = shopSection,
		position = 2
	)
	default Ownership ringOfCharos()
	{
		return Ownership.AUTO;
	}

	@ConfigItem(
		keyName = "wearShopPerks",
		name = "Check gloves and ring",
		description = "Stop a shop sale that would pay more with Karamja gloves you have but aren't wearing, and talking to"
			+ " Baraek or Fritz with furs or molten glass while your Ring of Charos (a) isn't on. Click again to go ahead.",
		section = shopSection,
		position = 3
	)
	default boolean wearShopPerks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopRates",
		name = "Show shop rates",
		description = "Under an open shop's window, show what it sells and buys for and its change per item, as a % of item value",
		section = shopSection,
		position = 4
	)
	default boolean shopRates()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopSellCounts",
		name = "How many to sell",
		description = "In a shop, label each item in your inventory with how many to sell on this world before each"
			+ " sale stops beating the GE or alching, counting what the shop already holds. \"hop\" means a fresh world"
			+ " would buy some.",
		section = shopSection,
		position = 5
	)
	default boolean shopSellCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopMapMarkers",
		name = "Shops on world map",
		description = "Mark the shops Asset Manager suggests on the world map. Hover one for what to sell there.",
		section = shopSection,
		position = 6
	)
	default boolean shopMapMarkers()
	{
		return true;
	}

	@ConfigSection(
		name = "Bank & inventory",
		description = "Icons, the price tooltip and keeping items",
		position = 4
	)
	String bankSection = "bank";

	@ConfigItem(
		keyName = "priceTooltip",
		name = "Price comparison tooltip",
		description = "When hovering an item in your bank or inventory, compare what it's worth on the GE, high alched,"
			+ " in Death's coffer and at the best shop",
		section = bankSection,
		position = 0
	)
	default boolean priceTooltip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "cofferIcons",
		name = "Coffer icons",
		description = "Mark items that should go to Death's coffer",
		section = bankSection,
		position = 1
	)
	default boolean cofferIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alchIcons",
		name = "High alch icons",
		description = "Mark items worth more high-alched than sold on the GE or to a shop (not shown for ironmen, who alch by default)",
		section = bankSection,
		position = 2
	)
	default boolean alchIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopIcons",
		name = "Shop icons",
		description = "Mark items worth more sold to a shop than sold on the GE or alched",
		section = bankSection,
		position = 3
	)
	default boolean shopIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "geIcons",
		name = "GE icons",
		description = "Mark spare items worth selling on the GE with a Grand Exchange icon",
		section = bankSection,
		position = 4
	)
	default boolean geIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepMenu",
		name = "Keep option in bank",
		description = "Add a Keep option when right-clicking items in your bank (Keep 1, 5, 10, X or all),"
			+ " to set how many Asset Manager should leave alone",
		section = bankSection,
		position = 5
	)
	default boolean keepMenu()
	{
		return true;
	}

	@ConfigItem(
		keyName = "protectKept",
		name = "Protect kept items",
		description = "Stop high alching, selling to a shop or offering on the GE the items you're keeping,"
			+ " when it would leave you fewer than your keep amount (counting your bank, inventory and equipment)",
		section = bankSection,
		position = 6
	)
	default boolean protectKept()
	{
		return true;
	}

	@ConfigSection(
		name = "Flipping",
		description = "Buying in one place to cash out in another",
		position = 5
	)
	String flipSection = "flip";

	@ConfigItem(
		keyName = "bestBuys",
		name = "Show flips",
		description = "List the most profitable things to buy and cash out somewhere else, on the side panel's Flipping tab:"
			+ " from shops whose requirements you meet to Death's coffer or alching (and, for accounts that can use the GE,"
			+ " shop to GE, and with Live GE prices on, GE to shop, coffer or alching)",
		section = flipSection,
		position = 0
	)
	default boolean bestBuys()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopFlips",
		name = "Show shop flips",
		description = "Mark items in shops that can be resold on the GE for a profit, with how many to buy; hover one for more",
		section = flipSection,
		position = 1
	)
	default boolean shopFlips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopCofferBuys",
		name = "Show shop coffer buys",
		description = "Mark items in shops that add more to Death's coffer than they cost, with how many to buy: enough to"
			+ " reach your coffer target with the Top-up plan, or as many as are worth it with All worth sacrificing."
			+ " Where the GE would make more, the GE icon shows instead; hover either to see both.",
		section = flipSection,
		position = 2
	)
	default boolean shopCofferBuys()
	{
		return true;
	}

	@Range(min = 1, max = 100_000_000)
	@ConfigItem(
		keyName = "minFlipProfit",
		name = "Min. flip profit (gp)",
		description = "Only mark a shop item, or list a flip, when buying the suggested amount makes at least this much",
		section = flipSection,
		position = 3
	)
	default int minFlipProfit()
	{
		return 500;
	}

	@Range(min = 1, max = 28)
	@ConfigItem(
		keyName = "geFlipItems",
		name = "Items per GE flip",
		description = "The most different items to flip in one go through the GE: buying on the GE to sell to a shop, or"
			+ " buying from a shop to sell on the GE. The GE has 8 offer slots; noted items take an inventory slot each.",
		section = flipSection,
		position = 4
	)
	default int geFlipItems()
	{
		return 8;
	}

	@ConfigSection(
		name = "GE prices",
		description = "Where GE prices come from and which price to use",
		position = 6
	)
	String priceSection = "prices";

	@ConfigItem(
		keyName = "livePrices",
		name = "Live GE prices (OSRS Wiki)",
		description = "Use real-time buy and sell prices and trade volumes from the OSRS Wiki, refreshed every 5 minutes,"
			+ " instead of RuneLite's 30-minute average. Needed for the GE price setting below.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = priceSection,
		position = 0
	)
	default boolean livePrices()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gePrice",
		name = "GE sell price",
		description = "With live GE prices on, what to value items at when selling on the GE."
			+ " List at market price: what buyers have been paying, which you'll usually get by listing and waiting a little."
			+ " Sell instantly: what sellers have been accepting.",
		section = priceSection,
		position = 1
	)
	default GePrice gePrice()
	{
		return GePrice.MARKET;
	}

	@ConfigItem(
		keyName = "flipPatience",
		name = "GE flip patience",
		description = "How long you'll wait for GE offers when flipping; set on the side panel's Flipping tab",
		hidden = true
	)
	default Patience flipPatience()
	{
		return Patience.HOUR;
	}

	@ConfigItem(
		keyName = "flipDest",
		name = "Cash out flips at",
		description = "Where flips cash out: alching, the GE, shops or Death's coffer; set on the side panel's Flipping tab",
		hidden = true
	)
	default Flip.Dest flipDest()
	{
		return Flip.Dest.GE;
	}

	@ConfigItem(
		keyName = "flipSort",
		name = "Sort flips by",
		description = "Order flips by GP per hour (counting hops, trips, conversations, alchs and waiting for the GE), total"
			+ " profit, or return on what they cost (best for Death's coffer, where the coins spent are gone); set on the side"
			+ " panel's Flipping tab",
		hidden = true
	)
	default FlipSort flipSort()
	{
		return FlipSort.GP_PER_HOUR;
	}

	@ConfigItem(
		keyName = "shopEvenHops",
		name = "Even hops selling to shops",
		description = "When a flip sells several items to a shop, hop no more worlds than its most profitable item takes,"
			+ " so you're never hopping just to sell one smaller item; set on the side panel's Flipping tab",
		hidden = true
	)
	default boolean shopEvenHops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "geOrder",
		name = "Order GE items",
		description = "Order the Grand Exchange bank view most valuable first, or quickest to sell first (to free bank"
			+ " spaces; needs live GE prices); set on the side panel's Grand Exchange row",
		hidden = true
	)
	default GeOrder geOrder()
	{
		return GeOrder.VALUE;
	}
}
