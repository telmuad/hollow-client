package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

/** Your latency to the server, as the server reports it in the player list. Hidden in singleplayer. */
public final class PingHud extends TextHudModule {
	private final BooleanSetting colorByPing = add(new BooleanSetting("colorByPing", "Color by ping", true));

	public PingHud() {
		super("ping", "Ping", "Your ping to the server (only shown on servers).", false, at(Anchor.CENTER, 40, Anchor.START, 2));
	}

	private static Integer latency(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null || mc.hasSingleplayerServer()) return null;
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? null : info.getLatency();
	}

	@Override
	public boolean visibleInGame(Minecraft mc) {
		return latency(mc) != null;
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		Integer ms = latency(mc);
		if (ms == null) {
			if (!preview) return;
			ms = 42;
		}
		int color = 0;
		if (colorByPing.on()) color = ms < 80 ? 0xFF5BD18B : (ms < 150 ? 0xFFFFE066 : 0xFFF2706B);
		out.add(new Line(ms + " ms", color));
	}
}
