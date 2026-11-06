# Development

Built from RuneLite's [example plugin](https://github.com/runelite/example-plugin) template; needs Java 11 to 17.

```
./gradlew run     # start RuneLite with the plugin
./gradlew test    # unit tests
python3 tools/update_shop_data.py   # refresh the bundled shop data from the OSRS Wiki
```

## Shop requirements

`tools/shop_requirements.json` is hand-curated from the OSRS Wiki, because the wiki only describes shop requirements in each page's text. Each shop maps to its requirements: `skills` (RuneLite `Skill` names and levels), `quests` (finished) and `started` (RuneLite `Quest` names), `diaries` (`AREA_TIER`, e.g. `KARAMJA_EASY`), `combat`, `questPoints`, and `other` (plain-English requirements the plugin can't check, shown to the player as warnings). `items` adds requirements for particular items in a shop. `note` and `evidence` are for whoever maintains the file and aren't bundled. An empty entry means the shop has no requirements. For a shop with several branches, record the easiest one.

`update_shop_data.py` merges the file into the shop data and lists any shop that sells something but has no entry, so new shops can be added after each refresh. Quests newer than the RuneLite version in `build.gradle` aren't in its `Quest` enum yet; put them in `other` until they are.

## Releasing an update

1. Refresh the shop data: `python3 tools/update_shop_data.py`, and add entries to `tools/shop_requirements.json` for any shops it lists.
2. Run the tests and try the plugin in the test client.
3. Commit and push, then update `commit=` in `plugins/asset-manager` in the [plugin-hub](https://github.com/runelite/plugin-hub) repository with the new commit hash and open a pull request.
