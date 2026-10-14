package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.input.Clicks;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class CpsHud extends TextHudModule {
	private final BooleanSetting showRight = add(new BooleanSetting("showRight", "Show right clicks", true));

	public CpsHud() {
		super("cps", "CPS Counter", "Left and right clicks per second.", false, at(Anchor.START, 2, Anchor.START, 98));
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		int left = preview && mc.player == null ? 9 : Clicks.leftCps();
		int right = preview && mc.player == null ? 4 : Clicks.rightCps();
		out.add(Line.of(showRight.on() ? "CPS: " + left + " | " + right : "CPS: " + left));
	}
}
