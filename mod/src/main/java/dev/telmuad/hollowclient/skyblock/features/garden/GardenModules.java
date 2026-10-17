package dev.telmuad.hollowclient.skyblock.features.garden;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.Contests;
import dev.telmuad.hollowclient.skyblock.data.Prices;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.state.RawItem;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/** The Garden area's HUD elements. */
public final class GardenModules {
	private GardenModules() {
	}

	private static OptionalDouble priceByName(String name) {
		Prices p = SkyBlock.data().prices();
		String id = p.idForName(name);
		if (id == null) return OptionalDouble.empty();
		Prices.Bazaar b = p.bazaar(id);
		if (b != null && b.buy() > 0) return OptionalDouble.of(b.buy());
		return p.lowestBin(id);
	}

	/** Visitors waiting (from the tab list), pests by plot (chat and tab list), copper and Garden level. */
	public static final class GardenHud extends SkyBlockHudModule {
		private final Garden.Pests pests = new Garden.Pests();

		public GardenHud() {
			super("sb_garden", "Garden", "Visitors waiting, pests and their plots, copper and Garden level.", "Garden", true,
					at(Anchor.START, 2, Anchor.START, 130), Island.GARDEN);
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> {
				long now = System.currentTimeMillis();
				switch (e.match().name()) {
					case "pest_spawn" -> pests.spawned(e.match().get("plot"), e.match().get("count").isEmpty() ? 1
							: Integer.parseInt(e.match().get("count")), now);
					case "pest_offline" -> pests.offline(e.match().get("plots"), now);
					default -> {
					}
				}
			});
			SkyBlock.bus().on(SbEvent.IslandChanged.class, e -> {
				if (e.to() != Island.GARDEN) pests.killedAll();
			});
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			boolean sample = preview && !SkyBlock.active();
			List<String> visitors = sample ? List.of("Liam", "Jerry", "Farmer Jon") : Garden.visitors(SkyBlock.state().tab());
			if (!sample) pests.fromTab(SkyBlock.state().tab());
			String level = sample ? "XV" : SkyBlock.state().tab().value("garden level");
			long copper = sample ? 3210 : SkyBlock.state().copper();
			out.add(new Line("Garden" + (level.isEmpty() ? "" : " " + level) + (copper >= 0 ? "  ·  Copper " + SbText.grouped(copper) : ""), 0xFF5BD18B));
			out.add(new Line("Visitors: " + visitors.size() + (visitors.size() >= 5 ? " (full)" : ""), visitors.size() >= 5 ? 0xFFF5C542 : 0));
			for (String v : visitors) out.add(new Line("  " + v, 0xFFB8BCCB));
			Map<String, Integer> plots = sample ? Map.of("4", 2) : pests.plots();
			int alive = sample ? 2 : pests.alive();
			if (alive > 0) {
				out.add(new Line("Pests: " + alive + "  (plot " + String.join(", ", plots.keySet()) + ")", 0xFFF2706B));
			} else if (!sample && pests.lastSpawn() > 0) {
				out.add(new Line("Pests: none · last " + SbText.duration(System.currentTimeMillis() - pests.lastSpawn()) + " ago", 0));
			}
		}
	}

	/** A visitor's offer while its menu is open: what the items cost on the bazaar and coins per copper. */
	public static final class VisitorHud extends SkyBlockHudModule {
		public VisitorHud() {
			super("sb_visitor_offer", "Visitor Offer", "While a visitor's menu is open: the bazaar cost of what they ask for and coins per copper.",
					"Garden", true, at(Anchor.CENTER, 0, Anchor.START, 10), Island.GARDEN);
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && offerItem() != null;
		}

		private static RawItem offerItem() {
			var c = SkyBlock.input().container();
			if (c == null) return null;
			for (RawItem i : c.raw()) {
				if (Patterns.of("menus").matches("visitor_accept", SbText.clean(i.name())) != null) return i;
			}
			return null;
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			RawItem accept = SkyBlock.active() ? offerItem() : null;
			Garden.Offer offer;
			if (accept != null) offer = Garden.offer(accept.lore());
			else if (preview) offer = new Garden.Offer(Map.of("Enchanted Wheat", 10L), 30, List.of("+30 Copper"));
			else return;
			OptionalDouble cost = accept == null ? OptionalDouble.of(16_000) : offer.cost(GardenModules::priceByName);
			out.add(new Line("Visitor offer", 0xFF5BD18B));
			for (Map.Entry<String, Long> e : offer.items().entrySet()) out.add(new Line(e.getValue() + "x " + e.getKey(), 0));
			out.add(new Line("Cost: " + (cost.isPresent() ? SbText.shortNumber(cost.getAsDouble()) + " coins" : "unknown"), 0xFFF5C542));
			if (offer.copper() > 0) {
				out.add(new Line("Copper: " + offer.copper() + (cost.isPresent() ? "  (" + SbText.shortNumber(cost.getAsDouble() / offer.copper()) + " coins each)" : ""),
						0xFFE08850));
			}
		}
	}

	/** The next Jacob's contests and their crops (Elite API). */
	public static final class JacobHud extends SkyBlockHudModule {
		private final NumberSetting count = add(new NumberSetting("count", "Contests shown", 3, 1, 6, 1).format(0, ""));

		public JacobHud() {
			super("sb_jacob", "Jacob's Contests", "The next Jacob's Farming Contests and their crops.", "Garden", false,
					at(Anchor.END, 2, Anchor.START, 140));
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis() / 1000;
			out.add(new Line("Jacob's contests", 0xFFF5C542));
			for (Contests.Contest c : SkyBlock.data().contests().next(now, count.get().intValue())) {
				boolean running = c.start() <= now && now < c.end();
				String when = running ? SbText.duration((c.end() - now) * 1000) + " left" : "in " + SbText.duration((c.start() - now) * 1000);
				String crops = c.crops().isEmpty() ? "crops unknown" : String.join(", ", c.crops());
				out.add(new Line(when + ": " + crops, running ? 0xFF5BD18B : 0));
			}
		}
	}

	/** Farming: blocks per second, the held tool's crop counter (and per hour), and your yaw/pitch. */
	public static final class FarmingHud extends SkyBlockHudModule {
		private final BooleanSetting angles = add(new BooleanSetting("angles", "Yaw and pitch", true));
		private final Garden.Speed speed = new Garden.Speed(3000);
		private long counterStart = -1;
		private long counterStartAt;
		private String toolUuid = "";

		public FarmingHud() {
			super("sb_farming", "Farming", "Blocks per second, your tool's crop counter per hour, and yaw/pitch.", "Garden", false,
					at(Anchor.CENTER, 0, Anchor.END, 120), Island.GARDEN);
		}

		@Override
		public void onLoaded() {
			ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
				if (live() && isCrop(state)) speed.broke(System.currentTimeMillis());
			});
		}

		private static boolean isCrop(BlockState s) {
			return s.is(Blocks.WHEAT) || s.is(Blocks.CARROTS) || s.is(Blocks.POTATOES) || s.is(Blocks.NETHER_WART) || s.is(Blocks.SUGAR_CANE)
					|| s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN) || s.is(Blocks.CACTUS) || s.is(Blocks.COCOA) || s.is(Blocks.RED_MUSHROOM)
					|| s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.SUNFLOWER) || s.is(Blocks.WILDFLOWERS) || s.is(Blocks.ROSE_BUSH);
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			boolean sample = preview && !SkyBlock.active();
			double bps = sample ? 19.6 : speed.perSecond(now);
			out.add(new Line(String.format(Locale.ROOT, "%.1f blocks/s", bps), bps >= 19 ? 0xFF5BD18B : 0));
			if (!sample && mc.player != null) {
				var raw = dev.telmuad.hollowclient.skyblock.mc.GameReader.raw(-1, mc.player.getMainHandItem());
				long counter = Garden.toolCounter(raw.customData());
				var parsed = raw.parse();
				if (counter >= 0) {
					if (!parsed.uuid().equals(toolUuid) || counterStart < 0 || counter < counterStart) {
						toolUuid = parsed.uuid();
						counterStart = counter;
						counterStartAt = now;
					}
					double perHour = now - counterStartAt > 60_000 ? (counter - counterStart) * 3_600_000.0 / (now - counterStartAt) : Double.NaN;
					out.add(new Line("Counter " + SbText.grouped(counter) + (Double.isNaN(perHour) ? "" : "  (" + SbText.shortNumber(perHour) + "/h)"), 0));
				}
			} else if (sample) {
				out.add(new Line("Counter 12,345,678  (1.2M/h)", 0));
			}
			if (angles.on() && mc.player != null) {
				float yaw = net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot());
				out.add(new Line(String.format(Locale.ROOT, "Yaw %.1f  Pitch %.1f", yaw, mc.player.getXRot()), 0xFFB8BCCB));
			}
		}
	}

	/** The Composter widget from the tab list, as Hypixel shows it. */
	public static final class ComposterHud extends SkyBlockHudModule {
		public ComposterHud() {
			super("sb_composter", "Composter", "The tab list's Composter widget (time left, organic matter, fuel) on the HUD.", "Garden", false,
					at(Anchor.START, 2, Anchor.START, 210), Island.GARDEN);
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			List<String> w = SkyBlock.active() ? SkyBlock.state().tab().widget("Composter") : List.of();
			if (w.isEmpty() && preview && !SkyBlock.active()) w = List.of("Composter:", "Time Left: 12m 30s", "Organic Matter: 120k", "Fuel: 30k");
			if (w.isEmpty()) return;
			List<Line> lines = new ArrayList<>();
			for (String l : w) {
				boolean empty = l.toLowerCase(Locale.ROOT).contains("inactive") || l.toLowerCase(Locale.ROOT).contains("empty");
				lines.add(new Line(l, empty ? 0xFFF2706B : lines.isEmpty() ? 0xFF5BD18B : 0));
			}
			out.addAll(lines);
		}

		@Override
		public Health health() {
			Health h = super.health();
			if (!h.ok()) return h;
			return SkyBlock.state().tab().widget("Composter").isEmpty()
					? Health.missing("No Composter widget in the tab list (turn it on in Hypixel's tab widget settings)") : Health.ok("Reading the Composter widget");
		}
	}
}
