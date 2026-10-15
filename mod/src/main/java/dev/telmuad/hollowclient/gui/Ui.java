package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text in Hollow's UI font (Inter, SIL Open Font License) or the vanilla font, depending on the UI Font module.
 * Every Hollow screen and HUD element draws and measures text through here, so switching the font is one toggle.
 */
public final class Ui {
	public static final FontDescription UI = new FontDescription.Resource(HollowClient.id("ui"));
	public static final FontDescription UI_BOLD = new FontDescription.Resource(HollowClient.id("ui_bold"));
	private static final String ELLIPSIS = "…";

	private Ui() {
	}

	public static boolean customFont() {
		return Modules.UI_FONT != null && Modules.UI_FONT.isEnabled();
	}

	public static boolean customFontInHud() {
		return customFont() && Modules.UI_FONT.inHud();
	}

	public static MutableComponent text(String s) {
		MutableComponent c = Component.literal(s == null ? "" : s);
		return customFont() ? c.withStyle(st -> st.withFont(UI)) : c;
	}

	/** Titles: semi-bold Inter, or vanilla bold. */
	public static MutableComponent bold(String s) {
		MutableComponent c = Component.literal(s == null ? "" : s);
		return customFont() ? c.withStyle(st -> st.withFont(UI_BOLD)) : c.withStyle(st -> st.withBold(true));
	}

	/** The component in the UI font, unless it already picks a font itself (for example {@link #bold}). */
	public static Component styled(Component c) {
		if (!customFont() || !FontDescription.DEFAULT.equals(c.getStyle().getFont())) return c;
		return c.copy().withStyle(st -> st.withFont(UI));
	}

	public static int width(Font font, String s) {
		return font.width(text(s));
	}

	public static int width(Font font, Component c) {
		return font.width(styled(c));
	}

	public static int boldWidth(Font font, String s) {
		return font.width(bold(s));
	}

	public static void draw(GuiGraphics g, Font font, String s, int x, int y, int color) {
		g.drawString(font, text(s), x, y, color, false);
	}

	public static void draw(GuiGraphics g, Font font, Component c, int x, int y, int color) {
		g.drawString(font, styled(c), x, y, color, false);
	}

	public static void drawShadow(GuiGraphics g, Font font, String s, int x, int y, int color, boolean shadow) {
		g.drawString(font, text(s), x, y, color, shadow);
	}

	public static void drawBold(GuiGraphics g, Font font, String s, int x, int y, int color) {
		g.drawString(font, bold(s), x, y, color, false);
	}

	public static void center(GuiGraphics g, Font font, String s, int cx, int y, int color) {
		g.drawString(font, text(s), cx - width(font, s) / 2, y, color, false);
	}

	public static void center(GuiGraphics g, Font font, Component c, int cx, int y, int color) {
		Component sc = styled(c);
		g.drawString(font, sc, cx - font.width(sc) / 2, y, color, false);
	}

	// ---- HUD text: the UI font only if the UI Font module says "use it for HUD elements too"

	public static MutableComponent hudText(String s) {
		MutableComponent c = Component.literal(s == null ? "" : s);
		return customFontInHud() ? c.withStyle(st -> st.withFont(UI)) : c;
	}

	public static int hudWidth(Font font, String s) {
		return font.width(hudText(s));
	}

	public static void drawHud(GuiGraphics g, Font font, String s, int x, int y, int color, boolean shadow) {
		g.drawString(font, hudText(s), x, y, color, shadow);
	}

	/** Cut text to a width, ending in "…" when it doesn't fit. */
	public static String fit(Font font, String s, int maxWidth) {
		if (s == null) return "";
		if (width(font, s) <= maxWidth) return s;
		int room = maxWidth - width(font, ELLIPSIS);
		if (room <= 0) return "";
		int lo = 0;
		int hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (width(font, s.substring(0, mid)) <= room) lo = mid;
			else hi = mid - 1;
		}
		return s.substring(0, lo).stripTrailing() + ELLIPSIS;
	}
}
