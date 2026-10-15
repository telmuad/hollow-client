package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.gui.WaypointsScreen;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.NumberSetting;
import net.minecraft.client.gui.screens.Screen;

/**
 * Waypoints for your own singleplayer worlds: a light beam and a label with the distance for each saved place, and
 * a death point where you last died. They never show on servers.
 */
public final class WaypointsModule extends Module implements ScreenProvider {
	private final Effect beams = effect("beams", "Light beams", true, 70, "A soft beam of light over each waypoint.");
	private final BooleanSetting labels = add(new BooleanSetting("labels", "Name labels", true));
	private final BooleanSetting distance = add(new BooleanSetting("distance", "Show distance", true));
	private final NumberSetting range = add(new NumberSetting("range", "Show labels within", 0, 0, 5000, 100)
			.display(v -> v <= 0 ? "Any distance" : Math.round(v) + " m"));
	private final BooleanSetting deathPoints = add(new BooleanSetting("deathPoints", "Save a death point", true));

	public WaypointsModule() {
		super("waypoints", "Waypoints", "Beams and labels for places you save in singleplayer worlds, plus a death point. Never on servers.",
				Category.QOL, true);
	}

	public float beams() {
		return beams.amount();
	}

	public boolean labels() {
		return labels.on();
	}

	public boolean distance() {
		return distance.on();
	}

	/** Label range in blocks, or 0 for any distance. */
	public double range() {
		return range.get();
	}

	public boolean deathPoints() {
		return deathPoints.on();
	}

	@Override
	public Screen createScreen(Screen parent) {
		return new WaypointsScreen(parent);
	}
}
