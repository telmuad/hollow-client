package dev.telmuad.hollowclient.module;

import dev.telmuad.hollowclient.hud.HudPosition;
import dev.telmuad.hollowclient.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base class for every feature. A module has an id (used in the config file), a display name, a category,
 * an enabled flag, an optional keybind and a list of settings.
 * <p>
 * The base class is plain Java on purpose: config loading and saving can be tested without a game.
 * Subclasses that draw or hook into the game live in {@code modules.*}.
 */
public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final boolean enabledByDefault;
	private final List<Setting<?>> settings = new ArrayList<>();
	private KeyDef keyDef = KeyDef.toggle();
	private boolean enabled;
	private Consumer<Module> changeListener = m -> {
	};

	protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
		this.id = Objects.requireNonNull(id);
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.category = Objects.requireNonNull(category);
		this.enabledByDefault = enabledByDefault;
		this.enabled = enabledByDefault;
	}

	public final String id() {
		return id;
	}

	public final String name() {
		return name;
	}

	public final String description() {
		return description;
	}

	public final Category category() {
		return category;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final boolean enabledByDefault() {
		return enabledByDefault;
	}

	/** A sub-tab inside the category (SkyBlock areas: "General", "Garden", ...); empty for none. */
	public String group() {
		return "";
	}

	/** False for modules that are always on (Theme, Performance): their card has no switch. */
	public boolean toggleable() {
		return true;
	}

	public final void setEnabled(boolean value) {
		if (!toggleable()) value = true;
		if (enabled == value) return;
		enabled = value;
		if (value) onEnable();
		else onDisable();
		changeListener.accept(this);
	}

	public final void toggle() {
		setEnabled(!enabled);
	}

	/**
	 * Set the enabled flag while loading the config at startup: no callbacks and no "changed" event,
	 * because the game isn't fully up yet. {@link #onLoaded()} runs afterwards.
	 */
	public final void restoreEnabled(boolean value) {
		enabled = value || !toggleable();
	}

	public final List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public final Setting<?> setting(String settingId) {
		for (Setting<?> s : settings) if (s.id().equals(settingId)) return s;
		return null;
	}

	public final KeyDef keyDef() {
		return keyDef;
	}

	protected final void keybind(KeyDef def) {
		this.keyDef = Objects.requireNonNull(def);
	}

	/** Register a setting. Changing it marks the config dirty and calls {@link #onSettingChanged}. */
	protected final <S extends Setting<?>> S add(S setting) {
		for (Setting<?> s : settings) {
			if (s.id().equals(setting.id())) throw new IllegalArgumentException("Duplicate setting " + setting.id() + " in " + id);
		}
		settings.add(setting);
		setting.setListener(s -> {
			onSettingChanged(s);
			changeListener.accept(this);
		});
		return setting;
	}

	/** Register an effect: a switch plus a strength slider (0-100 %) that shows while the switch is on. */
	protected final dev.telmuad.hollowclient.setting.Effect effect(String id, String name, boolean on, int strength, String description) {
		var toggle = add(new dev.telmuad.hollowclient.setting.BooleanSetting(id, name, on));
		toggle.describe(description);
		var slider = add(new dev.telmuad.hollowclient.setting.NumberSetting(id + "Strength", name + " strength", strength, 0, 100, 5).format(0, "%"));
		slider.visibleWhen(toggle::get);
		return new dev.telmuad.hollowclient.setting.Effect(toggle, slider);
	}

	public final void setChangeListener(Consumer<Module> listener) {
		this.changeListener = listener == null ? m -> {
		} : listener;
	}

	/** Mark this module's state as changed (for state that isn't a {@link Setting}, such as a HUD position). */
	protected final void markChanged() {
		changeListener.accept(this);
	}

	/** The HUD position, for modules drawn on the HUD. Null for everything else. */
	public HudPosition hudPosition() {
		return null;
	}

	/** The default HUD position (used by "reset"). Null for non-HUD modules. */
	public HudPosition defaultHudPosition() {
		return null;
	}

	/** Called after the config is loaded, with the loaded enabled state. */
	public void onLoaded() {
	}

	/** Called at the end of every client tick, whether the module is enabled or not. */
	public void onTick() {
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	protected void onSettingChanged(Setting<?> setting) {
	}

	/** Reset every setting and the enabled flag to defaults. */
	public void resetToDefaults() {
		for (Setting<?> s : settings) s.reset();
		HudPosition pos = hudPosition();
		HudPosition def = defaultHudPosition();
		if (pos != null && def != null) pos.copyFrom(def);
		setEnabled(enabledByDefault);
		markChanged();
	}
}
