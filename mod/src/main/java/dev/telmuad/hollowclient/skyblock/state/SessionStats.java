package dev.telmuad.hollowclient.skyblock.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Totals for the current SkyBlock session (since you joined SkyBlock): time played, purse change, bits, skill XP,
 * rare drops, slayer bosses and garden visitors. Shown by the Session HUD and the Hub, and saved as
 * {@code session_stats.json} in the profile's folder, which the launcher's SkyBlock page reads (label/value rows).
 * Plain Java.
 */
public final class SessionStats {
	public record Row(String label, String value) {
	}

	private final SkyBlockState state;
	private final LongSupplier clock;
	private long startedAt;
	private long startPurse = -1;
	private long bitsGained;
	private int rareDrops;
	private int slayerBosses;
	private int visitors;
	private final Map<String, Double> skillXp = new LinkedHashMap<>();

	public SessionStats(SkyBlockState state, EventBus bus, LongSupplier clock) {
		this.state = state;
		this.clock = clock;
		bus.on(SbEvent.JoinedSkyBlock.class, e -> reset());
		bus.on(SbEvent.ProfileChanged.class, e -> {
			if (clock.getAsLong() - startedAt > 30_000) return; // only the first profile line after joining starts a session
			startPurse = state.purse();
		});
		bus.on(SbEvent.PurseChanged.class, e -> {
			if (startPurse < 0) startPurse = e.purse() - e.delta();
		});
		bus.on(SbEvent.BitsChanged.class, e -> {
			if (e.delta() > 0) bitsGained += e.delta();
		});
		bus.on(SbEvent.RareDrop.class, e -> rareDrops++);
		bus.on(SbEvent.SlayerQuestCompleted.class, e -> slayerBosses++);
		bus.on(SbEvent.VisitorArrived.class, e -> visitors++);
		bus.on(SbEvent.SkillXpGained.class, e -> skillXp.merge(e.skill(), e.amount(), Double::sum));
	}

	public void reset() {
		startedAt = clock.getAsLong();
		startPurse = state.purse();
		bitsGained = 0;
		rareDrops = 0;
		slayerBosses = 0;
		visitors = 0;
		skillXp.clear();
	}

	public long playedMs() {
		return startedAt == 0 ? 0 : clock.getAsLong() - startedAt;
	}

	public long purseChange() {
		return startPurse < 0 || state.purse() < 0 ? 0 : state.purse() - startPurse;
	}

	public long bitsGained() {
		return bitsGained;
	}

	public int rareDrops() {
		return rareDrops;
	}

	public int slayerBosses() {
		return slayerBosses;
	}

	public int visitors() {
		return visitors;
	}

	public Map<String, Double> skillXp() {
		return Map.copyOf(skillXp);
	}

	/** The rows the HUD, the Hub and the launcher show (things that didn't happen are left out). */
	public List<Row> rows() {
		List<Row> out = new ArrayList<>();
		out.add(new Row("Played", SbText.duration(playedMs())));
		long purse = purseChange();
		if (purse != 0) out.add(new Row("Purse", (purse > 0 ? "+" : "-") + SbText.shortNumber(Math.abs(purse))));
		if (bitsGained > 0) out.add(new Row("Bits", "+" + SbText.shortNumber(bitsGained)));
		skillXp.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(3)
				.forEach(e -> out.add(new Row(e.getKey() + " XP", "+" + SbText.shortNumber(e.getValue()))));
		if (slayerBosses > 0) out.add(new Row("Slayer bosses", Integer.toString(slayerBosses)));
		if (rareDrops > 0) out.add(new Row("Rare drops", Integer.toString(rareDrops)));
		if (visitors > 0) out.add(new Row("Visitors", Integer.toString(visitors)));
		return out;
	}

	/** The file the launcher reads ({"version", "updated", "profile", "mode", "rows"}). */
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("version", 1);
		o.addProperty("updated", clock.getAsLong() / 1000);
		o.addProperty("profile", state.profileName());
		o.addProperty("mode", state.gameMode().name().toLowerCase(java.util.Locale.ROOT));
		JsonArray rows = new JsonArray();
		for (Row r : rows()) {
			JsonObject j = new JsonObject();
			j.addProperty("label", r.label());
			j.addProperty("value", r.value());
			rows.add(j);
		}
		o.add("rows", rows);
		return o;
	}
}
