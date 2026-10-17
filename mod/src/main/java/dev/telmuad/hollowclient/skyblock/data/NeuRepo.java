package dev.telmuad.hollowclient.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The NotEnoughUpdates item repository (MIT license, github.com/NotEnoughUpdates/NotEnoughUpdates-REPO): item names,
 * lore, recipes and constants (pet levels, essence costs, ...), the community database SkyBlock mods use.
 * <p>
 * It's downloaded as one zip into {@code config/hollowclient/skyblock/neu/}, at most once a day and only when the
 * repository changed (checked against its latest commit). Files are read straight out of the zip by name; nothing is
 * extracted, so a malformed zip can't write anywhere. Plain Java.
 */
public final class NeuRepo {
	public static final String COMMIT_URL = "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master";
	public static final String ZIP_URL = "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";
	public static final long CHECK_EVERY = 24 * 3_600_000L;
	private static final int CACHE = 512;

	private final Path dir;
	private final LongSupplier clock;
	private ZipFile zip;
	private String prefix = "";
	private final Map<String, JsonObject> cache = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, JsonObject> eldest) {
			return size() > CACHE;
		}
	};
	private String error = "";

	public NeuRepo(Path dir, LongSupplier clock) {
		this.dir = dir;
		this.clock = clock;
	}

	public Path zipFile() {
		return dir.resolve("repo.zip");
	}

	private Path shaFile() {
		return dir.resolve("repo.sha");
	}

	private Path checkedFile() {
		return dir.resolve("checked");
	}

	public synchronized boolean available() {
		return zip != null;
	}

	public String error() {
		return error;
	}

	/** Opens the downloaded zip if there is one. */
	public synchronized boolean open() {
		close();
		Path f = zipFile();
		if (!Files.isRegularFile(f)) return false;
		try {
			zip = new ZipFile(f.toFile(), StandardCharsets.UTF_8);
			prefix = "";
			Enumeration<? extends ZipEntry> en = zip.entries();
			if (en.hasMoreElements()) {
				String first = en.nextElement().getName();
				int slash = first.indexOf('/');
				if (slash > 0) prefix = first.substring(0, slash + 1); // GitHub zips have one top folder
			}
			cache.clear();
			return true;
		} catch (IOException e) {
			error = "the item repository is damaged: " + e.getMessage();
			zip = null;
			return false;
		}
	}

	public synchronized void close() {
		if (zip != null) {
			try {
				zip.close();
			} catch (IOException ignored) {
			}
		}
		zip = null;
	}

	/**
	 * Downloads the repository when it changed (checked at most once a day). Runs on the calling thread.
	 * {@code commit} answers the latest commit hash; {@code download} saves the zip to the given path.
	 */
	public void updateIfDue(Http.Fetcher commit, Downloader download, boolean force) {
		try {
			Files.createDirectories(dir);
			long last = Files.isRegularFile(checkedFile()) ? Files.getLastModifiedTime(checkedFile()).toMillis() : 0;
			if (!force && Files.isRegularFile(zipFile()) && clock.getAsLong() - last < CHECK_EVERY) {
				if (!available()) open();
				return;
			}
			String sha = new String(commit.get(COMMIT_URL), StandardCharsets.UTF_8).strip();
			if (!sha.matches("[0-9a-f]{40}")) throw new IOException("unexpected commit answer");
			String have = Files.isRegularFile(shaFile()) ? Files.readString(shaFile()).strip() : "";
			if (!sha.equals(have) || !Files.isRegularFile(zipFile())) {
				Path tmp = dir.resolve("repo.zip.download");
				download.save(ZIP_URL, tmp);
				try (ZipFile check = new ZipFile(tmp.toFile())) {
					if (check.size() < 10) throw new IOException("the download looks incomplete");
				}
				synchronized (this) {
					close();
					Files.move(tmp, zipFile(), StandardCopyOption.REPLACE_EXISTING);
				}
				Files.writeString(shaFile(), sha);
			}
			Files.writeString(checkedFile(), Long.toString(clock.getAsLong()));
			Files.setLastModifiedTime(checkedFile(), java.nio.file.attribute.FileTime.fromMillis(clock.getAsLong()));
			error = "";
			if (!available()) open();
		} catch (IOException | RuntimeException e) {
			error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			if (!available()) open();
		}
	}

	/** Saves a URL's body to a file. */
	public interface Downloader {
		void save(String url, Path target) throws IOException;
	}

	/** {@code items/<ID>.json}, or null. */
	public JsonObject item(String id) {
		if (id == null || !id.matches("[A-Za-z0-9_;:\\-.]+")) return null;
		return read("items/" + id.replace(':', '-') + ".json");
	}

	/** {@code constants/<name>.json}, or null. */
	public JsonObject constant(String name) {
		if (name == null || !name.matches("[A-Za-z0-9_\\-]+")) return null;
		return read("constants/" + name + ".json");
	}

	private synchronized JsonObject read(String path) {
		if (zip == null) return null;
		if (cache.containsKey(path)) return cache.get(path);
		ZipEntry e = zip.getEntry(prefix + path);
		JsonObject out = null;
		if (e != null && !e.isDirectory() && e.getSize() < 8_000_000) {
			try (InputStream in = zip.getInputStream(e)) {
				JsonElement el = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				if (el.isJsonObject()) out = el.getAsJsonObject();
			} catch (IOException | RuntimeException ignored) {
				out = null;
			}
		}
		cache.put(path, out);
		return out;
	}
}
