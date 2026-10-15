package dev.telmuad.hollowclient.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.config.ConfigCodec;
import dev.telmuad.hollowclient.config.Preset;
import dev.telmuad.hollowclient.theme.HollowTheme;
import dev.telmuad.hollowclient.theme.Themes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Save your whole look as a named preset and switch presets with one click: cape, hat, wings, halo, horns, aura,
 * pet, the theme, your trail, crosshair, HUD Style and HUD layout, and the texture pack. Applying a preset only
 * changes what it contains, so presets saved by older versions (cape, hat, wings, pack) still work.
 */
public final class PresetManager {
	/** Your look. */
	public static final List<String> MODULES = List.of("cape", "hat", "wings", "halo", "horns", "aura", "pet");
	/** Other things a preset remembers. HUD elements are added on top, with their positions. */
	public static final List<String> EXTRAS = List.of("crosshair", "particles", "hud_style");
	/** Key for the theme inside a preset. */
	public static final String THEME = "$theme";

	private PresetManager() {
	}

	public static Preset capture(String name) {
		JsonObject modules = new JsonObject();
		for (String id : MODULES) {
			HollowClient.modules().get(id).ifPresent(m -> modules.add(id, ConfigCodec.encodeModule(m, false)));
		}
		for (String id : EXTRAS) {
			HollowClient.modules().get(id).ifPresent(m -> modules.add(id, ConfigCodec.encodeModule(m, false)));
		}
		for (var m : HollowClient.modules().all()) {
			if (m.hudPosition() != null) modules.add(m.id(), ConfigCodec.encodeModule(m, true));
		}
		modules.add(THEME, Themes.current().toJson());
		return new Preset(name, modules, PackManager.active());
	}

	/** Save (or overwrite, matching the name without caring about case). */
	public static Preset save(String name) {
		Preset p = capture(name);
		HollowClient.styles().putPreset(p);
		HollowClient.config().markDirty();
		return p;
	}

	/** Apply a preset. Returns a warning for the screen (for example a missing pack), or null if all went well. */
	public static String apply(Preset preset) {
		for (String key : preset.modules().keySet()) {
			JsonElement value = preset.modules().get(key);
			if (THEME.equals(key)) {
				Themes.store().setCurrent(HollowTheme.fromJson(value, Themes.current()));
				continue;
			}
			HollowClient.modules().get(key).ifPresent(m -> ConfigCodec.decodeModule(m, value, false, m.hudPosition() != null));
		}
		String warning = null;
		String pack = preset.pack();
		if (pack.isEmpty()) {
			if (!PackManager.active().isEmpty()) PackManager.use(null);
		} else if (!PackManager.isKnown(pack)) {
			warning = "The pack \"" + pack + "\" isn't imported anymore, so it was skipped.";
		} else if (!pack.equals(PackManager.active())) {
			if (!PackManager.use(pack)) warning = "Minecraft couldn't load the pack \"" + pack + "\".";
		}
		HollowClient.config().markDirty();
		return warning;
	}

	public static void delete(String name) {
		if (HollowClient.styles().removePreset(name)) HollowClient.config().markDirty();
	}

	/** Rename; returns an error message or null. */
	public static String rename(String from, String to) {
		String clean = Preset.cleanName(to);
		Preset p = HollowClient.styles().findPreset(from);
		if (p == null) return "That preset doesn't exist anymore.";
		Preset other = HollowClient.styles().findPreset(clean);
		if (other != null && other != p) return "There is already a preset called \"" + clean + "\".";
		List<Preset> list = HollowClient.styles().presets;
		list.set(list.indexOf(p), new Preset(clean, p.modules(), p.pack()));
		HollowClient.config().markDirty();
		return null;
	}

	/** Short description, for example "Cape: Ember · Wizard hat · Wings: Angel · Pack: Faithful". */
	public static String summary(Preset p) {
		List<String> parts = new ArrayList<>();
		JsonObject cape = obj(p.modules().get("cape"));
		if (enabled(cape)) parts.add("Cape: " + CapeManager.nameOf(setting(cape, "cape", CapeManager.DEFAULT)));
		JsonObject hat = obj(p.modules().get("hat"));
		if (enabled(hat)) parts.add(pretty(setting(hat, "style", "top_hat")));
		JsonObject wings = obj(p.modules().get("wings"));
		if (enabled(wings)) parts.add("Wings: " + pretty(setting(wings, "style", "shadow")));
		JsonObject theme = obj(p.modules().get(THEME));
		if (theme.has("name") && theme.get("name").isJsonPrimitive()) parts.add("Theme: " + theme.get("name").getAsString());
		if (!p.pack().isEmpty()) parts.add("Pack: " + p.pack());
		return parts.isEmpty() ? "Nothing on" : String.join(" · ", parts);
	}

	private static String pretty(String enumName) {
		String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static JsonObject obj(JsonElement e) {
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	private static boolean enabled(JsonObject module) {
		JsonElement e = module.get("enabled");
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}

	private static String setting(JsonObject module, String key, String fallback) {
		JsonObject settings = obj(module.get("settings"));
		JsonElement e = settings.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
	}
}
