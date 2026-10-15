package dev.telmuad.hollowclient.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.setting.Setting;
import dev.telmuad.hollowclient.util.AtomicFiles;
import dev.telmuad.hollowclient.util.FileNames;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Profiles: one click to set up Hollow for PvP, building or filming, plus your own saved setups. Built-in profiles
 * only change the modules and settings they name; your own profiles are full snapshots of every module (with HUD
 * positions). Before the first profile is applied, your settings are saved, so "Back to my settings" undoes it.
 */
public final class Profiles {
	/** One change: a module's on/off state ({@code setting == null}) or one of its settings. */
	public record Change(String module, String setting, JsonElement value) {
	}

	public record Builtin(String id, String name, String description, List<Change> changes) {
	}

	private static final List<String> ALL_HUD = List.of("fps", "coordinates", "cps", "keystrokes", "armor", "potions", "ping", "clock",
			"toggle_status", "session_stats");

	public static final List<Builtin> BUILTIN = List.of(
			new Builtin("pvp", "PvP", "Clear view: no screen effects, a combat HUD (keystrokes, CPS, armor, potions), low fire.", pvp()),
			new Builtin("building", "Building", "Coordinates and clock, a filled block outline, fullbright, zoom and waypoints.", building()),
			new Builtin("cinematic", "Cinematic", "Film look: color grade, bloom, grain, motion blur, aurora, no HUD, camera paths.", cinematic()));

	private static String active;
	private static JsonObject before;

	private Profiles() {
	}

	private static Change on(String module, boolean value) {
		return new Change(module, null, new JsonPrimitive(value));
	}

	private static Change set(String module, String setting, boolean value) {
		return new Change(module, setting, new JsonPrimitive(value));
	}

	private static Change set(String module, String setting, Number value) {
		return new Change(module, setting, new JsonPrimitive(value));
	}

	private static Change set(String module, String setting, String value) {
		return new Change(module, setting, new JsonPrimitive(value));
	}

	private static List<Change> huds(List<String> shown) {
		List<Change> out = new ArrayList<>();
		for (String id : ALL_HUD) out.add(on(id, shown.contains(id)));
		return out;
	}

	private static List<Change> pvp() {
		List<Change> c = new ArrayList<>(huds(List.of("fps", "cps", "keystrokes", "armor", "potions", "ping", "toggle_status")));
		c.add(on("post_processing", false));
		c.add(on("sky", false));
		c.add(set("particles", "trail", false));
		c.add(set("particles", "footprints", false));
		c.add(on("hit_effects", true));
		c.add(set("hit_effects", "hitBurst", true));
		c.add(set("hit_effects", "hitBurstStrength", 35));
		c.add(on("hand_animations", true));
		c.add(set("hand_animations", "smoothSwitch", true));
		c.add(on("low_fire", true));
		c.add(on("clear_water", true));
		c.add(on("crosshair", true));
		c.add(on("toggle_sprint", true));
		c.add(on("camera_path", false));
		return c;
	}

	private static List<Change> building() {
		List<Change> c = new ArrayList<>(huds(List.of("fps", "coordinates", "clock", "toggle_status")));
		c.add(on("block_outline", true));
		c.add(set("block_outline", "fill", true));
		c.add(on("fullbright", true));
		c.add(on("zoom", true));
		c.add(on("waypoints", true));
		c.add(set("post_processing", "motionBlur", false));
		c.add(set("post_processing", "shake", false));
		c.add(set("particles", "trail", false));
		c.add(on("hit_effects", false));
		return c;
	}

	private static List<Change> cinematic() {
		List<Change> c = new ArrayList<>(huds(List.of()));
		c.add(on("post_processing", true));
		c.add(set("post_processing", "grade", "CINEMATIC"));
		c.add(set("post_processing", "bloom", true));
		c.add(set("post_processing", "vignette", true));
		c.add(set("post_processing", "grain", true));
		c.add(set("post_processing", "motionBlur", true));
		c.add(on("sky", true));
		c.add(set("sky", "aurora", true));
		c.add(set("sky", "shootingStars", true));
		c.add(on("fullbright", false));
		c.add(on("zoom", true));
		c.add(set("zoom", "smooth", true));
		c.add(set("zoom", "cinematic", true));
		c.add(on("hand_animations", true));
		c.add(set("hand_animations", "smoothSwitch", true));
		c.add(on("camera_path", true));
		return c;
	}

	// ------------------------------------------------------------------------------------------------ state

	private static Path dir() {
		return HollowClient.dataDir().resolve("profiles");
	}

	public static void load() {
		Path state = dir().resolve("state.json");
		if (!Files.isRegularFile(state)) return;
		try {
			JsonObject o = JsonParser.parseString(Files.readString(state, StandardCharsets.UTF_8)).getAsJsonObject();
			active = o.has("active") && !o.get("active").isJsonNull() ? o.get("active").getAsString() : null;
			before = o.has("before") && o.get("before").isJsonObject() ? o.getAsJsonObject("before") : null;
		} catch (IOException | RuntimeException e) {
			HollowClient.LOG.warn("Couldn't read the profile state", e);
		}
	}

	private static void saveState() {
		JsonObject o = new JsonObject();
		o.addProperty("active", active);
		if (before != null) o.add("before", before);
		try {
			Files.createDirectories(dir());
			AtomicFiles.write(dir().resolve("state.json"), o.toString());
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't save the profile state", e);
		}
	}

	/** The name of the profile in use, or null. */
	public static String active() {
		return active;
	}

	public static boolean canGoBack() {
		return before != null;
	}

	private static JsonObject snapshot(ModuleRegistry registry) {
		JsonObject modules = new JsonObject();
		for (Module m : registry.all()) modules.add(m.id(), ConfigCodec.encodeModule(m, true));
		return modules;
	}

	private static void restore(ModuleRegistry registry, JsonObject modules) {
		for (Module m : registry.all()) {
			if (modules.has(m.id())) ConfigCodec.decodeModule(m, modules.get(m.id()), false, true);
		}
		HollowClient.config().markDirty();
	}

	private static void rememberBefore(ModuleRegistry registry) {
		if (before == null) before = snapshot(registry);
	}

	/** Apply a built-in profile. Returns how many of its entries didn't match a module or setting (0 normally). */
	public static int apply(ModuleRegistry registry, Builtin profile) {
		rememberBefore(registry);
		int missing = 0;
		for (Change c : profile.changes()) {
			Optional<Module> m = registry.get(c.module());
			if (m.isEmpty()) {
				missing++;
				continue;
			}
			if (c.setting() == null) {
				m.get().setEnabled(c.value().getAsBoolean());
				continue;
			}
			Setting<?> s = m.get().setting(c.setting());
			if (s == null) {
				missing++;
				continue;
			}
			s.fromJson(c.value());
		}
		active = profile.name();
		saveState();
		HollowClient.config().markDirty();
		return missing;
	}

	/** Undo profiles: back to the settings you had before the first one. */
	public static void goBack(ModuleRegistry registry) {
		if (before == null) return;
		restore(registry, before);
		before = null;
		active = null;
		saveState();
	}

	// ------------------------------------------------------------------------------------------------ your own

	public static List<String> saved() {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir())) return out;
		try (DirectoryStream<Path> s = Files.newDirectoryStream(dir(), "*.json")) {
			for (Path p : s) {
				String n = p.getFileName().toString();
				if (n.equals("state.json")) continue;
				out.add(n.substring(0, n.length() - 5));
			}
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't list profiles", e);
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	private static Path file(String name) {
		return dir().resolve(FileNames.safeFileName(name) + ".json");
	}

	public static void save(ModuleRegistry registry, String name) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.add("modules", snapshot(registry));
		Files.createDirectories(dir());
		AtomicFiles.write(file(name), o.toString());
		active = name;
		saveState();
	}

	public static boolean applySaved(ModuleRegistry registry, String name) {
		try {
			JsonObject o = JsonParser.parseString(Files.readString(file(name), StandardCharsets.UTF_8)).getAsJsonObject();
			if (!o.has("modules") || !o.get("modules").isJsonObject()) return false;
			rememberBefore(registry);
			restore(registry, o.getAsJsonObject("modules"));
			active = name;
			saveState();
			return true;
		} catch (IOException | RuntimeException e) {
			HollowClient.LOG.warn("Couldn't apply profile {}", name, e);
			return false;
		}
	}

	public static void delete(String name) throws IOException {
		Files.deleteIfExists(file(name));
		if (name.equals(active)) {
			active = null;
			saveState();
		}
	}

	/** Names of the HUD elements a built-in profile shows (for its card). */
	public static List<String> hudShown(Builtin profile, ModuleRegistry registry) {
		List<String> out = new ArrayList<>();
		for (Change c : profile.changes()) {
			if (c.setting() != null || !c.value().getAsBoolean()) continue;
			registry.get(c.module()).filter(m -> m instanceof HudModule).ifPresent(m -> out.add(m.name()));
		}
		return out;
	}
}
