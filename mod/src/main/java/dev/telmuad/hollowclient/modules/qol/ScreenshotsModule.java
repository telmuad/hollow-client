package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.gui.ScreenshotsScreen;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.gui.screens.Screen;

/**
 * The screenshot manager (open it from this card or its key): browse your screenshots, view them large, copy, open,
 * show in the folder or move to a "deleted" folder. While on, taking a screenshot also shows a notification.
 */
public final class ScreenshotsModule extends Module implements ScreenProvider {
	private final BooleanSetting notify = add(new BooleanSetting("notify", "Notification when you take one", true));
	private final BooleanSetting shutter = add(new BooleanSetting("shutter", "Camera shutter sound", true));

	public ScreenshotsModule() {
		super("screenshots", "Screenshots", "Browse, view, copy and tidy your screenshots. Right-click the card or use its key to open.", Category.QOL, true);
	}

	public boolean notifyOnCapture() {
		return notify.on();
	}

	public boolean shutter() {
		return shutter.on();
	}

	@Override
	public Screen createScreen(Screen parent) {
		return new ScreenshotsScreen(parent);
	}
}
