package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Feature health: every SkyBlock feature, whether it's on, and ✓/✗ for whether its data is being detected right now. */
public final class FeatureHealthScreen extends SbScreen {
	private static final int ROW = 16;
	private float scroll;

	public FeatureHealthScreen(Screen parent) {
		super("Feature health", parent);
	}

	static List<Module> features() {
		List<Module> out = new ArrayList<>();
		for (Module m : HollowClient.modules().all()) if (m instanceof SkyBlockFeature) out.add(m);
		return out;
	}

	@Override
	protected String subtitle() {
		List<Module> f = features();
		long working = f.stream().filter(m -> m.isEnabled() && ((SkyBlockFeature) m).health().ok()).count();
		long on = f.stream().filter(Module::isEnabled).count();
		return working + " of " + on + " switched-on features have their data right now (" + f.size() + " features in all)";
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		drawFrame(g, mx, my, a);
		g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
		String lastGroup = null;
		int y = body.y() - Math.round(scroll);
		int i = 0;
		for (Module m : features()) {
			SkyBlockFeature f = (SkyBlockFeature) m;
			if (!f.group().equals(lastGroup)) {
				lastGroup = f.group();
				Ui.draw(g, font, Ui.bold(lastGroup.isEmpty() ? "Other" : lastGroup), body.x() + 2, y + 4, Theme.fade(Theme.accent(), a));
				y += ROW;
			}
			float ra = a * appear(Math.min(i++, 14));
			Rect r = new Rect(body.x(), y, body.w(), ROW - 2);
			Draw.roundRect(g, r, Theme.radius(3), Theme.fade(Theme.CARD, ra));
			SkyBlockFeature.Health h = f.health();
			boolean on = m.isEnabled();
			if (on) mark(g, r.x() + 4, r.y() + 2, h.ok(), ra);
			else Ui.draw(g, font, "off", r.x() + 3, r.y() + 3, Theme.fade(Theme.TEXT_FAINT, ra));
			Ui.draw(g, font, Ui.fit(font, m.name(), 150), r.x() + 22, r.y() + 3, Theme.fade(on ? Theme.TEXT : Theme.TEXT_FAINT, ra));
			String text = on ? h.text() : "Switched off";
			Ui.draw(g, font, Ui.fit(font, text, r.w() - 190), r.x() + 180, r.y() + 3,
					Theme.fade(!on ? Theme.TEXT_FAINT : h.ok() ? Theme.TEXT_DIM : BAD, ra));
			y += ROW;
		}
		g.disableScissor();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int content = (features().size() + 14) * ROW;
		scroll = (float) Math.max(0, Math.min(Math.max(0, content - body.h()), scroll - sy * 18));
		return true;
	}
}
