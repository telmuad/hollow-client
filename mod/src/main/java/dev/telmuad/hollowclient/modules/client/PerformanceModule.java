package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/**
 * One switch for every heavy effect (Performance Mode), and Auto Quality, which turns effect quality down while
 * FPS stays below a target and back up when there is room again.
 */
public final class PerformanceModule extends Module {
	private final BooleanSetting performanceMode = add(new BooleanSetting("performanceMode", "Performance Mode (turn off all heavy effects)", false));
	private final BooleanSetting autoQuality = add(new BooleanSetting("autoQuality", "Auto Quality (lower effects when FPS drops)", true));
	private final NumberSetting targetFps = add(new NumberSetting("targetFps", "Auto Quality target FPS", 60, 30, 240, 5).format(0, " FPS"));
	private final BooleanSetting reducedMotion = add(new BooleanSetting("reducedMotion", "Reduce UI motion", false));

	public PerformanceModule() {
		super("performance", "Performance", "Performance Mode, FPS-aware Auto Quality and reduced UI motion.", Category.CLIENT, true);
		targetFps.visibleWhen(autoQuality::get);
		reducedMotion.describe("Shorter, calmer menu animations and no menu particles.");
	}

	@Override
	public boolean toggleable() {
		return false;
	}

	public boolean performanceMode() {
		return performanceMode.on();
	}

	public BooleanSetting performanceModeSetting() {
		return performanceMode;
	}

	public boolean autoQuality() {
		return autoQuality.on();
	}

	public int targetFps() {
		return targetFps.getInt();
	}

	public boolean reducedMotion() {
		return reducedMotion.on();
	}
}
