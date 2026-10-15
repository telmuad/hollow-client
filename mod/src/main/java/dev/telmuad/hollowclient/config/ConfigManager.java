package dev.telmuad.hollowclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.module.ModuleRegistry;

import dev.telmuad.hollowclient.util.AtomicFiles;

import java.io.IOException;
import java.io.Reader;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Loads and saves {@code config/hollowclient.json}.
 * <ul>
 *   <li>Saves are debounced: many changes in a row become one write, {@value #SAVE_DELAY_MS} ms after the last one.</li>
 *   <li>Writes go to a temp file first and are then moved over the real file, so a crash never leaves half a file.</li>
 *   <li>A file that can't be parsed is renamed to {@code hollowclient.json.corrupt-<time>} and defaults are used.</li>
 * </ul>
 * Plain Java (no Minecraft classes), so it is covered by unit tests.
 */
public final class ConfigManager {
	public static final long SAVE_DELAY_MS = 500;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final ModuleRegistry registry;
	private final StylesConfig styles;
	private final Object lock = new Object();
	private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Hollow Client config saver");
		t.setDaemon(true);
		return t;
	});
	private ScheduledFuture<?> pending;
	private boolean loading;
	private Consumer<String> warn = msg -> {
	};

	public ConfigManager(Path file, ModuleRegistry registry, StylesConfig styles) {
		this.file = file;
		this.registry = registry;
		this.styles = styles;
	}

	public Path file() {
		return file;
	}

	public StylesConfig styles() {
		return styles;
	}

	public void setWarningSink(Consumer<String> sink) {
		this.warn = sink == null ? msg -> {
		} : sink;
	}

	/** Read the file (or defaults) into the modules. Returns true if a usable file was read. */
	public boolean load() {
		JsonObject root = null;
		boolean ok = false;
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (parsed != null && parsed.isJsonObject()) {
					root = parsed.getAsJsonObject();
					ok = true;
				} else {
					warn.accept("Config is not a JSON object, using defaults");
				}
			} catch (IOException | JsonParseException | IllegalStateException e) {
				warn.accept("Config could not be read (" + e.getMessage() + "), using defaults");
			}
			if (!ok) backupBroken();
		}
		synchronized (lock) {
			loading = true;
			try {
				ConfigCodec.decode(root, registry, styles);
			} finally {
				loading = false;
			}
		}
		saveNow();
		return ok;
	}

	/** Schedule a save. Calls during {@link #load()} are ignored. */
	public void markDirty() {
		synchronized (lock) {
			if (loading) return;
			if (pending != null) pending.cancel(false);
			String json = GSON.toJson(ConfigCodec.encode(registry, styles));
			pending = saver.schedule(() -> write(json), SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	/** Write right away (on shutdown, and after loading). */
	public void saveNow() {
		synchronized (lock) {
			if (pending != null) pending.cancel(false);
			pending = null;
			write(GSON.toJson(ConfigCodec.encode(registry, styles)));
		}
	}

	private void write(String json) {
		synchronized (lock) {
			try {
				AtomicFiles.write(file, json);
			} catch (IOException e) {
				warn.accept("Config could not be saved: " + e.getMessage());
			}
		}
	}

	private void backupBroken() {
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + stamp), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			warn.accept("Could not back up the broken config: " + e.getMessage());
		}
	}

	/** Pretty JSON of the current state, for tests and debugging. */
	public String snapshot() {
		return GSON.toJson(ConfigCodec.encode(registry, styles));
	}
}
