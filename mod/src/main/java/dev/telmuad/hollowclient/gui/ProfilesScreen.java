package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.config.Profiles;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Profiles: three built-in setups as cards (PvP, Building, Cinematic), your own saved setups below, and "Back to
 * my settings" to undo.
 */
public final class ProfilesScreen extends HollowScreen {
	private static final int ROW = 18;

	private final UiButton back = new UiButton("< Back", this::onClose);
	private final UiButton undo = new UiButton("Back to my settings", this::goBack);
	private final UiButton save = new UiButton("Save current", this::saveCurrent).style(UiButton.Style.PRIMARY);
	private final List<UiButton> use = new ArrayList<>();
	private final float[] hover = new float[Profiles.BUILTIN.size()];
	private Rect panel = Rect.EMPTY;
	private Rect[] cards = new Rect[0];
	private Rect savedList = Rect.EMPTY;
	private Rect nameBox = Rect.EMPTY;
	private EditBox name;
	private List<String> saved = List.of();
	private String confirmDelete;
	private String status;

	public ProfilesScreen(Screen parent) {
		super(Component.literal("Profiles"), parent);
		for (Profiles.Builtin p : Profiles.BUILTIN) {
			use.add(new UiButton("Use", () -> applyBuiltin(p)).style(UiButton.Style.PRIMARY));
		}
	}

	@Override
	protected void init() {
		super.init();
		saved = Profiles.saved();
		int pw = Math.min(width - 24, 600);
		int ph = Math.min(height - 24, 340);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		int uw = undo.preferredWidth(font);
		undo.bounds(new Rect(panel.right() - 8 - uw, panel.y() + 8, uw, 16));
		int n = Profiles.BUILTIN.size();
		int gap = 8;
		int cw = (pw - 16 - gap * (n - 1)) / n;
		int ch = Math.min(118, Math.max(92, ph / 2 - 30));
		cards = new Rect[n];
		for (int i = 0; i < n; i++) {
			cards[i] = new Rect(panel.x() + 8 + i * (cw + gap), panel.y() + 32, cw, ch);
			use.get(i).bounds(new Rect(cards[i].right() - 8 - 40, cards[i].bottom() - 22, 40, 16));
		}
		int listTop = panel.y() + 32 + ch + 26;
		int sw = save.preferredWidth(font);
		nameBox = new Rect(panel.x() + 8, listTop - 20 + 0, Math.min(200, pw / 2), 16);
		save.bounds(new Rect(nameBox.right() + 6, nameBox.y(), sw, 16));
		savedList = new Rect(panel.x() + 8, listTop, pw - 16, panel.bottom() - 20 - listTop);
		name = new EditBox(font, nameBox.x() + 5, nameBox.y() + 4, nameBox.w() - 8, 10, Component.literal("Profile name"));
		name.setBordered(false);
		name.setMaxLength(32);
		name.setTextColor(Theme.TEXT);
		name.setHint(Ui.text("Name for your setup").withStyle(ChatFormatting.DARK_GRAY));
		addWidget(name);
	}

	private void applyBuiltin(Profiles.Builtin p) {
		int missing = Profiles.apply(HollowClient.modules(), p);
		status = missing == 0 ? p.name() + " is set up. \"Back to my settings\" undoes it." : p.name() + " applied (" + missing + " items skipped).";
		Toasts.success("Profile: " + p.name(), "Applied. You can undo it here.");
	}

	private void goBack() {
		if (!Profiles.canGoBack()) return;
		Profiles.goBack(HollowClient.modules());
		status = "Your settings are back.";
		Toasts.info("Profiles", "Your settings are back.");
	}

	private void saveCurrent() {
		String n = name.getValue().trim();
		if (n.isEmpty()) {
			status = "Type a name first.";
			name.setFocused(true);
			setFocused(name);
			return;
		}
		try {
			Profiles.save(HollowClient.modules(), n);
			saved = Profiles.saved();
			name.setValue("");
			status = "Saved \"" + n + "\".";
		} catch (IOException e) {
			status = "Couldn't save: " + e.getMessage();
		}
	}

	private Rect rowRect(int i) {
		return new Rect(savedList.x(), savedList.y() + i * ROW, savedList.w(), ROW - 2);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mx, int my, float a) {
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		undo.setEnabled(Profiles.canGoBack());
		undo.render(g, font, mx, my, dt, a);
		String active = Profiles.active();
		String head = active == null ? "Profiles" : "Profiles · using " + active;
		Ui.draw(g, font, Ui.bold(head), back.bounds().right() + 8, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		for (int i = 0; i < cards.length; i++) {
			Profiles.Builtin p = Profiles.BUILTIN.get(i);
			Rect r = cards[i];
			boolean hov = r.contains(mx, my);
			hover[i] = Anim.approach(hover[i], hov ? 1f : 0f, dt, 14f);
			hoverGlow(g, r, hover[i], a);
			boolean isActive = p.name().equals(active);
			Draw.roundRect(g, r, Theme.radius(6), Theme.fade(Theme.mix(Theme.CARD, Theme.CARD_HOVER, hover[i]), a));
			Draw.roundOutline(g, r, Theme.radius(6), Theme.fade(isActive ? Theme.accent() : Theme.mix(Theme.STROKE, Theme.accent(), hover[i] * 0.6f), a));
			Fx.gradientBold(g, font, p.name(), r.x() + 8, r.y() + 8, a);
			if (isActive) {
				String tag = "In use";
				int tw = Ui.width(font, tag) + 8;
				Draw.roundRect(g, r.right() - 8 - tw, r.y() + 6, tw, 12, 6, Theme.fade(Theme.accentSoft(), a));
				Ui.draw(g, font, tag, r.right() - 4 - tw, r.y() + 8, Theme.fade(Theme.TEXT, a));
			}
			int y = r.y() + 22;
			for (FormattedCharSequence line : font.split(Ui.text(p.description()), r.w() - 16)) {
				if (y > r.bottom() - 34) break;
				g.drawString(font, line, r.x() + 8, y, Theme.fade(Theme.TEXT_DIM, a), false);
				y += 10;
			}
			int shown = Profiles.hudShown(p, HollowClient.modules()).size();
			String hudLine = shown == 0 ? "No HUD" : "HUD: " + shown;
			Ui.draw(g, font, Ui.fit(font, hudLine, r.w() - 64), r.x() + 8, r.bottom() - 18, Theme.fade(Theme.TEXT_FAINT, a));
			use.get(i).render(g, font, mx, my, dt, a);
		}
		// your own profiles
		boolean focused = name.isFocused();
		Draw.roundRect(g, nameBox, Theme.radius(4), Theme.fade(focused ? Theme.CARD_HOVER : Theme.CARD, a));
		Draw.roundOutline(g, nameBox, Theme.radius(4), Theme.fade(focused ? Theme.accent() : Theme.STROKE, a));
		if (a > 0.5f) name.render(g, mx, my, 0);
		save.render(g, font, mx, my, dt, a);
		Ui.draw(g, font, "Your profiles", save.bounds().right() + 10, nameBox.y() + 4, Theme.fade(Theme.TEXT_FAINT, a));
		if (saved.isEmpty()) {
			Ui.draw(g, font, "None yet. Set things up the way you like, name it and press \"Save current\".", savedList.x() + 4, savedList.y() + 6,
					Theme.fade(Theme.TEXT_FAINT, a));
		}
		g.enableScissor(savedList.x(), savedList.y(), savedList.right(), savedList.bottom());
		for (int i = 0; i < saved.size(); i++) {
			Rect r = rowRect(i);
			if (r.y() > savedList.bottom()) break;
			String s = saved.get(i);
			boolean hov = r.contains(mx, my);
			Draw.roundRect(g, r, Theme.radius(3), Theme.fade(hov ? Theme.CARD_HOVER : Theme.CARD, a));
			if (s.equals(active)) Draw.roundOutline(g, r, Theme.radius(3), Theme.fade(Theme.accent(), a));
			Ui.draw(g, font, s, r.x() + 8, r.y() + 4, Theme.fade(Theme.TEXT, a));
			Rect apply = new Rect(r.right() - 110, r.y() + 1, 50, 14);
			Rect del = new Rect(r.right() - 56, r.y() + 1, 52, 14);
			Draw.roundRect(g, apply, 3, Theme.fade(apply.contains(mx, my) ? Theme.accent() : Theme.accentSoft(), a));
			Ui.center(g, font, "Apply", apply.centerX(), apply.y() + 3, Theme.fade(Theme.TEXT, a));
			boolean confirm = s.equals(confirmDelete);
			Draw.roundRect(g, del, 3, Theme.fade(del.contains(mx, my) || confirm ? 0xFF7F1D1D : Theme.PANEL_RAISED, a));
			Ui.center(g, font, confirm ? "Sure?" : "Delete", del.centerX(), del.y() + 3, Theme.fade(Theme.TEXT, a));
		}
		g.disableScissor();
		String foot = status != null ? status : "Built-in profiles only change what they mention. Your own profiles save every module.";
		Ui.draw(g, font, Ui.fit(font, foot, panel.w() - 20), panel.x() + 10, panel.bottom() - 14, Theme.fade(Theme.TEXT_FAINT, a));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		double mx = event.x();
		double my = event.y();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
		if (back.click(mx, my) || undo.click(mx, my) || save.click(mx, my)) return true;
		for (UiButton b : use) if (b.click(mx, my)) return true;
		if (nameBox.contains(mx, my)) {
			name.setFocused(true);
			setFocused(name);
			return super.mouseClicked(event, doubleClick);
		}
		name.setFocused(false);
		for (int i = 0; i < saved.size(); i++) {
			Rect r = rowRect(i);
			if (!r.contains(mx, my)) continue;
			String s = saved.get(i);
			Rect apply = new Rect(r.right() - 110, r.y() + 1, 50, 14);
			Rect del = new Rect(r.right() - 56, r.y() + 1, 52, 14);
			if (apply.contains(mx, my)) {
				UiSounds.click();
				boolean ok = Profiles.applySaved(HollowClient.modules(), s);
				status = ok ? "Applied \"" + s + "\"." : "Couldn't read \"" + s + "\".";
				confirmDelete = null;
			} else if (del.contains(mx, my)) {
				UiSounds.click();
				if (s.equals(confirmDelete)) {
					try {
						Profiles.delete(s);
						saved = Profiles.saved();
						status = "Deleted \"" + s + "\".";
					} catch (IOException e) {
						status = "Couldn't delete: " + e.getMessage();
					}
					confirmDelete = null;
				} else {
					confirmDelete = s;
				}
			}
			return true;
		}
		confirmDelete = null;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (name.isFocused() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			saveCurrent();
			return true;
		}
		return super.keyPressed(event);
	}
}
