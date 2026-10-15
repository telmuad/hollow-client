package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.camera.CameraPath;
import dev.telmuad.hollowclient.camera.CameraPaths;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.waypoints.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** Camera path editor: the list of points, add/remove/clear, and Play. */
public final class CameraPathScreen extends HollowScreen {
	private static final int ROW = 14;

	private final UiButton back = new UiButton("< Back", this::onClose);
	private final UiButton play = new UiButton("Play", () -> CameraPaths.start(Minecraft.getInstance())).style(UiButton.Style.PRIMARY);
	private final UiButton settings = new UiButton("Settings", () -> open(new ModuleSettingsScreen(Modules.CAMERA_PATH, this)));
	private final UiButton addPoint = new UiButton("+ Add point here", () -> CameraPaths.addPoint(Minecraft.getInstance()));
	private final UiButton removeLast = new UiButton("Remove last", CameraPaths::removeLast);
	private final UiButton clear = new UiButton("Clear", this::clearPressed).style(UiButton.Style.DANGER);
	private Rect panel = Rect.EMPTY;
	private Rect list = Rect.EMPTY;
	private boolean confirmClear;
	private float scroll;
	private float scrollTarget;

	public CameraPathScreen(Screen parent) {
		super(Component.literal("Camera Paths"), parent);
	}

	@Override
	protected void init() {
		super.init();
		int pw = Math.min(width - 24, 520);
		int ph = Math.min(height - 24, 320);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		int pwid = Math.max(54, play.preferredWidth(font));
		play.bounds(new Rect(panel.right() - 8 - pwid, panel.y() + 8, pwid, 16));
		int sw = settings.preferredWidth(font);
		settings.bounds(new Rect(play.bounds().x() - 6 - sw, panel.y() + 8, sw, 16));
		int x = panel.x() + 8;
		for (UiButton b : new UiButton[]{addPoint, removeLast, clear}) {
			int w = b.preferredWidth(font);
			b.bounds(new Rect(x, panel.y() + 30, w, 16));
			x += w + 6;
		}
		list = new Rect(panel.x() + 8, panel.y() + 54, pw - 16, ph - 54 - 34);
	}

	private void clearPressed() {
		if (CameraPaths.keys().isEmpty()) return;
		if (!confirmClear) {
			confirmClear = true;
			return;
		}
		confirmClear = false;
		CameraPaths.clear();
	}

	@Override
	protected void renderContent(GuiGraphics g, int mx, int my, float a) {
		Minecraft mc = Minecraft.getInstance();
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		settings.render(g, font, mx, my, dt, a);
		Ui.draw(g, font, Ui.bold("Camera Paths"), back.bounds().right() + 8, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		boolean allowed = Waypoints.allowed(mc);
		boolean on = Modules.CAMERA_PATH != null && Modules.CAMERA_PATH.isEnabled();
		List<CameraPath.Key> keys = CameraPaths.keys();
		play.setEnabled(allowed && on && keys.size() >= 2);
		addPoint.setEnabled(allowed && on);
		removeLast.setEnabled(allowed && !keys.isEmpty());
		clear.setEnabled(allowed && !keys.isEmpty());
		clear.setLabel(confirmClear ? "Sure? Clear" : "Clear");
		play.render(g, font, mx, my, dt, a);
		for (UiButton b : new UiButton[]{addPoint, removeLast, clear}) b.render(g, font, mx, my, dt, a);
		if (!allowed) {
			Ui.center(g, font, "Camera paths work in your own singleplayer worlds only.", list.centerX(), list.y() + 30, Theme.fade(Theme.TEXT_DIM, a));
			return;
		}
		if (!on) {
			Ui.center(g, font, "Switch Camera Paths on in the Visual category first.", list.centerX(), list.y() + 30, Theme.fade(Theme.TEXT_DIM, a));
			return;
		}
		Draw.roundRect(g, list, Theme.radius(4), Theme.fade(Theme.PANEL_RAISED, a * 0.6f));
		if (keys.isEmpty()) {
			Ui.center(g, font, "Stand where the shot starts and add a point. Move, look, add the next one.", list.centerX(), list.y() + 20, Theme.fade(Theme.TEXT_DIM, a));
		}
		float max = Math.max(0, keys.size() * ROW - list.h() + 4);
		scrollTarget = Math.min(scrollTarget, max);
		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		for (int i = 0; i < keys.size(); i++) {
			CameraPath.Key k = keys.get(i);
			int y = list.y() + 3 + i * ROW - Math.round(scroll);
			if (y + ROW < list.y() || y > list.bottom()) continue;
			int c = Theme.gradient(keys.size() < 2 ? 0f : (float) i / (keys.size() - 1));
			Draw.roundRect(g, list.x() + 4, y, 18, 11, 5, Theme.fade(c, a));
			Ui.center(g, font, String.valueOf(i + 1), list.x() + 13, y + 2, Theme.fade(0xFFFFFFFF, a));
			Ui.draw(g, font, String.format(Locale.ROOT, "%.1f, %.1f, %.1f", k.x(), k.y(), k.z()), list.x() + 30, y + 2, Theme.fade(Theme.TEXT, a));
			Ui.draw(g, font, String.format(Locale.ROOT, "looking %.0f°, %.0f°", k.yaw(), k.pitch()), list.x() + 200, y + 2, Theme.fade(Theme.TEXT_FAINT, a));
		}
		g.disableScissor();
		String info;
		if (keys.size() >= 2) {
			CameraPath path = new CameraPath(keys);
			info = String.format(Locale.ROOT, "%d points · %.0f m · %.0f s flight. Moving or opening a menu stops it.", keys.size(), path.length(),
					Modules.CAMERA_PATH.duration());
		} else {
			info = "Add at least two points. Flight time, looping and easing are in the module settings.";
		}
		Ui.draw(g, font, Ui.fit(font, info, panel.w() - 20), panel.x() + 10, panel.bottom() - 28, Theme.fade(Theme.TEXT_DIM, a));
		Ui.draw(g, font, Ui.fit(font, "Tip: give \"Add camera point\" and \"Play / stop camera path\" keys in Controls.", panel.w() - 20),
				panel.x() + 10, panel.bottom() - 14, Theme.fade(Theme.TEXT_FAINT, a));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			double mx = event.x();
			double my = event.y();
			if (!clear.bounds().contains(mx, my)) confirmClear = false;
			for (UiButton b : new UiButton[]{back, play, settings, addPoint, removeLast, clear}) if (b.click(mx, my)) return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		float max = Math.max(0, CameraPaths.keys().size() * ROW - list.h() + 4);
		scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * 20));
		return true;
	}
}
