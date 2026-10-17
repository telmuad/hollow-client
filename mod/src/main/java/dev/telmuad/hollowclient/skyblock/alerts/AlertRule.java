package dev.telmuad.hollowclient.skyblock.alerts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One alert: when something happens (a SkyBlock event, a known chat pattern, or your own chat regex), show a title,
 * play a sound and optionally a toast. Display and sound only; an alert never does anything in the game.
 * <p>
 * {@code match} is an event key ({@link AlertEngine#EVENT_KEYS}), {@code pattern:<name>} for a chat pattern from
 * chat.json, or a regex for {@link Trigger#CHAT}. Titles can use {@code {placeholders}}: the event's values or the
 * regex groups ({@code {1}}, {@code {name}}). Plain Java.
 */
public record AlertRule(String id, String name, boolean enabled, boolean builtin, Trigger trigger, String match, String title,
						String subtitle, String sound, boolean toast, Set<String> islands, long cooldownMs) {
	public enum Trigger { EVENT, CHAT }

	public AlertRule {
		islands = Set.copyOf(islands);
		cooldownMs = Math.max(0, cooldownMs);
	}

	public AlertRule withEnabled(boolean on) {
		return new AlertRule(id, name, on, builtin, trigger, match, title, subtitle, sound, toast, islands, cooldownMs);
	}

	public AlertRule withSound(String s) {
		return new AlertRule(id, name, enabled, builtin, trigger, match, title, subtitle, s, toast, islands, cooldownMs);
	}

	public AlertRule withToast(boolean t) {
		return new AlertRule(id, name, enabled, builtin, trigger, match, title, subtitle, sound, t, islands, cooldownMs);
	}

	/** For CHAT rules: the compiled regex, or null if it doesn't compile. */
	public Pattern regex() {
		if (trigger != Trigger.CHAT) return null;
		try {
			return Pattern.compile(match);
		} catch (PatternSyntaxException e) {
			return null;
		}
	}

	/** Why a custom rule can't be saved, or null if it's fine. */
	public String problem() {
		if (name.isBlank()) return "Give the alert a name.";
		if (title.isBlank()) return "Type the title to show.";
		if (trigger == Trigger.CHAT) {
			if (match.isBlank()) return "Type the chat text (a regular expression) to watch for.";
			try {
				Pattern.compile(match);
			} catch (PatternSyntaxException e) {
				return "The pattern isn't a valid regular expression: " + e.getDescription();
			}
		}
		return null;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("name", name);
		o.addProperty("enabled", enabled);
		o.addProperty("trigger", trigger.name());
		o.addProperty("match", match);
		o.addProperty("title", title);
		o.addProperty("subtitle", subtitle);
		o.addProperty("sound", sound);
		o.addProperty("toast", toast);
		JsonArray is = new JsonArray();
		for (String i : islands) is.add(i);
		o.add("islands", is);
		o.addProperty("cooldownMs", cooldownMs);
		return o;
	}

	public static AlertRule fromJson(JsonObject o) {
		Set<String> islands = new LinkedHashSet<>();
		JsonElement is = o.get("islands");
		if (is != null && is.isJsonArray()) for (JsonElement e : is.getAsJsonArray()) islands.add(e.getAsString());
		Trigger t;
		try {
			t = Trigger.valueOf(str(o, "trigger", "CHAT"));
		} catch (IllegalArgumentException e) {
			t = Trigger.CHAT;
		}
		return new AlertRule(str(o, "id", "custom"), str(o, "name", "Alert"), !o.has("enabled") || o.get("enabled").getAsBoolean(),
				false, t, str(o, "match", ""), str(o, "title", ""), str(o, "subtitle", ""), str(o, "sound", "PLING"),
				o.has("toast") && o.get("toast").getAsBoolean(), islands, o.has("cooldownMs") ? o.get("cooldownMs").getAsLong() : 2000);
	}

	private static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}
}
