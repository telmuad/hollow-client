package dev.telmuad.hollowclient.gui.widget;

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** A thin horizontal slider over 0..1. Click or drag to change; the value comes from and goes to the caller. */
public final class UiSlider {
	private final DoubleSupplier value;
	private final DoubleConsumer onChange;
	private Rect bounds = Rect.EMPTY;
	private boolean dragging;
	private float hoverT;

	public UiSlider(DoubleSupplier value, DoubleConsumer onChange) {
		this.value = value;
		this.onChange = onChange;
	}

	public UiSlider bounds(Rect bounds) {
		this.bounds = bounds;
		return this;
	}

	public boolean isDragging() {
		return dragging;
	}

	public void render(GuiGraphicsExtractor g, int mx, int my, float dt, float alpha) {
		if (bounds.w() <= 0) return;
		boolean hover = dragging || bounds.inset(-2).contains(mx, my);
		hoverT = Anim.approach(hoverT, hover ? 1f : 0f, dt, 18f);
		int cy = bounds.y() + bounds.h() / 2;
		Draw.roundRect(g, bounds.x(), cy - 2, bounds.w(), 4, 2, Theme.fade(Theme.SWITCH_OFF, alpha));
		float v = (float) Math.max(0, Math.min(1, value.getAsDouble()));
		int fill = Math.round(bounds.w() * v);
		Draw.roundRect(g, bounds.x(), cy - 2, Math.max(4, fill), 4, 2, Theme.fade(Theme.accent(), alpha));
		int knob = 8;
		int kx = Math.max(bounds.x(), Math.min(bounds.right() - knob, bounds.x() + fill - knob / 2));
		Draw.circle(g, kx, cy - knob / 2, knob, Theme.fade(Theme.mix(0xFFD8D9E0, 0xFFFFFFFF, hoverT), alpha));
	}

	public boolean click(double mx, double my) {
		if (!bounds.inset(-3).contains(mx, my)) return false;
		dragging = true;
		update(mx);
		return true;
	}

	public boolean drag(double mx) {
		if (!dragging) return false;
		update(mx);
		return true;
	}

	public boolean release() {
		if (!dragging) return false;
		dragging = false;
		return true;
	}

	private void update(double mx) {
		onChange.accept(Anim.clamp01((float) ((mx - bounds.x()) / Math.max(1, bounds.w()))));
	}
}
