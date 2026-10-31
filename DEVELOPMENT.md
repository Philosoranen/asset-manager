# Development

Built from RuneLite's [example plugin](https://github.com/runelite/example-plugin) template; needs Java 11 to 17.

```
./gradlew run     # start RuneLite with the plugin
./gradlew test    # unit tests
python3 tools/update_shop_data.py   # refresh the bundled shop data from the OSRS Wiki
```

## Releasing an update

1. Refresh the shop data: `python3 tools/update_shop_data.py`.
2. Run the tests and try the plugin in the test client.
3. Commit and push, then update `commit=` in `plugins/asset-manager` in the [plugin-hub](https://github.com/runelite/plugin-hub) repository with the new commit hash and open a pull request.
