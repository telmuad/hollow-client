package dev.telmuad.hollowclient.skyblock.features.fishing;

import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** The Fishing area's HUD elements. */
public final class FishingModules {
	private FishingModules() {
	}

	/**
	 * Fishing HUD: how long your bobber's been out, "Fish on!" when the "!!!" shows over it, sea creatures near you
	 * (from their name tags) and this session's rare catches.
	 */
	public static final class FishingHud extends SkyBlockHudModule {
		private final BooleanSetting rareAlert = add(new BooleanSetting("rareAlert", "Rare catch alert", true));
		private final BooleanSetting capAlert = add(new BooleanSetting("capAlert", "Sea creature cap alert", true));
		private final BooleanSetting biteSound = add(new BooleanSetting("biteSound", "Sound when a fish bites", false));
		private final Fishing.Catches catches = new Fishing.Catches();
		private long castAt = -1;
		private boolean biting;
		private int nearby;
		private long activeAt;
		private long capAlertAt;
		private int doubleHooks;

		public FishingHud() {
			super("sb_fishing", "Fishing", "Bobber time, \"Fish on!\" when the !!! shows over your bobber, sea creatures near you and your rare catches.",
					"Fishing", true, at(Anchor.START, 2, Anchor.START, 150));
			biteSound.describe("Only a sound: you still reel in yourself.");
		}

		public Fishing.Catches catches() {
			return catches;
		}

		public int nearby() {
			return nearby;
		}

		public boolean biting() {
			return biting;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatLine.class, e -> {
				long now = System.currentTimeMillis();
				String rare = Fishing.rareCatch(e.line());
				if (rare != null) {
					catches.caught(rare, now);
					activeAt = now;
					if (rareAlert.get() && live()) {
						boolean mythic = Fishing.mythic(rare);
						SkyBlockAlerts.notify("rare_sea_creature", (mythic ? "§d§l" : "§6§l") + rare, mythic ? "§dMythic sea creature" : "§6Legendary sea creature",
								mythic ? "LEVEL_UP" : "BELL", true);
					}
				} else if (Fishing.capReached(e.line())) {
					if (capAlert.get() && live() && now - capAlertAt > 30_000) {
						capAlertAt = now;
						SkyBlockAlerts.notify("sea_creature_cap", "§c§lSea creature cap", "§7Kill some to make space", "ANVIL", false);
					}
				} else if (Fishing.doubleHook(e.line())) {
					doubleHooks++;
				}
			});
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (!live() || mc.player == null || mc.level == null) {
				castAt = -1;
				biting = false;
				return;
			}
			long now = System.currentTimeMillis();
			FishingHook hook = mc.player.fishing;
			boolean wasBiting = biting;
			biting = false;
			if (hook == null) {
				castAt = -1;
			} else {
				if (castAt < 0) castAt = now;
				activeAt = now;
				for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, hook.getBoundingBox().inflate(1.5, 3, 1.5))) {
					if (a.getCustomName() != null && Fishing.bite(LegacyText.of(a.getCustomName()))) biting = true;
				}
			}
			if (biting && !wasBiting && biteSound.get()) SkyBlockAlerts.play(mc, SkyBlockAlerts.Sound.PLING);
			if (mc.player.tickCount % 10 == 0) {
				int n = 0;
				for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(24))) {
					if (e instanceof ArmorStand a && a.getCustomName() != null && Fishing.seaCreature(LegacyText.of(a.getCustomName())) != null) n++;
				}
				nearby = n;
			}
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && System.currentTimeMillis() - activeAt < 120_000;
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			if (!SkyBlock.active()) {
				if (!preview) return;
				out.add(new Line("Fish on!", 0xFFF2706B));
				out.add(new Line("Bobber 14s  ·  Sea creatures near you: 4", 0));
				out.add(new Line("Thunder ×2 (12m ago) · Lord Jawbus ×1", 0xFFF5C542));
				return;
			}
			if (biting) out.add(new Line("Fish on!", 0xFFF2706B));
			String bobber = castAt >= 0 ? "Bobber " + SbText.duration(now - castAt) : "";
			String near = "Sea creatures near you: " + nearby;
			out.add(new Line(bobber.isEmpty() ? near : bobber + "  ·  " + near, nearby >= 10 ? 0xFFF5C542 : 0));
			List<String> recent = catches.recent();
			if (!recent.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				Map<String, Integer> counts = catches.counts();
				for (int i = 0; i < Math.min(3, recent.size()); i++) {
					String c = recent.get(i);
					if (!sb.isEmpty()) sb.append(" · ");
					sb.append(c).append(" ×").append(counts.get(c));
					if (i == 0) sb.append(" (").append(SbText.duration(now - catches.lastAt(c))).append(" ago)");
				}
				out.add(new Line(sb.toString(), 0xFFF5C542));
			}
			if (doubleHooks > 0) out.add(new Line("Double hooks: " + doubleHooks, 0xFFB8BCCB));
		}
	}

	/** Crimson Isle: trophy fish caught this session and in total (kept per profile), by tier. */
	public static final class TrophyFishHud extends SkyBlockHudModule {
		private final Fishing.Trophies session = new Fishing.Trophies();
		private final Fishing.Trophies total = new Fishing.Trophies();
		private String loadedFor;

		public TrophyFishHud() {
			super("sb_trophy_fish", "Trophy Fish", "Trophy fish caught this session and since Hollow started counting, by tier (from the TROPHY FISH chat line).",
					"Fishing", true, at(Anchor.START, 2, Anchor.START, 200), Island.CRIMSON_ISLE);
		}

		public Fishing.Trophies session() {
			return session;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatLine.class, e -> {
				Fishing.Trophy t = Fishing.trophy(e.line());
				if (t == null || !SkyBlock.active()) return;
				ensureLoaded();
				session.add(t);
				total.add(t);
				save();
			});
			SkyBlock.bus().on(SbEvent.ProfileChanged.class, e -> session.clear());
		}

		private void ensureLoaded() {
			String key = SkyBlock.state().profileKey();
			if (key.equals(loadedFor)) return;
			loadedFor = key;
			total.clear();
			JsonObject o = SkyBlock.store().read(key, "trophy_fish");
			for (String fish : o.keySet()) {
				if (!(o.get(fish) instanceof JsonObject tiers)) continue;
				for (Fishing.Tier tier : Fishing.Tier.values()) {
					int n = tiers.has(tier.name()) ? tiers.get(tier.name()).getAsInt() : 0;
					for (int i = 0; i < n; i++) total.add(new Fishing.Trophy(fish, tier));
				}
			}
		}

		private void save() {
			JsonObject o = new JsonObject();
			total.all().forEach((fish, tiers) -> {
				JsonObject t = new JsonObject();
				tiers.forEach((tier, n) -> t.addProperty(tier.name(), n));
				o.add(fish, t);
			});
			try {
				SkyBlock.store().write(loadedFor, "trophy_fish", o);
			} catch (IOException e) {
				HollowClient.LOG.warn("Trophy fish counts couldn't be saved: {}", e.toString());
			}
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && session.total() > 0;
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			Fishing.Trophies s = session;
			if (s.total() == 0) {
				if (!preview) return;
				s = new Fishing.Trophies();
				s.add(new Fishing.Trophy("Blobfish", Fishing.Tier.BRONZE));
				s.add(new Fishing.Trophy("Blobfish", Fishing.Tier.SILVER));
				s.add(new Fishing.Trophy("Lavahorse", Fishing.Tier.GOLD));
			}
			out.add(new Line("Trophy fish: " + s.total() + " this session", 0xFFE08850));
			StringBuilder tiers = new StringBuilder();
			for (Fishing.Tier t : Fishing.Tier.values()) {
				if (!tiers.isEmpty()) tiers.append("  ");
				tiers.append(t.name().charAt(0)).append(s.total(t));
			}
			out.add(new Line(tiers.toString(), 0));
			// the six most caught, each in the color of its best tier
			s.all().entrySet().stream()
					.sorted((a, b) -> Integer.compare(b.getValue().values().stream().mapToInt(Integer::intValue).sum(),
							a.getValue().values().stream().mapToInt(Integer::intValue).sum()))
					.limit(6)
					.forEach(e -> {
						Fishing.Tier best = null;
						for (Fishing.Tier t : Fishing.Tier.values()) if (e.getValue().getOrDefault(t, 0) > 0) best = t;
						int n = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
						out.add(new Line(e.getKey() + " ×" + n, best == null ? 0 : best.color));
					});
		}
	}
}
