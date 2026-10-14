package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.gui.widget.UiToggle;
import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Right Shift menu: categories on the left, a card per module with a toggle and a settings button,
 * a search field, and a button for the HUD editor. The Styles category shows four big tiles instead of cards.
 */
public final class HollowMenuScreen extends HollowScreen {
	private static Category lastCategory = Category.HUD;
	private static String lastQuery = "";

	private static final int CARD_H = 46;
	private static final int TILE_H = 58;

	private Category category = lastCategory;
	private String query = lastQuery;
	private Rect panel = Rect.EMPTY;
	private Rect header = Rect.EMPTY;
	private Rect rail = Rect.EMPTY;
	private Rect grid = Rect.EMPTY;
	private Rect section = Rect.EMPTY;
	private Rect footer = Rect.EMPTY;
	private Rect searchBox = Rect.EMPTY;
	private boolean railLabels = true;
	private int cols = 2;
	private int cardW = 150;
	private int gap = 6;
	private EditBox search;
	private final UiButton editHud = new UiButton("Edit HUD", this::openHudEditor).style(UiButton.Style.PRIMARY);
	private final Map<Object, Float> hover = new HashMap<>();
	private final Map<Module, UiToggle> toggles = new HashMap<>();
	private float scroll;
	private float scrollTarget;

	public HollowMenuScreen(Screen parent) {
		super(Component.literal("Hollow Client"), parent);
	}

	public HollowMenuScreen() {
		this(null);
	}

	/** Open the menu on a given category (clears the search). */
	public static HollowMenuScreen at(Category category) {
		lastCategory = category;
		lastQuery = "";
		return new HollowMenuScreen();
	}

	@Override
	protected void init() {
		super.init();
		int margin = width < 420 ? 4 : 14;
		int pw = Math.min(width - 2 * margin, 620);
		int ph = Math.min(height - 2 * margin, 370);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		railLabels = pw >= 440;
		header = new Rect(panel.x(), panel.y(), pw, 30);
		footer = new Rect(panel.x(), panel.bottom() - 18, pw, 18);
		int railW = railLabels ? 108 : 28;
		rail = new Rect(panel.x() + 8, header.bottom() + 2, railW, footer.y() - header.bottom() - 6);
		section = new Rect(rail.right() + 8, header.bottom() + 2, panel.right() - 8 - rail.right() - 8, 14);
		grid = new Rect(section.x(), section.bottom() + 2, section.w(), footer.y() - section.bottom() - 6);
		gap = 6;
		cols = Math.max(1, (grid.w() + gap) / (140 + gap));
		cardW = (grid.w() - 6 - gap * (cols - 1)) / cols;

		int bw = editHud.preferredWidth(font);
		editHud.bounds(new Rect(header.right() - 10 - bw, header.y() + 8, bw, 16));
		int searchW = Math.max(70, Math.min(170, editHud.bounds().x() - 10 - (header.x() + 120)));
		searchBox = new Rect(editHud.bounds().x() - 8 - searchW, header.y() + 8, searchW, 16);
		search = new EditBox(font, searchBox.x() + 14, searchBox.y() + 4, searchBox.w() - 18, 10, Component.literal("Search"));
		search.setBordered(false);
		search.setMaxLength(40);
		search.setTextColor(Theme.TEXT);
		search.setHint(Component.literal("Search modules").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(value -> {
			String q = value.trim().toLowerCase(Locale.ROOT);
			if (!q.equals(query)) {
				query = q;
				lastQuery = q;
				scrollTarget = 0;
			}
		});
		addWidget(search);
	}

	private boolean searching() {
		return !query.isEmpty();
	}

	private List<Module> visibleModules() {
		List<Module> out = new ArrayList<>();
		for (Module m : HollowClient.modules().all()) {
			if (searching()) {
				if (m.name().toLowerCase(Locale.ROOT).contains(query) || m.description().toLowerCase(Locale.ROOT).contains(query)) out.add(m);
			} else if (m.category() == category) {
				out.add(m);
			}
		}
		return out;
	}

	private boolean showingTiles() {
		return !searching() && category == Category.STYLES;
	}

	private int contentHeight() {
		if (showingTiles()) return 2 * (TILE_H + gap);
		int n = visibleModules().size();
		int rows = (n + cols - 1) / cols;
		return rows * (CARD_H + gap);
	}

	private float maxScroll() {
		return Math.max(0, contentHeight() - grid.h());
	}

	// ---------------------------------------------------------------------------------------------------- render

	@Override
	protected void renderContent(GuiGraphics g, int mx, int my, float a) {
		Draw.roundRect(g, panel.x() + 3, panel.y() + 4, panel.w(), panel.h(), 8, Theme.fade(0x55000000, a));
		Draw.roundRect(g, panel, 8, Theme.fade(Theme.PANEL, a));
		Draw.roundOutline(g, panel, 8, Theme.fade(Theme.STROKE, a));
		renderHeader(g, mx, my, a);
		renderRail(g, mx, my, a);
		renderSection(g, a);
		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		g.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
		if (showingTiles()) renderTiles(g, mx, my, a);
		else renderCards(g, mx, my, a);
		g.disableScissor();
		if (maxScroll() > 0) {
			int barH = Math.max(16, grid.h() * grid.h() / contentHeight());
			int barY = grid.y() + Math.round((grid.h() - barH) * (scroll / maxScroll()));
			Draw.roundRect(g, grid.right() - 2, barY, 2, barH, 1, Theme.fade(Theme.STROKE_STRONG, a));
		}
		renderFooter(g, a);
	}

	private void renderHeader(GuiGraphics g, int mx, int my, float a) {
		int lx = header.x() + 10;
		int ly = header.y() + 9;
		Draw.logo(g, lx, ly - 1, 13, Theme.fade(Theme.ACCENT, a));
		Component title = Component.literal("HOLLOW").withStyle(s -> s.withBold(true))
				.append(Component.literal(" CLIENT").withStyle(s -> s.withBold(false).withColor(Theme.TEXT_DIM & 0xFFFFFF)));
		g.drawString(font, title, lx + 18, ly + 2, Theme.fade(Theme.TEXT, a), false);

		boolean focused = search.isFocused();
		Draw.roundRect(g, searchBox, 3, Theme.fade(focused ? Theme.CARD_HOVER : Theme.CARD, a));
		Draw.roundOutline(g, searchBox, 3, Theme.fade(focused ? Theme.ACCENT : Theme.STROKE, a));
		drawMagnifier(g, searchBox.x() + 4, searchBox.y() + 4, Theme.fade(Theme.TEXT_DIM, a));
		if (a > 0.5f) search.render(g, mx, my, 0);
		editHud.render(g, font, mx, my, dt, a);
		g.fill(panel.x() + 8, header.bottom() - 2, panel.right() - 8, header.bottom() - 1, Theme.fade(Theme.STROKE, a));
	}

	private static void drawMagnifier(GuiGraphics g, int x, int y, int color) {
		Draw.roundOutline(g, x, y, 6, 6, 3, color);
		g.fill(x + 5, y + 5, x + 6, y + 6, color);
		g.fill(x + 6, y + 6, x + 8, y + 8, color);
	}

	private void renderRail(GuiGraphics g, int mx, int my, float a) {
		int y = rail.y() + 2;
		for (Category c : Category.values()) {
			Rect r = new Rect(rail.x(), y, rail.w(), 24);
			boolean selected = !searching() && c == category;
			float h = animate(c, r.contains(mx, my));
			int bg = selected ? Theme.CARD_ON : Theme.mix(0x00000000, Theme.CARD, h);
			Draw.roundRect(g, r, 4, Theme.fade(bg, a));
			if (selected) Draw.roundRect(g, r.x(), r.y() + 5, 2, 14, 1, Theme.fade(Theme.ACCENT, a));
			if (a > 0.6f) g.renderItem(iconFor(c), r.x() + 6, r.y() + 4);
			if (railLabels) {
				g.drawString(font, c.label(), r.x() + 27, r.y() + 8, Theme.fade(selected ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, h), a), false);
			} else if (r.contains(mx, my)) {
				g.setTooltipForNextFrame(font, Component.literal(c.label()), mx, my);
			}
			y += 26;
		}
	}

	private void renderSection(GuiGraphics g, float a) {
		String title = searching() ? "Search results" : category.label();
		g.drawString(font, Component.literal(title).withStyle(st -> st.withBold(true)), section.x() + 1, section.y() + 3, Theme.fade(Theme.TEXT, a), false);
		String count;
		if (showingTiles()) {
			count = "Only you can see your styles";
		} else {
			List<Module> mods = visibleModules();
			long on = mods.stream().filter(Module::isEnabled).count();
			count = on + " of " + mods.size() + " on";
		}
		g.drawString(font, count, section.right() - 4 - font.width(count), section.y() + 3, Theme.fade(Theme.TEXT_FAINT, a), false);
	}

	private static ItemStack iconFor(Category c) {
		return switch (c) {
			case HUD -> new ItemStack(Items.COMPASS);
			case VISUAL -> new ItemStack(Items.ENDER_EYE);
			case QOL -> new ItemStack(Items.FEATHER);
			case STYLES -> new ItemStack(Items.LEATHER_CHESTPLATE);
		};
	}

	private void renderCards(GuiGraphics g, int mx, int my, float a) {
		List<Module> mods = visibleModules();
		boolean inGrid = grid.contains(mx, my);
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			Rect r = cardRect(i);
			if (r.bottom() < grid.y() || r.y() > grid.bottom()) continue;
			boolean hovered = inGrid && r.contains(mx, my);
			float h = animate(m, hovered);
			int bg = m.isEnabled() ? Theme.mix(Theme.CARD_ON, 0xFF2A2440, h) : Theme.mix(Theme.CARD, Theme.CARD_HOVER, h);
			Draw.roundRect(g, r, 5, Theme.fade(bg, a));
			Draw.roundOutline(g, r, 5, Theme.fade(Theme.mix(m.isEnabled() ? 0x558B6CFF : Theme.STROKE, Theme.ACCENT, h * 0.7f), a));

			int tx = r.right() - 8 - UiToggle.W;
			int ty = r.y() + 7;
			toggles.computeIfAbsent(m, k -> new UiToggle()).render(g, tx, ty, m.isEnabled(), inGrid && UiToggle.hit(tx, ty, mx, my), dt, a);
			g.drawString(font, Text.fit(font, m.name(), tx - r.x() - 14), r.x() + 8, r.y() + 8, Theme.fade(Theme.TEXT, a), false);
			Text.wrapped(g, font, m.description(), r.x() + 8, r.y() + 21, r.w() - 30, 2, Theme.fade(Theme.TEXT_DIM, a));

			Rect gear = gearRect(r);
			boolean gh = inGrid && gear.contains(mx, my);
			if (gh) Draw.roundRect(g, gear, 3, Theme.fade(Theme.CARD_HOVER, a));
			Draw.settingsIcon(g, gear.x() + 4, gear.y() + 4, Theme.fade(gh ? Theme.TEXT : Theme.TEXT_FAINT, a));
			if (searching()) {
				String cat = m.category().label();
				g.drawString(font, cat, r.right() - 8 - font.width(cat), r.y() + 21 + 10 * 2 - 9, Theme.fade(Theme.TEXT_FAINT, a), false);
			}
		}
		if (mods.isEmpty()) {
			g.drawCenteredString(font, searching() ? "No modules match \"" + query + "\"" : "Nothing here yet.", grid.centerX(), grid.y() + 30, Theme.fade(Theme.TEXT_FAINT, a));
		}
	}

	private Rect cardRect(int i) {
		int col = i % cols;
		int row = i / cols;
		int x = grid.x() + col * (cardW + gap);
		int y = grid.y() + row * (CARD_H + gap) - Math.round(scroll);
		return new Rect(x, y, cardW, CARD_H);
	}

	private static Rect gearRect(Rect card) {
		return new Rect(card.right() - 18, card.bottom() - 17, 14, 14);
	}

	// Styles tiles

	private record Tile(StylesScreen.Tab tab, String title, String subtitle, ItemStack icon) {
	}

	private List<Tile> tiles() {
		return List.of(
				new Tile(StylesScreen.Tab.CAPES, "Capes", StylesScreen.capeSummary(), new ItemStack(Items.WHITE_BANNER)),
				new Tile(StylesScreen.Tab.COSMETICS, "Cosmetics", StylesScreen.cosmeticsSummary(), new ItemStack(Items.LEATHER_HELMET)),
				new Tile(StylesScreen.Tab.PACKS, "Texture Packs", StylesScreen.packSummary(), new ItemStack(Items.PAINTING)),
				new Tile(StylesScreen.Tab.PRESETS, "Presets", StylesScreen.presetSummary(), new ItemStack(Items.WRITABLE_BOOK)));
	}

	private Rect tileRect(int i) {
		int tcols = grid.w() >= 260 ? 2 : 1;
		int tw = (grid.w() - 6 - gap * (tcols - 1)) / tcols;
		int col = i % tcols;
		int row = i / tcols;
		return new Rect(grid.x() + col * (tw + gap), grid.y() + row * (TILE_H + gap) - Math.round(scroll), tw, TILE_H);
	}

	private void renderTiles(GuiGraphics g, int mx, int my, float a) {
		List<Tile> list = tiles();
		boolean inGrid = grid.contains(mx, my);
		for (int i = 0; i < list.size(); i++) {
			Tile t = list.get(i);
			Rect r = tileRect(i);
			float h = animate(t.tab, inGrid && r.contains(mx, my));
			Draw.roundRect(g, r, 6, Theme.fade(Theme.mix(Theme.CARD, Theme.CARD_HOVER, h), a));
			Draw.roundOutline(g, r, 6, Theme.fade(Theme.mix(Theme.STROKE, Theme.ACCENT, h * 0.8f), a));
			Draw.roundRect(g, r.x() + 8, r.y() + 8, 28, 28, 5, Theme.fade(Theme.CARD_ON, a));
			if (a > 0.6f) g.renderItem(t.icon, r.x() + 14, r.y() + 14);
			g.drawString(font, t.title, r.x() + 44, r.y() + 12, Theme.fade(Theme.TEXT, a), false);
			Text.wrapped(g, font, t.subtitle, r.x() + 44, r.y() + 25, r.w() - 52, 2, Theme.fade(Theme.TEXT_DIM, a));
			g.drawString(font, "Open >", r.right() - 8 - font.width("Open >"), r.bottom() - 13, Theme.fade(Theme.mix(Theme.TEXT_FAINT, Theme.ACCENT, h), a), false);
		}
	}

	private void renderFooter(GuiGraphics g, float a) {
		String hint = "Click a card to toggle · Right-click or the slider icon for settings · Right Shift closes";
		g.drawString(font, Text.fit(font, hint, footer.w() - 80), footer.x() + 10, footer.y() + 4, Theme.fade(Theme.TEXT_FAINT, a), false);
		String version = "v" + FabricLoader.getInstance().getModContainer(HollowClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		g.drawString(font, version, footer.right() - 10 - font.width(version), footer.y() + 4, Theme.fade(Theme.TEXT_FAINT, a), false);
	}

	private float animate(Object key, boolean on) {
		float v = Anim.approach(hover.getOrDefault(key, 0f), on ? 1f : 0f, dt, 16f);
		hover.put(key, v);
		return v;
	}

	// ---------------------------------------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		if (left && editHud.click(mx, my)) return true;

		int y = rail.y() + 2;
		for (Category c : Category.values()) {
			if (new Rect(rail.x(), y, rail.w(), 24).contains(mx, my)) {
				UiButton.playClick();
				category = c;
				lastCategory = c;
				scroll = scrollTarget = 0;
				if (searching()) search.setValue("");
				search.setFocused(false);
				return true;
			}
			y += 26;
		}

		if (grid.contains(mx, my)) {
			if (showingTiles()) {
				List<Tile> list = tiles();
				for (int i = 0; i < list.size(); i++) {
					if (tileRect(i).contains(mx, my)) {
						UiButton.playClick();
						open(new StylesScreen(list.get(i).tab, this));
						return true;
					}
				}
			} else {
				List<Module> mods = visibleModules();
				for (int i = 0; i < mods.size(); i++) {
					Rect r = cardRect(i);
					if (!r.contains(mx, my)) continue;
					Module m = mods.get(i);
					UiButton.playClick();
					if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || (left && gearRect(r).contains(mx, my))) {
						open(settingsFor(m, this));
					} else if (left) {
						m.toggle();
					}
					return true;
				}
			}
		}
		boolean handled = super.mouseClicked(event, doubleClick);
		if (!searchBox.contains(mx, my)) search.setFocused(false);
		return handled;
	}

	/** The settings screen for a module, with a "Move on screen" button for HUD modules. */
	public static ModuleSettingsScreen settingsFor(Module m, Screen parent) {
		ModuleSettingsScreen s = new ModuleSettingsScreen(m, parent);
		if (m.hudPosition() != null) s.withButton("Move on screen", screen -> screen.openEditor());
		return s;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - sy * 24));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search.isFocused()) {
			if (event.isEscape()) {
				search.setFocused(false);
				return true;
			}
			return super.keyPressed(event);
		}
		if (Keybinds.menuKey() != null && Keybinds.menuKey().matches(event)) {
			onClose();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_SLASH || (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_F)) {
			setFocused(search);
			search.setFocused(true);
			return true;
		}
		return super.keyPressed(event);
	}

	private void openHudEditor() {
		open(new HudEditorScreen(this));
	}
}
