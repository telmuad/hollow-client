package dev.telmuad.hollowclient.setting;

import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One configurable value of a module. Settings are plain Java (no Minecraft classes),
 * so config handling can be unit tested without a game.
 */
public abstract class Setting<T> {
	private final String id;
	private final String name;
	private final T defaultValue;
	private T value;
	private String description = "";
	private BooleanSupplier visible = () -> true;
	private Consumer<Setting<?>> listener = s -> {
	};

	protected Setting(String id, String name, T defaultValue) {
		this.id = Objects.requireNonNull(id);
		this.name = Objects.requireNonNull(name);
		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.value = defaultValue;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public T get() {
		return value;
	}

	public T defaultValue() {
		return defaultValue;
	}

	/** Sets a new value; it is cleaned up first (clamped, snapped). Listeners only fire on a real change. */
	public void set(T newValue) {
		T clean = sanitize(newValue == null ? defaultValue : newValue);
		if (!clean.equals(value)) {
			value = clean;
			listener.accept(this);
		}
	}

	public void reset() {
		set(defaultValue);
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	public Setting<T> describe(String text) {
		this.description = text == null ? "" : text;
		return this;
	}

	public Setting<T> visibleWhen(BooleanSupplier condition) {
		this.visible = condition == null ? () -> true : condition;
		return this;
	}

	public void setListener(Consumer<Setting<?>> listener) {
		this.listener = listener == null ? s -> {
		} : listener;
	}

	/** Clean up a value before it is stored. The default keeps it as is. */
	protected T sanitize(T raw) {
		return raw;
	}

	public abstract JsonElement toJson();

	/** Read a value from JSON. Anything unusable falls back to the default instead of throwing. */
	public abstract void fromJson(JsonElement json);

	/** Short text for the settings screen. */
	public String display() {
		return String.valueOf(value);
	}
}
