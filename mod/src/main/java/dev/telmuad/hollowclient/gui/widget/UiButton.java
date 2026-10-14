package dev.telmuad.hollowclient.gui.widget;

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/** A flat rounded button. */
public final class UiButton {
	public enum Style {NORMAL, PRIMARY, DANGER}

	private String label;
	private final Runnable onPress;
	private Rect bounds = Rect.EMPTY;
	private boolean enabled = true;
	private Style style = Style.NORMAL;
	private float hoverT;

	public UiButton(String label, Runnable onPress) {
		this.label = label;
		this.onPress = onPress;
	}

	public UiButton style(Style style) {
		this.style = style;
		return this;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public UiButton bounds(Rect bounds) {
		this.bounds = bounds;
		return this;
	}

	public Rect bounds() {
		return bounds;
	}

	public int preferredWidth(Font font) {
		return font.width(label) + 16;
	}

	public void render(GuiGraphics g, Font font, int mx, int my, float dt, float alpha) {
		if (bounds.w() <= 0) return;
		boolean hover = enabled && bounds.contains(mx, my);
		hoverT = Anim.approach(hoverT, hover ? 1f : 0f, dt, 18f);
		int bg;
		int fg;
		int outline;
		if (!enabled) {
			bg = Theme.CARD;
			fg = Theme.TEXT_FAINT;
			outline = Theme.STROKE;
		} else if (style == Style.PRIMARY) {
			bg = Theme.mix(Theme.ACCENT_SOFT, Theme.ACCENT, hoverT);
			fg = 0xFFFFFFFF;
			outline = 0;
		} else if (style == Style.DANGER) {
			bg = Theme.mix(Theme.CARD, 0xFF4A2024, hoverT);
			fg = Theme.mix(Theme.DANGER, 0xFFFFFFFF, hoverT * 0.5f);
			outline = Theme.mix(Theme.STROKE, Theme.DANGER, 0.5f + hoverT * 0.5f);
		} else {
			bg = Theme.mix(Theme.CARD, Theme.CARD_HOVER, hoverT);
			fg = Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hoverT);
			outline = Theme.mix(Theme.STROKE, Theme.ACCENT, hoverT * 0.6f);
		}
		Draw.roundRect(g, bounds, 3, Theme.fade(bg, alpha));
		if (outline != 0) Draw.roundOutline(g, bounds, 3, Theme.fade(outline, alpha));
		int tw = font.width(label);
		g.drawString(font, label, bounds.x() + (bounds.w() - tw) / 2, bounds.y() + (bounds.h() - 8) / 2 + 1, Theme.fade(fg, alpha), false);
	}

	public boolean click(double mx, double my) {
		if (!enabled || !bounds.contains(mx, my)) return false;
		playClick();
		onPress.run();
		return true;
	}

	public static void playClick() {
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
	}
}
