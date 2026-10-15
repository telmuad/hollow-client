package dev.telmuad.hollowclient.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.camera.CameraPath;

import java.util.ArrayList;
import java.util.List;

/**
 * What Hollow keeps per singleplayer world: waypoints and the camera path. Stored as JSON in
 * {@code config/hollowclient/worlds/<world folder>.json}; anything unreadable is skipped, never fatal.
 */
public final class WorldData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public final List<Waypoint> waypoints = new ArrayList<>();
	public final List<CameraPath.Key> cameraPath = new ArrayList<>();

	public String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray list = new JsonArray();
		for (Waypoint w : waypoints) {
			JsonObject o = new JsonObject();
			o.addProperty("name", w.name);
			o.addProperty("x", w.x);
			o.addProperty("y", w.y);
			o.addProperty("z", w.z);
			o.addProperty("dimension", w.dimension);
			o.addProperty("color", String.format("#%08X", w.color));
			o.addProperty("visible", w.visible);
			if (w.death) o.addProperty("death", true);
			list.add(o);
		}
		root.add("waypoints", list);
		JsonArray path = new JsonArray();
		for (CameraPath.Key k : cameraPath) {
			JsonArray a = new JsonArray();
			a.add(k.x());
			a.add(k.y());
			a.add(k.z());
			a.add(k.yaw());
			a.add(k.pitch());
			path.add(a);
		}
		root.add("cameraPath", path);
		return GSON.toJson(root);
	}

	public static WorldData fromJson(String text) {
		WorldData data = new WorldData();
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject()) return data;
			root = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			return data;
		}
		if (root.get("waypoints") instanceof JsonArray list) {
			for (JsonElement e : list) {
				try {
					JsonObject o = e.getAsJsonObject();
					String name = o.has("name") ? o.get("name").getAsString() : "Waypoint";
					String dim = o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld";
					int color = 0;
					if (o.has("color")) {
						String c = o.get("color").getAsString().replace("#", "");
						color = (int) Long.parseLong(c, 16);
					}
					Waypoint w = new Waypoint(name, o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), dim, color);
					w.visible = !o.has("visible") || o.get("visible").getAsBoolean();
					w.death = o.has("death") && o.get("death").getAsBoolean();
					if (Double.isFinite(w.x) && Double.isFinite(w.y) && Double.isFinite(w.z)) data.waypoints.add(w);
				} catch (RuntimeException ignored) {
					// skip a broken entry, keep the rest
				}
			}
		}
		if (root.get("cameraPath") instanceof JsonArray path) {
			for (JsonElement e : path) {
				try {
					JsonArray a = e.getAsJsonArray();
					data.cameraPath.add(new CameraPath.Key(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
							a.get(3).getAsFloat(), a.get(4).getAsFloat()));
				} catch (RuntimeException ignored) {
					// skip
				}
			}
		}
		return data;
	}
}
