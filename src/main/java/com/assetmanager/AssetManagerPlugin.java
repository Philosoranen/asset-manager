package com.assetmanager;

import com.assetmanager.bank.BankViews;
import com.assetmanager.coffer.CofferCandidate;
import com.assetmanager.coffer.CofferPlan;
import com.assetmanager.coffer.CofferPlanner;
import com.assetmanager.coffer.CofferTracker;
import com.assetmanager.coffer.GeCofferOverlay;
import com.assetmanager.death.DeathCostCalculator;
import com.assetmanager.death.DeathCostEstimate;
import com.assetmanager.death.DeathLocations;
import com.assetmanager.death.DeathScenario;
import com.assetmanager.death.RiskItem;
import com.assetmanager.dest.Destination;
import com.assetmanager.dest.Destinations;
import com.assetmanager.flip.BestBuys;
import com.assetmanager.flip.ShopFlipOverlay;
import com.assetmanager.flip.ShopFlipper;
import com.assetmanager.flip.ShopRatesOverlay;
import com.assetmanager.keep.GeKeepOverlay;
import com.assetmanager.keep.Holdings;
import com.assetmanager.keep.KeepGuard;
import com.assetmanager.keep.KeepRules;
import com.assetmanager.keep.Variants;
import com.assetmanager.shop.PerkGuard;
import com.assetmanager.shop.SellRules;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopCatalog;
import com.assetmanager.shop.ShopFinder;
import com.assetmanager.shop.ShopMapMarkers;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.ShopSellOverlay;
import com.assetmanager.shop.ShopStock;
import com.assetmanager.shop.ShopStockTracker;
import com.assetmanager.shop.UnlockReader;
import com.assetmanager.shop.Unlocks;
import com.assetmanager.ui.AssetManagerPanel;
import com.assetmanager.ui.CofferInfoBox;
import com.assetmanager.ui.PanelState;
import com.assetmanager.ui.PriceTooltip;
import com.assetmanager.ui.SuggestionOverlay;
import com.assetmanager.value.ItemValuator;
import com.assetmanager.value.ItemValues;
import com.assetmanager.value.LivePrices;
import com.assetmanager.value.SaleAdvisor;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.SkullIcon;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
@PluginDependency(BankTagsPlugin.class)
@PluginDescriptor(
	name = "Asset Manager",
	description = "Death's coffer coverage, death costs, and the best way to use your spare items: coffer, alching or shops",
	tags = {"death", "coffer", "gravestone", "pvm", "money", "alch", "shop", "ironman", "bank"}
)
public class AssetManagerPlugin extends Plugin
{
	private static final String KEY_BANK = "bankSnapshot";
	private static final String KEY_BANK_COINS = "bankCoins";
	private static final String KEY_SEED_VAULT = "seedVaultSnapshot";
	private static final String KEY_POTIONS = "potionStorageSnapshot";
	private static final String KEY_KEEP = "keepRules";
	private static final String KEY_PEAK_FEE = "peakFee.";
	/** How long the most expensive recent setup counts towards the coffer target. */
	private static final Duration PEAK_FEE_TTL = Duration.ofDays(14);
	/** Refresh prices every 30 minutes (in game ticks). */
	private static final int PRICE_REFRESH_TICKS = 3000;
	/** Deals listed in each of the panel's best-buys lists. */
	private static final int BEST_BUYS_SHOWN = 5;
	/** Tiles moved before shop distances are worked out again. */
	private static final int RECALC_DISTANCE = 64;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private InfoBoxManager infoBoxManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private EventBus eventBus;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ItemValuator itemValuator;
	@Inject
	private CofferTracker cofferTracker;
	@Inject
	private AssetManagerConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private SuggestionOverlay suggestionOverlay;
	@Inject
	private BankViews bankViews;
	@Inject
	private KeepGuard keepGuard;
	@Inject
	private GeKeepOverlay geKeepOverlay;
	@Inject
	private GeCofferOverlay geCofferOverlay;
	@Inject
	private PerkGuard perkGuard;
	@Inject
	private ShopMapMarkers shopMapMarkers;
	@Inject
	private ShopStockTracker shopStockTracker;
	@Inject
	private ShopSellOverlay shopSellOverlay;
	@Inject
	private ShopFinder shopFinder;
	@Inject
	private UnlockReader unlockReader;
	@Inject
	private ChatboxPanelManager chatboxPanelManager;
	@Inject
	private PriceTooltip priceTooltip;
	@Inject
	private ShopFlipper shopFlipper;
	@Inject
	private ShopFlipOverlay shopFlipOverlay;
	@Inject
	private ShopRatesOverlay shopRatesOverlay;
	@Inject
	private LivePrices livePrices;
	@Inject
	private Gson gson;

	private ShopCatalog shopCatalog;
	private KeepRules keepRules = new KeepRules();

	private AssetManagerPanel panel;
	private NavigationButton navButton;
	private CofferInfoBox infoBox;

	/** Canonical item id -> quantity; null until the bank has been seen. */
	/** The bank's items, with what's in its potion storage; null until the bank has been seen. */
	private Map<Integer, Integer> bankItems;
	/** The bank's own items, and what's in its potion storage, which the game keeps as doses rather than items. */
	private Map<Integer, Integer> bankContainerItems;
	private Map<Integer, Integer> potionStorageItems = Collections.emptyMap();
	/** Potion storage changed, or the bank was opened: read it the next tick the bank is open. */
	private boolean potionStorageChanged;
	private boolean wasBankOpen;
	/** Canonical item id -> quantity; null until the seed vault has been opened. */
	private Map<Integer, Integer> seedVaultItems;
	private long bankCoins;

	private boolean dirty;
	/** Plans spare items off the client thread, one recalculation at a time. */
	private ExecutorService planner;
	private final AtomicInteger calcVersion = new AtomicInteger();
	/** The shops last suggested, kept over others paying the same. */
	private Set<String> suggestedShops = Collections.emptySet();
	private boolean lastSkulled;
	private boolean lastProtectItem;
	private int ticksSincePriceRefresh;
	private WorldPoint lastCalcLocation;
	/** Where death costs were last worked out for. */
	private DeathScenario lastScenario;
	/** What the player has unlocked, for shop requirements; read again when levels, quests or the account change. */
	private Unlocks unlocks = Unlocks.EVERYTHING;
	private boolean unlocksStale = true;
	/** The best shop buys need working out again: prices, unlocks or settings changed. */
	private boolean bestBuysStale = true;
	private List<BestBuys.Deal> cofferBuys = Collections.emptyList();
	private List<BestBuys.Deal> geBuys = Collections.emptyList();
	/** Coins the best buys were worked out for. */
	private long bestBuysBudget = -1;

	@Provides
	AssetManagerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AssetManagerConfig.class);
	}

	@Override
	protected void startUp()
	{
		shopCatalog = ShopCatalog.load(gson);
		planner = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "Asset Manager planner");
			t.setDaemon(true);
			return t;
		});
		panel = new AssetManagerPanel(
			value -> clientThread.invokeLater(() -> cofferTracker.setBalance(value, "manual entry")),
			(itemId, keep) -> clientThread.invokeLater(() -> setKeep(itemId, keep)),
			destination -> clientThread.invokeLater(() -> showInBank(destination)),
			destination -> clientThread.invokeLater(() -> findShop(destination.getName(), destination.getShop())),
			shop -> clientThread.invokeLater(() -> findShop(shop.getName(), shop)));
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Asset Manager")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(suggestionOverlay);
		bankViews.register();
		eventBus.register(keepGuard);
		perkGuard.setShops(shopCatalog);
		perkGuard.setOpenShop(shopFlipper::getOpenShop);
		eventBus.register(perkGuard);
		overlayManager.add(geKeepOverlay);
		overlayManager.add(geCofferOverlay);

		infoBox = new CofferInfoBox(itemManager.getImage(ItemID.COINS_10000), this);
		infoBox.setWarnDeaths(config.warnDeaths());
		updateInfoBox();

		cofferTracker.setOnChange(() -> dirty = true);
		eventBus.register(cofferTracker);
		eventBus.register(priceTooltip);
		shopFlipper.setShops(shopCatalog);
		eventBus.register(shopFlipper);
		shopStockTracker.setOpenShop(shopFlipper::getOpenShop);
		shopStockTracker.setOnChange(() -> dirty = true);
		eventBus.register(shopStockTracker);
		shopSellOverlay.setOpenShop(shopFlipper::getOpenShop);
		overlayManager.add(shopSellOverlay);
		shopFinder.setOnArrived(shop ->
		{
			chat("Asset Manager: you're at " + shop + ".");
			findingChanged(null);
		});
		eventBus.register(shopFinder);
		overlayManager.add(shopFlipOverlay);
		overlayManager.add(shopRatesOverlay);
		livePrices.setOnUpdate(() -> clientThread.invokeLater(() ->
		{
			itemValuator.clearCache();
			bestBuysStale = true;
			dirty = true;
		}));
		if (config.livePrices())
		{
			livePrices.start();
		}

		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(cofferTracker);
		eventBus.unregister(priceTooltip);
		eventBus.unregister(shopFlipper);
		eventBus.unregister(shopStockTracker);
		overlayManager.remove(shopSellOverlay);
		eventBus.unregister(shopFinder);
		clientThread.invoke(shopFinder::clear);
		overlayManager.remove(shopFlipOverlay);
		overlayManager.remove(shopRatesOverlay);
		livePrices.stop();
		priceTooltip.setContext(null);
		cofferTracker.setOnChange(() -> {});
		clientToolbar.removeNavigation(navButton);
		infoBoxManager.removeInfoBox(infoBox);
		overlayManager.remove(suggestionOverlay);
		suggestionOverlay.setSuggestions(Suggestions.NONE);
		eventBus.unregister(keepGuard);
		eventBus.unregister(perkGuard);
		shopMapMarkers.clear();
		overlayManager.remove(geKeepOverlay);
		overlayManager.remove(geCofferOverlay);
		clientThread.invoke(bankViews::unregister);
		planner.shutdown();
		calcVersion.incrementAndGet();
		bankItems = null;
		bankContainerItems = null;
		seedVaultItems = null;
		panel = null;
		infoBox = null;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadProfile();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// Another account may log in next
			unlocks = Unlocks.EVERYTHING;
			unlocksStale = true;
			bestBuysStale = true;
		}
		dirty = true;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Integer known = unlocks.getSkills() == null ? null : unlocks.getSkills().get(event.getSkill().name());
		if (known != null && known != event.getLevel())
		{
			unlocksStale = true;
			bestBuysStale = true;
			dirty = true;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AssetManagerConfig.GROUP.equals(event.getGroup()) || event.getKey().startsWith("rsprofile"))
		{
			return;
		}
		if ("livePrices".equals(event.getKey()))
		{
			if (config.livePrices())
			{
				livePrices.start();
			}
			else
			{
				livePrices.stop();
				clientThread.invokeLater(itemValuator::clearCache);
			}
		}
		if ("gePrice".equals(event.getKey()))
		{
			clientThread.invokeLater(itemValuator::clearCache);
		}
		infoBox.setWarnDeaths(config.warnDeaths());
		updateInfoBox();
		bestBuysStale = true;
		dirty = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			scanBank(event.getItemContainer());
			dirty = true;
		}
		else if (id == InventoryID.SEED_VAULT)
		{
			scanSeedVault(event.getItemContainer());
			dirty = true;
		}
		else if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() == VarPlayerID.QP_TOTAL && unlocks.getSkills() != null && event.getValue() != unlocks.getQuestPoints())
		{
			// A quest was finished
			unlocksStale = true;
			bestBuysStale = true;
			dirty = true;
		}
		switch (event.getVarpId())
		{
			case VarPlayerID.POTIONSTORE_BASE_VAR_1:
			case VarPlayerID.POTIONSTORE_BASE_VAR_2:
			case VarPlayerID.POTIONSTORE_BASE_VAR_3:
			case VarPlayerID.POTIONSTORE_BASE_VAR_4:
			case VarPlayerID.POTIONSTORE_BASE_VAR_5:
			case VarPlayerID.POTIONSTORE_BASE_VAR_6:
				potionStorageChanged = true;
				break;
			default:
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		cofferTracker.readCofferWindow();
		Widget bankWidget = client.getWidget(InterfaceID.Bankmain.ITEMS);
		boolean bankOpen = bankWidget != null && !bankWidget.isHidden();
		// Read when the bank opens, then only when the storage changes
		if (bankOpen && !wasBankOpen)
		{
			potionStorageChanged = true;
		}
		wasBankOpen = bankOpen;
		if (potionStorageChanged && bankOpen)
		{
			potionStorageChanged = false;
			scanPotionStorage();
			dirty = true;
		}
		bankViews.onGameTick();

		boolean skulled = isSkulled();
		boolean protectItem = client.getVarbitValue(VarbitID.PRAYER_PROTECTITEM) == 1;
		if (skulled != lastSkulled || protectItem != lastProtectItem)
		{
			lastSkulled = skulled;
			lastProtectItem = protectItem;
			dirty = true;
		}

		// The closest of equally good shops depends on where you are
		WorldPoint location = playerLocation();
		if (location != null && (lastCalcLocation == null || location.distanceTo2D(lastCalcLocation) > RECALC_DISTANCE
			|| DeathLocations.at(location.getRegionID()) != lastScenario))
		{
			dirty = true;
		}

		if (++ticksSincePriceRefresh >= PRICE_REFRESH_TICKS)
		{
			ticksSincePriceRefresh = 0;
			itemValuator.clearCache();
			// Diaries and other progress aren't watched, so they're read again now and then too
			unlocksStale = true;
			bestBuysStale = true;
			dirty = true;
		}

		if (dirty)
		{
			dirty = false;
			recalculate();
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.keepMenu())
		{
			return;
		}
		// Right-clicking an item in the bank, the bank's inventory or the seed vault
		MenuEntry item = null;
		for (MenuEntry e : event.getMenuEntries())
		{
			int widget = e.getParam1();
			int group = widget >>> 16;
			if ((widget == InterfaceID.Bankmain.ITEMS || widget == InterfaceID.Bankside.ITEMS
				|| group == InterfaceID.SEED_VAULT || group == InterfaceID.SEED_VAULT_DEPOSIT) && itemIdOf(e) > 0)
			{
				item = e;
				break;
			}
		}
		if (item == null)
		{
			return;
		}
		String target = item.getTarget();
		// Only changes Asset Manager's keep rules (Keep X opens a RuneLite chatbox prompt); nothing is sent to the game.
		int canonical = itemManager.canonicalize(itemIdOf(item));
		int current = keepRules.get(canonical);
		String keeping = current == 0 ? "" : " (keeping " + (current == KeepRules.KEEP_ALL ? "all" : QuantityFormatter.formatNumber(current)) + ")";
		// Just above Cancel, at the bottom of the menu
		MenuEntry keep = client.getMenu().createMenuEntry(1)
			.setOption("Keep")
			.setTarget(target + keeping)
			.setType(MenuAction.RUNELITE);
		Menu sub = keep.createSubMenu();
		// Entries added later are shown higher, so this builds the list bottom-up
		if (current != 0)
		{
			keepOption(sub, "Stop keeping", target, e -> keepFromMenu(canonical, 0));
		}
		keepOption(sub, "Keep all", target, e -> keepFromMenu(canonical, KeepRules.KEEP_ALL));
		keepOption(sub, "Keep X", target, e -> promptKeep(canonical));
		for (int n : new int[]{10, 5, 1})
		{
			keepOption(sub, "Keep " + n, target, e -> keepFromMenu(canonical, n));
		}
	}

	private static int itemIdOf(MenuEntry e)
	{
		if (e.getItemId() > 0)
		{
			return e.getItemId();
		}
		return e.getWidget() == null ? -1 : e.getWidget().getItemId();
	}

	private static void keepOption(Menu menu, String option, String target, Consumer<MenuEntry> onClick)
	{
		menu.createMenuEntry(-1)
			.setOption(option)
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(onClick);
	}

	private void keepFromMenu(int itemId, int keep)
	{
		setKeep(itemId, keep);
		chat(keepMessage(itemManager.getItemComposition(itemId).getName(), keep));
	}

	private static String keepMessage(String name, int keep)
	{
		return keep == 0 ? "Asset Manager: no longer keeping " + name + "."
			: keep == KeepRules.KEEP_ALL ? "Asset Manager: keeping all " + name + "."
			: "Asset Manager: keeping " + QuantityFormatter.formatNumber(keep) + " x " + name + ".";
	}

	private void promptKeep(int rawItemId)
	{
		int itemId = itemManager.canonicalize(rawItemId);
		String name = itemManager.getItemComposition(itemId).getName();
		int current = keepRules.get(itemId);
		chatboxPanelManager.openTextInput("How many " + name + " should Asset Manager leave alone? (a number like 5 or 10k, \"all\", or 0 to clear)")
			.value(current == KeepRules.KEEP_ALL ? "all" : current > 0 ? String.valueOf(current) : "")
			.onDone((Consumer<String>) input -> clientThread.invokeLater(() ->
			{
				Integer keep = KeepRules.parseInput(input);
				if (keep == null)
				{
					chat("Asset Manager: \"" + input + "\" isn't a number (like 5 or 10k) or \"all\".");
					return;
				}
				setKeep(itemId, keep);
				chat(keepMessage(name, keep));
			}))
			.build();
	}

	private void showInBank(Destination d)
	{
		if (!bankViews.show(d))
		{
			chat("Asset Manager: open your bank to see " + (d.getKind() == Destination.Kind.SHOP ? "what to sell to " + d.getName()
				: d.getKind() == Destination.Kind.ALL ? "all your spare items" : "your " + d.getName() + " items") + ".");
		}
	}

	/** Points the way to a shop from the panel, or stops if it's already the one being found. Client thread. */
	/** Points the way to a shop, or the nearest of a group of shops paying the same ({@code name}); again to stop. */
	private void findShop(String name, Shop fallback)
	{
		if (name.equals(shopFinder.getShop()))
		{
			shopFinder.clear();
			findingChanged(null);
			return;
		}
		// For a group of shops paying the same, the one nearest the player now
		WorldPoint here = playerLocation();
		Shop shop = Optional.ofNullable(shopCatalog.nearestInGroup(name, here)).orElse(fallback);
		WorldPoint at = shop.nearestPoint(here);
		if (at == null)
		{
			chat("Asset Manager: the OSRS Wiki doesn't say where " + shop.getName() + " is.");
			return;
		}
		shopFinder.find(name, at);
		findingChanged(name);
		String where = shop.getLocation() == null || shop.getLocation().isEmpty() ? "" : " in " + shop.getLocation();
		chat("Asset Manager: " + (shop.getName().equals(name) ? name + " is" + where
			: "the nearest " + name.toLowerCase() + " is " + shop.getName() + where)
			+ ". Open your world map to see it; the hint arrow points the way.");
	}

	private void findingChanged(String shop)
	{
		shopMapMarkers.setFocused(shop);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setFindingShop(shop);
			}
		});
	}

	private void setKeep(int itemId, int keep)
	{
		keepRules.set(itemId, keep);
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_KEEP, keepRules.encode());
		dirty = true;
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}

	private void loadProfile()
	{
		keepRules = KeepRules.decode(configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_KEEP));
		keepGuard.setRules(keepRules);
		suggestionOverlay.setKeepRules(keepRules);
		cofferTracker.load();
		shopStockTracker.load();
		loadBank();
		itemValuator.clearCache();
		dirty = true;
	}

	private void recalculate()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			publish(null);
			return;
		}

		AccountMode mode = resolveAccountMode();
		List<RiskItem> carried = new ArrayList<>();
		collectRiskItems(client.getItemContainer(InventoryID.INV), carried);
		collectRiskItems(client.getItemContainer(InventoryID.WORN), carried);

		boolean skulled = isSkulled();
		boolean protectItem = client.getVarbitValue(VarbitID.PRAYER_PROTECTITEM) == 1;
		int keep = mode == AccountMode.ULTIMATE_IRONMAN ? 0 : (skulled ? 0 : 3) + (protectItem ? 1 : 0);
		// What a death costs depends on where you are: a boss's retrieval service, or a gravestone
		WorldPoint here = playerLocation();
		DeathScenario scenario = here == null ? DeathScenario.GRAVESTONE : DeathLocations.at(here.getRegionID());
		lastScenario = scenario;
		DeathCostEstimate estimate = DeathCostCalculator.estimate(mergeRiskItems(carried), keep, mode.hasIronFeeDiscount(), scenario);

		long balance = cofferTracker.isKnown() ? cofferTracker.getBalance() : 0;
		long peakFee = updatePeakFee(estimate);
		long target = Math.max(config.minCofferTarget(), Math.max(estimate.getScenarioFee(), peakFee) * config.targetDeaths());
		int glovesTier = resolveGlovesTier();
		boolean charos = resolveCharos();
		WorldPoint location = playerLocation();
		lastCalcLocation = location;
		// Keep suggesting the same shops as the player walks to them, rather than the closest of equally good ones
		Set<String> preferred = new HashSet<>(suggestedShops);
		if (shopFinder.getShop() != null)
		{
			preferred.add(shopFinder.getShop());
		}
		ShopPerks perks = new ShopPerks(glovesTier, charos, config.avoidWilderness(), location, preferred);
		// Deals have to be affordable: coins in the bank and inventory, or no limit until the bank has been seen
		long budget = bankItems == null ? Long.MAX_VALUE : bankCoins + inventoryCoins();
		if (bestBuysStale || budget != bestBuysBudget)
		{
			bestBuysStale = false;
			bestBuysBudget = budget;
			updateBestBuys(mode, perks, budget);
		}
		shopFlipper.setGlovesTier(glovesTier);
		perkGuard.setGlovesTier(glovesTier);
		perkGuard.setCharos(charos);
		Holdings holdings = holdings();
		// Potion doses and jewellery charges share one keep rule
		Map<Integer, String> names = new HashMap<>();
		for (int id : holdings.allItemIds())
		{
			names.put(id, itemValuator.get(id).getName());
		}
		for (int id : keepRules.asMap().keySet())
		{
			names.put(id, itemValuator.get(id).getName());
		}
		if (keepRules.setVariants(Variants.group(names)))
		{
			configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_KEEP, keepRules.encode());
		}
		SellRules sellRules = new SellRules(config.minShopGain(), config.shopMaxWorlds(), config.minGainPerTrip());
		List<ShopStock.Busy> busy = shopStockTracker.get().busy(System.currentTimeMillis());
		Inputs in = new Inputs(mode, perks, holdings, sellRules, location, balance, target, peakFee, skulled, protectItem, estimate);
		// A newer recalculation makes this one's results stale
		int version = calcVersion.incrementAndGet();
		Apply apply = (plan, saleResult, destinations) -> applyResults(in, plan, saleResult, destinations);
		if (bankItems != null)
		{
			Map<Integer, ItemValues> values = new HashMap<>();
			for (int itemId : holdings.itemIds())
			{
				values.put(itemId, itemValuator.get(itemId));
			}
			Map<Integer, Integer> spare = holdings.spare(keepRules);
			int natureRuneCost = itemValuator.getNatureRuneCost();
			// Shops others are overstocking an item at on every world aren't worth a trip for it until they restock
			Map<String, Set<String>> busyShops = new HashMap<>();
			for (ShopStock.Busy b : busy)
			{
				busyShops.computeIfAbsent(b.getItem(), k -> new HashSet<>()).add(b.getShop());
			}
			Map<Integer, Set<String>> avoid = new HashMap<>();
			values.forEach((id, v) ->
			{
				Set<String> shops = busyShops.get(v.getName().toLowerCase());
				if (shops != null)
				{
					avoid.put(id, shops);
				}
			});
			// What the kept items in the bank are worth, to list them most valuable first
			Map<Integer, Long> keptValues = new HashMap<>();
			for (int rule : keepRules.asMap().keySet())
			{
				for (int id : keepRules.members(rule))
				{
					int inBank = bankItems.getOrDefault(id, 0);
					if (inBank > 0)
					{
						ItemValues v = itemValuator.get(id);
						keptValues.put(id, inBank * Math.max(v.getGeNetSell(), v.getHaPrice()));
					}
				}
			}
			// Spare items not in the bank can't be shown in a bank view, so say where they are
			Map<Integer, Holdings.Place> outsideBank = new HashMap<>();
			for (int id : spare.keySet())
			{
				if (holdings.spare(Holdings.Place.BANK, id, keepRules) == 0)
				{
					outsideBank.put(id, holdings.spare(Holdings.Place.SEED_VAULT, id, keepRules) > 0
						? Holdings.Place.SEED_VAULT : Holdings.Place.INVENTORY);
				}
			}
			// The planning itself is the slow part, so it runs off the client thread; everything it needs is copied
			boolean canUseCoffer = mode.canUseCoffer();
			boolean canUseGe = mode.canUseGrandExchange();
			int minGainPercent = config.minGainPercent();
			int minAlchGain = config.minAlchGain();
			long minGeSale = config.minGeSale();
			CofferSuggestionMode cofferMode = config.cofferSuggestions();
			long coins = bankCoins;
			ShopCatalog catalog = shopCatalog;
			planner.execute(() ->
			{
				if (version != calcVersion.get())
				{
					return;
				}
				CofferPlan p = null;
				if (canUseCoffer)
				{
					List<CofferCandidate> candidates = CofferPlanner.findCandidates(values, spare, canUseGe, natureRuneCost, minGainPercent);
					p = CofferPlanner.plan(candidates, balance, target, coins);
				}
				SaleAdvisor.Result r = SaleAdvisor.suggest(values, spare, canUseGe, natureRuneCost, minAlchGain, minGeSale, catalog,
					perks, sellRules, avoid);
				Destinations d = Destinations.build(p, cofferMode, r.getSales(), r.getTrips(), keptValues, outsideBank);
				CofferPlan planned = p;
				clientThread.invokeLater(() ->
				{
					if (version == calcVersion.get())
					{
						apply.accept(planned, r, d);
					}
				});
			});
			return;
		}
		apply.accept(null, SaleAdvisor.Result.NONE, Destinations.NONE);
	}

	@FunctionalInterface
	private interface Apply
	{
		void accept(CofferPlan plan, SaleAdvisor.Result saleResult, Destinations destinations);
	}

	/** Hands a recalculation's results to the overlays, bank views, map and panel. Client thread. */
	private void applyResults(Inputs in, CofferPlan plan, SaleAdvisor.Result saleResult, Destinations destinations)
	{
		AccountMode mode = in.mode;
		ShopPerks perks = in.perks;
		Holdings holdings = in.holdings;
		long now = System.currentTimeMillis();
		ShopStock shopStock = shopStockTracker.get();
		List<PanelState.KeptItem> kept = new ArrayList<>();
		for (Map.Entry<Integer, Integer> rule : keepRules.asMap().entrySet())
		{
			String name = itemValuator.get(rule.getKey()).getName();
			kept.add(new PanelState.KeptItem(rule.getKey(), keepRules.members(rule.getKey()).size() > 1 ? Variants.base(name) : name,
				rule.getValue(), bankItems == null ? -1 : holdings.total(rule.getKey(), keepRules)));
		}

		suggestionOverlay.setSuggestions(destinations.getSuggestions());
		if (bankItems != null)
		{
			suggestedShops = destinations.getShops().stream().map(Destination::getName).collect(Collectors.toSet());
		}
		lastCalcLocation = in.location;
		suggestionOverlay.setKeepRules(keepRules);
		suggestionOverlay.setHoldings(holdings);
		keepGuard.setRules(keepRules);
		keepGuard.setHoldings(holdings);
		shopSellOverlay.setContext(config.shopSellCounts() ? new ShopSellOverlay.Context(perks, config.minShopGain(),
			itemValuator.getNatureRuneCost(), mode.canUseGrandExchange(), keepRules, holdings) : null);
		geCofferOverlay.setShortfall(mode.canUseCoffer() ? Math.max(0, in.target - in.balance) : 0);
		geCofferOverlay.setCofferKnown(cofferTracker.isKnown());
		priceTooltip.setContext(new PriceTooltip.Context(mode, itemValuator.getNatureRuneCost(), perks, in.sellRules,
			saleResult.getSkippedShops(), destinations.getSuggestions().getShop(), shopCatalog, keepRules, holdings));
		bankViews.update(destinations);
		shopMapMarkers.update(config.shopMapMarkers() ? destinations.getShops() : Collections.emptyList(), in.location);

		publish(new PanelState(mode, in.balance, cofferTracker.isKnown(), cofferTracker.getSource(), cofferTracker.getUpdatedAt(),
			in.skulled, in.protectItem, in.estimate, plan, in.target, in.peakFee, config.minCofferTarget(), kept, perks,
			bankCoins, config.targetDeaths(), bankItems == null ? null : destinations, restockedBy(shopStock, destinations, now),
			shopStock.busy(now), cofferBuys, geBuys, bankItems != null));
	}

	private long inventoryCoins()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		return inv == null ? 0 : inv.count(ItemID.COINS);
	}

	/**
	 * Works out the best things to buy from shops for the coffer and the GE, off the client thread. Prices are gathered
	 * here first; the player's unlocks are read only when they may have changed, as that runs a script per quest.
	 */
	private void updateBestBuys(AccountMode mode, ShopPerks perks, long coins)
	{
		if (!config.bestBuys())
		{
			cofferBuys = Collections.emptyList();
			geBuys = Collections.emptyList();
			return;
		}
		if (unlocksStale)
		{
			unlocksStale = false;
			unlocks = unlockReader.read();
		}
		Map<Integer, ItemValues> values = new HashMap<>();
		Map<Integer, Long> volumes = new HashMap<>();
		Set<Integer> stackable = new HashSet<>();
		for (Shop shop : shopCatalog.getShops())
		{
			for (Shop.Item item : shop.getItems().values())
			{
				Integer id = item.getId();
				if (id == null || item.isNs() || values.containsKey(id))
				{
					continue;
				}
				values.put(id, itemValuator.get(id));
				LivePrices.Volume hour = livePrices.volume(id);
				volumes.put(id, hour == null ? Long.MAX_VALUE : (long) hour.getHigh() + hour.getLow());
				if (itemManager.getItemComposition(id).isStackable())
				{
					stackable.add(id);
				}
			}
		}
		BestBuys.Prices prices = new BestBuys.Prices()
		{
			@Override
			public long coffer(int itemId)
			{
				return values.get(itemId).getCofferValue();
			}

			@Override
			public long ge(int itemId)
			{
				return values.get(itemId).getGeNetSell();
			}

			@Override
			public long geVolume(int itemId)
			{
				return volumes.get(itemId);
			}

			@Override
			public int storeValue(int itemId)
			{
				return values.get(itemId).getStorePrice();
			}

			@Override
			public boolean stackable(int itemId)
			{
				return stackable.contains(itemId);
			}
		};
		Unlocks have = unlocks;
		List<Shop> shops = shopCatalog.getShops();
		long minGain = config.minFlipProfit();
		boolean coffer = mode.canUseCoffer();
		boolean ge = mode.canUseGrandExchange();
		planner.execute(() ->
		{
			List<BestBuys.Deal> c = coffer
				? BestBuys.find(BestBuys.Kind.COFFER, shops, prices, have, perks, coins, minGain, BEST_BUYS_SHOWN)
				: Collections.emptyList();
			List<BestBuys.Deal> g = ge
				? BestBuys.find(BestBuys.Kind.GE, shops, prices, have, perks, coins, minGain, BEST_BUYS_SHOWN)
				: Collections.emptyList();
			clientThread.invokeLater(() ->
			{
				cofferBuys = c;
				geBuys = g;
				dirty = true;
			});
		});
	}

	/** What a recalculation worked out on the client thread, for applying its results. */
	@AllArgsConstructor
	private static class Inputs
	{
		final AccountMode mode;
		final ShopPerks perks;
		final Holdings holdings;
		final SellRules sellRules;
		final WorldPoint location;
		final long balance;
		final long target;
		final long peakFee;
		final boolean skulled;
		final boolean protectItem;
		final DeathCostEstimate estimate;
	}

	/** For each suggested shop seen overstocked, when every world the player saw it overstocked on is back to normal. */
	private static Map<String, Long> restockedBy(ShopStock stock, Destinations destinations, long now)
	{
		Map<String, Long> out = new HashMap<>();
		for (Destination d : destinations.getShops())
		{
			String shop = d.getShop().getName();
			stock.overstocked(shop, now).stream().mapToLong(ShopStock.Seen::getNormalAt).max().ifPresent(t -> out.put(shop, t));
		}
		return out;
	}

	/**
	 * Remembers the most expensive death cost seen in the last 14 days, wherever it was, so the coffer target still
	 * reflects your PvM gear (and the boss's retrieval fee) while you're standing in the bank without it.
	 */
	private long updatePeakFee(DeathCostEstimate estimate)
	{
		// Across all places, so gearing up for a boss sets the target even while back at the bank
		String key = KEY_PEAK_FEE + "any";
		long now = System.currentTimeMillis();
		long peak = 0;
		long storedAt = 0;
		String stored = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, key);
		if (stored != null)
		{
			String[] parts = stored.split(":");
			try
			{
				if (parts.length == 2 && now - Long.parseLong(parts[1]) < PEAK_FEE_TTL.toMillis())
				{
					peak = Long.parseLong(parts[0]);
					storedAt = Long.parseLong(parts[1]);
				}
			}
			catch (NumberFormatException ignored)
			{
				// treat as no peak
			}
		}
		long fee = estimate.getScenarioFee();
		if (fee > 0 && fee >= peak)
		{
			// Rewrite when it goes up, or hourly while it's matched so it doesn't expire mid-use
			if (fee > peak || now - storedAt > Duration.ofHours(1).toMillis())
			{
				configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, key, fee + ":" + now);
			}
			peak = fee;
		}
		return peak;
	}

	private int resolveGlovesTier()
	{
		KaramjaGloves setting = config.karamjaGloves();
		if (setting != KaramjaGloves.AUTO)
		{
			return setting.getTier();
		}
		if (client.getVarbitValue(VarbitID.ATJUN_HARD_REWARD) == 1)
		{
			return 3;
		}
		return client.getVarbitValue(VarbitID.ATJUN_EASY_REWARD) == 1 ? 1 : 0;
	}

	/** Whether the player owns an activated Ring of Charos, in the bank, inventory or worn. */
	private boolean resolveCharos()
	{
		Ownership setting = config.ringOfCharos();
		if (setting != Ownership.AUTO)
		{
			return setting == Ownership.YES;
		}
		if (bankItems != null && bankItems.getOrDefault(ItemID.RING_OF_CHAROS_UNLOCKED, 0) > 0)
		{
			return true;
		}
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer c = client.getItemContainer(container);
			if (c != null && c.count(ItemID.RING_OF_CHAROS_UNLOCKED) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/** Turns names from the old "Never suggest" setting into "keep all" rules once the item is in the bank. */
	private void publish(PanelState state)
	{
		if (infoBox != null)
		{
			infoBox.setState(state);
		}
		int warn = config.warnDeaths();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.update(state, warn);
			}
		});
	}

	private void updateInfoBox()
	{
		infoBoxManager.removeInfoBox(infoBox);
		if (config.showInfoBox())
		{
			infoBoxManager.addInfoBox(infoBox);
		}
	}

	private AccountMode resolveAccountMode()
	{
		AccountMode mode = config.accountMode();
		return mode == AccountMode.AUTO ? AccountMode.fromVarbit(client.getVarbitValue(VarbitID.IRONMAN)) : mode;
	}

	/** Where the player is on the world map (outside of any instance), or null if not logged in. */
	private WorldPoint playerLocation()
	{
		Player player = client.getLocalPlayer();
		return player == null ? null : WorldPoint.fromLocalInstance(client, player.getLocalLocation());
	}

	private boolean isSkulled()
	{
		Player player = client.getLocalPlayer();
		return player != null && player.getSkullIcon() != SkullIcon.NONE;
	}

	private void collectRiskItems(ItemContainer container, List<RiskItem> out)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(item.getId());
			ItemValues v = itemValuator.get(item.getId());
			out.add(new RiskItem(v.getItemId(), v.getName(), item.getQuantity(), comp.isStackable(),
				v.getProtectionValue(), v.getGuidePrice()));
		}
	}

	/** Combines entries of the same item so the panel lists "28 x Shark" rather than 28 rows. */
	private static List<RiskItem> mergeRiskItems(List<RiskItem> items)
	{
		Map<Integer, RiskItem> merged = new LinkedHashMap<>();
		for (RiskItem i : items)
		{
			merged.merge(i.getItemId(), i, (a, b) -> new RiskItem(a.getItemId(), a.getName(), a.getQuantity() + b.getQuantity(),
				a.isStackable(), a.getProtectionValue(), a.getFeeValue()));
		}
		return new ArrayList<>(merged.values());
	}

	private void scanBank(ItemContainer bank)
	{
		long[] coins = new long[1];
		bankContainerItems = countItems(bank, coins);
		bankCoins = coins[0];
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_BANK, encodeItems(bankContainerItems));
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_BANK_COINS, bankCoins);
		mergeBank();
	}

	/**
	 * Reads the bank's potion storage, which holds doses rather than items: counted as that many full potions, plus one
	 * with the doses left over. Like RuneLite's bank tags, it asks the game's own scripts. Only while the bank is open.
	 */
	private void scanPotionStorage()
	{
		Map<Integer, Integer> items = new HashMap<>();
		for (int listId : new int[]{EnumID.POTIONSTORE_POTIONS, EnumID.POTIONSTORE_UNFINISHED_POTIONS})
		{
			for (int potionId : client.getEnum(listId).getIntVals())
			{
				client.runScript(ScriptID.POTIONSTORE_DOSES, potionId);
				int doses = client.getIntStack()[0];
				client.runScript(ScriptID.POTIONSTORE_WITHDRAW_DOSES, potionId);
				int perPotion = client.getIntStack()[0];
				if (doses <= 0 || perPotion <= 0)
				{
					continue;
				}
				// The potion's item for each number of doses
				EnumComposition byDoses = client.getEnum(potionId);
				int full = byDoses.getIntValue(perPotion);
				if (full > 0 && doses >= perPotion)
				{
					items.merge(itemManager.canonicalize(full), doses / perPotion, Integer::sum);
				}
				int rest = doses % perPotion;
				if (rest > 0 && byDoses.getIntValue(rest) > 0)
				{
					items.merge(itemManager.canonicalize(byDoses.getIntValue(rest)), 1, Integer::sum);
				}
			}
		}
		potionStorageItems = items;
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_POTIONS, encodeItems(items));
		mergeBank();
	}

	/** The bank's items with its potion storage. */
	private void mergeBank()
	{
		if (bankContainerItems == null)
		{
			bankItems = null;
			return;
		}
		Map<Integer, Integer> all = new HashMap<>(bankContainerItems);
		potionStorageItems.forEach((id, n) -> all.merge(id, n, Integer::sum));
		bankItems = all;
	}

	private void scanSeedVault(ItemContainer vault)
	{
		seedVaultItems = countItems(vault, new long[1]);
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_SEED_VAULT, encodeItems(seedVaultItems));
	}

	/**
	 * Item quantities by canonical id, leaving out placeholders and coins (added to {@code coins[0]}); an empty map for
	 * a missing container.
	 */
	private Map<Integer, Integer> countItems(ItemContainer container, long[] coins)
	{
		Map<Integer, Integer> items = new HashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			if (item.getId() == ItemID.COINS)
			{
				coins[0] += item.getQuantity();
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(item.getId());
			if (comp.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			items.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
		}
		return items;
	}

	/** Everything the player has, where it is. */
	private Holdings holdings()
	{
		return new Holdings()
			.with(Holdings.Place.WORN, countItems(client.getItemContainer(InventoryID.WORN), new long[1]))
			.with(Holdings.Place.INVENTORY, countItems(client.getItemContainer(InventoryID.INV), new long[1]))
			.with(Holdings.Place.BANK, bankItems)
			.with(Holdings.Place.SEED_VAULT, seedVaultItems);
	}

	private static String encodeItems(Map<Integer, Integer> items)
	{
		return items.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
	}

	/** Null if there's nothing saved or it can't be read. */
	private static Map<Integer, Integer> decodeItems(String encoded)
	{
		if (encoded == null)
		{
			return null;
		}
		Map<Integer, Integer> items = new HashMap<>();
		try
		{
			for (String entry : encoded.split(","))
			{
				if (!entry.isEmpty())
				{
					String[] parts = entry.split(":");
					items.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
				}
			}
			return items;
		}
		catch (NumberFormatException | ArrayIndexOutOfBoundsException e)
		{
			log.debug("Ignoring an unreadable item snapshot", e);
			return null;
		}
	}

	private void loadBank()
	{
		bankContainerItems = decodeItems(configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_BANK));
		Map<Integer, Integer> potions = decodeItems(configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_POTIONS));
		potionStorageItems = potions == null ? Collections.emptyMap() : potions;
		mergeBank();
		String coins = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_BANK_COINS);
		try
		{
			bankCoins = bankItems == null || coins == null ? 0 : Long.parseLong(coins);
		}
		catch (NumberFormatException e)
		{
			bankCoins = 0;
		}
		seedVaultItems = decodeItems(configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_SEED_VAULT));
	}
}
