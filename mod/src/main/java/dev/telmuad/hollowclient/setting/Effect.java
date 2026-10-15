package dev.telmuad.hollowclient.setting;

/**
 * One visual effect's pair of settings: an on/off switch and a 0-100 % strength slider (the slider only shows while
 * the switch is on). Create with {@code Module.effect(...)}.
 */
public final class Effect {
	private final BooleanSetting on;
	private final NumberSetting strength;

	public Effect(BooleanSetting on, NumberSetting strength) {
		this.on = on;
		this.strength = strength;
	}

	public boolean on() {
		return on.on();
	}

	/** Strength as 0..1 (ignores the switch). */
	public float strength() {
		return strength.getFloat() / 100f;
	}

	/** 0 when switched off, otherwise the strength as 0..1. */
	public float amount() {
		return on.on() ? strength() : 0f;
	}

	public BooleanSetting switchSetting() {
		return on;
	}

	public NumberSetting strengthSetting() {
		return strength;
	}
}
