# Asset Manager

Asset Manager keeps Death's coffer topped up for your PvM, and works out the best way to cash out everything else you don't need: high alch, the Grand Exchange, or the shop that pays most, including how many to sell per world.

## Getting started

1. Open your bank once, so Asset Manager knows what you have. It remembers it between sessions.
2. Check your coffer with Death (or type the balance into the side panel), so it knows how many deaths you're covered for.
3. Right-click the gear and supplies you use and choose **Keep**. Everything you don't keep counts as spare.
4. Open the Asset Manager side panel. Each row (Death's coffer, High alch, Grand Exchange, each shop) shows what's worth sending there; click one to see those items in your bank, most valuable first.

Asset Manager only reads what the game shows you and never acts for you: it adds icons, tooltips, menu options and warnings, and can stop a click that would sell or alch something you're keeping.

## Death's coffer

- **Coffer balance** – read from the game when you check your coffer, remembered per account, and settable by hand from the side panel.
- **What a death costs right now** – works out which items you keep (3 most valuable, +1 with Protect Item, 0 when skulled) and the reclaim fee for:
  - your gravestone (1k / 10k / 100k per item worth 100k / 1m / 10m+, capped at 500k),
  - Death's Office (5% of each item worth 100k+),
  - boss item retrieval services (Vorkath, Zulrah, Nex, ToB, ToA, Hydra, Nightmare, ...). These are picked automatically when you're in the boss's area; everywhere else it's the gravestone.
  - Ironmen get 50% off grave and Death's Office fees, and ultimate ironmen can't use the coffer.
- **Deaths covered** – an infobox shows how many deaths your coffer pays for in your current gear, and turns red when it's running low.
- **Top-up plan** – after you open your bank once, lists the banked items that are worth more sacrificed to the coffer (105% of GE guide price) than sold on the GE after 2% tax (not for ironmen) or high-alched after the nature rune cost. It then plans the cheapest way to cover your target number of deaths.

## Spare items

- **Keep amounts** – right-click an item in your bank or seed vault and choose **Keep** → Keep 1, 5, 10, X (you can type amounts like 10k or 1.5m) or all to set how many Asset Manager should leave alone (e.g. keep 1 rune platebody for a clue step, or all the glories you collect). The Keep option shows the current amount, and "Stop keeping" removes it. Manage them in the panel under "Items you're keeping". Only the spare quantity is ever suggested. Potions with different doses and jewellery with different charges count as one item: keeping 4 prayer potions keeps any 4, those with the most doses first, so you don't need a new rule each time you drink a dose or use a charge. Uncharged jewellery counts too, kept last; trimmed versions like Amulet of glory(t4) are their own item.
- **Inventory, seed vault and potion storage** – spare items in your inventory and seed vault count too (the seed vault is remembered from the last time you opened it). Your bank's potion storage counts as part of your bank: its doses are counted as full potions plus one with whatever doses are left over. Kept items are counted from what you're wearing first, then your inventory, bank and seed vault, so if the ones you keep are in your inventory, those get no icons or tooltip and the banked ones are spare. Items only spare outside the bank are noted on each panel row ("2 in your seed vault"), since bank views can't show them.
- **Protect kept items** – if a high alch cast, a shop sale or a GE offer would leave you with fewer than your keep amount (counting your bank, inventory and equipment), the click is cancelled with a chat message saying how many you can part with. On the GE's offer screen, a sell offer whose quantity dips into your kept items has its Confirm button greyed out and blocked, with the "..." quantity button outlined and labelled with the most you can enter.
- **Compare GE offers with the coffer** – while your coffer is short of its target, the GE's offer screen says when sacrificing the item you're selling would add more to the coffer than the offer gets after tax, and how many would fill it. It only informs; the offer isn't blocked. Once the coffer is full it stays quiet, since coins from a sale are worth more than coins locked in the coffer.
- **Shops** – compares what 360+ shops across Gielinor would pay (from the OSRS Wiki), including specialty shops like Ali Morrisane's rune shop, the Wilderness Bandit Camp, the Rogues' Den, the Void Knight General Store and Davon's Amulet Store.
  - Karamja gloves prices are used when you've earned them (detected from your Karamja diary rewards, or set in the config). Gloves 3+ unlock the better prices at the Karamja General Store and Jiminua's Jungle Store.
  - An activated Ring of Charos gets better prices from Baraek (furs: 150 gp for grey wolf fur instead of 120) and Fritz the Glassblower (molten glass: 25 gp instead of 20). It's used when the ring is in your bank, inventory or equipment.
  - **Avoid Wilderness shops** leaves out the 14 shops the OSRS Wiki places in the Wilderness (Bandit Duty Free, Battle Runes, the Mage Arena shops, the Wilderness cape shops, Tony's Pizza Bases). The Old Nite in Ferox Enclave is a safe zone, so it's still suggested.
  - Traders who pay a fixed amount through dialogue (Baraek, Fritz, silk merchants and others) are priced at their actual coin amounts.
  - Prices that depend on quests, ranks or leagues aren't used.
  - Shops pay less for each one they already hold, so on each world you sell while each sale still beats the GE or alching, then hop to a fresh world. Each stack goes one way, whole: all to a shop, or all alched or sold on the GE.
  - Shop visits are planned as trips. A world hop costs what you say one is worth (**World hop worth**, 2 nature runes by default), and all the stacks you're selling at a shop share the hops: one stack gaining a nature rune per world isn't worth hopping for, three are. A trip hops as many worlds as gains the most; a stack that would need more goes another way. A shop is only suggested if the trip gains at least 2,000 gp after hops (**Min. gain per shop trip**), and never needs more than **Max worlds to hop** (0 = no limit). Each item goes to the shop that gains most over the GE or alching, counting only the hops it adds to that shop's trip, so many stacks can make a shop worth hopping that one stack wouldn't. Ali Morrisane's rune shop (no price drop, no hops) usually wins for runes.
  - **How many to sell** – in a shop, each item in your inventory is labelled with how many to sell on this world: as many as each sale still beats the GE or alching, counting what the shop already holds, including what other players have sold it. "sell all" means the whole spare stack; "hop" means none here, but a fresh world would buy some. Kept items are left out.
  - **Restock timers** – when you open a shop, Asset Manager notes which items are over their normal stock on that world and, from each item's restock time on the wiki, when they'll be back to normal. Which point in its restock cycle an item is at isn't shown, so at first the timer is the worst case ("up to 6h 0m"); once you see the stock drop by one without buying it, that item's cycle on that world is known and its timer is exact, for a week. The shop's info box shows the timers for the world you're on, and the side panel shows when the worlds you've used for a suggested shop are back to normal. If other players have overstocked an item on 2 or more worlds you checked, it's probably overstocked everywhere: that shop isn't suggested for that item until those worlds restock. Only what you see in the shop window is used; there's no way to see a shop on another world without going there.
  - **Check gloves and ring** stops a shop sale that would pay more with Karamja gloves you have but aren't wearing, and talking to Baraek or Fritz with furs or molten glass while your Ring of Charos (a) isn't on. Click again to go ahead anyway.
  - Many shops pay the same (the farming shops, for example). Shops gaining within 1% of the best count as the same, and the one closest to where you're standing wins, as the crow flies (teleports aren't considered). The panel and tooltip say when a shop was picked this way.
  - Suggested shops are marked on the world map (at the branch nearest you); hover a marker for what to sell there, click it to centre the map on it.
  - Each shop gets its own row in the panel, most profitable first; click it to see just that shop's items in your bank, so you can grab them and go. Hover an item for how many to sell per world, how many worlds, and what to do with the rest.
- **High alch** – items worth more alched than sold on the GE (mains only; ironmen alch by default).
- **Sell on the GE** (mains only) – spare stacks worth at least 10k on the GE after tax (configurable) where the GE beats alching and shops, sorted by value. Everything you aren't keeping counts as spare, so use Keep on the gear and supplies you use.
- **Side panel** – a summary of your coffer, target and death costs, then **Your spare items**: one row per destination (Everything, Death's coffer, High alch, Grand Exchange, every shop that's worth a trip, and Kept), each with how many items and what they're worth. Shops are listed by how much they gain you, with where they are and anything to wear.
- **Bank views** – click a row to see its items in your bank, most valuable first (Everything has a blank row between destinations). Nothing is added to your tab list; switch tabs to leave. Clicking with the bank closed opens the view next time you open it. While a view is open, items with a keep rule are labelled "keep 1", "keep all", etc. Hover an item for its price comparison.
- **Icons** – suggested items get a small coffer, High Alchemy, shop or GE icon in the bank, inventory and seed vault.
- **Coffer target** – your target deaths times the cost of your most expensive setup in the last 14 days (so it still works while you're standing in the bank), and never less than the minimum target (500k by default, the most a gravestone reclaim can cost).

## Shop flipping

- When a shop is open, items it sells that can be resold on the GE for a profit get a small Grand Exchange icon.
- Hover one to see how many to buy, the shop price (it rises as the shop's stock drops), what the GE pays after the 2% tax, the profit, and what limits the amount (the price rise, shop stock, inventory space, coins in your inventory, or, with live prices, how many the GE trades in an hour).
- **Live GE prices (OSRS Wiki)** is an opt-in setting. RuneLite's own prices are a single 30-minute average; the Wiki's real-time data has separate buy and sell prices and how many sold on the GE in the last hour. It's off by default because it contacts a third-party server.
- **GE sell price** (with live prices on) picks which price items are valued at everywhere: **List at market price** (default; what buyers have been paying, which you'll usually get by listing and waiting a little), **Midpoint**, or **Sell instantly**. Nature runes are costed at the instant-buy price. A single odd trade can't set the price: each side uses the lower of the latest trade and the last hour's average, the buy side is ignored if nobody bought in the last hour, and if buyers are paying more than twice what sellers accept (a barely traded item), the sell price is used. GE values are after the 2% tax, which isn't charged on items sold for under 50 gp; the tooltip also shows the price before tax.

## Network use and privacy

Asset Manager makes no network requests unless you turn on **Live GE prices (OSRS Wiki)**, which fetches prices from prices.runescape.wiki every 5 minutes. Nothing about you or your account is sent anywhere. Your coffer balance, bank snapshot, keep rules and shop timers are saved in your RuneLite profile.

## Data

Shop prices, stock and restock times, and death fee rules come from the OSRS Wiki. They're bundled with the plugin and kept up to date with each release, so there's nothing to download or set up.

## Roadmap

- "Made into" values (processing items into something worth more)
- A single best-use recommendation per item: GE, alch, shop, coffer or processing
