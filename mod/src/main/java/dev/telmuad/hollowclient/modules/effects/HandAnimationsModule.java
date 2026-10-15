package dev.telmuad.hollowclient.modules.effects;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.NumberSetting;

/**
 * How your hand and held item move in first person: smoother item switching, swing speed, and 1.7-style options.
 * All of it is drawing only: the attack cooldown, reach and what is sent to the server don't change.
 */
public final class HandAnimationsModule extends Module {
	private final Effect smoothSwitch = effect("smoothSwitch", "Smoother switching", true, 70, "Eased motion when the held item goes down and comes back up.");
	private final BooleanSetting customSwing = add(new BooleanSetting("customSwing", "Change swing speed", false));
	private final NumberSetting swingSpeed = add(new NumberSetting("swingSpeed", "Swing speed", 1, 0.5, 2, 0.05).format(2, "x"));
	private final BooleanSetting noCooldownDip = add(new BooleanSetting("noCooldownDip", "1.7: no item dip after hitting", false));
	private final Effect classicPosition = effect("classicPosition", "1.7: classic held-item position", false, 100, "Hold items a bit lower and bigger, like old versions.");
	private final BooleanSetting instantSneak = add(new BooleanSetting("instantSneak", "1.7: instant sneak camera", false));

	public HandAnimationsModule() {
		super("hand_animations", "Hand Animations", "Smoother item switching, swing speed and 1.7-style hand animations (looks only).", Category.EFFECTS, true);
		swingSpeed.visibleWhen(customSwing::get);
		noCooldownDip.describe("The held item no longer sinks while the attack cooldown recharges. The cooldown itself is unchanged.");
		instantSneak.describe("The camera drops at once when you sneak, like 1.7, instead of gliding.");
	}

	public float smoothSwitch() {
		return smoothSwitch.amount();
	}

	/** Swing speed multiplier (1 = vanilla). */
	public float swingSpeed() {
		return customSwing.on() ? swingSpeed.getFloat() : 1f;
	}

	public boolean noCooldownDip() {
		return noCooldownDip.on();
	}

	public float classicPosition() {
		return classicPosition.amount();
	}

	public boolean instantSneak() {
		return instantSneak.on();
	}
}
