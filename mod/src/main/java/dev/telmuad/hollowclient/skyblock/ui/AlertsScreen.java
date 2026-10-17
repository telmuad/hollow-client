package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.gui.UiSounds;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.gui.widget.UiToggle;
import dev.telmuad.hollowclient.skyblock.alerts.AlertEngine;
import dev.telmuad.hollowclient.skyblock.alerts.AlertRule;
import dev.telmuad.hollowclient.skyblock.alerts.AlertStore;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alerts: every built-in alert with its switch, sound and toast, your own chat alerts, and a form to add one
 * (a regular expression to watch for in chat and the title to show; {1} or {name} put matched text in the title).
 */
public final class AlertsScreen extends SbScreen {
	private static final int ROW = 18;

	private final Map<String, UiToggle> toggles = new HashMap<>();
	private final UiButton add = new UiButton("Add alert", this::addAlert).style(UiButton.Style.PRIMARY);
	private final UiButton soundButton = new UiButton("Sound: Pling", this::cycleSound);
	private SkyBlockAlerts.Sound newSound = SkyBlockAlerts.Sound.PLING;
	private EditBox name, pattern, title;
	private Rect list = Rect.EMPTY;
	private Rect nameBox = Rect.EMPTY, patternBox = Rect.EMPTY, titleBox = Rect.EMPTY;
	private float scroll;
	private String status = "";
	private String confirmDelete;

	public AlertsScreen(Screen parent) {
		super("Alerts", parent);
	}

	@Override
	protected void init() {
		super.init();
		int formY = body.bottom() - 38;
		list = new Rect(body.x(), body.y(), body.w(), formY - body.y() - 8);
		int w3 = (body.w() - 16) / 3;
		nameBox = new Rect(body.x(), formY, w3 - 20, 16);
		patternBox = new Rect(nameBox.right() + 6, formY, w3 + 40, 16);
		titleBox = new Rect(patternBox.right() + 6, formY, body.right() - patternBox.right() - 6, 16);
		name = box(nameBox, "Name", 32);
		pattern = box(patternBox, "Chat regex, e.g. (\\w+) found", 200);
		title = box(titleBox, "Title, e.g. {1}!", 64);
		int y2 = formY + 20;
		soundButton.bounds(new Rect(body.x(), y2, soundButton.preferredWidth(font) + 10, 16));
		add.bounds(new Rect(body.right() - add.preferredWidth(font), y2, add.preferredWidth(font), 16));
	}

	private EditBox box(Rect r, String hint, int max) {
		EditBox b = new EditBox(font, r.x() + 5, r.y() + 4, r.w() - 8, 10, Component.literal(hint));
		b.setBordered(false);
		b.setMaxLength(max);
		b.setTextColor(Theme.TEXT);
		b.setHint(Ui.text(hint).withStyle(ChatFormatting.DARK_GRAY));
		addWidget(b);
		return b;
	}

	@Override
	protected String subtitle() {
		List<AlertRule> rules = SkyBlockAlerts.rules();
		long on = rules.stream().filter(AlertRule::enabled).count();
		return on + " of " + rules.size() + " alerts on · a title, a sound and an optional toast. Display only.";
	}

	@Override
	protected String footer() {
		return status.isEmpty() ? "Click a switch to turn an alert on or off · Sound cycles · Test shows it now" : status;
	}

	private Rect rowRect(int i) {
		return new Rect(list.x(), list.y() + i * ROW - Math.round(scroll), list.w(), ROW - 2);
	}

	private static String when(AlertRule r) {
		if (r.trigger() == AlertRule.Trigger.CHAT) return "chat: " + r.match();
		if (r.match().startsWith("pattern:")) return "chat pattern " + r.match().substring(8);
		return "event: " + r.match().replace('_', ' ');
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		drawFrame(g, mx, my, a);
		List<AlertRule> rules = SkyBlockAlerts.rules();
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		for (int i = 0; i < rules.size(); i++) {
			AlertRule r = rules.get(i);
			Rect row = rowRect(i);
			if (row.bottom() < list.y() || row.y() > list.bottom()) continue;
			float ra = a * appear(Math.min(i, 12));
			boolean hov = row.contains(mx, my) && list.contains(mx, my);
			Draw.roundRect(g, row, Theme.radius(3), Theme.fade(hov ? Theme.CARD_HOVER : Theme.CARD, ra));
			toggles.computeIfAbsent(r.id(), k -> new UiToggle()).render(g, row.x() + 4, row.y() + 3, r.enabled(), hov, dt, ra);
			int x = row.x() + 30;
			Ui.draw(g, font, Ui.fit(font, r.name(), 110), x, row.y() + 4, Theme.fade(Theme.TEXT, ra));
			if (r.builtin()) {
				Draw.roundRect(g, x + 114, row.y() + 3, 36, 10, 3, Theme.fade(0x30FFFFFF, ra));
				Ui.draw(g, font, "built-in", x + 116, row.y() + 4, Theme.fade(Theme.TEXT_FAINT, ra));
			}
			Rect[] b = buttons(row, r);
			drawSmall(g, b[0], "Test", mx, my, ra);
			drawSmall(g, b[1], r.toast() ? "Toast ✓" : "Toast ✗", mx, my, ra);
			drawSmall(g, b[2], SkyBlockAlerts.Sound.of(r.sound()).label, mx, my, ra);
			if (!r.builtin()) drawSmall(g, b[3], r.id().equals(confirmDelete) ? "Sure?" : "Delete", mx, my, ra);
			int whenRight = (r.builtin() ? b[2].x() : b[3].x()) - 6;
			String w = Ui.fit(font, when(r), whenRight - (x + 156));
			Ui.draw(g, font, w, x + 156, row.y() + 4, Theme.fade(Theme.TEXT_FAINT, ra));
		}
		g.disableScissor();
		for (Rect box : new Rect[]{nameBox, patternBox, titleBox}) {
			Draw.roundRect(g, box, Theme.radius(4), Theme.fade(Theme.CARD, a));
			Draw.roundOutline(g, box, Theme.radius(4), Theme.fade(Theme.STROKE, a));
		}
		if (a > 0.5f) {
			name.extractRenderState(g, mx, my, 0);
			pattern.extractRenderState(g, mx, my, 0);
			title.extractRenderState(g, mx, my, 0);
		}
		soundButton.setLabel("Sound: " + newSound.label);
		soundButton.render(g, font, mx, my, dt, a);
		add.render(g, font, mx, my, dt, a);
	}

	/** Test, toast, sound and (custom only) delete buttons, right to left. */
	private Rect[] buttons(Rect row, AlertRule r) {
		int right = row.right() - 4;
		Rect test = new Rect(right - 30, row.y() + 1, 30, 14);
		Rect toast = new Rect(test.x() - 52, row.y() + 1, 48, 14);
		Rect sound = new Rect(toast.x() - 58, row.y() + 1, 54, 14);
		Rect del = new Rect(sound.x() - 44, row.y() + 1, 40, 14);
		return new Rect[]{test, toast, sound, del};
	}

	private void drawSmall(GuiGraphicsExtractor g, Rect r, String text, int mx, int my, float a) {
		boolean h = r.contains(mx, my);
		Draw.roundRect(g, r, 3, Theme.fade(h ? Theme.accentSoft() : Theme.PANEL_RAISED, a));
		Ui.center(g, font, text, r.centerX(), r.y() + 3, Theme.fade(Theme.TEXT, a));
	}

	private void cycleSound() {
		newSound = newSound.next();
		if (minecraft != null) SkyBlockAlerts.play(minecraft, newSound);
	}

	private void addAlert() {
		AlertRule r = AlertStore.newCustom(name.getValue().trim(), pattern.getValue().trim(), title.getValue().trim(), newSound.name(), false);
		String problem = r.problem();
		if (problem != null) {
			status = problem;
			return;
		}
		SkyBlockAlerts.update(r);
		name.setValue("");
		pattern.setValue("");
		title.setValue("");
		status = "Added \"" + r.name() + "\". It watches every chat line from the server (never other players' messages).";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		double mx = event.x(), my = event.y();
		if (event.button() == 0) {
			if (add.click(mx, my) || soundButton.click(mx, my)) return true;
			for (EditBox b : new EditBox[]{name, pattern, title}) b.setFocused(false);
			for (var pair : List.of(Map.entry(nameBox, name), Map.entry(patternBox, pattern), Map.entry(titleBox, title))) {
				if (pair.getKey().contains(mx, my)) {
					pair.getValue().setFocused(true);
					setFocused(pair.getValue());
					return super.mouseClicked(event, doubleClick);
				}
			}
			if (list.contains(mx, my)) {
				List<AlertRule> rules = SkyBlockAlerts.rules();
				for (int i = 0; i < rules.size(); i++) {
					Rect row = rowRect(i);
					if (!row.contains(mx, my)) continue;
					AlertRule r = rules.get(i);
					Rect[] b = buttons(row, r);
					UiSounds.click();
					if (b[0].contains(mx, my)) {
						SkyBlockAlerts.show(new AlertEngine.Fired(r, r.title().replaceAll("\\{[^}]*}", "").strip(), "test"));
					} else if (b[1].contains(mx, my)) {
						SkyBlockAlerts.update(r.withToast(!r.toast()));
					} else if (b[2].contains(mx, my)) {
						SkyBlockAlerts.Sound next = SkyBlockAlerts.Sound.of(r.sound()).next();
						SkyBlockAlerts.update(r.withSound(next.name()));
						if (minecraft != null) SkyBlockAlerts.play(minecraft, next);
					} else if (!r.builtin() && b[3].contains(mx, my)) {
						if (r.id().equals(confirmDelete)) {
							SkyBlockAlerts.remove(r.id());
							confirmDelete = null;
							status = "Deleted \"" + r.name() + "\".";
						} else {
							confirmDelete = r.id();
						}
					} else {
						SkyBlockAlerts.update(r.withEnabled(!r.enabled()));
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int max = Math.max(0, SkyBlockAlerts.rules().size() * ROW - list.h());
		scroll = (float) Math.max(0, Math.min(max, scroll - sy * 18));
		return true;
	}
}
