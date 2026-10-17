package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.Prices;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What SkyBlock Mode needs, checked right now, with exactly what to do about anything missing. */
public final class SetupCheck {
	public record Item(String title, boolean ok, String detail, String fix) {
	}

	/** Tab list widgets SkyBlock Mode reads, and what for (more are added by each area). */
	public static final Map<String, String> TAB_WIDGETS = new LinkedHashMap<>();

	static {
		TAB_WIDGETS.put("Area", "the island when the Hypixel Mod API isn't installed");
		TAB_WIDGETS.put("Profile", "your profile name and game mode (Ironman, Stranded, Bingo)");
	}

	/** The SkyBlock overlapping mods Hollow knows, by mod id. */
	public static final Map<String, String> OTHER_MODS = Map.of("skyblocker", "Skyblocker", "skyhanni", "SkyHanni", "firmament", "Firmament");

	private SetupCheck() {
	}

	public static List<Item> run() {
		List<Item> out = new ArrayList<>();
		SkyBlockState s = SkyBlock.state();
		boolean modApi = FabricLoader.getInstance().isModLoaded("hypixel-mod-api");
		out.add(new Item("Hypixel Mod API", modApi,
				modApi ? "Installed: Hypixel tells Hollow exactly which island you're on." : "Not installed. SkyBlock Mode reads the scoreboard and tab list instead, which works but is less exact.",
				modApi ? "" : "The Hollow launcher's 26.2 profile installs it. Playing another way: add \"Hypixel Mod API\" from Modrinth to your mods folder."));
		var mode = Modules.SKYBLOCK_MODE;
		boolean modeOn = mode == null || mode.isEnabled();
		out.add(new Item("SkyBlock Mode is on", modeOn, modeOn ? "SkyBlock features turn on when you join SkyBlock." : "SkyBlock Mode is switched off, so no SkyBlock feature runs.",
				modeOn ? "" : "Right Shift menu → SkyBlock → SkyBlock Mode."));
		boolean on = s.onSkyBlock();
		out.add(new Item("SkyBlock detected", on,
				on ? "On " + (s.island() == Island.UNKNOWN ? "SkyBlock (island not known yet)" : s.island().displayName())
						+ (SkyBlock.simulator().running() ? ", from the simulator." : s.fromModApi() ? ", from the Hypixel Mod API." : ", from the scoreboard and tab list.")
						: "Not on SkyBlock right now.",
				on ? "" : "Join Hypixel (mc.hypixel.net) and type /skyblock. Then open this again."));
		boolean profile = !s.profileName().isEmpty();
		out.add(new Item("Profile", profile,
				profile ? s.profileName() + modeText(s.gameMode()) + (s.profileId().isEmpty() ? " (id not seen yet; saved under the name for now)" : "") : "Not known yet.",
				profile ? "" : "Shown once you're on SkyBlock: Hypixel says \"You are playing on profile\" when you join, and the tab list has a Profile line."));
		if (on) {
			List<String> missing = new ArrayList<>();
			TabListParser.TabInfo tab = s.tab();
			for (Map.Entry<String, String> w : TAB_WIDGETS.entrySet()) {
				boolean present = !tab.widget(w.getKey()).isEmpty() || !tab.value(w.getKey()).isEmpty()
						|| (w.getKey().equals("Area") && !tab.area().isEmpty());
				if (!present) missing.add(w.getKey() + " (" + w.getValue() + ")");
			}
			out.add(new Item("Tab list widgets", missing.isEmpty(),
					missing.isEmpty() ? "Every widget SkyBlock Mode reads is in your tab list." : "Missing: " + String.join(", ", missing) + ".",
					missing.isEmpty() ? "" : "In SkyBlock, open the SkyBlock Menu (/sbmenu) → Settings and find the tab list widget settings (part of the player list settings); turn those widgets on for this island."));
		}
		Prices p = SkyBlock.data().prices();
		boolean prices = p.loaded();
		boolean downloads = mode == null || mode.downloads();
		out.add(new Item("Prices", prices,
				prices ? "Bazaar and lowest BIN prices loaded." : downloads ? "Not loaded yet. They load while you're on SkyBlock." : "Downloads are switched off.",
				prices ? "" : downloads ? "Join SkyBlock or press \"Refresh prices\" in the Hub. If it stays empty, check your internet connection."
						: "Right Shift → SkyBlock → SkyBlock Mode → \"Download prices and item data\"."));
		boolean neu = SkyBlock.data().neu().available();
		String neuError = SkyBlock.data().neu().error();
		out.add(new Item("Item repository (NEU)", neu, neu ? "Downloaded." : neuError.isEmpty() ? "Not downloaded yet (it's about 30 MB, once a day at most)." : "Not available: " + neuError,
				neu ? "" : "It downloads while you're on SkyBlock. Some item details need it; everything else works without."));
		List<String> problems = Patterns.problems();
		out.add(new Item("Text patterns", problems.isEmpty(), problems.isEmpty() ? "All patterns load." : "Broken: " + String.join(", ", problems),
				problems.isEmpty() ? "" : "A pattern override in config/hollowclient/skyblock/patterns may be wrong; delete it to use Hollow's own."));
		List<String> others = new ArrayList<>();
		OTHER_MODS.forEach((id, name) -> {
			if (FabricLoader.getInstance().isModLoaded(id)) others.add(name);
		});
		out.add(new Item("Other SkyBlock mods", others.isEmpty(), others.isEmpty() ? "None installed." : "Installed: " + String.join(", ", others) + ".",
				others.isEmpty() ? "" : "Open Compatibility in the Hub to switch off Hollow features they already have."));
		return out;
	}

	private static String modeText(TabListParser.GameMode m) {
		return switch (m) {
			case NORMAL -> "";
			case IRONMAN -> " (Ironman)";
			case STRANDED -> " (Stranded)";
			case BINGO -> " (Bingo)";
		};
	}
}
