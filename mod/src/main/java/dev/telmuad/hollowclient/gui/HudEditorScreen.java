package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.hud.HudLayout;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition;
import dev.telmuad.hollowclient.hud.HudRenderer;
import dev.telmuad.hollowclient.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drag HUD elements anywhere; scroll over one (or drag its corner handle) to scale it; elements snap to the screen
 * edges, the center lines and each other (hold Alt to place freely). Right-click resets one element.
 * Positions are stored relative to the screen (see {@link HudLayout}), so they survive resizing.
 */
public final class HudEditorScreen extends HollowScreen {
	private static final double SNAP = 4;
	private static final int HANDLE = 5;
	private static final String HINT = "Drag: move · Scroll or corner: scale · Right-click: reset · Alt: free move";

	private final Module focus;
	private final Map<HudModule, HudLayout.Box> boxes = new IdentityHashMap<>();
	private final UiButton done = new UiButton("Done", this::onClose).style(UiButton.Style.PRIMARY);
	private final UiButton resetAll = new UiButton("Reset all", this::resetAll).style(UiButton.Style.DANGER);
	private final UiButton snapButton = new UiButton("Snap: On", this::toggleSnap);
	private final UiButton showAll = new UiButton("Show disabled: Off", this::toggleShowAll);
	private boolean snapping = true;
	private boolean includeDisabled;
	private boolean confirmReset;
	private HudModule selected;
	private HudModule dragging;
	private boolean scalingDrag;
	private double grabDx;
	private double grabDy;
	private double guideX = Double.NaN;
	private double guideY = Double.NaN;

	public HudEditorScreen(Screen parent) {
		this(parent, null);
	}

	public HudEditorScreen(Screen parent, Module focus) {
		super(Component.literal("HUD editor"), parent);
		this.focus = focus;
	}

	@Override
	protected void init() {
		super.init();
		if (focus instanceof HudModule h) selected = h;
		placeToolbar(height / 2 - 60);
	}

	/** Lay the toolbar out with its top at {@code top}; returns the bar's rectangle. */
	private Rect placeToolbar(int top) {
		int doneW = Math.max(done.preferredWidth(font), 50);
		int snapW = snapButton.preferredWidth(font) + 20;
		int showW = Ui.width(font, "Show disabled: Off") + 16;
		int total = resetAll.preferredWidth(font) + 4 + snapW + 4 + showW + 4 + doneW;
		int x = width / 2 - total / 2;
		int y = top + 22;
		resetAll.bounds(new Rect(x, y, resetAll.preferredWidth(font), 16));
		x += resetAll.bounds().w() + 4;
		snapButton.bounds(new Rect(x, y, snapW, 16));
		x += snapW + 4;
		showAll.bounds(new Rect(x, y, showW, 16));
		x += showW + 4;
		done.bounds(new Rect(x, y, doneW, 16));
		int barW = Math.max(total, Ui.width(font, HINT)) + 16;
		return new Rect(width / 2 - barW / 2, top, barW, 44);
	}

	/** Put the toolbar where it covers the fewest HUD elements: above the crosshair, above the hotbar, or at the top. */
	private Rect chooseToolbar() {
		int[] candidates = {height / 2 - 60, height - 110, 30};
		Rect best = null;
		double bestOverlap = Double.MAX_VALUE;
		for (int top : candidates) {
			Rect bar = placeToolbar(Math.max(2, top));
			double overlap = 0;
			for (HudLayout.Box b : boxes.values()) {
				double ix = Math.min(bar.right(), b.right()) - Math.max(bar.x(), b.x());
				double iy = Math.min(bar.bottom(), b.bottom()) - Math.max(bar.y(), b.y());
				if (ix > 0 && iy > 0) overlap += ix * iy;
			}
			if (overlap < bestOverlap) {
				bestOverlap = overlap;
				best = bar;
			}
			if (overlap == 0) break;
		}
		return placeToolbar(best.y());
	}

	private List<HudModule> elements() {
		List<HudModule> out = new ArrayList<>();
		for (HudModule m : HudRenderer.hudModules()) if (m.isEnabled() || includeDisabled || m == focus) out.add(m);
		return out;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// Light dim only, so the real HUD layout is easy to judge.
		g.fill(0, 0, width, height, Theme.fade(0x40000000, openProgress()));
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		// Center lines, faint, as a hint for where snapping happens.
		g.fill(width / 2, 0, width / 2 + 1, height, Theme.fade(0x18FFFFFF, a));
		g.fill(0, height / 2, width, height / 2 + 1, Theme.fade(0x18FFFFFF, a));

		boxes.clear();
		HudModule hovered = null;
		for (HudModule m : elements()) {
			HudLayout.Box box = HudRenderer.drawModule(g, minecraft, m, true);
			if (box == null) continue;
			boxes.put(m, box);
			if (box.contains(mx, my) || handle(box).contains(mx, my)) hovered = m;
		}
		for (Map.Entry<HudModule, HudLayout.Box> e : boxes.entrySet()) {
			HudModule m = e.getKey();
			HudLayout.Box b = e.getValue();
			int x = (int) Math.floor(b.x()) - 1;
			int y = (int) Math.floor(b.y()) - 1;
			int w = (int) Math.ceil(b.w()) + 2;
			int h = (int) Math.ceil(b.h()) + 2;
			boolean sel = m == selected;
			int color = sel ? Theme.accent() : (m == hovered ? 0xCCFFFFFF : 0x55FFFFFF);
			if (!m.isEnabled()) color = Theme.fade(color, 0.5f);
			g.outline(x, y, w, h, Theme.fade(color, a));
			if (sel || m == hovered) {
				Rect hr = handle(b);
				g.fill(hr.x(), hr.y(), hr.right(), hr.bottom(), Theme.fade(sel ? Theme.accent() : 0xCCFFFFFF, a));
				String label = m.name() + (m.isEnabled() ? "" : " (off)") + "  " + Math.round(m.hudPosition().scale * 100) + "%";
				int ly = y - 10 >= 0 ? y - 10 : y + h + 2;
				int lx = Math.max(0, Math.min(width - Ui.width(font, label) - 4, x));
				g.fill(lx, ly - 1, lx + Ui.width(font, label) + 4, ly + 9, Theme.fade(0xC0000000, a));
				Ui.draw(g, font, label, lx + 2, ly, Theme.fade(Theme.TEXT, a));
			}
		}
		if (!Double.isNaN(guideX)) {
			int gx = (int) Math.round(guideX);
			g.fill(gx, 0, gx + 1, height, Theme.fade(Theme.guide(), a));
		}
		if (!Double.isNaN(guideY)) {
			int gy = (int) Math.round(guideY);
			g.fill(0, gy, width, gy + 1, Theme.fade(Theme.guide(), a));
		}

		if (dragging == null) {
			Rect bar = chooseToolbar();
			Draw.roundRect(g, bar, 6, Theme.fade(0xD0101015, a));
			Draw.roundOutline(g, bar, 6, Theme.fade(Theme.STROKE, a));
			Ui.center(g, font, HINT, width / 2, bar.y() + 7, Theme.fade(Theme.TEXT_DIM, a));
			resetAll.setLabel(confirmReset ? "Click again" : "Reset all");
			snapButton.setLabel("Snap: " + (snapping ? "On" : "Off"));
			showAll.setLabel("Show disabled: " + (includeDisabled ? "On" : "Off"));
			resetAll.render(g, font, mx, my, dt, a);
			snapButton.render(g, font, mx, my, dt, a);
			showAll.render(g, font, mx, my, dt, a);
			done.render(g, font, mx, my, dt, a);
		}
	}

	private static Rect handle(HudLayout.Box b) {
		int x = (int) Math.ceil(b.right()) - HANDLE + 2;
		int y = (int) Math.ceil(b.bottom()) - HANDLE + 2;
		return new Rect(x, y, HANDLE, HANDLE);
	}

	private boolean toolbarHit(double mx, double my) {
		return resetAll.bounds().contains(mx, my) || snapButton.bounds().contains(mx, my)
				|| showAll.bounds().contains(mx, my) || done.bounds().contains(mx, my);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		boolean left = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		if (left && toolbarHit(mx, my)) {
			if (!resetAll.bounds().contains(mx, my)) confirmReset = false;
			resetAll.click(mx, my);
			snapButton.click(mx, my);
			showAll.click(mx, my);
			done.click(mx, my);
			return true;
		}
		confirmReset = false;
		// Topmost first: the last drawn element wins.
		List<HudModule> order = new ArrayList<>(boxes.keySet());
		java.util.Collections.reverse(order);
		if (selected != null && boxes.containsKey(selected)) {
			order.remove(selected);
			order.add(0, selected);
		}
		for (HudModule m : order) {
			HudLayout.Box b = boxes.get(m);
			boolean onHandle = handle(b).contains(mx, my);
			if (!onHandle && !b.contains(mx, my)) continue;
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				m.resetPosition();
				selected = m;
				return true;
			}
			if (left) {
				selected = m;
				dragging = m;
				scalingDrag = onHandle;
				grabDx = mx - b.x();
				grabDy = my - b.y();
				return true;
			}
		}
		if (left) selected = null;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		HudLayout.Box b = boxes.get(dragging);
		if (b == null) return true;
		HudPosition pos = dragging.hudPosition();
		if (scalingDrag) {
			double unscaledW = b.w() / pos.scale;
			double unscaledH = b.h() / pos.scale;
			double wanted = Math.max((event.x() - b.x()) / unscaledW, (event.y() - b.y()) / unscaledH);
			float newScale = HudPosition.clampScale((float) wanted);
			if (newScale != pos.scale) {
				// Keep the top-left corner where it is while the size changes.
				pos.scale = newScale;
				HudLayout.store(pos, b.x(), b.y(), unscaledW * newScale, unscaledH * newScale, width, height);
				dragging.positionChanged();
			}
			return true;
		}
		double nx = event.x() - grabDx;
		double ny = event.y() - grabDy;
		boolean free = !snapping || event.hasAltDown();
		if (free) {
			guideX = Double.NaN;
			guideY = Double.NaN;
		} else {
			List<HudLayout.Box> others = new ArrayList<>();
			for (Map.Entry<HudModule, HudLayout.Box> e : boxes.entrySet()) if (e.getKey() != dragging) others.add(e.getValue());
			HudLayout.Snap s = HudLayout.snap(nx, ny, b.w(), b.h(), width, height, others, SNAP);
			nx = s.x();
			ny = s.y();
			guideX = s.guideX();
			guideY = s.guideY();
		}
		HudLayout.store(pos, Math.round(nx), Math.round(ny), b.w(), b.h(), width, height);
		dragging.positionChanged();
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		scalingDrag = false;
		guideX = Double.NaN;
		guideY = Double.NaN;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		HudModule target = null;
		for (Map.Entry<HudModule, HudLayout.Box> e : boxes.entrySet()) if (e.getValue().contains(mx, my)) target = e.getKey();
		if (target == null) target = selected;
		if (target == null || sy == 0) return false;
		HudPosition pos = target.hudPosition();
		float next = HudPosition.clampScale(pos.scale + (sy > 0 ? 0.1f : -0.1f));
		if (next != pos.scale) {
			pos.scale = next;
			target.positionChanged();
		}
		selected = target;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (selected != null && boxes.containsKey(selected)) {
			int step = event.hasShiftDown() ? 10 : 1;
			int dx = 0;
			int dy = 0;
			switch (event.key()) {
				case GLFW.GLFW_KEY_LEFT -> dx = -step;
				case GLFW.GLFW_KEY_RIGHT -> dx = step;
				case GLFW.GLFW_KEY_UP -> dy = -step;
				case GLFW.GLFW_KEY_DOWN -> dy = step;
				case GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_DELETE -> {
					selected.resetPosition();
					return true;
				}
				default -> {
				}
			}
			if (dx != 0 || dy != 0) {
				HudLayout.Box b = boxes.get(selected);
				HudLayout.store(selected.hudPosition(), b.x() + dx, b.y() + dy, b.w(), b.h(), width, height);
				selected.positionChanged();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void resetAll() {
		if (!confirmReset) {
			confirmReset = true;
			return;
		}
		confirmReset = false;
		for (HudModule m : HudRenderer.hudModules()) m.resetPosition();
	}

	private void toggleSnap() {
		snapping = !snapping;
	}

	private void toggleShowAll() {
		includeDisabled = !includeDisabled;
	}
}
