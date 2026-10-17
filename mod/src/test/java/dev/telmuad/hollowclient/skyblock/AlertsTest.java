package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.alerts.AlertEngine;
import dev.telmuad.hollowclient.skyblock.alerts.AlertRule;
import dev.telmuad.hollowclient.skyblock.alerts.AlertStore;
import dev.telmuad.hollowclient.skyblock.data.Election;
import dev.telmuad.hollowclient.skyblock.data.SkyCalendar;
import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockInput;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AlertsTest {
	private final long[] now = {1_000_000};
	private final List<AlertEngine.Fired> fired = new ArrayList<>();
	private String island = "hub";

	private SkyBlockInput setUp(List<AlertRule> rules) {
		EventBus bus = new EventBus();
		bus.setErrorSink(t -> {
			throw new AssertionError(t);
		});
		AlertEngine engine = new AlertEngine(() -> now[0], () -> island, fired::add);
		engine.setRules(rules);
		engine.attach(bus);
		return new SkyBlockInput(new SkyBlockState(), bus, () -> now[0]);
	}

	private static AlertStore builtins(Path dir) {
		AlertStore s = new AlertStore(dir.resolve("alerts.json"));
		s.builtin("rare_drop", "Rare drop", "rare_drop", "§6§l{kind}!", "{item} {mf}", "PLING", true, 500);
		s.builtin("pet_drop", "Pet drop", "pet_drop", "§d§lPET DROP!", "{item} {mf}", "LEVEL_UP", true, 500);
		s.builtin("visitor", "Garden visitor", "visitor_arrived", "§aVisitor", "{name}", "CHIME", false, 1000, "garden");
		s.builtin("burrow", "Griffin burrow", "pattern:griffin_burrow", "Burrow {n}/{total}", "", "DING", false, 0);
		return s;
	}

	@Test
	void builtInAlertsFireWithTheirValues(@TempDir Path dir) {
		SkyBlockInput in = setUp(builtins(dir).rules());
		in.chat("§6§lRARE DROP! §5Shard of the Shredded §b(+312% §b✯ Magic Find§b)");
		in.chat("§5§lPET DROP! §6Ender Dragon");
		assertEquals(2, fired.size());
		assertEquals("§6§lRARE DROP!", fired.get(0).title());
		assertEquals("Shard of the Shredded 312%", fired.get(0).subtitle());
		assertEquals("pet_drop", fired.get(1).rule().id());
		assertEquals("Ender Dragon", fired.get(1).subtitle(), "no magic find, no dangling text");
	}

	@Test
	void islandFilterCooldownAndPatternKeys(@TempDir Path dir) {
		SkyBlockInput in = setUp(builtins(dir).rules());
		in.chat("§aLiam §ehas arrived on your Garden!");
		assertTrue(fired.isEmpty(), "the visitor alert is only for the Garden");
		island = "garden";
		in.chat("§aLiam §ehas arrived on your Garden!");
		in.chat("§aJerry §ehas arrived on your Garden!");
		assertEquals(1, fired.size(), "1 s cooldown");
		now[0] += 1500;
		in.chat("§aJerry §ehas arrived on your Garden!");
		assertEquals(List.of("Liam", "Jerry"), fired.stream().map(AlertEngine.Fired::subtitle).toList());
		in.chat("§eYou dug out a Griffin Burrow! §7(3/6)");
		assertEquals("Burrow 3/6", fired.getLast().title());
	}

	@Test
	void customChatAlertsAndPlayersCantTriggerThem() {
		AlertRule r = AlertStore.newCustom("Wither key", "(?<who>\\w+) has obtained (Wither|Blood) Key!", "{2} key: {who}", "BELL", false);
		assertNull(r.problem());
		SkyBlockInput in = setUp(List.of(r));
		in.chat("§aPlayerTwo §ehas obtained §8Wither Key§e!");
		assertEquals("Wither key: PlayerTwo", fired.getFirst().title());
		in.chat("§8[§9287§8] §b[MVP§c+§b] PlayerOne§f: PlayerOne has obtained Blood Key!");
		assertEquals(1, fired.size(), "a player typing the same words doesn't count");
	}

	@Test
	void badRulesAreRejected() {
		assertNotNull(AlertStore.newCustom("x", "([unclosed", "t", "PLING", false).problem());
		assertNotNull(AlertStore.newCustom("", "a", "t", "PLING", false).problem());
		assertNotNull(AlertStore.newCustom("x", "a", "", "PLING", false).problem());
	}

	@Test
	void storeKeepsChangesAndCustomRules(@TempDir Path dir) throws Exception {
		AlertStore s = builtins(dir);
		AlertRule pet = s.rules().stream().filter(r -> r.id().equals("pet_drop")).findFirst().orElseThrow();
		s.update(pet.withEnabled(false).withSound("BELL"));
		AlertRule mine = AlertStore.newCustom("Mine", "hello", "Hi", "DING", true);
		s.update(mine);
		s.save();
		AlertStore again = builtins(dir);
		again.load();
		AlertRule pet2 = again.rules().stream().filter(r -> r.id().equals("pet_drop")).findFirst().orElseThrow();
		assertFalse(pet2.enabled());
		assertEquals("BELL", pet2.sound());
		assertEquals("§d§lPET DROP!", pet2.title(), "built-in texts stay the built-in ones");
		assertTrue(again.rules().stream().anyMatch(r -> r.id().equals(mine.id()) && r.toast()));
		again.remove(mine.id());
		assertFalse(again.rules().stream().anyMatch(r -> r.id().equals(mine.id())));
	}

	@Test
	void fillPlaceholders() {
		assertEquals("Boss Sven 4", AlertEngine.fill("Boss {boss} {tier}", Map.of("boss", "Sven", "tier", "4")));
		assertEquals("Drop", AlertEngine.fill("Drop {missing}", Map.of()));
		assertEquals("x", AlertEngine.fill("x ({mf})", Map.of("mf", "")));
	}

	// ------------------------------------------------------------------------------------------------ calendar and mayor

	@Test
	void calendarMatchesTheLaunchersMath() {
		long now = 1792195033L; // Early Autumn 13th, Year 520 (the launcher test uses the same moment)
		assertEquals("Early Autumn 13th, Year 520", SkyCalendar.date(now).toString());
		List<SkyCalendar.Event> ev = SkyCalendar.events(now, Set.of(), 12);
		assertTrue(ev.stream().anyMatch(e -> e.name().equals("Harvest Feast") && e.running(now)));
		SkyCalendar.Event jacob = SkyCalendar.nextJacob(now);
		assertEquals(1792196100L, jacob.start(), "the same contest the Elite API lists");
		assertEquals(1200, jacob.end() - jacob.start());
		SkyCalendar.Event spooky = ev.stream().filter(e -> e.name().equals("Spooky Festival")).findFirst().orElseThrow();
		assertEquals(new SkyCalendar.SkyDate(520, 7, 29, 0, 0), SkyCalendar.date(spooky.start()));
		assertTrue(SkyCalendar.events(now, Set.of("Fishing Festival"), 30).stream().anyMatch(e -> e.name().equals("Fishing Festival")));
		assertEquals("12:00am", SkyCalendar.date(SkyCalendar.EPOCH).time());
	}

	@Test
	void mayorFromTheElectionResource() {
		Election.Mayor m = Election.parse("""
				{"success":true,"mayor":{"key":"economist","name":"Diaz","perks":[{"name":"Stock Exchange"},{"name":"Volume Trading"}],
				 "minister":{"key":"farming","name":"Finnegan","perk":{"name":"Blooming Business"}}}}""");
		assertEquals("Diaz", m.name());
		assertEquals(List.of("Stock Exchange", "Volume Trading"), m.perks());
		assertEquals("Finnegan", m.minister());
		assertTrue(m.allPerks().contains("Blooming Business"));
		assertNull(Election.parse("{\"success\":false}"));
		assertNull(Election.parse("not json"));
	}
}
