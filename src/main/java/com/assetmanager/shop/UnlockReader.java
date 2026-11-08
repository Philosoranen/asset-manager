package com.assetmanager.shop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Reads the player's skills, quests and diaries for checking shop requirements. Client thread only. */
@Singleton
public class UnlockReader
{
	/** Each diary's completion varbit, by AREA_TIER. */
	private static final Map<String, Integer> DIARIES = new HashMap<>();

	static
	{
		diary("ARDOUGNE", VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
			VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);
		diary("DESERT", VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
			VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE);
		diary("FALADOR", VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
			VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE);
		diary("FREMENNIK", VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
			VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);
		diary("KANDARIN", VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
			VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);
		// Karamja's easy to hard diaries predate the others and use their own varbits
		diary("KARAMJA", VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE,
			VarbitID.KARAMJA_DIARY_ELITE_COMPLETE);
		diary("KOUREND", VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
			VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE);
		diary("LUMBRIDGE", VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
			VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
		diary("MORYTANIA", VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
			VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
		diary("VARROCK", VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
			VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE);
		diary("WESTERN", VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
			VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE);
		diary("WILDERNESS", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
			VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);
	}

	private static void diary(String area, int easy, int medium, int hard, int elite)
	{
		DIARIES.put(area + "_EASY", easy);
		DIARIES.put(area + "_MEDIUM", medium);
		DIARIES.put(area + "_HARD", hard);
		DIARIES.put(area + "_ELITE", elite);
	}

	private final Client client;

	@Inject
	UnlockReader(Client client)
	{
		this.client = client;
	}

	/** The player's progress, or Unlocks.EVERYTHING if they aren't logged in. Runs a script per quest, so not every tick. */
	public Unlocks read()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return Unlocks.EVERYTHING;
		}
		Map<String, Integer> skills = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			skills.put(skill.name(), client.getRealSkillLevel(skill));
		}
		Set<String> finished = new HashSet<>();
		Set<String> started = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			QuestState state = quest.getState(client);
			if (state == QuestState.FINISHED)
			{
				finished.add(quest.name());
			}
			else if (state == QuestState.IN_PROGRESS)
			{
				started.add(quest.name());
			}
		}
		Set<String> diaries = new HashSet<>();
		DIARIES.forEach((name, varbit) ->
		{
			if (client.getVarbitValue(varbit) > 0)
			{
				diaries.add(name);
			}
		});
		// Members content is only usable on a members world, which only members can log in to
		boolean members = client.getWorldType().contains(WorldType.MEMBERS);
		return new Unlocks(skills, finished, started, diaries, me.getCombatLevel(),
			client.getVarpValue(VarPlayerID.QP_TOTAL), members);
	}
}
