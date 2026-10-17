package dev.telmuad.hollowclient.skyblock.state;

import java.util.Locale;

/**
 * SkyBlock islands (server "modes"). The mode ids are what Hypixel's location packet (and /locraw) report; the names
 * are what the tab list shows after "Area:". Checked against the current islands on 2026-10-16.
 */
public enum Island {
	PRIVATE_ISLAND("dynamic", "Private Island"),
	GARDEN("garden", "Garden", "The Garden"),
	HUB("hub", "Hub"),
	FARMING_ISLANDS("farming_1", "The Farming Islands", "The Barn", "Mushroom Desert"),
	THE_PARK("foraging_1", "The Park"),
	GALATEA("foraging_2", "Galatea", "Moonglade Marsh"),
	TORRHUS_CANYON("foraging_3", "Torrhus Canyon"),
	SPIDERS_DEN("combat_1", "Spider's Den"),
	THE_END("combat_3", "The End"),
	CRIMSON_ISLE("crimson_isle", "Crimson Isle"),
	GOLD_MINE("mining_1", "Gold Mine"),
	DEEP_CAVERNS("mining_2", "Deep Caverns"),
	DWARVEN_MINES("mining_3", "Dwarven Mines"),
	CRYSTAL_HOLLOWS("crystal_hollows", "Crystal Hollows"),
	MINESHAFT("mineshaft", "Mineshaft", "Glacite Mineshafts"),
	BACKWATER_BAYOU("fishing_1", "Backwater Bayou"),
	DUNGEON_HUB("dungeon_hub", "Dungeon Hub"),
	DUNGEON("dungeon", "Catacombs", "The Catacombs"),
	KUUDRA("kuudra", "Kuudra", "Kuudra's Hollow"),
	JERRYS_WORKSHOP("winter", "Jerry's Workshop"),
	THE_RIFT("rift", "The Rift"),
	DARK_AUCTION("dark_auction", "Dark Auction"),
	SAFARI("safari", "Safari"),
	LOTUS_ATOLL("lotus_atoll", "Lotus Atoll"),
	UNKNOWN("unknown", "Unknown");

	private final String mode;
	private final String displayName;
	private final String[] aliases;

	Island(String mode, String displayName, String... aliases) {
		this.mode = mode;
		this.displayName = displayName;
		this.aliases = aliases;
	}

	public String mode() {
		return mode;
	}

	public String displayName() {
		return displayName;
	}

	public static Island fromMode(String mode) {
		if (mode == null) return UNKNOWN;
		String m = mode.strip().toLowerCase(Locale.ROOT);
		for (Island i : values()) if (i.mode.equals(m)) return i;
		return UNKNOWN;
	}

	/** From the tab list's "Area:"/"Dungeon:" value or the location packet's map name. */
	public static Island fromName(String name) {
		if (name == null) return UNKNOWN;
		String n = name.strip();
		for (Island i : values()) {
			if (i.displayName.equalsIgnoreCase(n)) return i;
			for (String a : i.aliases) if (a.equalsIgnoreCase(n)) return i;
		}
		return UNKNOWN;
	}

	public boolean isMining() {
		return this == GOLD_MINE || this == DEEP_CAVERNS || this == DWARVEN_MINES || this == CRYSTAL_HOLLOWS || this == MINESHAFT;
	}

	public boolean isInstanced() {
		return this == DUNGEON || this == KUUDRA;
	}
}
