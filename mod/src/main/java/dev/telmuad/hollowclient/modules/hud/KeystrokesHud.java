package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.input.Clicks;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Fx;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.hud.HudStyle;
import net.minecraft.util.Util;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** W A S D, both mouse buttons (with CPS) and the space bar; held keys light up. */
public final class KeystrokesHud extends HudModule {
	private static final int KEY = 18;
	private static final int GAP = 2;
	private static final int WIDTH = KEY * 3 + GAP * 2;

	private final BooleanSetting showMouse = add(new BooleanSetting("showMouse", "Show mouse buttons", true));
	private final BooleanSetting showCps = add(new BooleanSetting("showCps", "Show CPS under mouse buttons", true));
	private final BooleanSetting showSpace = add(new BooleanSetting("showSpace", "Show space bar", true));
	private final ColorSetting pressedColor = add(new ColorSetting("pressedColor", "Pressed key color", 0xD0F0F0F5));
	private final ColorSetting pressedText = add(new ColorSetting("pressedTextColor", "Pressed text color", 0xFF101015));
	private final Effect keyGlow = effect("keyGlow", "Key glow", true, 70, "Pressed keys glow in the theme color.");
	private final Effect ripple = effect("ripple", "Press ripple", true, 60, "A ripple spreads across a key when you press it.");
	private final float[] glow = new float[7];
	private final boolean[] wasDown = new boolean[7];
	private final long[] pressedAt = new long[7];
	private long lastNanos;

	public KeystrokesHud() {
		super("keystrokes", "Keystrokes", "Shows W A S D, mouse buttons and space as you press them.", false,
				at(Anchor.START, 2, Anchor.CENTER, 30));
		showCps.visibleWhen(showMouse::get);
	}

	@Override
	public Size measure(Minecraft mc, Font font, boolean preview) {
		int h = KEY * 2 + GAP;
		if (showMouse.on()) h += GAP + KEY;
		if (showSpace.on()) h += GAP + 10;
		return new Size(WIDTH, h);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, Font font, boolean preview) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		Options o = mc.options;
		boolean demo = preview && mc.player == null;
		int x1 = KEY + GAP;
		int x2 = 2 * (KEY + GAP);
		key(g, font, 0, o.keyUp, x1, 0, KEY, KEY, dt);
		key(g, font, 1, o.keyLeft, 0, KEY + GAP, KEY, KEY, dt);
		key(g, font, 2, o.keyDown, x1, KEY + GAP, KEY, KEY, dt);
		key(g, font, 3, o.keyRight, x2, KEY + GAP, KEY, KEY, dt);
		int y = 2 * (KEY + GAP);
		if (showMouse.on()) {
			int mw = (WIDTH - GAP) / 2;
			String l = showCps.on() ? (demo ? 9 : Clicks.leftCps()) + " CPS" : null;
			String r = showCps.on() ? (demo ? 4 : Clicks.rightCps()) + " CPS" : null;
			mouseKey(g, font, 4, o.keyAttack, "LMB", l, 0, y, mw, dt);
			mouseKey(g, font, 5, o.keyUse, "RMB", r, mw + GAP, y, WIDTH - mw - GAP, dt);
			y += KEY + GAP;
		}
		if (showSpace.on()) {
			boolean down = o.keyJump.isDown();
			press(6, down);
			glow[6] = Anim.approach(glow[6], down ? 1f : 0f, dt, 25f);
			box(g, 6, 0, y, WIDTH, 10, glow[6]);
			int c = ColorUtil.mix(textColor.argb(), pressedText.argb(), glow[6]);
			int bar = WIDTH / 3;
			g.fill(bar, y + 4, WIDTH - bar, y + 5, ColorUtil.fade(c, alpha));
		}
	}

	/** Remember when a key went down, for its ripple. */
	private void press(int i, boolean down) {
		if (down && !wasDown[i]) pressedAt[i] = Util.getMillis();
		wasDown[i] = down;
	}

	private void key(GuiGraphicsExtractor g, Font font, int i, KeyMapping mapping, int x, int y, int w, int h, float dt) {
		press(i, mapping.isDown());
		glow[i] = Anim.approach(glow[i], mapping.isDown() ? 1f : 0f, dt, 25f);
		box(g, i, x, y, w, h, glow[i]);
		String label = label(mapping);
		int c = ColorUtil.mix(textColor.argb(), pressedText.argb(), glow[i]);
		text(g, font, label, x + (w - width(font, label)) / 2 + 1, y + (h - 8) / 2 + 1, c);
	}

	private void mouseKey(GuiGraphicsExtractor g, Font font, int i, KeyMapping mapping, String name, String cps, int x, int y, int w, float dt) {
		press(i, mapping.isDown());
		glow[i] = Anim.approach(glow[i], mapping.isDown() ? 1f : 0f, dt, 25f);
		box(g, i, x, y, w, KEY, glow[i]);
		int c = ColorUtil.mix(textColor.argb(), pressedText.argb(), glow[i]);
		if (cps == null) {
			text(g, font, name, x + (w - width(font, name)) / 2 + 1, y + 6, c);
		} else {
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y + 2);
			g.pose().scale(0.75f, 0.75f);
			text(g, font, name, -width(font, name) / 2, 0, c);
			text(g, font, cps, -width(font, cps) / 2, 11, c);
			g.pose().popMatrix();
		}
	}

	/** One key: glow, background in the HUD style, pressed color, and the press ripple. */
	private void box(GuiGraphicsExtractor g, int i, int x, int y, int w, int h, float t) {
		float glowAmount = keyGlow.amount();
		if (glowAmount > 0f && t > 0.01f) {
			Fx.glow(g, x, y, w, h, 5, ColorUtil.fade(Theme.accent(), alpha * t * glowAmount * 0.8f));
		}
		int base = background.on() ? backgroundColor.argb() : 0;
		int color = ColorUtil.mix(base, pressedColor.argb(), t);
		boolean styled = HudStyle.module() != null;
		int rad = styled ? Math.min(Theme.radius(3), h / 2) : 0;
		if ((color >>> 24) != 0) Draw.roundRect(g, x, y, w, h, rad, ColorUtil.fade(color, alpha));
		if (styled && background.on() && t < 0.99f) {
			Draw.roundOutline(g, x, y, w, h, rad, ColorUtil.fade(ColorUtil.mix(Theme.STROKE, Theme.accent(), t), alpha * (1f - t * 0.5f)));
		}
		float rippleAmount = ripple.amount();
		long age = Util.getMillis() - pressedAt[i];
		if (rippleAmount > 0f && pressedAt[i] != 0 && age < 420) {
			float p = age / 420f;
			float radius = 2f + Math.max(w, h) * Anim.easeOutCubic(p);
			g.enableScissor(x, y, x + w, y + h);
			Fx.disc(g, x + w / 2f, y + h / 2f, radius, ColorUtil.fade(ColorUtil.mix(Theme.accent(), 0xFFFFFFFF, 0.5f), alpha * rippleAmount * 0.5f * (1f - p)));
			g.disableScissor();
		}
	}

	private static String label(KeyMapping mapping) {
		String s = mapping.getTranslatedKeyMessage().getString();
		if (s.length() > 3) s = s.substring(0, 3);
		return s.toUpperCase(java.util.Locale.ROOT);
	}
}
