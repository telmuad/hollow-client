package dev.telmuad.hollowclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String id, String name, boolean defaultValue) {
		super(id, name, defaultValue);
	}

	public boolean on() {
		return get();
	}

	public void toggle() {
		set(!get());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			set(json.getAsBoolean());
		} else {
			reset();
		}
	}

	@Override
	public String display() {
		return get() ? "On" : "Off";
	}
}
