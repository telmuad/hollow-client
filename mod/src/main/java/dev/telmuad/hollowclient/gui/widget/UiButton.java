package dev.telmuad.hollowclient.gui.widget;

import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.HollowScreen;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.UiSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A flat rounded button. */
public final class UiButton {
	public enum Style {NORMAL, PRIMARY, DANGER}

	private String label;
	private final Runnable onPress;
	private Rect bounds = Rect.EMPTY;
	private boolean enabled = true;
	private Style style = Style.NORMAL;
	private float hoverT;
	private boolean wasHover;

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
		return Ui.width(font, label) + 16;
	}

	public void render(GuiGraphicsExtractor g, Font font, int mx, int my, float dt, float alpha) {
		if (bounds.w() <= 0) return;
		boolean hover = enabled && bounds.contains(mx, my);
		if (hover && !wasHover) UiSounds.hover();
		wasHover = hover;
		hoverT = Anim.approach(hoverT, hover ? 1f : 0f, dt, 18f);
		int bg;
		int fg;
		int outline;
		if (!enabled) {
			bg = Theme.CARD;
			fg = Theme.TEXT_FAINT;
			outline = Theme.STROKE;
		} else if (style == Style.PRIMARY) {
			bg = Theme.mix(Theme.accentSoft(), Theme.accent(), hoverT);
			fg = 0xFFFFFFFF;
			outline = 0;
		} else if (style == Style.DANGER) {
			bg = Theme.mix(Theme.CARD, 0xFF4A2024, hoverT);
			fg = Theme.mix(Theme.DANGER, 0xFFFFFFFF, hoverT * 0.5f);
			outline = Theme.mix(Theme.STROKE, Theme.DANGER, 0.5f + hoverT * 0.5f);
		} else {
			bg = Theme.mix(Theme.CARD, Theme.CARD_HOVER, hoverT);
			fg = Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hoverT);
			outline = Theme.mix(Theme.STROKE, Theme.accent(), hoverT * 0.6f);
		}
		if (enabled) HollowScreen.hoverGlow(g, bounds, hoverT * (style == Style.DANGER ? 0.5f : 1f), alpha);
		int r = Theme.radius(3);
		Draw.roundRect(g, bounds, r, Theme.fade(bg, alpha));
		if (style == Style.PRIMARY && enabled) {
			// a soft top-to-bottom sheen on the main buttons
			Draw.roundGradient(g, bounds.x(), bounds.y(), bounds.w(), bounds.h(), r, Theme.fade(0x2EFFFFFF, alpha), 0x00FFFFFF);
		}
		if (outline != 0) Draw.roundOutline(g, bounds, r, Theme.fade(outline, alpha));
		int tw = Ui.width(font, label);
		Ui.draw(g, font, label, bounds.x() + (bounds.w() - tw) / 2, bounds.y() + (bounds.h() - 8) / 2 + 1, Theme.fade(fg, alpha));
	}

	public boolean click(double mx, double my) {
		if (!enabled || !bounds.contains(mx, my)) return false;
		HollowScreen.ripple(mx, my, bounds);
		playClick();
		onPress.run();
		return true;
	}

	public static void playClick() {
		UiSounds.click();
	}
}
