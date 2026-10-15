package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.gui.widget.ColorPicker;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.gui.widget.UiSlider;
import dev.telmuad.hollowclient.gui.widget.UiToggle;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.theme.HollowTheme;
import dev.telmuad.hollowclient.theme.ThemeStore;
import dev.telmuad.hollowclient.theme.Themes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Theme editor: pick one of the presets or your custom themes, change colors, glow, blur, rounding and animation
 * speed live, and save the result as a custom theme. The launcher uses the same theme (shared theme.json).
 */
public final class ThemeScreen extends HollowScreen {
	private static final int ROW = 22;

	private record Hit(Rect rect, Runnable action) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private final List<Hit> frameHits = new ArrayList<>();
	private final UiButton back = new UiButton("< Back", this::onClose);
	private final UiToggle rainbowToggle = new UiToggle();
	private final UiButton save = new UiButton("Save", this::saveCustom).style(UiButton.Style.PRIMARY);
	private final UiSlider glow = new UiSlider(() -> theme().glow(), v -> edit(t -> t.withGlow((float) v)));
	private final UiSlider blur = new UiSlider(() -> theme().blur(), v -> edit(t -> t.withBlur((float) v)));
	private final UiSlider round = new UiSlider(() -> theme().roundness() / 8.0, v -> edit(t -> t.withRoundness((int) Math.round(v * 8))));
	private final UiSlider speed = new UiSlider(() -> (theme().animationSpeed() - 0.25) / 2.75, v -> edit(t -> t.withAnimationSpeed((float) (0.25 + v * 2.75))));
	private UiSlider active;
	private Rect panel = Rect.EMPTY;
	private Rect left = Rect.EMPTY;
	private Rect right = Rect.EMPTY;
	private Rect list = Rect.EMPTY;
	private Rect saveRow = Rect.EMPTY;
	private int row = ROW;
	private float listScroll;
	private float listScrollTarget;
	private EditBox name;
	private ColorPicker picker;
	private boolean dirty;
	private String status;
	private boolean statusError;
	private long statusAt;
	private String confirmDelete;

	public ThemeScreen(Screen parent) {
		super(Component.literal("Theme"), parent);
	}

	private static ThemeStore store() {
		return Themes.store();
	}

	private static HollowTheme theme() {
		return Themes.current();
	}

	/** Change the current theme without writing the file on every slider step (saved when you let go or leave). */
	private void edit(java.util.function.UnaryOperator<HollowTheme> change) {
		HollowTheme next = change.apply(theme());
		if (next.equals(theme())) return;
		store().setCurrent(next, false);
		dirty = true;
	}

	private void persist() {
		if (dirty) {
			store().save();
			dirty = false;
		}
	}

	@Override
	protected void init() {
		super.init();
		int pw = Math.min(width - 24, 600);
		int ph = Math.min(height - 16, 340);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		int top = panel.y() + 35;
		int bottom = panel.bottom() - 20;
		int split = Math.max(150, Math.min(pw * 2 / 5, 230));
		left = new Rect(panel.x() + 10, top, split - 16, bottom - top);
		right = new Rect(panel.x() + split + 6, top, pw - split - 16, bottom - top);
		list = new Rect(left.x(), left.y() + 13, left.w(), left.h() - 13 - 22);
		saveRow = new Rect(left.x(), left.bottom() - 16, left.w(), 16);
		row = Math.max(16, Math.min(ROW, (right.h() - 13) / 8));
		name = new EditBox(font, 0, 0, 100, 12, Component.literal("Theme name"));
		name.setBordered(false);
		name.setMaxLength(HollowTheme.MAX_NAME);
		name.setTextColor(Theme.TEXT);
		name.setHint(Ui.text("Name your theme").withStyle(ChatFormatting.DARK_GRAY));
		name.setValue(theme().isPreset() ? "" : theme().name());
		addWidget(name);
	}

	private void hit(Rect r, Runnable action) {
		frameHits.add(new Hit(r, action));
	}

	/** A hit area cut to the visible part of the scrolling theme list. */
	private void listHit(Rect r, Runnable action) {
		int y0 = Math.max(r.y(), list.y());
		int y1 = Math.min(r.bottom(), list.bottom());
		if (y1 > y0) hit(new Rect(r.x(), y0, r.w(), y1 - y0), action);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float a) {
		frameHits.clear();
		int mx = picker != null ? -10000 : mouseX;
		int my = picker != null ? -10000 : mouseY;
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		hit(back.bounds(), () -> back.click(lastX, lastY));
		int s = 14;
		Fx.sigil(g, back.bounds().right() + 8, panel.y() + 9, s, a, true);
		Ui.draw(g, font, Ui.bold("Theme"), back.bounds().right() + 12 + s, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		String note = "Shared with the launcher";
		Ui.draw(g, font, note, panel.right() - 10 - Ui.width(font, note), panel.y() + 12, Theme.fade(Theme.TEXT_FAINT, a));
		g.fill(panel.x() + 8, panel.y() + 29, panel.right() - 8, panel.y() + 30, Theme.fade(Theme.STROKE, a));

		renderThemes(g, mx, my, a);
		renderSaveRow(g, mx, my, a);
		renderEditor(g, mx, my, a);
		renderFooter(g, a);
		hits.clear();
		hits.addAll(frameHits);
	}

	private static final int CARD_H = 18;
	private static final int CARD_GAP = 3;

	private float maxListScroll() {
		return Math.max(0, store().all().size() * (CARD_H + CARD_GAP) - CARD_GAP - list.h());
	}

	private void renderThemes(GuiGraphics g, int mx, int my, float a) {
		Ui.draw(g, font, Ui.bold("Presets & saved"), left.x(), left.y(), Theme.fade(Theme.TEXT_DIM, a));
		List<HollowTheme> all = store().all();
		listScrollTarget = Math.max(0, Math.min(maxListScroll(), listScrollTarget));
		listScroll = Anim.approach(listScroll, listScrollTarget, dt, 18f);
		long now = net.minecraft.util.Util.getMillis();
		boolean inList = list.contains(mx, my);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		for (int i = 0; i < all.size(); i++) {
			HollowTheme t = all.get(i);
			Rect r = new Rect(list.x(), list.y() + i * (CARD_H + CARD_GAP) - Math.round(listScroll), list.w() - 4, CARD_H);
			if (r.bottom() < list.y() || r.y() > list.bottom()) continue;
			boolean selected = t.name().equalsIgnoreCase(theme().name());
			boolean hover = inList && r.contains(mx, my);
			int rad = Theme.radius(4);
			Draw.roundRect(g, r, rad, Theme.fade(selected ? Theme.cardOn() : (hover ? Theme.CARD_HOVER : Theme.CARD), a));
			if (selected) Draw.roundOutline(g, r, rad, Theme.fade(t.accentAt(now), a));
			// gradient swatch
			int bx = r.x() + 5;
			int bw = 14;
			for (int k = 0; k < bw; k++) {
				g.fill(bx + k, r.y() + 5, bx + k + 1, r.bottom() - 5, Theme.fade(t.gradientAt(k / (float) (bw - 1), now), a));
			}
			boolean custom = HollowTheme.preset(t.name()) == null;
			int textRoom = r.w() - 26 - (custom ? 14 : 4);
			Ui.draw(g, font, Text.fit(font, t.name(), textRoom), r.x() + 24, r.y() + 5, Theme.fade(selected ? Theme.TEXT : Theme.TEXT_DIM, a));
			listHit(r, () -> {
				UiSounds.click();
				store().setCurrent(t);
				name.setValue(custom ? t.name() : "");
				status("Theme: " + t.name(), false);
			});
			if (custom) {
				Rect x = new Rect(r.right() - 13, r.y() + 4, 10, 10);
				boolean asking = t.name().equalsIgnoreCase(confirmDelete);
				boolean xh = inList && x.contains(mx, my);
				Draw.roundRect(g, x, 2, Theme.fade(asking ? Theme.DANGER : (xh ? 0xA0000000 : 0x50000000), a));
				Ui.center(g, font, asking ? "?" : "x", x.centerX() + 1, x.y() + 1, Theme.fade(0xFFFFFFFF, a));
				listHit(x, () -> {
					UiSounds.click();
					if (asking) {
						store().deleteCustom(t.name());
						confirmDelete = null;
						status("Deleted \"" + t.name() + "\".", false);
					} else {
						confirmDelete = t.name();
						status("Click the ? again to delete \"" + t.name() + "\".", true);
					}
				});
			}
		}
		g.disableScissor();
		float max = maxListScroll();
		if (max > 0) {
			// thin scrollbar
			int trackH = list.h();
			int thumbH = Math.max(12, Math.round(trackH * list.h() / (list.h() + max)));
			int thumbY = list.y() + Math.round((trackH - thumbH) * (listScroll / max));
			g.fill(list.right() - 2, thumbY, list.right(), thumbY + thumbH, Theme.fade(Theme.STROKE_STRONG, a));
		}
	}

	private void renderSaveRow(GuiGraphics g, int mx, int my, float a) {
		int bw = save.preferredWidth(font);
		Rect box = new Rect(saveRow.x(), saveRow.y(), saveRow.w() - bw - 4, saveRow.h());
		boolean focused = name.isFocused();
		Draw.roundRect(g, box, Theme.radius(3), Theme.fade(focused ? Theme.CARD_HOVER : Theme.CARD, a));
		Draw.roundOutline(g, box, Theme.radius(3), Theme.fade(focused ? Theme.accent() : Theme.STROKE, a));
		name.setX(box.x() + 5);
		name.setY(box.y() + 4);
		name.setWidth(box.w() - 10);
		if (a > 0.5f) name.render(g, mx, my, 0);
		Rect sr = new Rect(box.right() + 4, saveRow.y(), bw, saveRow.h());
		save.bounds(sr).render(g, font, mx, my, dt, a);
		hit(sr, () -> save.click(lastX, lastY));
	}

	private void renderEditor(GuiGraphics g, int mx, int my, float a) {
		HollowTheme t = theme();
		int x = right.x();
		int y = right.y();
		int w = right.w();
		Ui.draw(g, font, Ui.bold("Edit"), x, y, Theme.fade(Theme.TEXT_DIM, a));
		y += 13;
		y = colorRow(g, x, y, w, "Accent", t.accent(), c -> edit(th -> th.withAccent(c)), mx, my, a);
		y = colorRow(g, x, y, w, "Gradient start", t.gradientStart(), c -> edit(th -> th.withGradient(c, th.gradientEnd())), mx, my, a);
		y = colorRow(g, x, y, w, "Gradient end", t.gradientEnd(), c -> edit(th -> th.withGradient(th.gradientStart(), c)), mx, my, a);
		y = sliderRow(g, x, y, w, "Glow", Math.round(t.glow() * 100) + "%", glow, mx, my, a);
		y = sliderRow(g, x, y, w, "Background blur", Math.round(t.blur() * 100) + "%", blur, mx, my, a);
		y = sliderRow(g, x, y, w, "Corner roundness", t.roundness() + " px", round, mx, my, a);
		y = sliderRow(g, x, y, w, "Animation speed", String.format(java.util.Locale.ROOT, "%.2fx", t.animationSpeed()), speed, mx, my, a);
		Ui.draw(g, font, "Rainbow cycle", x, y + (row - 8) / 2, Theme.fade(Theme.TEXT, a));
		int tx = x + w - UiToggle.W;
		int ty = y + (row - UiToggle.H) / 2;
		rainbowToggle.render(g, tx, ty, t.rainbow(), UiToggle.hit(tx, ty, mx, my), dt, a);
		hit(new Rect(tx - 2, ty - 2, UiToggle.W + 4, UiToggle.H + 4), () -> {
			UiSounds.toggle(!theme().rainbow());
			edit(th -> th.withRainbow(!th.rainbow()));
			persist();
		});
		y += row + 4;

		// Live preview, when there is room: a glowing card with the sigil and gradient text
		Rect prev = new Rect(x, y, w, Math.min(40, right.bottom() - y));
		if (prev.h() >= 28) {
			Fx.glow(g, prev.x(), prev.y(), prev.w(), prev.h(), 8, Theme.fade(Theme.accent(), a * t.glow() * 0.5f));
			Draw.roundRect(g, prev, Theme.radius(6), Theme.fade(Theme.CARD, a));
			Fx.gradientOutline(g, prev, Theme.radius(6), a, 0.25f);
			int s = Math.min(prev.h() - 8, 26);
			Fx.sigil(g, prev.x() + 6, prev.y() + (prev.h() - s) / 2, s, a, true);
			Fx.gradientText(g, font, "Hollow Client", prev.x() + 12 + s, prev.y() + prev.h() / 2 - 4, a, false, false);
		}
	}

	private int colorRow(GuiGraphics g, int x, int y, int w, String label, int color, Consumer<Integer> set, int mx, int my, float a) {
		Ui.draw(g, font, label, x, y + (row - 8) / 2, Theme.fade(Theme.TEXT, a));
		Rect sw = new Rect(x + w - 30, y + (row - 12) / 2, 30, 12);
		boolean hover = sw.contains(mx, my);
		Draw.roundRect(g, sw, Theme.radius(3), Theme.fade(color, a));
		Draw.roundOutline(g, sw, Theme.radius(3), Theme.fade(hover ? 0xFFFFFFFF : Theme.STROKE_STRONG, a));
		hit(sw, () -> {
			UiSounds.click();
			ColorSetting setting = new ColorSetting("c", label, color, false);
			setting.setListener(st -> set.accept(setting.get() | 0xFF000000));
			picker = new ColorPicker(setting);
			picker.layout(width, height, sw.right(), sw.bottom() + 2);
		});
		return y + row;
	}

	private int sliderRow(GuiGraphics g, int x, int y, int w, String label, String value, UiSlider slider, int mx, int my, float a) {
		Ui.draw(g, font, label, x, y + (row - 8) / 2, Theme.fade(Theme.TEXT, a));
		int sw = Math.max(50, Math.min(90, w - Ui.width(font, label) - 44));
		Rect sr = new Rect(x + w - sw, y + (row - 10) / 2, sw, 10);
		slider.bounds(sr).render(g, mx, my, dt, a);
		Ui.draw(g, font, value, sr.x() - 6 - Ui.width(font, value), y + (row - 8) / 2, Theme.fade(Theme.TEXT_DIM, a));
		hit(sr.inset(-3), () -> {
			slider.click(lastX, lastY);
			active = slider;
		});
		return y + row;
	}

	private double lastX;
	private double lastY;

	private void saveCustom() {
		String n = name.getValue().trim();
		if (n.isEmpty()) {
			status("Type a name first.", true);
			return;
		}
		HollowTheme saved = store().saveCustom(theme().withName(n));
		dirty = false;
		name.setValue(saved.name());
		status("Saved \"" + saved.name() + "\".", false);
		Toasts.success("Theme saved", saved.name() + " is also used by the launcher.");
	}

	private void status(String s, boolean error) {
		status = s;
		statusError = error;
		statusAt = net.minecraft.util.Util.getMillis();
	}

	private void renderFooter(GuiGraphics g, float a) {
		long age = net.minecraft.util.Util.getMillis() - statusAt;
		String text = status != null && age < 5000 ? status : "Tip: presets can be tweaked, then saved under your own name.";
		int color = status != null && age < 5000 ? (statusError ? Theme.DANGER : Theme.SUCCESS) : Theme.TEXT_FAINT;
		Ui.draw(g, font, Text.fit(font, text, panel.w() - 20), panel.x() + 10, panel.bottom() - 14, Theme.fade(color, a));
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
		if (picker != null) {
			g.nextStratum();
			picker.render(g, font, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		lastX = event.x();
		lastY = event.y();
		if (closing()) return true;
		if (picker != null) {
			if (!picker.mouseClicked(event.x(), event.y())) {
				picker = null;
				persist();
			}
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				if (hits.get(i).rect.contains(event.x(), event.y())) {
					String before = confirmDelete;
					hits.get(i).action.run();
					if (before != null && before.equals(confirmDelete)) confirmDelete = null;
					return true;
				}
			}
			confirmDelete = null;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (picker == null && left.contains(mx, my)) {
			listScrollTarget = Math.max(0, Math.min(maxListScroll(), listScrollTarget - (float) sy * 21));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (picker != null) return picker.mouseDragged(event.x(), event.y());
		if (active != null && active.drag(event.x())) return true;
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (picker != null) return picker.mouseReleased();
		if (active != null) {
			active.release();
			active = null;
			persist();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (picker != null) {
			if (event.isEscape()) {
				picker = null;
				persist();
			} else {
				picker.keyPressed(event.key());
			}
			return true;
		}
		if (name.isFocused() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			saveCustom();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (picker != null) {
			picker.charTyped(event.codepoint());
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void removed() {
		persist();
		super.removed();
	}
}
