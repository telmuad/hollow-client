package dev.telmuad.hollowclient.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.config.ConfigCodec;
import dev.telmuad.hollowclient.config.Preset;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Save the current cape + hat + wings + pack as a named preset, and switch presets with one click. */
public final class PresetManager {
	/** The modules a preset covers. */
	public static final List<String> MODULES = List.of("cape", "hat", "wings");

	private PresetManager() {
	}

	public static Preset capture(String name) {
		JsonObject modules = new JsonObject();
		for (String id : MODULES) {
			HollowClient.modules().get(id).ifPresent(m -> modules.add(id, ConfigCodec.encodeModule(m, false)));
		}
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
		for (String id : MODULES) {
			HollowClient.modules().get(id).ifPresent(m -> ConfigCodec.decodeModule(m, preset.modules().get(id), false, false));
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
