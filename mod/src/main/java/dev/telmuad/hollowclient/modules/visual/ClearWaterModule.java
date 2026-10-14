package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Removes the blue underwater overlay and pushes underwater fog further out. Visual only. */
public final class ClearWaterModule extends Module {
	private final BooleanSetting removeOverlay = add(new BooleanSetting("removeOverlay", "Remove underwater overlay", true));
	private final BooleanSetting clearFog = add(new BooleanSetting("clearFog", "Clear underwater fog", true));
	private final NumberSetting distance = add(new NumberSetting("distance", "Underwater view distance", 96, 16, 256, 8).format(0, " blocks"));

	public ClearWaterModule() {
		super("clear_water", "Clear Water", "See further underwater and remove the water overlay.", Category.VISUAL, false);
		distance.visibleWhen(clearFog::get);
	}

	public boolean hideOverlay() {
		return isEnabled() && removeOverlay.on();
	}

	/** Fog end distance to use underwater, or {@code original} when off. */
	public float fogEnd(float original) {
		if (!isEnabled() || !clearFog.on()) return original;
		return Math.max(original, distance.getFloat());
	}
}
