package com.assetmanager.flip;

import com.assetmanager.AssetManagerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.FontID;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * Guides the player through buying a flip on the GE: keeps the chosen flip and how far its buying has got (see
 * {@link GeGuide}), shows its next item first in the GE's item search, and fills in an offer's price or quantity when
 * the player clicks the line it adds to the chatbox. One click fills in one box; the player still presses Enter and
 * confirms each offer.
 */
@Slf4j
@Singleton
public class GeGuider
{
	private static final String KEY_GUIDE = "geGuide";
	/** GE_NEWOFFER_TYPE for a sell offer; a buy is 0. */
	private static final int GE_SELL = 1;
	/** The chatbox's input modes: a number (an offer's price or quantity), and the GE's item search. */
	private static final int INPUT_NUMBER = 7;
	private static final int INPUT_GE_SEARCH = 14;
	/** On login the GE sends every slot's offer again over a couple of ticks; those aren't changes. */
	private static final int LOGIN_BURST_TICKS = 2;
	/** The chatbox's own input colour, and brighter when hovered. */
	private static final int FILL_COLOR = 0x0000FF;
	private static final int FILL_HOVER = 0x4F4FFF;

	/** What the chatbox is asking for, when it's something the guide can fill in. */
	public enum Ask
	{
		PRICE,
		QUANTITY,
		SEARCH
	}

	/** What can be filled in now: {@code value} for a price or quantity, or {@code item} to find in the search. */
	@Value
	public static class Fill
	{
		Ask ask;
		GeGuide.Item item;
		long value;
	}

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final AssetManagerConfig config;
	private final Gson gson;

	/** The flip being bought; null when none. */
	@Getter
	private volatile GeGuide guide;
	/** Called on the client thread when the guide or its progress changes. */
	@Setter
	private Runnable onChange = () -> {};
	private int loginTick = -1;
	/** The chatbox line to click to fill in a price or quantity; null when not shown. */
	private Widget fillLine;
	/** The chatbox's last question, logged when it changes. */
	private String lastAsk;
	/** The GE's "last searched" item has been set to the guide's. */
	private boolean lastSearchedSet;

	@Inject
	GeGuider(Client client, ClientThread clientThread, ConfigManager configManager, ItemManager itemManager,
		AssetManagerConfig config, Gson gson)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.config = config;
		this.gson = gson;
	}

	/**
	 * Starts guiding a GE-bought flip. What's already in buy offers counts, and so does {@code held}: how many of each
	 * item are spare in the inventory and bank. Client thread.
	 */
	public void start(FlipRun run, Map<Integer, Integer> held)
	{
		if (run.getSource() != Flip.Source.GE)
		{
			return;
		}
		GeGuide g = GeGuide.of(run, held);
		readSlots(g);
		guide = g;
		save();
		chat("Asset Manager: buying " + describe(g) + ". Open the GE and follow the highlights; in the price or quantity"
			+ " box, click the blue line to fill it in.");
		onChange.run();
	}

	/** Stops guiding. Client thread. */
	public void stop()
	{
		if (guide != null)
		{
			guide = null;
			save();
			onChange.run();
		}
	}

	/** Loads the profile's guide, if one was left going. Client thread. */
	public void load()
	{
		GeGuide g = null;
		String json = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_GUIDE);
		if (json != null && !json.isEmpty())
		{
			try
			{
				g = GeGuide.load(gson.fromJson(json, GeGuide.Saved.class));
			}
			catch (JsonParseException e)
			{
				log.debug("Couldn't read the saved GE guide", e);
			}
		}
		if (g != null && client.getGameState() == GameState.LOGGED_IN)
		{
			readSlots(g);
		}
		guide = g;
	}

	private void save()
	{
		if (guide == null)
		{
			configManager.unsetRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_GUIDE);
		}
		else
		{
			configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_GUIDE, gson.toJson(guide.save()));
		}
	}

	/** Notes the slots as they are, without counting them as changes. */
	private void readSlots(GeGuide g)
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}
		for (int n = 0; n < offers.length && n < GeGuide.SLOTS; n++)
		{
			g.setSlot(n, slot(offers[n]), false);
		}
	}

	private static GeGuide.Slot slot(GrandExchangeOffer o)
	{
		if (o == null || o.getState() == GrandExchangeOfferState.EMPTY || o.getItemId() <= 0)
		{
			return GeGuide.Slot.EMPTY;
		}
		GrandExchangeOfferState s = o.getState();
		boolean buy = s == GrandExchangeOfferState.BUYING || s == GrandExchangeOfferState.BOUGHT
			|| s == GrandExchangeOfferState.CANCELLED_BUY;
		return new GeGuide.Slot(o.getItemId(), buy, s == GrandExchangeOfferState.BUYING || s == GrandExchangeOfferState.SELLING,
			o.getQuantitySold(), o.getTotalQuantity(), o.getPrice());
	}

	/**
	 * While the GE is open, its "last searched" item is the flip's next one, so the item search shows it as soon as it
	 * opens: one click picks it. Only the client's copy is changed; the server's is put back when the GE closes or
	 * there's nothing left to buy.
	 */
	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		Fill f = fill();
		updateFillLine(f);
		GeGuide g = guide;
		GeGuide.Item next = g == null ? null : g.next();
		Widget ge = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		boolean open = ge != null && !ge.isHidden();
		int want = next != null && open ? next.getItemId() : -1;
		if (want > 0)
		{
			if (client.getVarpValue(VarPlayerID.GE_LAST_SEARCHED) != want)
			{
				client.setVarpValue(VarPlayerID.GE_LAST_SEARCHED, want);
				client.queueChangedVarp(VarPlayerID.GE_LAST_SEARCHED);
			}
			lastSearchedSet = true;
		}
		else if (lastSearchedSet)
		{
			lastSearchedSet = false;
			restoreLastSearched();
		}
	}

	/** Puts the GE's "last searched" item back to the server's. */
	private void restoreLastSearched()
	{
		int server = client.getServerVarpValue(VarPlayerID.GE_LAST_SEARCHED);
		if (client.getVarpValue(VarPlayerID.GE_LAST_SEARCHED) != server)
		{
			client.setVarpValue(VarPlayerID.GE_LAST_SEARCHED, server);
			client.queueChangedVarp(VarPlayerID.GE_LAST_SEARCHED);
		}
	}

	/** Puts back anything changed in the client, when the plugin stops. Client thread. */
	public void shutDown()
	{
		if (lastSearchedSet)
		{
			lastSearchedSet = false;
			restoreLastSearched();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loginTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GeGuide g = guide;
		if (g == null)
		{
			return;
		}
		// Turned on while logged in, there's no login burst to wait out
		boolean count = client.getGameState() == GameState.LOGGED_IN
			&& (loginTick < 0 || client.getTickCount() > loginTick + LOGIN_BURST_TICKS);
		boolean wasBought = g.isAllBought();
		g.setSlot(event.getSlot(), slot(event.getOffer()), count);
		if (count)
		{
			save();
			if (!wasBought && g.isAllBought())
			{
				chat("Asset Manager: everything for " + describe(g) + " is bought. " + cashOut(g));
			}
		}
		onChange.run();
	}

	/** Before the GE plugin's own search, so its fuzzy search doesn't take over. */
	@Subscribe(priority = 1)
	public void onGrandExchangeSearched(GrandExchangeSearched event)
	{
		GeGuide g = guide;
		if (g == null || event.isConsumed())
		{
			return;
		}
		String input = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
		boolean asked = input == null || input.isEmpty();
		GeGuide.Item next = g.next();
		if (!asked || next == null)
		{
			return;
		}
		event.consume();
		client.setGeSearchResultIndex(0);
		client.setGeSearchResultCount(1);
		client.setGeSearchResultIds(new short[]{(short) next.getItemId()});
	}

	/** What can be filled in now; null if nothing. */
	public Fill fill()
	{
		GeGuide g = guide;
		if (g == null)
		{
			return null;
		}
		int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		if (mode == INPUT_GE_SEARCH)
		{
			GeGuide.Item next = g.next();
			return next == null ? null : new Fill(Ask.SEARCH, next, 0);
		}
		// The price box may not use the same input mode as the quantity box, so it's told apart by its question
		if (mode == 0)
		{
			return null;
		}
		GeGuide.Item item = setupItem(g);
		Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		if (item == null || title == null || title.isHidden() || title.getText() == null)
		{
			return null;
		}
		String ask = Text.removeTags(title.getText()).toLowerCase();
		if (!ask.equals(lastAsk))
		{
			lastAsk = ask;
			log.debug("GE guide: chatbox asks \"{}\" in input mode {}", ask, mode);
		}
		if (ask.contains("price"))
		{
			return new Fill(Ask.PRICE, item, item.getPrice());
		}
		int qty = g.toOrder(item.getItemId());
		if ((ask.contains("how many") || ask.contains("quantity")) && qty > 0)
		{
			return new Fill(Ask.QUANTITY, item, qty);
		}
		return null;
	}

	/** The flip's item a buy offer is being set up for; null if none or another item. */
	public GeGuide.Item setupItem(GeGuide g)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden() || client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == GE_SELL)
		{
			return null;
		}
		return g.item(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH));
	}

	/** Fills in the price or quantity box with the flip's. Client thread. */
	private void applyFill()
	{
		Fill f = fill();
		if (f == null || f.getAsk() == Ask.SEARCH)
		{
			return;
		}
		String value = String.valueOf(f.getValue());
		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (input != null)
		{
			input.setText(value + "*");
		}
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, value);
	}

	/**
	 * While the price or quantity box is open for one of the flip's items, a line above its question to click to fill
	 * it in. Kept up to date each frame; the game clears it with the box.
	 */
	private void updateFillLine(Fill f)
	{
		boolean show = f != null && f.getAsk() != Ask.SEARCH && config.geGuideClick();
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		if (!show || layer == null || title == null)
		{
			if (fillLine != null)
			{
				fillLine.setHidden(true);
				fillLine = null;
			}
			return;
		}
		String text = fillText(f);
		if (fillLine == null || !isChild(layer, fillLine))
		{
			Widget w = layer.createChild(-1, WidgetType.TEXT);
			w.setFontId(FontID.PLAIN_12);
			w.setTextColor(FILL_COLOR);
			w.setTextShadowed(false);
			w.setXTextAlignment(WidgetTextAlignment.CENTER);
			w.setYTextAlignment(WidgetTextAlignment.CENTER);
			w.setOriginalX(0);
			w.setOriginalY(Math.max(2, title.getRelativeY() - 20));
			w.setOriginalWidth(layer.getWidth());
			w.setOriginalHeight(16);
			w.setAction(0, "Fill in");
			w.setHasListener(true);
			w.setOnOpListener((JavaScriptCallback) ev -> applyFill());
			w.setOnMouseOverListener((JavaScriptCallback) ev -> w.setTextColor(FILL_HOVER));
			w.setOnMouseLeaveListener((JavaScriptCallback) ev -> w.setTextColor(FILL_COLOR));
			w.setText(text);
			w.revalidate();
			fillLine = w;
		}
		else if (!text.equals(fillLine.getText()))
		{
			fillLine.setText(text);
		}
	}

	/** "Click here to enter 3,500". */
	private String fillText(Fill f)
	{
		String amount = QuantityFormatter.formatNumber(f.getValue()) + (f.getAsk() == Ask.PRICE ? " gp" : "");
		return "Click here to enter " + amount;
	}

	private static boolean isChild(Widget parent, Widget child)
	{
		Widget[] children = parent.getDynamicChildren();
		if (children != null)
		{
			for (Widget c : children)
			{
				if (c == child)
				{
					return !child.isHidden();
				}
			}
		}
		return false;
	}

	/** Whether the chatbox shows its own line to click, so the overlay needn't say it. */
	public boolean isFillLineShown()
	{
		return fillLine != null;
	}

	/** One item's progress, for the side panel. */
	@Value
	public static class ItemView
	{
		int itemId;
		String name;
		int quantity;
		int bought;
		int ordered;
		long price;
	}

	/** The guide as the side panel shows it. */
	@Value
	public static class View
	{
		String description;
		String cashOut;
		/** The shop to sell to; null if none. */
		String shop;
		List<ItemView> items;
		boolean allBought;
	}

	/** The guide for the side panel; null if there's none. */
	public static View view(GeGuide g)
	{
		if (g == null)
		{
			return null;
		}
		List<ItemView> items = new ArrayList<>();
		for (GeGuide.Item i : g.getItems())
		{
			items.add(new ItemView(i.getItemId(), i.getName(), i.getQuantity(), g.bought(i.getItemId()), g.ordered(i.getItemId()),
				i.getPrice()));
		}
		return new View(describe(g), cashOut(g), g.getShop(), items, g.isAllBought());
	}

	/** "3 items for Bob's Brilliant Axes", "50 x rune bar for the coffer". */
	public static String describe(GeGuide g)
	{
		if (g.getItems().size() == 1)
		{
			GeGuide.Item i = g.getItems().get(0);
			return QuantityFormatter.formatNumber(i.getQuantity()) + " x " + i.getName() + destination(g);
		}
		return g.getItems().size() + " items" + destination(g);
	}

	private static String destination(GeGuide g)
	{
		switch (g.getDest())
		{
			case SHOP:
				return g.getShop() == null ? " to sell to a shop" : " for " + g.getShop();
			case COFFER:
				return " for Death's coffer";
			case ALCH:
				return " to alch";
			default:
				return "";
		}
	}

	/** What to do once it's all bought. */
	public static String cashOut(GeGuide g)
	{
		switch (g.getDest())
		{
			case SHOP:
				return "Sell them at " + (g.getShop() == null ? "the shop" : g.getShop()) + ".";
			case COFFER:
				return "Put them in Death's coffer.";
			case ALCH:
				return "Alch them.";
			default:
				return "";
		}
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}
}
