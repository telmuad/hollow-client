package dev.telmuad.hollowclient.skyblock.features.hunting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The Hunting area's HUD elements. */
public final class HuntingModules {
	private HuntingModules() {
	}

	/** Your Huntraps: how many are out, how long ago, and when they've caught something. Kept per profile. */
	public static final class HuntrapHud extends SkyBlockHudModule {
		private final BooleanSetting everywhere = add(new BooleanSetting("everywhere", "Show on every island", false));
		private final BooleanSetting caughtAlert = add(new BooleanSetting("caughtAlert", "Alert when traps catch something", true));
		private final Hunting.Traps traps = new Hunting.Traps();
		private String loadedFor;

		public HuntrapHud() {
			super("sb_huntraps", "Huntraps", "Your Huntraps from their chat lines: how many are out, how long ago they went down, and catches waiting.",
					"Hunting", true, at(Anchor.START, 2, Anchor.START, 120));
		}

		public Hunting.Traps traps() {
			return traps;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatLine.class, e -> {
				if (!SkyBlock.active()) return;
				ensureLoaded();
				int before = traps.caught();
				if (!traps.chat(e.line(), System.currentTimeMillis())) return;
				save();
				if (traps.caught() > before && caughtAlert.get() && isEnabled()) {
					SkyBlockAlerts.notify("huntraps", "§6§lHuntraps", "§e" + traps.caught() + " caught something", "DING", true);
				}
			});
		}

		private void ensureLoaded() {
			String key = SkyBlock.state().profileKey();
			if (key.equals(loadedFor)) return;
			loadedFor = key;
			JsonObject o = SkyBlock.store().read(key, "huntraps");
			List<Long> placed = new ArrayList<>();
			if (o.get("placed") instanceof JsonArray a) for (JsonElement e : a) placed.add(e.getAsLong());
			traps.restore(placed, o.has("max") ? o.get("max").getAsInt() : 5, o.has("caught") ? o.get("caught").getAsInt() : 0,
					o.has("caughtAt") ? o.get("caughtAt").getAsLong() : -1);
		}

		private void save() {
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			traps.placedAt().forEach(a::add);
			o.add("placed", a);
			o.addProperty("max", traps.max());
			o.addProperty("caught", traps.caught());
			o.addProperty("caughtAt", traps.caughtAt());
			try {
				SkyBlock.store().write(loadedFor, "huntraps", o);
			} catch (IOException e) {
				HollowClient.LOG.warn("Huntraps couldn't be saved: {}", e.toString());
			}
		}

		@Override
		public void onTick() {
			if (live()) ensureLoaded();
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			if (!super.visibleInGame(mc) || (traps.count() == 0 && traps.caught() == 0)) return false;
			Island i = SkyBlock.state().island();
			return everywhere.get() || i == Island.GALATEA || i == Island.TORRHUS_CANYON;
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			Hunting.Traps t = traps;
			if (!SkyBlock.active() || (t.count() == 0 && t.caught() == 0)) {
				if (!preview) return;
				t = new Hunting.Traps();
				t.restore(List.of(now - 9 * 3_600_000L, now - 5 * 3_600_000L, now - 3_600_000L), 5, 1, now - 600_000);
			}
			out.add(new Line("Huntraps " + t.count() + "/" + t.max(), 0xFF5BD18B));
			long oldest = t.oldest();
			if (oldest >= 0) {
				long age = now - oldest;
				out.add(new Line("Oldest " + SbText.duration(age) + " ago" + (age >= Hunting.COMMON_CATCH_MS ? " (could be done)" : ""),
						age >= Hunting.COMMON_CATCH_MS ? 0xFFF5C542 : 0));
			}
			if (t.caught() > 0) {
				out.add(new Line(t.caught() + " caught" + (t.caughtAt() > 0 ? " " + SbText.duration(now - t.caughtAt()) + " ago" : "") + " · collect",
						0xFFF5C542));
			}
		}
	}
}
