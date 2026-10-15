package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/**
 * Screen effects on the world (not on menus or the HUD): bloom, motion blur, chromatic aberration, vignette, film
 * grain and color grading presets, plus a small camera shake when you take damage. Turned off automatically in
 * Performance Mode and while an Iris shader pack is active.
 */
public final class PostProcessingModule extends Module {
	/** Color grading looks: saturation, contrast, exposure, highlight tint and shadow lift. */
	public enum Grade {
		NONE(1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
		CINEMATIC(0.88f, 1.14f, 0.97f, 1.05f, 1.0f, 0.93f, -0.01f, 0.012f, 0.03f),
		VIBRANT(1.38f, 1.08f, 1.03f, 1f, 1f, 1f, 0f, 0f, 0f),
		NOIR(0f, 1.32f, 0.95f, 0.98f, 1f, 1.04f, 0f, 0f, 0.01f),
		DREAMY(1.12f, 0.9f, 1.06f, 1.04f, 0.98f, 1.07f, 0.035f, 0.02f, 0.05f);

		public final float saturation;
		public final float contrast;
		public final float exposure;
		public final float tintR;
		public final float tintG;
		public final float tintB;
		public final float liftR;
		public final float liftG;
		public final float liftB;

		Grade(float saturation, float contrast, float exposure, float tintR, float tintG, float tintB, float liftR, float liftG, float liftB) {
			this.saturation = saturation;
			this.contrast = contrast;
			this.exposure = exposure;
			this.tintR = tintR;
			this.tintG = tintG;
			this.tintB = tintB;
			this.liftR = liftR;
			this.liftG = liftG;
			this.liftB = liftB;
		}
	}

	private final EnumSetting<Grade> grade = add(new EnumSetting<>("grade", "Color grading", Grade.NONE));
	private final NumberSetting gradeStrength = add(new NumberSetting("gradeStrength", "Color grading strength", 80, 0, 100, 5).format(0, "%"));
	private final Effect bloom = effect("bloom", "Bloom", true, 45, "Bright things (torches, sky, glowing blocks) softly glow.");
	private final NumberSetting bloomThreshold = add(new NumberSetting("bloomThreshold", "Bloom threshold", 55, 10, 95, 5).format(0, "%"));
	private final Effect motionBlur = effect("motionBlur", "Motion blur", false, 40, "Blends each frame with the last one for smooth motion trails.");
	private final Effect chromatic = effect("chromatic", "Chromatic aberration", false, 30, "Red and blue split a little toward the screen edges.");
	private final Effect vignette = effect("vignette", "Vignette", true, 35, "Darkens the screen corners.");
	private final Effect grain = effect("grain", "Film grain", false, 25, "A fine moving noise like film.");
	private final Effect shake = effect("shake", "Damage screen shake", true, 50, "A short camera shake when you get hurt (on top of vanilla's tilt).");

	public PostProcessingModule() {
		super("post_processing", "Post Processing", "Bloom, motion blur, chromatic aberration, vignette, film grain, color grading and damage shake.", Category.VISUAL, true);
		gradeStrength.visibleWhen(() -> grade.get() != Grade.NONE);
		bloomThreshold.visibleWhen(bloom.switchSetting()::get);
	}

	public Grade grade() {
		return grade.get();
	}

	public float gradeStrength() {
		return grade.get() == Grade.NONE ? 0f : gradeStrength.getFloat() / 100f;
	}

	public float bloom() {
		return bloom.amount();
	}

	public float bloomThreshold() {
		return bloomThreshold.getFloat() / 100f;
	}

	public float motionBlur() {
		return motionBlur.amount();
	}

	public float chromatic() {
		return chromatic.amount();
	}

	public float vignette() {
		return vignette.amount();
	}

	public float grain() {
		return grain.amount();
	}

	public float shake() {
		return shake.amount();
	}

	/** Whether any screen pass has work to do (the shake is a camera effect, not a pass). */
	public boolean anyScreenEffect() {
		return bloom() > 0 || motionBlur() > 0 || chromatic() > 0 || vignette() > 0 || grain() > 0 || gradeStrength() > 0;
	}
}
