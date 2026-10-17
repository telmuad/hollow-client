package dev.telmuad.hollowclient.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Jacob's Farming Contests for the SkyBlock year, with their crops, from the Elite API's keyless
 * {@code /contests/at/now} (the whole year in one answer, so a cached copy stays useful for days). Plain Java.
 */
public final class Contests {
	public static final String URL = "https://api.elitebot.dev/contests/at/now";
	public static final long TTL = 3 * 3_600_000L;

	public record Contest(long start, List<String> crops) {
		public long end() {
			return start + SkyCalendar.DAY;
		}
	}

	private volatile TreeMap<Long, List<String>> byStart = new TreeMap<>();

	public void refresh(Http http, boolean force) {
		Http.Result r = http.getNow("contests", URL, TTL, force);
		if (r.ok()) load(new String(r.body(), StandardCharsets.UTF_8));
	}

	public void loadCached(Http http) {
		Http.Result r = http.cached("contests");
		if (r.ok()) load(new String(r.body(), StandardCharsets.UTF_8));
	}

	public void load(String json) {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject() || !root.getAsJsonObject().has("contests")) return;
			TreeMap<Long, List<String>> out = new TreeMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().getAsJsonObject("contests").entrySet()) {
				if (!e.getValue().isJsonArray()) continue;
				List<String> crops = new ArrayList<>();
				for (JsonElement c : e.getValue().getAsJsonArray()) crops.add(c.getAsString());
				out.put(Long.parseLong(e.getKey()), List.copyOf(crops));
			}
			if (!out.isEmpty()) byStart = out;
		} catch (RuntimeException ignored) {
			// keep what we had
		}
	}

	/** The running and next contests from {@code now} (unix seconds); crops are empty when unknown. */
	public List<Contest> next(long now, int count) {
		List<Contest> out = new ArrayList<>();
		long start = SkyCalendar.nextJacob(now).start();
		for (int i = 0; i < count; i++) {
			long s = start + i * 3 * SkyCalendar.DAY;
			out.add(new Contest(s, byStart.getOrDefault(s, List.of())));
		}
		return out;
	}

	public boolean loaded() {
		return !byStart.isEmpty();
	}
}
