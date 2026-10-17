package dev.telmuad.hollowclient.skyblock.features.dungeons;

import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.AlertEngine;
import dev.telmuad.hollowclient.skyblock.alerts.AlertRule;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.features.items.ItemCache;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.SidebarParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockFeature;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/** The Dungeons area's modules. */
public final class DungeonModules {
	private DungeonModules() {
	}

	/** Score estimate, secrets, crypts, deaths, keys and run splits during a dungeon run. */
	public static final class DungeonHud extends SkyBlockHudModule {
		private final BooleanSetting alerts = add(new BooleanSetting("alerts", "Alert at 270 (S) and 300 (S+)", true));
		private final Dungeons.Splits splits = new Dungeons.Splits();
		private final Dungeons.Keys keys = new Dungeons.Keys();
		private boolean said270, said300;
		private String lastRun = "";

		public DungeonHud() {
			super("sb_dungeon", "Dungeon", "Score estimate (S/S+), secrets, crypts, deaths, keys and split times in dungeons.", "Dungeons", true,
					at(Anchor.START, 2, Anchor.START, 120), Island.DUNGEON);
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> {
				long now = System.currentTimeMillis();
				var m = e.match();
				splits.onChat(m.name(), m.get("boss").isEmpty() ? null : m.get("boss"), now);
				keys.onChat(m.name(), m.name().equals("dungeon_door_opened") ? m.get("door") : m.get("key"), m.get("who"));
				if (m.name().equals("dungeon_score")) {
					lastRun = floor() + " " + m.get("grade") + " " + m.get("score") + " in " + SbText.duration(splits.elapsed(now));
				}
				if (m.name().equals("dungeon_starting") || m.name().equals("dungeon_run_start")) {
					keys.reset();
					said270 = said300 = false;
				}
			});
		}

		private static String floor() {
			SidebarParser.Dungeon d = SkyBlock.state().sidebar().dungeon();
			return d == null ? "" : d.floor();
		}

		Dungeons.Score score() {
			SidebarParser.Dungeon d = SkyBlock.state().sidebar().dungeon();
			Dungeons.TabStats t = Dungeons.tabStats(SkyBlock.state().tab());
			boolean ezpz = SkyBlock.data().election().mayor().allPerks().stream().anyMatch(p -> p.toUpperCase().contains("EZPZ"));
			long elapsed = d != null && d.elapsedMs() >= 0 ? d.elapsedMs() : splits.elapsed(System.currentTimeMillis());
			return Dungeons.score(new Dungeons.ScoreInput(d == null ? "" : d.floor(), d == null ? 0 : Math.max(0, d.cleared()), t.secretsPercent(),
					t.crypts(), t.deaths(), t.failedPuzzles(), elapsed, ezpz, false));
		}

		@Override
		public void onTick() {
			if (!live() || !alerts.on() || !splits.running()) return;
			int total = score().total();
			if (total >= 270 && !said270) {
				said270 = true;
				fire("§e§lS reached", "Score " + total);
			}
			if (total >= 300 && !said300) {
				said300 = true;
				fire("§6§lS+ reached", "Score " + total);
			}
		}

		private static void fire(String title, String sub) {
			AlertRule r = new AlertRule("dungeon_score", "Dungeon score", true, true, AlertRule.Trigger.EVENT, "", title, sub, "LEVEL_UP", false, Set.of(), 0);
			SkyBlockAlerts.show(new AlertEngine.Fired(r, title, sub));
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			if (!SkyBlock.active()) {
				if (!preview) return;
				out.add(new Line("F7  ·  S+ 306", 0xFFF5C542));
				out.add(new Line("Secrets 100%  Crypts 5  Deaths 0", 0));
				out.add(new Line("Keys: 1 wither", 0xFFB8BCCB));
				out.add(new Line("Blood open 1m 12s · Boss 4m 40s", 0xFFB8BCCB));
				return;
			}
			Dungeons.Score s = score();
			Dungeons.TabStats t = Dungeons.tabStats(SkyBlock.state().tab());
			int color = s.total() >= 300 ? 0xFFF5C542 : s.total() >= 270 ? 0xFF5BD18B : 0;
			out.add(new Line(floor() + "  ·  " + s.rank() + " " + s.total(), color));
			out.add(new Line(String.format(java.util.Locale.ROOT, "Secrets %.0f%%  Crypts %d  Deaths %d", t.secretsPercent(), t.crypts(), t.deaths()), 0));
			if (t.puzzlesLeft() > 0 || t.failedPuzzles() > 0) out.add(new Line("Puzzles left " + t.puzzlesLeft() + (t.failedPuzzles() > 0 ? "  failed " + t.failedPuzzles() : ""), 0));
			if (keys.wither() > 0 || keys.blood()) {
				out.add(new Line("Keys: " + (keys.wither() > 0 ? keys.wither() + " wither " : "") + (keys.blood() ? "blood" : ""), 0xFFB8BCCB));
			}
			StringBuilder sb = new StringBuilder();
			for (String name : splits.order()) {
				if (!sb.isEmpty()) sb.append(" · ");
				sb.append(name).append(' ').append(SbText.duration(splits.marks().get(name)));
			}
			if (!sb.isEmpty()) out.add(new Line(sb.toString(), 0xFFB8BCCB));
			if (!splits.running() && !lastRun.isEmpty()) out.add(new Line("Last: " + lastRun, 0xFF8A8F9C));
		}
	}

	/** The dungeon map from the map item in your hotbar, drawn as it is (nothing it doesn't show is added). */
	public static final class DungeonMapHud extends HudModule implements SkyBlockFeature {
		private final NumberSetting size = add(new NumberSetting("size", "Size", 96, 48, 160, 8).format(0, " px"));
		private MapId mapId;
		private MapItemSavedData data;

		public DungeonMapHud() {
			super("sb_dungeon_map", "Dungeon Map", "Your dungeon map (the map item) on the HUD, with the player markers it shows.", Category.SKYBLOCK, false,
					at(Anchor.END, 2, Anchor.START, 2));
		}

		@Override
		public String group() {
			return "Dungeons";
		}

		@Override
		public Set<Island> islands() {
			return Set.of(Island.DUNGEON);
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return mc.player != null && SkyBlockFeature.live(this, islands()) && find(mc);
		}

		private boolean find(Minecraft mc) {
			for (int i = 0; i < 9; i++) {
				ItemStack st = mc.player.getInventory().getItem(i);
				MapId id = st.get(DataComponents.MAP_ID);
				if (id == null) continue;
				MapItemSavedData d = MapItem.getSavedData(id, mc.level);
				if (d == null) continue;
				mapId = id;
				data = d;
				return true;
			}
			return false;
		}

		@Override
		public Size measure(Minecraft mc, Font font, boolean preview) {
			int s = size.get().intValue();
			if (!preview && (mc.player == null || !find(mc))) return new Size(0, 0);
			return new Size(s + 2 * PAD, s + 2 * PAD);
		}

		@Override
		public void draw(GuiGraphicsExtractor g, Minecraft mc, Font font, boolean preview) {
			int s = size.get().intValue();
			drawBackground(g, s + 2 * PAD, s + 2 * PAD);
			if (mapId == null || data == null || mc.player == null) {
				text(g, font, "Dungeon map", PAD + 4, PAD + 4, 0xFFB8BCCB);
				return;
			}
			var tex = mc.getMapTextureManager().prepareMapTexture(mapId, data);
			g.blit(RenderPipelines.GUI_TEXTURED, tex, PAD, PAD, 0f, 0f, s, s, 128, 128, 128, 128, ColorUtil.fade(0xFFFFFFFF, alpha));
			for (MapDecoration d : data.getDecorations()) {
				float x = PAD + (d.x() + 128) / 256f * s;
				float y = PAD + (d.y() + 128) / 256f * s;
				var type = d.type().value();
				int color = type == MapDecorationTypes.FRAME.value() ? 0xFF5BD18B : type == MapDecorationTypes.PLAYER.value() ? 0xFFFFFFFF
						: type == MapDecorationTypes.BLUE_MARKER.value() ? 0xFF55AAFF : 0xFFFF5555;
				g.fill(Math.round(x) - 1, Math.round(y) - 1, Math.round(x) + 2, Math.round(y) + 2, ColorUtil.fade(color, alpha));
			}
		}

		@Override
		public Health health() {
			Health h = SkyBlockFeature.super.health();
			if (!h.ok()) return h;
			return mapId != null ? Health.ok("Reading the map item") : Health.missing("No dungeon map in your hotbar");
		}
	}

	/** Floor 7 terminals: the slots to click are outlined (order, colors, starts-with, panes, same color with click counts). */
	public static final class TerminalsModule extends SkyBlockModule {
		private static TerminalsModule instance;
		private Dungeons.Solution solution;
		private AbstractContainerScreen<?> screen;

		public TerminalsModule() {
			super("sb_terminals", "Terminal Helper", "Floor 7 terminals: outlines the slots to click. Display only; you click.", "Dungeons", true, Island.DUNGEON);
			instance = this;
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (!live() || mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s)) {
				solution = null;
				return;
			}
			String title = SbText.clean(s.getTitle().getString());
			if (Dungeons.terminal(title) == null) {
				solution = null;
				return;
			}
			List<Dungeons.Slot> slots = new ArrayList<>();
			for (Slot sl : s.getMenu().slots) {
				if (sl.container == mc.player.getInventory() || !sl.hasItem()) continue;
				ItemStack st = sl.getItem();
				slots.add(new Dungeons.Slot(sl.index, BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), st.getHoverName().getString(),
						st.hasFoil()));
			}
			screen = s;
			solution = Dungeons.solve(title, slots);
		}

		public static void overlay(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Slot slot) {
			TerminalsModule m = instance;
			if (m == null || m.solution == null || s != m.screen) return;
			if (m.solution.highlight().contains(slot.index)) {
				int c = 0xFF5BD18B;
				g.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y, c);
				g.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17, c);
				g.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, c);
				g.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16, c);
			}
			Integer n = m.solution.clicks().get(slot.index);
			if (n != null) g.text(Minecraft.getInstance().font, Integer.toString(n), slot.x + 5, slot.y + 4, 0xFFFFE066, true);
		}
	}

	/** The blaze puzzle (Higher or Lower): visible blazes by health, lowest and highest. */
	public static final class BlazeHud extends SkyBlockHudModule {
		public BlazeHud() {
			super("sb_blaze", "Blaze Puzzle", "Higher or Lower: the visible blazes ordered by health (lowest and highest next).", "Dungeons", true,
					at(Anchor.CENTER, 0, Anchor.START, 40), Island.DUNGEON);
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			if (!SkyBlock.active() || mc.level == null || mc.player == null) {
				if (preview) out.add(new Line("Blazes: lowest 1,200 · highest 9,800", 0xFFF5C542));
				return;
			}
			Map<Integer, Integer> hp = new HashMap<>();
			for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(30))) {
				if (a.getCustomName() == null) continue;
				String n = SbText.clean(a.getCustomName().getString());
				Matcher m = java.util.regex.Pattern.compile("Blaze.*?([\\d,]+)/[\\d,]+❤").matcher(n);
				if (m.find()) hp.put(a.getId(), (int) SbText.longOr(m.group(1), 0));
			}
			if (hp.size() < 3) return;
			List<Integer> order = Dungeons.blazeOrder(hp);
			out.add(new Line("Blazes (" + order.size() + "): lowest " + SbText.grouped(hp.get(order.getFirst())) + " · highest "
					+ SbText.grouped(hp.get(order.getLast())), 0xFFF5C542));
		}
	}

	/** Spirit Leap menu: each teammate's class (from the scoreboard party lines) next to their head. */
	public static final class SpiritLeapModule extends SkyBlockModule {
		private static SpiritLeapModule instance;

		public SpiritLeapModule() {
			super("sb_spirit_leap", "Spirit Leap Classes", "Shows each teammate's class on their head in the Spirit Leap menu.", "Dungeons", true, Island.DUNGEON);
			instance = this;
		}

		public static void overlay(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Slot slot) {
			SpiritLeapModule m = instance;
			if (m == null || !m.live() || slot.container == Minecraft.getInstance().player.getInventory()) return;
			if (Patterns.of("menus").find("spirit_leap", SbText.clean(s.getTitle().getString())) == null) return;
			String name = SbText.clean(slot.getItem().getHoverName().getString());
			String cls = classes().get(name);
			if (cls == null) return;
			g.text(Minecraft.getInstance().font, cls, slot.x, slot.y, 0xFFFFE066, true);
		}

		/** Player name -> class letter from the sidebar's party lines ("[M] Name 5,000❤"). */
		static Map<String, String> classes() {
			Map<String, String> out = new HashMap<>();
			Patterns p = Patterns.of("tab");
			for (String line : SkyBlock.state().sidebar().lines()) {
				Matcher mm = p.matches("party_member", line);
				if (mm != null) out.put(mm.group("name"), mm.group("class"));
			}
			return out;
		}
	}

	/** Reward chests after a run: what's inside is worth vs what opening costs. */
	public static final class RewardChestHud extends SkyBlockHudModule {
		public RewardChestHud() {
			super("sb_reward_chest", "Reward Chest Value", "In a dungeon reward chest: the value of what's inside against the cost to open it.", "Dungeons", true,
					at(Anchor.CENTER, 0, Anchor.START, 10));
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && chest(mc) != null;
		}

		private static AbstractContainerScreen<?> chest(Minecraft mc) {
			if (mc.gui.screen() instanceof AbstractContainerScreen<?> s && Patterns.of("menus").find("reward_chest", SbText.clean(s.getTitle().getString())) != null) return s;
			return null;
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			AbstractContainerScreen<?> s = chest(mc);
			if (s == null) {
				if (preview && !SkyBlock.active()) out.add(new Line("Chest: 12.4M worth · costs 2M · +10.4M", 0xFF5BD18B));
				return;
			}
			double value = 0;
			long cost = 0;
			Patterns p = Patterns.of("menus");
			for (Slot sl : s.getMenu().slots) {
				if (sl.container == mc.player.getInventory() || !sl.hasItem()) continue;
				ItemStack st = sl.getItem();
				String name = SbText.clean(st.getHoverName().getString());
				if (p.matches("reward_open", name) != null) {
					ItemLore lore = st.get(DataComponents.LORE);
					if (lore != null) for (Component c : lore.lines()) {
						Matcher m = p.find("reward_cost", SbText.clean(c.getString()));
						if (m != null) cost = SbText.longOr(m.group("coins"), 0);
					}
					continue;
				}
				ItemParser.SbItem item = ItemCache.of(st);
				if (item == null || !item.isSkyBlock()) continue;
				value += SkyBlock.data().prices().value(item, ItemParser.petLevel(item)).value() * st.getCount();
			}
			double profit = value - cost;
			out.add(new Line("Chest: " + SbText.shortNumber(value) + " worth · costs " + SbText.shortNumber(cost) + " · " + (profit >= 0 ? "+" : "")
					+ SbText.shortNumber(profit), profit >= 0 ? 0xFF5BD18B : 0xFFF2706B));
		}
	}
}
