package dev.telmuad.hollowclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** A HUD module that shows a few lines of text. Lines line up on the right when the element sits on the right. */
public abstract class TextHudModule extends HudModule {
	/** One line of text; color 0 means "use the text color setting". */
	public record Line(String text, int color) {
		public static Line of(String text) {
			return new Line(text, 0);
		}
	}

	private final List<Line> lines = new ArrayList<>();
	private int width;

	protected TextHudModule(String id, String name, String description, boolean enabledByDefault, HudPosition defaults) {
		super(id, name, description, enabledByDefault, defaults);
	}

	/** Fill {@code out} with the lines to show. */
	protected abstract void lines(Minecraft mc, boolean preview, List<Line> out);

	@Override
	public final Size measure(Minecraft mc, Font font, boolean preview) {
		lines.clear();
		lines(mc, preview, lines);
		width = 0;
		for (Line l : lines) width = Math.max(width, width(font, l.text()));
		if (lines.isEmpty()) return new Size(0, 0);
		return new Size(width + 2 * PAD, lines.size() * 10 - 1 + 2 * PAD - 1);
	}

	@Override
	public final void draw(GuiGraphics g, Minecraft mc, Font font, boolean preview) {
		if (lines.isEmpty()) return;
		int w = width + 2 * PAD;
		drawBackground(g, w, lines.size() * 10 - 1 + 2 * PAD - 1);
		boolean right = alignRight();
		int y = PAD;
		for (int i = 0; i < lines.size(); i++) {
			Line l = lines.get(i);
			int x = right ? w - PAD - width(font, l.text()) : PAD;
			rollingText(g, font, l.text(), x, y, l.color() != 0 ? l.color() : textColor.argb(), i);
			y += 10;
		}
	}
}
