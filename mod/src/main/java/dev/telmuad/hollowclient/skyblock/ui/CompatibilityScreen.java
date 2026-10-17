package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Text;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.module.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compatibility: if Skyblocker, SkyHanni or Firmament is installed, the Hollow features they already have are listed
 * and can be switched off in one click, so you don't get two of the same HUD. Nothing about the other mods is changed.
 */
public final class CompatibilityScreen extends SbScreen {
	private final List<String> installed = new ArrayList<>();
	private final List<UiButton> buttons = new ArrayList<>();
	private Rect[] cards = new Rect[0];
	private String status = "";

	public CompatibilityScreen(Screen parent) {
		super("Compatibility", parent);
		for (Map.Entry<String, String> e : SetupCheck.OTHER_MODS.entrySet()) {
			if (FabricLoader.getInstance().isModLoaded(e.getKey())) installed.add(e.getKey());
		}
		installed.sort(String::compareTo);
		for (String id : installed) buttons.add(new UiButton("Switch off Hollow's overlapping features", () -> switchOff(id)).style(UiButton.Style.PRIMARY));
	}

	/** Hollow SkyBlock features that a mod already has and that are switched on. */
	public static List<Module> overlapping(String modId) {
		List<Module> out = new ArrayList<>();
		for (Module m : FeatureHealthScreen.features()) {
			if (m.isEnabled() && ((SkyBlockFeature) m).overlaps().contains(modId)) out.add(m);
		}
		return out;
	}

	private void switchOff(String id) {
		List<Module> list = overlapping(id);
		for (Module m : list) m.setEnabled(false);
		status = list.isEmpty() ? "Nothing to switch off." : "Switched off " + list.size() + " Hollow feature" + (list.size() == 1 ? "" : "s")
				+ " that " + SetupCheck.OTHER_MODS.get(id) + " has too. Switch them back on in the menu any time.";
		Toasts.success("Compatibility", status);
	}

	@Override
	protected void init() {
		super.init();
		cards = new Rect[installed.size()];
		int h = installed.isEmpty() ? 0 : Math.min(110, (body.h() - 8 * (installed.size() - 1)) / installed.size());
		for (int i = 0; i < installed.size(); i++) {
			cards[i] = new Rect(body.x(), body.y() + i * (h + 8), body.w(), h);
			UiButton b = buttons.get(i);
			int bw = b.preferredWidth(font) + 8;
			b.bounds(new Rect(cards[i].right() - 8 - bw, cards[i].bottom() - 22, bw, 16));
		}
	}

	@Override
	protected String subtitle() {
		if (installed.isEmpty()) return "No other SkyBlock mods installed";
		List<String> names = new ArrayList<>();
		for (String id : installed) names.add(SetupCheck.OTHER_MODS.get(id));
		return "Installed: " + String.join(", ", names);
	}

	@Override
	protected String footer() {
		return status.isEmpty() ? "Hollow never changes the other mods; it only switches off its own copies of their features." : status;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		drawFrame(g, mx, my, a);
		if (installed.isEmpty()) {
			Text.wrapped(g, font, "Skyblocker, SkyHanni and Firmament aren't installed, so nothing overlaps. If you add one later, this screen lists the Hollow features it already has.",
					body.x() + 4, body.y() + 6, body.w() - 8, 4, Theme.fade(Theme.TEXT_DIM, a));
			return;
		}
		for (int i = 0; i < installed.size(); i++) {
			String id = installed.get(i);
			Rect r = cards[i];
			float ca = card(g, r, SetupCheck.OTHER_MODS.get(id), i, a);
			if (ca <= 0) continue;
			List<Module> list = overlapping(id);
			String text = list.isEmpty() ? "None of your switched-on Hollow features overlap with it."
					: "It already has: " + String.join(", ", list.stream().map(Module::name).toList()) + ".";
			Text.wrapped(g, font, text, r.x() + 8, r.y() + 22, r.w() - 16, 4, Theme.fade(Theme.TEXT_DIM, ca));
			UiButton b = buttons.get(i);
			b.setEnabled(!list.isEmpty());
			b.render(g, font, mx, my, dt, ca);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		if (event.button() == 0) for (UiButton b : buttons) if (b.click(event.x(), event.y())) return true;
		return super.mouseClicked(event, doubleClick);
	}
}
