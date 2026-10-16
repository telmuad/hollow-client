package dev.telmuad.hollowclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Text helpers for fixed-width spots. */
public final class Text {
	private static final String ELLIPSIS = "…";

	private Text() {
	}

	public static String fit(Font font, String text, int width) {
		return Ui.fit(font, text, width);
	}

	/** Draw wrapped text, at most {@code maxLines} lines; the last line gets an ellipsis if cut. Returns lines drawn. */
	public static int wrapped(GuiGraphicsExtractor g, Font font, String text, int x, int y, int width, int maxLines, int color) {
		List<FormattedCharSequence> lines = font.split(Ui.text(text), Math.max(1, width));
		int n = Math.min(maxLines, lines.size());
		for (int i = 0; i < n; i++) {
			if (i == n - 1 && lines.size() > n) {
				// Rebuild the cut line as a string so it can carry an ellipsis.
				StringBuilder sb = new StringBuilder();
				lines.get(i).accept((idx, style, cp) -> {
					sb.appendCodePoint(cp);
					return true;
				});
				String line = sb.toString().stripTrailing();
				String shown = Ui.width(font, line + ELLIPSIS) <= width ? line + ELLIPSIS : fit(font, line, width);
				Ui.draw(g, font, shown, x, y + i * 10, color);
			} else {
				g.text(font, lines.get(i), x, y + i * 10, color, false);
			}
		}
		return n;
	}
}
