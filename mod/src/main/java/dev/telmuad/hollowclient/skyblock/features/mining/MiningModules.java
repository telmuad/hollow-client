package dev.telmuad.hollowclient.skyblock.features.mining;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.render.EntityHighlights;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.parse.ActionBarParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The Mining area's HUD elements. */
public final class MiningModules {
	private MiningModules() {
	}

	private static final Island[] MINES = {Island.DWARVEN_MINES, Island.CRYSTAL_HOLLOWS, Island.MINESHAFT};

	/** Commissions, the mining event, Cold or Heat and today's Sky Mall buff. */
	public static final class MiningHud extends SkyBlockHudModule {
		private final BooleanSetting doneAlert = add(new BooleanSetting("doneAlert", "Commission done alert", true));
		private final BooleanSetting coldAlert = add(new BooleanSetting("coldAlert", "Cold warning at -75", true));
		private final Set<String> done = new HashSet<>();
		private boolean coldWarned;
		private boolean seeded;
		private String skyMall = "";

		public MiningHud() {
			super("sb_mining", "Mining", "Commissions and their progress, the mining event, Cold or Heat and today's Sky Mall buff.", "Mining", true,
					at(Anchor.START, 2, Anchor.START, 120), MINES);
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> {
				if (e.match().name().equals("sky_mall_buff")) skyMall = e.match().get("buff");
			});
			SkyBlock.bus().on(SbEvent.IslandChanged.class, e -> {
				done.clear();
				seeded = false;
			});
		}

		@Override
		public void onTick() {
			if (!live()) return;
			List<Mining.Commission> list = Mining.commissions(SkyBlock.state().tab());
			if (!seeded && !list.isEmpty()) {
				// ones already done when you arrive were alerted before (or you know): no alert for them
				seeded = true;
				for (Mining.Commission c : list) if (c.done()) done.add(c.name());
			}
			for (Mining.Commission c : list) {
				if (c.done() && done.add(c.name()) && doneAlert.get()) {
					SkyBlockAlerts.notify("commission_done", "§a§lCommission done", "§e" + c.name() + " §7· claim it", "DING", true);
				} else if (!c.done()) {
					done.remove(c.name());
				}
			}
			int cold = Mining.cold(SkyBlock.state().sidebar());
			if (cold <= -75 && !coldWarned && coldAlert.get()) {
				coldWarned = true;
				SkyBlockAlerts.notify("cold", "§b§lCold " + cold + "❄", "§7At -100 you're sent back to camp", "ANVIL", false);
			} else if (cold > -60) {
				coldWarned = false;
			}
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			if (!SkyBlock.active()) {
				if (!preview) return;
				out.add(new Line("Event: 2X POWDER · 12m 30s", 0xFFF5C542));
				out.add(new Line("Royal Mines Mithril 45%", 0));
				out.add(new Line("✔ Goblin Slayer", 0xFF5BD18B));
				out.add(new Line("Cold -32❄", 0xFF7FD6F5));
				out.add(new Line("Sky Mall: +100 Mining Speed", 0xFFB8BCCB));
				return;
			}
			Mining.Event ev = Mining.event(SkyBlock.state().tab());
			if (!ev.name().isEmpty()) out.add(new Line("Event: " + ev.name() + (ev.endsIn().isEmpty() ? "" : " · " + ev.endsIn()), 0xFFF5C542));
			for (Mining.Commission c : Mining.commissions(SkyBlock.state().tab())) {
				out.add(c.done() ? new Line("✔ " + c.name(), 0xFF5BD18B)
						: new Line(c.name() + " " + String.format(Locale.ROOT, "%.0f%%", c.percent()), 0));
			}
			int cold = Mining.cold(SkyBlock.state().sidebar());
			if (cold <= 0) out.add(new Line("Cold " + cold + "❄", cold <= -75 ? 0xFFF2706B : cold <= -50 ? 0xFFF5C542 : 0xFF7FD6F5));
			int heat = Mining.heat(SkyBlock.state().sidebar());
			if (heat >= 0) out.add(new Line("Heat " + heat + "♨", heat >= 90 ? 0xFFF2706B : heat >= 60 ? 0xFFF5C542 : 0xFFE08850));
			if (!skyMall.isEmpty() && SkyBlock.state().island() != Island.MINESHAFT) out.add(new Line("Sky Mall: " + skyMall, 0xFFB8BCCB));
		}
	}

	/** Powder totals from the Powders widget, what you've gained this session and per hour. */
	public static final class PowderHud extends SkyBlockHudModule {
		private final Map<Mining.Powder, Mining.Gain> gains = new java.util.EnumMap<>(Mining.Powder.class);

		public PowderHud() {
			super("sb_powder", "Powder", "Mithril, Gemstone and Glacite powder from the tab list, gained this session and per hour.", "Mining", true,
					at(Anchor.START, 2, Anchor.START, 200), MINES);
			for (Mining.Powder p : Mining.Powder.values()) gains.put(p, new Mining.Gain());
		}

		public Mining.Gain gain(Mining.Powder p) {
			return gains.get(p);
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ProfileChanged.class, e -> gains.values().forEach(Mining.Gain::reset));
		}

		@Override
		public void onTick() {
			if (!live()) return;
			long now = System.currentTimeMillis();
			Mining.powders(SkyBlock.state().tab()).forEach((p, amount) -> gains.get(p).update(now, amount));
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			Map<Mining.Powder, Long> amounts = SkyBlock.active() ? Mining.powders(SkyBlock.state().tab()) : Map.of();
			if (amounts.isEmpty()) {
				if (!preview) return;
				out.add(new Line("Mithril 1.2M  +34.5k · 52k/h", Mining.Powder.MITHRIL.color));
				out.add(new Line("Gemstone 845k  +12k · 18k/h", Mining.Powder.GEMSTONE.color));
				out.add(new Line("Glacite 230k", Mining.Powder.GLACITE.color));
				return;
			}
			amounts.forEach((p, amount) -> {
				Mining.Gain g = gains.get(p);
				double ph = g.perHour(now);
				String extra = g.gained() > 0 ? "  +" + SbText.shortNumber(g.gained()) + (Double.isNaN(ph) ? "" : " · " + SbText.shortNumber(ph) + "/h") : "";
				out.add(new Line(p.label + " " + SbText.shortNumber(amount) + extra, p.color));
			});
		}
	}

	/** Your pickaxe ability's cooldown, from the "used" and "now available" chat lines. */
	public static final class AbilityHud extends SkyBlockHudModule {
		private final BooleanSetting readyAlert = add(new BooleanSetting("readyAlert", "Alert when ready", false));
		private final Mining.Ability ability = new Mining.Ability();

		public AbilityHud() {
			super("sb_pickaxe_ability", "Pickaxe Ability", "Your pickaxe ability's cooldown (Mining Speed Boost, Pickobulus, …) from its chat lines.",
					"Mining", true, at(Anchor.CENTER, 0, Anchor.END, 84));
			readyAlert.describe("Hypixel also says it in chat; this adds a title and sound.");
		}

		public Mining.Ability ability() {
			return ability;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> {
				long now = System.currentTimeMillis();
				switch (e.match().name()) {
					case "pickaxe_ability_used" -> ability.used(e.match().get("ability"), now);
					case "pickaxe_ability_ready" -> {
						ability.available(e.match().get("ability"), now);
						if (readyAlert.get() && live()) SkyBlockAlerts.notify("pickaxe_ability", "§6" + e.match().get("ability"), "§aready", "PLING", false);
					}
					default -> {
					}
				}
			});
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && !ability.name().isEmpty();
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			if (ability.name().isEmpty()) {
				if (preview) out.add(new Line("Mining Speed Boost: 1m 12s", 0xFFF5C542));
				return;
			}
			long left = ability.left(now);
			out.add(left <= 0 ? new Line(ability.name() + ": ready", 0xFF5BD18B)
					: new Line(ability.name() + ": " + (ability.estimated() ? "~" : "") + SbText.duration(left), 0xFFF5C542));
		}
	}

	/** What's in your Forge and when it's done; kept between sessions, so it counts down anywhere. */
	public static final class ForgeHud extends SkyBlockHudModule {
		private final BooleanSetting everywhere = add(new BooleanSetting("everywhere", "Show on every island", false));
		private final BooleanSetting readyAlert = add(new BooleanSetting("readyAlert", "Alert when something's done", true));
		private List<Mining.ForgeSlot> slots = List.of();
		private String loadedFor;
		private final Set<Integer> alerted = new HashSet<>();

		public ForgeHud() {
			super("sb_forge", "Forge", "What's in your Forge and when each slot is done (read from the Forges tab widget, kept between sessions).",
					"Mining", true, at(Anchor.END, 2, Anchor.START, 2));
		}

		public List<Mining.ForgeSlot> slots() {
			return slots;
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			if (!super.visibleInGame(mc) || slots.stream().allMatch(Mining.ForgeSlot::empty)) return false;
			Island i = SkyBlock.state().island();
			return everywhere.get() || i == Island.DWARVEN_MINES || i == Island.CRYSTAL_HOLLOWS || i == Island.MINESHAFT;
		}

		@Override
		public void onTick() {
			if (!live()) return;
			String key = SkyBlock.state().profileKey();
			if (!key.equals(loadedFor)) {
				loadedFor = key;
				slots = load(key);
				alerted.clear();
				long now = System.currentTimeMillis();
				for (Mining.ForgeSlot s : slots) if (!s.empty() && s.readyAt() <= now) alerted.add(s.slot());
			}
			long now = System.currentTimeMillis();
			List<Mining.ForgeSlot> read = Mining.forge(SkyBlock.state().tab(), now);
			if (!read.isEmpty() && SkyBlock.state().tabAt() > 0 && changed(read)) {
				slots = read;
				save(key);
			}
			for (Mining.ForgeSlot s : slots) {
				if (s.empty() || s.readyAt() > now) {
					alerted.remove(s.slot());
				} else if (alerted.add(s.slot()) && readyAlert.get()) {
					SkyBlockAlerts.notify("forge_ready", "§6§lForge", "§e" + s.item() + " §ais done", "DING", true);
				}
			}
		}

		/** A reading counts as new when the items changed or a time moved more than a minute. */
		private boolean changed(List<Mining.ForgeSlot> read) {
			if (read.size() != slots.size()) return true;
			for (int i = 0; i < read.size(); i++) {
				Mining.ForgeSlot a = read.get(i), b = slots.get(i);
				if (a.slot() != b.slot() || !a.item().equals(b.item()) || Math.abs(a.readyAt() - b.readyAt()) > 60_000) return true;
			}
			return false;
		}

		private static List<Mining.ForgeSlot> load(String key) {
			List<Mining.ForgeSlot> out = new ArrayList<>();
			JsonObject o = SkyBlock.store().read(key, "forge");
			if (o.get("slots") instanceof JsonArray a) {
				for (JsonElement e : a) {
					if (!(e instanceof JsonObject s)) continue;
					try {
						out.add(new Mining.ForgeSlot(s.get("slot").getAsInt(), s.get("item").getAsString(), s.get("readyAt").getAsLong()));
					} catch (RuntimeException ignored) {
						// a broken entry is skipped
					}
				}
			}
			return out;
		}

		private void save(String key) {
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			for (Mining.ForgeSlot s : slots) {
				JsonObject e = new JsonObject();
				e.addProperty("slot", s.slot());
				e.addProperty("item", s.item());
				e.addProperty("readyAt", s.readyAt());
				a.add(e);
			}
			o.add("slots", a);
			try {
				SkyBlock.store().write(key, "forge", o);
			} catch (IOException e) {
				HollowClient.LOG.warn("Forge times couldn't be saved: {}", e.toString());
			}
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			List<Mining.ForgeSlot> show = slots;
			if (show.isEmpty() || !SkyBlock.active()) {
				if (!preview) return;
				show = List.of(new Mining.ForgeSlot(1, "Refined Diamond", 0), new Mining.ForgeSlot(2, "Gemstone Mixture", now + 3_600_000L * 3 + 600_000));
			}
			out.add(new Line("Forge", 0xFFE08850));
			for (Mining.ForgeSlot s : show) {
				if (s.empty()) continue;
				long left = s.readyAt() - now;
				out.add(left <= 0 ? new Line(s.slot() + ") " + s.item() + ": done", 0xFF5BD18B)
						: new Line(s.slot() + ") " + s.item() + ": " + SbText.duration(left), 0));
			}
		}
	}

	/** Crystal Hollows: which crystals you've found for the Nucleus. */
	public static final class CrystalsHud extends SkyBlockHudModule {
		public CrystalsHud() {
			super("sb_crystals", "Crystals", "The Crystal Hollows crystals you've found or placed, from the Crystals tab widget.", "Mining", true,
					at(Anchor.START, 2, Anchor.START, 240), Island.CRYSTAL_HOLLOWS);
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			Map<String, String> crystals = SkyBlock.active() ? Mining.crystals(SkyBlock.state().tab()) : Map.of();
			if (crystals.isEmpty()) {
				if (!preview) return;
				crystals = Map.of("Jade", "Found", "Amber", "Not Found", "Amethyst", "Placed");
			}
			List<String> have = new ArrayList<>(), missing = new ArrayList<>();
			crystals.forEach((name, state) -> (Mining.crystalFound(state) ? have : missing).add(name));
			out.add(new Line("Crystals " + have.size() + "/" + crystals.size(), 0xFFE070E0));
			if (!have.isEmpty()) out.add(new Line("✔ " + String.join(", ", have), 0xFF5BD18B));
			if (!missing.isEmpty()) out.add(new Line("✖ " + String.join(", ", missing), 0xFFB8BCCB));
		}
	}

	/**
	 * Mines of Divan: a guess at the treasure from the metal detector's readings at different spots, marked on the
	 * floor above it (walls and blocks hide the marker). Hold the detector and walk around a little.
	 */
	public static final class MetalDetectorHud extends SkyBlockHudModule {
		private final BooleanSetting marker = add(new BooleanSetting("marker", "Mark the guess on the floor", true));
		private final MetalDetector detector = new MetalDetector();
		private long seenAt;
		private double lastDistance = -1;
		private double floorY;

		public MetalDetectorHud() {
			super("sb_metal_detector", "Metal Detector", "Mines of Divan: guesses where the treasure is from the detector's readings at a few spots "
					+ "and marks the floor above it. Walk around a little while holding the detector.", "Mining", true,
					at(Anchor.CENTER, 0, Anchor.START, 2), Island.CRYSTAL_HOLLOWS);
		}

		public MetalDetector detector() {
			return detector;
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && System.currentTimeMillis() - seenAt < 10_000;
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (!live() || mc.player == null) {
				EntityHighlights.set(id(), List.of());
				return;
			}
			ActionBarParser.ActionBar bar = SkyBlock.state().actionBar();
			long at = SkyBlock.state().actionBarAt();
			if (bar != null && at != seenAt) {
				double d = Mining.treasureDistance(bar.text());
				if (d >= 0) {
					if (System.currentTimeMillis() - seenAt > 60_000) detector.clear();
					seenAt = at;
					lastDistance = d;
					floorY = mc.player.getY();
					detector.add(new MetalDetector.Reading(mc.player.getX(), mc.player.getY(), mc.player.getZ(), d));
				}
			}
			MetalDetector.Guess g = detector.guess();
			if (g == null || !marker.get() || System.currentTimeMillis() - seenAt > 10_000) {
				EntityHighlights.set(id(), List.of());
				return;
			}
			double bx = Math.floor(g.x()), bz = Math.floor(g.z());
			EntityHighlights.set(id(), List.of(new EntityHighlights.Box(new AABB(bx, floorY, bz, bx + 1, floorY + 1.6, bz + 1), 0x50F5C542)));
		}

		@Override
		protected void onDisable() {
			EntityHighlights.set(id(), List.of());
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			MetalDetector.Guess g = detector.guess();
			if (lastDistance < 0 || mc.player == null) {
				if (!preview) return;
				out.add(new Line("Treasure 8.5 m", 0xFFF5C542));
				out.add(new Line("3 down · 6 m NE (±0.4)", 0));
				return;
			}
			out.add(new Line("Treasure " + String.format(Locale.ROOT, "%.1f m", lastDistance), 0xFFF5C542));
			if (g == null) {
				out.add(new Line("Walk around (" + detector.count() + "/4 spots)", 0xFFB8BCCB));
				return;
			}
			double dx = g.x() - mc.player.getX(), dz = g.z() - mc.player.getZ();
			int down = (int) Math.round(mc.player.getY() - g.y());
			out.add(new Line(String.format(Locale.ROOT, "%d down · %.0f m %s (±%.1f)", Math.max(0, down), Math.hypot(dx, dz), compass(dx, dz),
					g.error()), g.error() < 1 ? 0 : 0xFFF5C542));
		}

		static String compass(double dx, double dz) {
			if (Math.hypot(dx, dz) < 1) return "here";
			String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
			double deg = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = south (+z), 90 = west (-x)
			return names[(int) Math.floorMod(Math.round(deg / 45), 8)];
		}
	}
}
