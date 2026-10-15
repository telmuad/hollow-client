package dev.telmuad.hollowclient.waypoints;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.qol.WaypointsModule;
import dev.telmuad.hollowclient.util.AtomicFiles;
import dev.telmuad.hollowclient.util.FileNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Waypoints and the camera path of the singleplayer world you are in. They only exist in your own singleplayer
 * worlds (not on servers, and not while the world is open to LAN), so they can never help in multiplayer.
 */
public final class Waypoints {
	private static String worldKey;
	private static WorldData data = new WorldData();
	private static long dirtyAt;
	private static boolean wasDead;

	private Waypoints() {
	}

	/** True in your own singleplayer world that isn't open to LAN. */
	public static boolean allowed(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		return mc.level != null && mc.player != null && server != null && !server.isPublished();
	}

	public static WorldData data() {
		return data;
	}

	public static String dimension(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	/** The world's save folder name, made safe for a file name. */
	private static String worldKey(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.level == null) return null;
		Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		Path name = root.getFileName();
		return name == null ? null : FileNames.safeFileName(name.toString());
	}

	private static Path file(String key) {
		return HollowClient.dataDir().resolve("worlds").resolve(key + ".json");
	}

	/** Every client tick: load when the world changes, drop a death point, save changes after a second. */
	public static void tick(Minecraft mc) {
		String key = worldKey(mc);
		if (!Objects.equals(key, worldKey)) {
			saveNow();
			worldKey = key;
			data = key == null ? new WorldData() : load(key);
			wasDead = false;
		}
		if (key != null && allowed(mc)) {
			boolean dead = mc.player.isDeadOrDying();
			WaypointsModule m = Modules.WAYPOINTS;
			if (dead && !wasDead && m != null && m.isEnabled() && m.deathPoints()) addDeathPoint(mc);
			wasDead = dead;
		}
		if (dirtyAt != 0 && Util.getMillis() - dirtyAt > 1000) saveNow();
	}

	private static WorldData load(String key) {
		Path f = file(key);
		if (!Files.isRegularFile(f)) return new WorldData();
		try {
			return WorldData.fromJson(Files.readString(f, StandardCharsets.UTF_8));
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't read waypoints for {}", key, e);
			return new WorldData();
		}
	}

	public static void changed() {
		dirtyAt = Util.getMillis();
	}

	public static void saveNow() {
		if (dirtyAt == 0 || worldKey == null) {
			dirtyAt = 0;
			return;
		}
		dirtyAt = 0;
		try {
			Path f = file(worldKey);
			Files.createDirectories(f.getParent());
			AtomicFiles.write(f, data.toJson());
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't save waypoints", e);
		}
	}

	/** Waypoints of the dimension you are in. */
	public static List<Waypoint> here(Minecraft mc) {
		String dim = dimension(mc);
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : data.waypoints) if (w.dimension.equals(dim)) out.add(w);
		return out;
	}

	/** Add a waypoint where you stand. Returns null outside singleplayer. */
	public static Waypoint addHere(Minecraft mc, String name) {
		if (!allowed(mc)) {
			Toasts.info("Waypoints are singleplayer only", "They work in your own worlds, never on servers.");
			return null;
		}
		String n = name == null || name.isBlank() ? nextName() : name.trim();
		Waypoint w = new Waypoint(n, Math.floor(mc.player.getX()) + 0.5, Math.floor(mc.player.getY()), Math.floor(mc.player.getZ()) + 0.5,
				dimension(mc), Waypoint.PALETTE[(data.waypoints.size() + 1) % Waypoint.PALETTE.length]);
		data.waypoints.add(w);
		changed();
		return w;
	}

	private static String nextName() {
		int n = 1;
		while (true) {
			String candidate = "Waypoint " + n;
			boolean used = false;
			for (Waypoint w : data.waypoints) {
				if (w.name.equalsIgnoreCase(candidate)) {
					used = true;
					break;
				}
			}
			if (!used) return candidate;
			n++;
		}
	}

	/** Where you died: one death point per world (the newest replaces the old one). */
	private static void addDeathPoint(Minecraft mc) {
		data.waypoints.removeIf(w -> w.death);
		Waypoint w = new Waypoint("Death", mc.player.getX(), Math.floor(mc.player.getY()), mc.player.getZ(), dimension(mc), 0xFFEF4444);
		w.death = true;
		data.waypoints.add(w);
		changed();
		Toasts.info("Death point saved", String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(w.x), (int) w.y, (int) Math.floor(w.z)));
	}

	public static void remove(Waypoint w) {
		data.waypoints.remove(w);
		changed();
	}
}
