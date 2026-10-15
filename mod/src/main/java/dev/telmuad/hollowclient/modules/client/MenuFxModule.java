package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.render.Quality;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/**
 * The eye candy in Hollow's menus. Each effect has its own switch and strength slider; the module switch turns
 * them all off at once. Performance Mode and "Reduce UI motion" also calm them down (see {@link Quality}).
 * Blur strength and glow are part of the theme (Theme editor).
 */
public final class MenuFxModule extends Module {
	/** One effect: a switch plus a 0..100 strength slider that is shown only while the switch is on. */
	public final class Effect {
		private final BooleanSetting on;
		private final NumberSetting strength;
		private final boolean heavy;
		private final boolean motion;

		private Effect(String id, String name, int defaultStrength, boolean heavy, boolean motion, String description) {
			this.on = add(new BooleanSetting(id, name, true));
			this.on.describe(description);
			this.strength = add(new NumberSetting(id + "Strength", name + " strength", defaultStrength, 0, 100, 5).format(0, "%"));
			this.strength.visibleWhen(on::get);
			this.heavy = heavy;
			this.motion = motion;
		}

		/** 0 when off (or not allowed right now), otherwise the strength as 0..1. */
		public float amount() {
			if (!isEnabled() || !on.on()) return 0f;
			if (heavy && !Quality.heavyAllowed()) return 0f;
			if (motion && Quality.reducedMotion()) return 0f;
			return strength.getFloat() / 100f;
		}

		public boolean active() {
			return amount() > 0f;
		}
	}

	public final Effect particles = new Effect("particles", "Void particles", 60, true, true,
			"Drifting particles behind menus that move away from your mouse.");
	public final Effect entrance = new Effect("entrance", "Entrance animations", 100, false, true,
			"Cards slide and fade in one after another; panels spring open and closed.");
	public final Effect tilt = new Effect("tilt", "Card tilt", 50, false, true,
			"Cards lean toward your mouse with a moving shine.");
	public final Effect hoverGlow = new Effect("hoverGlow", "Hover glow", 70, false, false,
			"A soft glow around the card or button under your mouse.");
	public final Effect ripples = new Effect("ripples", "Click ripples", 60, false, true,
			"A ripple spreads out from where you click.");
	public final Effect borders = new Effect("borders", "Animated borders", 80, false, false,
			"Panel and card borders in the theme gradient, slowly flowing.");

	public MenuFxModule() {
		super("menu_fx", "Menu Effects", "Particles, spring animations, card tilt, glow, ripples and flowing borders in Hollow's menus.", Category.CLIENT, true);
	}
}
