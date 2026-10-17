package dev.telmuad.hollowclient.skyblock.features.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Item protection: items you've locked (by their SkyBlock uuid) and hotbar/inventory slots you've locked (for items
 * without a uuid). A locked item can't be dropped, sold or salvaged by mistake; that's the one vanilla action SkyBlock
 * Mode ever blocks. Plain Java.
 */
public final class ItemLocks {
	private final Set<String> uuids = new LinkedHashSet<>();
	private final Set<Integer> slots = new LinkedHashSet<>();

	public boolean locked(ItemParser.SbItem item, int inventorySlot) {
		if (item != null && !item.uuid().isEmpty() && uuids.contains(item.uuid())) return true;
		return inventorySlot >= 0 && slots.contains(inventorySlot);
	}

	/** Locks or unlocks: by uuid when the item has one, else the player inventory slot (-1 = nothing to lock). Returns the new state. */
	public boolean toggle(ItemParser.SbItem item, int inventorySlot) {
		if (item != null && !item.uuid().isEmpty()) {
			if (!uuids.remove(item.uuid())) {
				uuids.add(item.uuid());
				return true;
			}
			return false;
		}
		if (inventorySlot < 0) return false;
		if (!slots.remove(inventorySlot)) {
			slots.add(inventorySlot);
			return true;
		}
		return false;
	}

	public int size() {
		return uuids.size() + slots.size();
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		JsonArray u = new JsonArray();
		uuids.forEach(u::add);
		JsonArray s = new JsonArray();
		slots.forEach(s::add);
		o.add("uuids", u);
		o.add("slots", s);
		return o;
	}

	public void load(JsonObject o) {
		uuids.clear();
		slots.clear();
		if (o == null) return;
		if (o.has("uuids") && o.get("uuids").isJsonArray()) for (JsonElement e : o.getAsJsonArray("uuids")) uuids.add(e.getAsString());
		if (o.has("slots") && o.get("slots").isJsonArray()) for (JsonElement e : o.getAsJsonArray("slots")) slots.add(e.getAsInt());
	}
}
