package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.render.WaypointFx;
import dev.telmuad.hollowclient.waypoints.Waypoint;
import dev.telmuad.hollowclient.waypoints.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The waypoint list of this singleplayer world: add one where you stand, rename (click the name), recolor (click
 * the dot), show or hide, delete. Waypoints of other dimensions are listed with their dimension.
 */
public final class WaypointsScreen extends HollowScreen {
	private static final int ROW = 24;

	private final UiButton back = new UiButton("< Back", this::onClose);
	private final UiButton add = new UiButton("+ Add here", this::addHere).style(UiButton.Style.PRIMARY);
	private Rect panel = Rect.EMPTY;
	private Rect list = Rect.EMPTY;
	private EditBox rename;
	private Waypoint renaming;
	private float scroll;
	private float scrollTarget;

	public WaypointsScreen(Screen parent) {
		super(Component.literal("Waypoints"), parent);
	}

	@Override
	protected void init() {
		super.init();
		int pw = Math.min(width - 24, 560);
		int ph = Math.min(height - 24, 330);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		int aw = add.preferredWidth(font);
		add.bounds(new Rect(panel.right() - 8 - aw, panel.y() + 8, aw, 16));
		list = new Rect(panel.x() + 8, panel.y() + 32, pw - 16, ph - 32 - 20);
		rename = new EditBox(font, 0, 0, 120, 10, Component.literal("Name"));
		rename.setBordered(false);
		rename.setMaxLength(32);
		rename.setTextColor(Theme.TEXT);
		rename.visible = false;
		addWidget(rename);
	}

	private List<Waypoint> entries() {
		return new ArrayList<>(Waypoints.data().waypoints);
	}

	private void addHere() {
		Minecraft mc = Minecraft.getInstance();
		Waypoint w = Waypoints.addHere(mc, null);
		if (w != null) {
			Toasts.success("Waypoint added", w.name);
			startRename(w);
			scrollTarget = Math.max(0, entries().size() * ROW - list.h());
		}
	}

	private void startRename(Waypoint w) {
		renaming = w;
		rename.setValue(w.name);
		rename.visible = true;
		rename.setFocused(true);
		setFocused(rename);
		rename.moveCursorToEnd(false);
	}

	private void finishRename(boolean keep) {
		if (renaming != null && keep) {
			String v = rename.getValue().trim();
			if (!v.isEmpty() && !v.equals(renaming.name)) {
				renaming.name = v;
				Waypoints.changed();
			}
		}
		renaming = null;
		rename.visible = false;
		rename.setFocused(false);
		setFocused(null);
	}

	private Rect rowRect(int i) {
		return new Rect(list.x(), list.y() + i * ROW - Math.round(scroll), list.w() - 6, ROW - 2);
	}

	private static Rect dotRect(Rect r) {
		return new Rect(r.x() + 6, r.y() + 6, 10, 10);
	}

	private static Rect deleteRect(Rect r) {
		return new Rect(r.right() - 20, r.y() + 3, 16, 16);
	}

	private static Rect eyeRect(Rect r) {
		return new Rect(r.right() - 64, r.y() + 3, 40, 16);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mx, int my, float a) {
		Minecraft mc = Minecraft.getInstance();
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		boolean allowed = Waypoints.allowed(mc);
		add.setEnabled(allowed);
		add.render(g, font, mx, my, dt, a);
		Ui.draw(g, font, Ui.bold("Waypoints"), back.bounds().right() + 8, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		if (!allowed) {
			Ui.center(g, font, "Waypoints work in your own singleplayer worlds only.", list.centerX(), list.y() + 40, Theme.fade(Theme.TEXT_DIM, a));
			Ui.center(g, font, "They never show on servers or in worlds open to LAN.", list.centerX(), list.y() + 54, Theme.fade(Theme.TEXT_FAINT, a));
			rename.visible = false;
			return;
		}
		List<Waypoint> items = entries();
		if (items.isEmpty()) {
			Ui.center(g, font, "No waypoints yet. \"+ Add here\" saves the spot you stand on.", list.centerX(), list.y() + 40, Theme.fade(Theme.TEXT_DIM, a));
		}
		float max = Math.max(0, items.size() * ROW - list.h());
		scrollTarget = Math.min(scrollTarget, max);
		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		String dim = Waypoints.dimension(mc);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		boolean renameShown = false;
		for (int i = 0; i < items.size(); i++) {
			Waypoint w = items.get(i);
			Rect r = rowRect(i);
			if (r.bottom() < list.y() || r.y() > list.bottom()) continue;
			boolean hover = r.contains(mx, my) && list.contains(mx, my);
			Draw.roundRect(g, r, Theme.radius(4), Theme.fade(hover ? Theme.CARD_HOVER : Theme.CARD, a));
			int c = WaypointFx.color(w);
			Rect dot = dotRect(r);
			Draw.circle(g, dot.x(), dot.y(), 10, Theme.fade(w.visible ? c : Theme.mix(c, Theme.CARD, 0.6f), a));
			if (dot.contains(mx, my)) Draw.roundOutline(g, dot.inset(-2), 6, Theme.fade(0xFFFFFFFF, a));
			int tx = r.x() + 22;
			if (w == renaming) {
				Draw.roundRect(g, tx - 3, r.y() + 2, 140, 11, 2, Theme.fade(Theme.PANEL_RAISED, a));
				Draw.roundOutline(g, tx - 3, r.y() + 2, 140, 11, 2, Theme.fade(Theme.accent(), a));
				rename.setX(tx);
				rename.setY(r.y() + 3);
				rename.setWidth(134);
				renameShown = true;
			} else {
				Ui.draw(g, font, Ui.fit(font, w.name, r.w() / 2), tx, r.y() + 3, Theme.fade(Theme.TEXT, a));
			}
			String where = String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(w.x), (int) Math.floor(w.y), (int) Math.floor(w.z));
			boolean here = w.dimension.equals(dim);
			if (!here) where += " · " + w.dimension.replace("minecraft:", "").replace('_', ' ');
			else if (mc.player != null) where += " · " + WaypointFx.formatDistance(w.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())) + " away";
			if (w.death) where += " · death point";
			Ui.draw(g, font, where, tx, r.y() + 13, Theme.fade(Theme.TEXT_FAINT, a));
			// show/hide and delete
			Rect eye = eyeRect(r);
			boolean eyeHover = eye.contains(mx, my);
			Draw.roundRect(g, eye, 3, Theme.fade(eyeHover ? Theme.CARD_HOVER : Theme.PANEL_RAISED, a));
			Ui.center(g, font, w.visible ? "Shown" : "Hidden", eye.centerX(), eye.y() + 4, Theme.fade(w.visible ? Theme.accent() : Theme.TEXT_FAINT, a));
			Rect del = deleteRect(r);
			boolean delHover = del.contains(mx, my);
			Draw.roundRect(g, del, 3, Theme.fade(delHover ? 0xFF7F1D1D : Theme.PANEL_RAISED, a));
			Ui.center(g, font, "x", del.centerX() + 1, del.y() + 4, Theme.fade(delHover ? 0xFFFFFFFF : Theme.TEXT_DIM, a));
		}
		rename.visible = renameShown;
		if (renameShown && a > 0.5f) rename.render(g, mx, my, 0);
		g.disableScissor();
		Ui.draw(g, font, "Click a name to rename it, the dot to change its color. A death point is saved where you die.",
				panel.x() + 10, panel.bottom() - 14, Theme.fade(Theme.TEXT_FAINT, a));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		double mx = event.x();
		double my = event.y();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
		if (back.click(mx, my) || add.click(mx, my)) return true;
		if (renaming != null && rename.isMouseOver(mx, my)) return super.mouseClicked(event, doubleClick);
		if (renaming != null) finishRename(true);
		if (!list.contains(mx, my) || !Waypoints.allowed(Minecraft.getInstance())) return super.mouseClicked(event, doubleClick);
		List<Waypoint> items = entries();
		for (int i = 0; i < items.size(); i++) {
			Rect r = rowRect(i);
			if (!r.contains(mx, my)) continue;
			Waypoint w = items.get(i);
			if (deleteRect(r).contains(mx, my)) {
				Waypoints.remove(w);
				UiSounds.click();
				Toasts.info("Waypoint deleted", w.name);
			} else if (eyeRect(r).contains(mx, my)) {
				w.visible = !w.visible;
				Waypoints.changed();
				UiSounds.toggle(w.visible);
			} else if (dotRect(r).inset(-2).contains(mx, my)) {
				w.color = w.nextColor();
				Waypoints.changed();
				UiSounds.click();
			} else {
				UiSounds.click();
				ripple(mx, my, r);
				startRename(w);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		float max = Math.max(0, entries().size() * ROW - list.h());
		scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * 26));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (renaming != null) {
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				finishRename(true);
				return true;
			}
			if (event.isEscape()) {
				finishRename(false);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		if (renaming != null) finishRename(true);
		Waypoints.saveNow();
		super.removed();
	}

	/** For tests: rename the newest waypoint. */
	public void renameLast(String name) {
		List<Waypoint> items = entries();
		if (items.isEmpty()) return;
		startRename(items.get(items.size() - 1));
		rename.setValue(name);
		finishRename(true);
	}
}
