package dev.telmuad.hollowclient.hud;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A module drawn on the HUD. Every HUD module has text color, background on/off (+ color), text shadow and a
 * position/scale that the HUD editor changes. Subclasses measure their content once per frame, then draw it at
 * (0, 0) unscaled; {@link HudRenderer} handles placing and scaling.
 */
public abstract class HudModule extends Module {
	public static final int PAD = 3;

	protected final ColorSetting textColor;
	protected final BooleanSetting background;
	protected final ColorSetting backgroundColor;
	protected final BooleanSetting shadow;
	private final HudPosition defaults;
	private final HudPosition position;

	public record Size(int w, int h) {
	}

	protected HudModule(String id, String name, String description, boolean enabledByDefault, HudPosition defaults) {
		super(id, name, description, Category.HUD, enabledByDefault);
		this.defaults = defaults.copy();
		this.position = defaults.copy();
		textColor = add(new ColorSetting("textColor", "Text color", 0xFFFFFFFF));
		background = add(new BooleanSetting("background", "Background", true));
		backgroundColor = add(new ColorSetting("backgroundColor", "Background color", 0x80101015));
		backgroundColor.visibleWhen(background::get);
		shadow = add(new BooleanSetting("textShadow", "Text shadow", true));
	}

	/** Default position from pixel offsets on a 480x270 (GUI scale 4 at 1080p) reference screen. */
	protected static HudPosition at(HudPosition.Anchor ax, int px, HudPosition.Anchor ay, int py) {
		return new HudPosition(ax, px / 480.0, ay, py / 270.0, 1f);
	}

	@Override
	public final HudPosition hudPosition() {
		return position;
	}

	@Override
	public final HudPosition defaultHudPosition() {
		return defaults;
	}

	/** Call after the HUD editor moved or scaled the element, so the config gets saved. */
	public final void positionChanged() {
		markChanged();
	}

	public void resetPosition() {
		position.copyFrom(defaults);
		markChanged();
	}

	/** Whether the element has something to show in game right now (for example Ping only on servers). */
	public boolean visibleInGame(Minecraft mc) {
		return mc.player != null;
	}

	/**
	 * Work out what to show and how big it is, unscaled. Called once per frame before {@link #draw}.
	 *
	 * @param preview true in the HUD editor when there is no live data (sample values are shown)
	 */
	public abstract Size measure(Minecraft mc, Font font, boolean preview);

	/** Draw the content measured by the last {@link #measure} call, with its top-left at (0, 0). */
	public abstract void draw(GuiGraphics g, Minecraft mc, Font font, boolean preview);

	/** True when the element sits in the right third of the screen, so text can line up on the right. */
	protected final boolean alignRight() {
		return position.anchorX == HudPosition.Anchor.END;
	}

	protected final void drawBackground(GuiGraphics g, int w, int h) {
		if (background.on()) g.fill(0, 0, w, h, backgroundColor.argb());
	}

	protected final void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
		g.drawString(font, s, x, y, color, shadow.on());
	}
}
