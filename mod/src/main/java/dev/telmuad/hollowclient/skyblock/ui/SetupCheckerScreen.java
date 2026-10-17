package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Text;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** The setup checker: everything SkyBlock Mode needs, ✓ or ✗, and what to do about each ✗. */
public final class SetupCheckerScreen extends SbScreen {
	private List<SetupCheck.Item> items = List.of();
	private long checkedAt;
	private float scroll;
	private int contentH;

	public SetupCheckerScreen(Screen parent) {
		super("Setup checker", parent);
	}

	@Override
	protected String subtitle() {
		long ok = items.stream().filter(SetupCheck.Item::ok).count();
		return ok + " of " + items.size() + " ready · checked live";
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		if (System.currentTimeMillis() - checkedAt > 1000) {
			items = SetupCheck.run();
			checkedAt = System.currentTimeMillis();
		}
		drawFrame(g, mx, my, a);
		g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
		int y = body.y() - Math.round(scroll);
		int textW = body.w() - 40;
		for (int i = 0; i < items.size(); i++) {
			SetupCheck.Item it = items.get(i);
			float ra = a * appear(i);
			int lines = 1 + font.split(Ui.text(it.detail()), textW).size() + (it.fix().isEmpty() ? 0 : font.split(Ui.text("→ " + it.fix()), textW).size());
			int h = 8 + lines * 10 + 2;
			Rect r = new Rect(body.x(), y, body.w(), h);
			Draw.roundRect(g, r, Theme.radius(5), Theme.fade(Theme.CARD, ra));
			Draw.roundOutline(g, r, Theme.radius(5), Theme.fade(it.ok() ? Theme.STROKE : 0x60F2706B, ra));
			mark(g, r.x() + 8, r.y() + 5, it.ok(), ra);
			int tx = r.x() + 26;
			Ui.draw(g, font, Ui.bold(it.title()), tx, r.y() + 6, Theme.fade(Theme.TEXT, ra));
			int ty = r.y() + 17;
			ty += 10 * Text.wrapped(g, font, it.detail(), tx, ty, textW, 4, Theme.fade(Theme.TEXT_DIM, ra));
			if (!it.fix().isEmpty()) Text.wrapped(g, font, "→ " + it.fix(), tx, ty, textW, 4, Theme.fade(GOLD, ra));
			y += h + 5;
		}
		contentH = y + Math.round(scroll) - body.y();
		g.disableScissor();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll = (float) Math.max(0, Math.min(Math.max(0, contentH - body.h()), scroll - sy * 20));
		return true;
	}
}
