package dev.telmuad.hollowclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.telmuad.hollowclient.gui.widget.ColorPicker;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.gui.widget.UiSlider;
import dev.telmuad.hollowclient.gui.widget.UiToggle;
import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.setting.Setting;
import dev.telmuad.hollowclient.setting.StringSetting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Settings for one module: its keybind, then one row per setting (toggle, slider, choice, color). */
public class ModuleSettingsScreen extends HollowScreen {
	private static final int ROW_H = 22;

	private final Module module;
	private final List<Row> rows = new ArrayList<>();
	private final Map<Setting<?>, UiSlider> sliders = new HashMap<>();
	private final Map<Object, UiToggle> toggles = new HashMap<>();
	private final UiToggle enabledToggle = new UiToggle();
	private final UiButton back = new UiButton("< Back", this::onClose);
	private final UiButton reset = new UiButton("Reset to defaults", this::resetModule).style(UiButton.Style.DANGER);
	private final List<UiButton> extraButtons = new ArrayList<>();
	private Rect panel = Rect.EMPTY;
	private Rect list = Rect.EMPTY;
	private float scroll;
	private float scrollTarget;
	private ColorPicker picker;
	private boolean listening;
	private boolean confirmReset;

	private record Row(String label, String tooltip, Object target) {
	}

	public ModuleSettingsScreen(Module module, Screen parent) {
		super(Component.literal(module.name()), parent);
		this.module = module;
	}

	/** Extra buttons in the footer (for example "Move on screen" for HUD modules). */
	public ModuleSettingsScreen withButton(String label, Consumer<ModuleSettingsScreen> action) {
		extraButtons.add(new UiButton(label, () -> action.accept(this)));
		return this;
	}

	public Module module() {
		return module;
	}

	/** Open the HUD editor with this module's element highlighted. */
	public void openEditor() {
		open(new HudEditorScreen(this, module));
	}

	@Override
	protected void init() {
		super.init();
		int pw = Math.min(width - 24, 380);
		int ph = Math.min(height - 24, 320);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		list = new Rect(panel.x() + 8, panel.y() + 48, pw - 16, ph - 48 - 30);
		int bx = panel.x() + 8;
		int by = panel.bottom() - 24;
		for (UiButton b : extraButtons) {
			b.bounds(new Rect(bx, by, b.preferredWidth(font), 16));
			bx += b.bounds().w() + 4;
		}
		reset.bounds(new Rect(panel.right() - 8 - reset.preferredWidth(font), by, reset.preferredWidth(font), 16));
		buildRows();
	}

	/** Text fields for text settings (chat macros, highlight words ...), kept per setting. */
	private final Map<StringSetting, EditBox> textBoxes = new HashMap<>();

	private EditBox textBox(StringSetting setting) {
		return textBoxes.computeIfAbsent(setting, st -> {
			EditBox box = new EditBox(font, 0, 0, 150, 12, Component.literal(st.name()));
			box.setBordered(false);
			box.setMaxLength(256);
			box.setTextColor(Theme.TEXT);
			box.setValue(st.get());
			box.setResponder(st::set);
			return box;
		});
	}

	private void buildRows() {
		textBoxes.clear();
		rows.clear();
		KeyMapping key = Keybinds.of(module);
		if (key != null) rows.add(new Row("Keybind", "Also in Options > Controls > Hollow Client. Backspace clears it.", key));
		for (Setting<?> s : module.settings()) {
			if (s instanceof BooleanSetting || s instanceof NumberSetting || s instanceof EnumSetting<?> || s instanceof ColorSetting) {
				rows.add(new Row(s.name(), s.description(), s));
			} else if (s instanceof StringSetting ss) {
				rows.add(new Row(s.name(), s.description(), s));
				addWidget(textBox(ss));
			}
		}
	}

	private List<Row> visibleRows() {
		List<Row> out = new ArrayList<>();
		for (Row r : rows) {
			if (r.target instanceof Setting<?> s && !s.isVisible()) continue;
			out.add(r);
		}
		return out;
	}

	private int contentHeight() {
		return visibleRows().size() * ROW_H;
	}

	private float maxScroll() {
		return Math.max(0, contentHeight() - list.h());
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float a) {
		boolean modal = picker != null;
		int mx = modal ? -10000 : mouseX;
		int my = modal ? -10000 : mouseY;

		drawPanel(g, panel, 7, a);
		back.render(g, font, mx, my, dt, a);

		int titleX = back.bounds().right() + 8;
		Ui.draw(g, font, Ui.bold(module.name()), titleX, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		Ui.draw(g, font, Text.fit(font, module.description(), panel.w() - 24), panel.x() + 10, panel.y() + 31, Theme.fade(Theme.TEXT_DIM, a));

		int tx = panel.right() - 10 - UiToggle.W;
		int ty = panel.y() + 11;
		if (module.toggleable()) {
			Ui.draw(g, font, module.isEnabled() ? "Enabled" : "Disabled", tx - 4 - Ui.width(font, module.isEnabled() ? "Enabled" : "Disabled"), ty + 2, Theme.fade(module.isEnabled() ? Theme.TEXT : Theme.TEXT_FAINT, a));
			enabledToggle.render(g, tx, ty, module.isEnabled(), UiToggle.hit(tx, ty, mx, my), dt, a);
		}

		g.fill(panel.x() + 8, list.y() - 4, panel.right() - 8, list.y() - 3, Theme.fade(Theme.STROKE, a));

		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		int y = list.y() - Math.round(scroll);
		for (Row row : visibleRows()) {
			if (y + ROW_H > list.y() - ROW_H && y < list.bottom() + ROW_H) renderRow(g, row, y, mx, my, a);
			y += ROW_H;
		}
		g.disableScissor();
		if (rows.isEmpty()) {
			Ui.center(g, font, "This module has no settings.", list.centerX(), list.y() + 20, Theme.fade(Theme.TEXT_FAINT, a));
		}
		if (maxScroll() > 0) {
			int barH = Math.max(16, list.h() * list.h() / contentHeight());
			int barY = list.y() + Math.round((list.h() - barH) * (scroll / maxScroll()));
			Draw.roundRect(g, list.right() - 2, barY, 2, barH, 1, Theme.fade(Theme.STROKE_STRONG, a));
		}

		for (UiButton b : extraButtons) b.render(g, font, mx, my, dt, a);
		reset.setLabel(confirmReset ? "Click again to reset" : "Reset to defaults");
		reset.bounds(new Rect(panel.right() - 8 - reset.preferredWidth(font), reset.bounds().y(), reset.preferredWidth(font), 16));
		reset.render(g, font, mx, my, dt, a);
	}

	private void renderRow(GuiGraphics g, Row row, int y, int mx, int my, float a) {
		Rect r = new Rect(list.x(), y, list.w() - 6, ROW_H - 2);
		boolean hover = r.contains(mx, my) && list.contains(mx, my);
		if (hover) Draw.roundRect(g, r, 3, Theme.fade(Theme.CARD, a));
		int textY = y + (ROW_H - 2 - 8) / 2 + 1;
		Ui.draw(g, font, row.label, r.x() + 6, textY, Theme.fade(Theme.TEXT, a));
		if (hover && !row.tooltip.isEmpty() && mx < r.x() + 6 + Ui.width(font, row.label) + 4) {
			g.setTooltipForNextFrame(font, font.split(Component.literal(row.tooltip), 200), mx, my);
		}
		int right = r.right() - 6;
		int cy = y + (ROW_H - 2) / 2;
		Object t = row.target;
		if (t instanceof KeyMapping key) {
			Rect b = keyButton(r);
			boolean bh = b.contains(mx, my);
			Draw.roundRect(g, b, 3, Theme.fade(listening ? Theme.accentSoft() : (bh ? Theme.CARD_HOVER : Theme.CARD), a));
			Draw.roundOutline(g, b, 3, Theme.fade(listening ? Theme.accent() : Theme.STROKE, a));
			String label = listening ? "> press a key <" : (key.isUnbound() ? "None" : key.getTranslatedKeyMessage().getString());
			Ui.center(g, font, Text.fit(font, label, b.w() - 6), b.centerX(), b.y() + 4, Theme.fade(Theme.TEXT, a));
		} else if (t instanceof BooleanSetting bs) {
			int tx = right - UiToggle.W;
			int ty = cy - UiToggle.H / 2;
			toggles.computeIfAbsent(bs, k -> new UiToggle()).render(g, tx, ty, bs.get(), UiToggle.hit(tx, ty, mx, my), dt, a);
		} else if (t instanceof NumberSetting ns) {
			UiSlider slider = sliders.computeIfAbsent(ns, k -> new UiSlider(ns::fraction, ns::setFraction));
			Rect sr = new Rect(right - 100, cy - 5, 100, 10);
			slider.bounds(sr).render(g, mx, my, dt, a);
			String v = ns.display();
			Ui.draw(g, font, v, sr.x() - 6 - Ui.width(font, v), textY, Theme.fade(Theme.TEXT_DIM, a));
		} else if (t instanceof EnumSetting<?> es) {
			Rect b = new Rect(right - 100, cy - 7, 100, 14);
			boolean bh = b.contains(mx, my);
			Draw.roundRect(g, b, 3, Theme.fade(bh ? Theme.CARD_HOVER : Theme.CARD, a));
			Draw.roundOutline(g, b, 3, Theme.fade(Theme.STROKE, a));
			Ui.draw(g, font, "<", b.x() + 4, b.y() + 3, Theme.fade(Theme.TEXT_DIM, a));
			Ui.draw(g, font, ">", b.right() - 4 - Ui.width(font, ">"), b.y() + 3, Theme.fade(Theme.TEXT_DIM, a));
			Ui.center(g, font, Text.fit(font, es.display(), b.w() - 20), b.centerX(), b.y() + 3, Theme.fade(Theme.TEXT, a));
		} else if (t instanceof StringSetting ss) {
			EditBox box = textBox(ss);
			Rect b = textRect(r);
			boolean focused = box.isFocused();
			Draw.roundRect(g, b, 3, Theme.fade(focused ? Theme.CARD_HOVER : Theme.CARD, a));
			Draw.roundOutline(g, b, 3, Theme.fade(focused ? Theme.accent() : Theme.STROKE, a));
			box.setX(b.x() + 4);
			box.setY(b.y() + 3);
			box.setWidth(b.w() - 8);
			if (a > 0.5f) box.render(g, mx, my, 0);
		} else if (t instanceof ColorSetting cs) {
			Rect sw = new Rect(right - 26, cy - 6, 26, 12);
			Draw.checker(g, sw.x(), sw.y(), sw.w(), sw.h(), 3);
			g.fill(sw.x(), sw.y(), sw.right(), sw.bottom(), cs.get());
			g.renderOutline(sw.x(), sw.y(), sw.w(), sw.h(), Theme.fade(sw.contains(mx, my) ? 0xFFFFFFFF : Theme.STROKE_STRONG, a));
			String hex = cs.display();
			Ui.draw(g, font, hex, sw.x() - 6 - Ui.width(font, hex), textY, Theme.fade(Theme.TEXT_DIM, a));
		}
	}

	/** Text fields take the right half of the row. */
	private Rect textRect(Rect row) {
		int w = Math.max(90, Math.min(220, row.w() / 2));
		return new Rect(row.right() - 6 - w, row.y() + (ROW_H - 2 - 14) / 2, w, 14);
	}

	private Rect keyButton(Rect row) {
		return new Rect(row.right() - 6 - 100, row.y() + (ROW_H - 2 - 14) / 2, 100, 14);
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
		if (picker != null) {
			g.nextStratum();
			picker.render(g, font, mouseX, mouseY);
		}
	}

	private Row rowAt(double mx, double my) {
		if (!list.contains(mx, my)) return null;
		int y = list.y() - Math.round(scroll);
		for (Row row : visibleRows()) {
			if (my >= y && my < y + ROW_H) return row;
			y += ROW_H;
		}
		return null;
	}

	private int rowY(Row target) {
		int y = list.y() - Math.round(scroll);
		for (Row row : visibleRows()) {
			if (row == target) return y;
			y += ROW_H;
		}
		return y;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (picker != null) {
			if (!picker.mouseClicked(mx, my)) picker = null;
			return true;
		}
		if (listening) {
			Row keyRow = rows.isEmpty() ? null : rows.get(0);
			if (keyRow != null && keyRow.target instanceof KeyMapping key) {
				Keybinds.rebind(key, InputConstants.Type.MOUSE.getOrCreate(event.button()));
			}
			listening = false;
			return true;
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
		boolean left = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		if (left && back.click(mx, my)) return true;
		if (left && reset.click(mx, my)) return true;
		for (UiButton b : extraButtons) if (left && b.click(mx, my)) return true;
		confirmReset = false;
		int tx = panel.right() - 10 - UiToggle.W;
		if (left && module.toggleable() && UiToggle.hit(tx, panel.y() + 11, mx, my)) {
			UiSounds.toggle(!module.isEnabled());
			module.toggle();
			return true;
		}
		Row row = rowAt(mx, my);
		if (row == null) return super.mouseClicked(event, doubleClick);
		Rect r = new Rect(list.x(), rowY(row), list.w() - 6, ROW_H - 2);
		Object t = row.target;
		if (t instanceof KeyMapping) {
			if (left && keyButton(r).contains(mx, my)) {
				UiButton.playClick();
				listening = true;
			}
		} else if (t instanceof BooleanSetting bs) {
			if (left) {
				UiButton.playClick();
				bs.toggle();
			}
		} else if (t instanceof NumberSetting ns) {
			UiSlider slider = sliders.get(ns);
			if (left && slider != null) slider.click(mx, my);
		} else if (t instanceof EnumSetting<?> es) {
			UiButton.playClick();
			Rect b = new Rect(r.right() - 6 - 100, 0, 100, 0);
			es.cycle(!left || mx < b.centerX() ? -1 : 1);
		} else if (t instanceof StringSetting ss) {
			EditBox box = textBox(ss);
			for (EditBox other : textBoxes.values()) if (other != box) other.setFocused(false);
			setFocused(box);
			box.setFocused(true);
			box.mouseClicked(event, doubleClick);
		} else if (t instanceof ColorSetting cs && left) {
			UiButton.playClick();
			picker = new ColorPicker(cs);
			picker.layout(width, height, (int) mx, (int) my + 8);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (picker != null) return picker.mouseDragged(event.x(), event.y());
		for (UiSlider s : sliders.values()) if (s.drag(event.x())) return true;
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (picker != null) return picker.mouseReleased();
		for (UiSlider s : sliders.values()) s.release();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (picker != null) return true;
		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - sy * ROW_H * 1.5));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening) {
			Row keyRow = rows.isEmpty() ? null : rows.get(0);
			if (keyRow != null && keyRow.target instanceof KeyMapping key && !event.isEscape()) {
				boolean clear = event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE;
				Keybinds.rebind(key, clear ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			}
			listening = false;
			return true;
		}
		if (picker != null) {
			if (event.isEscape()) {
				picker = null;
				return true;
			}
			if (picker.keyPressed(event.key())) return true;
			return true;
		}
		for (EditBox box : textBoxes.values()) {
			if (box.isFocused() && (event.isEscape() || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
				box.setFocused(false);
				setFocused(null);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (picker != null && picker.charTyped(event.codepoint())) return true;
		return super.charTyped(event);
	}

	private void resetModule() {
		if (!confirmReset) {
			confirmReset = true;
			return;
		}
		confirmReset = false;
		module.resetToDefaults();
		KeyMapping key = Keybinds.of(module);
		if (key != null) Keybinds.rebind(key, key.getDefaultKey());
	}
}
