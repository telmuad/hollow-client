package dev.telmuad.hollowclient.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The current mayor, their perks and the minister, from Hypixel's keyless {@code /v2/resources/skyblock/election}. Plain Java. */
public final class Election {
	public static final String URL = "https://api.hypixel.net/v2/resources/skyblock/election";
	public static final long TTL = 10 * 60_000L;

	public record Mayor(String name, List<String> perks, String minister, String ministerPerk) {
		public static final Mayor UNKNOWN = new Mayor("", List.of(), "", "");

		public Set<String> allPerks() {
			Set<String> s = perks.stream().collect(Collectors.toSet());
			if (!ministerPerk.isEmpty()) s.add(ministerPerk);
			return s;
		}
	}

	private volatile Mayor mayor = Mayor.UNKNOWN;
	private volatile long at;

	public Mayor mayor() {
		return mayor;
	}

	public long at() {
		return at;
	}

	public void refresh(Http http, boolean force) {
		Http.Result r = http.getNow("election", URL, TTL, force);
		if (r.ok() && r.fetchedAt() != at) load(r.body(), r.fetchedAt());
	}

	public void loadCached(Http http) {
		Http.Result r = http.cached("election");
		if (r.ok()) load(r.body(), r.fetchedAt());
	}

	void load(byte[] body, long fetchedAt) {
		Mayor m = parse(new String(body, StandardCharsets.UTF_8));
		if (m != null) {
			mayor = m;
			at = fetchedAt;
		}
	}

	public static Mayor parse(String json) {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) return null;
			JsonObject o = root.getAsJsonObject();
			if (!o.has("mayor") || !o.get("mayor").isJsonObject()) return null;
			JsonObject m = o.getAsJsonObject("mayor");
			List<String> perks = new ArrayList<>();
			if (m.has("perks") && m.get("perks").isJsonArray()) {
				for (JsonElement p : m.getAsJsonArray("perks")) {
					if (p.isJsonObject() && p.getAsJsonObject().has("name")) perks.add(p.getAsJsonObject().get("name").getAsString());
				}
			}
			String minister = "", ministerPerk = "";
			if (m.has("minister") && m.get("minister").isJsonObject()) {
				JsonObject mi = m.getAsJsonObject("minister");
				minister = mi.has("name") ? mi.get("name").getAsString() : "";
				if (mi.has("perk") && mi.get("perk").isJsonObject() && mi.getAsJsonObject("perk").has("name")) {
					ministerPerk = mi.getAsJsonObject("perk").get("name").getAsString();
				}
			}
			return new Mayor(m.has("name") ? m.get("name").getAsString() : "?", List.copyOf(perks), minister, ministerPerk);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
