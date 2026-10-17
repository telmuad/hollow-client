package dev.telmuad.hollowclient.skyblock.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition;
import dev.telmuad.hollowclient.hud.HudRenderer;
import dev.telmuad.hollowclient.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A separate HUD layout while on SkyBlock (or one per island): which HUD elements are on and where they are. Joining
 * SkyBlock saves the layout you had and loads the SkyBlock one; leaving swaps back. The first time on an island
 * that has no layout yet, it starts from the shared SkyBlock layout. Saved in
 * {@code config/hollowclient/skyblock/hud_layouts.json}.
 */
public final class HudLayouts {
	public static final String OUTSIDE = "outside";
	public static final String SKYBLOCK = "skyblock";

	private final Path file;
	private JsonObject layouts = new JsonObject();
	private String active = OUTSIDE;

	public HudLayouts(Path file) {
		this.file = file;
		load();
	}

	public String active() {
		return active;
	}

	private void load() {
		try {
			if (!Files.isRegularFile(file)) return;
			JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!e.isJsonObject()) return;
			JsonObject o = e.getAsJsonObject();
			if (o.has("layouts") && o.get("layouts").isJsonObject()) layouts = o.getAsJsonObject("layouts");
			if (o.has("active")) active = o.get("active").getAsString();
		} catch (IOException | RuntimeException e) {
			HollowClient.LOG.warn("SkyBlock HUD layouts couldn't be read: {}", e.toString());
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		o.addProperty("active", active);
		o.add("layouts", layouts);
		try {
			Files.createDirectories(file.getParent());
			AtomicFiles.write(file, new GsonBuilder().setPrettyPrinting().create().toJson(o));
		} catch (IOException e) {
			HollowClient.LOG.warn("SkyBlock HUD layouts couldn't be saved: {}", e.toString());
		}
	}

	/** At startup: if the game closed while a SkyBlock layout was on, put your normal layout back. */
	public void startup() {
		if (!OUTSIDE.equals(active) && layouts.has(OUTSIDE)) {
			apply(layouts.getAsJsonObject(OUTSIDE));
			active = OUTSIDE;
			save();
		}
	}

	/** Switch to the layout called {@code key} (saving the current one under its own name first). */
	public void enter(String key) {
		if (key.equals(active)) return;
		layouts.add(active, snapshot());
		if (layouts.has(key)) apply(layouts.getAsJsonObject(key));
		else if (key.startsWith(SKYBLOCK + ":") && layouts.has(SKYBLOCK)) apply(layouts.getAsJsonObject(SKYBLOCK));
		active = key;
		save();
	}

	private static JsonObject snapshot() {
		JsonObject out = new JsonObject();
		for (HudModule m : HudRenderer.hudModules()) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.isEnabled());
			o.add("hud", m.hudPosition().toJson());
			out.add(m.id(), o);
		}
		return out;
	}

	private static void apply(JsonObject layout) {
		for (HudModule m : HudRenderer.hudModules()) {
			JsonElement e = layout.get(m.id());
			if (e == null || !e.isJsonObject()) continue;
			JsonObject o = e.getAsJsonObject();
			if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
			if (o.has("hud")) m.hudPosition().copyFrom(HudPosition.fromJson(o.get("hud"), m.defaultHudPosition()));
			m.positionChanged();
		}
	}
}
