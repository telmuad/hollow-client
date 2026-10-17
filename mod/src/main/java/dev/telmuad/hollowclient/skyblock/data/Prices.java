package dev.telmuad.hollowclient.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Prices from public sources that need no API key:
 * <ul>
 *   <li>Bazaar (insta-buy / insta-sell) from Hypixel's {@code /v2/skyblock/bazaar}, every 5 minutes.</li>
 *   <li>Lowest BIN from the Elite API's {@code /resources/auctions} (the base listing of each item, and pets by type,
 *   rarity and level), every 15 minutes.</li>
 *   <li>NPC sell prices and item names from Hypixel's {@code /v2/resources/skyblock/items}, once a day.</li>
 * </ul>
 * When a source is down the last download is used. Values are coins per item. Plain Java.
 */
public final class Prices {
	public static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	public static final String LBIN_URL = "https://api.elitebot.dev/resources/auctions";
	public static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
	public static final long BAZAAR_TTL = 5 * 60_000L, LBIN_TTL = 15 * 60_000L, ITEMS_TTL = 24 * 3_600_000L;

	public record Bazaar(double buy, double sell, long weeklyBuy, long weeklySell) {
	}

	public record PetPrice(int minLevel, int maxLevel, double price) {
	}

	/** Where a value came from. */
	public enum Source { BAZAAR, LOWEST_BIN, NPC, NONE }

	public record Estimate(double value, Source source) {
		public static final Estimate NONE = new Estimate(0, Source.NONE);
	}

	private volatile Map<String, Bazaar> bazaar = Map.of();
	private volatile Map<String, Double> lbin = Map.of();
	private volatile Map<String, List<PetPrice>> pets = Map.of();
	private volatile Map<String, Double> npc = Map.of();
	private volatile Map<String, String> names = Map.of();
	private volatile long bazaarAt, lbinAt, itemsAt;

	// ------------------------------------------------------------------------------------------------ lookups

	public boolean loaded() {
		return !bazaar.isEmpty() || !lbin.isEmpty();
	}

	public long bazaarAt() {
		return bazaarAt;
	}

	public long lbinAt() {
		return lbinAt;
	}

	public long itemsAt() {
		return itemsAt;
	}

	public Bazaar bazaar(String id) {
		return bazaar.get(id);
	}

	public OptionalDouble lowestBin(String id) {
		Double v = lbin.get(id);
		return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
	}

	/** A pet's lowest BIN for its type, rarity and level (the closest level group if none covers it). */
	public OptionalDouble petLowestBin(String type, ItemParser.Rarity tier, int level) {
		List<PetPrice> list = pets.get(type + ";" + tier.name());
		if (list == null || list.isEmpty()) return OptionalDouble.empty();
		PetPrice best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (PetPrice p : list) {
			int d = level < p.minLevel() ? p.minLevel() - level : (level > p.maxLevel() ? level - p.maxLevel() : 0);
			if (d < bestDistance) {
				bestDistance = d;
				best = p;
			}
		}
		return best == null ? OptionalDouble.empty() : OptionalDouble.of(best.price());
	}

	public OptionalDouble npc(String id) {
		Double v = npc.get(id);
		return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
	}

	/** The item's name from Hypixel's item list, or null. */
	public String name(String id) {
		return names.get(id);
	}

	/**
	 * What one item is worth: the bazaar insta-sell price for bazaar items, else the lowest BIN, else the NPC price.
	 * {@code petLevel} matters only for pets (-1 if unknown).
	 */
	public Estimate value(ItemParser.SbItem item, int petLevel) {
		if (item == null || !item.isSkyBlock()) return Estimate.NONE;
		String id = item.priceId();
		Bazaar b = bazaar.get(id);
		if (b != null && b.sell() > 0) return new Estimate(b.sell(), Source.BAZAAR);
		if (item.pet() != null) {
			OptionalDouble p = petLowestBin(item.pet().type(), item.pet().tier(), petLevel < 0 ? 1 : petLevel);
			if (p.isPresent()) return new Estimate(p.getAsDouble(), Source.LOWEST_BIN);
		}
		OptionalDouble l = lowestBin(item.id());
		if (l.isPresent()) return new Estimate(l.getAsDouble(), Source.LOWEST_BIN);
		OptionalDouble n = npc(item.id());
		if (n.isPresent()) return new Estimate(n.getAsDouble(), Source.NPC);
		return Estimate.NONE;
	}

	// ------------------------------------------------------------------------------------------------ loading

	/** Fetches whatever is due (on the calling thread; call it from a background task). */
	public void refresh(Http http, boolean force) {
		Http.Result b = http.getNow("bazaar", BAZAAR_URL, BAZAAR_TTL, force);
		if (b.ok() && (bazaarAt != b.fetchedAt())) loadBazaar(parse(b.body()), b.fetchedAt());
		Http.Result l = http.getNow("lowest_bin", LBIN_URL, LBIN_TTL, force);
		if (l.ok() && (lbinAt != l.fetchedAt())) loadLowestBin(parse(l.body()), l.fetchedAt());
		Http.Result i = http.getNow("items", ITEMS_URL, ITEMS_TTL, force);
		if (i.ok() && (itemsAt != i.fetchedAt())) loadItems(parse(i.body()), i.fetchedAt());
	}

	/** Reads whatever is in the cache, without downloading (startup). */
	public void loadCached(Http http) {
		Http.Result b = http.cached("bazaar");
		if (b.ok()) loadBazaar(parse(b.body()), b.fetchedAt());
		Http.Result l = http.cached("lowest_bin");
		if (l.ok()) loadLowestBin(parse(l.body()), l.fetchedAt());
		Http.Result i = http.cached("items");
		if (i.ok()) loadItems(parse(i.body()), i.fetchedAt());
	}

	private static JsonObject parse(byte[] body) {
		try {
			JsonElement e = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
		} catch (RuntimeException e) {
			return new JsonObject();
		}
	}

	public void loadBazaar(JsonObject root, long at) {
		JsonObject products = obj(root, "products");
		if (products == null) return;
		Map<String, Bazaar> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : products.entrySet()) {
			JsonObject q = e.getValue().isJsonObject() ? obj(e.getValue().getAsJsonObject(), "quick_status") : null;
			if (q == null) continue;
			out.put(e.getKey(), new Bazaar(num(q, "buyPrice"), num(q, "sellPrice"), (long) num(q, "buyMovingWeek"),
					(long) num(q, "sellMovingWeek")));
		}
		if (!out.isEmpty()) {
			bazaar = Map.copyOf(out);
			bazaarAt = at;
		}
	}

	public void loadLowestBin(JsonObject root, long at) {
		JsonObject items = obj(root, "items");
		if (items == null) return;
		Map<String, Double> out = new HashMap<>();
		Map<String, List<PetPrice>> petOut = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : items.entrySet()) {
			if (!e.getValue().isJsonArray()) continue;
			for (JsonElement ve : e.getValue().getAsJsonArray()) {
				if (!ve.isJsonObject()) continue;
				JsonObject v = ve.getAsJsonObject();
				double price = num(v, "lowest") > 0 ? num(v, "lowest") : num(v, "lowest3Day");
				if (price <= 0) continue;
				JsonObject by = obj(v, "variedBy");
				if (by != null && by.has("pet") && by.has("rarity")) {
					JsonObject lv = obj(by, "petLevel");
					int min = lv == null ? 1 : (int) num(lv, "min");
					int max = lv == null ? 200 : (int) num(lv, "max");
					petOut.computeIfAbsent(by.get("pet").getAsString() + ";" + by.get("rarity").getAsString(), k -> new ArrayList<>())
							.add(new PetPrice(min, max, price));
				} else if (str(v, "variantKey").isEmpty()) {
					out.put(e.getKey(), price);
				}
			}
		}
		if (!out.isEmpty() || !petOut.isEmpty()) {
			lbin = Map.copyOf(out);
			pets = Map.copyOf(petOut);
			lbinAt = at;
		}
	}

	public void loadItems(JsonObject root, long at) {
		JsonElement items = root.get("items");
		if (items == null || !items.isJsonArray()) return;
		Map<String, Double> npcOut = new HashMap<>();
		Map<String, String> nameOut = new HashMap<>();
		for (JsonElement e : items.getAsJsonArray()) {
			if (!e.isJsonObject()) continue;
			JsonObject o = e.getAsJsonObject();
			String id = str(o, "id");
			if (id.isEmpty()) continue;
			if (o.has("name")) nameOut.put(id, str(o, "name"));
			if (num(o, "npc_sell_price") > 0) npcOut.put(id, num(o, "npc_sell_price"));
		}
		if (!nameOut.isEmpty()) {
			npc = Map.copyOf(npcOut);
			names = Map.copyOf(nameOut);
			itemsAt = at;
		}
	}

	private static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
