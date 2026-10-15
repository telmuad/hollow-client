package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.ui.TitleScreenModule;
import dev.telmuad.hollowclient.render.Nebula;
import dev.telmuad.hollowclient.render.Quality;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import com.mojang.realmsclient.RealmsMainScreen;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hollow's title screen. It stands in for vanilla's (it extends {@link TitleScreen} so anything that waits for "the
 * title screen" still finds it) with an animated nebula or the panorama, mouse parallax, the glowing sigil and
 * wordmark, a column of animated buttons, and a clock + FPS chip. The buttons open the same vanilla screens.
 */
public final class HollowTitleScreen extends TitleScreen {
	private static final int GAP = 5;
	private static final int FOOTER = 16;
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);

	private final boolean firstOpen;
	private long openedAt = -1;
	private long lastNanos;
	private float dt;
	private float parX;
	private float parY;
	private int focus = -1;
	private final List<Entry> entries = new ArrayList<>();
	private final List<Entry> small = new ArrayList<>();
	private Rect column = Rect.EMPTY;
	private Rect copyright = Rect.EMPTY;

	private static final class Entry {
		final Component label;
		final Runnable action;
		final boolean active;
		final @Nullable Component whyNot;
		final boolean primary;
		Rect bounds = Rect.EMPTY;
		float hover;
		boolean wasHover;

		Entry(Component label, Runnable action, boolean active, @Nullable Component whyNot, boolean primary) {
			this.label = label;
			this.action = action;
			this.active = active;
			this.whyNot = whyNot;
			this.primary = primary;
		}
	}

	public HollowTitleScreen(boolean fading) {
		super(fading);
		this.firstOpen = fading;
	}

	private static TitleScreenModule module() {
		return Modules.TITLE_SCREEN;
	}

	@Override
	protected void init() {
		entries.clear();
		small.clear();
		boolean mp = minecraft.allowsMultiplayer();
		Component why = mp ? null : Component.translatable("title.multiplayer.disabled");
		entries.add(new Entry(Component.translatable("menu.singleplayer"), () -> minecraft.setScreen(new SelectWorldScreen(this)), true, null, false));
		entries.add(new Entry(Component.translatable("menu.multiplayer"), () -> minecraft.setScreen(
				minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)), mp, why, false));
		entries.add(new Entry(Component.translatable("menu.online"), () -> minecraft.setScreen(new RealmsMainScreen(this)), mp, why, false));
		entries.add(new Entry(Component.literal("Hollow Client"), () -> minecraft.setScreen(new HollowMenuScreen(this)), true, null, true));
		entries.add(new Entry(Component.translatable("menu.options"), () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)), true, null, false));
		entries.add(new Entry(Component.translatable("menu.quit"), () -> minecraft.stop(), true, null, false));
		small.add(new Entry(Component.translatable("options.language"), () -> minecraft.setScreen(
				new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())), true, null, false));
		small.add(new Entry(Component.translatable("options.accessibility"), () -> minecraft.setScreen(
				new AccessibilityOptionsScreen(this, minecraft.options)), true, null, false));
		if (openedAt < 0) openedAt = Util.getMillis();
		Component credits = Component.translatable("title.credits");
		int cw = Ui.width(font, credits);
		copyright = new Rect(width - cw - 6, height - 12, cw + 4, 10);
	}

	// ------------------------------------------------------------------------------------------------ render

	private float entrance(int index) {
		if (HollowScreen.fxAmount(m -> m.entrance) <= 0f) return 1f;
		float delay = (firstOpen ? 350f : 60f) + index * 55f;
		float dur = firstOpen ? 520f : 320f;
		return Anim.clamp01((Util.getMillis() - openedAt - delay) * Theme.speed() / dur);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		TitleScreenModule m = module();
		float parallax = m == null ? 0f : m.parallax();
		boolean nebula = m != null && m.background() == TitleScreenModule.Background.NEBULA && Quality.heavyAllowed()
				&& Nebula.draw(g, width, height, mouseX, mouseY, parallax, m.brightness());
		if (!nebula) renderPanorama(g, partialTick);
		// soft darkening on the left, behind the menu column
		g.fillGradient(0, 0, width, height, 0x10000000, 0x50000000);
		float particles = HollowScreen.fxAmount(f -> f.particles) * Math.max(0.35f, Quality.scale());
		ParticleField.shared().render(g, width, height, mouseX, mouseY, dt, 1f, particles);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		TitleScreenModule m = module();
		float strength = m == null ? 0f : m.parallax();
		float tx = width > 0 ? (mouseX - width / 2f) / (width / 2f) : 0f;
		float ty = height > 0 ? (mouseY - height / 2f) / (height / 2f) : 0f;
		parX = Anim.approach(parX, -tx * strength, dt, 4f);
		parY = Anim.approach(parY, -ty * strength, dt, 4f);

		renderWatermark(g);

		boolean big = height >= 300;
		int sig = big ? 44 : 28;
		float wordH = big ? 20f : 13f;
		int bw = Math.min(160, Math.max(110, width / 4));
		int x0 = Math.max(18, Math.round(width * 0.09f));
		int n = entries.size();
		// button height shrinks on short windows so the panel never runs into the footer
		int room = height - FOOTER - 10 - sig - 12 - 16 - 8 - 18;
		int buttonH = Math.max(15, Math.min(22, (room - (n - 1) * GAP) / n));
		int buttonsH = n * buttonH + (n - 1) * GAP;
		int blockH = sig + 12 + buttonsH + 8 + 18 + 8;
		int y0 = Math.max(8, (height - FOOTER - blockH) / 2);
		int ox = Math.round(parX * 5);
		int oy = Math.round(parY * 4);

		// logo row: glowing sigil + wordmark
		float la = Anim.easeOutCubic(entrance(0));
		float lx = x0 + ox - (1f - la) * 16f;
		float glow = Theme.glow() * (0.7f + 0.3f * la);
		Fx.sigil(g, lx, y0 + oy, sig, la, 1f, glow);
		Fx.wordmark(g, lx + sig + 8, y0 + oy + (sig - wordH) / 2f, wordH, la, true);

		// buttons in a glass column
		int by = y0 + sig + 12;
		column = new Rect(x0 - 8 + ox, by - 8 + oy, bw + 16, buttonsH + 16 + 8 + 18);
		float ca = Anim.easeOutCubic(entrance(1));
		Fx.glass(g, column, Theme.radius(8), ca * 0.9f, Theme.glow() * 0.6f, HollowScreen.fxAmount(f -> f.borders), 0.1f);
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			e.bounds = new Rect(x0 + ox, by + oy + i * (buttonH + GAP), bw, buttonH);
			renderButton(g, e, i == focus, mouseX, mouseY, entrance(i + 1));
		}
		int sy = by + oy + buttonsH + 8;
		for (int i = 0; i < small.size(); i++) {
			Entry e = small.get(i);
			e.bounds = new Rect(x0 + ox + i * (18 + GAP), sy, 18, 18);
			renderSmall(g, e, i, mouseX, mouseY, entrance(entries.size() + 1));
		}

		renderInfoChip(g);
		renderFooter(g, mouseX, mouseY);
		Ripples.shared().render(g, 1f);
	}

	/** A large, faint sigil turning slowly on the right; it moves the most with the mouse. */
	private void renderWatermark(GuiGraphics g) {
		float a = 0.07f * Anim.easeOutCubic(entrance(0));
		if (a <= 0.005f) return;
		float size = Math.min(height * 0.85f, width * 0.45f);
		float cx = width * 0.72f + parX * 16;
		float cy = height * 0.5f + parY * 12;
		float angle = HollowScreen.fxAmount(f -> f.entrance) > 0f ? (float) ((Util.getMillis() * Theme.speed() / 60000.0) % 1.0 * Math.PI * 2) : 0f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(angle);
		Fx.SIGIL_GLOW.draw(g, -size * 0.65f, -size * 0.65f, size * 1.3f, size * 1.3f, ColorUtil.fade(Theme.accent(), a * Theme.glow()));
		Fx.SIGIL.draw(g, -size / 2f, -size / 2f, size, size, ColorUtil.fade(Theme.accent2(), a));
		g.pose().popMatrix();
	}

	private void renderButton(GuiGraphics g, Entry e, boolean focused, int mx, int my, float appear) {
		float a = Anim.easeOutCubic(appear);
		if (a <= 0.01f) return;
		boolean hover = e.active && (e.bounds.contains(mx, my) || focused);
		if (hover && !e.wasHover) UiSounds.hover();
		e.wasHover = hover;
		e.hover = Anim.approach(e.hover, hover ? 1f : 0f, dt, 16f);
		int slide = Math.round((1f - a) * -18f);
		Rect r = e.bounds.offset(slide, 0);
		HollowScreen.hoverGlow(g, r, e.hover, a);
		int rad = Theme.radius(5);
		int bg = e.primary ? Theme.mix(Theme.accentSoft(), Theme.accent(), e.hover * 0.6f) : Theme.mix(0x12FFFFFF, Theme.CARD_HOVER, e.hover);
		Draw.roundRect(g, r, rad, Theme.fade(bg, a));
		if (e.primary) Draw.roundGradient(g, r.x(), r.y(), r.w(), r.h(), rad, Theme.fade(0x30FFFFFF, a), 0x00FFFFFF);
		Draw.roundOutline(g, r, rad, Theme.fade(Theme.mix(e.primary ? 0x40FFFFFF : Theme.STROKE, Theme.accent(), e.hover * 0.8f), a));
		// accent bar grows on hover
		int barH = Math.round(4 + 8 * e.hover);
		if (e.hover > 0.01f) Draw.roundRect(g, r.x() + 3, r.centerY() - barH / 2, 2, barH, 1, Theme.fade(e.primary ? 0xFFFFFFFF : Theme.accent(), a * e.hover));
		int textColor = !e.active ? Theme.TEXT_FAINT : (e.primary ? 0xFFFFFFFF : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, Math.max(e.hover, 0.35f)));
		int tx = r.x() + 12 + Math.round(e.hover * 3);
		if (e.primary) {
			Fx.sigil(g, (float) tx - 1, r.y() + 4f, 12f, a, 0f, 0f);
			tx += 15;
		}
		Ui.draw(g, font, e.label, tx, r.y() + 6, Theme.fade(textColor, a));
		if (!e.active && e.bounds.contains(mx, my) && e.whyNot != null) g.setTooltipForNextFrame(font, e.whyNot, mx, my);
	}

	/** The small icon buttons (language, accessibility) under the column; their names show as tooltips. */
	private void renderSmall(GuiGraphics g, Entry e, int index, int mx, int my, float appear) {
		float a = Anim.easeOutCubic(appear);
		if (a <= 0.01f) return;
		boolean hover = e.bounds.contains(mx, my);
		if (hover && !e.wasHover) UiSounds.hover();
		e.wasHover = hover;
		e.hover = Anim.approach(e.hover, hover ? 1f : 0f, dt, 16f);
		HollowScreen.hoverGlow(g, e.bounds, e.hover, a);
		Draw.roundRect(g, e.bounds, Theme.radius(5), Theme.fade(Theme.mix(0x14FFFFFF, Theme.CARD_HOVER, e.hover), a));
		Draw.roundOutline(g, e.bounds, Theme.radius(5), Theme.fade(Theme.mix(Theme.STROKE, Theme.accent(), e.hover), a));
		var sprite = net.minecraft.resources.Identifier.withDefaultNamespace(index == 0 ? "icon/language" : "icon/accessibility");
		g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, e.bounds.x() + 2, e.bounds.y() + 2, 14, 14,
				ColorUtil.fade(Theme.mix(0xFFB8BCCB, 0xFFFFFFFF, e.hover), a));
		if (hover) g.setTooltipForNextFrame(font, e.label, mx, my);
	}

	private void renderInfoChip(GuiGraphics g) {
		TitleScreenModule m = module();
		if (m == null || (!m.clock() && !m.fps())) return;
		List<String> parts = new ArrayList<>();
		if (m.clock()) parts.add(LocalTime.now().format(m.twentyFour() ? H24 : H12));
		if (m.fps()) parts.add(minecraft.getFps() + " FPS");
		String text = String.join("  ·  ", parts);
		float a = Anim.easeOutCubic(entrance(2));
		int w = Ui.width(font, text) + 16;
		Rect r = new Rect(width - w - 10 + Math.round(parX * 3), 10 + Math.round(parY * 2), w, 18);
		Fx.glass(g, r, Theme.radius(9), a * 0.9f, 0f, 0f, 0f);
		Draw.circle(g, r.x() + 6, r.centerY() - 2, 4, Theme.fade(Theme.accent(), a));
		Ui.draw(g, font, text, r.x() + 12, r.y() + 5, Theme.fade(Theme.TEXT, a));
	}

	private void renderFooter(GuiGraphics g, int mx, int my) {
		String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + " " + I18n.get("menu.modded").trim()
				+ "  ·  Hollow Client " + FabricLoader.getInstance().getModContainer(HollowClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		Ui.draw(g, font, version, 6, height - 11, Theme.fade(Theme.TEXT_FAINT, 0.9f));
		boolean hover = copyright.contains(mx, my);
		Component credits = Component.translatable("title.credits");
		Ui.draw(g, font, credits, copyright.x() + 2, copyright.y() + 1, hover ? Theme.TEXT : Theme.fade(Theme.TEXT_FAINT, 0.9f));
		if (hover) g.fill(copyright.x() + 2, copyright.bottom(), copyright.right() - 2, copyright.bottom() + 1, Theme.TEXT_DIM);
	}

	// ------------------------------------------------------------------------------------------------ input

	private void activate(Entry e, double mx, double my) {
		if (!e.active) return;
		UiSounds.click();
		HollowScreen.ripple(mx, my, e.bounds);
		e.action.run();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (Entry e : entries) {
				if (e.bounds.contains(event.x(), event.y())) {
					activate(e, event.x(), event.y());
					return true;
				}
			}
			for (Entry e : small) {
				if (e.bounds.contains(event.x(), event.y())) {
					activate(e, event.x(), event.y());
					return true;
				}
			}
			if (copyright.contains(event.x(), event.y())) {
				UiSounds.click();
				minecraft.setScreen(new CreditsAndAttributionScreen(this));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Keybinds.menuKey() != null && Keybinds.menuKey().matches(event)) {
			UiSounds.click();
			minecraft.setScreen(new HollowMenuScreen(this));
			return true;
		}
		int key = event.key();
		if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB && !event.hasShiftDown()) {
			focus = next(focus, 1);
			return true;
		}
		if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_TAB) {
			focus = next(focus, -1);
			return true;
		}
		if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && focus >= 0) {
			Entry e = entries.get(focus);
			activate(e, e.bounds.centerX(), e.bounds.centerY());
			return true;
		}
		return super.keyPressed(event);
	}

	private int next(int from, int dir) {
		int n = entries.size();
		int i = from < 0 ? (dir > 0 ? -1 : n) : from;
		for (int step = 0; step < n; step++) {
			i = Math.floorMod(i + dir, n);
			if (entries.get(i).active) return i;
		}
		return -1;
	}
}
