package dev.telmuad.hollowclient.skyblock.state;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.util.AtomicFiles;
import dev.telmuad.hollowclient.util.FileNames;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Files kept per SkyBlock profile: {@code config/hollowclient/skyblock/profiles/<profile>/<name>.json}. The profile
 * is the profile id when known (from the "Profile ID" chat line), else the profile's name. Plain Java.
 */
public final class ProfileStore {
	private final Path root;

	public ProfileStore(Path root) {
		this.root = root;
	}

	public Path root() {
		return root;
	}

	public Path dir(String profileKey) {
		String safe = FileNames.safeFileName(profileKey == null || profileKey.isBlank() ? "default" : profileKey);
		return root.resolve(safe.isBlank() ? "default" : safe);
	}

	public JsonObject read(String profileKey, String name) {
		Path f = dir(profileKey).resolve(name + ".json");
		if (!Files.isRegularFile(f)) return new JsonObject();
		try {
			JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
		} catch (IOException | RuntimeException e) {
			return new JsonObject();
		}
	}

	public void write(String profileKey, String name, JsonObject data) throws IOException {
		Path f = dir(profileKey).resolve(name + ".json");
		Files.createDirectories(f.getParent());
		AtomicFiles.write(f, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data));
	}
}
