package dev.telmuad.hollowclient.input;

import dev.telmuad.hollowclient.util.ClickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Click counting for the CPS and keystrokes displays. Fed by {@code MouseHandlerMixin}; only in-game clicks count. */
public final class Clicks {
	private static final ClickTracker LEFT = new ClickTracker();
	private static final ClickTracker RIGHT = new ClickTracker();

	private Clicks() {
	}

	public static void onMouseButton(int button, int action) {
		if (action != GLFW.GLFW_PRESS) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || mc.player == null) return;
		long now = Util.getMillis();
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) LEFT.press(now);
		else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT.press(now);
	}

	public static int leftCps() {
		return LEFT.cps(Util.getMillis());
	}

	public static int rightCps() {
		return RIGHT.cps(Util.getMillis());
	}
}
