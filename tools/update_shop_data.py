#!/usr/bin/env python3
"""
Regenerates src/main/resources/com/assetmanager/shops.json from the OSRS Wiki.

Only shops that trade in coins are included. Each item keeps the shop's normal buy and sell rates, plus the
Karamja gloves and Ring of Charos prices where the wiki lists them. Rates that depend on quests, ranks or leagues are
skipped because the plugin can't tell whether the player has unlocked them.

Run from the project root:  python3 tools/update_shop_data.py
"""
import json
import re
import sys
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import date

API = "https://oldschool.runescape.wiki/api.php"
UA = "AssetManager RuneLite plugin data generator (https://github.com/runelite/plugin-hub)"
OUT = "src/main/resources/com/assetmanager/shops.json"

GLOVES_NOTES = {"(Karamja gloves)": 1, "(Karamja gloves 3+)": 3}
# Where the store table's note is less specific than the shop page. Karamja General Store's page says
# gloves 3+ are needed, but its table is only marked "(Karamja gloves)".
GLOVES_TIER_OVERRIDES = {"Karamja General Store": 3}
# Notes that name a section of a shop rather than a requirement. Each section becomes its own shop.
SECTION_NOTES = {"(Catalytic runes)": False, "(Elemental runes)": False, "(general)": True}
# Better prices when the player has an activated Ring of Charos
CHAROS_NOTE = "(Ring of Charos)"
# NPCs you sell to through dialogue have no location on their shop listing.
LOCATION_OVERRIDES = {"Baraek": "Varrock Square"}
# Shops the wiki puts in the Wilderness region that are in a safe zone
SAFE_WILDERNESS_SHOPS = {"The Old Nite"}  # Ferox Enclave


def bucket(query):
    rows, offset = [], 0
    while True:
        q = query + f".limit(5000).offset({offset}).run()"
        url = API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": q})
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA})) as r:
            page = json.load(r)["bucket"]
        rows += page
        if len(page) < 5000:
            return rows
        offset += 5000


def map_points(text):
    """Map coordinates from a page's infobox map(s): [[x, y], ...]. Several for shops with branches."""
    points = []
    for line in re.findall(r"^\|\s*map\d*\s*=\s*(.*)$", text, re.M):
        for args in re.findall(r"\{\{Map\|([^}]*)\}\}", line, re.I):
            named = dict(re.findall(r"\b(x|y)\s*=\s*(\d+)", args))
            if "x" in named and "y" in named:
                points.append([int(named["x"]), int(named["y"])])
                continue
            pair = re.search(r"(?:^|\|)\s*(\d+)\s*,\s*(\d+)\s*(?:\||$)", args)
            if pair:
                points.append([int(pair.group(1)), int(pair.group(2))])
    return points


def restock_times(text):
    """Ticks for each item's stock to move one back towards normal, by lowercased item name, from a page's StoreLines."""
    times = {}
    for args in re.findall(r"\{\{\s*StoreLine\s*\|([^}]*)\}\}", text, re.I):
        named = {k.strip().lower(): v.strip() for k, v in re.findall(r"([A-Za-z]+)\s*=\s*([^|]*)", args)}
        name = named.get("displayname") or named.get("name")
        ticks = to_int(named.get("restock"), -1)
        if name and ticks > 0:
            times[clean(name).lower()] = ticks
    return times


def page_info(titles):
    """For each shop page: whether its infobox puts it in the Wilderness (its leagueRegion), its map points, and its
    items' restock times."""
    found, points, restocks, titles = set(), {}, {}, sorted(set(titles))
    for i in range(0, len(titles), 50):
        chunk = titles[i:i + 50]
        url = API + "?" + urllib.parse.urlencode({
            "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "format": "json",
            "formatversion": "2", "redirects": "1", "titles": "|".join(chunk)})
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA})) as r:
            query = json.load(r)["query"]
        # Map each resolved title back to the title that was asked for
        source = {t: t for t in chunk}
        for step in query.get("normalized", []) + query.get("redirects", []):
            source[step["to"]] = source.get(step["from"], step["from"])
        for page in query["pages"]:
            text = page.get("revisions", [{}])[0].get("slots", {}).get("main", {}).get("content", "")
            title = source.get(page["title"], page["title"])
            region = re.search(r"\|\s*leagueRegion\s*=\s*([^\n|}]*)", text)
            if region and "wilderness" in region.group(1).lower():
                found.add(title)
            if map_points(text):
                points[title] = map_points(text)
            if restock_times(text):
                restocks[title] = restock_times(text)
    return found, points, restocks


def to_int(s, default=0):
    m = re.match(r"-?\d+", str(s).replace(",", ""))
    return int(m.group()) if m else default


def clean(s):
    # "[[Brimhaven]]" -> "Brimhaven", "[[A|B]]" -> "B"
    s = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", str(s or ""))
    return re.sub(r"<[^>]+>", "", s).strip()


def rate(r, key):
    """A multiplier, or None if the shop doesn't trade this way (price listed as N/A)."""
    price_key = "store_buy_price" if key == "store_buy_multiplier" else "store_sell_price"
    if r.get(price_key) in (None, "", "N/A"):
        return None
    m = to_int(r.get(key))
    return m if m > 0 else None


def main():
    shops_info = {r["page_name"]: r for r in bucket(
        "bucket('infobox_shop').select('page_name','specialty','location','is_members_only')")}
    lines = bucket("bucket('storeline').select('sold_by','store_buy_price','store_sell_price','store_currency',"
                   "'store_delta','store_stock','store_buy_multiplier','store_sell_multiplier','store_notes','sold_item_json')")

    shops = defaultdict(lambda: {"items": {}, "buy": Counter(), "sell": Counter(), "delta": Counter()})
    for r in lines:
        if r.get("store_currency") != "Coins":
            continue
        notes = r.get("store_notes") or ""
        shop_name = r["sold_by"]
        if notes in SECTION_NOTES:
            shop_name = shop_name.rstrip(".") + " " + notes
            notes = ""
        gloves = GLOVES_NOTES.get(notes)
        charos = notes == CHAROS_NOTE
        if notes and gloves is None and not charos:
            continue
        buy, sell = rate(r, "store_buy_multiplier"), rate(r, "store_sell_multiplier")
        if buy is None and sell is None:
            continue
        name = json.loads(r.get("sold_item_json") or "{}").get("Display name") or r.get("sold_item")
        shop = shops[shop_name]
        item = shop["items"].setdefault(clean(name).lower(), {})
        if charos:
            # Kept for the buy side only; fixed-price traders get a coin amount, others a rate
            if buy:
                item["c"] = (to_int(r.get("store_buy_price")), buy, to_int(r.get("store_delta")), r.get("store_stock"))
            continue
        if gloves:
            if buy:
                item["g"] = buy
            if sell:
                item["gs"] = sell
            shop["glovesTier"] = gloves
        else:
            stock = r.get("store_stock")
            item.update({"buy": buy, "sell": sell, "d": to_int(r.get("store_delta")),
                         "s": -1 if stock == "\u221e" else max(0, to_int(stock))})
            # Traders like Baraek pay a fixed number of coins rather than a % of value. The wiki lists them at
            # 100% with no change per item and no stock; the coins they pay are in the buy price.
            if buy == 1000 and item["d"] == 0 and stock in ("N/A", "\u221e"):
                item["p"] = to_int(r.get("store_buy_price"))
            if buy:
                shop["buy"][buy] += 1
            if sell:
                shop["sell"][sell] += 1
            shop["delta"][item["d"]] += 1

    # Section shops (like Ali's) share their base page; others have parentheses in their own page title
    wild, points, restocks = page_info([n for name in shops for n in (name.rstrip("."), name.partition(" (")[0])])
    out = []
    for name, shop in sorted(shops.items()):
        if not shop["delta"]:
            continue  # only conditional prices
        base, _, section = name.partition(" (")
        info = shops_info.get(name) or shops_info.get(base + ".") or shops_info.get(base, {})
        buy = shop["buy"].most_common(1)[0][0] if shop["buy"] else 0
        sell = shop["sell"].most_common(1)[0][0] if shop["sell"] else 0
        delta = shop["delta"].most_common(1)[0][0]
        items = {}
        restock = restocks.get(name.rstrip(".")) or restocks.get(base) or {}
        for item_name, it in sorted(shop["items"].items()):
            if "d" not in it:
                continue
            entry = {"s": it["s"]}
            if it["buy"] is None:
                entry["nb"] = True
            elif it["buy"] != buy:
                entry["m"] = it["buy"]
            if it["sell"] is not None and it["sell"] != sell:
                entry["sm"] = it["sell"]
            if it["sell"] is None:
                entry["ns"] = True
            if it["d"] != delta:
                entry["d"] = it["d"]
            if "p" in it:
                entry["p"] = it["p"]
            if "c" in it:
                price, mult, d, stock = it["c"]
                if mult == 1000 and d == 0 and stock in ("N/A", "\u221e"):
                    entry["cp"] = price
                else:
                    entry["cm"] = mult
            for k in ("g", "gs"):
                if k in it:
                    entry[k] = it[k]
            if item_name in restock and it["s"] >= 0:
                # Ticks for the stock to move one back towards normal
                entry["r"] = restock[item_name]
            items[item_name] = entry
        s = {
            "name": name.rstrip("."),
            "location": LOCATION_OVERRIDES.get(name.rstrip("."), clean(info.get("location"))),
            "members": str(info.get("is_members_only")).lower() in ("true", "1", "yes"),
            "general": clean(info.get("specialty")).lower() == "general store" or SECTION_NOTES.get("(" + section, False),
            "buyMultiplier": buy,
            "sellMultiplier": sell,
            "delta": delta,
            "items": items,
        }
        # What kind of shop it is ("General store", "Farming shop"), for grouping shops that pay the same
        specialty = clean(info.get("specialty"))
        if specialty and specialty.lower() not in ("none", "yes"):
            s["type"] = specialty
        page = s["name"] if s["name"] in points or s["name"] in wild else base
        if page in wild and s["name"] not in SAFE_WILDERNESS_SHOPS:
            s["wilderness"] = True
        if page in points:
            # Where the shop is, for picking the closest of equally good shops
            s["at"] = points[page]
        if "glovesTier" in shop:
            s["glovesTier"] = GLOVES_TIER_OVERRIDES.get(s["name"], shop["glovesTier"])
            glove_buys = Counter(it["g"] for it in shop["items"].values() if "g" in it)
            if glove_buys:
                # The rate for items not in stock (general stores) with gloves
                s["glovesBuyMultiplier"] = glove_buys.most_common(1)[0][0]
        out.append(s)

    data = {"generated": date.today().isoformat(), "source": "https://oldschool.runescape.wiki", "shops": out}
    with open(OUT, "w") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=False)
    print(f"Wrote {len(out)} shops to {OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
