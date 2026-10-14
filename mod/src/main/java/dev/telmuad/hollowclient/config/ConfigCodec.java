package dev.telmuad.hollowclient.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.hud.HudPosition;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.setting.Setting;

/**
 * Turns module state into JSON and back. Shape of config/hollowclient.json:
 * <pre>
 * {
 *   "version": 1,
 *   "modules": { "fps": { "enabled": true, "settings": { "textColor": "#FFFFFFFF", ... }, "hud": { ... } }, ... },
 *   "styles": { "packs": [...], "activePack": "", "presets": [...] }
 * }
 * </pre>
 * Reading never throws: unknown keys are ignored and anything missing or of the wrong type keeps its default.
 */
public final class ConfigCodec {
	public static final int VERSION = 1;

	private ConfigCodec() {
	}

	public static JsonObject encode(ModuleRegistry registry, StylesConfig styles) {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonObject modules = new JsonObject();
		for (Module m : registry.all()) modules.add(m.id(), encodeModule(m, true));
		root.add("modules", modules);
		root.add("styles", styles.toJson());
		return root;
	}

	public static JsonObject encodeModule(Module m, boolean includeHud) {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", m.isEnabled());
		JsonObject settings = new JsonObject();
		for (Setting<?> s : m.settings()) settings.add(s.id(), s.toJson());
		o.add("settings", settings);
		if (includeHud && m.hudPosition() != null) o.add("hud", m.hudPosition().toJson());
		return o;
	}

	/** Load everything at startup. Enabled flags are restored silently (see {@link Module#restoreEnabled}). */
	public static void decode(JsonObject root, ModuleRegistry registry, StylesConfig styles) {
		JsonObject modules = root != null && root.has("modules") && root.get("modules").isJsonObject()
				? root.getAsJsonObject("modules") : new JsonObject();
		for (Module m : registry.all()) decodeModule(m, modules.get(m.id()), true, true);
		styles.fromJson(root == null ? null : root.get("styles"));
	}

	/**
	 * Apply module JSON. Anything missing goes back to the default.
	 *
	 * @param silent     true at startup (no enable/disable callbacks); false when applying a preset in game
	 * @param includeHud whether to read the "hud" block
	 */
	public static void decodeModule(Module m, JsonElement json, boolean silent, boolean includeHud) {
		JsonObject o = json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
		JsonObject settings = o.has("settings") && o.get("settings").isJsonObject() ? o.getAsJsonObject("settings") : new JsonObject();
		for (Setting<?> s : m.settings()) {
			JsonElement value = settings.get(s.id());
			if (value == null) s.reset();
			else s.fromJson(value);
		}
		if (includeHud && m.hudPosition() != null && m.defaultHudPosition() != null) {
			m.hudPosition().copyFrom(HudPosition.fromJson(o.get("hud"), m.defaultHudPosition()));
		}
		boolean enabled = m.enabledByDefault();
		JsonElement e = o.get("enabled");
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) enabled = e.getAsBoolean();
		if (silent) m.restoreEnabled(enabled);
		else m.setEnabled(enabled);
	}
}
