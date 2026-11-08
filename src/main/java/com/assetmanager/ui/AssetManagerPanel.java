package com.assetmanager.ui;

import com.assetmanager.coffer.CofferPlan;
import com.assetmanager.death.DeathCostEstimate;
import com.assetmanager.death.DeathScenario;
import com.assetmanager.dest.Destination;
import com.assetmanager.dest.Destinations;
import com.assetmanager.flip.Flip;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
	// the 225px panel after its padding and scrollbar.
	/** Full-width text. */
	private static final int TEXT_WIDTH = 150;
	/** A name with a value to its right. */
	private static final int NAME_WIDTH = 100;
	/** The grey detail line under a row's name. */
	private static final int DETAIL_WIDTH = 140;
	private static final Color GOOD = new Color(0x4CAF50);
	private static final Color WARN = new Color(0xFF9800);
	private static final Color BAD = new Color(0xF44336);
	private static final Color MUTED = new Color(0x9E9E9E);
	private static final String GOOD_HTML = "#4CAF50";

	private static final String SECTION_KEPT = "Manage kept items";

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
	private final LongConsumer onManualBalance;
	/** (item id, keep amount); 0 removes the rule. */
	private final BiConsumer<Integer, Integer> onKeepChange;
	private final Consumer<Destination> onShowInBank;
	/** Find a shop: centre the map on it and point the hint arrow at it; again to stop. */
	private final Consumer<Destination> onFind;
	/** Find a shop to buy from, the same way. */
	private final Consumer<Shop> onFindShop;
	/** The shop being found, or null. */
	private String findingShop;
	private final Set<String> collapsed = new HashSet<>();
	private PanelState lastState;
	private int warnDeaths = 2;

	public AssetManagerPanel(LongConsumer onManualBalance, BiConsumer<Integer, Integer> onKeepChange, Consumer<Destination> onShowInBank,
		Consumer<Destination> onFind, Consumer<Shop> onFindShop, Patience patience, Consumer<Patience> onPatience,
		Consumer<Flip.Dest> onDest, FlipSort sort, Consumer<FlipSort> onSort)
	{
		super(false);
		this.onFindShop = onFindShop;
		this.onManualBalance = onManualBalance;
		this.onKeepChange = onKeepChange;
		this.onShowInBank = onShowInBank;
		this.onFind = onFind;
		collapsed.add(SECTION_KEPT);

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

	public void update(PanelState state, int warnDeaths)
	{
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
			if (state.getAccountMode().canUseCoffer())
			{
				addCofferSection(state);
			}
			else
			{
				addRow(text("Ultimate ironmen can't use Death's coffer.", Color.LIGHT_GRAY));
			}
			addRiskSection(state);
			addDestinationsSection(state);
			addKeptSection(state);
		}
		else
		{
			addFlipsSection(state);
		}

		content.revalidate();
		content.repaint();
	}

	// --- Sections ---

	private void addCofferSection(PanelState state)
	{
		if (!header("Death's coffer"))
		{
			return;
		}
		if (state.isCofferKnown())
		{
			JLabel balance = new JLabel(QuantityFormatter.formatNumber(state.getCofferBalance()) + " gp");
			balance.setFont(FontManager.getRunescapeBoldFont());
			balance.setForeground(Color.WHITE);
			addRow(balance);
			addRow(text("From " + state.getCofferSource() + ", " + ago(state.getCofferUpdatedAt()), MUTED));
		}
		else
		{
			addRow(text("Balance unknown. Open the coffer at Death's Office, or set it below.", WARN));
		}

		long deaths = state.getDeathsCovered();
		DeathCostEstimate est = state.getEstimate();
		if (deaths >= 0 && est != null)
		{
			String covered = deaths == Long.MAX_VALUE ? "Dying is free in your current gear"
				: "Covers <b>" + deaths + "</b> death" + (deaths == 1 ? "" : "s") + " at " + gp(est.getScenarioFee()) + " each";
			addRow(text(covered, deaths < warnDeaths ? BAD : GOOD));
		}

		addGap(4);
		addRow(valueRow("Target", gp(state.getCofferTarget()), Color.WHITE));
		addRow(text(targetBasis(state), MUTED));
		CofferPlan plan = state.getPlan();
		if (plan != null)
		{
			if (plan.getShortfall() <= 0)
			{
				addRow(text("Your coffer meets the target.", GOOD));
			}
			else
			{
				addRow(valueRow("Short by", gp(plan.getShortfall()), WARN));
				if (!plan.getSteps().isEmpty())
				{
					addRow(text("Sacrifice the items under Death's coffer below"
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

		JButton set = smallButton("Set balance", "Enter your coffer balance by hand");
		set.addActionListener(e -> promptBalance());
		addGap(4);
		addRow(set);
	}

	private void addRiskSection(PanelState state)
	{
		if (!header("If you die now"))
		{
			return;
		}
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

	private void addDestinationsSection(PanelState state)
	{
		if (!header("Your spare items"))
		{
			return;
		}
		Destinations dests = state.getDestinations();
		if (dests == null)
		{
			addRow(text("Open your bank once so it can be scanned.", WARN));
			return;
		}
		addRow(text("Click one to see its items in your bank, most valuable first.", MUTED));
		addGap(2);

		boolean ge = state.getAccountMode().canUseGrandExchange();
		destinationRow(dests.get(Destination.Kind.ALL), state);
		if (state.getAccountMode().canUseCoffer())
		{
			destinationRow(dests.get(Destination.Kind.COFFER), state);
		}
		if (ge)
		{
			destinationRow(dests.get(Destination.Kind.ALCH), state);
			destinationRow(dests.get(Destination.Kind.GE), state);
		}

		subheader("Shops");
		List<Destination> shops = dests.getShops();
		if (shops.isEmpty())
		{
			addRow(text("No shop pays more than " + (ge ? "the GE or alching" : "alching") + " for your spare items.", MUTED));
		}
		for (Destination shop : shops)
		{
			destinationRow(shop, state);
		}
		if (!shops.isEmpty())
		{
			addRow(text("These shops are marked on your world map.", MUTED));
		}
		for (ShopStock.Busy b : state.getBusyShops())
		{
			addRow(wrapped("Not suggesting " + b.getShop() + " for " + b.getItem()
				+ " until about " + ShopTimes.clock(b.getUntil()) + ": others overstocked it on " + b.getWorlds()
				+ " worlds you checked.", DETAIL_WIDTH, WARN));
		}
		ShopPerks perks = state.getPerks();
		addRow(text("Karamja gloves: " + (perks.getGlovesTier() >= 3 ? "3 or 4" : perks.getGlovesTier() >= 1 ? "1 or 2" : "none")
			+ " · Ring of Charos (a): " + (perks.isCharos() ? "yes" : "no")
			+ (perks.isAvoidWilderness() ? " · Wilderness shops skipped" : ""), MUTED));

		subheader("Other");
		destinationRow(dests.get(Destination.Kind.KEPT), state);
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
		List<Flip> flips = state.getFlips();
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
			addRow(text((state.getFlipSort() == FlipSort.RETURN ? "Best return first" : "Most profitable first")
				+ ", spending up to the " + gp(state.getFlipCoins()) + " in your inventory and using"
				+ " shops whose requirements you meet. Each is one go: a shop visit, or one round of GE offers.", MUTED));
		}
		for (String note : notes)
		{
			addRow(text(note, MUTED));
		}
		addGap(4);
		flips.forEach(f -> flipRow(f, state.getFlipSort()));
	}

	/** One flip: the route, what it makes, how many and why no more, and a Find button for a shop. */
	private void flipRow(Flip flip, FlipSort sort)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		top.add(wrapped(capitalise(flip.getItem()) + "<br><font color='#FF9800'>" + flip.getSource().getLabel() + " &rarr; "
			+ flip.getDest().getLabel() + "</font>", NAME_WIDTH, Color.WHITE), BorderLayout.CENTER);
		JPanel east = new JPanel();
		east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
		east.setOpaque(false);
		// What it's sorted by goes first, in green
		JLabel profit = new JLabel("+" + gp(flip.getProfit()));
		profit.setToolTipText("What this flip makes in all");
		JLabel ret = new JLabel("+" + percent(flip.getReturnPercent()));
		ret.setToolTipText("Return: what it makes for each coin spent, " + gp(flip.getCost()) + " in all");
		JLabel second = sort == FlipSort.RETURN ? profit : new JLabel("+" + gp(flip.getProfitEach()) + " ea");
		if (second != profit)
		{
			second.setToolTipText(percent(flip.getPercent()) + " profit on what the first one costs");
		}
		JLabel first = sort == FlipSort.RETURN ? ret : profit;
		for (JLabel l : new JLabel[]{first, second})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(l == first ? GOOD : MUTED);
			l.setAlignmentX(Component.RIGHT_ALIGNMENT);
			east.add(l);
		}
		Shop shop = flip.getShop();
		if (shop != null)
		{
			boolean finding = shop.getName().equals(findingShop);
			JButton find = smallButton(finding ? "Stop" : "Find",
				finding ? "Stop pointing the way to this shop" : "Centre the world map on this shop and point the hint arrow at it");
			find.setAlignmentX(Component.RIGHT_ALIGNMENT);
			find.addActionListener(e -> onFindShop.accept(shop));
			east.add(Box.createVerticalStrut(2));
			east.add(find);
		}
		top.add(east, BorderLayout.EAST);
		row.add(top, BorderLayout.CENTER);
		row.add(wrapped(flipDetail(flip), DETAIL_WIDTH, MUTED), BorderLayout.SOUTH);
		addRow(row);
	}

	private static String flipDetail(Flip flip)
	{
		String qty = QuantityFormatter.formatNumber(flip.getQuantity());
		StringBuilder d = new StringBuilder();
		Shop buyShop = flip.getBuyShop();
		if (buyShop != null)
		{
			d.append("Buy ").append(qty).append(" from ").append(where(buyShop)).append(" from ").append(gp(flip.getFirstCost()))
				.append(flip.isPriceRises() ? ", rising with each one" : " each");
			if (flip.getBuyWorlds() > 1)
			{
				// Each world's shop starts full, so a few on each keeps the price down
				d.append(", ").append(QuantityFormatter.formatNumber(flip.getPerWorld())).append(" a world, hopping over ")
					.append(flip.getBuyWorlds()).append(" worlds");
			}
		}
		else
		{
			d.append("Buy ").append(qty).append(" on the GE at about ").append(gp(flip.getFirstCost())).append(" each");
		}
		d.append(" (").append(gp(flip.getCost())).append(" in all), then ");
		switch (flip.getDest())
		{
			case GE:
				d.append("sell them on the GE for ").append(gp(flip.getFirstValue())).append(" each after tax.");
				break;
			case COFFER:
				d.append("put them in Death's coffer for ").append(gp(flip.getFirstValue())).append(" each.");
				break;
			case ALCH:
				d.append("alch them for ").append(gp(flip.getFirstValue())).append(" each after the nature rune.");
				break;
			case SHOP:
				ShopOffer o = flip.getSellOffer();
				d.append("sell them to ").append(where(o.getShop()));
				if (o.getEqualShops() > 0)
				{
					d.append(" (or any of ").append(o.getEqualShops()).append(" others paying the same)");
				}
				d.append(" from ").append(gp(o.getFirstUnit())).append(" each");
				if (o.getWorlds() > 1)
				{
					d.append(", ").append(QuantityFormatter.formatNumber(o.getPerWorld())).append(" a world over ")
						.append(o.getWorlds()).append(" worlds");
				}
				if (o.isUsesKaramjaGloves())
				{
					d.append(", wearing Karamja gloves");
				}
				if (o.isUsesCharos())
				{
					d.append(", wearing your Ring of Charos (a)");
				}
				d.append(o.getShop().getTalk() != null ? ". " + o.getShop().getTalk() + " only takes unnoted ones, so it's an"
					+ " inventory a trip." : ". The shop takes noted ones.");
				break;
		}
		d.append(" Limited by ").append(flip.getLimit().getDescription());
		if (flip.getGeTrades() != Long.MAX_VALUE)
		{
			d.append("; about ").append(QuantityFormatter.formatNumber(flip.getGeTrades())).append(" trade on the GE in that time");
		}
		d.append('.');
		if (buyShop != null)
		{
			Shop.Item item = buyShop.getItems().get(flip.getItem());
			int[] bundle = item == null ? null : item.getB();
			if (bundle != null && bundle.length == 2)
			{
				d.append(" Offer ").append(gp(bundle[1])).append(" for ").append(bundle[0]).append(" to get that price.");
			}
			if (buyShop.getTalk() != null)
			{
				d.append(" Bought by talking to ").append(buyShop.getTalk()).append(", sometimes one at a time.");
			}
		}
		if (!flip.getUnchecked().isEmpty())
		{
			d.append(" <font color='#FF9800'>Also needs: ").append(String.join("; ", flip.getUnchecked())).append(".</font>");
		}
		return d.toString();
	}

	private static String where(Shop shop)
	{
		return shop.getName() + (shop.getLocation() == null || shop.getLocation().isEmpty() ? "" : " (" + shop.getLocation() + ")");
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
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
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
			if (d.getKind() == Destination.Kind.SHOP && d.getShop() != null)
			{
				boolean finding = d.getName().equals(findingShop);
				JButton find = smallButton(finding ? "Stop" : "Find",
					finding ? "Stop pointing the way to this shop"
						: "Centre the world map on this shop and point the hint arrow at it");
				find.setAlignmentX(Component.RIGHT_ALIGNMENT);
				find.addActionListener(e -> onFind.accept(d));
				east.add(Box.createVerticalStrut(2));
				east.add(find);
			}
			top.add(east, BorderLayout.EAST);
		}
		row.add(top, BorderLayout.CENTER);
		row.add(wrapped(empty ? emptyText(d) : detail(d, state), DETAIL_WIDTH, MUTED), BorderLayout.SOUTH);

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
					row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				}
			});
		}
		addRow(row);
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

	private void addKeptSection(PanelState state)
	{
		if (!header(SECTION_KEPT))
		{
			return;
		}
		if (state.getKeptItems().isEmpty())
		{
			addRow(text("Right-click an item in your bank and choose Keep to set how many to leave alone.", MUTED));
			return;
		}
		for (PanelState.KeptItem k : state.getKeptItems())
		{
			String what = k.getKeep() == KeepRules.KEEP_ALL ? "keep all" : "keep " + QuantityFormatter.formatNumber(k.getKeep());
			String have = k.getOwned() < 0 ? null : "you have " + QuantityFormatter.formatNumber(k.getOwned());
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(3, 6, 3, 6));
			JPanel top = new JPanel(new BorderLayout(4, 0));
			top.setOpaque(false);
			top.add(wrapped(k.getName(), NAME_WIDTH, Color.WHITE), BorderLayout.CENTER);
			JLabel v = new JLabel(what);
			v.setFont(FontManager.getRunescapeSmallFont());
			v.setForeground(Color.LIGHT_GRAY);
			v.setVerticalAlignment(JLabel.TOP);
			top.add(v, BorderLayout.EAST);
			row.add(top, BorderLayout.CENTER);

			JButton edit = smallButton("Edit", "Change how many to keep");
			edit.addActionListener(e -> promptKeep(k));
			JButton remove = smallButton("×", "Stop keeping this item");
			remove.addActionListener(e -> onKeepChange.accept(k.getItemId(), 0));
			JPanel bottom = new JPanel();
			bottom.setLayout(new BoxLayout(bottom, BoxLayout.X_AXIS));
			bottom.setOpaque(false);
			if (have != null)
			{
				JLabel h = new JLabel(have);
				h.setFont(FontManager.getRunescapeSmallFont());
				h.setForeground(MUTED);
				bottom.add(h);
			}
			bottom.add(Box.createHorizontalGlue());
			bottom.add(edit);
			bottom.add(Box.createHorizontalStrut(4));
			bottom.add(remove);
			row.add(bottom, BorderLayout.SOUTH);
			addRow(row);
		}
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

	private void promptKeep(PanelState.KeptItem k)
	{
		String current = k.getKeep() == KeepRules.KEEP_ALL ? "all" : String.valueOf(k.getKeep());
		Object input = JOptionPane.showInputDialog(this, "How many " + k.getName() + " should Asset Manager leave alone?\n(a number like 5 or 10k, \"all\", or 0 to stop keeping it)",
			"Keep " + k.getName(), JOptionPane.PLAIN_MESSAGE, null, null, current);
		if (input == null)
		{
			return;
		}
		Integer keep = KeepRules.parseInput(input.toString());
		if (keep == null)
		{
			JOptionPane.showMessageDialog(this, "Enter a number (like 5 or 10k) or \"all\".", "Keep " + k.getName(), JOptionPane.ERROR_MESSAGE);
			return;
		}
		onKeepChange.accept(k.getItemId(), keep);
	}

	private void promptBalance()
	{
		String input = JOptionPane.showInputDialog(this, "Death's coffer balance (e.g. 2500000 or 2.5m):", "Set coffer balance", JOptionPane.PLAIN_MESSAGE);
		if (input == null || input.trim().isEmpty())
		{
			return;
		}
		try
		{
			onManualBalance.accept(QuantityFormatter.parseQuantity(input.trim()));
		}
		catch (ParseException e)
		{
			JOptionPane.showMessageDialog(this, "That isn't a number.", "Set coffer balance", JOptionPane.ERROR_MESSAGE);
		}
	}

	// --- Building blocks ---

	/**
	 * Adds a section header that collapses the section when clicked.
	 *
	 * @return whether the section is expanded and its contents should be added
	 */
	private boolean header(String name)
	{
		boolean open = !collapsed.contains(name);
		JLabel l = new JLabel((open ? "▾ " : "▸ ") + name);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		l.setToolTipText(open ? "Click to collapse" : "Click to expand");
		l.addMouseListener(new MouseAdapter()
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
		addRow(l);
		return open;
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
		content.add(c);
		if (c instanceof JPanel && ((JPanel) c).isOpaque())
		{
			// Space between rows
			JComponent gap = (JComponent) Box.createVerticalStrut(2);
			gap.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(gap);
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
}
