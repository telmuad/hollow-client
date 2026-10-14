package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class FpsHud extends TextHudModule {
	private final BooleanSetting colorByFps = add(new BooleanSetting("colorByFps", "Color by FPS", false));

	public FpsHud() {
		super("fps", "FPS Counter", "Shows your frames per second.", true, at(Anchor.START, 2, Anchor.START, 2));
		colorByFps.describe("Green above 60, yellow above 30, red below.");
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		int fps = preview && mc.player == null ? 144 : mc.getFps();
		int color = 0;
		if (colorByFps.on()) color = fps >= 60 ? 0xFF5BD18B : (fps >= 30 ? 0xFFFFE066 : 0xFFF2706B);
		out.add(new Line(fps + " FPS", color));
	}
}
