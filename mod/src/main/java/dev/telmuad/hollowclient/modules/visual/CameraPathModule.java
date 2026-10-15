package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.gui.CameraPathScreen;
import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import net.minecraft.client.gui.screens.Screen;

/**
 * Cinematic camera paths for singleplayer: place points, then fly the camera smoothly through them for videos and
 * screenshots. Your player stays put and nothing is sent to the server. Never works on servers.
 */
public final class CameraPathModule extends Module implements ScreenProvider {
	private final NumberSetting duration = add(new NumberSetting("duration", "Flight time", 12, 2, 120, 1).format(0, " s"));
	private final BooleanSetting ease = add(new BooleanSetting("ease", "Slow start and end", true));
	private final BooleanSetting loop = add(new BooleanSetting("loop", "Loop", false));
	private final BooleanSetting hideHud = add(new BooleanSetting("hideHud", "Hide the HUD while flying", true));
	private final BooleanSetting preview = add(new BooleanSetting("preview", "Show the path while editing", true));

	public CameraPathModule() {
		super("camera_path", "Camera Paths", "Singleplayer cinematic camera: place points, then fly smoothly through them. Moving stops it.",
				Category.VISUAL, false);
	}

	public double duration() {
		return duration.get();
	}

	public boolean ease() {
		return ease.on();
	}

	public boolean loop() {
		return loop.on();
	}

	public boolean hideHud() {
		return hideHud.on();
	}

	public boolean preview() {
		return preview.on();
	}

	@Override
	public Screen createScreen(Screen parent) {
		return new CameraPathScreen(parent);
	}
}
