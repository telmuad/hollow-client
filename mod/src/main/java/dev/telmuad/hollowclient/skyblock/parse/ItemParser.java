package dev.telmuad.hollowclient.skyblock.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Reads a SkyBlock item. Everything that identifies the item comes from its {@code custom_data} component (Hypixel's
 * "ExtraAttributes"): the SkyBlock id, uuid, reforge, enchantments, stars, gems, hot potato books, recombobulator and
 * pet info. The material and display name are never used to guess what an item is; only the rarity is read from the
 * last lore line, because that's where the game shows it. Plain Java: the item arrives as JSON (the adapter converts
 * the item's NBT).
 */
public final class ItemParser {
	public enum Rarity {
		COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, DIVINE, SPECIAL, VERY_SPECIAL, ULTIMATE, ADMIN, UNKNOWN;

		public static Rarity of(String s) {
			if (s == null) return UNKNOWN;
			try {
				return valueOf(s.strip().replace(' ', '_'));
			} catch (IllegalArgumentException e) {
				return UNKNOWN;
			}
		}

		/** One step down (an item recombobulated to MYTHIC was LEGENDARY). */
		public Rarity previous() {
			return switch (this) {
				case UNCOMMON -> COMMON;
				case RARE -> UNCOMMON;
				case EPIC -> RARE;
				case LEGENDARY -> EPIC;
				case MYTHIC -> LEGENDARY;
				case DIVINE -> MYTHIC;
				case VERY_SPECIAL -> SPECIAL;
				default -> this;
			};
		}
	}

	/** Pet data from {@code petInfo}. */
	public record PetInfo(String type, Rarity tier, double exp, String heldItem, int candyUsed, boolean active, String skin) {
	}

	/**
	 * A SkyBlock item. {@code id} is empty for items that aren't SkyBlock items (vanilla items, menu glass panes).
	 * {@code stars} counts all stars (dungeon 1-5 plus master 6-10).
	 */
	public record SbItem(String id, String uuid, String name, int count, Rarity rarity, String type, boolean dungeon,
						 String reforge, Map<String, Integer> enchantments, int stars, int hotPotatoes, boolean recombobulated,
						 Map<String, String> gems, PetInfo pet, long timestamp, boolean soulbound, boolean museum,
						 JsonObject data) {
		public boolean isSkyBlock() {
			return !id.isEmpty();
		}

		/** The id prices are listed under: pets by type and rarity, enchanted books by their (single) enchantment. */
		public String priceId() {
			if (pet != null) return pet.type() + ";" + pet.tier().ordinal();
			if ("ENCHANTED_BOOK".equals(id) && enchantments.size() == 1) {
				Map.Entry<String, Integer> e = enchantments.entrySet().iterator().next();
				return "ENCHANTMENT_" + e.getKey().toUpperCase(java.util.Locale.ROOT) + "_" + e.getValue();
			}
			return id;
		}
	}

	private ItemParser() {
	}

	/** A pet's level from its name ("[Lvl 100] Golden Dragon"), -1 if it isn't there. */
	public static int petLevel(SbItem item) {
		if (item == null || item.pet() == null) return -1;
		Matcher m = Patterns.of("item").find("pet_name", item.name());
		return m == null ? -1 : Integer.parseInt(m.group("level"));
	}

	/**
	 * @param customData the item's custom data as JSON (may be null)
	 * @param name       display name (formatting allowed)
	 * @param lore       lore lines (formatting allowed)
	 */
	public static SbItem parse(JsonObject customData, String name, List<String> lore, int count) {
		JsonObject d = customData == null ? new JsonObject() : customData;
		String id = str(d, "id");
		Map<String, Integer> ench = new LinkedHashMap<>();
		JsonObject eo = obj(d, "enchantments");
		if (eo != null) for (Map.Entry<String, JsonElement> e : eo.entrySet()) ench.put(e.getKey(), intOf(e.getValue()));
		Map<String, String> gems = new LinkedHashMap<>();
		JsonObject go = obj(d, "gems");
		if (go != null) {
			for (Map.Entry<String, JsonElement> e : go.entrySet()) {
				JsonElement v = e.getValue();
				if (v.isJsonPrimitive()) gems.put(e.getKey(), v.getAsString());
				else if (v.isJsonObject() && v.getAsJsonObject().has("quality")) gems.put(e.getKey(), str(v.getAsJsonObject(), "quality"));
			}
		}
		int stars = Math.max(integer(d, "upgrade_level"), integer(d, "dungeon_item_level"));

		Rarity rarity = Rarity.UNKNOWN;
		String type = "";
		boolean dungeon = false;
		boolean soulbound = false;
		Patterns p = Patterns.of("item");
		for (String l : lore) if (p.matches("soulbound", SbText.clean(l)) != null) soulbound = true;
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = SbText.clean(lore.get(i));
			if (line.isEmpty() || p.matches("soulbound", line) != null) continue;
			Matcher m = p.matches("rarity_line", line);
			if (m != null) {
				rarity = Rarity.of(m.group("rarity"));
				dungeon = m.group("dungeon") != null;
				type = m.group("type") == null ? "" : m.group("type").strip();
			}
			break;
		}
		PetInfo pet = pet(d);
		if (pet != null && rarity == Rarity.UNKNOWN) rarity = pet.tier();
		return new SbItem(id, str(d, "uuid"), SbText.clean(name), count, rarity, type, dungeon, str(d, "modifier"), ench,
				stars, integer(d, "hot_potato_count"), integer(d, "rarity_upgrades") > 0, gems, pet, longOf(d, "timestamp"),
				soulbound, integer(d, "donated_museum") > 0, d);
	}

	private static PetInfo pet(JsonObject d) {
		JsonElement e = d.get("petInfo");
		JsonObject o = null;
		if (e != null && e.isJsonPrimitive()) {
			try {
				JsonElement parsed = JsonParser.parseString(e.getAsString());
				if (parsed.isJsonObject()) o = parsed.getAsJsonObject();
			} catch (RuntimeException ignored) {
				return null;
			}
		} else if (e != null && e.isJsonObject()) {
			o = e.getAsJsonObject();
		}
		if (o == null || str(o, "type").isEmpty()) return null;
		double exp = 0;
		try {
			exp = o.has("exp") ? o.get("exp").getAsDouble() : 0;
		} catch (RuntimeException ignored) {
		}
		return new PetInfo(str(o, "type"), Rarity.of(str(o, "tier")), exp, str(o, "heldItem"), integer(o, "candyUsed"),
				o.has("active") && o.get("active").isJsonPrimitive() && o.get("active").getAsBoolean(), str(o, "skin"));
	}

	// ------------------------------------------------------------------------------------------------ JSON helpers

	private static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static int integer(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null ? 0 : intOf(e);
	}

	private static int intOf(JsonElement e) {
		try {
			return e.isJsonPrimitive() ? (int) e.getAsDouble() : 0;
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	private static long longOf(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0L;
		} catch (RuntimeException ex) {
			return 0L;
		}
	}
}
