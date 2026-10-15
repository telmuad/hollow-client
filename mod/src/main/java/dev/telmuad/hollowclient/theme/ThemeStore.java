package dev.telmuad.hollowclient.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The current theme plus your saved custom themes, kept in {@code config/hollowclient/theme.json}. The launcher reads
 * and writes the same file, so a theme picked in one shows up in the other ({@link #reloadIfChanged()} notices
 * changes made by the launcher while the game runs). Plain Java, unit tested.
 * <pre>
 * { "version": 1, "current": { ...theme... }, "custom": [ { ...theme... } ] }
 * </pre>
 */
public final class ThemeStore {
	public static final int MAX_CUSTOM = 30;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private HollowTheme current = HollowTheme.VOID;
	private final List<HollowTheme> custom = new ArrayList<>();
	private long lastModified = -1;
	private long lastSize = -1;

	public ThemeStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	public HollowTheme current() {
		return current;
	}

	public List<HollowTheme> custom() {
		return Collections.unmodifiableList(custom);
	}

	/** Presets first, then custom themes. */
	public List<HollowTheme> all() {
		List<HollowTheme> out = new ArrayList<>(HollowTheme.PRESETS);
		out.addAll(custom);
		return out;
	}

	public void setCurrent(HollowTheme theme) {
		setCurrent(theme, true);
	}

	/** Switch theme; {@code save = false} while a slider is being dragged (the editor saves when you let go). */
	public void setCurrent(HollowTheme theme, boolean save) {
		if (theme == null || theme.equals(current)) return;
		current = theme;
		if (save) save();
	}

	/** Save as a custom theme (replacing one with the same name, case-insensitive). Preset names get " (custom)". */
	public HollowTheme saveCustom(HollowTheme theme) {
		HollowTheme t = theme;
		if (HollowTheme.preset(t.name()) != null) t = t.withName(t.name() + " (custom)");
		for (int i = 0; i < custom.size(); i++) {
			if (custom.get(i).name().equalsIgnoreCase(t.name())) {
				custom.set(i, t);
				current = t;
				save();
				return t;
			}
		}
		if (custom.size() < MAX_CUSTOM) custom.add(t);
		current = t;
		save();
		return t;
	}

	public boolean deleteCustom(String name) {
		boolean removed = custom.removeIf(t -> t.name().equalsIgnoreCase(name));
		if (removed) save();
		return removed;
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("current", current.toJson());
		JsonArray arr = new JsonArray();
		custom.forEach(t -> arr.add(t.toJson()));
		root.add("custom", arr);
		return root;
	}

	public void fromJson(JsonElement json) {
		custom.clear();
		current = HollowTheme.VOID;
		if (json == null || !json.isJsonObject()) return;
		JsonObject o = json.getAsJsonObject();
		current = HollowTheme.fromJson(o.get("current"), HollowTheme.VOID);
		if (o.has("custom") && o.get("custom").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("custom")) {
				if (custom.size() >= MAX_CUSTOM) break;
				if (!e.isJsonObject()) continue;
				HollowTheme t = HollowTheme.fromJson(e, HollowTheme.VOID.withName("Custom"));
				if (custom.stream().noneMatch(c -> c.name().equalsIgnoreCase(t.name()))) custom.add(t);
			}
		}
	}

	/** Load the file (missing or broken = defaults). Never throws. */
	public void load() {
		if (!Files.isRegularFile(file)) {
			fromJson(null);
			save();
			return;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			fromJson(JsonParser.parseReader(r));
		} catch (IOException | RuntimeException e) {
			fromJson(null);
		}
		remember();
	}

	/** Re-read the file if something else (the launcher) changed it. Returns true if it was reloaded. */
	public boolean reloadIfChanged() {
		try {
			if (!Files.isRegularFile(file)) return false;
			long m = Files.getLastModifiedTime(file).toMillis();
			long s = Files.size(file);
			if (m == lastModified && s == lastSize) return false;
		} catch (IOException e) {
			return false;
		}
		HollowTheme before = current;
		load();
		return !before.equals(current);
	}

	public void save() {
		try {
			Path dir = file.toAbsolutePath().getParent();
			if (dir != null) Files.createDirectories(dir);
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(toJson(), w);
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			remember();
		} catch (IOException ignored) {
			// The theme still works for this session; it just isn't shared.
		}
	}

	private void remember() {
		try {
			lastModified = Files.getLastModifiedTime(file).toMillis();
			lastSize = Files.size(file);
		} catch (IOException e) {
			lastModified = -1;
		}
	}
}
