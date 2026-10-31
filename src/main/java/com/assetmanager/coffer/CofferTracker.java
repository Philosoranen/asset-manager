package com.assetmanager.coffer;

import com.assetmanager.AssetManagerConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Keeps track of the Death's coffer balance. There is no documented variable for it, so it is read from
 * several places and the most recent reading wins:
 * <ul>
 *     <li>the value the server sends with gravestone/coffer interfaces</li>
 *     <li>the "Coffer: ..." line of the Death's coffer window</li>
 *     <li>chat messages that state the coffer balance</li>
 *     <li>a value the player enters manually</li>
 * </ul>
 * The balance is saved per account so it survives restarts.
 */
@Slf4j
@Singleton
public class CofferTracker
{
	private static final String KEY_BALANCE = "cofferBalance";
	private static final String KEY_UPDATED = "cofferUpdated";
	private static final String KEY_SOURCE = "cofferSource";

	private static final Pattern NUMBER = Pattern.compile("(\\d[\\d,]*)");
	private static final Pattern COFFER_LINE = Pattern.compile("coffer\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([kmb])?");
	private static final Pattern BALANCE_WORDS = Pattern.compile("contains|balance|currently|you have|value of|remaining|left in", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final ConfigManager configManager;

	@Getter
	private long balance = -1;
	@Getter
	private Instant updatedAt;
	@Getter
	private String source;

	private Runnable onChange = () -> {};
	private String lastWindowText;

	@Inject
	CofferTracker(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	public void setOnChange(Runnable onChange)
	{
		this.onChange = onChange;
	}

	public boolean isKnown()
	{
		return balance >= 0;
	}

	/** Loads the saved balance for the current account. */
	public void load()
	{
		String b = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_BALANCE);
		String u = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_UPDATED);
		source = configManager.getRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_SOURCE);
		try
		{
			balance = b == null ? -1 : Long.parseLong(b);
			updatedAt = u == null ? null : Instant.ofEpochMilli(Long.parseLong(u));
		}
		catch (NumberFormatException e)
		{
			balance = -1;
			updatedAt = null;
		}
		onChange.run();
	}

	public void setBalance(long value, String from)
	{
		log.debug("Coffer balance {} from {}", value, from);
		balance = value;
		updatedAt = Instant.now();
		source = from;
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_BALANCE, value);
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_UPDATED, updatedAt.toEpochMilli());
		configManager.setRSProfileConfiguration(AssetManagerConfig.GROUP, KEY_SOURCE, from);
		onChange.run();
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() != VarClientID.GRAVESTONE_TRANSMIT_COFFER)
		{
			return;
		}
		int value = client.getVarcIntValue(VarClientID.GRAVESTONE_TRANSMIT_COFFER);
		log.debug("GRAVESTONE_TRANSMIT_COFFER changed to {}", value);
		if (value >= 0)
		{
			setBalance(value, "game data");
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.MESBOX
			&& event.getType() != ChatMessageType.DIALOG)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		if (!message.toLowerCase().contains("coffer"))
		{
			return;
		}
		// Logged so the exact wording can be confirmed and the parsing tightened up.
		log.debug("Coffer chat message ({}): {}", event.getType(), message);
		if (BALANCE_WORDS.matcher(message).find())
		{
			Long value = firstNumber(message);
			if (value != null)
			{
				setBalance(value, "chat");
			}
		}
	}

	/** Reads the balance from the Death's coffer window if it's open. Call once per tick. */
	public void readCofferWindow()
	{
		if (client.getWidget(InterfaceID.DeathCoffer.UNIVERSE) == null)
		{
			lastWindowText = null;
			return;
		}
		List<String> texts = windowTexts();
		String joined = String.join(" | ", texts);
		if (joined.equals(lastWindowText))
		{
			return;
		}
		lastWindowText = joined;
		log.debug("Death's coffer window text: {}", joined);

		Long value = findBalance(texts);
		if (value != null && value != balance)
		{
			setBalance(value, "coffer window");
		}
	}

	/**
	 * Finds the "Coffer: ..." line in the coffer window, e.g. "Coffer: Empty" or "Coffer: 1,234,567 coins".
	 */
	static Long findBalance(List<String> texts)
	{
		for (String text : texts)
		{
			Matcher m = COFFER_LINE.matcher(text.trim());
			if (m.matches())
			{
				Long value = parseAmount(m.group(1));
				if (value != null)
				{
					return value;
				}
			}
		}
		return null;
	}

	/** Parses "Empty", "1,234,567", "1,234 coins", "1.5M" or "250K". */
	static Long parseAmount(String s)
	{
		String t = s.toLowerCase().replace(",", "").replace("coins", "").replace("gp", "").trim();
		if (t.equals("empty") || t.equals("none"))
		{
			return 0L;
		}
		Matcher m = AMOUNT.matcher(t);
		if (!m.matches())
		{
			return null;
		}
		double n = Double.parseDouble(m.group(1));
		String suffix = m.group(2);
		if (suffix != null)
		{
			n *= suffix.equals("k") ? 1e3 : suffix.equals("m") ? 1e6 : 1e9;
		}
		return Math.round(n);
	}

	/** All visible, non-empty texts in the coffer window. */
	private List<String> windowTexts()
	{
		List<String> texts = new ArrayList<>();
		collectTexts(client.getWidget(InterfaceID.DeathCoffer.UNIVERSE), texts);
		return texts;
	}

	private static void collectTexts(Widget w, List<String> out)
	{
		if (w == null || w.isHidden())
		{
			return;
		}
		if (w.getText() != null && !w.getText().isEmpty())
		{
			out.add(Text.removeTags(w.getText()));
		}
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					collectTexts(c, out);
				}
			}
		}
	}

	static Long firstNumber(String text)
	{
		Matcher m = NUMBER.matcher(text);
		if (!m.find())
		{
			return null;
		}
		try
		{
			return Long.parseLong(m.group(1).replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
