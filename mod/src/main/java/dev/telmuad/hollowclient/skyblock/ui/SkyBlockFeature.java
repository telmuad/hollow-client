package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.state.Island;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A SkyBlock Mode feature (module or HUD element). It lives in the SkyBlock menu category under an area sub-tab, works
 * only on SkyBlock (and only on its islands, if it names any), can say whether its data is there right now (feature
 * health), and can name the other SkyBlock mods that have the same feature (compatibility screen).
 */
public interface SkyBlockFeature {
	/** "OK" or what's missing, for the feature health screen. */
	record Health(boolean ok, String text) {
		public static Health ok(String text) {
			return new Health(true, text);
		}

		public static Health missing(String text) {
			return new Health(false, text);
		}
	}

	String group();

	/** Islands where the feature works; empty = everywhere on SkyBlock. */
	Set<Island> islands();

	/** Mod ids ("skyblocker", "skyhanni", "firmament") with the same feature. */
	default Set<String> overlaps() {
		return Set.of();
	}

	/** Whether the feature's data is detected right now. Override to check more than "on SkyBlock, right island". */
	default Health health() {
		return baseHealth(islands());
	}

	static Health baseHealth(Set<Island> islands) {
		if (!SkyBlock.active()) return Health.missing("Not on SkyBlock");
		if (!islands.isEmpty() && !islands.contains(SkyBlock.state().island())) {
			return Health.missing("Only on " + islands.stream().map(Island::displayName).sorted().collect(Collectors.joining(", ")));
		}
		return Health.ok("Ready");
	}

	/** True when this feature should do its thing now: switched on, on SkyBlock, on one of its islands. */
	static boolean live(Module m, Set<Island> islands) {
		return m.isEnabled() && SkyBlock.active() && (islands.isEmpty() || islands.contains(SkyBlock.state().island()));
	}
}
