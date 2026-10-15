package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** The outline around the block you are looking at: color (theme, custom or rainbow), thickness, fill and pulse. */
public final class BlockOutlineModule extends Module {
	public enum ColorMode {THEME, CUSTOM, RAINBOW}

	private final EnumSetting<ColorMode> mode = add(new EnumSetting<>("mode", "Color", ColorMode.THEME));
	private final ColorSetting color = add(new ColorSetting("color", "Custom color", 0xE0FFFFFF));
	private final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1.5, 0.5, 4, 0.25).format(2, "x"));
	private final Effect fill = effect("fill", "Fill", true, 25, "A see-through fill on the block's faces.");
	private final Effect pulse = effect("pulse", "Pulse", false, 50, "The outline gently breathes.");

	public BlockOutlineModule() {
		super("block_outline", "Block Outline", "Color, thickness, fill and pulse for the outline of the block you look at.", Category.VISUAL, true);
		color.visibleWhen(() -> mode.get() == ColorMode.CUSTOM);
	}

	public ColorMode mode() {
		return mode.get();
	}

	public int customColor() {
		return color.argb();
	}

	public float thickness() {
		return thickness.getFloat();
	}

	public float fill() {
		return fill.amount();
	}

	public float pulse() {
		return pulse.amount();
	}
}
