package dev.telmuad.hollowclient.skyblock.features.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

/** Item helpers from NEU data: craft cost from recipes, reforge stones, essence costs, slot labels. Plain Java. */
public final class ItemData {
	private ItemData() {
	}

	/**
	 * Ingredients of an item's crafting recipe ({@code recipe} {"A1": "ID:count", ...} or the first crafting entry of
	 * {@code recipes}), merged by id; empty when there's no crafting recipe.
	 */
	public static Map<String, Integer> ingredients(JsonObject neuItem) {
		Map<String, Integer> out = new LinkedHashMap<>();
		if (neuItem == null) return out;
		JsonObject recipe = null;
		JsonElement r = neuItem.get("recipe");
		if (r != null) recipe = asObject(r);
		if (recipe == null && neuItem.has("recipes")) {
			JsonElement rs = neuItem.get("recipes");
			JsonArray list = rs.isJsonArray() ? rs.getAsJsonArray() : null;
			if (list == null && rs.isJsonPrimitive()) {
				try {
					JsonElement parsed = JsonParser.parseString(rs.getAsString());
					if (parsed.isJsonArray()) list = parsed.getAsJsonArray();
				} catch (RuntimeException ignored) {
				}
			}
			if (list != null) {
				for (JsonElement e : list) {
					JsonObject o = asObject(e);
					if (o != null && (!o.has("type") || "crafting".equals(o.get("type").getAsString()))) {
						recipe = o;
						break;
					}
				}
			}
		}
		if (recipe == null) return out;
		for (String slot : new String[]{"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"}) {
			JsonElement e = recipe.get(slot);
			if (e == null || !e.isJsonPrimitive()) continue;
			String v = e.getAsString();
			if (v.isEmpty()) continue;
			int colon = v.lastIndexOf(':');
			String id = colon > 0 ? v.substring(0, colon) : v;
			int count = 1;
			if (colon > 0) {
				try {
					count = (int) Double.parseDouble(v.substring(colon + 1));
				} catch (NumberFormatException ignored) {
				}
			}
			out.merge(id, count, Integer::sum);
		}
		return out;
	}

	/** What the ingredients cost to buy today; empty if any ingredient has no price. */
	public static OptionalDouble craftCost(JsonObject neuItem, Function<String, OptionalDouble> price) {
		Map<String, Integer> ing = ingredients(neuItem);
		if (ing.isEmpty()) return OptionalDouble.empty();
		double total = 0;
		for (Map.Entry<String, Integer> e : ing.entrySet()) {
			OptionalDouble p = price.apply(e.getKey());
			if (p.isEmpty()) return OptionalDouble.empty();
			total += p.getAsDouble() * e.getValue();
		}
		return OptionalDouble.of(total);
	}

	/** NEU stores some fields (recipe, lore) as strings of JSON-ish text; accept both. */
	private static JsonObject asObject(JsonElement e) {
		if (e.isJsonObject()) return e.getAsJsonObject();
		if (e.isJsonPrimitive()) {
			try {
				JsonElement parsed = JsonParser.parseString(e.getAsString());
				return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
			} catch (RuntimeException ex) {
				return null;
			}
		}
		return null;
	}

	/** reforgestones.json: reforge name (lower case) -> stone id. */
	public static Map<String, String> reforgeStones(JsonObject constants) {
		Map<String, String> out = new LinkedHashMap<>();
		if (constants == null) return out;
		for (Map.Entry<String, JsonElement> e : constants.entrySet()) {
			JsonObject o = asObject(e.getValue());
			if (o == null || !o.has("reforgeName")) continue;
			out.put(o.get("reforgeName").getAsString().toLowerCase(Locale.ROOT).replace(' ', '_'), e.getKey());
		}
		return out;
	}

	/** essencecosts.json for one item: {"type": "Wither", "1": 150, "2": 300, ...}. */
	public static ItemValue.EssenceCost essence(JsonObject constants, String itemId) {
		if (constants == null || !constants.has(itemId)) return null;
		JsonObject o = asObject(constants.get(itemId));
		if (o == null || !o.has("type")) return null;
		int n = 0;
		while (o.has(Integer.toString(n + 1))) n++;
		int[] costs = new int[n];
		for (int i = 0; i < n; i++) costs[i] = o.get(Integer.toString(i + 1)).getAsInt();
		return new ItemValue.EssenceCost(o.get("type").getAsString(), costs);
	}

	/** The short text shown on a slot: pet level, star count, book short name, minion tier (or null). */
	public static String slotLabel(ItemParser.SbItem item) {
		if (!item.isSkyBlock()) return null;
		if (item.pet() != null) {
			int level = ItemParser.petLevel(item);
			return level > 0 ? Integer.toString(level) : null;
		}
		if ("ENCHANTED_BOOK".equals(item.id()) && item.enchantments().size() == 1) {
			Map.Entry<String, Integer> e = item.enchantments().entrySet().iterator().next();
			return shortEnchant(e.getKey()) + SbText.toRoman(e.getValue());
		}
		java.util.regex.Matcher minion = java.util.regex.Pattern.compile(".+_GENERATOR_(\\d+)").matcher(item.id());
		if (minion.matches()) return SbText.toRoman(Integer.parseInt(minion.group(1)));
		if (item.stars() > 0) return item.stars() > 5 ? "✪" + item.stars() : "✪" + item.stars();
		return null;
	}

	/** "ultimate_wise" -> "UW", "sharpness" -> "Sh", "first_strike" -> "FS". */
	static String shortEnchant(String key) {
		String k = key.toLowerCase(Locale.ROOT);
		boolean ultimate = k.startsWith("ultimate_");
		if (ultimate) k = k.substring("ultimate_".length());
		String[] words = k.split("_");
		StringBuilder sb = new StringBuilder(ultimate ? "U" : "");
		if (words.length == 1 && !ultimate) {
			sb.append(Character.toUpperCase(words[0].charAt(0)));
			if (words[0].length() > 1) sb.append(words[0].charAt(1));
		} else {
			for (String w : words) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0)));
		}
		return sb.toString();
	}
}
