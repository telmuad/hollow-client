package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.KeyDef;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.util.ZoomState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Hold the zoom key (C by default) to zoom in; scroll while holding to zoom further in or out. */
public final class ZoomModule extends Module {
	private final NumberSetting zoom = add(new NumberSetting("zoom", "Zoom amount", 4, 2, 50, 0.5).format(1, "x"));
	private final BooleanSetting scroll = add(new BooleanSetting("scroll", "Scroll to adjust", true));
	private final BooleanSetting smooth = add(new BooleanSetting("smooth", "Smooth zoom", true));
	private final NumberSetting smoothSpeed = add(new NumberSetting("smoothSpeed", "Smooth zoom speed", 12, 2, 30, 1));
	private final BooleanSetting lowerSensitivity = add(new BooleanSetting("lowerSensitivity", "Lower mouse sensitivity while zoomed", true));
	private final BooleanSetting cinematic = add(new BooleanSetting("cinematic", "Cinematic camera while zoomed", false));

	private final ZoomState state = new ZoomState();
	private long lastNanos;
	private boolean changedSmoothCamera;

	public ZoomModule() {
		super("zoom", "Zoom", "Hold a key to zoom in. Scroll while holding to change how far.", Category.QOL, true);
		keybind(KeyDef.hold(GLFW.GLFW_KEY_C));
		smoothSpeed.visibleWhen(smooth::get);
		zoom.describe("How far each zoom starts. Scrolling changes it until you let go.");
	}

	private boolean keyHeld(Minecraft mc) {
		KeyMapping key = Keybinds.of(this);
		return isEnabled() && key != null && key.isDown() && mc.gui.screen() == null && mc.player != null;
	}

	/** Called by the FOV mixin once per frame (or more); returns the factor the FOV is multiplied by. */
	public double fovMultiplier() {
		Minecraft mc = Minecraft.getInstance();
		if (keyHeld(mc)) state.start(zoom.get());
		else state.stop();
		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
		lastNanos = now;
		state.update(dt, smooth.on(), smoothSpeed.get());
		updateCinematic(mc);
		return state.fovMultiplier();
	}

	private void updateCinematic(Minecraft mc) {
		boolean want = cinematic.on() && state.isActive();
		if (want && !mc.options.smoothCamera) {
			mc.options.smoothCamera = true;
			changedSmoothCamera = true;
		} else if (!want && changedSmoothCamera) {
			mc.options.smoothCamera = false;
			changedSmoothCamera = false;
		}
	}

	/** Mouse wheel while zooming. Returns true if the scroll was used (and must not change the hotbar slot). */
	public boolean onScroll(double vertical) {
		if (!state.isActive() || !scroll.on() || vertical == 0) return false;
		state.scroll(Math.signum(vertical));
		return true;
	}

	/** Multiplier for mouse turning speed. */
	public double sensitivityMultiplier() {
		if (!lowerSensitivity.on() || !state.isZoomed()) return 1.0;
		return state.fovMultiplier();
	}

	@Override
	protected void onDisable() {
		state.stop();
		Minecraft mc = Minecraft.getInstance();
		if (changedSmoothCamera && mc.options != null) {
			mc.options.smoothCamera = false;
			changedSmoothCamera = false;
		}
	}
}
