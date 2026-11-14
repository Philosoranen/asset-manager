package com.assetmanager.ui;

import com.assetmanager.coffer.CofferPlan;
import com.assetmanager.death.DeathCostEstimate;
import com.assetmanager.death.DeathScenario;
import com.assetmanager.dest.Destination;
import com.assetmanager.dest.Destinations;
import com.assetmanager.dest.GeOrder;
import com.assetmanager.flip.Flip;
import com.assetmanager.flip.FlipRun;
import com.assetmanager.flip.ShopRates;
import com.assetmanager.flip.FlipTime;
import com.assetmanager.flip.FlipSort;
import com.assetmanager.flip.Flips;
import com.assetmanager.keep.Holdings;
import com.assetmanager.keep.KeepRules;
import com.assetmanager.shop.Shop;
import com.assetmanager.shop.ShopOffer;
import com.assetmanager.shop.ShopPerks;
import com.assetmanager.shop.ShopStock;
import com.assetmanager.shop.ShopTimes;
import com.assetmanager.value.Patience;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/**
 * The side panel, in two tabs. Liquidate: a summary of your coffer and death costs, then where your spare items should
 * go. Flipping: things worth buying in one place to cash out in another, how long the player will wait for GE offers
 * set above them. Each destination
 * is one row (Death's coffer, high alching, the GE, each shop, kept items) with how many items and what they're worth;
 * clicking it shows those items in the bank, where the tooltips have the per-item detail.
 * <p>
 * Everything is sized to the panel's width so it never scrolls sideways, and sections collapse by clicking their
 * headers.
 */
public class AssetManagerPanel extends PluginPanel
{
	// Widths for wrapped HTML text, in CSS pixels. Swing renders these about 1.3x wider, so they're sized to fit
	// a section card in the 225px panel, after the padding, the card's edge and the scrollbar.
	/** Full-width text. */
	private static final int TEXT_WIDTH = 138;
	/** A name with a value to its right. */
	private static final int NAME_WIDTH = 100;
	/** The grey detail line under a row's name. */
	private static final int DETAIL_WIDTH = 128;
	private static final Color GOOD = new Color(0x4CAF50);
	private static final Color WARN = new Color(0xFF9800);
	private static final Color BAD = new Color(0xF44336);
	private static final Color MUTED = new Color(0x9E9E9E);
	/** A section's card, darker than the panel so sections stand apart. */
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	/** A card's title bar. */
	private static final Color CARD_HEADER = new Color(0x171717);
	/** A row inside a card, lighter than the card. */
	private static final Color ROW = new Color(0x2C2C2C);
	private static final Color ROW_HOVER = new Color(0x3A3A3A);
	private static final Color LINE = new Color(0x3C3C3C);
	/** An inventory slot, as wide as the game's, and how many fit across a flip row, as in the inventory. */
	private static final int SLOT_WIDTH = 36;
	private static final int SLOT_HEIGHT = 32;
	private static final int SLOTS_ACROSS = 4;
	private static final Color SLOT = new Color(0x3E3529);
	private static final String GOOD_HTML = "#4CAF50";

	private static final String SECTION_KEPT = "Kept items";

	public enum Tab
	{
		LIQUIDATE("Liquidate"),
		FLIPPING("Flipping");

		private final String label;

		Tab(String label)
		{
			this.label = label;
		}
	}

	private final JPanel content = new WidthTrackingPanel();
	private final JScrollPane scroll = new JScrollPane(content);
	private Tab tab = Tab.LIQUIDATE;
	/** Where each tab was scrolled to, so switching back doesn't lose your place. */
	private final int[] scrollPositions = new int[Tab.values().length];
	// The Flipping tab's choices sit outside the content, so a refresh doesn't close one while it's open
	/** Where flips cash out. */
	private final JPanel destRow = new JPanel(new BorderLayout(4, 0));
	private final JComboBox<Flip.Dest> destBox = new JComboBox<>();
	/** The places the account can cash out, as the dest box lists them. */
	private List<Flip.Dest> destChoices = new ArrayList<>();
	/** What the player just picked, until the flips have been worked out for it; null when they have. */
	private Flip.Dest pickedDest;
	/** Set while the dest box is being refilled, which isn't the player choosing. */
	private boolean fillingDests;
	/** How long the player will wait for GE offers. */
	private final JPanel patienceRow = new JPanel(new BorderLayout(4, 0));
	/** How flips are ordered. */
	private final JPanel sortRow = new JPanel(new BorderLayout(4, 0));
	/** Asks for the coffer balance, in the chatbox. */
	private final Runnable onSetBalance;
	/** (item id, keep amount); 0 removes the rule. */
	private final Consumer<Destination> onShowInBank;
	private final Consumer<GeOrder> onGeOrder;
	private final Consumer<Boolean> onAvoidWilderness;
	private final Consumer<Boolean> onEvenHops;
	/** Puts an item's icon, with a stack size, on a label. */
	private final ItemIcons itemIcons;
	/** Find a shop: centre the map on it and point the hint arrow at it; again to stop. */
	private final Consumer<Destination> onFind;
	/** Find a shop to buy from, the same way. */
	private final Consumer<Shop> onFindShop;
	/** The shop being found, or null. */
	private String findingShop;
	private final Set<String> collapsed = new HashSet<>();
	/** Whether the content is behind the last state, having changed while the panel was hidden. */
	private boolean stale = true;
	/** Where rows are added: the content, or the body of the card being built. */
	private JPanel target = content;
	private PanelState lastState;
	private int warnDeaths = 2;

	public AssetManagerPanel(Runnable onSetBalance, Consumer<Destination> onShowInBank,
		Consumer<Destination> onFind, Consumer<Shop> onFindShop, Patience patience, Consumer<Patience> onPatience,
		Consumer<Flip.Dest> onDest, FlipSort sort, Consumer<FlipSort> onSort, Consumer<GeOrder> onGeOrder,
		Consumer<Boolean> onAvoidWilderness, Consumer<Boolean> onEvenHops, ItemIcons itemIcons)
	{
		super(false);
		this.itemIcons = itemIcons;
		this.onEvenHops = onEvenHops;
		this.onGeOrder = onGeOrder;
		this.onAvoidWilderness = onAvoidWilderness;
		this.onFindShop = onFindShop;
		this.onSetBalance = onSetBalance;
		this.onShowInBank = onShowInBank;
		this.onFind = onFind;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(8, 8, 0, 8));
		JLabel title = new JLabel("Asset Manager");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.NORTH);
		MaterialTabGroup tabs = new MaterialTabGroup();
		tabs.setLayout(new GridLayout(1, 0, 6, 0));
		for (Tab t : Tab.values())
		{
			MaterialTab mt = new MaterialTab(t.label, tabs, null);
			mt.setOnSelectEvent(() ->
			{
				selectTab(t);
				return true;
			});
			tabs.addTab(mt);
		}
		tabs.select(tabs.getTab(tab.ordinal()));
		header.add(tabs, BorderLayout.CENTER);

		destRow.setOpaque(false);
		destRow.setBorder(new EmptyBorder(0, 0, 2, 0));
		destBox.setFont(FontManager.getRunescapeSmallFont());
		destBox.setFocusable(false);
		destBox.setToolTipText("Where to cash out what you buy: alch it, sell it on the GE or to a shop, or put it in Death's coffer");
		destBox.addActionListener(e ->
		{
			if (!fillingDests && destBox.getSelectedItem() != null)
			{
				pickedDest = (Flip.Dest) destBox.getSelectedItem();
				onDest.accept(pickedDest);
			}
		});
		destRow.add(controlLabel("Cash out:"), BorderLayout.WEST);
		destRow.add(destBox, BorderLayout.CENTER);
		destRow.setVisible(false);

		patienceRow.setOpaque(false);
		patienceRow.setBorder(new EmptyBorder(0, 0, 2, 0));
		JLabel waitLabel = controlLabel("GE offers:");
		JComboBox<Patience> wait = new JComboBox<>(Patience.values());
		wait.setSelectedItem(patience);
		wait.setFont(FontManager.getRunescapeSmallFont());
		wait.setFocusable(false);
		wait.setToolTipText("How long you'll wait for GE offers to fill. Waiting gets better prices and lets more trade.");
		wait.addActionListener(e -> onPatience.accept((Patience) wait.getSelectedItem()));
		patienceRow.add(waitLabel, BorderLayout.WEST);
		patienceRow.add(wait, BorderLayout.CENTER);
		patienceRow.setVisible(false);
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setOpaque(false);
		controls.add(destRow);
		controls.add(patienceRow);
		sortRow.setOpaque(false);
		sortRow.setBorder(new EmptyBorder(0, 0, 4, 0));
		JComboBox<FlipSort> sortBox = new JComboBox<>(FlipSort.values());
		sortBox.setSelectedItem(sort);
		sortBox.setFont(FontManager.getRunescapeSmallFont());
		sortBox.setFocusable(false);
		sortBox.setToolTipText("Total profit makes the most money. Return makes the most of each coin spent, which matters for"
			+ " Death's coffer, where the coins are gone.");
		sortBox.addActionListener(e -> onSort.accept((FlipSort) sortBox.getSelectedItem()));
		sortRow.add(controlLabel("Sort by:"), BorderLayout.WEST);
		sortRow.add(sortBox, BorderLayout.CENTER);
		sortRow.setVisible(false);
		controls.add(sortRow);
		header.add(controls, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(0, 8, 8, 8));

		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		add(scroll, BorderLayout.CENTER);

		update(null, warnDeaths);
	}

	/** Which shop is being found, to show on its row. */
	public void setFindingShop(String shop)
	{
		findingShop = shop;
		update(lastState, warnDeaths);
	}

	private void selectTab(Tab t)
	{
		if (t == tab)
		{
			return;
		}
		scrollPositions[tab.ordinal()] = scroll.getVerticalScrollBar().getValue();
		tab = t;
		update(lastState, warnDeaths);
		int position = scrollPositions[t.ordinal()];
		// Restore after the new content has been laid out, or the scroll bar's range is still the old tab's
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	/**
	 * Shows new state. The panel is only rebuilt while it's on screen and when something on it changed: rebuilding
	 * is slow (all its text is HTML), and the state is published on every inventory change.
	 */
	public void setState(PanelState state, int warnDeaths)
	{
		if (stale || !Objects.equals(state, lastState) || warnDeaths != this.warnDeaths)
		{
			lastState = state;
			this.warnDeaths = warnDeaths;
			stale = true;
			if (isShowing())
			{
				update(state, warnDeaths);
			}
		}
	}

	@Override
	public void onActivate()
	{
		if (stale)
		{
			update(lastState, warnDeaths);
		}
	}

	private void update(PanelState state, int warnDeaths)
	{
		stale = false;
		this.lastState = state;
		this.warnDeaths = warnDeaths;
		content.removeAll();
		boolean flipping = tab == Tab.FLIPPING && state != null;
		if (state != null)
		{
			fillDests(state);
		}
		destRow.setVisible(flipping && destChoices.size() > 1);
		sortRow.setVisible(flipping);
		// Patience only matters for the GE
		patienceRow.setVisible(flipping && state.getAccountMode().canUseGrandExchange());

		if (state == null)
		{
			addRow(text(tab == Tab.LIQUIDATE ? "Log in to see your death costs." : "Log in to see shop deals.", Color.LIGHT_GRAY));
		}
		else if (tab == Tab.LIQUIDATE)
		{
			addGap(8);
			addDeathCard(state);
			addSpareCard(state);
			addShopsCard(state);
			addKeptCard(state);
		}
		else
		{
			addFlipsSection(state);
		}

		content.revalidate();
		content.repaint();
	}

	// --- Sections ---

	/** The coffer, its target and what dying now would cost, together: the coffer is there to pay for deaths. */
	private void addDeathCard(PanelState state)
	{
		boolean coffer = state.getAccountMode().canUseCoffer();
		card(coffer ? "Death's coffer" : "If you die now", () ->
		{
			if (coffer)
			{
				addCofferSummary(state);
				divider();
				minorHeading("If you die now");
			}
			else
			{
				addRow(text("Ultimate ironmen can't use Death's coffer.", MUTED));
			}
			addRiskSummary(state);
		});
	}

	private void addCofferSummary(PanelState state)
	{
		boolean known = state.isCofferKnown();
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		JLabel balance = new JLabel(known ? QuantityFormatter.formatNumber(state.getCofferBalance()) + " gp" : "Balance unknown");
		balance.setFont(FontManager.getRunescapeBoldFont());
		balance.setForeground(known ? Color.WHITE : WARN);
		top.add(balance, BorderLayout.CENTER);
		JButton set = smallButton(known ? "Edit" : "Set", "Enter your coffer balance by hand");
		set.addActionListener(e -> onSetBalance.run());
		top.add(set, BorderLayout.EAST);
		addRow(top);
		addRow(text(known ? "From " + state.getCofferSource() + ", " + ago(state.getCofferUpdatedAt())
			: "Open the coffer at Death's Office, or click Set.", MUTED));

		long deaths = state.getDeathsCovered();
		DeathCostEstimate est = state.getEstimate();
		if (deaths >= 0 && est != null)
		{
			String covered = deaths == Long.MAX_VALUE ? "Dying is free in your current gear"
				: "Covers <b>" + deaths + "</b> death" + (deaths == 1 ? "" : "s") + " at " + gp(est.getScenarioFee()) + " each";
			addRow(text(covered, deaths < warnDeaths ? BAD : GOOD));
		}

		addGap(6);
		long target = state.getCofferTarget();
		CofferPlan plan = state.getPlan();
		boolean met = plan != null && plan.getShortfall() <= 0;
		if (known && target > 0)
		{
			Meter meter = new Meter((double) state.getCofferBalance() / target, met ? GOOD : WARN);
			meter.setToolTipText(QuantityFormatter.formatNumber(state.getCofferBalance()) + " of a "
				+ QuantityFormatter.formatNumber(target) + " gp target");
			addRow(meter);
			addGap(3);
		}
		addRow(valueRow("Target", gp(target), Color.WHITE));
		addRow(text(targetBasis(state), MUTED));
		if (plan != null)
		{
			if (met)
			{
				addRow(text("Your coffer meets the target.", GOOD));
			}
			else
			{
				addRow(valueRow("Short by", gp(plan.getShortfall()), WARN));
				if (!plan.getSteps().isEmpty())
				{
					addRow(text("Sacrifice the items under Death's coffer in Your spare items"
						+ (plan.getCoinsToDeposit() > 0 ? ", then deposit " + gp(plan.getCoinsToDeposit()) : "") + ".", MUTED));
				}
				else if (plan.getCoinsToDeposit() > 0)
				{
					addRow(text("Deposit " + gp(plan.getCoinsToDeposit()) + " to reach it.", MUTED));
				}
				if (plan.getRemainingShortfall() > 0)
				{
					addRow(text("Still short " + gp(plan.getRemainingShortfall()) + " after that.", BAD));
				}
			}
		}
	}

	private void addRiskSummary(PanelState state)
	{
		DeathCostEstimate est = state.getEstimate();
		if (est == null)
		{
			addRow(text("Waiting for inventory and equipment...", MUTED));
			return;
		}

		String status = "Keeping " + est.getKeepCount() + " item" + (est.getKeepCount() == 1 ? "" : "s")
			+ (state.isSkulled() ? " · <font color='#F44336'>skulled</font>" : "")
			+ (state.isProtectItem() ? " · Protect Item" : "")
			+ (est.isIronDiscount() ? " · iron 50% off" : "");
		addRow(text(status, Color.LIGHT_GRAY));
		addRow(valueRow("To reclaim", gp(est.getValueAtRisk()), Color.WHITE));
		DeathScenario sc = est.getScenario();
		if (sc != DeathScenario.GRAVESTONE && sc != DeathScenario.DEATHS_OFFICE)
		{
			// At a boss with its own retrieval service, that's what a death costs
			addRow(valueRow(sc.getDisplayName() + " retrieval", gp(est.getScenarioFee()), feeColor(est.getScenarioFee())));
			addRow(text("You're at " + sc.getDisplayName() + ", so items go to its retrieval service.", MUTED));
		}
		else
		{
			addRow(valueRow("Gravestone fee", gp(est.getGravestoneFee()), feeColor(est.getGravestoneFee())));
			addRow(valueRow("If the grave expires", gp(est.getDeathsOfficeFee()), feeColor(est.getDeathsOfficeFee())));
		}
	}

	/** Where spare items go other than shops: the coffer, alching and the GE, and what's kept. */
	private void addSpareCard(PanelState state)
	{
		card("Your spare items", () ->
		{
			Destinations dests = state.getDestinations();
			if (dests == null)
			{
				addRow(text("Open your bank once so it can be scanned.", WARN));
				return;
			}
			addRow(text("Click one to see its items in your bank, most valuable first.", MUTED));
			addGap(2);

			destinationRow(dests.get(Destination.Kind.ALL), state);
			if (state.getAccountMode().canUseCoffer())
			{
				destinationRow(dests.get(Destination.Kind.COFFER), state);
			}
			if (state.getAccountMode().canUseGrandExchange())
			{
				destinationRow(dests.get(Destination.Kind.ALCH), state);
				destinationRow(dests.get(Destination.Kind.GE), state);
			}
		});
	}

	/** One line per shop whose items others have overstocked everywhere, with the items in its tooltip. */
	private void addBusyShops(List<ShopStock.Busy> busy)
	{
		Map<String, List<ShopStock.Busy>> byShop = new LinkedHashMap<>();
		busy.forEach(b -> byShop.computeIfAbsent(b.getShop(), k -> new ArrayList<>()).add(b));
		byShop.forEach((shop, list) ->
		{
			long until = list.stream().mapToLong(ShopStock.Busy::getUntil).max().orElse(0);
			JLabel l = text("Skipping " + shop + " for " + items(list.size()) + " until about " + ShopTimes.clock(until)
				+ ": others overstocked " + (list.size() == 1 ? "it" : "them") + ".", WARN);
			StringBuilder tip = new StringBuilder("<html>Overstocked by other players on the worlds you checked:");
			list.forEach(b -> tip.append("<br>").append(b.getItem()).append(" (").append(b.getWorlds()).append(" worlds, until about ")
				.append(ShopTimes.clock(b.getUntil())).append(')'));
			l.setToolTipText(tip.append("</html>").toString());
			addRow(l);
		});
	}

	/** The shops worth a trip with spare items, best first. */
	private void addShopsCard(PanelState state)
	{
		Destinations dests = state.getDestinations();
		if (dests == null)
		{
			return;
		}
		card("Shops", () ->
		{
			boolean ge = state.getAccountMode().canUseGrandExchange();
			ShopPerks perks = state.getPerks();
			List<Destination> shops = dests.getShops();
			addRow(text(shops.isEmpty() ? "No shop pays more than " + (ge ? "the GE or alching" : "alching") + " for your spare items."
				: "Worth a trip with your spare items, most gained first. They're marked on your world map.", MUTED));
			addRow(wildernessToggle(perks.isAvoidWilderness()));
			addGap(2);
			for (Destination shop : shops)
			{
				destinationRow(shop, state);
			}
			addBusyShops(state.getBusyShops());
			addGap(2);
			addRow(text("Karamja gloves: " + (perks.getGlovesTier() >= 3 ? "3 or 4" : perks.getGlovesTier() >= 1 ? "1 or 2" : "none")
				+ " · Ring of Charos (a): " + (perks.isCharos() ? "yes" : "no"), MUTED));
		});
	}

	/** Lists the places the account can cash out, when they change, with the one in use selected. */
	private void fillDests(PanelState state)
	{
		List<Flip.Dest> can = Flips.destinations(state.getAccountMode());
		if (pickedDest != null && (state.getFlipDest() == pickedDest || !can.contains(pickedDest)))
		{
			pickedDest = null;
		}
		// Until the flips catch up with the player's pick, a refresh shouldn't put the old choice back
		Flip.Dest show = pickedDest != null ? pickedDest : state.getFlipDest();
		if (can.equals(destChoices) && show == destBox.getSelectedItem())
		{
			return;
		}
		fillingDests = true;
		destBox.removeAllItems();
		can.forEach(destBox::addItem);
		destBox.setSelectedItem(show);
		fillingDests = false;
		destChoices = can;
	}

	private static JLabel controlLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.LIGHT_GRAY);
		l.setPreferredSize(new Dimension(52, l.getPreferredSize().height));
		return l;
	}

	private void addFlipsSection(PanelState state)
	{
		addGap(6);
		if (!state.isFlipsEnabled())
		{
			addRow(text("Turn on Show flips in the settings to list things worth buying in one place and cashing out in another.",
				Color.LIGHT_GRAY));
			return;
		}
		List<String> notes = new ArrayList<>();
		Flip.Dest flipDest = state.getFlipDest();
		if (state.getAccountMode().canUseGrandExchange() && flipDest != Flip.Dest.GE && !state.isGeFlips())
		{
			notes.add("<font color='#FF9800'>" + (flipDest == Flip.Dest.SHOP ? "Selling to shops is for what you buy on the GE, so it"
				: "Including what you could buy on the GE") + " needs Live GE prices on in the settings (if they're on, prices are"
				+ " still loading).</font>");
		}
		if (flipDest == Flip.Dest.GE)
		{
			notes.add("Things to buy from shops and sell on the GE.");
		}
		if (flipDest == Flip.Dest.SHOP)
		{
			addRow(evenHopsToggle(state.isEvenHops()));
		}
		List<FlipRun> flips = state.getFlips();
		if (state.getFlipCoins() <= 0)
		{
			addRow(text("Carry some coins to see flips. They only spend the coins in your inventory, so take out as much as you"
				+ " want to flip with.", Color.LIGHT_GRAY));
		}
		else if (flips.isEmpty())
		{
			addRow(text("Nothing is worth flipping right now with the " + gp(state.getFlipCoins()) + " in your inventory.",
				Color.LIGHT_GRAY));
		}
		else
		{
			JLabel intro = text("Spending up to the " + gp(state.getFlipCoins()) + " in your inventory, in shops you can use."
				+ " Hover an item for its prices.", MUTED);
			intro.setToolTipText("<html>Each row is one go: a shop visit with everything worth it there, or one round of GE"
				+ " offers.<br>Times are rough: hops, trips, conversations, alchs and the GE's wait.</html>");
			addRow(intro);
		}
		for (String note : notes)
		{
			addRow(text(note, MUTED));
		}
		addGap(4);
		flips.forEach(f -> flipRow(f, state.getFlipSort()));
	}

	/**
	 * One go: where, what it makes, its items as they'd sit in the inventory (hover one for its prices), a line of
	 * the shop's rates and the go's time, and a button showing the shop on the map.
	 */
	private void flipRow(FlipRun run, FlipSort sort)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(5, 6, 6, 6));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		Shop at = run.getShop();
		String route = run.getSource().getLabel() + " to " + run.getDest().getLabel()
			+ (at != null && at.getLocation() != null && !at.getLocation().isEmpty() ? " &middot; " + at.getLocation() : "");
		top.add(wrapped(capitalise(run.getName()) + "<br><font color='#FF9800'>" + route + "</font>", NAME_WIDTH, Color.WHITE),
			BorderLayout.CENTER);
		JPanel east = new JPanel();
		east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
		east.setOpaque(false);
		// What it's sorted by goes first, in green
		JLabel profit = new JLabel("+" + gp(run.getProfit()));
		profit.setToolTipText("What one go makes, " + gp(run.getCost()) + " spent");
		JLabel hourly = new JLabel("+" + QuantityFormatter.quantityToStackSize(Math.round(run.getGpPerHour())) + "/hr");
		hourly.setToolTipText("About what it makes an hour, at about " + FlipTime.describe(run.getSeconds()) + " a go");
		JLabel ret = new JLabel("+" + percent(run.getReturnPercent()));
		ret.setToolTipText("Return: what it makes for each coin spent, " + gp(run.getCost()) + " in all");
		JLabel first = sort == FlipSort.RETURN ? ret : sort == FlipSort.PROFIT ? profit : hourly;
		JLabel second = sort == FlipSort.PROFIT ? hourly : profit;
		for (JLabel l : new JLabel[]{first, second})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(l == first ? GOOD : MUTED);
			l.setAlignmentX(Component.RIGHT_ALIGNMENT);
			east.add(l);
		}
		top.add(east, BorderLayout.EAST);
		top.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(top);

		row.add(Box.createVerticalStrut(4));
		JPanel slots = itemSlots(run);
		slots.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(slots);
		row.add(Box.createVerticalStrut(4));

		JLabel stats = wrapped(runStats(run), DETAIL_WIDTH, MUTED);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(stats);
		Set<String> unchecked = new LinkedHashSet<>();
		run.getFlips().forEach(f -> unchecked.addAll(f.getUnchecked()));
		if (!unchecked.isEmpty())
		{
			JLabel needs = wrapped("Also needs: " + String.join("; ", unchecked), DETAIL_WIDTH, WARN);
			needs.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.add(needs);
		}

		Shop shop = run.getShop();
		if (shop != null)
		{
			JButton map = mapButton(shop.getName().equals(findingShop), () -> onFindShop.accept(shop));
			JPanel buttons = new JPanel(new BorderLayout());
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(4, 0, 0, 0));
			buttons.add(map, BorderLayout.EAST);
			buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.add(buttons);
		}
		addRow(row);
	}

	/** The go's items as inventory slots, with how many on each; hover one for what it costs and fetches. */
	private JPanel itemSlots(FlipRun run)
	{
		JPanel grid = new JPanel(new GridLayout(0, SLOTS_ACROSS, 2, 2));
		grid.setOpaque(false);
		for (Flip f : run.getFlips())
		{
			JLabel slot = new StackLabel(f.getQuantity());
			slot.setHorizontalAlignment(JLabel.CENTER);
			slot.setOpaque(true);
			slot.setBackground(SLOT);
			slot.setPreferredSize(new Dimension(SLOT_WIDTH, SLOT_HEIGHT));
			slot.setToolTipText(itemTip(f));
			// One icon per item, whatever the quantity, so the game isn't asked to draw a new one each time it changes
			itemIcons.addTo(slot, f.getItemId());
			grid.add(slot);
		}
		// Keeps the slots their size when there are fewer than a row
		for (int n = run.getFlips().size(); n < SLOTS_ACROSS; n++)
		{
			grid.add(Box.createRigidArea(new Dimension(SLOT_WIDTH, SLOT_HEIGHT)));
		}
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.add(grid, BorderLayout.WEST);
		return wrap;
	}

	private static String itemTip(Flip f)
	{
		StringBuilder t = new StringBuilder("<html><b>").append(QuantityFormatter.formatNumber(f.getQuantity())).append(" x ")
			.append(f.getItem()).append("</b><br>Buy for ").append(gp(f.getFirstCost())).append(f.isPriceRises() ? ", rising" : "")
			.append("<br>").append(sellVerb(f.getDest())).append(' ').append(gp(f.getFirstValue()))
			.append(f.getSellOffer() != null && f.getSellOffer().getWorlds() > 1 ? ", falling" : "")
			.append("<br>Profit +").append(gp(f.getProfit()))
			.append("<br>Stopped by ").append(f.getLimit().getDescription());
		if (f.getGeTrades() != Long.MAX_VALUE)
		{
			t.append(" (").append(QuantityFormatter.formatNumber(f.getGeTrades())).append(" trade on the GE in that time)");
		}
		Shop.Item item = f.getBuyShop() == null ? null : f.getBuyShop().getItems().get(f.getItem());
		int[] bundle = item == null ? null : item.getB();
		if (bundle != null && bundle.length == 2)
		{
			t.append("<br>Offer ").append(gp(bundle[1])).append(" for ").append(bundle[0]).append(" to get that price");
		}
		return t.append("</html>").toString();
	}

	private static String sellVerb(Flip.Dest dest)
	{
		switch (dest)
		{
			case COFFER:
				return "Coffer adds";
			case ALCH:
				return "Alchs for";
			case GE:
				return "Sells on the GE for";
			default:
				return "Shop pays";
		}
	}

	/** One line about the shop and the go: its rates, what to wear, hops and time. */
	private static String runStats(FlipRun run)
	{
		List<String> parts = new ArrayList<>();
		Shop shop = run.getShop();
		boolean talk = shop != null && shop.getTalk() != null;
		if (run.getDest() == Flip.Dest.SHOP && shop != null)
		{
			ShopOffer o = run.getFlips().get(0).getSellOffer();
			if (talk || o.isFixedPrice())
			{
				parts.add(talk ? "Unnoted only, 28 a trip" : "Fixed price");
			}
			else
			{
				parts.add("Pays " + tenths(o.getRate()) + (shop.getDelta() > 0 ? ", " + tenths(shop.getDelta()) + " less each" : ""));
			}
			if (o.isUsesKaramjaGloves())
			{
				parts.add("wear Karamja gloves");
			}
			if (o.isUsesCharos())
			{
				parts.add("wear Ring of Charos (a)");
			}
		}
		else if (run.getSource() == Flip.Source.SHOP && shop != null)
		{
			ShopRates r = ShopRates.of(shop, false);
			parts.add(talk ? "Bought by talking" : "Sells at " + tenths(r.getSell())
				+ (r.getDelta() > 0 ? ", " + tenths(r.getDelta()) + " more each" : ""));
		}
		if (run.getWorlds() > 1)
		{
			parts.add(run.getWorlds() + " worlds");
		}
		parts.add("~" + FlipTime.describe(run.getSeconds()));
		if (run.getEqualShops() > 0)
		{
			parts.add("or " + run.getEqualShops() + " shops like it");
		}
		return String.join(" &middot; ", parts);
	}

	/** Tenths of a percent as a percentage: 25 -> "2.5%". */
	private static String tenths(int t)
	{
		return t % 10 == 0 ? (t / 10) + "%" : (t / 10) + "." + Math.abs(t % 10) + "%";
	}

	/** Shows a shop on the world map and points the hint arrow at it; again to stop. */
	private static JButton mapButton(boolean showing, Runnable onClick)
	{
		JButton b = smallButton(showing ? "Stop" : "Show on map", showing ? "Stop pointing the way to this shop"
			: "Mark this shop on your world map (it's centred there when you open the map) and point the hint arrow at it");
		b.addActionListener(e -> onClick.run());
		return b;
	}

	/** 23.4 -> "23%", 0.42 -> "0.4%", 1234 -> "1,234%" */
	private static String percent(double p)
	{
		return p < 10 ? String.format("%.1f%%", p) : QuantityFormatter.formatNumber(Math.round(p)) + "%";
	}

	/** "bagged willow tree" -> "Bagged willow tree" */
	private static String capitalise(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** One destination: name and value, a detail line, and a click to show its items in the bank. */
	private void destinationRow(Destination d, PanelState state)
	{
		if (d == null)
		{
			return;
		}
		boolean empty = d.getCount() == 0;
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ROW);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		top.add(wrapped(d.getName(), NAME_WIDTH, empty ? MUTED : Color.WHITE), BorderLayout.CENTER);
		if (!empty)
		{
			JLabel value = new JLabel(gp(d.getValue()));
			value.setFont(FontManager.getRunescapeSmallFont());
			value.setForeground(Color.WHITE);
			value.setVerticalAlignment(JLabel.TOP);
			value.setAlignmentX(Component.RIGHT_ALIGNMENT);
			JPanel east = new JPanel();
			east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
			east.setOpaque(false);
			east.add(value);
			if (d.getKind() == Destination.Kind.GE)
			{
				JButton order = geOrderButton(state);
				order.setAlignmentX(Component.RIGHT_ALIGNMENT);
				east.add(Box.createVerticalStrut(2));
				east.add(order);
			}
			top.add(east, BorderLayout.EAST);
		}
		row.add(top, BorderLayout.CENTER);
		JLabel detail = wrapped(empty ? emptyText(d) : detail(d, state), DETAIL_WIDTH, MUTED);
		if (!empty && d.getKind() == Destination.Kind.SHOP && d.getShop() != null)
		{
			// Under the details, so the shop's name keeps the row's width
			JPanel south = new JPanel(new BorderLayout());
			south.setOpaque(false);
			south.add(detail, BorderLayout.CENTER);
			JPanel buttons = new JPanel(new BorderLayout());
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(4, 0, 0, 0));
			buttons.add(mapButton(d.getName().equals(findingShop), () -> onFind.accept(d)), BorderLayout.EAST);
			south.add(buttons, BorderLayout.SOUTH);
			row.add(south, BorderLayout.SOUTH);
		}
		else
		{
			row.add(detail, BorderLayout.SOUTH);
		}

		if (!empty)
		{
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.setToolTipText("Show " + (d.getKind() == Destination.Kind.SHOP ? "what to sell to " + d.getName()
				: "these items") + " in your bank");
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onShowInBank.accept(d);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					row.setBackground(ROW_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setBackground(ROW);
				}
			});
		}
		addRow(row);
	}

	/** The same setting as "Avoid Wilderness shops" in the plugin's settings, here so players see they can turn them off. */
	private JCheckBox wildernessToggle(boolean avoid)
	{
		JCheckBox box = new JCheckBox("Avoid Wilderness shops", avoid);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(Color.LIGHT_GRAY);
		box.setOpaque(false);
		box.setFocusPainted(false);
		box.setBorder(new EmptyBorder(1, 0, 1, 0));
		box.setToolTipText("Never suggest selling to shops in the Wilderness, like Bandit Duty Free, where other players can"
			+ " attack you. Ferox Enclave is a safe zone and still counts. Also in the plugin's settings.");
		box.addActionListener(e -> onAvoidWilderness.accept(box.isSelected()));
		return box;
	}

	/** Whether a go selling several items to a shop hops only as many worlds as its most profitable item takes. */
	private JCheckBox evenHopsToggle(boolean even)
	{
		JCheckBox box = new JCheckBox("Even hops", even);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(Color.LIGHT_GRAY);
		box.setOpaque(false);
		box.setFocusPainted(false);
		box.setBorder(new EmptyBorder(1, 0, 1, 0));
		box.setToolTipText("<html>On: hop no more worlds than the most profitable item takes to sell, so you're never hopping"
			+ "<br>just to sell one smaller item. Off: buy as many of each as any number of hops would sell"
			+ "<br>(up to Shop: max worlds to hop in the settings), for more profit but longer goes.</html>");
		box.addActionListener(e -> onEvenHops.accept(box.isSelected()));
		return box;
	}

	/** Switches the GE bank view between most valuable and quickest to sell first. */
	private JButton geOrderButton(PanelState state)
	{
		boolean quickest = state.getGeOrder() == GeOrder.QUICKEST;
		GeOrder next = quickest ? GeOrder.VALUE : GeOrder.QUICKEST;
		JButton b = smallButton(state.getGeOrder().getLabel(), quickest
			? "Shown quickest to sell first, to free the most bank spaces: click to show the most valuable first"
			: "Shown most valuable first: click to show what sells quickest first, to free the most bank spaces");
		if (!quickest && !state.isGeVolumes())
		{
			// Without trade volumes there's nothing to order by
			b.setEnabled(false);
			b.setToolTipText("Turn on Live GE prices in the settings to order these by how quickly they sell");
		}
		b.addActionListener(e -> onGeOrder.accept(next));
		return b;
	}

	private static String detail(Destination d, PanelState state)
	{
		List<String> parts = new ArrayList<>();
		boolean group = d.getKind() == Destination.Kind.SHOP && !d.getName().equals(d.getShop().getName());
		if (group)
		{
			parts.add("nearest: " + d.getShop().getName());
		}
		else if (d.getKind() == Destination.Kind.SHOP && d.getShop().getLocation() != null && !d.getShop().getLocation().isEmpty())
		{
			parts.add(d.getShop().getLocation());
		}
		parts.add(items(d.getCount()));
		String vs = state.getAccountMode().canUseGrandExchange() ? "the GE or alching" : "alching";
		switch (d.getKind())
		{
			case ALL:
				break;
			case COFFER:
				parts.add(state.getPlan() != null && state.getPlan().getShortfall() > 0 && !state.getPlan().getSteps().isEmpty()
					? "reaches your target" : "worth more there");
				break;
			case ALCH:
				vs = "the GE";
				break;
			case GE:
				parts.add("after tax");
				if (state.getGeOrder() == GeOrder.QUICKEST)
				{
					parts.add(state.isGeVolumes() ? "quickest to sell first" : "quickest to sell first once live prices load");
				}
				break;
			case KEPT:
				parts.add("in your bank");
				break;
			default:
				if (d.isNeedsGloves())
				{
					parts.add("wear Karamja gloves");
				}
				if (d.isNeedsCharos())
				{
					parts.add("wear Ring of Charos (a)");
				}
				if (d.getWorlds() > 1)
				{
					parts.add("hop " + d.getWorlds() + " worlds");
				}
				if (d.getEqualShops() > 0)
				{
					parts.add((group ? "any of " : "closest of ") + (d.getEqualShops() + 1) + " paying the same");
				}
				Long restocked = state.getRestockedBy().get(d.getShop().getName());
				if (restocked != null)
				{
					parts.add("worlds you saw overstocked are back to normal by " + ShopTimes.clock(restocked));
				}
		}
		d.getElsewhere().forEach((place, n) -> parts.add(n + " in " + (place == Holdings.Place.INVENTORY ? "your inventory" : "your " + place.getDisplayName())));
		if (d.getGain() > 0)
		{
			parts.add("<font color='" + GOOD_HTML + "'>+" + gp(d.getGain()) + "</font> vs "
				+ (d.getKind() == Destination.Kind.ALL ? "the next best way" : d.getKind() == Destination.Kind.COFFER ? "selling" : vs));
		}
		return String.join(" · ", parts);
	}

	private static String emptyText(Destination d)
	{
		switch (d.getKind())
		{
			case COFFER:
				return "Nothing needed right now";
			case KEPT:
				return "Right-click a bank item and choose Keep";
			default:
				return "Nothing right now";
		}
	}

	/**
	 * What's kept, as a row to click to see it in the bank. Keep rules are changed in the bank (right-click, Keep), so
	 * there's no list here: with hundreds of rules it was slow to build.
	 */
	private void addKeptCard(PanelState state)
	{
		card(SECTION_KEPT, () ->
		{
			Destinations dests = state.getDestinations();
			// A row like every other view's: click it to see the items in the bank
			destinationRow(dests == null ? null : dests.get(Destination.Kind.KEPT), state);
			addRow(text(state.getKeepRuleCount() == 0
				? "Right-click an item in your bank and choose Keep to set how many to leave alone."
				: "To change one, right-click it in your bank and choose Keep.", MUTED));
		});
	}

	private static String targetBasis(PanelState state)
	{
		DeathCostEstimate est = state.getEstimate();
		long perDeath = Math.max(est == null ? 0 : est.getScenarioFee(), state.getPeakFee());
		long fromDeaths = perDeath * state.getTargetDeaths();
		if (fromDeaths >= state.getMinCofferTarget() && perDeath > 0)
		{
			boolean fromPeak = est == null || state.getPeakFee() > est.getScenarioFee();
			return state.getTargetDeaths() + " deaths × " + gp(perDeath)
				+ (fromPeak ? " (your priciest setup in 14 days)" : "");
		}
		return "Your minimum target. Gear up once to plan for your real setup.";
	}

	// --- Dialogs ---

	// --- Building blocks ---

	/**
	 * Adds a section as a card: a title bar that collapses it when clicked, and, while it's open, whatever {@code body}
	 * adds.
	 */
	private void card(String name, Runnable body)
	{
		boolean open = !collapsed.contains(name);
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, open ? ColorScheme.BRAND_ORANGE : LINE));

		// Drawn rather than typed: the RuneScape font has no arrow characters, which some systems show as a stray glyph
		JLabel title = new JLabel(name, new Triangle(open, ColorScheme.BRAND_ORANGE), JLabel.LEADING);
		title.setIconTextGap(5);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(CARD_HEADER);
		bar.setBorder(new EmptyBorder(5, 6, 5, 6));
		bar.add(title, BorderLayout.CENTER);
		bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		bar.setToolTipText(open ? "Click to collapse" : "Click to expand");
		bar.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!collapsed.remove(name))
				{
					collapsed.add(name);
				}
				update(lastState, warnDeaths);
			}
		});
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(bar);

		if (open)
		{
			JPanel inner = new JPanel();
			inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
			inner.setOpaque(false);
			inner.setBorder(new EmptyBorder(6, 6, 8, 6));
			inner.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(inner);
			JPanel outer = target;
			target = inner;
			try
			{
				body.run();
			}
			finally
			{
				target = outer;
			}
		}
		addRow(card);
		addGap(8);
	}

	/** A thin line between parts of a card. */
	private void divider()
	{
		addGap(8);
		JPanel line = new JPanel();
		line.setBackground(LINE);
		line.setPreferredSize(new Dimension(0, 1));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		target.add(line);
		addGap(6);
	}

	/** A heading for a part of a card. */
	private void minorHeading(String name)
	{
		JLabel l = new JLabel(name);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
		addRow(l);
	}

	/** A small label grouping rows within a section. */
	private void subheader(String name)
	{
		JLabel l = new JLabel(name);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.LIGHT_GRAY);
		l.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		addRow(l);
	}

	/** A label and a value on one line. */
	private static JPanel valueRow(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		JLabel l = new JLabel(label);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.LIGHT_GRAY);
		JLabel v = new JLabel(value);
		v.setFont(FontManager.getRunescapeSmallFont());
		v.setForeground(valueColor);
		row.add(l, BorderLayout.CENTER);
		row.add(v, BorderLayout.EAST);
		return row;
	}

	private static JLabel text(String html, Color color)
	{
		JLabel l = wrapped(html, TEXT_WIDTH, color);
		l.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		return l;
	}

	private static JLabel wrapped(String html, int width, Color color)
	{
		JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + html + "</div></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	private static JButton smallButton(String label, String tooltip)
	{
		JButton b = new JButton(label);
		b.setToolTipText(tooltip);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setMargin(new Insets(0, 4, 0, 4));
		b.setFocusPainted(false);
		return b;
	}

	private void addGap(int px)
	{
		addRow((JComponent) Box.createVerticalStrut(px));
	}

	private void addRow(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		target.add(c);
		// Space between rows; cards are followed by their own gap
		if (c instanceof JPanel && c.isOpaque() && c.getBackground() != CARD)
		{
			JComponent gap = (JComponent) Box.createVerticalStrut(target == content ? 2 : 3);
			gap.setAlignmentX(Component.LEFT_ALIGNMENT);
			target.add(gap);
		}
	}

	private static Color feeColor(long fee)
	{
		return fee == 0 ? GOOD : Color.WHITE;
	}

	private static String items(int n)
	{
		return n + " item" + (n == 1 ? "" : "s");
	}

	private static String gp(long v)
	{
		return QuantityFormatter.quantityToStackSize(v) + " gp";
	}

	private static String ago(Instant t)
	{
		if (t == null)
		{
			return "unknown time";
		}
		long mins = Duration.between(t, Instant.now()).toMinutes();
		if (mins < 1)
		{
			return "just now";
		}
		if (mins < 60)
		{
			return mins + " min ago";
		}
		if (mins < 60 * 48)
		{
			return (mins / 60) + " h ago";
		}
		return (mins / 60 / 24) + " days ago";
	}

	/** Keeps content as wide as the scroll pane's viewport, so nothing can push it into sideways scrolling. */
	private static class WidthTrackingPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/** Puts an item's icon (no stack size) on a label. */
	public interface ItemIcons
	{
		void addTo(JLabel label, int itemId);
	}

	/** An item icon with its stack size in the top-left corner, coloured as the game does. */
	private static class StackLabel extends JLabel
	{
		private final int quantity;

		StackLabel(int quantity)
		{
			this.quantity = quantity;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (quantity <= 1)
			{
				return;
			}
			// As the game writes stacks: whole numbers up to 99,999, then thousands, then millions
			String text = quantity < 100_000 ? String.valueOf(quantity) : quantity < 10_000_000 ? quantity / 1_000 + "K"
				: quantity / 1_000_000 + "M";
			g.setFont(FontManager.getRunescapeSmallFont());
			int y = g.getFontMetrics().getAscent();
			g.setColor(Color.BLACK);
			g.drawString(text, 2, y + 1);
			g.setColor(quantity >= 10_000_000 ? new Color(0x00FF80) : quantity >= 100_000 ? Color.WHITE : Color.YELLOW);
			g.drawString(text, 1, y);
		}
	}

	/** A bar filled to how far the coffer is to its target. */
	private static class Meter extends JComponent
	{
		private static final int HEIGHT = 6;
		private final double fraction;
		private final Color color;

		Meter(double fraction, Color color)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.color = color;
			setPreferredSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(LINE);
			g.fillRect(0, 0, getWidth(), HEIGHT);
			g.setColor(color);
			g.fillRect(0, 0, (int) Math.round(getWidth() * fraction), HEIGHT);
		}
	}

	/** A small triangle pointing down when a section is open, right when it's collapsed. */
	private static class Triangle implements Icon
	{
		private static final int SIZE = 8;
		private final boolean down;
		private final Color color;

		Triangle(boolean down, Color color)
		{
			this.down = down;
			this.color = color;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			Polygon p = down
				? new Polygon(new int[]{x, x + SIZE, x + SIZE / 2}, new int[]{y + 2, y + 2, y + SIZE - 1}, 3)
				: new Polygon(new int[]{x + 2, x + SIZE - 1, x + 2}, new int[]{y, y + SIZE / 2, y + SIZE}, 3);
			g2.fill(p);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
