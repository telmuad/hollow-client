package dev.telmuad.hollowclient.skyblock.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;

import java.util.ArrayList;
import java.util.List;

/**
 * An item as plain data: slot, material id, count, name and lore (formatting kept) and the custom data as JSON. This is
 * what the game adapter produces, what the debug recorder writes and what the simulator replays.
 */
public record RawItem(int slot, String material, int count, String name, List<String> lore, JsonObject customData) {
	public ItemParser.SbItem parse() {
		return ItemParser.parse(customData, name, lore, count);
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("slot", slot);
		o.addProperty("material", material);
		o.addProperty("count", count);
		o.addProperty("name", name);
		JsonArray l = new JsonArray();
		for (String s : lore) l.add(s);
		o.add("lore", l);
		if (customData != null) o.add("custom_data", customData);
		return o;
	}

	public static RawItem fromJson(JsonObject o) {
		List<String> lore = new ArrayList<>();
		JsonElement l = o.get("lore");
		if (l != null && l.isJsonArray()) for (JsonElement e : l.getAsJsonArray()) lore.add(e.getAsString());
		JsonElement cd = o.get("custom_data");
		return new RawItem(o.has("slot") ? o.get("slot").getAsInt() : -1,
				o.has("material") ? o.get("material").getAsString() : "minecraft:air",
				o.has("count") ? o.get("count").getAsInt() : 1,
				o.has("name") ? o.get("name").getAsString() : "",
				lore, cd != null && cd.isJsonObject() ? cd.getAsJsonObject() : new JsonObject());
	}
}
