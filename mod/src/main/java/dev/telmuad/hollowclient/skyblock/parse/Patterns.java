package dev.telmuad.hollowclient.skyblock.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Every text pattern SkyBlock Mode matches, by group ({@code sidebar}, {@code tab}, {@code actionbar}, {@code chat},
 * ...). They live in {@code assets/hollowclient/skyblock/patterns/<group>.json} as {@code "name": "regex"}, so a
 * changed Hypixel message is a data fix, not a code change. A file with the same name in the override folder
 * ({@code config/hollowclient/skyblock/patterns}) replaces single patterns without rebuilding the mod.
 * <p>
 * Patterns are matched against cleaned text (see {@link dev.telmuad.hollowclient.skyblock.text.SbText#clean}).
 * A pattern that is missing or doesn't compile never matches and is listed in {@link #problems()}.
 */
public final class Patterns {
	private static final String RESOURCE = "/assets/hollowclient/skyblock/patterns/";
	private static final Pattern NEVER = Pattern.compile("(?!)");
	private static final Map<String, Patterns> GROUPS = new ConcurrentHashMap<>();
	private static final List<String> PROBLEMS = Collections.synchronizedList(new ArrayList<>());
	private static volatile Path overrideDir;

	private final String group;
	private final Map<String, Pattern> patterns;

	private Patterns(String group, Map<String, Pattern> patterns) {
		this.group = group;
		this.patterns = patterns;
	}

	public static Patterns of(String group) {
		return GROUPS.computeIfAbsent(group, Patterns::load);
	}

	/** Where user overrides are read from (set once at startup; null = none). Clears the loaded groups. */
	public static void setOverrideDir(Path dir) {
		overrideDir = dir;
		GROUPS.clear();
	}

	/** Forget the loaded groups so the next use reads the files again. */
	public static void reload() {
		PROBLEMS.clear();
		GROUPS.clear();
	}

	/** Patterns that were asked for but are missing, or that don't compile ("group.name: reason"). */
	public static List<String> problems() {
		synchronized (PROBLEMS) {
			return new ArrayList<>(PROBLEMS);
		}
	}

	public String group() {
		return group;
	}

	public Set<String> names() {
		return Collections.unmodifiableSet(patterns.keySet());
	}

	public boolean has(String name) {
		return patterns.containsKey(name);
	}

	public Pattern get(String name) {
		Pattern p = patterns.get(name);
		if (p == null) {
			String problem = group + "." + name + ": missing";
			if (!PROBLEMS.contains(problem)) PROBLEMS.add(problem);
			return NEVER;
		}
		return p;
	}

	/** The matcher if the whole text matches, else null. */
	public Matcher matches(String name, String text) {
		if (text == null) return null;
		Matcher m = get(name).matcher(text);
		return m.matches() ? m : null;
	}

	/** The matcher at the first match anywhere in the text, else null. */
	public Matcher find(String name, String text) {
		if (text == null) return null;
		Matcher m = get(name).matcher(text);
		return m.find() ? m : null;
	}

	/** A named group of a match, or null when the group didn't take part (or doesn't exist). */
	public static String group(Matcher m, String groupName) {
		try {
			return m.group(groupName);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------------------------------------ loading

	private static Patterns load(String group) {
		Map<String, String> sources = new LinkedHashMap<>();
		try (InputStream in = Patterns.class.getResourceAsStream(RESOURCE + group + ".json")) {
			if (in != null) read(new InputStreamReader(in, StandardCharsets.UTF_8), sources);
			else PROBLEMS.add(group + ": no pattern file");
		} catch (IOException | RuntimeException e) {
			PROBLEMS.add(group + ": " + e.getMessage());
		}
		Path dir = overrideDir;
		if (dir != null) {
			Path f = dir.resolve(group + ".json");
			if (Files.isRegularFile(f)) {
				try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
					read(r, sources);
				} catch (IOException | RuntimeException e) {
					PROBLEMS.add(group + " (override): " + e.getMessage());
				}
			}
		}
		Map<String, Pattern> compiled = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : sources.entrySet()) {
			try {
				compiled.put(e.getKey(), Pattern.compile(e.getValue()));
			} catch (PatternSyntaxException ex) {
				PROBLEMS.add(group + "." + e.getKey() + ": " + ex.getDescription());
			}
		}
		return new Patterns(group, compiled);
	}

	private static void read(Reader reader, Map<String, String> into) {
		JsonElement root = JsonParser.parseReader(reader);
		if (!root.isJsonObject()) return;
		JsonObject o = root.getAsJsonObject();
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (e.getKey().startsWith("_")) continue; // comments
			if (e.getValue().isJsonPrimitive()) into.put(e.getKey(), e.getValue().getAsString());
		}
	}
}
