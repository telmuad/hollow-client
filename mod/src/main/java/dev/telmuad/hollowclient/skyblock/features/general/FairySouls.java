package dev.telmuad.hollowclient.skyblock.features.general;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fairy souls: their fixed locations per island (from the NEU repository's constants/fairy_souls.json) and which ones
 * you've found on this profile. Hypixel doesn't tell clients which souls are found, so Hollow marks the nearest soul
 * when chat says "SOUL! You found a Fairy Soul!" (or "already found"). Plain Java.
 */
public final class FairySouls {
	public record Soul(int x, int y, int z) {
		public String key() {
			return x + "," + y + "," + z;
		}

		public double distanceSq(double px, double py, double pz) {
			double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	private final Map<String, List<Soul>> byIsland = new LinkedHashMap<>();
	private final Map<String, Set<String>> found = new LinkedHashMap<>();
	private int total;

	/** Reads NEU's fairy_souls.json: {"Max Souls": n, "<island mode>": ["x,y,z", ...]}. */
	public static FairySouls fromNeu(JsonObject o) {
		FairySouls f = new FairySouls();
		if (o == null) return f;
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (!e.getValue().isJsonArray()) {
				if (e.getKey().equalsIgnoreCase("Max Souls") && e.getValue().isJsonPrimitive()) f.total = e.getValue().getAsInt();
				continue;
			}
			List<Soul> souls = new ArrayList<>();
			for (JsonElement s : e.getValue().getAsJsonArray()) {
				String[] parts = s.getAsString().split(",");
				if (parts.length != 3) continue;
				try {
					souls.add(new Soul(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()), Integer.parseInt(parts[2].strip())));
				} catch (NumberFormatException ignored) {
				}
			}
			f.byIsland.put(e.getKey(), souls);
		}
		if (f.total == 0) f.total = f.byIsland.values().stream().mapToInt(List::size).sum();
		return f;
	}

	public boolean loaded() {
		return !byIsland.isEmpty();
	}

	public int total() {
		return total;
	}

	public List<Soul> souls(String island) {
		return byIsland.getOrDefault(island, List.of());
	}

	public boolean isFound(String island, Soul s) {
		return found.getOrDefault(island, Set.of()).contains(s.key());
	}

	public List<Soul> missing(String island) {
		List<Soul> out = new ArrayList<>();
		for (Soul s : souls(island)) if (!isFound(island, s)) out.add(s);
		return out;
	}

	public int foundCount() {
		return found.values().stream().mapToInt(Set::size).sum();
	}

	/** Marks the nearest soul within {@code maxDist} blocks as found; returns it (null if none is that close). */
	public Soul markNearest(String island, double x, double y, double z, double maxDist) {
		Soul best = null;
		double bestD = maxDist * maxDist;
		for (Soul s : souls(island)) {
			double d = s.distanceSq(x, y, z);
			if (d <= bestD) {
				bestD = d;
				best = s;
			}
		}
		if (best != null) found.computeIfAbsent(island, k -> new HashSet<>()).add(best.key());
		return best;
	}

	public void markAll(String island, boolean isFound) {
		if (isFound) {
			Set<String> set = found.computeIfAbsent(island, k -> new HashSet<>());
			for (Soul s : souls(island)) set.add(s.key());
		} else {
			found.remove(island);
		}
	}

	/** Found souls for the per-profile file: {"<island>": ["x,y,z", ...]}. */
	public JsonObject foundJson() {
		JsonObject o = new JsonObject();
		found.forEach((island, keys) -> {
			JsonArray a = new JsonArray();
			keys.stream().sorted().forEach(a::add);
			o.add(island, a);
		});
		return o;
	}

	public void loadFound(JsonObject o) {
		found.clear();
		if (o == null) return;
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (!e.getValue().isJsonArray()) continue;
			Set<String> keys = new HashSet<>();
			for (JsonElement k : e.getValue().getAsJsonArray()) keys.add(k.getAsString());
			found.put(e.getKey(), keys);
		}
	}
}
