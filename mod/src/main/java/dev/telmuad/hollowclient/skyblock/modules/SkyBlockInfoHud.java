package dev.telmuad.hollowclient.skyblock.modules;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Where you are and what you have: island and area, the SkyBlock date and time, purse, bits (motes in the Rift, copper on the Garden). */
public final class SkyBlockInfoHud extends SkyBlockHudModule {
	private static final int GOLD = 0xFFF5C542, AQUA = 0xFF55FFFF, PINK = 0xFFFF77DD, COPPER = 0xFFE08850, DIM = 0xFFB8BCCB;

	private final BooleanSetting showPlace = add(new BooleanSetting("place", "Island and area", true));
	private final BooleanSetting showDate = add(new BooleanSetting("date", "SkyBlock date and time", true));
	private final BooleanSetting showPurse = add(new BooleanSetting("purse", "Purse", true));
	private final BooleanSetting showBits = add(new BooleanSetting("bits", "Bits", true));
	private final BooleanSetting showExtra = add(new BooleanSetting("extra", "Motes, copper", true));

	public SkyBlockInfoHud() {
		super("sb_info", "SkyBlock Info", "Island and area, SkyBlock date and time, purse and bits.", "General", true,
				at(Anchor.START, 2, Anchor.START, 40));
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		SkyBlockState s = SkyBlock.state();
		boolean sample = preview && !SkyBlock.active();
		if (showPlace.on()) {
			String island = sample ? "Hub" : s.island() == Island.UNKNOWN ? "SkyBlock" : s.island().displayName();
			String area = sample ? "Village" : s.area();
			out.add(new Line((s.sidebar().rift() && !sample ? "ф " : "⏣ ") + island + (area.isEmpty() || area.equals(island) ? "" : " · " + area), 0));
		}
		if (showDate.on()) {
			String date = sample ? "Early Autumn 13th" : s.skyDate();
			String time = sample ? "4:20pm" : s.skyTime();
			if (!date.isEmpty() || !time.isEmpty()) out.add(new Line((date + " " + time).strip(), DIM));
		}
		if (showPurse.on()) {
			long purse = sample ? 1_251_000 : s.purse();
			if (purse >= 0) out.add(new Line((s.sidebar().piggy() ? "Piggy " : "Purse ") + SbText.shortNumber(purse), GOLD));
		}
		if (showBits.on()) {
			long bits = sample ? 1484 : s.bits();
			if (bits >= 0) out.add(new Line("Bits " + SbText.grouped(bits), AQUA));
		}
		if (showExtra.on() && !sample) {
			if (s.motes() >= 0 && s.island() == Island.THE_RIFT) out.add(new Line("Motes " + SbText.grouped(s.motes()), PINK));
			if (s.copper() >= 0 && s.island() == Island.GARDEN) out.add(new Line("Copper " + SbText.grouped(s.copper()), COPPER));
		}
	}

	@Override
	public Health health() {
		Health base = super.health();
		if (!base.ok()) return base;
		return SkyBlock.state().purse() >= 0 || SkyBlock.state().motes() >= 0 ? Health.ok("Reading the scoreboard")
				: Health.missing("No purse line on the scoreboard yet");
	}
}
