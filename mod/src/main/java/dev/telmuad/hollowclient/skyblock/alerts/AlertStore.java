package dev.telmuad.hollowclient.skyblock.alerts;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The alert list: the built-in alerts (each area adds its own) with your changes to them (on/off, sound, toast), and
 * your own chat alerts. Saved in {@code config/hollowclient/skyblock/alerts.json}. Plain Java.
 */
public final class AlertStore {
	private final Path file;
	private final Map<String, AlertRule> builtins = new LinkedHashMap<>();
	private final Map<String, JsonObject> overrides = new LinkedHashMap<>();
	private final List<AlertRule> custom = new ArrayList<>();

	public AlertStore(Path file) {
		this.file = file;
	}

	/** Registers a built-in alert (called by each area). */
	public void builtin(String id, String name, String eventOrPattern, String title, String subtitle, String sound, boolean toast,
						long cooldownMs, String... islands) {
		builtins.put(id, new AlertRule(id, name, true, true, AlertRule.Trigger.EVENT, eventOrPattern, title, subtitle, sound, toast,
				Set.of(islands), cooldownMs));
	}

	/** Built-ins (with your changes) followed by your own alerts. */
	public List<AlertRule> rules() {
		List<AlertRule> out = new ArrayList<>();
		for (AlertRule b : builtins.values()) {
			JsonObject o = overrides.get(b.id());
			AlertRule r = b;
			if (o != null) {
				if (o.has("enabled")) r = r.withEnabled(o.get("enabled").getAsBoolean());
				if (o.has("sound")) r = r.withSound(o.get("sound").getAsString());
				if (o.has("toast")) r = r.withToast(o.get("toast").getAsBoolean());
			}
			out.add(r);
		}
		out.addAll(custom);
		return out;
	}

	public void load() {
		overrides.clear();
		custom.clear();
		if (!Files.isRegularFile(file)) return;
		try {
			JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!e.isJsonObject()) return;
			JsonObject root = e.getAsJsonObject();
			if (root.has("builtins") && root.get("builtins").isJsonObject()) {
				for (Map.Entry<String, JsonElement> b : root.getAsJsonObject("builtins").entrySet()) {
					if (b.getValue().isJsonObject()) overrides.put(b.getKey(), b.getValue().getAsJsonObject());
				}
			}
			if (root.has("custom") && root.get("custom").isJsonArray()) {
				for (JsonElement c : root.getAsJsonArray("custom")) {
					if (!c.isJsonObject()) continue;
					AlertRule r = AlertRule.fromJson(c.getAsJsonObject());
					if (r.problem() == null) custom.add(r);
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// a broken file means defaults; it's rewritten on the next change
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject b = new JsonObject();
		overrides.forEach(b::add);
		root.add("builtins", b);
		JsonArray c = new JsonArray();
		for (AlertRule r : custom) c.add(r.toJson());
		root.add("custom", c);
		Files.createDirectories(file.getParent());
		AtomicFiles.write(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
	}

	/** Changes a rule: built-ins keep only on/off, sound and toast; custom rules are replaced as a whole. */
	public void update(AlertRule r) {
		if (builtins.containsKey(r.id())) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", r.enabled());
			o.addProperty("sound", r.sound());
			o.addProperty("toast", r.toast());
			overrides.put(r.id(), o);
			return;
		}
		for (int i = 0; i < custom.size(); i++) {
			if (custom.get(i).id().equals(r.id())) {
				custom.set(i, r);
				return;
			}
		}
		custom.add(r);
	}

	public void remove(String id) {
		custom.removeIf(r -> r.id().equals(id));
	}

	/** A new custom chat alert with a fresh id. */
	public static AlertRule newCustom(String name, String regex, String title, String sound, boolean toast) {
		return new AlertRule("custom-" + UUID.randomUUID().toString().substring(0, 8), name, true, false, AlertRule.Trigger.CHAT,
				regex, title, "", sound, toast, Set.of(), 2000);
	}
}
