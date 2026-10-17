package dev.telmuad.hollowclient.skyblock.event;

import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.state.Island;

/**
 * Something that happened in SkyBlock, posted on the {@link EventBus}. Features react to these (and read
 * {@link dev.telmuad.hollowclient.skyblock.state.SkyBlockState}); they never parse game text themselves.
 */
public sealed interface SbEvent {
	/** A short description for the event log in the Hub. */
	default String describe() {
		return toString();
	}

	record JoinedSkyBlock() implements SbEvent {
	}

	record LeftSkyBlock() implements SbEvent {
	}

	record IslandChanged(Island from, Island to) implements SbEvent {
	}

	record AreaChanged(String from, String to) implements SbEvent {
	}

	record ProfileChanged(String name, String id, TabListParser.GameMode mode) implements SbEvent {
	}

	/** Any chat pattern that matched (alerts use these). */
	record ChatMatched(ChatParser.ChatMatch match, String line) implements SbEvent {
	}

	record RareDrop(String kind, String item, int count, int magicFind) implements SbEvent {
	}

	record SlayerQuestStarted() implements SbEvent {
	}

	record SlayerBossSpawned(String boss, int tier) implements SbEvent {
	}

	record SlayerQuestCompleted(String boss, int tier) implements SbEvent {
	}

	record SlayerQuestFailed() implements SbEvent {
	}

	record SkillXpGained(String skill, double amount, String progress) implements SbEvent {
	}

	record SkillLevelUp(String skill, int level) implements SbEvent {
	}

	record VisitorArrived(String name) implements SbEvent {
	}

	record PurseChanged(long purse, long delta) implements SbEvent {
	}

	record BitsChanged(long bits, long delta) implements SbEvent {
	}

	record TitleShown(String title, String subtitle) implements SbEvent {
	}

	record ContainerOpened(String title, int slots) implements SbEvent {
	}
}
