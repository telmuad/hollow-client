package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.state.RawItem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the SkyBlock test fixtures from src/test/resources/skyblock/fixtures. */
public final class Fixtures {
	private Fixtures() {
	}

	public static JsonObject file(String name) {
		try (InputStream in = Fixtures.class.getResourceAsStream("/skyblock/fixtures/" + name)) {
			if (in == null) throw new IllegalStateException("missing fixture " + name);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static List<String> strings(JsonArray a) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : a) out.add(e.getAsString());
		return out;
	}

	public static String sidebarTitle(String key) {
		return file("sidebars.json").getAsJsonObject(key).get("title").getAsString();
	}

	public static List<String> sidebarLines(String key) {
		return strings(file("sidebars.json").getAsJsonObject(key).getAsJsonArray("lines"));
	}

	public static List<String> tab(String key) {
		return strings(file("tablists.json").getAsJsonArray(key));
	}

	public static RawItem item(String key) {
		return RawItem.fromJson(file("items.json").getAsJsonObject(key));
	}
}
