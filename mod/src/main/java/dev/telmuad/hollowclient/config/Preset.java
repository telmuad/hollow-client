package dev.telmuad.hollowclient.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.setting.StringSetting;

/**
 * A saved style combination: the state of the style modules (cape, hat, wings) plus the texture pack.
 *
 * @param name    display name, also the key (unique, case-insensitive)
 * @param modules module id → module JSON (same shape as in the config file)
 * @param pack    imported pack name, or "" for no pack
 */
public record Preset(String name, JsonObject modules, String pack) {
	public static final int MAX_NAME = 32;

	public Preset {
		name = cleanName(name);
		modules = modules == null ? new JsonObject() : modules.deepCopy();
		pack = pack == null ? "" : StringSetting.clean(pack, 200);
	}

	public static String cleanName(String raw) {
		String s = raw == null ? "" : StringSetting.clean(raw, MAX_NAME).trim();
		return s.isEmpty() ? "Preset" : s;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.add("modules", modules.deepCopy());
		o.addProperty("pack", pack);
		return o;
	}

	/** Returns null if the JSON isn't a usable preset. */
	public static Preset fromJson(JsonElement json) {
		if (json == null || !json.isJsonObject()) return null;
		JsonObject o = json.getAsJsonObject();
		String name = string(o.get("name"));
		if (name == null || name.isBlank()) return null;
		JsonObject modules = o.has("modules") && o.get("modules").isJsonObject() ? o.getAsJsonObject("modules") : new JsonObject();
		String pack = string(o.get("pack"));
		return new Preset(name, modules, pack == null ? "" : pack);
	}

	private static String string(JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
	}
}
