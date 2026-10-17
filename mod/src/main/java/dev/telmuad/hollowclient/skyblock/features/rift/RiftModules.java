package dev.telmuad.hollowclient.skyblock.features.rift;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.features.mining.Mining;
import dev.telmuad.hollowclient.skyblock.parse.ActionBarParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.minecraft.client.Minecraft;

import java.util.List;

/** The Rift's HUD elements. */
public final class RiftModules {
	private RiftModules() {
	}

	/** Rift time left (warnings at one minute and 20 seconds) and motes gained this visit, per hour. */
	public static final class RiftHud extends SkyBlockHudModule {
		private final BooleanSetting warnings = add(new BooleanSetting("warnings", "Warn at 1 minute and 20 seconds left", true));
		private final Mining.Gain motes = new Mining.Gain();
		private long timeLeft = -1;
		private long timeAt;
		private boolean warned60, warned20;

		public RiftHud() {
			super("sb_rift", "Rift", "Rift time left with warnings before it runs out, and motes gained this visit and per hour.", "Rift", true,
					at(Anchor.START, 2, Anchor.START, 120), Island.THE_RIFT);
		}

		public Mining.Gain motes() {
			return motes;
		}

		/** Time left now: the last reading counted down (it drains about a second per second). */
		public long timeLeft(long now) {
			return timeLeft < 0 ? -1 : Math.max(0, timeLeft - (now - timeAt));
		}

		@Override
		public void onTick() {
			if (!live()) {
				if (SkyBlock.state().island() != Island.THE_RIFT) {
					timeLeft = -1;
					motes.reset();
				}
				return;
			}
			long now = System.currentTimeMillis();
			ActionBarParser.ActionBar bar = SkyBlock.state().actionBar();
			if (bar != null && bar.riftTimeMs() >= 0 && SkyBlock.state().actionBarAt() != timeAt) {
				timeLeft = bar.riftTimeMs();
				timeAt = SkyBlock.state().actionBarAt();
			}
			motes.update(now, SkyBlock.state().motes());
			long left = timeLeft(now);
			if (left < 0) return;
			if (left > 65_000) warned60 = false;
			if (left > 25_000) warned20 = false;
			if (warnings.get() && left <= 60_000 && left > 20_000 && !warned60) {
				warned60 = true;
				SkyBlockAlerts.notify("rift_time", "§a§l1 minute left", "§7in the Rift", "BELL", false);
			} else if (warnings.get() && left <= 20_000 && !warned20) {
				warned20 = true;
				warned60 = true;
				SkyBlockAlerts.notify("rift_time", "§c§l" + (left / 1000) + " seconds left", "§7in the Rift", "ANVIL", false);
			}
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			if (!SkyBlock.active()) {
				if (!preview) return;
				out.add(new Line("Rift time 5m 20s", 0xFF5BD18B));
				out.add(new Line("Motes 12,345  +840 · 6.2k/h", 0xFFF08CD0));
				return;
			}
			long left = timeLeft(now);
			if (left >= 0) out.add(new Line("Rift time " + SbText.duration(left), left <= 20_000 ? 0xFFF2706B : left <= 60_000 ? 0xFFF5C542 : 0xFF5BD18B));
			long m = SkyBlock.state().motes();
			if (m >= 0) {
				double ph = motes.perHour(now);
				out.add(new Line("Motes " + SbText.grouped(m) + (motes.gained() > 0 ? "  +" + SbText.grouped(motes.gained())
						+ (Double.isNaN(ph) ? "" : " · " + SbText.shortNumber(ph) + "/h") : ""), 0xFFF08CD0));
			}
		}
	}
}
