package dev.telmuad.hollowclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Shared base for Hollow's screens: a fade/scale-in animation, a frame-time {@code dt} for widget animations,
 * a dimmed (not blurred) background so the HUD stays visible, and "back" to the parent screen on close.
 */
public abstract class HollowScreen extends Screen {
	protected static final float OPEN_MS = 150f;

	protected final Screen parent;
	private long openedAt = -1;
	private long lastFrameNanos;
	protected float dt;

	protected HollowScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (openedAt < 0) openedAt = Util.getMillis();
	}

	/** 0..1 while opening. */
	protected float openProgress() {
		if (openedAt < 0) return 1f;
		return Anim.easeOutCubic((Util.getMillis() - openedAt) / OPEN_MS);
	}

	/** Skip the open animation (used when switching between Hollow screens). */
	protected void skipOpenAnimation() {
		openedAt = Util.getMillis() - (long) OPEN_MS;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float p = openProgress();
		g.fillGradient(0, 0, width, height, Theme.fade(Theme.DIM_TOP, p), Theme.fade(Theme.DIM_BOTTOM, p));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
		super.render(g, mouseX, mouseY, partialTick);
		float p = openProgress();
		float scale = 0.97f + 0.03f * p;
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(scale, scale);
		g.pose().translate(-width / 2f, -height / 2f);
		renderContent(g, mouseX, mouseY, p);
		g.pose().popMatrix();
		renderOverlay(g, mouseX, mouseY);
	}

	/** Draw the screen. {@code alpha} is the open-animation progress; multiply colors with it. */
	protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float alpha);

	/** Draw things above everything (popups). Not affected by the open animation. */
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Switch to another Hollow screen without replaying the open animation. */
	protected void open(HollowScreen next) {
		next.skipOpenAnimation();
		if (minecraft != null) minecraft.setScreen(next);
	}
}
