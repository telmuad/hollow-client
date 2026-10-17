package dev.telmuad.hollowclient.skyblock.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.state.Location;
import dev.telmuad.hollowclient.skyblock.state.RawItem;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockInput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Plays a scenario into {@link SkyBlockInput}: the debug recorder's JSONL format, one input per line, played at its
 * {@code t} (ms). Built-in scenarios live in {@code assets/hollowclient/skyblock/sim/} (listed in index.json); your own
 * recordings in {@code hollow-debug/} can be played too. One extra type, {@code give}, puts an item into your
 * inventory (singleplayer only). Plain Java; the caller makes sure it only runs in singleplayer.
 */
public final class Simulator {
	private static final String RESOURCE = "/assets/hollowclient/skyblock/sim/";

	public record Scenario(String name, String description, boolean recording) {
	}

	private record Step(long t, JsonObject entry) {
	}

	private final List<Step> steps = new ArrayList<>();
	private int next;
	private long startedAt;
	private String name = "";
	private double speed = 1;
	private Consumer<JsonObject> world;

	/** Handles "summon" steps (a name tag floating near you, for HUDs that read visible name tags). */
	public void setWorld(Consumer<JsonObject> world) {
		this.world = world;
	}

	public boolean running() {
		return next < steps.size();
	}

	public String name() {
		return name;
	}

	public int progress() {
		return next;
	}

	public int size() {
		return steps.size();
	}

	/** Built-in scenarios plus recordings (newest first) in {@code debugDir}. */
	public static List<Scenario> list(Path debugDir) {
		List<Scenario> out = new ArrayList<>();
		try (InputStream in = Simulator.class.getResourceAsStream(RESOURCE + "index.json")) {
			if (in != null) {
				JsonObject idx = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement e : idx.getAsJsonArray("scenarios")) {
					JsonObject o = e.getAsJsonObject();
					out.add(new Scenario(o.get("name").getAsString(), o.get("description").getAsString(), false));
				}
			}
		} catch (IOException | RuntimeException ignored) {
		}
		if (debugDir != null && Files.isDirectory(debugDir)) {
			try (Stream<Path> files = Files.list(debugDir)) {
				files.filter(f -> f.getFileName().toString().endsWith(".jsonl"))
						.sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
						.forEach(f -> {
							String n = f.getFileName().toString();
							out.add(new Scenario(n.substring(0, n.length() - ".jsonl".length()), "your recording", true));
						});
			} catch (IOException ignored) {
			}
		}
		return out;
	}

	/** Loads a scenario by name (built-in first, then {@code debugDir/<name>.jsonl}). False if there's no such scenario. */
	public boolean load(String scenario, Path debugDir, long now) throws IOException {
		Reader reader = null;
		InputStream in = Simulator.class.getResourceAsStream(RESOURCE + scenario + ".jsonl");
		if (in != null) reader = new InputStreamReader(in, StandardCharsets.UTF_8);
		else if (debugDir != null && scenario.matches("[A-Za-z0-9_.-]+")) {
			Path f = debugDir.resolve(scenario + ".jsonl");
			if (Files.isRegularFile(f)) reader = Files.newBufferedReader(f, StandardCharsets.UTF_8);
		}
		if (reader == null) return false;
		List<Step> loaded = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.isBlank() || line.startsWith("//")) continue;
				JsonObject o = JsonParser.parseString(line).getAsJsonObject();
				long t = o.has("t") ? o.get("t").getAsLong() : (loaded.isEmpty() ? 0 : loaded.getLast().t());
				loaded.add(new Step(t, o));
			}
		}
		steps.clear();
		steps.addAll(loaded);
		next = 0;
		startedAt = now;
		name = scenario;
		return true;
	}

	public void setSpeed(double speed) {
		this.speed = Math.max(0.1, speed);
	}

	public void stop() {
		next = steps.size();
	}

	/** Plays every step that is due. Returns how many ran. */
	public int tick(long now, SkyBlockInput input, Consumer<RawItem> give) {
		int ran = 0;
		while (next < steps.size() && steps.get(next).t() <= (now - startedAt) * speed) {
			apply(steps.get(next++).entry(), input, give, world);
			ran++;
		}
		return ran;
	}

	/** Plays the whole scenario at once (tests). */
	public void runAll(SkyBlockInput input, Consumer<RawItem> give) {
		while (next < steps.size()) apply(steps.get(next++).entry(), input, give, world);
	}

	/** One recorded input into SkyBlock Mode. Unknown types are skipped. */
	public static void apply(JsonObject e, SkyBlockInput input, Consumer<RawItem> give) {
		apply(e, input, give, null);
	}

	public static void apply(JsonObject e, SkyBlockInput input, Consumer<RawItem> give, Consumer<JsonObject> world) {
		String type = e.has("type") ? e.get("type").getAsString() : "";
		switch (type) {
			case "location" -> input.location(new Location(s(e, "serverName"), s(e, "serverType"), s(e, "lobbyName"),
					s(e, "mode"), s(e, "map")));
			case "left" -> input.left();
			case "sidebar" -> input.sidebar(s(e, "title"), strings(e.getAsJsonArray("lines")));
			case "tab" -> input.tabList(strings(e.getAsJsonArray("lines")));
			case "chat" -> input.chat(s(e, "text"));
			case "actionbar" -> input.actionBar(s(e, "text"));
			case "title" -> input.title(s(e, "title"), s(e, "subtitle"));
			case "container" -> {
				List<RawItem> items = new ArrayList<>();
				JsonArray a = e.getAsJsonArray("items");
				if (a != null) for (JsonElement i : a) items.add(RawItem.fromJson(i.getAsJsonObject()));
				input.container(s(e, "title"), items);
			}
			case "container_closed" -> input.containerClosed();
			case "give" -> {
				if (give != null && e.has("item")) give.accept(RawItem.fromJson(e.getAsJsonObject("item")));
			}
			case "summon", "clear_summons" -> {
				if (world != null) world.accept(e);
			}
			default -> {
			}
		}
	}

	private static String s(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static List<String> strings(JsonArray a) {
		List<String> out = new ArrayList<>();
		if (a != null) for (JsonElement e : a) out.add(e.getAsString());
		return out;
	}
}
