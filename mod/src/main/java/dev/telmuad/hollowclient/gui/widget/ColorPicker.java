package dev.telmuad.hollowclient.gui.widget;

import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * A popup color picker for a {@link ColorSetting}: saturation/value square, hue bar, optional alpha bar,
 * a hex field and a row of swatches. Every change is written straight to the setting.
 */
public final class ColorPicker {
	private static final int[] SWATCHES = {
			0xFFFFFFFF, 0xFFB0B3BD, 0xFF16171C, 0xFFF2706B, 0xFFFFA94D, 0xFFFFE066,
			0xFF5BD18B, 0xFF4DD4E8, 0xFF5B8CFF, 0xFF8B6CFF, 0xFFE879F9, 0xFFFF5CAA};
	private static final int SV_W = 96;
	private static final int SV_H = 72;

	private enum Drag {NONE, SV, HUE, ALPHA}

	private final ColorSetting setting;
	private final int original;
	private float hue;
	private float sat;
	private float val;
	private int alpha;
	private Rect panel = Rect.EMPTY;
	private Rect sv = Rect.EMPTY;
	private Rect hueBar = Rect.EMPTY;
	private Rect alphaBar = Rect.EMPTY;
	private Rect hexBox = Rect.EMPTY;
	private Rect swatchRow = Rect.EMPTY;
	private Drag drag = Drag.NONE;
	private boolean hexFocused;
	private String hexText;

	public ColorPicker(ColorSetting setting) {
		this.setting = setting;
		this.original = setting.get();
		readFromSetting();
	}

	private void readFromSetting() {
		int c = setting.get();
		float[] hsv = ColorUtil.toHsv(c);
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
		alpha = setting.hasAlpha() ? ColorUtil.alpha(c) : 255;
		hexText = hexOf(c);
	}

	private String hexOf(int c) {
		return setting.hasAlpha() ? ColorUtil.toHex(c).substring(1) : String.format(Locale.ROOT, "%06X", c & 0xFFFFFF);
	}

	/** Place the popup next to (anchorX, anchorY), kept on screen. */
	public void layout(int screenW, int screenH, int anchorX, int anchorY) {
		int pad = 6;
		int w = pad + SV_W + 6 + 10 + pad;
		int h = pad + SV_H + (setting.hasAlpha() ? 4 + 8 : 0) + 6 + 14 + 6 + 2 * 11 + pad;
		int x = Math.max(4, Math.min(screenW - w - 4, anchorX - w));
		int y = Math.max(4, Math.min(screenH - h - 4, anchorY));
		panel = new Rect(x, y, w, h);
		sv = new Rect(x + pad, y + pad, SV_W, SV_H);
		hueBar = new Rect(sv.right() + 6, sv.y(), 10, SV_H);
		int cy = sv.bottom() + 4;
		alphaBar = setting.hasAlpha() ? new Rect(sv.x(), cy, SV_W + 16, 8) : Rect.EMPTY;
		if (setting.hasAlpha()) cy = alphaBar.bottom();
		cy += 6;
		hexBox = new Rect(sv.x() + 18, cy, w - 2 * pad - 18, 14);
		swatchRow = new Rect(sv.x(), hexBox.bottom() + 6, w - 2 * pad, 2 * 11);
	}

	public boolean contains(double mx, double my) {
		return panel.contains(mx, my);
	}

	public void render(GuiGraphics g, Font font, int mx, int my) {
		Draw.roundRect(g, panel.x() + 2, panel.y() + 3, panel.w(), panel.h(), 5, 0x66000000);
		Draw.roundRect(g, panel, 5, Theme.PANEL_RAISED);
		Draw.roundOutline(g, panel, 5, Theme.STROKE_STRONG);

		// Saturation (left to right) x value (top to bottom) for the current hue.
		for (int i = 0; i < sv.w(); i++) {
			int top = ColorUtil.fromHsv(hue, i / (float) (sv.w() - 1), 1f);
			g.fillGradient(sv.x() + i, sv.y(), sv.x() + i + 1, sv.bottom(), top, 0xFF000000);
		}
		int px = sv.x() + Math.round(sat * (sv.w() - 1));
		int py = sv.y() + Math.round((1 - val) * (sv.h() - 1));
		g.renderOutline(px - 2, py - 2, 5, 5, val > 0.5f ? 0xFF000000 : 0xFFFFFFFF);

		for (int s = 0; s < 6; s++) {
			int y0 = hueBar.y() + hueBar.h() * s / 6;
			int y1 = hueBar.y() + hueBar.h() * (s + 1) / 6;
			g.fillGradient(hueBar.x(), y0, hueBar.right(), y1, ColorUtil.fromHsv(s / 6f, 1, 1), ColorUtil.fromHsv((s + 1) / 6f, 1, 1));
		}
		int hy = hueBar.y() + Math.round(hue * (hueBar.h() - 1));
		g.fill(hueBar.x() - 1, hy - 1, hueBar.right() + 1, hy + 1, 0xFFFFFFFF);

		int rgb = ColorUtil.fromHsv(hue, sat, val) & 0xFFFFFF;
		if (setting.hasAlpha()) {
			Draw.checker(g, alphaBar.x(), alphaBar.y(), alphaBar.w(), alphaBar.h(), 4);
			for (int i = 0; i < alphaBar.w(); i++) {
				int a = Math.round(255f * i / (alphaBar.w() - 1));
				g.fill(alphaBar.x() + i, alphaBar.y(), alphaBar.x() + i + 1, alphaBar.bottom(), (a << 24) | rgb);
			}
			int ax = alphaBar.x() + Math.round(alpha / 255f * (alphaBar.w() - 1));
			g.fill(ax - 1, alphaBar.y() - 1, ax + 1, alphaBar.bottom() + 1, 0xFFFFFFFF);
		}

		// Old / new preview square next to the hex field.
		Draw.checker(g, sv.x(), hexBox.y(), 14, 14, 3);
		g.fill(sv.x(), hexBox.y(), sv.x() + 7, hexBox.bottom(), original);
		g.fill(sv.x() + 7, hexBox.y(), sv.x() + 14, hexBox.bottom(), setting.get());

		Draw.roundRect(g, hexBox, 3, hexFocused ? Theme.CARD_HOVER : Theme.CARD);
		Draw.roundOutline(g, hexBox, 3, hexFocused ? Theme.accent() : Theme.STROKE);
		String shown = "#" + hexText + (hexFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		Ui.draw(g, font, shown, hexBox.x() + 4, hexBox.y() + 3, Theme.TEXT);

		int sw = 9;
		int perRow = SWATCHES.length / 2;
		int gap = (swatchRow.w() - perRow * sw) / Math.max(1, perRow - 1);
		for (int i = 0; i < SWATCHES.length; i++) {
			int sx = swatchRow.x() + (i % perRow) * (sw + gap);
			int sy = swatchRow.y() + (i / perRow) * 11;
			Draw.roundRect(g, sx, sy, sw, sw, 2, SWATCHES[i]);
			boolean hovered = new Rect(sx, sy, sw, sw).contains(mx, my);
			// Outline every swatch so the dark ones stay visible on the dark panel.
			Draw.roundOutline(g, sx - 1, sy - 1, sw + 2, sw + 2, 2, hovered ? 0xFFFFFFFF : Theme.STROKE_STRONG);
		}
	}

	/** Returns true if the click was inside the popup. */
	public boolean mouseClicked(double mx, double my) {
		if (!panel.contains(mx, my)) return false;
		hexFocused = hexBox.contains(mx, my);
		if (sv.inset(-2).contains(mx, my)) {
			drag = Drag.SV;
		} else if (hueBar.inset(-2).contains(mx, my)) {
			drag = Drag.HUE;
		} else if (alphaBar.inset(-2).contains(mx, my)) {
			drag = Drag.ALPHA;
		} else {
			clickSwatch(mx, my);
			return true;
		}
		mouseDragged(mx, my);
		return true;
	}

	private void clickSwatch(double mx, double my) {
		int sw = 9;
		int perRow = SWATCHES.length / 2;
		int gap = (swatchRow.w() - perRow * sw) / Math.max(1, perRow - 1);
		for (int i = 0; i < SWATCHES.length; i++) {
			int sx = swatchRow.x() + (i % perRow) * (sw + gap);
			int sy = swatchRow.y() + (i / perRow) * 11;
			if (new Rect(sx, sy, sw, sw).contains(mx, my)) {
				UiButton.playClick();
				setting.set(ColorUtil.withAlpha(SWATCHES[i], alpha));
				readFromSetting();
				return;
			}
		}
	}

	public boolean mouseDragged(double mx, double my) {
		switch (drag) {
			case SV -> {
				sat = clamp((float) ((mx - sv.x()) / (sv.w() - 1)));
				val = 1 - clamp((float) ((my - sv.y()) / (sv.h() - 1)));
			}
			case HUE -> hue = Math.min(0.9999f, clamp((float) ((my - hueBar.y()) / (hueBar.h() - 1))));
			case ALPHA -> alpha = Math.round(255 * clamp((float) ((mx - alphaBar.x()) / (alphaBar.w() - 1))));
			case NONE -> {
				return false;
			}
		}
		apply();
		return true;
	}

	public boolean mouseReleased() {
		boolean was = drag != Drag.NONE;
		drag = Drag.NONE;
		return was;
	}

	private void apply() {
		setting.set(ColorUtil.withAlpha(ColorUtil.fromHsv(hue, sat, val), alpha));
		hexText = hexOf(setting.get());
	}

	/** Typing into the hex field. Returns true if the key was used. */
	public boolean keyPressed(int key) {
		if (!hexFocused) return false;
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (!hexText.isEmpty()) hexText = hexText.substring(0, hexText.length() - 1);
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			commitHex();
			hexFocused = false;
			return true;
		}
		return false;
	}

	public boolean charTyped(int codepoint) {
		if (!hexFocused) return false;
		char c = Character.toUpperCase((char) codepoint);
		int max = setting.hasAlpha() ? 8 : 6;
		if (((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) && hexText.length() < max) {
			hexText += c;
			if (hexText.length() == max) commitHex();
		}
		return true;
	}

	private void commitHex() {
		Integer parsed = ColorUtil.parseHex(hexText);
		if (parsed != null) {
			setting.set(parsed);
			readFromSetting();
		} else {
			hexText = hexOf(setting.get());
		}
	}

	public boolean isTyping() {
		return hexFocused;
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
