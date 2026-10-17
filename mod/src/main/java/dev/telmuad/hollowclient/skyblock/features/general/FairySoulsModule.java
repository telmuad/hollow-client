package dev.telmuad.hollowclient.skyblock.features.general;

import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.render.WaypointFx;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import dev.telmuad.hollowclient.waypoints.Waypoint;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fairy soul waypoints: the fixed locations of the souls you haven't found on this profile, as light beams with labels.
 * A soul counts as found when chat says so while you're next to it. Locations come from the NEU repository.
 */
public final class FairySoulsModule extends SkyBlockModule {
	private final NumberSetting range = add(new NumberSetting("range", "Show within", 0, 0, 500, 10).format(0, " blocks"));
	private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFF77DD));
	private FairySouls souls = new FairySouls();
	private String profileKey = "";
	private long lastLoadTry;

	public FairySoulsModule() {
		super("sb_fairy_souls", "Fairy Souls", "Waypoints to the fairy souls you haven't found yet on this profile.", "General", false);
		range.describe("0 shows every missing soul on the island.");
	}

	@Override
	public void onLoaded() {
		WaypointFx.addSource(this::waypoints);
		SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> {
			String n = e.match().name();
			if (n.equals("fairy_soul_found") || n.equals("fairy_soul_already")) found();
		});
		SkyBlock.bus().on(SbEvent.ProfileChanged.class, e -> profileKey = "");
	}

	private FairySouls data() {
		if (!souls.loaded() && System.currentTimeMillis() - lastLoadTry > 5000 && SkyBlock.data().neu().available()) {
			lastLoadTry = System.currentTimeMillis();
			souls = FairySouls.fromNeu(SkyBlock.data().neu().constant("fairy_souls"));
			profileKey = "";
		}
		String key = SkyBlock.state().profileKey();
		if (souls.loaded() && !key.equals(profileKey)) {
			profileKey = key;
			JsonObject found = SkyBlock.store().read(key, "fairy_souls");
			souls.loadFound(found.has("found") && found.get("found").isJsonObject() ? found.getAsJsonObject("found") : null);
		}
		return souls;
	}

	private void save() {
		JsonObject o = new JsonObject();
		o.add("found", souls.foundJson());
		try {
			SkyBlock.store().write(SkyBlock.state().profileKey(), "fairy_souls", o);
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't save found fairy souls: {}", e.toString());
		}
	}

	private void found() {
		Minecraft mc = Minecraft.getInstance();
		if (!live() || mc.player == null) return;
		FairySouls.Soul s = data().markNearest(SkyBlock.state().island().mode(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), 8);
		if (s != null) save();
	}

	/** Marks every soul on the current island found (or not); for /hollow sbsouls. Returns how many there are. */
	public int markIsland(boolean found) {
		String island = SkyBlock.state().island().mode();
		data().markAll(island, found);
		save();
		return data().souls(island).size();
	}

	private List<Waypoint> waypoints(Minecraft mc) {
		if (!live() || mc.player == null) return List.of();
		FairySouls f = data();
		String island = SkyBlock.state().island().mode();
		List<Waypoint> out = new ArrayList<>();
		int r = range.get().intValue();
		for (FairySouls.Soul s : f.missing(island)) {
			if (r > 0 && Math.sqrt(s.distanceSq(mc.player.getX(), mc.player.getY(), mc.player.getZ())) > r) continue;
			out.add(new Waypoint("Fairy Soul", s.x() + 0.5, s.y(), s.z() + 0.5, "", color.argb()));
		}
		return out;
	}

	@Override
	public Health health() {
		Health h = super.health();
		if (!h.ok()) return h;
		if (!SkyBlock.data().neu().available()) return Health.missing("Needs the item repository (it downloads on SkyBlock)");
		FairySouls f = data();
		String island = SkyBlock.state().island().mode();
		return f.souls(island).isEmpty() ? Health.ok("No fairy souls on this island")
				: Health.ok(f.missing(island).size() + " of " + f.souls(island).size() + " souls here still to find");
	}

	/** For the toast after /hollow sbsouls. */
	public static void report(int count, boolean found) {
		Toasts.info("Fairy souls", (found ? "Marked " : "Unmarked ") + count + " souls on this island.");
	}
}
