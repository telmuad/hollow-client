package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.gui.widget.UiToggle;
import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Right Shift menu: categories on the left, a card per module with a toggle and a settings button,
 * a live search field, and a button for the HUD editor. The Styles category shows four big tiles instead of cards.
 * <p>
 * Motion: cards slide in one after another, glide to their new place while you search, lean toward the mouse
 * with a moving shine, glow on hover and ripple on click; the category marker springs between categories.
 * All of it follows the Menu Effects module.
 */
public final class HollowMenuScreen extends HollowScreen {
	private static Category lastCategory = Category.HUD;
	private static String lastQuery = "";

	private static final int CARD_H = 46;
	private static final int TILE_H = 58;
	private static final int RAIL_ROW = 26;
	private static final long STAGGER_MS = 28;
	private static final float APPEAR_MS = 300f;

	private Category category = lastCategory;
	private String query = lastQuery;
	private Rect panel = Rect.EMPTY;
	private Rect header = Rect.EMPTY;
	private Rect rail = Rect.EMPTY;
	private Rect grid = Rect.EMPTY;
	private Rect section = Rect.EMPTY;
	private Rect footer = Rect.EMPTY;
	private Rect searchBox = Rect.EMPTY;
	private Rect clearSearch = Rect.EMPTY;
	private boolean railLabels = true;
	private int cols = 2;
	private int cardW = 150;
	private int gap = 6;
	private EditBox search;
	private final UiButton editHud = new UiButton("Edit HUD", this::openHudEditor).style(UiButton.Style.PRIMARY);
	private final Map<Object, Float> hover = new HashMap<>();
	private final Map<Module, UiToggle> toggles = new HashMap<>();
	private final Map<Object, CardAnim> anims = new HashMap<>();
	private Set<Object> shownLastFrame = new HashSet<>();
	private long listShownAt = Util.getMillis();
	private final Spring railMarker = Spring.bouncy(-1f);
	private float railMarkerAlpha;
	private float searchFocus;
	private float scroll;
	private float scrollTarget;

	/** Per card/tile animation state. */
	private static final class CardAnim {
		float x;
		float y;
		long appearAt;
		float hover;
		float tiltX;
		float tiltY;
		float press;
	}

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
		clearSearch = new Rect(searchBox.right() - 13, searchBox.y() + 3, 10, 10);
		search = new EditBox(font, searchBox.x() + 14, searchBox.y() + 4, searchBox.w() - 30, 10, Component.literal("Search"));
		search.setBordered(false);
		search.setMaxLength(40);
		search.setTextColor(Theme.TEXT);
		search.setHint(Ui.text("Search modules").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(value -> {
			String q = value.trim().toLowerCase(Locale.ROOT);
			if (!q.equals(query)) {
				query = q;
				lastQuery = q;
				scrollTarget = 0;
				listShownAt = Util.getMillis();
			}
		});
		addWidget(search);
		anims.clear();
		shownLastFrame = new HashSet<>();
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

	private void switchCategory(Category c) {
		category = c;
		lastCategory = c;
		scroll = scrollTarget = 0;
		listShownAt = Util.getMillis();
		if (searching()) search.setValue("");
		search.setFocused(false);
	}

	// ---------------------------------------------------------------------------------------------------- render

	@Override
	protected void renderContent(GuiGraphics g, int mx, int my, float a) {
		drawPanel(g, panel, 8, a);
		renderHeader(g, mx, my, a);
		renderRail(g, mx, my, a);
		renderSection(g, a);
		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		g.enableScissor(grid.x() - 1, grid.y(), grid.right() + 1, grid.bottom());
		Set<Object> shown = new HashSet<>();
		if (showingTiles()) renderTiles(g, mx, my, a, shown);
		else renderCards(g, mx, my, a, shown);
		g.disableScissor();
		shownLastFrame = shown;
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
		Fx.sigil(g, lx - 2, ly - 4, 18, a, true);
		int tx = lx + 20;
		tx += Fx.gradientBold(g, font, "HOLLOW", tx, ly + 2, a);
		Ui.draw(g, font, " CLIENT", tx, ly + 2, Theme.fade(Theme.TEXT_DIM, a));

		boolean focused = search.isFocused();
		searchFocus = Anim.approach(searchFocus, focused ? 1f : 0f, dt, 14f);
		int rad = Theme.radius(4);
		if (searchFocus > 0.01f) {
			Fx.glow(g, searchBox.x(), searchBox.y(), searchBox.w(), searchBox.h(), 6,
					Theme.fade(Theme.accent(), a * searchFocus * (0.2f + 0.4f * Theme.glow())));
		}
		Draw.roundRect(g, searchBox, rad, Theme.fade(Theme.mix(Theme.CARD, Theme.CARD_HOVER, searchFocus), a));
		Draw.roundOutline(g, searchBox, rad, Theme.fade(Theme.mix(Theme.STROKE, Theme.accent(), searchFocus), a));
		drawMagnifier(g, searchBox.x() + 4, searchBox.y() + 4, Theme.fade(Theme.mix(Theme.TEXT_DIM, Theme.accent(), searchFocus), a));
		if (a > 0.5f) search.render(g, mx, my, 0);
		if (searching()) {
			boolean ch = clearSearch.contains(mx, my);
			Draw.roundRect(g, clearSearch, 2, Theme.fade(ch ? Theme.CARD_HOVER : 0x00000000, a));
			Ui.center(g, font, "x", clearSearch.centerX() + 1, clearSearch.y() + 1, Theme.fade(ch ? Theme.TEXT : Theme.TEXT_FAINT, a));
		}
		editHud.render(g, font, mx, my, dt, a);
		g.fill(panel.x() + 8, header.bottom() - 2, panel.right() - 8, header.bottom() - 1, Theme.fade(Theme.STROKE, a));
	}

	private static void drawMagnifier(GuiGraphics g, int x, int y, int color) {
		Draw.roundOutline(g, x, y, 6, 6, 3, color);
		g.fill(x + 5, y + 5, x + 6, y + 6, color);
		g.fill(x + 6, y + 6, x + 8, y + 8, color);
	}

	private void renderRail(GuiGraphics g, int mx, int my, float a) {
		Category[] cats = Category.values();
		float targetY = rail.y() + 2 + category.ordinal() * RAIL_ROW;
		if (railMarker.value() < 0) railMarker.snap(targetY);
		railMarker.target(targetY);
		float markerY = fxAmount(m -> m.entrance) > 0 ? railMarker.update(dt * Theme.speed()) : railMarker.snap(targetY).value();
		railMarkerAlpha = Anim.approach(railMarkerAlpha, searching() ? 0f : 1f, dt, 12f);
		if (railMarkerAlpha > 0.01f) {
			Rect marker = new Rect(rail.x(), Math.round(markerY), rail.w(), 24);
			float ma = a * railMarkerAlpha;
			Draw.roundRect(g, marker, Theme.radius(5), Theme.fade(Theme.cardOn(), ma));
			Fx.glow(g, marker.x(), marker.y() + 5, 2, 14, 4, Theme.fade(Theme.accent(), ma * Theme.glow() * 0.8f));
			Draw.roundRect(g, marker.x(), marker.y() + 5, 2, 14, 1, Theme.fade(Theme.accent(), ma));
		}
		int y = rail.y() + 2;
		for (Category c : cats) {
			Rect r = new Rect(rail.x(), y, rail.w(), 24);
			boolean selected = !searching() && c == category;
			float h = animate(c, r.contains(mx, my));
			if (!selected) Draw.roundRect(g, r, Theme.radius(5), Theme.fade(Theme.mix(0x00000000, Theme.CARD, h), a));
			if (a > 0.6f) {
				g.pose().pushMatrix();
				float bump = 1f + 0.08f * h;
				g.pose().translate(r.x() + 14, r.y() + 12);
				g.pose().scale(bump, bump);
				g.renderItem(iconFor(c), -8, -8);
				g.pose().popMatrix();
			}
			if (railLabels) {
				int color = selected ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, h);
				Ui.draw(g, font, c.label(), r.x() + 27 + Math.round(h * 2), r.y() + 8, Theme.fade(color, a));
			} else if (r.contains(mx, my)) {
				g.setTooltipForNextFrame(font, Component.literal(c.label()), mx, my);
			}
			y += RAIL_ROW;
		}
	}

	private void renderSection(GuiGraphics g, float a) {
		String title = searching() ? "Search results" : category.label();
		float t = Anim.easeOutCubic((Util.getMillis() - listShownAt) * Theme.speed() / APPEAR_MS);
		int slide = fxAmount(m -> m.entrance) > 0 ? Math.round((1f - t) * 8) : 0;
		Ui.draw(g, font, Ui.bold(title), section.x() + 1 + slide, section.y() + 3, Theme.fade(Theme.TEXT, a * (0.4f + 0.6f * t)));
		String count;
		if (showingTiles()) {
			count = "Only you can see your styles";
		} else {
			List<Module> mods = visibleModules();
			long on = mods.stream().filter(m -> m.toggleable() && m.isEnabled()).count();
			long toggleable = mods.stream().filter(Module::toggleable).count();
			count = searching() ? mods.size() + (mods.size() == 1 ? " match" : " matches") : on + " of " + toggleable + " on";
		}
		Ui.draw(g, font, count, section.right() - 4 - Ui.width(font, count), section.y() + 3, Theme.fade(Theme.TEXT_FAINT, a));
	}

	private static ItemStack iconFor(Category c) {
		return switch (c) {
			case HUD -> new ItemStack(Items.COMPASS);
			case INTERFACE -> new ItemStack(Items.ITEM_FRAME);
			case VISUAL -> new ItemStack(Items.ENDER_EYE);
			case QOL -> new ItemStack(Items.FEATHER);
			case STYLES -> new ItemStack(Items.LEATHER_CHESTPLATE);
			case CLIENT -> new ItemStack(Items.AMETHYST_SHARD);
		};
	}

	// ---------------------------------------------------------------------------------------------------- cards

	/** Animation state for one card, created when it first appears (staggered by its place in the list). */
	private CardAnim anim(Object key, int index, Rect scrolled, Set<Object> shown) {
		Rect target = scrolled.offset(0, Math.round(scroll));
		shown.add(key);
		CardAnim c = anims.get(key);
		if (c == null || !shownLastFrame.contains(key)) {
			c = new CardAnim();
			c.x = target.x();
			c.y = target.y();
			c.appearAt = listShownAt + Math.min(12, index) * STAGGER_MS;
			anims.put(key, c);
		} else {
			// glide to the new place (search results reflow smoothly)
			c.x = Anim.approach(c.x, target.x(), dt, 16f);
			c.y = Anim.approach(c.y, target.y(), dt, 16f);
		}
		return c;
	}

	/** 0..1 appear progress of a card. */
	private float appear(CardAnim c) {
		if (fxAmount(m -> m.entrance) <= 0f) return 1f;
		return Anim.clamp01((Util.getMillis() - c.appearAt) * Theme.speed() / APPEAR_MS);
	}

	/**
	 * Push the transform for a card: slide up while appearing, scale a touch on hover and press, and lean toward
	 * the mouse (a shear that reads as a 3D tilt). Pop it with {@code g.pose().popMatrix()}.
	 */
	private void pushCardTransform(GuiGraphics g, CardAnim c, Rect r, boolean hovered, int mx, int my, float appear) {
		float tilt = fxAmount(m -> m.tilt);
		float rx = 0f;
		float ry = 0f;
		if (hovered && tilt > 0f) {
			rx = Math.max(-1f, Math.min(1f, (mx - r.centerX()) / (r.w() / 2f)));
			ry = Math.max(-1f, Math.min(1f, (my - r.centerY()) / (r.h() / 2f)));
		}
		c.tiltX = Anim.approach(c.tiltX, rx, dt, 10f);
		c.tiltY = Anim.approach(c.tiltY, ry, dt, 10f);
		c.press = Anim.approach(c.press, 0f, dt, 9f);
		float rise = (1f - easeOutBack(appear)) * 10f;
		float scale = (0.94f + 0.06f * Anim.easeOutCubic(appear)) * (1f + 0.02f * c.hover * Math.max(tilt, 0.5f)) * (1f - 0.035f * c.press);
		g.pose().pushMatrix();
		g.pose().translate(r.x() + r.w() / 2f, r.y() + r.h() / 2f + rise);
		g.pose().scale(scale, scale);
		float k = 0.035f * tilt;
		if (k > 0f) g.pose().mul(new Matrix3x2f(1f, c.tiltX * k * 0.6f, -c.tiltY * k, 1f, 0f, 0f));
		g.pose().translate(-r.w() / 2f, -r.h() / 2f);
	}

	private static float easeOutBack(float t) {
		t = Anim.clamp01(t);
		float c1 = 1.4f;
		float u = t - 1f;
		return 1f + (c1 + 1f) * u * u * u + c1 * u * u;
	}

	/** The moving shine on a tilted card: a soft light that follows the mouse. Drawn in card-local coordinates. */
	private void shine(GuiGraphics g, CardAnim c, int w, int h, float a) {
		float tilt = fxAmount(m -> m.tilt);
		if (tilt <= 0f || c.hover <= 0.01f) return;
		float sx = w / 2f + c.tiltX * w / 2f;
		float sy = h / 2f + c.tiltY * h / 2f;
		g.enableScissor(0, 0, w, h);
		Fx.dot(g, sx, sy, Math.max(w, h) * 0.55f, ColorUtil.fade(0xFFFFFFFF, a * c.hover * tilt * 0.13f));
		g.disableScissor();
	}

	private void renderCards(GuiGraphics g, int mx, int my, float a, Set<Object> shown) {
		List<Module> mods = visibleModules();
		boolean inGrid = grid.contains(mx, my);
		float borders = fxAmount(m -> m.borders);
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			Rect target = cardRect(i);
			CardAnim c = anim(m, i, target, shown);
			Rect r = animatedRect(m, target);
			if (r.bottom() < grid.y() - 12 || r.y() > grid.bottom() + 12) continue;
			boolean hovered = inGrid && r.contains(mx, my);
			c.hover = Anim.approach(c.hover, hovered ? 1f : 0f, dt, 14f);
			float ap = appear(c);
			float ca = a * Anim.easeOutCubic(ap);
			if (ca <= 0.01f) continue;

			hoverGlow(g, r, c.hover, ca);
			pushCardTransform(g, c, r, hovered, mx, my, ap);
			int w = r.w();
			int h = r.h();
			int rad = Theme.radius(6);
			boolean lit = m.isEnabled() && m.toggleable();
			int bg = lit ? Theme.mix(Theme.cardOn(), Theme.mix(Theme.cardOn(), 0xFFFFFFFF, 0.06f), c.hover)
					: Theme.mix(Theme.CARD, Theme.CARD_HOVER, c.hover);
			Draw.roundRect(g, 0, 0, w, h, rad, Theme.fade(bg, ca));
			Draw.roundGradient(g, 0, 0, w, h / 2, rad, Theme.fade(0x12FFFFFF, ca), 0x00FFFFFF);
			shine(g, c, w, h, ca);
			if (lit && borders > 0f) {
				Fx.gradientOutline(g, new Rect(0, 0, w, h), rad, ca * borders * (0.45f + 0.35f * c.hover), i * 0.13f);
			} else {
				Draw.roundOutline(g, 0, 0, w, h, rad, Theme.fade(Theme.mix(Theme.STROKE, Theme.accent(), c.hover * 0.7f), ca));
			}

			int tx = w - 8 - UiToggle.W;
			int ty = 7;
			// mouse in card-local coordinates (close enough for the small hover effects inside)
			int lmx = mx - r.x();
			int lmy = my - r.y();
			if (m.toggleable()) {
				toggles.computeIfAbsent(m, k -> new UiToggle()).render(g, tx, ty, m.isEnabled(), hovered && UiToggle.hit(tx, ty, lmx, lmy), dt, ca);
			} else {
				Ui.draw(g, font, "Open >", w - 8 - Ui.width(font, "Open >") + Math.round(c.hover * 2), ty + 2, Theme.fade(Theme.mix(Theme.TEXT_FAINT, Theme.accent(), c.hover), ca));
			}
			int nameRoom = tx - 14;
			if (searching()) {
				// which category the result is from, as a small tag before the switch
				String cat = m.category().label();
				int cw = Ui.width(font, cat) + 6;
				int cx = (m.toggleable() ? tx : w - 8 - Ui.width(font, "Open >")) - 6 - cw;
				Draw.roundRect(g, cx, 7, cw, 11, 3, Theme.fade(0x30FFFFFF, ca));
				Ui.draw(g, font, cat, cx + 3, 9, Theme.fade(Theme.TEXT_DIM, ca));
				nameRoom = cx - 12;
			}
			drawName(g, m.name(), 8, 8, nameRoom, ca);
			Text.wrapped(g, font, m.description(), 8, 21, w - 30, 2, Theme.fade(Theme.TEXT_DIM, ca));

			Rect gear = gearRect(new Rect(0, 0, w, h));
			boolean gh = hovered && gear.contains(lmx, lmy);
			if (gh) Draw.roundRect(g, gear, 3, Theme.fade(Theme.CARD_HOVER, ca));
			Draw.settingsIcon(g, gear.x() + 4, gear.y() + 4, Theme.fade(gh ? Theme.TEXT : Theme.TEXT_FAINT, ca));
			g.pose().popMatrix();
		}
		if (mods.isEmpty()) {
			float t = Anim.easeOutCubic((Util.getMillis() - listShownAt) * Theme.speed() / APPEAR_MS);
			int s = 28;
			Fx.sigil(g, grid.centerX() - s / 2, grid.y() + 24, s, a * 0.5f * t, true);
			Ui.center(g, font, searching() ? "No modules match \"" + query + "\"" : "Nothing here yet.", grid.centerX(), grid.y() + 24 + s + 8, Theme.fade(Theme.TEXT_FAINT, a * t));
		}
	}

	/** The module name, with the part that matches the search lit up in the accent color. */
	private void drawName(GuiGraphics g, String name, int x, int y, int maxW, float a) {
		String shownName = Text.fit(font, name, maxW);
		int at = searching() ? shownName.toLowerCase(Locale.ROOT).indexOf(query) : -1;
		if (at < 0) {
			Ui.draw(g, font, shownName, x, y, Theme.fade(Theme.TEXT, a));
			return;
		}
		int end = Math.min(shownName.length(), at + query.length());
		String before = shownName.substring(0, at);
		String match = shownName.substring(at, end);
		String after = shownName.substring(end);
		int cx = x;
		Ui.draw(g, font, before, cx, y, Theme.fade(Theme.TEXT, a));
		cx += Ui.width(font, before);
		int mw = Ui.width(font, match);
		Draw.roundRect(g, cx - 1, y - 2, mw + 2, 12, 2, Theme.fade(Theme.accent(), a * 0.25f));
		Ui.draw(g, font, match, cx, y, Theme.fade(Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.35f), a));
		cx += mw;
		Ui.draw(g, font, after, cx, y, Theme.fade(Theme.TEXT, a));
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

	/** The card currently drawn at a point (animated position), or -1. */
	private int cardAt(List<Module> mods, double mx, double my) {
		for (int i = 0; i < mods.size(); i++) {
			if (animatedRect(mods.get(i), cardRect(i)).contains(mx, my)) return i;
		}
		return -1;
	}

	/** Where a card is drawn right now: its animated layout position, scrolled. */
	private Rect animatedRect(Object key, Rect target) {
		CardAnim c = anims.get(key);
		if (c == null) return target;
		return new Rect(Math.round(c.x), Math.round(c.y) - Math.round(scroll), target.w(), target.h());
	}

	// ---------------------------------------------------------------------------------------------------- styles tiles

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

	private void renderTiles(GuiGraphics g, int mx, int my, float a, Set<Object> shown) {
		List<Tile> list = tiles();
		boolean inGrid = grid.contains(mx, my);
		float borders = fxAmount(m -> m.borders);
		for (int i = 0; i < list.size(); i++) {
			Tile t = list.get(i);
			Rect target = tileRect(i);
			CardAnim c = anim(t.tab, i, target, shown);
			Rect r = animatedRect(t.tab, target);
			boolean hovered = inGrid && r.contains(mx, my);
			c.hover = Anim.approach(c.hover, hovered ? 1f : 0f, dt, 14f);
			float ap = appear(c);
			float ca = a * Anim.easeOutCubic(ap);
			if (ca <= 0.01f) continue;
			hoverGlow(g, r, c.hover, ca);
			pushCardTransform(g, c, r, hovered, mx, my, ap);
			int w = r.w();
			int h = r.h();
			int rad = Theme.radius(7);
			Draw.roundRect(g, 0, 0, w, h, rad, Theme.fade(Theme.mix(Theme.CARD, Theme.CARD_HOVER, c.hover), ca));
			Draw.roundGradient(g, 0, 0, w, h / 2, rad, Theme.fade(0x12FFFFFF, ca), 0x00FFFFFF);
			shine(g, c, w, h, ca);
			if (borders > 0f && c.hover > 0.01f) {
				Draw.roundOutline(g, 0, 0, w, h, rad, Theme.fade(Theme.STROKE, ca * (1f - c.hover)));
				Fx.gradientOutline(g, new Rect(0, 0, w, h), rad, ca * borders * c.hover, i * 0.2f);
			} else {
				Draw.roundOutline(g, 0, 0, w, h, rad, Theme.fade(Theme.mix(Theme.STROKE, Theme.accent(), c.hover * 0.8f), ca));
			}
			Draw.roundRect(g, 8, 8, 28, 28, Theme.radius(5), Theme.fade(Theme.cardOn(), ca));
			if (ca > 0.6f) {
				g.pose().pushMatrix();
				float bump = 1f + 0.1f * c.hover;
				g.pose().translate(22, 22);
				g.pose().scale(bump, bump);
				g.renderItem(t.icon, -8, -8);
				g.pose().popMatrix();
			}
			Ui.draw(g, font, Ui.bold(t.title), 44, 12, Theme.fade(Theme.TEXT, ca));
			Text.wrapped(g, font, t.subtitle, 44, 25, w - 52, 2, Theme.fade(Theme.TEXT_DIM, ca));
			String open = "Open >";
			Ui.draw(g, font, open, w - 8 - Ui.width(font, open) + Math.round(c.hover * 2), h - 13, Theme.fade(Theme.mix(Theme.TEXT_FAINT, Theme.accent(), c.hover), ca));
			g.pose().popMatrix();
		}
	}

	private void renderFooter(GuiGraphics g, float a) {
		String hint = showingTiles() ? "Click a tile to open it · Right Shift closes"
				: "Click a card to toggle · Right-click or the slider icon for settings · / to search · Right Shift closes";
		Ui.draw(g, font, Text.fit(font, hint, footer.w() - 80), footer.x() + 10, footer.y() + 4, Theme.fade(Theme.TEXT_FAINT, a));
		String version = "v" + FabricLoader.getInstance().getModContainer(HollowClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		Ui.draw(g, font, version, footer.right() - 10 - Ui.width(font, version), footer.y() + 4, Theme.fade(Theme.TEXT_FAINT, a));
	}

	private float animate(Object key, boolean on) {
		float before = hover.getOrDefault(key, 0f);
		float v = Anim.approach(before, on ? 1f : 0f, dt, 16f);
		if (on && before == 0f) UiSounds.hover();
		hover.put(key, v);
		return v;
	}

	// ---------------------------------------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		if (left && editHud.click(mx, my)) return true;
		if (left && searching() && clearSearch.contains(mx, my)) {
			UiSounds.click();
			search.setValue("");
			return true;
		}

		int y = rail.y() + 2;
		for (Category c : Category.values()) {
			Rect r = new Rect(rail.x(), y, rail.w(), 24);
			if (r.contains(mx, my)) {
				UiSounds.click();
				ripple(mx, my, r);
				if (c != category || searching()) switchCategory(c);
				return true;
			}
			y += RAIL_ROW;
		}

		if (grid.contains(mx, my)) {
			if (showingTiles()) {
				List<Tile> list = tiles();
				for (int i = 0; i < list.size(); i++) {
					Rect r = animatedRect(list.get(i).tab, tileRect(i));
					if (r.contains(mx, my)) {
						UiSounds.click();
						ripple(mx, my, r);
						open(new StylesScreen(list.get(i).tab, this));
						return true;
					}
				}
			} else {
				List<Module> mods = visibleModules();
				int i = cardAt(mods, mx, my);
				if (i >= 0) {
					Module m = mods.get(i);
					Rect r = animatedRect(m, cardRect(i));
					CardAnim c = anims.get(m);
					if (c != null) c.press = 1f;
					ripple(mx, my, r);
					if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || (left && (gearRect(r).contains(mx, my) || !m.toggleable()))) {
						UiSounds.click();
						show(settingsFor(m, this));
					} else if (left) {
						UiSounds.toggle(!m.isEnabled());
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

	private void show(Screen s) {
		if (s instanceof HollowScreen hs) open(hs);
		else if (minecraft != null) minecraft.setScreen(s);
	}

	/**
	 * The screen a module's card opens: its own screen (Theme) or the settings screen, with a "Move on screen"
	 * button for HUD modules.
	 */
	public static Screen settingsFor(Module m, Screen parent) {
		if (m instanceof ScreenProvider provider) return provider.createScreen(parent);
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
