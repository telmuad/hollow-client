package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.ui.ChatStyleModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Chat restyle (used by the chat mixins): one rounded, theme-tinted panel behind the visible lines instead of black
 * bars, an accent edge, new lines sliding in from the left, and a glassy typing bar. Message text, hover and click
 * handling stay vanilla's.
 */
public final class ChatStyle {
	private static final int RADIUS = 5;
	private static final float SLIDE_TICKS = 5f;

	private ChatStyle() {
	}

	private static ChatStyleModule module() {
		ChatStyleModule m = Modules.CHAT_STYLE;
		return m != null && m.isEnabled() ? m : null;
	}

	public static boolean backgroundOn() {
		ChatStyleModule m = module();
		return m != null && m.background().on();
	}

	public static boolean inputBar() {
		ChatStyleModule m = module();
		return m != null && m.inputBar();
	}

	/**
	 * Draw the panel. {@code rows} holds one {index, alpha} pair per visible line, as vanilla's chat loop reports them
	 * (index 0 = newest, at the bottom). Coordinates match vanilla's (already scaled) chat space.
	 */
	public static void drawBackground(ChatComponent chat, ChatComponent.ChatGraphicsAccess access, List<float[]> rows, int screenHeight) {
		if (rows.isEmpty()) return;
		ChatStyleModule m = module();
		Minecraft mc = Minecraft.getInstance();
		if (scale0(mc) <= 0f) return;
		float scale = mc.options.chatScale().get().floatValue();
		int width = Mth.ceil(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale);
		int bottom = Mth.floor((screenHeight - 40) / scale);
		int lineH = (int) (9 * (mc.options.chatLineSpacing().get() + 1.0));
		float opacity = m.background().strength() * 0.9f + 0.1f;
		int base = ColorUtil.mix(0xFF0A0B12, Theme.accent(), 0.16f);
		int x0 = -4;
		int x1 = width + 8;
		int top = Integer.MAX_VALUE;
		int low = Integer.MIN_VALUE;
		for (float[] row : rows) {
			int k = (int) row[0];
			top = Math.min(top, bottom - (k + 1) * lineH);
			low = Math.max(low, bottom - k * lineH);
		}
		for (float[] row : rows) {
			int k = (int) row[0];
			float a = row[1];
			int y1 = bottom - k * lineH;
			int y0 = y1 - lineH;
			int color = ColorUtil.fade(ColorUtil.withAlpha(base, 255), a * opacity);
			for (int y = y0; y < y1; y++) {
				int inset = inset(y - top, low - 1 - y);
				access.fill(x0 + inset, y, x1 - inset, y + 1, color);
			}
			if (m.accentEdge()) {
				// a 2 px bar in the theme gradient just inside the left edge
				int e0 = Math.max(y0, top + 2);
				int e1 = Math.min(y1, low - 2);
				if (e1 > e0) access.fill(x0 + 1, e0, x0 + 3, e1, ColorUtil.fade(Theme.gradient(Math.min(1f, k / 10f)), a * Math.min(1f, opacity + 0.35f)));
			}
		}
	}

	private static float scale0(Minecraft mc) {
		return mc.options.chatScale().get().floatValue();
	}

	/** How far a pixel row is pulled in at the rounded top and bottom corners. */
	private static int inset(int fromTop, int fromBottom) {
		int d = Math.min(fromTop, fromBottom);
		if (d >= RADIUS || d < 0) return 0;
		double dy = RADIUS - d - 0.5;
		return (int) Math.round(RADIUS - Math.sqrt(RADIUS * RADIUS - dy * dy));
	}

	/** 0..1 how far a new line is into its slide-in (1 = done). */
	private static float slideProgress(GuiMessage.Line line) {
		ChatStyleModule m = module();
		if (m == null || m.slide() <= 0f) return 1f;
		Minecraft mc = Minecraft.getInstance();
		float age = mc.gui.getGuiTicks() - line.addedTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return Anim.clamp01(age * Theme.speed() / SLIDE_TICKS);
	}

	/** Horizontal offset for a line that is sliding in (negative = still to the left). */
	public static float slideOffset(GuiMessage.Line line) {
		float t = slideProgress(line);
		if (t >= 1f) return 0f;
		return -(1f - Anim.easeOutCubic(t)) * 36f * Modules.CHAT_STYLE.slide();
	}

	public static float slideFade(GuiMessage.Line line) {
		return Anim.easeOutCubic(slideProgress(line));
	}

	/** The typing bar at the bottom of the chat screen. */
	public static void drawInputBar(GuiGraphics g, int x0, int y0, int x1, int y1) {
		Rect r = new Rect(x0, y0 - 1, x1 - x0, y1 - y0 + 2);
		int rad = Theme.radius(4);
		Fx.glow(g, r.x(), r.y(), r.w(), r.h(), 6, Theme.fade(Theme.accent(), 0.12f + 0.25f * Theme.glow()));
		Draw.roundRect(g, r, rad, 0xC80B0C13);
		Draw.roundGradient(g, r.x(), r.y(), r.w(), r.h(), rad, 0x14FFFFFF, 0x00FFFFFF);
		float borders = HollowScreen.fxAmount(f -> f.borders);
		if (borders > 0f) Fx.gradientOutline(g, r, rad, 0.35f + 0.5f * borders, 0f);
		else Draw.roundOutline(g, r, rad, Theme.fade(Theme.accent(), 0.6f));
	}
}
