package dev.telmuad.hollowclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Free text, capped in length and stripped of control characters and the section sign.
 * Used for values picked elsewhere (for example the selected cape id); it has no widget of its own.
 */
public final class StringSetting extends Setting<String> {
	private final int maxLength;

	public StringSetting(String id, String name, String defaultValue, int maxLength) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
	}

	public static String clean(String raw, int maxLength) {
		StringBuilder out = new StringBuilder();
		raw.codePoints().filter(cp -> cp >= 0x20 && cp != 0x7F && cp != 0xA7).limit(maxLength).forEach(out::appendCodePoint);
		return out.toString();
	}

	@Override
	protected String sanitize(String raw) {
		return clean(raw, maxLength);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) set(json.getAsString());
		else reset();
	}
}
