package dev.telmuad.hollowclient.hud;

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Fx;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.hud.HudStyleModule;
import dev.telmuad.hollowclient.render.Quality;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Drawing for HUD elements in the HUD Style module's look: panel backgrounds (glass, flat, outline, none), text
 * (optionally in the theme gradient), glow and gradient borders, and rolling digits. All drawing happens in the
 * element's own unscaled coordinates; {@link HudRenderer} places and scales it.
 */
public final class HudStyle {
	private static final float ROLL_TICKS_MS = 260f;

	private HudStyle() {
	}

	public static HudStyleModule module() {
		HudStyleModule m = Modules.HUD_STYLE;
		return m != null && m.isEnabled() ? m : null;
	}

	/** Background of an element in the current look. {@code alpha} is the element's fade (0..1). */
	public static void background(GuiGraphics g, int w, int h, int color, boolean enabled, float alpha) {
		HudStyleModule m = module();
		if (m == null) {
			if (enabled) g.fill(0, 0, w, h, ColorUtil.fade(color, alpha));
			return;
		}
		int rad = Math.min(Theme.radius(4), Math.min(w, h) / 2);
		if (m.glow() > 0f && m.look() != HudStyleModule.Look.TEXT_ONLY) {
			Fx.glow(g, 0, 0, w, h, 6, ColorUtil.fade(Theme.accent(), alpha * m.glow() * 0.5f * (0.4f + 0.6f * Theme.glow())));
		}
		switch (m.look()) {
			case GLASS -> {
				if (enabled) {
					// the element's background color, tinted a little toward the theme
					int tinted = ColorUtil.mix(color, ColorUtil.withAlpha(Theme.accent(), ColorUtil.alpha(color)), 0.12f);
					Draw.roundRect(g, 0, 0, w, h, rad, ColorUtil.fade(tinted, alpha));
					Draw.roundGradient(g, 0, 0, w, Math.max(2, h / 2), rad, ColorUtil.fade(0x18FFFFFF, alpha), 0x00FFFFFF);
				}
				border(g, w, h, rad, m, alpha);
			}
			case FLAT -> {
				if (enabled) Draw.roundRect(g, 0, 0, w, h, rad, ColorUtil.fade(color, alpha));
				if (m.border() > 0f) border(g, w, h, rad, m, alpha);
			}
			case OUTLINE -> border(g, w, h, rad, m, Math.max(alpha * 0.6f, alpha * m.border()));
			case TEXT_ONLY -> {
			}
		}
	}

	private static void border(GuiGraphics g, int w, int h, int rad, HudStyleModule m, float alpha) {
		if (m.border() > 0f) {
			Fx.gradientOutline(g, new Rect(0, 0, w, h), rad, alpha * m.border() * 0.8f, 0f);
		} else {
			Draw.roundOutline(g, 0, 0, w, h, rad, ColorUtil.fade(Theme.STROKE, alpha));
		}
	}

	// ------------------------------------------------------------------------------------------------ text

	public static int width(Font font, String s) {
		return Ui.hudWidth(font, s);
	}

	/** One piece of HUD text in the HUD font, gradient or plain. */
	public static void text(GuiGraphics g, Font font, String s, int x, int y, int color, boolean shadow, float alpha) {
		HudStyleModule m = module();
		if (m != null && m.gradientText() > 0f) {
			float k = m.gradientText();
			int cx = x;
			int n = s.length();
			float phase = (float) ((Util.getMillis() * Theme.speed() / 4000.0) % 1.0);
			for (int i = 0; i < n; ) {
				int cp = s.codePointAt(i);
				String ch = new String(Character.toChars(cp));
				float t = n <= 1 ? 0f : i / (float) (n - 1);
				int grad = Theme.gradient(Math.abs(((t * 0.8f + phase) % 1f) * 2 - 1));
				Ui.drawHud(g, font, ch, cx, y, ColorUtil.fade(ColorUtil.mix(color, grad, k), alpha), shadow);
				cx += Ui.hudWidth(font, ch);
				i += Character.charCount(cp);
			}
			return;
		}
		Ui.drawHud(g, font, s, x, y, ColorUtil.fade(color, alpha), shadow);
	}

	// ------------------------------------------------------------------------------------------------ rolling numbers

	/** Per text slot: the last text and, per character, the old digit and when it started rolling. */
	public static final class Roller {
		private String last = "";
		private char[] from = new char[0];
		private long[] start = new long[0];

		/**
		 * Draw {@code s}; digits that changed since last frame roll up. Falls back to plain text when the text's
		 * shape changed (different length or different non-digit characters) or rolling is off.
		 */
		public void draw(GuiGraphics g, Font font, String s, int x, int y, int color, boolean shadow, float alpha) {
			HudStyleModule m = module();
			float speed = m == null ? 0f : m.rolling();
			if (speed <= 0f || Quality.reducedMotion()) {
				last = s;
				text(g, font, s, x, y, color, shadow, alpha);
				return;
			}
			long now = Util.getMillis();
			if (s.length() != last.length() || !sameShape(s, last)) {
				from = new char[s.length()];
				start = new long[s.length()];
			} else {
				for (int i = 0; i < s.length(); i++) {
					if (s.charAt(i) != last.charAt(i) && Character.isDigit(s.charAt(i))) {
						from[i] = last.charAt(i);
						start[i] = now;
					}
				}
			}
			last = s;
			float duration = ROLL_TICKS_MS / (0.4f + speed * 0.9f) / Theme.speed();
			boolean any = false;
			for (int i = 0; i < s.length(); i++) if (start[i] != 0 && now - start[i] < duration) any = true;
			if (!any) {
				text(g, font, s, x, y, color, shadow, alpha);
				return;
			}
			// clip to the line so rolling digits don't spill above or below it
			g.enableScissor(x - 1, y - 1, x + width(font, s) + 2, y + 9);
			int cx = x;
			for (int i = 0; i < s.length(); i++) {
				String ch = String.valueOf(s.charAt(i));
				float t = start[i] == 0 ? 1f : Anim.clamp01((now - start[i]) / duration);
				if (t >= 1f) {
					text(g, font, ch, cx, y, color, shadow, alpha);
				} else {
					float e = Anim.easeOutCubic(t);
					g.pose().pushMatrix();
					g.pose().translate(0f, -e * 9f);
					text(g, font, String.valueOf(from[i]), cx, y, color, shadow, alpha * (1f - e));
					g.pose().translate(0f, 9f);
					text(g, font, ch, cx, y, color, shadow, alpha * e);
					g.pose().popMatrix();
				}
				cx += width(font, ch);
			}
			g.disableScissor();
		}

		private static boolean sameShape(String a, String b) {
			for (int i = 0; i < a.length(); i++) {
				char x = a.charAt(i);
				char y = b.charAt(i);
				if (x != y && !(Character.isDigit(x) && Character.isDigit(y))) return false;
			}
			return true;
		}
	}

	private static final Map<Object, Roller> ROLLERS = new HashMap<>();

	/** The roller for one text slot (for example module + line number). */
	public static Roller roller(Object owner, int slot) {
		return ROLLERS.computeIfAbsent(java.util.List.of(owner, slot), k -> new Roller());
	}
}
