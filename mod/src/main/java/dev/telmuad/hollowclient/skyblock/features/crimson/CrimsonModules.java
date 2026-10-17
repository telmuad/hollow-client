package dev.telmuad.hollowclient.skyblock.features.crimson;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.StringSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Crimson Isle area's modules, plus a HUD that shows any tab list widget you name. */
public final class CrimsonModules {
	private CrimsonModules() {
	}

	/** Whether a name tag is a Vanquisher's ("[Lv235] Vanquisher 1M/1M❤"). */
	public static boolean vanquisher(String nameTag) {
		return Patterns.of("crimson").find("vanquisher_tag", SbText.clean(nameTag)) != null;
	}

	/** A title and sound when a Vanquisher's name tag shows up near you (once per Vanquisher). */
	public static final class VanquisherAlert extends SkyBlockModule {
		private final Set<Integer> seen = new HashSet<>();
		private int alerts;

		public VanquisherAlert() {
			super("sb_vanquisher", "Vanquisher Alert", "A title and sound when a Vanquisher's name tag appears near you on the Crimson Isle.", "Crimson Isle", true,
					Island.CRIMSON_ISLE);
		}

		public int alerts() {
			return alerts;
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (!live() || mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0) return;
			for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
				if (a.getCustomName() == null || !vanquisher(LegacyText.of(a.getCustomName()))) continue;
				if (seen.add(a.getId())) {
					alerts++;
					SkyBlockAlerts.notify("vanquisher", "§5§lVanquisher", "§7nearby", "BELL", true);
				}
			}
			if (seen.size() > 64) seen.clear();
		}
	}

	/**
	 * Shows the tab list widgets you name (as their titles read, e.g. "Faction Quests, Reputation"), anywhere on
	 * SkyBlock. Whatever the widget holds is shown as it is, so it works for widgets Hollow doesn't know.
	 */
	public static final class TabWidgetHud extends SkyBlockHudModule {
		private final StringSetting widgets = add(new StringSetting("widgets", "Widgets (comma-separated titles)", "", 200));

		public TabWidgetHud() {
			super("sb_tab_widgets", "Tab Widgets", "Shows the tab list widgets you name (their titles, comma-separated) on screen, e.g. Faction Quests on the "
					+ "Crimson Isle. The widget has to be on in Hypixel's tab list settings.", "General", false, at(Anchor.END, 2, Anchor.START, 60));
		}

		public void setWidgets(String value) {
			widgets.set(value);
		}

		static List<String> names(String setting) {
			List<String> out = new ArrayList<>();
			for (String s : setting.split(",")) if (!s.isBlank()) out.add(s.strip().replaceAll(":$", ""));
			return out;
		}

		/** The lines to show for the named widgets (each widget's title line first). */
		public static List<String> widgetLines(TabListParser.TabInfo tab, List<String> names) {
			List<String> out = new ArrayList<>();
			for (String n : names) out.addAll(tab.widget(n));
			return out;
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && !widgetLines(SkyBlock.state().tab(), names(widgets.get())).isEmpty();
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			List<List<String>> shown = new ArrayList<>();
			if (SkyBlock.active()) {
				for (String n : names(widgets.get())) {
					List<String> w = SkyBlock.state().tab().widget(n);
					if (!w.isEmpty()) shown.add(w);
				}
			}
			if (shown.isEmpty()) {
				if (!preview) return;
				shown.add(List.of("Faction Quests:", "Kill 10 Flaming Spiders", "Fish 3 Lava Leeches"));
			}
			for (List<String> w : shown) {
				// each widget's title line stands out
				for (int i = 0; i < w.size(); i++) out.add(new Line(w.get(i), i == 0 ? 0xFFF5C542 : 0));
			}
		}
	}
}
