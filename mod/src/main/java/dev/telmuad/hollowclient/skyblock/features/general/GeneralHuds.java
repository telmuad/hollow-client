package dev.telmuad.hollowclient.skyblock.features.general;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.SkyCalendar;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small General-area HUD elements: pet, next events, deployables. */
public final class GeneralHuds {
	private GeneralHuds() {
	}

	/** Your active pet from the tab list's Pet widget: name, level and XP to the next level. */
	public static final class PetHud extends SkyBlockHudModule {
		public PetHud() {
			super("sb_pet", "Pet", "Your active pet, its level and XP progress (from the tab list's Pet widget).", "General", false,
					at(Anchor.START, 2, Anchor.START, 92));
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			GeneralParse.PetStatus p = SkyBlock.active() ? GeneralParse.pet(SkyBlock.state().tab()) : null;
			if (p == null && preview) p = new GeneralParse.PetStatus("Golden Dragon", 100, "", 100, true);
			if (p == null) return;
			out.add(new Line("Pet: " + p.name() + (p.level() > 0 ? " · " + p.level() : ""), 0xFFF5C542));
			if (p.max()) out.add(new Line("MAX LEVEL", 0xFFB8BCCB));
			else if (!p.xp().isEmpty()) out.add(new Line(p.xp() + " XP (" + p.percent() + "%)", 0xFFB8BCCB));
		}

		@Override
		public Health health() {
			Health h = super.health();
			if (!h.ok()) return h;
			return SkyBlock.state().tab().pet().isEmpty() ? Health.missing("No Pet widget in the tab list (turn it on in Hypixel's tab widget settings)")
					: Health.ok("Reading the Pet widget");
		}
	}

	/** The next SkyBlock events with countdowns (Dark Auction, Jacob's contest, festivals, ...). */
	public static final class NextEventsHud extends SkyBlockHudModule {
		private final NumberSetting count = add(new NumberSetting("count", "Events shown", 3, 1, 6, 1).format(0, ""));

		public NextEventsHud() {
			super("sb_next_events", "Next Events", "The next SkyBlock events with countdowns: Dark Auction, Jacob's contests, festivals and more.",
					"General", false, at(Anchor.END, 2, Anchor.START, 40));
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis() / 1000;
			var perks = SkyBlock.data().election().mayor().allPerks();
			for (SkyCalendar.Event e : SkyCalendar.events(now, perks, count.get().intValue())) {
				String when = e.running(now) ? SbText.duration((e.end() - now) * 1000) + " left" : "in " + SbText.duration((e.start() - now) * 1000);
				out.add(new Line(e.name() + ": " + when, e.running(now) ? 0xFF5BD18B : 0));
			}
		}
	}

	/** Power orbs, flares and totems near you and the seconds they have left (from their visible name tags). */
	public static final class DeployablesHud extends SkyBlockHudModule {
		private final NumberSetting range = add(new NumberSetting("range", "Range", 30, 5, 60, 1).format(0, " blocks"));
		private List<GeneralParse.Deployable> found = List.of();
		private long scannedAt;

		public DeployablesHud() {
			super("sb_deployables", "Deployables", "Power orbs, flares and totems near you with the time they have left.", "General", false,
					at(Anchor.END, 2, Anchor.END, 70));
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			if (SkyBlock.active() && mc.level != null && mc.player != null && System.currentTimeMillis() - scannedAt > 250) {
				scannedAt = System.currentTimeMillis();
				List<GeneralParse.Deployable> list = new ArrayList<>();
				double r = range.get();
				for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(r))) {
					if (!stand.isCustomNameVisible() || stand.getCustomName() == null) continue;
					GeneralParse.Deployable d = GeneralParse.deployable(dev.telmuad.hollowclient.skyblock.mc.LegacyText.of(stand.getCustomName()));
					if (d != null) list.add(d);
				}
				list.sort(Comparator.comparingInt(GeneralParse.Deployable::seconds).reversed());
				found = list;
			}
			List<GeneralParse.Deployable> list = found;
			if (list.isEmpty() && preview && !SkyBlock.active()) list = List.of(new GeneralParse.Deployable("Overflux", 45));
			for (GeneralParse.Deployable d : list) out.add(new Line(d.name() + "  " + d.seconds() + "s", d.seconds() <= 5 ? 0xFFF2706B : 0));
		}
	}
}
