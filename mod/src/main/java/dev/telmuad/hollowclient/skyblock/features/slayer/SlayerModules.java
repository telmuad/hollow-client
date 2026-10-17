package dev.telmuad.hollowclient.skyblock.features.slayer;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.render.EntityHighlights;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.Prices;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import dev.telmuad.hollowclient.skyblock.parse.SidebarParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** The Slayers area's modules. */
public final class SlayerModules {
	private SlayerModules() {
	}

	/** Your boss right now (found from its "Spawned by" tag), shared by the HUD and the highlight. */
	static Slayer.Boss boss;
	private static long scannedAt;

	public static Slayer.Boss scan(Minecraft mc) {
		if (System.currentTimeMillis() - scannedAt < 200) return boss;
		scannedAt = System.currentTimeMillis();
		if (mc.player == null || mc.level == null || !SkyBlock.active()) return boss = null;
		List<Slayer.Thing> labels = new ArrayList<>();
		List<Slayer.Thing> mobs = new ArrayList<>();
		AABB area = mc.player.getBoundingBox().inflate(40);
		for (Entity e : mc.level.getEntities(mc.player, area)) {
			if (e instanceof ArmorStand a) {
				if (a.getCustomName() != null) labels.add(new Slayer.Thing(a.getId(), a.getX(), a.getY(), a.getZ(), LegacyText.of(a.getCustomName())));
			} else if (e instanceof LivingEntity l && !(e instanceof Player)) {
				mobs.add(new Slayer.Thing(l.getId(), l.getX(), l.getY(), l.getZ(), ""));
			}
		}
		return boss = Slayer.findBoss(labels, mobs, mc.player.getGameProfile().name());
	}

	/** Slayer HUD: quest progress, your boss's tags and fight time, kills per hour, drops and their value. */
	public static final class SlayerHud extends SkyBlockHudModule {
		private final Slayer.Stats stats = new Slayer.Stats();
		private final Slayer.Drops drops = new Slayer.Drops();
		private boolean questActive;
		private long killedAt = -1;

		public SlayerHud() {
			super("sb_slayer", "Slayer", "Quest progress, your boss's health and tags, fight time, bosses per hour and drop value.", "Slayers", true,
					at(Anchor.START, 2, Anchor.START, 120));
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.SlayerQuestStarted.class, e -> questActive = true);
			SkyBlock.bus().on(SbEvent.SlayerBossSpawned.class, e -> stats.spawned(System.currentTimeMillis()));
			SkyBlock.bus().on(SbEvent.SlayerQuestCompleted.class, e -> {
				stats.killed(System.currentTimeMillis());
				killedAt = System.currentTimeMillis();
				questActive = false;
			});
			SkyBlock.bus().on(SbEvent.SlayerQuestFailed.class, e -> {
				stats.failed();
				questActive = false;
			});
			SkyBlock.bus().on(SbEvent.RareDrop.class, e -> {
				// only drops while slaying: during a quest or just after the boss dies
				if (!questActive && stats.fightMs(System.currentTimeMillis()) < 0 && (killedAt < 0 || System.currentTimeMillis() - killedAt > 15_000)) return;
				Prices p = SkyBlock.data().prices();
				String id = p.idForName(e.item());
				double each = 0;
				if (id != null) {
					Prices.Bazaar b = p.bazaar(id);
					each = b != null && b.sell() > 0 ? b.sell() : p.lowestBin(id).orElse(0);
				}
				drops.add(e.item(), e.count(), each);
			});
		}

		public Slayer.Stats stats() {
			return stats;
		}

		public Slayer.Drops drops() {
			return drops;
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && (SkyBlock.state().sidebar().slayer() != null || stats.kills() > 0);
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			if (!SkyBlock.active()) {
				if (!preview) return;
				out.add(new Line("Sven Packmaster IV", 0xFFAA00AA));
				out.add(new Line("☠ Sven Packmaster 1.2M❤  ·  0m 34s", 0xFFF2706B));
				out.add(new Line("12 bosses · 1m 05s avg · 48/h", 0));
				out.add(new Line("Drops 3.4M · 13.6M/h", 0xFFF5C542));
				return;
			}
			SidebarParser.Slayer q = SkyBlock.state().sidebar().slayer();
			if (q != null) {
				out.add(new Line(q.boss() + " " + SbText.toRoman(q.tier()), 0xFFAA00AA));
				if (!q.bossUp() && q.needed() > 0) {
					out.add(new Line(SbText.grouped(q.current()) + "/" + SbText.grouped(q.needed()) + " " + q.unit()
							+ String.format(java.util.Locale.ROOT, " (%.0f%%)", 100.0 * q.current() / q.needed()), 0));
				}
			}
			Slayer.Boss b = scan(mc);
			long fight = stats.fightMs(now);
			if (b != null) {
				for (String label : b.labels()) out.add(new Line(label, 0xFFF2706B));
			}
			if (fight >= 0) out.add(new Line("Fight " + SbText.duration(fight), 0xFFB8BCCB));
			if (stats.kills() > 0) {
				double ph = stats.perHour(now);
				out.add(new Line(stats.kills() + " bosses · " + SbText.duration(stats.averageKillMs()) + " avg"
						+ (Double.isNaN(ph) ? "" : String.format(java.util.Locale.ROOT, " · %.0f/h", ph)), 0));
			}
			if (drops.total() > 0) {
				double hours = stats.since() < 0 ? 0 : (now - stats.since()) / 3_600_000.0;
				out.add(new Line("Drops " + SbText.shortNumber(drops.total()) + (hours > 0.02 ? " · " + SbText.shortNumber(drops.total() / hours) + "/h" : ""),
						0xFFF5C542));
			}
		}
	}

	/** A soft box around your own slayer boss, only where you can see it (blocks hide it). */
	public static final class BossHighlight extends SkyBlockModule {
		private final ColorSetting color = add(new ColorSetting("color", "Color", 0x40FF5555));

		public BossHighlight() {
			super("sb_boss_highlight", "Boss Highlight", "A soft box around your own slayer boss (found from its \"Spawned by\" tag). Never other players' bosses, and walls hide it.", "Slayers", false);
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (!live() || mc.level == null) {
				EntityHighlights.set(id(), List.of());
				return;
			}
			Slayer.Boss b = scan(mc);
			if (b == null || b.entityId() < 0) {
				EntityHighlights.set(id(), List.of());
				return;
			}
			Entity e = mc.level.getEntity(b.entityId());
			if (e == null) {
				EntityHighlights.set(id(), List.of());
				return;
			}
			EntityHighlights.set(id(), List.of(new EntityHighlights.Box(e.getBoundingBox().inflate(0.05), color.argb())));
		}

		@Override
		protected void onDisable() {
			EntityHighlights.set(id(), List.of());
		}
	}
}
