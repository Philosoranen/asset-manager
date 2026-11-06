package com.assetmanager;

import com.assetmanager.value.GePrice;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(AssetManagerConfig.GROUP)
public interface AssetManagerConfig extends Config
{
	String GROUP = "assetmanager";

	@ConfigSection(
		name = "Death's coffer",
		description = "Death cost and coffer settings",
		position = 0
	)
	String cofferSection = "coffer";

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

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "targetDeaths",
		name = "Target deaths covered",
		description = "The top-up plan aims to cover this many deaths in your current gear",
		section = cofferSection,
		position = 2
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
		position = 3
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
		position = 4
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
		position = 5
	)
	default boolean showInfoBox()
	{
		return true;
	}

	@Range(min = -100, max = 1000)
	@ConfigItem(
		keyName = "minGainPercent",
		name = "Min. gain over alternative (%)",
		description = "Only suggest sacrificing items whose coffer value beats selling or alching by at least this much",
		section = cofferSection,
		position = 6
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
		position = 7
	)
	default boolean geCofferHint()
	{
		return true;
	}

	@ConfigSection(
		name = "Shops & GE prices",
		description = "Shop rates, shop items that can be resold on the Grand Exchange for a profit, and GE prices",
		position = 2
	)
	String flipSection = "flip";

	@ConfigItem(
		keyName = "shopFlips",
		name = "Show shop flips",
		description = "Mark items in shops that can be resold on the GE for a profit; hover one to see how many to buy",
		section = flipSection,
		position = 1
	)
	default boolean shopFlips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopSellCounts",
		name = "How many to sell",
		description = "In a shop, label each item in your inventory with how many to sell on this world before each"
			+ " sale stops beating the GE or alching, counting what the shop already holds. \"hop\" means a fresh world"
			+ " would buy some.",
		section = flipSection,
		position = 7
	)
	default boolean shopSellCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopRates",
		name = "Show shop rates",
		description = "Under an open shop's window, show what it sells and buys for and its change per item, as a % of item value",
		section = flipSection,
		position = 0
	)
	default boolean shopRates()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bestBuys",
		name = "Show best shop buys",
		description = "List the most profitable things to buy from shops for Death's coffer (and to resell on the GE, "
			+ "for accounts that can use it), out of the shops whose skill, quest and diary requirements you meet",
		section = flipSection,
		position = 1
	)
	default boolean bestBuys()
	{
		return true;
	}

	@Range(min = 1, max = 100_000_000)
	@ConfigItem(
		keyName = "minFlipProfit",
		name = "Min. flip profit (gp)",
		description = "Only mark a shop item when buying the suggested amount makes at least this much",
		section = flipSection,
		position = 2
	)
	default int minFlipProfit()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "shopMapMarkers",
		name = "Shops on world map",
		description = "Mark the shops Asset Manager suggests on the world map. Hover one for what to sell there.",
		section = flipSection,
		position = 3
	)
	default boolean shopMapMarkers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wearShopPerks",
		name = "Check gloves and ring",
		description = "Stop a shop sale that would pay more with Karamja gloves you have but aren't wearing, and talking to"
			+ " Baraek or Fritz with furs or molten glass while your Ring of Charos (a) isn't on. Click again to go ahead.",
		section = flipSection,
		position = 4
	)
	default boolean wearShopPerks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "livePrices",
		name = "Live GE prices (OSRS Wiki)",
		description = "Use real-time buy and sell prices and trade volumes from the OSRS Wiki, refreshed every 5 minutes,"
			+ " instead of RuneLite's 30-minute average. Needed for the GE price setting below.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = flipSection,
		position = 5
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
		section = flipSection,
		position = 6
	)
	default GePrice gePrice()
	{
		return GePrice.MARKET;
	}

	@ConfigSection(
		name = "Bank",
		description = "Bank views, item icons and keeping items",
		position = 1
	)
	String bankSection = "bank";

	@ConfigItem(
		keyName = "keepMenu",
		name = "Keep option in bank",
		description = "Add a Keep option when right-clicking items in your bank (Keep 1, 5, 10, X or all),"
			+ " to set how many Asset Manager should leave alone",
		section = bankSection,
		position = 99
	)
	default boolean keepMenu()
	{
		return true;
	}

	@ConfigItem(
		keyName = "priceTooltip",
		name = "Price comparison tooltip",
		description = "When hovering an item in your bank or inventory, compare what it's worth on the GE, high alched,"
			+ " in Death's coffer and at the best shop",
		section = bankSection,
		position = -1
	)
	default boolean priceTooltip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "protectKept",
		name = "Protect kept items",
		description = "Stop high alching, selling to a shop or offering on the GE the items you're keeping,"
			+ " when it would leave you fewer than your keep amount (counting your bank, inventory and equipment)",
		section = bankSection,
		position = 0
	)
	default boolean protectKept()
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
		keyName = "cofferSuggestions",
		name = "Coffer suggestions",
		description = "Top-up plan: only the items needed to reach your target deaths covered."
			+ " All: every item worth more in the coffer than sold or alched.",
		section = bankSection,
		position = 2
	)
	default CofferSuggestionMode cofferSuggestions()
	{
		return CofferSuggestionMode.PLAN;
	}

	@ConfigItem(
		keyName = "alchIcons",
		name = "High alch icons",
		description = "Mark items worth more high-alched than sold on the GE or to a shop (not shown for ironmen, who alch by default)",
		section = bankSection,
		position = 3
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
		position = 4
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
		position = 5
	)
	default boolean geIcons()
	{
		return true;
	}

	@Range(min = 1, max = Integer.MAX_VALUE)
	@ConfigItem(
		keyName = "minGeSale",
		name = "Min. GE sale (gp)",
		description = "Only suggest selling a spare stack on the GE when it's worth at least this much there, after tax",
		section = bankSection,
		position = 6
	)
	default int minGeSale()
	{
		return 10_000;
	}

	@ConfigItem(
		keyName = "karamjaGloves",
		name = "Karamja gloves",
		description = "Karamja gloves improve prices at some Karamja shops (wear them when selling)."
			+ " Detected from your Karamja diary rewards unless set here.",
		section = bankSection,
		position = 7
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
		section = bankSection,
		position = 8
	)
	default Ownership ringOfCharos()
	{
		return Ownership.AUTO;
	}

	@ConfigItem(
		keyName = "avoidWilderness",
		name = "Avoid Wilderness shops",
		description = "Never suggest selling to shops in the Wilderness, like Bandit Duty Free."
			+ " Ferox Enclave is a safe zone and still counts.",
		section = bankSection,
		position = 9
	)
	default boolean avoidWilderness()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minAlchGain",
		name = "Min. alch gain per item (gp)",
		description = "Only suggest alching when it beats the next best option by at least this much per item,"
			+ " after the nature rune and GE tax",
		section = bankSection,
		position = 10
	)
	default int minAlchGain()
	{
		return 100;
	}

	@Range(min = 1, max = 1_000_000)
	@ConfigItem(
		keyName = "minShopGainPerItem",
		name = "Min. shop gain per item (gp)",
		description = "Sell to a shop while each sale beats the GE and alching by at least this much, then hop worlds."
			+ " Often a shop only wins by the cost of a nature rune.",
		section = bankSection,
		position = 11
	)
	default int minShopGain()
	{
		return 1;
	}

	@Range(min = 0, max = 100_000_000)
	@ConfigItem(
		keyName = "minGainPerTrip",
		name = "Min. gain per shop trip (gp)",
		description = "Only suggest a shop when everything to sell there beats the GE or alching by this much together",
		section = bankSection,
		position = 12
	)
	default int minGainPerTrip()
	{
		return 2_000;
	}

	@Range(min = 0, max = 10_000)
	@ConfigItem(
		keyName = "shopMaxWorlds",
		name = "Max worlds to hop",
		description = "The most worlds you'd hop to sell one item. Each shop's own price drop sets how many to sell per"
			+ " world; a stack needing more worlds than this is left for another shop, the GE or alching. 0 = no"
			+ " limit: hop as much as it takes, over as many sessions as you like.",
		section = bankSection,
		position = 13
	)
	default int shopMaxWorlds()
	{
		return 10;
	}
}
