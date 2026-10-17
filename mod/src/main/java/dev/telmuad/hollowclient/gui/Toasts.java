package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.client.ToastsModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hollow's notification cards. They are drawn right after vanilla's toasts (see ToastManagerMixin), so they show
 * over menus and in game. Each card springs in from the screen edge, shows a draining progress line in the theme
 * gradient, and springs back out. Up to four at once; older ones are pushed out.
 */
public final class Toasts {
	public enum Kind {
		INFO(0), SUCCESS(Theme.SUCCESS), WARNING(0xFFF5B547), ERROR(Theme.DANGER);

		final int color;

		Kind(int color) {
			this.color = color;
		}

		int color() {
			return color == 0 ? Theme.accent() : color;
		}
	}

	private static final int W = 176;
	private static final int H = 34;
	private static final int GAP = 4;
	private static final int MAX = 4;
	private static final int MARGIN = 6;

	private static final class Card {
		final Kind kind;
		final net.minecraft.resources.Identifier icon;
		final String title;
		final String message;
		final long created;
		final long duration;
		final Spring slide = Spring.bouncy(0f).target(1f);
		float y = -1f;
		boolean leaving;
		long last = Util.getMillis();

		Card(Kind kind, String title, String message, long duration, net.minecraft.resources.Identifier icon) {
			this.kind = kind;
			this.icon = icon;
			this.title = title;
			this.message = message;
			this.created = Util.getMillis();
			this.duration = duration;
		}
	}

	private static final List<Card> CARDS = new ArrayList<>();

	private Toasts() {
	}

	public static void info(String title, String message) {
		show(Kind.INFO, title, message);
	}

	public static void success(String title, String message) {
		show(Kind.SUCCESS, title, message);
	}

	public static void warning(String title, String message) {
		show(Kind.WARNING, title, message);
	}

	public static void error(String title, String message) {
		show(Kind.ERROR, title, message);
	}

	private static final net.minecraft.resources.Identifier SKYBLOCK_BADGE = dev.telmuad.hollowclient.HollowClient.id("textures/gui/skyblock_badge.png");

	/** A notification with the SkyBlock badge instead of the Hollow emblem. */
	public static void showBadge(String title, String message) {
		show(Kind.INFO, title, message, SKYBLOCK_BADGE);
	}

	/** Show a notification. Safe to call from any thread. */
	public static void show(Kind kind, String title, String message) {
		show(kind, title, message, null);
	}

	public static void show(Kind kind, String title, String message, net.minecraft.resources.Identifier icon) {
		Minecraft mc = Minecraft.getInstance();
		if (!mc.isSameThread()) {
			mc.execute(() -> show(kind, title, message, icon));
			return;
		}
		ToastsModule module = Modules.TOASTS;
		if (module == null || !module.isEnabled()) {
			SystemToast.addOrUpdate(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.literal(title), message == null || message.isEmpty() ? null : Component.literal(message));
			return;
		}
		// the same message again just restarts the old card
		CARDS.removeIf(c -> c.title.equals(title) && java.util.Objects.equals(c.message, message));
		// too many: the oldest cards slide out (and are dropped at once if they pile up)
		long showing = CARDS.stream().filter(c -> !c.leaving).count();
		for (Card c : CARDS) {
			if (showing < MAX) break;
			if (!c.leaving) {
				c.leaving = true;
				showing--;
			}
		}
		while (CARDS.size() >= MAX * 2) CARDS.remove(0);
		CARDS.add(new Card(kind, title, message, module.durationMs(), icon));
		if (module.sound()) UiSounds.play(kind == Kind.ERROR || kind == Kind.WARNING ? UiSounds.NOTIFY : UiSounds.TOAST);
	}

	/** Number of cards on screen (for tests). */
	public static int count() {
		return CARDS.size();
	}

	public static void clear() {
		CARDS.clear();
	}

	public static void render(GuiGraphicsExtractor g) {
		if (CARDS.isEmpty()) return;
		ToastsModule module = Modules.TOASTS;
		if (module == null) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		ToastsModule.Corner corner = module.corner();
		boolean right = corner == ToastsModule.Corner.TOP_RIGHT || corner == ToastsModule.Corner.BOTTOM_RIGHT;
		boolean top = corner == ToastsModule.Corner.TOP_RIGHT || corner == ToastsModule.Corner.TOP_LEFT;
		long now = Util.getMillis();
		float opacity = module.opacity();

		// newest card nearest the corner
		int slot = 0;
		for (int i = CARDS.size() - 1; i >= 0; i--) {
			Card c = CARDS.get(i);
			float dt = Math.min(0.1f, (now - c.last) / 1000f);
			c.last = now;
			if (!c.leaving && now - c.created > c.duration) c.leaving = true;
			if (c.leaving) c.slide.target(0f);
			c.slide.update(dt * Theme.speed());
			float targetY = top ? MARGIN + slot * (H + GAP) : sh - MARGIN - H - slot * (H + GAP);
			c.y = c.y < 0 ? targetY : Anim.approach(c.y, targetY, dt, 14f);
			if (!c.leaving) slot++;
		}
		for (Iterator<Card> it = CARDS.iterator(); it.hasNext(); ) {
			Card c = it.next();
			if (c.leaving && c.slide.value() <= 0.01f) it.remove();
		}

		for (Card c : CARDS) {
			float s = c.slide.value();
			float a = Math.max(0f, Math.min(1f, s)) * opacity;
			if (a <= 0.01f) continue;
			int x = right ? Math.round(sw - MARGIN - W * s) : Math.round(MARGIN - W * (1f - s));
			int y = Math.round(c.y);
			Rect r = new Rect(x, y, W, H);
			int accent = c.kind.color();
			Fx.glass(g, r, Theme.radius(6), a, Theme.glow() * 0.8f, 0f, 0f);
			// kind stripe and icon
			Draw.roundRect(g, r.x() + 5, r.y() + 6, 2, H - 12, 1, ColorUtil.fade(accent, a));
			if (c.icon != null) {
				g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, c.icon, r.x() + 10, r.y() + (H - 18) / 2, 0f, 0f, 18, 18, 18, 18,
						ColorUtil.fade(0xFFFFFFFF, a));
			} else {
				Fx.sigil(g, r.x() + 11, r.y() + (H - 16) / 2, 16, a, false);
			}
			int tx = r.x() + 32;
			Ui.draw(g, font, Ui.bold(Text.fit(font, c.title, W - 40)), tx, r.y() + 7, ColorUtil.fade(Theme.TEXT, a));
			if (c.message != null && !c.message.isEmpty()) {
				Ui.draw(g, font, Ui.fit(font, c.message, W - 40), tx, r.y() + 19, ColorUtil.fade(Theme.TEXT_DIM, a));
			}
			// time left: a thin gradient line along the bottom
			float left = c.leaving ? 0f : 1f - Math.min(1f, (now - c.created) / (float) c.duration);
			int lw = Math.round((W - 16) * left);
			for (int k = 0; k < lw; k += 4) {
				int seg = Math.min(4, lw - k);
				g.fill(r.x() + 8 + k, r.bottom() - 3, r.x() + 8 + k + seg, r.bottom() - 2,
						ColorUtil.fade(Theme.gradient(k / (float) (W - 16)), a * 0.8f));
			}
		}
	}
}
