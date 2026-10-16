package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.config.Preset;
import dev.telmuad.hollowclient.gui.widget.ColorPicker;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.gui.widget.UiSlider;
import dev.telmuad.hollowclient.gui.widget.UiToggle;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.render.CosmeticModels.Hat;
import dev.telmuad.hollowclient.render.CosmeticModels.Wings;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.styles.CapeManager;
import dev.telmuad.hollowclient.styles.PackImporter;
import dev.telmuad.hollowclient.styles.PackManager;
import dev.telmuad.hollowclient.styles.PresetManager;
import dev.telmuad.hollowclient.util.FileDialogs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Styles: capes, cosmetics (hat + wings), texture pack import and presets. Everything here only changes what you
 * see. Clicking a choice applies it straight away; a live model of your player shows the result when in a world.
 * <p>
 * The screen is drawn "immediate mode": every frame, clickable things register a hit area, and clicks are matched
 * against the areas from the last frame.
 */
public final class StylesScreen extends HollowScreen {
	public enum Tab {
		CAPES("Capes"), COSMETICS("Wardrobe"), EMOTES("Emotes"), PACKS("Texture Packs"), PRESETS("Presets");
		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int ROW = 22;

	private float previewYaw = 180f;
	private float previewYawTarget = 180f;
	private boolean draggingPreview;
	private long lastPreviewDrag;
	private double previewDragX;

	private record Hit(Rect rect, Runnable action, boolean inList) {
	}

	private Tab tab;
	private Rect panel = Rect.EMPTY;
	private Rect toolbar = Rect.EMPTY;
	private Rect list = Rect.EMPTY;
	private Rect previewArea = Rect.EMPTY;
	private Rect footer = Rect.EMPTY;
	private final UiButton back = new UiButton("< Back", this::onClose);
	private final List<Hit> hits = new ArrayList<>();
	private final List<Hit> frameHits = new ArrayList<>();
	private final Map<Object, UiToggle> toggles = new HashMap<>();
	private final Map<Object, Float> hover = new HashMap<>();
	private final Map<Object, UiSlider> sliders = new HashMap<>();
	private UiSlider activeSlider;
	private ColorPicker picker;
	private EditBox presetName;
	private float scroll;
	private float scrollTarget;
	private int contentHeight;
	private String status;
	private boolean statusError;
	private long statusAt;
	private String confirm;
	private final Deque<Path> importQueue = new ArrayDeque<>();

	public StylesScreen(Tab tab, Screen parent) {
		super(Component.literal("Styles"), parent);
		this.tab = tab;
		CapeManager.refresh();
		faceFor(tab);
	}

	/** The preview shows your back for capes and your front for everything else. */
	private void faceFor(Tab t) {
		previewYawTarget = t == Tab.CAPES ? 0f : 180f;
		previewYaw = previewYawTarget;
	}

	// ------------------------------------------------------------------------------------------------ summaries for the menu tiles

	public static String capeSummary() {
		if (Modules.CAPE == null || !Modules.CAPE.isEnabled()) return "Off. Built-in capes or your own PNG.";
		return "Wearing: " + CapeManager.nameOf(Modules.CAPE.capeId());
	}

	public static String cosmeticsSummary() {
		List<String> on = new ArrayList<>();
		if (Modules.HAT != null && Modules.HAT.isEnabled()) on.add(Modules.HAT.hat().label());
		if (Modules.WINGS != null && Modules.WINGS.isEnabled()) on.add(Modules.WINGS.wings().label() + " wings");
		if (Modules.HALO != null && Modules.HALO.isEnabled()) on.add("halo");
		if (Modules.HORNS != null && Modules.HORNS.isEnabled()) on.add("horns");
		if (Modules.AURA != null && Modules.AURA.isEnabled()) on.add("aura");
		if (Modules.PET != null && Modules.PET.isEnabled()) on.add("pet");
		return on.isEmpty() ? "Off. Hats, wings, a halo, horns, an aura and a pet." : "Wearing: " + String.join(", ", on);
	}

	public static String packSummary() {
		String active = PackManager.active();
		int n = HollowClient.styles().packs.size();
		if (!active.isEmpty()) return "Using: " + active;
		return n == 0 ? "Import a resource pack .zip or folder." : n + " imported, none in use.";
	}

	public static String presetSummary() {
		int n = HollowClient.styles().presets.size();
		return n == 0 ? "Save your cape, cosmetics and pack as a preset." : n + (n == 1 ? " preset saved." : " presets saved.");
	}

	// ------------------------------------------------------------------------------------------------ layout

	@Override
	protected void init() {
		super.init();
		int margin = width < 420 ? 4 : 14;
		int pw = Math.min(width - 2 * margin, 620);
		int ph = Math.min(height - 2 * margin, 370);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		footer = new Rect(panel.x() + 8, panel.bottom() - 18, pw - 16, 14);
		boolean showPreview = minecraft != null && minecraft.player != null && pw >= 420 && tab != Tab.PACKS;
		int previewW = showPreview ? Math.min(130, pw / 4) : 0;
		int contentRight = panel.right() - 8 - (showPreview ? previewW + 8 : 0);
		previewArea = showPreview ? new Rect(contentRight + 8, panel.y() + 34, previewW, ph - 34 - 22) : Rect.EMPTY;
		toolbar = new Rect(panel.x() + 8, panel.y() + 34, contentRight - panel.x() - 8, 18);
		int listTop = toolbar.bottom() + 6;
		list = new Rect(panel.x() + 8, listTop, contentRight - panel.x() - 8, footer.y() - 4 - listTop);

		presetName = new EditBox(font, 0, 0, 140, 14, Component.literal("Preset name"));
		presetName.setMaxLength(Preset.MAX_NAME);
		presetName.setHint(Ui.text("Preset name").withStyle(ChatFormatting.DARK_GRAY));
		presetName.setBordered(false);
		presetName.setTextColor(Theme.TEXT);
		presetName.visible = tab == Tab.PRESETS;
		addWidget(presetName);
	}

	private void switchTab(Tab next) {
		if (next == tab) return;
		tab = next;
		faceFor(next);
		scroll = scrollTarget = 0;
		confirm = null;
		picker = null;
		CapeManager.refresh();
		rebuildWidgets();
	}

	private void status(String message, boolean error) {
		status = message;
		statusError = error;
		statusAt = Util.getMillis();
	}

	// ------------------------------------------------------------------------------------------------ immediate-mode helpers

	private void hit(Rect r, Runnable action, boolean inList) {
		frameHits.add(new Hit(r, action, inList));
	}

	private float anim(Object key, boolean on) {
		float v = Anim.approach(hover.getOrDefault(key, 0f), on ? 1f : 0f, dt, 16f);
		hover.put(key, v);
		return v;
	}

	private boolean mouseIn(Rect r, int mx, int my, boolean inList) {
		return r.contains(mx, my) && (!inList || list.contains(mx, my));
	}

	private void button(GuiGraphicsExtractor g, Rect r, String label, UiButton.Style style, int mx, int my, float a, boolean inList, Runnable action) {
		boolean h = mouseIn(r, mx, my, inList);
		float t = anim(r.x() * 31 + r.y() * 17 + label.hashCode(), h);
		int bg;
		int fg;
		int outline;
		switch (style) {
			case PRIMARY -> {
				bg = Theme.mix(Theme.accentSoft(), Theme.accent(), t);
				fg = 0xFFFFFFFF;
				outline = 0;
			}
			case DANGER -> {
				bg = Theme.mix(Theme.CARD, 0xFF4A2024, t);
				fg = Theme.mix(Theme.DANGER, 0xFFFFFFFF, t * 0.5f);
				outline = Theme.mix(Theme.STROKE, Theme.DANGER, 0.5f + t * 0.5f);
			}
			default -> {
				bg = Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
				fg = Theme.mix(Theme.TEXT_DIM, Theme.TEXT, t);
				outline = Theme.mix(Theme.STROKE, Theme.accent(), t * 0.6f);
			}
		}
		Draw.roundRect(g, r, 3, Theme.fade(bg, a));
		if (outline != 0) Draw.roundOutline(g, r, 3, Theme.fade(outline, a));
		String text = Text.fit(font, label, r.w() - 6);
		Ui.draw(g, font, text, r.x() + (r.w() - Ui.width(font, text)) / 2, r.y() + (r.h() - 8) / 2 + 1, Theme.fade(fg, a));
		hit(r, () -> {
			UiButton.playClick();
			action.run();
		}, inList);
	}

	private int buttonW(String label) {
		return Ui.width(font, label) + 14;
	}

	private void toggle(GuiGraphicsExtractor g, Object key, int x, int y, boolean value, int mx, int my, float a, boolean inList, Runnable action) {
		UiToggle t = toggles.computeIfAbsent(key, k -> new UiToggle());
		t.render(g, x, y, value, UiToggle.hit(x, y, mx, my) && (!inList || list.contains(mx, my)), dt, a);
		hit(new Rect(x - 2, y - 2, UiToggle.W + 4, UiToggle.H + 4), () -> {
			UiButton.playClick();
			action.run();
		}, inList);
	}

	private void chip(GuiGraphicsExtractor g, Rect r, String label, boolean selected, int mx, int my, float a, Runnable action) {
		boolean h = mouseIn(r, mx, my, true);
		float t = anim("chip" + label + r.y(), h);
		int bg = selected ? Theme.cardOn() : Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
		Draw.roundRect(g, r, 4, Theme.fade(bg, a));
		Draw.roundOutline(g, r, 4, Theme.fade(selected ? Theme.accent() : Theme.mix(Theme.STROKE, Theme.STROKE_STRONG, t), a));
		Ui.draw(g, font, label, r.x() + (r.w() - Ui.width(font, label)) / 2, r.y() + (r.h() - 8) / 2 + 1, Theme.fade(selected ? Theme.TEXT : Theme.TEXT_DIM, a));
		hit(r, () -> {
			UiButton.playClick();
			action.run();
		}, true);
	}

	private void swatch(GuiGraphicsExtractor g, Rect r, ColorSetting setting, int mx, int my, float a) {
		Draw.checker(g, r.x(), r.y(), r.w(), r.h(), 3);
		g.fill(r.x(), r.y(), r.right(), r.bottom(), setting.get());
		g.outline(r.x(), r.y(), r.w(), r.h(), Theme.fade(mouseIn(r, mx, my, true) ? 0xFFFFFFFF : Theme.STROKE_STRONG, a));
		hit(r, () -> {
			UiButton.playClick();
			picker = new ColorPicker(setting);
			picker.layout(width, height, r.right(), r.bottom() + 2);
		}, true);
	}

	private void slider(GuiGraphicsExtractor g, Rect r, NumberSetting setting, int mx, int my, float a) {
		UiSlider s = sliders.computeIfAbsent(setting, k -> new UiSlider(setting::fraction, setting::setFraction));
		s.bounds(r).render(g, mx, my, dt, a);
		hit(r.inset(-3), () -> {
			s.click(lastClickX, lastClickY);
			activeSlider = s;
		}, true);
	}

	private double lastClickX;
	private double lastClickY;

	// ------------------------------------------------------------------------------------------------ render

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		frameHits.clear();
		boolean modal = picker != null;
		int mx = modal ? -10000 : mouseX;
		int my = modal ? -10000 : mouseY;

		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		hit(back.bounds(), () -> back.click(back.bounds().x() + 1, back.bounds().y() + 1), false);
		Ui.draw(g, font, Ui.bold("Styles"), back.bounds().right() + 8, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		renderTabs(g, mx, my, a);
		g.fill(panel.x() + 8, panel.y() + 29, panel.right() - 8, panel.y() + 30, Theme.fade(Theme.STROKE, a));

		presetName.visible = tab == Tab.PRESETS;
		switch (tab) {
			case CAPES -> capesToolbar(g, mx, my, a);
			case COSMETICS -> cosmeticsToolbar(g, a);
			case EMOTES -> emotesToolbar(g, a);
			case PACKS -> packsToolbar(g, mx, my, a);
			case PRESETS -> presetsToolbar(g, mx, my, a);
		}

		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		int top = list.y() - Math.round(scroll);
		contentHeight = switch (tab) {
			case CAPES -> capesList(g, top, mx, my, a);
			case COSMETICS -> cosmeticsList(g, top, mx, my, a);
			case EMOTES -> emotesList(g, top, mx, my, a);
			case PACKS -> packsList(g, top, mx, my, a);
			case PRESETS -> presetsList(g, top, mx, my, a);
		};
		g.disableScissor();
		float max = maxScroll();
		if (scrollTarget > max) scrollTarget = max;
		if (max > 0) {
			int barH = Math.max(16, list.h() * list.h() / Math.max(1, contentHeight));
			int barY = list.y() + Math.round((list.h() - barH) * (Math.min(scroll, max) / max));
			Draw.roundRect(g, list.right() - 2, barY, 2, barH, 1, Theme.fade(Theme.STROKE_STRONG, a));
		}

		if (previewArea.w() > 0) {
			Draw.roundRect(g, previewArea, 6, Theme.fade(0x40000000, a));
			if (a > 0.6f) {
				// drag to spin; after a few idle seconds it turns slowly on its own
				if (!draggingPreview && Util.getMillis() - lastPreviewDrag > 2500) previewYawTarget += dt * 24f;
				previewYaw = Anim.approach(previewYaw, previewYawTarget, dt, 10f);
				PlayerPreview.render(g, previewArea.x(), previewArea.y() + 4, previewArea.w(), previewArea.h() - 18, previewYaw);
			}
			String cap = draggingPreview ? "Turning..." : "Drag to turn";
			Ui.draw(g, font, cap, previewArea.centerX() - Ui.width(font, cap) / 2, previewArea.bottom() - 12, Theme.fade(Theme.TEXT_FAINT, a));
		}
		renderFooter(g, a);
		hits.clear();
		hits.addAll(frameHits);
	}

	private void renderTabs(GuiGraphicsExtractor g, int mx, int my, float a) {
		int x = panel.right() - 8;
		List<Tab> tabs = List.of(Tab.values());
		int[] widths = new int[tabs.size()];
		int total = 0;
		for (int i = 0; i < tabs.size(); i++) {
			widths[i] = Ui.width(font, tabs.get(i).label) + 14;
			total += widths[i];
		}
		x -= total;
		Rect bar = new Rect(x - 2, panel.y() + 7, total + 4, 18);
		Draw.roundRect(g, bar, 4, Theme.fade(Theme.RAIL, a));
		for (int i = 0; i < tabs.size(); i++) {
			Tab t = tabs.get(i);
			Rect r = new Rect(x, panel.y() + 9, widths[i], 14);
			boolean sel = t == tab;
			float h = anim("tab" + t, r.contains(mx, my));
			if (sel) Draw.roundRect(g, r, 3, Theme.fade(Theme.accentSoft(), a));
			else if (h > 0.01f) Draw.roundRect(g, r, 3, Theme.fade(Theme.CARD_HOVER, h * a));
			Ui.draw(g, font, t.label, r.x() + 7, r.y() + 3, Theme.fade(sel ? 0xFFFFFFFF : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, h), a));
			hit(r, () -> {
				UiButton.playClick();
				switchTab(t);
			}, false);
			x += widths[i];
		}
	}

	private void renderFooter(GuiGraphicsExtractor g, float a) {
		long age = Util.getMillis() - statusAt;
		if (status != null && age < 6000) {
			float fade = age > 5000 ? 1f - (age - 5000) / 1000f : 1f;
			int color = statusError ? Theme.DANGER : Theme.SUCCESS;
			Ui.draw(g, font, Text.fit(font, status, footer.w()), footer.x() + 2, footer.y() + 3, Theme.fade(color, a * fade));
			return;
		}
		String hint = switch (tab) {
			case CAPES -> "Tip: drop a PNG or animated GIF on this screen to import it as a cape.";
			case COSMETICS -> "Drag the preview to turn your player. Cosmetics show in third person (F5).";
			case EMOTES -> "Hold B in game for the emote wheel. Only you see emotes.";
			case PACKS -> "Tip: drop a pack .zip or folder on this screen to import it.";
			case PRESETS -> "A preset saves your look, theme, trail, crosshair, HUD layout and pack.";
		};
		Ui.draw(g, font, Text.fit(font, hint, footer.w()), footer.x() + 2, footer.y() + 3, Theme.fade(Theme.TEXT_FAINT, a));
	}

	// ------------------------------------------------------------------------------------------------ capes

	private void capesToolbar(GuiGraphicsExtractor g, int mx, int my, float a) {
		int x = toolbar.x();
		int y = toolbar.y() + 3;
		boolean on = Modules.CAPE.isEnabled();
		toggle(g, "cape_on", x, y, on, mx, my, a, false, Modules.CAPE::toggle);
		Ui.draw(g, font, "Cape", x + UiToggle.W + 5, y + 2, Theme.fade(Theme.TEXT, a));
		x += UiToggle.W + 5 + Ui.width(font, "Cape") + 14;
		boolean el = Modules.CAPE.onElytra();
		toggle(g, "cape_elytra", x, y, el, mx, my, a, false, () -> Modules.CAPE.elytraSetting().toggle());
		Ui.draw(g, font, "On elytra", x + UiToggle.W + 5, y + 2, Theme.fade(Theme.TEXT, a));

		int right = toolbar.right();
		String open = "Open folder";
		Rect openR = new Rect(right - buttonW(open), toolbar.y(), buttonW(open), 16);
		button(g, openR, open, UiButton.Style.NORMAL, mx, my, a, false, () -> {
			try {
				Files.createDirectories(CapeManager.folder());
			} catch (java.io.IOException ignored) {
			}
			Util.getPlatform().openPath(CapeManager.folder());
		});
		String imp = "Import PNG...";
		Rect impR = new Rect(openR.x() - 4 - buttonW(imp), toolbar.y(), buttonW(imp), 16);
		button(g, impR, imp, UiButton.Style.PRIMARY, mx, my, a, false, () ->
				FileDialogs.openFile("Choose a cape image", downloadsDir(), new String[]{"*.png", "*.gif"}, "Cape images (.png, .gif)", this::importCape));
	}

	private int capesList(GuiGraphicsExtractor g, int top, int mx, int my, float a) {
		List<CapeManager.Cape> capes = CapeManager.all();
		int tileW = 56;
		int tileH = 80;
		int gap = 6;
		int cols = Math.max(1, (list.w() - 6 + gap) / (tileW + gap));
		String current = Modules.CAPE.isEnabled() ? Modules.CAPE.capeId() : null;
		for (int i = 0; i < capes.size(); i++) {
			CapeManager.Cape c = capes.get(i);
			Rect r = new Rect(list.x() + (i % cols) * (tileW + gap), top + (i / cols) * (tileH + gap), tileW, tileH);
			if (r.bottom() < list.y() || r.y() > list.bottom()) continue;
			boolean sel = c.id().equals(current);
			float h = anim(c.id(), mouseIn(r, mx, my, true));
			Draw.roundRect(g, r, 5, Theme.fade(sel ? Theme.cardOn() : Theme.mix(Theme.CARD, Theme.CARD_HOVER, h), a));
			Draw.roundOutline(g, r, 5, Theme.fade(sel ? Theme.accent() : Theme.mix(Theme.STROKE, Theme.STROKE_STRONG, h), a));
			Identifier tex = CapeManager.texture(c.id());
			int cx = r.x() + (tileW - 30) / 2;
			int cy = r.y() + 7;
			if (tex != null) {
				// The cape's outer face: UV (1,1)-(11,17) on the 64x32 layout, whatever the resolution.
				g.blit(tex, cx, cy, cx + 30, cy + 48, 1 / 64f, 11 / 64f, 1 / 32f, 17 / 32f);
			} else {
				g.fill(cx, cy, cx + 30, cy + 48, Theme.fade(0xFF3A1E22, a));
				Ui.center(g, font, "?", cx + 15, cy + 20, Theme.fade(Theme.DANGER, a));
			}
			String name = Text.fit(font, c.name(), tileW - 6);
			Ui.draw(g, font, name, r.x() + (tileW - Ui.width(font, name)) / 2, r.bottom() - 18, Theme.fade(sel ? Theme.TEXT : Theme.TEXT_DIM, a));
			String sub = c.builtin() ? "Built-in" : "Imported";
			Ui.draw(g, font, sub, r.x() + (tileW - Ui.width(font, sub)) / 2, r.bottom() - 9, Theme.fade(Theme.TEXT_FAINT, a));
			hit(r, () -> {
				UiButton.playClick();
				Modules.CAPE.select(c.id());
			}, true);
			if (!c.builtin()) {
				Rect x = new Rect(r.right() - 11, r.y() + 2, 9, 9);
				boolean asking = ("cape:" + c.id()).equals(confirm);
				boolean xh = mouseIn(x, mx, my, true);
				Draw.roundRect(g, x, 2, Theme.fade(asking ? Theme.DANGER : (xh ? 0xFF4A2024 : 0x80000000), a));
				Ui.draw(g, font, asking ? "?" : "x", x.x() + (asking ? 2 : 2), x.y(), Theme.fade(0xFFFFFFFF, a));
				hit(x, () -> {
					UiButton.playClick();
					if (asking) {
						if (c.id().equals(Modules.CAPE.capeId())) Modules.CAPE.select(CapeManager.DEFAULT);
						CapeManager.remove(c.id());
						confirm = null;
						status("Removed the cape \"" + c.name() + "\".", false);
					} else {
						confirm = "cape:" + c.id();
						status("Click the ? again to delete \"" + c.name() + "\".", true);
					}
				}, true);
			}
		}
		int rows = (capes.size() + cols - 1) / cols;
		int y = top + rows * (tileH + gap) + 4;
		y = effectRow(g, y, "cape_glow", Modules.CAPE.glowEffect(), mx, my, a);
		y = effectRow(g, y, "cape_shimmer", Modules.CAPE.shimmerEffect(), mx, my, a);
		return y - top;
	}

	/** One effect: its name, a strength slider (while on) and a switch. */
	private int effectRow(GuiGraphicsExtractor g, int y, String key, dev.telmuad.hollowclient.setting.Effect effect, int mx, int my, float a) {
		var on = effect.switchSetting();
		y = row(g, y, on.name(), a);
		int tx = list.right() - 8 - UiToggle.W;
		toggle(g, key, tx, y - ROW + 5, on.on(), mx, my, a, true, on::toggle);
		if (on.on()) {
			NumberSetting strength = effect.strengthSetting();
			Rect sr = new Rect(tx - 10 - 90, y - ROW + 6, 90, 10);
			slider(g, sr, strength, mx, my, a);
			Ui.draw(g, font, strength.display(), sr.x() - 6 - Ui.width(font, strength.display()), y - ROW + 7, Theme.fade(Theme.TEXT_DIM, a));
		}
		return y;
	}

	private int colorRow(GuiGraphicsExtractor g, int y, String label, dev.telmuad.hollowclient.setting.ColorSetting color, int mx, int my, float a) {
		y = row(g, y, label, a);
		swatch(g, new Rect(list.right() - 8 - 30, y - ROW + 5, 30, 12), color, mx, my, a);
		return y;
	}

	private void importCape(Path file) {
		if (file == null) return;
		try {
			String id = CapeManager.importCape(file);
			Modules.CAPE.select(id);
			status("Imported \"" + file.getFileName() + "\" and put it on.", false);
			Toasts.success("Cape imported", String.valueOf(file.getFileName()));
		} catch (PackImporter.ImportException e) {
			status(e.getMessage(), true);
			Toasts.error("Cape not imported", e.getMessage());
		}
	}

	// ------------------------------------------------------------------------------------------------ cosmetics

	private void cosmeticsToolbar(GuiGraphicsExtractor g, float a) {
		Ui.draw(g, font, "Pick a hat and wings. Changes show right away.", toolbar.x() + 1, toolbar.y() + 5, Theme.fade(Theme.TEXT_DIM, a));
	}

	private int cosmeticsList(GuiGraphicsExtractor g, int top, int mx, int my, float a) {
		int y = top;
		y = section(g, y, "Hat", Modules.HAT.isEnabled(), Modules.HAT::toggle, mx, my, a);
		y = chips(g, y, Modules.HAT.styleSetting(), Hat.values(), mx, my, a, () -> Modules.HAT.setEnabled(true));
		y = row(g, y, "Tint", a);
		swatch(g, new Rect(list.right() - 8 - 30, y - ROW + 5, 30, 12), Modules.HAT.tintSetting(), mx, my, a);
		y += 8;
		y = section(g, y, "Wings", Modules.WINGS.isEnabled(), Modules.WINGS::toggle, mx, my, a);
		y = chips(g, y, Modules.WINGS.styleSetting(), Wings.values(), mx, my, a, () -> Modules.WINGS.setEnabled(true));
		y = row(g, y, "Tint", a);
		swatch(g, new Rect(list.right() - 8 - 30, y - ROW + 5, 30, 12), Modules.WINGS.tintSetting(), mx, my, a);
		y = row(g, y, "Flap speed", a);
		NumberSetting flap = Modules.WINGS.flapSetting();
		Rect sr = new Rect(list.right() - 8 - 90, y - ROW + 6, 90, 10);
		slider(g, sr, flap, mx, my, a);
		Ui.draw(g, font, flap.display(), sr.x() - 6 - Ui.width(font, flap.display()), y - ROW + 7, Theme.fade(Theme.TEXT_DIM, a));
		y = row(g, y, "Hide while wearing elytra", a);
		toggle(g, "wings_elytra", list.right() - 8 - UiToggle.W, y - ROW + 5, Modules.WINGS.hideWithElytra(), mx, my, a, true,
				() -> Modules.WINGS.hideWithElytraSetting().toggle());
		y += 8;
		var halo = Modules.HALO;
		y = section(g, y, "Halo", halo.isEnabled(), halo::toggle, mx, my, a);
		y = chips(g, y, halo.styleSetting(), dev.telmuad.hollowclient.modules.styles.HaloModule.Style.values(), mx, my, a, () -> halo.setEnabled(true));
		y = row(g, y, "Theme color", a);
		toggle(g, "halo_theme", list.right() - 8 - UiToggle.W, y - ROW + 5, halo.themeColorSetting().on(), mx, my, a, true, halo.themeColorSetting()::toggle);
		if (!halo.themeColorSetting().on()) y = colorRow(g, y, "Color", halo.tintSetting(), mx, my, a);
		y = effectRow(g, y, "halo_glow", halo.glowEffect(), mx, my, a);
		y = effectRow(g, y, "halo_spin", halo.spinEffect(), mx, my, a);
		y += 8;
		var horns = Modules.HORNS;
		y = section(g, y, "Horns", horns.isEnabled(), horns::toggle, mx, my, a);
		y = chips(g, y, horns.styleSetting(), dev.telmuad.hollowclient.modules.styles.HornsModule.Style.values(), mx, my, a, () -> horns.setEnabled(true));
		y = colorRow(g, y, "Tint", horns.tintSetting(), mx, my, a);
		y += 8;
		var aura = Modules.AURA;
		y = section(g, y, "Aura", aura.isEnabled(), aura::toggle, mx, my, a);
		y = chips(g, y, aura.styleSetting(), dev.telmuad.hollowclient.modules.styles.AuraModule.Style.values(), mx, my, a, () -> aura.setEnabled(true));
		y = effectRow(g, y, "aura_density", aura.densityEffect(), mx, my, a);
		y += 8;
		var pet = Modules.PET;
		y = section(g, y, "Pet", pet.isEnabled(), pet::toggle, mx, my, a);
		y = chips(g, y, pet.styleSetting(), dev.telmuad.hollowclient.modules.styles.PetModule.Style.values(), mx, my, a, () -> pet.setEnabled(true));
		y = colorRow(g, y, "Tint", pet.tintSetting(), mx, my, a);
		return y - top;
	}

	// ------------------------------------------------------------------------------------------------ emotes

	private void emotesToolbar(GuiGraphicsExtractor g, float a) {
		Ui.draw(g, font, "Click one to play it now. In game, hold B for the wheel.", toolbar.x() + 1, toolbar.y() + 5, Theme.fade(Theme.TEXT_DIM, a));
	}

	private int emotesList(GuiGraphicsExtractor g, int top, int mx, int my, float a) {
		var emotes = Modules.EMOTES;
		int y = section(g, top, "Emotes", emotes.isEnabled(), emotes::toggle, mx, my, a);
		var all = dev.telmuad.hollowclient.styles.Emotes.Emote.values();
		var playing = dev.telmuad.hollowclient.styles.Emotes.current();
		int x = list.x() + 8;
		int w = 72;
		int h = 22;
		int gap = 6;
		for (var e : all) {
			if (x + w > list.right() - 8) {
				x = list.x() + 8;
				y += h + gap;
			}
			Rect r = new Rect(x, y, w, h);
			chip(g, r, e.label(), e == playing, mx, my, a, () -> {
				if (!emotes.isEnabled()) emotes.setEnabled(true);
				dev.telmuad.hollowclient.styles.Emotes.play(e);
			});
			x += w + gap;
		}
		y += h + 12;
		y = row(g, y, "Switch to third person while emoting", a);
		toggle(g, "emote_third", list.right() - 8 - UiToggle.W, y - ROW + 5, emotes.thirdPerson(), mx, my, a, true, emotes.thirdPersonSetting()::toggle);
		y = row(g, y, "Stop when you move", a);
		toggle(g, "emote_stop", list.right() - 8 - UiToggle.W, y - ROW + 5, emotes.stopWhenMoving(), mx, my, a, true, emotes.stopWhenMovingSetting()::toggle);
		return y - top;
	}

	private int section(GuiGraphicsExtractor g, int y, String title, boolean on, Runnable toggleAction, int mx, int my, float a) {
		Rect r = new Rect(list.x(), y, list.w() - 6, ROW);
		Draw.roundRect(g, r, 4, Theme.fade(Theme.RAIL, a));
		Ui.draw(g, font, Ui.bold(title), r.x() + 8, y + 7, Theme.fade(Theme.TEXT, a));
		toggle(g, "section_" + title, r.right() - 8 - UiToggle.W, y + 5, on, mx, my, a, true, toggleAction);
		return y + ROW + 4;
	}

	private <E extends Enum<E>> int chips(GuiGraphicsExtractor g, int y, EnumSetting<E> setting, E[] values, int mx, int my, float a, Runnable alsoEnable) {
		int x = list.x() + 8;
		for (E v : values) {
			String label = EnumSetting.labelOf(v);
			int w = Ui.width(font, label) + 16;
			if (x + w > list.right() - 8) {
				x = list.x() + 8;
				y += 20;
			}
			chip(g, new Rect(x, y, w, 16), label, setting.get() == v, mx, my, a, () -> {
				setting.set(v);
				alsoEnable.run();
			});
			x += w + 4;
		}
		return y + 22;
	}

	private int row(GuiGraphicsExtractor g, int y, String label, float a) {
		Ui.draw(g, font, label, list.x() + 8, y + 7, Theme.fade(Theme.TEXT, a));
		return y + ROW;
	}

	// ------------------------------------------------------------------------------------------------ packs

	private void packsToolbar(GuiGraphicsExtractor g, int mx, int my, float a) {
		int x = toolbar.x();
		String[] labels = {"Import .zip...", "Import folder...", "Open folder", "Use none"};
		Runnable[] actions = {
				() -> FileDialogs.openFile("Choose a resource pack .zip", downloadsDir(), new String[]{"*.zip"}, "Resource packs (.zip)", this::importPack),
				() -> FileDialogs.pickFolder("Choose a resource pack folder", downloadsDir(), this::importPack),
				PackManager::openFolder,
				() -> {
					if (!PackManager.active().isEmpty()) {
						PackManager.use(null);
						status("Switched Hollow's packs off. Reloading textures...", false);
					}
				}};
		for (int i = 0; i < labels.length; i++) {
			int w = buttonW(labels[i]);
			if (x + w > toolbar.right()) break;
			button(g, new Rect(x, toolbar.y(), w, 16), labels[i], i == 0 ? UiButton.Style.PRIMARY : UiButton.Style.NORMAL, mx, my, a, false, actions[i]);
			x += w + 4;
		}
		if (PackManager.busy()) {
			String dots = ".".repeat((int) (Util.getMillis() / 400 % 4));
			Ui.draw(g, font, "Importing" + dots, x + 4, toolbar.y() + 5, Theme.fade(Theme.accent(), a));
		}
	}

	private int packsList(GuiGraphicsExtractor g, int top, int mx, int my, float a) {
		List<PackManager.Entry> packs = PackManager.list();
		int y = top;
		if (packs.isEmpty()) {
			Text.wrapped(g, font, "No packs yet. Import a resource pack .zip or folder (or drop one here). Hollow copies it into your resourcepacks folder and you can switch packs from this list.",
					list.x() + 8, y + 8, list.w() - 16, 4, Theme.fade(Theme.TEXT_FAINT, a));
			return 60;
		}
		for (PackManager.Entry p : packs) {
			Rect r = new Rect(list.x(), y, list.w() - 6, 28);
			if (r.bottom() >= list.y() && r.y() <= list.bottom()) {
				float h = anim("pack" + p.name(), mouseIn(r, mx, my, true));
				Draw.roundRect(g, r, 5, Theme.fade(p.active() ? Theme.cardOn() : Theme.mix(Theme.CARD, Theme.CARD_HOVER, h), a));
				Draw.roundOutline(g, r, 5, Theme.fade(p.active() ? Theme.accent() : Theme.STROKE, a));
				Identifier icon = p.exists() ? PackManager.icon(p.name()) : null;
				if (icon != null) g.blit(icon, r.x() + 5, r.y() + 4, r.x() + 25, r.y() + 24, 0f, 1f, 0f, 1f);
				else Draw.roundRect(g, r.x() + 5, r.y() + 4, 20, 20, 3, Theme.fade(Theme.SWITCH_OFF, a));
				// Buttons from the right
				int bx = r.right() - 6;
				String removeLabel = ("pack:" + p.name()).equals(confirm) ? "Sure?" : "Remove";
				int rw = buttonW(removeLabel);
				bx -= rw;
				button(g, new Rect(bx, r.y() + 6, rw, 16), removeLabel, UiButton.Style.DANGER, mx, my, a, true, () -> removePack(p.name()));
				if (p.exists()) {
					if (p.active()) {
						String act = "In use";
						int aw = Ui.width(font, act) + 12;
						bx -= aw + 4;
						Draw.roundRect(g, bx, r.y() + 7, aw, 14, 7, Theme.fade(Theme.accentSoft(), a));
						Ui.draw(g, font, act, bx + 6, r.y() + 10, Theme.fade(0xFFFFFFFF, a));
					} else {
						int uw = buttonW("Use");
						bx -= uw + 4;
						button(g, new Rect(bx, r.y() + 6, uw, 16), "Use", UiButton.Style.PRIMARY, mx, my, a, true, () -> {
							if (PackManager.use(p.name())) status("Switching to \"" + p.name() + "\". Reloading textures...", false);
							else status("Minecraft couldn't load \"" + p.name() + "\".", true);
						});
					}
				}
				int textRight = bx - 6;
				String name = Text.fit(font, p.name(), textRight - (r.x() + 32));
				Ui.draw(g, font, name, r.x() + 32, r.y() + 5, Theme.fade(Theme.TEXT, a));
				String sub = !p.exists() ? "Missing from the resourcepacks folder" : (p.name().toLowerCase(Locale.ROOT).endsWith(".zip") ? "Zip pack" : "Folder pack");
				Ui.draw(g, font, Text.fit(font, sub, textRight - (r.x() + 32)), r.x() + 32, r.y() + 16, Theme.fade(p.exists() ? Theme.TEXT_FAINT : Theme.DANGER, a));
			}
			y += 32;
		}
		return y - top;
	}

	private void importPack(Path source) {
		if (source == null) return;
		if (PackManager.busy()) {
			importQueue.add(source);
			return;
		}
		status("Importing \"" + source.getFileName() + "\"...", false);
		PackManager.importAsync(source, name -> {
			status("Imported \"" + name + "\". Click Use to switch to it.", false);
			Toasts.success("Texture pack imported", name);
			nextImport();
		}, error -> {
			status(error, true);
			Toasts.error("Texture pack not imported", error);
			nextImport();
		});
	}

	private void nextImport() {
		Path next = importQueue.poll();
		if (next != null) importPack(next);
	}

	private void removePack(String name) {
		if (!("pack:" + name).equals(confirm)) {
			confirm = "pack:" + name;
			return;
		}
		confirm = null;
		PackManager.remove(name);
		status("Removed \"" + name + "\" and deleted Hollow's copy of it.", false);
	}

	// ------------------------------------------------------------------------------------------------ presets

	private void presetsToolbar(GuiGraphicsExtractor g, int mx, int my, float a) {
		int boxW = Math.min(160, toolbar.w() / 2);
		Rect box = new Rect(toolbar.x(), toolbar.y(), boxW, 16);
		boolean focused = presetName.isFocused();
		Draw.roundRect(g, box, 3, Theme.fade(focused ? Theme.CARD_HOVER : Theme.CARD, a));
		Draw.roundOutline(g, box, 3, Theme.fade(focused ? Theme.accent() : Theme.STROKE, a));
		presetName.setX(box.x() + 5);
		presetName.setY(box.y() + 4);
		presetName.setWidth(boxW - 10);
		if (a > 0.5f) presetName.extractRenderState(g, mx, my, 0);
		String save = "Save current";
		button(g, new Rect(toolbar.x() + boxW + 8, toolbar.y(), buttonW(save), 16), save, UiButton.Style.PRIMARY, mx, my, a, false, this::savePreset);
	}

	private void savePreset() {
		String name = presetName.getValue().trim();
		if (name.isEmpty()) {
			status("Type a name for the preset first.", true);
			return;
		}
		boolean existed = HollowClient.styles().findPreset(name) != null;
		if (!existed && HollowClient.styles().presets.size() >= dev.telmuad.hollowclient.config.StylesConfig.MAX_PRESETS) {
			status("You have the maximum number of presets. Delete one first.", true);
			return;
		}
		Preset p = PresetManager.save(name);
		presetName.setValue("");
		status((existed ? "Updated" : "Saved") + " preset \"" + p.name() + "\".", false);
	}

	private int presetsList(GuiGraphicsExtractor g, int top, int mx, int my, float a) {
		List<Preset> presets = new ArrayList<>(HollowClient.styles().presets);
		int y = top;
		if (presets.isEmpty()) {
			Text.wrapped(g, font, "No presets yet. Set up a cape, hat, wings and texture pack, type a name above and click Save current. Then switch between looks with one click.",
					list.x() + 8, y + 8, list.w() - 16, 4, Theme.fade(Theme.TEXT_FAINT, a));
			return 60;
		}
		for (Preset p : presets) {
			Rect r = new Rect(list.x(), y, list.w() - 6, 30);
			if (r.bottom() >= list.y() && r.y() <= list.bottom()) {
				float h = anim("preset" + p.name(), mouseIn(r, mx, my, true));
				Draw.roundRect(g, r, 5, Theme.fade(Theme.mix(Theme.CARD, Theme.CARD_HOVER, h), a));
				Draw.roundOutline(g, r, 5, Theme.fade(Theme.STROKE, a));
				int bx = r.right() - 6;
				String del = ("preset:" + p.name()).equals(confirm) ? "Sure?" : "Delete";
				bx -= buttonW(del);
				button(g, new Rect(bx, r.y() + 7, buttonW(del), 16), del, UiButton.Style.DANGER, mx, my, a, true, () -> {
					if (("preset:" + p.name()).equals(confirm)) {
						confirm = null;
						PresetManager.delete(p.name());
						status("Deleted preset \"" + p.name() + "\".", false);
					} else {
						confirm = "preset:" + p.name();
					}
				});
				bx -= buttonW("Rename") + 4;
				button(g, new Rect(bx, r.y() + 7, buttonW("Rename"), 16), "Rename", UiButton.Style.NORMAL, mx, my, a, true, () -> {
					String to = presetName.getValue().trim();
					if (to.isEmpty()) {
						status("Type the new name in the box above, then click Rename.", true);
						return;
					}
					String error = PresetManager.rename(p.name(), to);
					if (error != null) status(error, true);
					else {
						presetName.setValue("");
						status("Renamed to \"" + Preset.cleanName(to) + "\".", false);
					}
				});
				bx -= buttonW("Apply") + 4;
				button(g, new Rect(bx, r.y() + 7, buttonW("Apply"), 16), "Apply", UiButton.Style.PRIMARY, mx, my, a, true, () -> {
					String warning = PresetManager.apply(p);
					if (warning != null) {
						status(warning, true);
						Toasts.warning("Preset applied with a warning", warning);
					} else {
						status("Applied \"" + p.name() + "\".", false);
						Toasts.success("Preset applied", p.name());
					}
				});
				int textW = bx - 6 - (r.x() + 8);
				Ui.draw(g, font, Text.fit(font, p.name(), textW), r.x() + 8, r.y() + 5, Theme.fade(Theme.TEXT, a));
				Ui.draw(g, font, Text.fit(font, PresetManager.summary(p), textW), r.x() + 8, r.y() + 17, Theme.fade(Theme.TEXT_FAINT, a));
			}
			y += 34;
		}
		return y - top;
	}

	// ------------------------------------------------------------------------------------------------ input

	private float maxScroll() {
		return Math.max(0, contentHeight - list.h());
	}

	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (picker != null) {
			g.nextStratum();
			picker.render(g, font, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		lastClickX = mx;
		lastClickY = my;
		if (picker != null) {
			if (!picker.mouseClicked(mx, my)) picker = null;
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && previewArea.contains(mx, my)) {
			draggingPreview = true;
			previewDragX = mx;
			lastPreviewDrag = Util.getMillis();
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (h.rect.contains(mx, my) && (!h.inList || list.contains(mx, my))) {
					String before = confirm;
					h.action.run();
					// Any click that isn't the second half of a confirmation clears the pending confirmation.
					if (before != null && before.equals(confirm)) confirm = null;
					return true;
				}
			}
			confirm = null;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (picker != null) return picker.mouseDragged(event.x(), event.y());
		if (activeSlider != null && activeSlider.drag(event.x())) return true;
		if (draggingPreview) {
			previewYawTarget += (float) (event.x() - previewDragX) * 2.2f;
			previewDragX = event.x();
			lastPreviewDrag = Util.getMillis();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (picker != null) return picker.mouseReleased();
		draggingPreview = false;
		if (activeSlider != null) {
			activeSlider.release();
			activeSlider = null;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (picker != null) return true;
		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - sy * 24));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (picker != null) {
			if (event.isEscape()) picker = null;
			else picker.keyPressed(event.key());
			return true;
		}
		if (presetName.isFocused() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			savePreset();
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
	public void onFilesDrop(List<Path> paths) {
		for (Path p : paths) {
			String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
			if (name.endsWith(".png") || name.endsWith(".gif")) {
				if (tab != Tab.CAPES) switchTab(Tab.CAPES);
				importCape(p);
			} else if (name.endsWith(".zip") || Files.isDirectory(p)) {
				if (tab != Tab.PACKS) switchTab(Tab.PACKS);
				importPack(p);
			} else {
				status("Drop a .png or .gif (cape), a .zip or a folder (texture pack).", true);
			}
		}
	}

	private static Path downloadsDir() {
		Path home = Path.of(System.getProperty("user.home"));
		Path downloads = home.resolve("Downloads");
		return Files.isDirectory(downloads) ? downloads : home;
	}
}
