package dev.telmuad.hollowclient.skyblock.features.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * An item's estimated value, piece by piece: the clean item's price plus what was added to it (enchantments, hot and
 * fuming potato books, recombobulator, reforge stone, gemstones, essence for stars, master stars). Each upgrade is
 * priced at what it costs to buy today. It's an estimate, not what the item sells for. Plain Java.
 */
public final class ItemValue {
	/** Prices by item id (bazaar insta-buy or lowest BIN). */
	public interface PriceLookup {
		OptionalDouble price(String id);
	}

	/** NEU data the estimate needs (null when the item repository isn't downloaded). */
	public interface Neu {
		/** The reforge stone id for a reforge name ("withered" -> "WITHER_BLOOD"), or null for blacksmith reforges. */
		String reforgeStone(String reforge);

		/** Essence type and cost per star (index 0 = 1st star), or null. */
		EssenceCost essence(String itemId);
	}

	public record EssenceCost(String type, int[] perStar) {
	}

	public record Part(String label, double value) {
	}

	public record Estimate(double total, List<Part> parts) {
	}

	private static final String[] MASTER_STARS = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR",
			"FIFTH_MASTER_STAR"};

	private ItemValue() {
	}

	public static Estimate estimate(ItemParser.SbItem item, double base, PriceLookup prices, Neu neu) {
		List<Part> parts = new ArrayList<>();
		if (base > 0) parts.add(new Part("Item", base));
		double ench = 0;
		for (Map.Entry<String, Integer> e : item.enchantments().entrySet()) {
			if ("ENCHANTED_BOOK".equals(item.id())) break; // the book itself is the base price
			ench += prices.price("ENCHANTMENT_" + e.getKey().toUpperCase(Locale.ROOT) + "_" + e.getValue()).orElse(0);
		}
		if (ench > 0) parts.add(new Part("Enchantments", ench));
		int potatoes = item.hotPotatoes();
		if (potatoes > 0) {
			double v = Math.min(10, potatoes) * prices.price("HOT_POTATO_BOOK").orElse(0)
					+ Math.max(0, potatoes - 10) * prices.price("FUMING_POTATO_BOOK").orElse(0);
			if (v > 0) parts.add(new Part("Potato books (" + potatoes + ")", v));
		}
		if (item.recombobulated()) {
			double v = prices.price("RECOMBOBULATOR_3000").orElse(0);
			if (v > 0) parts.add(new Part("Recombobulator", v));
		}
		if (!item.reforge().isEmpty() && neu != null) {
			String stone = neu.reforgeStone(item.reforge());
			if (stone != null) {
				double v = prices.price(stone).orElse(0);
				if (v > 0) parts.add(new Part("Reforge stone", v));
			}
		}
		double gems = 0;
		for (Map.Entry<String, String> g : item.gems().entrySet()) {
			String slot = g.getKey();
			if (slot.endsWith("_gem") || slot.equals("unlocked_slots")) continue;
			String quality = g.getValue();
			String type = gemType(item.data(), slot);
			if (type == null || quality == null || quality.isEmpty()) continue;
			gems += prices.price(quality.toUpperCase(Locale.ROOT) + "_" + type + "_GEM").orElse(0);
		}
		if (gems > 0) parts.add(new Part("Gemstones", gems));
		if (item.stars() > 0 && neu != null) {
			EssenceCost cost = neu.essence(item.id());
			if (cost != null) {
				int essence = 0;
				for (int i = 0; i < Math.min(item.stars(), cost.perStar().length); i++) essence += cost.perStar()[i];
				double v = essence * prices.price("ESSENCE_" + cost.type().toUpperCase(Locale.ROOT)).orElse(0);
				if (v > 0) parts.add(new Part("Stars (" + essence + " " + cost.type() + " essence)", v));
			}
			double master = 0;
			for (int i = 0; i < Math.min(5, item.stars() - 5); i++) master += prices.price(MASTER_STARS[i]).orElse(0);
			if (master > 0) parts.add(new Part("Master stars", master));
		}
		double total = 0;
		for (Part p : parts) total += p.value();
		return new Estimate(total, List.copyOf(parts));
	}

	/**
	 * The gem in a slot: typed slots are named after their gem ("RUBY_0"); universal slots ("COMBAT_0") store the gem
	 * in "<slot>_gem".
	 */
	static String gemType(JsonObject data, String slot) {
		JsonObject gems = data.has("gems") && data.get("gems").isJsonObject() ? data.getAsJsonObject("gems") : null;
		if (gems != null) {
			JsonElement t = gems.get(slot + "_gem");
			if (t != null && t.isJsonPrimitive()) return t.getAsString().toUpperCase(Locale.ROOT);
		}
		String prefix = slot.contains("_") ? slot.substring(0, slot.lastIndexOf('_')) : slot;
		return switch (prefix) {
			case "RUBY", "AMBER", "SAPPHIRE", "JADE", "AMETHYST", "TOPAZ", "JASPER", "OPAL", "AQUAMARINE", "CITRINE", "ONYX", "PERIDOT" -> prefix;
			default -> null;
		};
	}
}
