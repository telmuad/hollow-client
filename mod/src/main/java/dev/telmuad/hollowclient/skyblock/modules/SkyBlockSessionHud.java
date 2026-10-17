package dev.telmuad.hollowclient.skyblock.modules;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.state.SessionStats;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.minecraft.client.Minecraft;

import java.util.List;

/** This SkyBlock session: time, purse change, bits, top skill XP, slayer bosses, rare drops and garden visitors. */
public final class SkyBlockSessionHud extends SkyBlockHudModule {
	private final NumberSetting rows = add(new NumberSetting("rows", "Lines", 6, 2, 9, 1).format(0, ""));

	public SkyBlockSessionHud() {
		super("sb_session", "SkyBlock Session", "Time played, purse change, bits, skill XP, slayer bosses and rare drops this session.",
				"General", false, at(Anchor.END, 2, Anchor.START, 110));
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		List<SessionStats.Row> list = preview && !SkyBlock.active()
				? List.of(new SessionStats.Row("Played", "1h 42m"), new SessionStats.Row("Purse", "+3.42M"),
				new SessionStats.Row("Bits", "+750"), new SessionStats.Row("Farming XP", "+1.18M"),
				new SessionStats.Row("Rare drops", "2"))
				: SkyBlock.session().rows();
		out.add(new Line("Session", 0xFFF5C542));
		int n = 0;
		for (SessionStats.Row r : list) {
			if (n++ >= rows.get().intValue()) break;
			out.add(new Line(r.label() + ": " + r.value(), 0));
		}
	}
}
