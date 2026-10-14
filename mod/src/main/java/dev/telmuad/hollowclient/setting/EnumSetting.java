package dev.telmuad.hollowclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] values;

	public EnumSetting(String id, String name, E defaultValue) {
		super(id, name, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public E[] values() {
		return values.clone();
	}

	public void cycle(int direction) {
		int i = get().ordinal() + (direction >= 0 ? 1 : -1);
		set(values[Math.floorMod(i, values.length)]);
	}

	/** Friendly name for an enum value: its {@link Labeled#label()}, or the name in sentence case. */
	public static String labelOf(Enum<?> value) {
		if (value instanceof Labeled labeled) return labeled.label();
		String raw = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get().name().toLowerCase(Locale.ROOT));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			String name = json.getAsString();
			for (E value : values) {
				if (value.name().equalsIgnoreCase(name)) {
					set(value);
					return;
				}
			}
		}
		reset();
	}

	@Override
	public String display() {
		return labelOf(get());
	}
}
