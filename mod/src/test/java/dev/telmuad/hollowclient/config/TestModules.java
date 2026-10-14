package dev.telmuad.hollowclient.config;

import dev.telmuad.hollowclient.hud.HudPosition;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Small game-free modules for config tests. */
final class TestModules {
	enum Shape {CROSS, DOT, CIRCLE}

	static final class Hud extends Module {
		final ColorSetting color = add(new ColorSetting("textColor", "Text color", 0xFFFFFFFF));
		final BooleanSetting background = add(new BooleanSetting("background", "Background", true));
		final NumberSetting scale = add(new NumberSetting("opacity", "Opacity", 0.5, 0, 1, 0.05));
		final HudPosition def = new HudPosition(Anchor.START, 0.01, Anchor.START, 0.02, 1f);
		final HudPosition pos = def.copy();
		int enables;
		int disables;

		Hud() {
			super("fps", "FPS", "Shows FPS", Category.HUD, true);
		}

		@Override
		public HudPosition hudPosition() {
			return pos;
		}

		@Override
		public HudPosition defaultHudPosition() {
			return def;
		}

		@Override
		protected void onEnable() {
			enables++;
		}

		@Override
		protected void onDisable() {
			disables++;
		}
	}

	static final class Plain extends Module {
		final NumberSetting zoom = add(new NumberSetting("zoom", "Zoom", 4, 2, 50, 0.5));
		final EnumSetting<Shape> shape = add(new EnumSetting<>("shape", "Shape", Shape.CROSS));

		Plain() {
			super("zoom", "Zoom", "Zooms", Category.QOL, false);
		}
	}

	static ModuleRegistry registry(Hud hud, Plain plain) {
		ModuleRegistry r = new ModuleRegistry();
		r.register(hud);
		r.register(plain);
		return r;
	}

	private TestModules() {
	}
}
