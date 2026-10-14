package dev.telmuad.hollowclient.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.setting.StringSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Style state that isn't a module setting: imported texture packs and saved presets. */
public final class StylesConfig {
	public static final int MAX_PRESETS = 50;
	public static final int MAX_PACKS = 200;

	/** Names of packs Hollow imported into the resourcepacks folder (file or folder names). */
	public final List<String> packs = new ArrayList<>();
	/** The imported pack that is switched on, or "" for none. */
	public String activePack = "";
	public final List<Preset> presets = new ArrayList<>();

	public void reset() {
		packs.clear();
		activePack = "";
		presets.clear();
	}

	public Preset findPreset(String name) {
		for (Preset p : presets) if (p.name().equalsIgnoreCase(name)) return p;
		return null;
	}

	/** Add or replace (by name, ignoring case) a preset. */
	public void putPreset(Preset preset) {
		for (int i = 0; i < presets.size(); i++) {
			if (presets.get(i).name().equalsIgnoreCase(preset.name())) {
				presets.set(i, preset);
				return;
			}
		}
		if (presets.size() < MAX_PRESETS) presets.add(preset);
	}

	public boolean removePreset(String name) {
		return presets.removeIf(p -> p.name().equalsIgnoreCase(name));
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		JsonArray packArray = new JsonArray();
		packs.forEach(packArray::add);
		o.add("packs", packArray);
		o.addProperty("activePack", activePack);
		JsonArray presetArray = new JsonArray();
		presets.forEach(p -> presetArray.add(p.toJson()));
		o.add("presets", presetArray);
		return o;
	}

	public void fromJson(JsonElement json) {
		reset();
		if (json == null || !json.isJsonObject()) return;
		JsonObject o = json.getAsJsonObject();
		if (o.has("packs") && o.get("packs").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("packs")) {
				if (packs.size() >= MAX_PACKS) break;
				if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
					String name = StringSetting.clean(e.getAsString(), 200);
					if (!name.isBlank() && packs.stream().noneMatch(p -> p.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))) {
						packs.add(name);
					}
				}
			}
		}
		JsonElement active = o.get("activePack");
		if (active != null && active.isJsonPrimitive() && active.getAsJsonPrimitive().isString()) {
			String name = active.getAsString();
			activePack = packs.contains(name) ? name : "";
		}
		if (o.has("presets") && o.get("presets").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("presets")) {
				Preset p = Preset.fromJson(e);
				if (p != null && findPreset(p.name()) == null) putPreset(p);
			}
		}
	}
}
