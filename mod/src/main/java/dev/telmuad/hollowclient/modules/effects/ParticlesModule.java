package dev.telmuad.hollowclient.modules.effects;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;

/**
 * Your own particles: block-break pieces and sparkles, a trail behind you, and footprints / dust where you walk. They
 * are spawned by your client only; other players don't see them.
 */
public final class ParticlesModule extends Module {
	public enum TrailStyle {THEME, SPARKLES, FLAMES, HEARTS, NOTES, CLOUDS}

	private final Effect breakPieces = effect("breakPieces", "More block-break pieces", true, 30, "Up to three times the pieces when a block breaks.");
	private final Effect breakSparkles = effect("breakSparkles", "Block-break sparkles", true, 40, "Theme-colored sparkles when a block breaks.");
	private final Effect trail = effect("trail", "Trail", false, 50, "Particles follow you as you move (see it in third person). Strength = how many.");
	private final EnumSetting<TrailStyle> trailStyle = add(new EnumSetting<>("trailStyle", "Trail style", TrailStyle.THEME));
	private final BooleanSetting trailFirstPerson = add(new BooleanSetting("trailFirstPerson", "Trail in first person too", false));
	private final Effect footprints = effect("footprints", "Footprints", false, 50, "Fading footprints where you walk.");
	private final Effect dust = effect("dust", "Footstep dust", true, 30, "Little puffs of dust when you sprint or land.");

	public ParticlesModule() {
		super("particles", "Particles", "Block-break pieces and sparkles, your own trail, footprints and footstep dust.", Category.EFFECTS, true);
		trailStyle.visibleWhen(trail.switchSetting()::get);
		trailFirstPerson.visibleWhen(trail.switchSetting()::get);
	}

	/** Extra copies of vanilla's break particles: 0 up to 2. */
	public int extraBreakRounds() {
		return Math.round(2 * breakPieces.amount());
	}

	public float breakSparkles() {
		return breakSparkles.amount();
	}

	public float trail() {
		return trail.amount();
	}

	public TrailStyle trailStyle() {
		return trailStyle.get();
	}

	public boolean trailFirstPerson() {
		return trailFirstPerson.on();
	}

	public float footprints() {
		return footprints.amount();
	}

	public float dust() {
		return dust.amount();
	}
}
