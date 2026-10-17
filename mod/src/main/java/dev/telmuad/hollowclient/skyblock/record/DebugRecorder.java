package dev.telmuad.hollowclient.skyblock.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockInput;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Writes everything SkyBlock Mode reads to {@code hollow-debug/sb-<time>.jsonl} in the game folder, one JSON object per
 * line: {@code {"t": <ms since start>, "type": "sidebar"|"tab"|"chat"|"actionbar"|"title"|"container"|...}}.
 * The simulator replays the same format, so a recording from a real SkyBlock session becomes a test scenario.
 * <p>
 * Privacy: other players' chat (public, private, party, guild, co-op) never reaches the recorder (see
 * {@link SkyBlockInput#chat}); player names that appear anywhere else are replaced with Player1, Player2, ...;
 * your own name becomes "Me". The files stay on your computer. Plain Java.
 */
public final class DebugRecorder implements SkyBlockInput.Recorder {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Path dir;
	private final LongSupplier clock;
	private Supplier<Collection<String>> playerNames = List::of;
	private String ownName = "";
	private final Map<String, String> aliases = new LinkedHashMap<>();
	private BufferedWriter out;
	private Path file;
	private long start;
	private int lines;

	public DebugRecorder(Path dir, LongSupplier clock) {
		this.dir = dir;
		this.clock = clock;
	}

	/** Where the names to hide come from (the tab list's real players) and your own name. */
	public void setNames(Supplier<Collection<String>> playerNames, String ownName) {
		this.playerNames = playerNames == null ? List::of : playerNames;
		this.ownName = ownName == null ? "" : ownName;
	}

	public boolean active() {
		return out != null;
	}

	public Path file() {
		return file;
	}

	public int lines() {
		return lines;
	}

	public synchronized Path start() throws IOException {
		if (out != null) return file;
		Files.createDirectories(dir);
		file = dir.resolve("sb-" + LocalDateTime.now().format(STAMP) + ".jsonl");
		out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
		start = clock.getAsLong();
		lines = 0;
		aliases.clear();
		return file;
	}

	public synchronized Path stop() {
		Path f = file;
		if (out != null) {
			try {
				out.close();
			} catch (IOException ignored) {
			}
		}
		out = null;
		return f;
	}

	public synchronized void flush() {
		if (out == null) return;
		try {
			out.flush();
		} catch (IOException e) {
			stop();
		}
	}

	@Override
	public synchronized void record(JsonObject entry) {
		if (out == null) return;
		JsonObject line = new JsonObject();
		line.addProperty("t", clock.getAsLong() - start);
		for (Map.Entry<String, com.google.gson.JsonElement> e : entry.entrySet()) line.add(e.getKey(), e.getValue());
		try {
			out.write(anonymize(GSON.toJson(line)));
			out.newLine();
			lines++;
		} catch (IOException e) {
			stop();
		}
	}

	/** Replaces player names (longest first, whole words only) with stable aliases. */
	public String anonymize(String text) {
		List<String> names = new ArrayList<>(playerNames.get());
		if (!ownName.isEmpty()) names.add(ownName);
		names.sort(Comparator.comparingInt(String::length).reversed());
		String out = text;
		for (String n : names) {
			if (n.length() < 3 || !out.contains(n)) continue;
			String alias = n.equals(ownName) ? "Me" : aliases.computeIfAbsent(n, k -> "Player" + (aliases.size() + 1));
			// a name may follow a formatting code ("§aName"), which isn't part of the name
			out = out.replaceAll("(?:(?<=§[0-9a-fk-orA-FK-OR])|(?<![A-Za-z0-9_]))" + java.util.regex.Pattern.quote(n)
					+ "(?![A-Za-z0-9_])", alias);
		}
		return out;
	}

	/** One JSON object (item dump) to its own file next to the recordings. */
	public Path writeDump(String prefix, JsonObject data) throws IOException {
		Files.createDirectories(dir);
		Path f = dir.resolve(prefix + "-" + LocalDateTime.now().format(STAMP) + ".json");
		Files.writeString(f, anonymize(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data)),
				StandardCharsets.UTF_8);
		return f;
	}
}
