package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.record.DebugRecorder;
import dev.telmuad.hollowclient.skyblock.record.Simulator;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.state.ProfileStore;
import dev.telmuad.hollowclient.skyblock.state.RawItem;
import dev.telmuad.hollowclient.skyblock.state.SessionStats;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockInput;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecorderAndSimulatorTest {
	private final long[] now = {1_000_000};

	private SkyBlockInput newInput(List<SbEvent> events) {
		EventBus bus = new EventBus();
		bus.setErrorSink(t -> {
			throw new AssertionError(t);
		});
		bus.onAny(events::add);
		return new SkyBlockInput(new SkyBlockState(), bus, () -> now[0]);
	}

	@Test
	void everyBuiltInScenarioIsListedAndPlays() throws Exception {
		List<Simulator.Scenario> list = Simulator.list(null);
		assertEquals(List.of("hub", "garden", "slayer", "dungeon", "rift", "general", "items"), list.stream().map(Simulator.Scenario::name).toList());
		for (Simulator.Scenario s : list) {
			List<SbEvent> events = new ArrayList<>();
			SkyBlockInput input = newInput(events);
			Simulator sim = new Simulator();
			assertTrue(sim.load(s.name(), null, 0), s.name());
			sim.runAll(input, item -> {
			});
			assertTrue(input.state().onSkyBlock(), s.name());
			assertFalse(sim.running());
		}
	}

	@Test
	void scenariosEndInTheRightState() throws Exception {
		assertEquals(Island.HUB, play("hub").state().island());
		SkyBlockInput hub = play("hub");
		assertEquals("Apple", hub.state().profileName());
		assertEquals(1_251_000, hub.state().purse());
		assertEquals(1484, hub.state().bits());
		assertEquals(Island.GARDEN, play("garden").state().island());
		assertEquals(Island.THE_PARK, play("slayer").state().island());
		assertEquals(Island.DUNGEON, play("dungeon").state().island());
		assertEquals(Island.THE_RIFT, play("rift").state().island());
	}

	@Test
	void sessionTotalsFromTheHubScenario() throws Exception {
		List<SbEvent> events = new ArrayList<>();
		SkyBlockInput input = newInput(events);
		SessionStats session = new SessionStats(input.state(), input.bus(), () -> now[0]);
		Simulator sim = new Simulator();
		sim.load("hub", null, 0);
		sim.runAll(input, i -> {
		});
		now[0] += 10 * 60_000;
		assertEquals(16_433, session.purseChange());
		assertEquals(250, session.bitsGained());
		assertEquals(1, session.rareDrops());
		assertEquals(25.0, session.skillXp().get("Farming"), 1e-9);
		List<String> labels = session.rows().stream().map(SessionStats.Row::label).toList();
		assertEquals(List.of("Played", "Purse", "Bits", "Farming XP", "Rare drops"), labels);
		JsonObject json = session.toJson();
		assertEquals("Apple", json.get("profile").getAsString());
		assertEquals("+16.4k", json.getAsJsonArray("rows").get(1).getAsJsonObject().get("value").getAsString());
	}

	@Test
	void itemsScenarioGivesFourItems() throws Exception {
		List<RawItem> given = new ArrayList<>();
		SkyBlockInput input = newInput(new ArrayList<>());
		Simulator sim = new Simulator();
		sim.load("items", null, 0);
		sim.runAll(input, given::add);
		assertEquals(List.of("HYPERION", "PET", "ENCHANTED_BOOK", "TITANIUM_DRILL_4"), given.stream().map(i -> i.parse().id()).toList());
		assertNull(input.container(), "the menu was closed at the end");
	}

	@Test
	void stepsPlayAtTheirTime() throws Exception {
		SkyBlockInput input = newInput(new ArrayList<>());
		Simulator sim = new Simulator();
		sim.load("hub", null, 0);
		int first = sim.tick(150, input, i -> {
		});
		assertEquals(2, first, "location at 0 and the sidebar at 100");
		assertTrue(sim.running());
		sim.tick(100_000, input, i -> {
		});
		assertFalse(sim.running());
	}

	@Test
	void unknownScenarioAndUnsafeNames(@TempDir Path dir) throws Exception {
		assertFalse(new Simulator().load("nope", dir, 0));
		Files.writeString(dir.resolve("mine.jsonl"), "{\"t\":0,\"type\":\"chat\",\"text\":\"Welcome to Hypixel SkyBlock!\"}\n");
		assertTrue(new Simulator().load("mine", dir, 0));
		assertFalse(new Simulator().load("../mine", dir, 0));
		assertTrue(Simulator.list(dir).stream().anyMatch(s -> s.recording() && s.name().equals("mine")));
	}

	private SkyBlockInput play(String name) throws Exception {
		SkyBlockInput input = newInput(new ArrayList<>());
		Simulator sim = new Simulator();
		sim.load(name, null, 0);
		sim.runAll(input, i -> {
		});
		return input;
	}

	// ------------------------------------------------------------------------------------------------ recorder

	@Test
	void recordingsReplayToTheSameState(@TempDir Path dir) throws Exception {
		DebugRecorder rec = new DebugRecorder(dir, () -> now[0]);
		rec.setNames(() -> List.of("PlayerOne", "Player_Two"), "RealMe");
		SkyBlockInput live = newInput(new ArrayList<>());
		live.setRecorder(rec);
		Path file = rec.start();
		live.sidebar(Fixtures.sidebarTitle("hub"), Fixtures.sidebarLines("hub"));
		live.tabList(Fixtures.tab("hub"));
		live.chat("§aYou are playing on profile: §eApple");
		live.chat("§dFrom §b[MVP§c+§b] PlayerOne§7: my secret");
		live.chat("§eRealMe found a Fairy Soul!");
		rec.stop();
		String text = Files.readString(file);
		assertFalse(text.contains("PlayerOne") || text.contains("Player_Two") || text.contains("RealMe"), text);
		assertFalse(text.contains("my secret"));
		assertTrue(text.contains("Me found a Fairy Soul!"));
		assertTrue(text.contains("Player1") || text.contains("Player2"));
		JsonObject first = JsonParser.parseString(text.lines().findFirst().orElseThrow()).getAsJsonObject();
		assertEquals(0, first.get("t").getAsLong());
		assertEquals("sidebar", first.get("type").getAsString());

		Simulator sim = new Simulator();
		assertTrue(sim.load(file.getFileName().toString().replace(".jsonl", ""), dir, 0));
		SkyBlockInput replay = newInput(new ArrayList<>());
		sim.runAll(replay, i -> {
		});
		assertEquals(live.state().island(), replay.state().island());
		assertEquals(live.state().purse(), replay.state().purse());
		assertEquals("Apple", replay.state().profileName());
	}

	@Test
	void anonymizingKeepsOtherWordsIntact() {
		DebugRecorder rec = new DebugRecorder(Path.of("."), () -> 0);
		rec.setNames(() -> List.of("Ann"), "");
		assertEquals("Player1 met Annabelle", rec.anonymize("Ann met Annabelle"));
	}

	@Test
	void profileStore(@TempDir Path dir) throws Exception {
		ProfileStore store = new ProfileStore(dir);
		JsonObject o = new JsonObject();
		o.addProperty("x", 1);
		store.write("0b1c2d3e-0000", "session_stats", o);
		assertEquals(1, store.read("0b1c2d3e-0000", "session_stats").get("x").getAsInt());
		assertEquals(0, store.read("other", "session_stats").size());
		assertTrue(store.dir("../../evil").startsWith(dir), "names can't leave the folder");
	}
}
