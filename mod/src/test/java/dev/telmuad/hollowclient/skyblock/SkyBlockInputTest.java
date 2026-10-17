package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.state.Location;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockInput;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkyBlockInputTest {
	private SkyBlockState state;
	private EventBus bus;
	private SkyBlockInput input;
	private final List<SbEvent> events = new ArrayList<>();
	private final List<JsonObject> recorded = new ArrayList<>();
	private long now = 1_000_000;

	@BeforeEach
	void setUp() {
		state = new SkyBlockState();
		bus = new EventBus();
		bus.setErrorSink(t -> {
			throw new AssertionError(t);
		});
		bus.onAny(events::add);
		input = new SkyBlockInput(state, bus, () -> now);
		input.setRecorder(recorded::add);
	}

	private <T extends SbEvent> List<T> of(Class<T> type) {
		List<T> out = new ArrayList<>();
		for (SbEvent e : events) if (type.isInstance(e)) out.add(type.cast(e));
		return out;
	}

	@Test
	void joiningFromTheScoreboardAndTabList() {
		input.sidebar(Fixtures.sidebarTitle("hub"), Fixtures.sidebarLines("hub"));
		assertTrue(state.onSkyBlock());
		assertFalse(state.fromModApi());
		assertEquals(1, of(SbEvent.JoinedSkyBlock.class).size());
		assertEquals("Village", state.area());
		assertEquals(1_234_567, state.purse());
		assertEquals(Island.UNKNOWN, state.island(), "the island comes from the tab list or the Mod API");

		input.tabList(Fixtures.tab("hub"));
		assertEquals(Island.HUB, state.island());
		assertEquals(List.of(new SbEvent.IslandChanged(Island.UNKNOWN, Island.HUB)), of(SbEvent.IslandChanged.class));
		assertEquals("Apple", state.profileName());
		assertEquals(TabListParser.GameMode.IRONMAN, state.gameMode());
		assertEquals(1, of(SbEvent.ProfileChanged.class).size());

		// same scoreboard again: nothing new
		int before = events.size();
		input.sidebar(Fixtures.sidebarTitle("hub"), Fixtures.sidebarLines("hub"));
		assertEquals(before, events.size());
	}

	@Test
	void purseChangesAndLeaving() {
		input.sidebar(Fixtures.sidebarTitle("hub"), Fixtures.sidebarLines("hub"));
		List<String> richer = new ArrayList<>(Fixtures.sidebarLines("hub"));
		richer.replaceAll(l -> l.startsWith("Purse") ? "Purse: §61,240,000 §e(+5,433)" : l);
		input.sidebar(Fixtures.sidebarTitle("hub"), richer);
		assertEquals(List.of(new SbEvent.PurseChanged(1_240_000, 5_433)), of(SbEvent.PurseChanged.class));

		input.sidebar(Fixtures.sidebarTitle("bedwars"), Fixtures.sidebarLines("bedwars"));
		assertFalse(state.onSkyBlock());
		assertEquals(1, of(SbEvent.LeftSkyBlock.class).size());
	}

	@Test
	void theModApiWinsOverTheScoreboard() {
		input.location(new Location("mini42AB", "SKYBLOCK", "", "mining_3", "Dwarven Mines"));
		assertTrue(state.onSkyBlock() && state.fromModApi());
		assertEquals(Island.DWARVEN_MINES, state.island());
		assertEquals("mini42AB", state.serverId());
		input.tabList(Fixtures.tab("hub"));
		assertEquals(Island.DWARVEN_MINES, state.island(), "tab list doesn't override the exact location");
		input.location(new Location("lobby1", "BEDWARS", "", "", ""));
		assertFalse(state.onSkyBlock());
		input.left();
		assertEquals(Island.UNKNOWN, state.island());
		assertFalse(state.fromModApi());
	}

	@Test
	void riftDungeonAndKuudraFromTheScoreboard() {
		input.sidebar(Fixtures.sidebarTitle("rift"), Fixtures.sidebarLines("rift"));
		assertEquals(Island.THE_RIFT, state.island());
		assertEquals(12_345, state.motes());
		input.sidebar(Fixtures.sidebarTitle("dungeon"), Fixtures.sidebarLines("dungeon"));
		assertEquals(Island.DUNGEON, state.island());
		input.sidebar(Fixtures.sidebarTitle("kuudra"), Fixtures.sidebarLines("kuudra"));
		assertEquals(Island.KUUDRA, state.island());
	}

	@Test
	void chatBecomesTypedEvents() {
		input.chat("§aYou are playing on profile: §eApple");
		input.chat("§aProfile ID: 0B1C2D3E-0000-4000-8000-0000000000AA");
		assertEquals("Apple", state.profileName());
		assertEquals("0b1c2d3e-0000-4000-8000-0000000000aa", state.profileId());
		assertEquals("0b1c2d3e-0000-4000-8000-0000000000aa", state.profileKey());

		input.chat("§6§lRARE DROP! §5Shard of the Shredded §b(+312% §b✯ Magic Find§b)");
		assertEquals(List.of(new SbEvent.RareDrop("RARE DROP", "Shard of the Shredded", 1, 312)), of(SbEvent.RareDrop.class));
		input.chat("§aLiam §ehas arrived on your Garden!");
		assertEquals(List.of(new SbEvent.VisitorArrived("Liam")), of(SbEvent.VisitorArrived.class));
		assertTrue(of(SbEvent.ChatMatched.class).size() >= 4);
	}

	@Test
	void slayerBossSpawnAndQuestComplete() {
		input.sidebar(Fixtures.sidebarTitle("slayer_progress"), Fixtures.sidebarLines("slayer_progress"));
		assertTrue(of(SbEvent.SlayerBossSpawned.class).isEmpty());
		input.sidebar(Fixtures.sidebarTitle("slayer_boss"), Fixtures.sidebarLines("slayer_boss"));
		assertEquals(List.of(new SbEvent.SlayerBossSpawned("Sven Packmaster", 4)), of(SbEvent.SlayerBossSpawned.class));
		input.chat("§a§l  SLAYER QUEST COMPLETE!");
		assertEquals(List.of(new SbEvent.SlayerQuestCompleted("Sven Packmaster", 4)), of(SbEvent.SlayerQuestCompleted.class));
	}

	@Test
	void skillXpCountsEachNewGainOnce() {
		input.actionBar("§c900/900❤   §3+12.5 Farming (1,234/5,000)");
		input.actionBar("§c880/900❤   §3+12.5 Farming (1,234/5,000)");
		input.actionBar("§c880/900❤   §3+12.5 Farming (1,246.5/5,000)");
		assertEquals(2, of(SbEvent.SkillXpGained.class).size());
		assertEquals(880, state.actionBar().health());
	}

	@Test
	void recorderGetsEverythingButPlayerMessages() {
		input.sidebar(Fixtures.sidebarTitle("hub"), Fixtures.sidebarLines("hub"));
		input.chat("§dFrom §b[MVP§c+§b] PlayerOne§7: secret");
		input.chat("§8[§9287§8] §b[MVP§c+§b] PlayerOne§f: hello");
		input.chat("§6§lRARE DROP! §5Shard of the Shredded");
		input.actionBar("§c900/900❤");
		input.actionBar("§c900/900❤");
		input.container("Bazaar ➜ Farming", List.of(Fixtures.item("book")));
		List<String> types = recorded.stream().map(j -> j.get("type").getAsString()).toList();
		assertEquals(List.of("sidebar", "chat", "actionbar", "container"), types);
		assertFalse(recorded.toString().contains("secret"));
		assertFalse(recorded.toString().contains("hello"));
		assertEquals("Bazaar ➜ Farming", input.container().title());
		assertEquals("ENCHANTED_BOOK", input.container().items().getFirst().id());
	}

	@Test
	void aFailingListenerDoesntStopTheOthers() {
		EventBus b = new EventBus();
		List<Throwable> errors = new ArrayList<>();
		b.setErrorSink(errors::add);
		List<SbEvent> got = new ArrayList<>();
		b.on(SbEvent.JoinedSkyBlock.class, e -> {
			throw new IllegalStateException("boom");
		});
		b.on(SbEvent.JoinedSkyBlock.class, got::add);
		b.post(new SbEvent.JoinedSkyBlock());
		assertEquals(1, got.size());
		assertEquals(1, errors.size());
		assertEquals(1, b.recent().size());
	}
}
