package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Fx;
import dev.telmuad.hollowclient.gui.HollowScreen;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Shared look of the SkyBlock screens: a big glass panel with the SkyBlock badge, a title and a subtitle in the header,
 * a "< Back" button, and helpers for cards that slide in one after another, label/value rows and ✓/✗ marks.
 */
public abstract class SbScreen extends HollowScreen {
	public static final Identifier BADGE = HollowClient.id("textures/gui/skyblock_badge.png");
	protected static final int OK = 0xFF5BD18B, BAD = 0xFFF2706B, GOLD = 0xFFF5C542;

	protected final UiButton back = new UiButton("< Back", this::onClose);
	protected Rect panel = Rect.EMPTY;
	protected Rect body = Rect.EMPTY;
	private long shownAt;

	protected SbScreen(String title, Screen parent) {
		super(Component.literal(title), parent);
	}

	@Override
	protected void init() {
		super.init();
		int pw = Math.min(width - 16, 760);
		int ph = Math.min(height - 16, 420);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		body = new Rect(panel.x() + 10, panel.y() + 36, pw - 20, ph - 36 - 18);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 9, back.preferredWidth(font), 16));
		if (shownAt == 0) shownAt = Util.getMillis();
	}

	/** Subtitle under the title (location, counts, ...). */
	protected abstract String subtitle();

	/** Footer hint. */
	protected String footer() {
		return "Esc goes back";
	}

	protected void drawFrame(GuiGraphicsExtractor g, int mx, int my, float a) {
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		int x = back.bounds().right() + 10;
		int y = panel.y() + 7;
		g.blit(RenderPipelines.GUI_TEXTURED, BADGE, x, y, 0f, 0f, 20, 20, 20, 20, ColorUtil.fade(0xFFFFFFFF, a));
		int tx = x + 25;
		Fx.gradientBold(g, font, getTitle().getString(), tx, y + 2, a);
		Ui.draw(g, font, Ui.fit(font, subtitle(), panel.right() - tx - 12), tx, y + 13, Theme.fade(Theme.TEXT_DIM, a));
		g.fill(panel.x() + 8, panel.y() + 31, panel.right() - 8, panel.y() + 32, Theme.fade(Theme.STROKE, a));
		Ui.draw(g, font, Ui.fit(font, footer(), panel.w() - 20), panel.x() + 10, panel.bottom() - 13, Theme.fade(Theme.TEXT_FAINT, a));
	}

	/** 0..1 slide-in of the i-th card. */
	protected float appear(int i) {
		if (fxAmount(m -> m.entrance) <= 0f) return 1f;
		return Anim.easeOutCubic(Anim.clamp01((Util.getMillis() - shownAt - i * 45L) * Theme.speed() / 320f));
	}

	/** A card with a title; returns the card's alpha (0 while it hasn't appeared yet). */
	protected float card(GuiGraphicsExtractor g, Rect r, String title, int index, float a) {
		float ap = appear(index);
		float ca = a * ap;
		if (ca <= 0.01f) return 0f;
		g.pose().pushMatrix();
		g.pose().translate(0, (1f - ap) * 10f);
		Draw.roundRect(g, r, Theme.radius(6), Theme.fade(Theme.CARD, ca));
		Draw.roundGradient(g, r.x(), r.y(), r.w(), Math.min(24, r.h()), Theme.radius(6), Theme.fade(0x12FFFFFF, ca), 0x00FFFFFF);
		Draw.roundOutline(g, r, Theme.radius(6), Theme.fade(Theme.STROKE, ca));
		Ui.draw(g, font, Ui.bold(title), r.x() + 8, r.y() + 7, Theme.fade(Theme.TEXT, ca));
		g.pose().popMatrix();
		return ca;
	}

	/** Translate for the card body while it slides in (pair with {@code g.pose().popMatrix()}). */
	protected void pushCard(GuiGraphicsExtractor g, int index) {
		g.pose().pushMatrix();
		g.pose().translate(0, (1f - appear(index)) * 10f);
	}

	/** "label ........ value" on one line: the value gets its room first (up to 55%), the label the rest. */
	protected void row(GuiGraphicsExtractor g, String label, String value, int x, int y, int w, int valueColor, float a) {
		String v = Ui.fit(font, value, w * 55 / 100);
		int vw = Ui.width(font, v);
		Ui.draw(g, font, Ui.fit(font, label, w - vw - 6), x, y, Theme.fade(Theme.TEXT_DIM, a));
		Ui.draw(g, font, v, x + w - vw, y, Theme.fade(valueColor == 0 ? Theme.TEXT : valueColor, a));
	}

	/** A small ✓ or ✗ in a circle. */
	protected void mark(GuiGraphicsExtractor g, int x, int y, boolean ok, float a) {
		int c = ok ? OK : BAD;
		Draw.circle(g, x, y, 10, ColorUtil.fade(c, a * 0.25f));
		Ui.center(g, font, ok ? "✓" : "✗", x + 5, y + 1, ColorUtil.fade(c, a));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && back.click(event.x(), event.y())) return true;
		return super.mouseClicked(event, doubleClick);
	}
}
