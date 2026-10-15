package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.client.MenuFxModule;
import dev.telmuad.hollowclient.render.Quality;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Shared base for Hollow's screens:
 * <ul>
 *   <li>springs open (fade + a scale with a little overshoot) and fades/shrinks away on close, with whoosh sounds;</li>
 *   <li>a blurred, dimmed background (blur strength from the theme, off in Performance Mode) with the void particle
 *   field on top;</li>
 *   <li>click ripples, a frame-time {@code dt} for widget animations, glass panels, and "back" to the parent.</li>
 * </ul>
 * Every effect follows the Menu Effects module (switch + strength each) and Performance Mode.
 */
public abstract class HollowScreen extends Screen {
	protected static final float OPEN_MS = 180f;
	private static final float CLOSE_MS = 120f;

	protected final Screen parent;
	private long openedAt = -1;
	private long closingAt = -1;
	private boolean closeScheduled;
	private long lastFrameNanos;
	private final Spring openScale = Spring.bouncy(0.9f).target(1f);
	protected float dt;

	protected HollowScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	protected static MenuFxModule fx() {
		return Modules.MENU_FX;
	}

	/** Strength (0..1) of one menu effect right now; 0 if it is off or not allowed. */
	public static float fxAmount(java.util.function.Function<MenuFxModule, MenuFxModule.Effect> effect) {
		MenuFxModule m = fx();
		return m == null ? 0f : effect.apply(m).amount();
	}

	@Override
	protected void init() {
		if (openedAt < 0) {
			openedAt = Util.getMillis();
			if (!(parent instanceof HollowScreen)) UiSounds.play(UiSounds.OPEN, 1f, 0.6f);
		}
	}

	private boolean animated() {
		return fxAmount(m -> m.entrance) > 0f;
	}

	/** 0..1 while opening, back to 0 while closing. Multiply colors with it. */
	protected float openProgress() {
		long now = Util.getMillis();
		float speed = Theme.speed();
		float p = openedAt < 0 ? 1f : Anim.easeOutCubic((now - openedAt) * speed / (animated() ? OPEN_MS : 90f));
		if (closingAt >= 0) p = Math.min(p, 1f - Anim.clamp01((now - closingAt) * speed / CLOSE_MS));
		return p;
	}

	/** Skip the open animation (used when switching between Hollow screens). */
	protected void skipOpenAnimation() {
		openedAt = Util.getMillis() - (long) OPEN_MS * 4;
		openScale.snap(1f);
	}

	protected boolean closing() {
		return closingAt >= 0;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (minecraft != null && minecraft.level == null && !titleBackground(g, mouseX, mouseY)) renderPanorama(g, partialTick);
		if (blurRadius(0) >= 1) ScreenStyles.tryBlur(g);
		float p = openProgress();
		g.fillGradient(0, 0, width, height, Theme.fade(Theme.DIM_TOP, p), Theme.fade(Theme.DIM_BOTTOM, p));
		renderParticles(g, mouseX, mouseY, p);
	}

	/** On the title screen (no world), Hollow menus use the title screen's nebula when it is on. */
	private boolean titleBackground(GuiGraphics g, int mouseX, int mouseY) {
		var m = Modules.TITLE_SCREEN;
		if (m == null || !m.isEnabled() || m.background() != dev.telmuad.hollowclient.modules.ui.TitleScreenModule.Background.NEBULA) return false;
		if (!Quality.heavyAllowed()) return false;
		return dev.telmuad.hollowclient.render.Nebula.draw(g, width, height, mouseX, mouseY, m.parallax(), m.brightness());
	}

	protected void renderParticles(GuiGraphics g, int mouseX, int mouseY, float alpha) {
		float amount = fxAmount(m -> m.particles) * Math.max(0.35f, Quality.scale());
		ParticleField.shared().render(g, width, height, mouseX, mouseY, dt, alpha, amount);
	}

	/** Menu blur radius while this screen is open (0..10, like the Accessibility option). See OptionsMixin. */
	public int blurRadius(int vanilla) {
		if (!Quality.heavyAllowed()) return 0;
		return Math.round(Theme.current().blur() * 10f * openProgress());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
		super.render(g, mouseX, mouseY, partialTick);
		float p = openProgress();
		float scale;
		if (animated()) {
			scale = openScale.update(dt * Theme.speed());
		} else {
			openScale.snap(1f);
			scale = 0.98f + 0.02f * p;
		}
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(scale, scale);
		g.pose().translate(-width / 2f, -height / 2f);
		renderContent(g, mouseX, mouseY, p);
		Ripples.shared().render(g, p);
		g.pose().popMatrix();
		renderOverlay(g, mouseX, mouseY);
		if (closing() && p <= 0f) finishClose();
	}

	/** Draw the screen. {@code alpha} is the open-animation progress; multiply colors with it. */
	protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float alpha);

	/** Draw things above everything (popups). Not affected by the open animation. */
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
	}

	// ------------------------------------------------------------------------------------------------ effects

	/** A glass panel with the theme glow and (if enabled) the flowing gradient border. */
	protected void drawPanel(GuiGraphics g, Rect r, int designRadius, float alpha) {
		Fx.glass(g, r, Theme.radius(designRadius), alpha, Theme.glow(), fxAmount(m -> m.borders), 0f);
	}

	/** Glow around something under the mouse; {@code hover} is its 0..1 hover animation. */
	public static void hoverGlow(GuiGraphics g, Rect r, float hover, float alpha) {
		float amount = fxAmount(m -> m.hoverGlow);
		if (amount <= 0f || hover <= 0.01f) return;
		Fx.glow(g, r.x(), r.y(), r.w(), r.h(), 8, ColorUtil.fade(Theme.accent(), alpha * hover * amount * (0.25f + 0.45f * Theme.glow())));
	}

	/** Start a click ripple at the mouse, clipped to {@code clip}. */
	public static void ripple(double x, double y, Rect clip) {
		float amount = fxAmount(m -> m.ripples);
		if (amount > 0f) Ripples.shared().add(x, y, clip, ColorUtil.mix(Theme.accent(), 0xFFFFFFFF, 0.45f), amount);
	}

	// ------------------------------------------------------------------------------------------------ closing

	@Override
	public void onClose() {
		if (minecraft == null) return;
		// moving back to another Hollow screen is instant; leaving Hollow's menus plays the close animation
		if (parent instanceof HollowScreen || !animated()) {
			if (!(parent instanceof HollowScreen)) UiSounds.play(UiSounds.CLOSE, 1f, 0.6f);
			minecraft.setScreen(parent);
			return;
		}
		if (closing()) return;
		closingAt = Util.getMillis();
		openScale.target(0.94f);
		UiSounds.play(UiSounds.CLOSE, 1f, 0.6f);
	}

	@Override
	public void tick() {
		super.tick();
		// safety net if frames stop (window minimized): finish closing anyway
		if (closing() && Util.getMillis() - closingAt > CLOSE_MS * 4) finishClose();
	}

	private void finishClose() {
		if (closeScheduled || minecraft == null) return;
		closeScheduled = true;
		minecraft.execute(() -> {
			if (minecraft.screen == this) minecraft.setScreen(parent);
		});
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
