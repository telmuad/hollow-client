package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.parse.ActionBarParser;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.SidebarParser;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParsersTest {
	// ------------------------------------------------------------------------------------------------ text

	@Test
	void stripsFormattingEmojiAndInvisibleCharacters() {
		assertEquals("Purse: 1,234 (+5)", SbText.clean("§7 Purse: §61,234 §e(+5) 🎂"));
		assertEquals("⏣ Village", SbText.clean(" §7⏣ §bVillage"));
		assertEquals("ab", SbText.strip("a​b"));
		assertEquals("x y", SbText.strip("x y"));
		assertEquals("", SbText.clean("§7 👾"));
		assertEquals("a", SbText.strip("§ka§r"));
	}

	@Test
	void numbersAndNumerals() {
		assertEquals(1234567, SbText.longOr("1,234,567", -1));
		assertEquals(1500, SbText.longOr("1.5k", -1));
		assertEquals(3_400_000, SbText.longOr("3.4M", -1));
		assertEquals(2_000_000_000L, SbText.longOr("2B", -1));
		assertEquals(-5, SbText.longOr("-5", 0));
		assertEquals(-1, SbText.longOr("nope", -1));
		assertEquals(14, SbText.roman("XIV"));
		assertEquals(60, SbText.roman("LX"));
		assertEquals(7, SbText.roman("7"));
		assertEquals(0, SbText.roman("ABC"));
		assertEquals("XLIX", SbText.toRoman(49));
		assertEquals("1.23M", SbText.shortNumber(1_234_567));
		assertEquals("12.3k", SbText.shortNumber(12_300));
		assertEquals("1,234", SbText.shortNumber(1234));
		assertEquals(320_000, SbText.parseDuration("05m 20s"));
		assertEquals(3_720_000, SbText.parseDuration("1h 2m"));
		assertEquals(65_000, SbText.parseDuration("1:05"));
		assertEquals(-1, SbText.parseDuration("soon"));
		assertEquals("4m 05s", SbText.duration(245_000));
	}

	// ------------------------------------------------------------------------------------------------ patterns

	@Test
	void everyPatternFileCompiles() {
		Patterns.reload();
		for (String group : List.of("sidebar", "tab", "actionbar", "chat", "item")) {
			assertFalse(Patterns.of(group).names().isEmpty(), group);
		}
		assertEquals(List.of(), Patterns.problems());
	}

	@Test
	void overridesReplaceSinglePatterns(@TempDir Path dir) throws Exception {
		try {
			Files.writeString(dir.resolve("chat.json"), "{\"welcome\": \"Welcome to the test!\"}");
			Patterns.setOverrideDir(dir);
			assertNotNull(Patterns.of("chat").matches("welcome", "Welcome to the test!"));
			assertNull(Patterns.of("chat").matches("welcome", "Welcome to Hypixel SkyBlock!"));
			assertTrue(Patterns.of("chat").has("rare_drop"), "the others are still there");
		} finally {
			Patterns.setOverrideDir(null);
		}
	}

	@Test
	void missingPatternNeverMatchesAndIsReported() {
		Patterns.reload();
		assertNull(Patterns.of("chat").matches("does_not_exist", "anything"));
		assertTrue(Patterns.problems().contains("chat.does_not_exist: missing"));
		Patterns.reload();
	}

	// ------------------------------------------------------------------------------------------------ sidebar

	private static SidebarParser.Sidebar sidebar(String key) {
		return SidebarParser.parse(Fixtures.sidebarTitle(key), Fixtures.sidebarLines(key));
	}

	@Test
	void hubSidebar() {
		SidebarParser.Sidebar s = sidebar("hub");
		assertTrue(s.skyblock());
		assertFalse(s.guest());
		assertEquals("m42AB", s.serverId());
		assertEquals("Early Autumn 13th", s.skyDate());
		assertEquals("4:20pm", s.time());
		assertEquals("Village", s.area());
		assertFalse(s.rift());
		assertEquals(1_234_567, s.purse());
		assertEquals(5, s.purseChange());
		assertFalse(s.piggy());
		assertEquals(1234, s.bits());
		assertNull(s.slayer());
		assertNull(s.dungeon());
		assertFalse(s.lines().contains(""), "blank marker lines are dropped");
	}

	@Test
	void coopPiggyAndGuestTitles() {
		SidebarParser.Sidebar s = sidebar("piggy_coop");
		assertTrue(s.coop() && s.piggy());
		assertEquals(512_004, s.purse());
		assertEquals(80, s.bits());
		assertEquals("Your Island", s.area());
		assertTrue(sidebar("guest").guest());
	}

	@Test
	void slayerQuest() {
		SidebarParser.Slayer s = sidebar("slayer_progress").slayer();
		assertEquals("Sven Packmaster", s.boss());
		assertEquals(4, s.tier());
		assertEquals(1234, s.current());
		assertEquals(2000, s.needed());
		assertEquals("Combat XP", s.unit());
		assertFalse(s.bossUp());
		SidebarParser.Slayer up = sidebar("slayer_boss").slayer();
		assertTrue(up.bossUp());
		assertEquals(-1, up.current());
	}

	@Test
	void dungeonRiftKuudraGarden() {
		SidebarParser.Sidebar d = sidebar("dungeon");
		assertEquals("F7", d.dungeon().floor());
		assertEquals(45, d.dungeon().cleared());
		assertEquals(320_000, d.dungeon().elapsedMs());
		assertEquals("m55C", d.serverId());
		SidebarParser.Sidebar r = sidebar("rift");
		assertTrue(r.rift());
		assertEquals("Wyld Woods", r.area());
		assertEquals(12_345, r.motes());
		assertEquals(-1, r.purse());
		assertEquals("T5", sidebar("kuudra").kuudraTier());
		assertEquals(3210, sidebar("garden").copper());
	}

	@Test
	void otherGamesAreNotSkyBlock() {
		SidebarParser.Sidebar s = sidebar("bedwars");
		assertFalse(s.skyblock());
		assertEquals(-1, s.purse());
	}

	// ------------------------------------------------------------------------------------------------ tab list

	@Test
	void hubTabList() {
		TabListParser.TabInfo t = TabListParser.parse(Fixtures.tab("hub"));
		assertEquals(24, t.players());
		assertEquals("Hub", t.area());
		assertEquals(Island.HUB, Island.fromName(t.area()));
		assertEquals("mini42AB", t.server());
		assertEquals("Apple", t.profile());
		assertEquals(TabListParser.GameMode.IRONMAN, t.gameMode());
		assertEquals(287, t.sbLevel());
		assertEquals("1.23B", t.bank());
		assertEquals("Golden Dragon", t.pet());
		assertEquals(100, t.petLevel());
		assertEquals("240/253", t.value("Fairy Souls"));
		assertEquals("✦400", t.value("speed"));
		assertTrue(t.widget("Skills").contains("Speed: ✦400"));
		assertFalse(t.values().containsKey("playerone"), "player entries aren't widgets");
	}

	@Test
	void gardenDungeonMinesTabLists() {
		TabListParser.TabInfo g = TabListParser.parse(Fixtures.tab("garden"));
		assertEquals(Island.GARDEN, Island.fromName(g.area()));
		assertTrue(g.coop());
		assertEquals("Banana", g.profile());
		assertEquals("(3)", g.value("visitors"));
		assertEquals(List.of("Visitors: (3)", "Jerry", "Liam", "Farmer Jon"), g.widget("Visitors"));
		assertEquals("XV", g.value("garden level"));

		TabListParser.TabInfo d = TabListParser.parse(Fixtures.tab("dungeon"));
		assertEquals(Island.DUNGEON, Island.fromName(d.area()));
		assertEquals(TabListParser.GameMode.BINGO, d.gameMode());
		assertEquals("45%", d.value("secrets found"));

		TabListParser.TabInfo m = TabListParser.parse(Fixtures.tab("mines"));
		assertEquals(Island.DWARVEN_MINES, Island.fromName(m.area()));
		assertEquals(TabListParser.GameMode.STRANDED, m.gameMode());
		assertEquals(List.of("Commissions:", "Royal Mines Mithril: 45%", "Goblin Slayer: DONE"), m.widget("Commissions"));
	}

	// ------------------------------------------------------------------------------------------------ action bar

	@Test
	void actionBar() {
		ActionBarParser.ActionBar a = ActionBarParser.parse("§c1,234/1,500❤     §a512§a❈ Defense     §b300/400✎ Mana");
		assertEquals(1234, a.health());
		assertEquals(1500, a.maxHealth());
		assertEquals(512, a.defense());
		assertEquals(300, a.mana());
		assertEquals(400, a.maxMana());
		assertNull(a.skill());

		ActionBarParser.ActionBar s = ActionBarParser.parse("§c900/900❤   §3+12.5 Farming (1,234/5,000)   §b80/80✎ §3200ʬ");
		assertEquals("Farming", s.skill().skill());
		assertEquals(12.5, s.skill().gained(), 1e-9);
		assertEquals("1,234/5,000", s.skill().progress());
		assertEquals(200, s.overflowMana());

		ActionBarParser.ActionBar r = ActionBarParser.parse("§a5m 20sф Left    §73/7 Secrets");
		assertEquals(320_000, r.riftTimeMs());
		assertEquals(3, r.secretsFound());
		assertEquals(7, r.secretsTotal());
		assertEquals(-1, r.health());
	}

	// ------------------------------------------------------------------------------------------------ chat

	@Test
	void chatEvents() {
		ChatParser.ChatMatch drop = ChatParser.parse("§6§lRARE DROP! §5Shard of the Shredded §b(+312% §b✯ Magic Find§b)").getFirst();
		assertEquals("rare_drop", drop.name());
		assertEquals("RARE DROP", drop.get("kind"));
		assertEquals("Shard of the Shredded", drop.get("item"));
		assertEquals("312", drop.get("mf"));

		ChatParser.ChatMatch pet = ChatParser.parse("§5§lPET DROP! §6Ender Dragon").getFirst();
		assertEquals("PET DROP", pet.get("kind"));
		assertEquals("", pet.get("mf"));

		ChatParser.ChatMatch two = ChatParser.parse("§9§lVERY RARE DROP! §7(§f2x§7) §9Enchanted Diamond").getFirst();
		assertEquals("2", two.get("count"));

		assertEquals("profile_id", ChatParser.parse("§aProfile ID: 0b1c2d3e-0000-4000-8000-0000000000aa").getFirst().name());
		assertEquals("Apple", ChatParser.parse("§aYou are playing on profile: §eApple §b(Co-op)").getFirst().get("name"));
		assertEquals("Liam", ChatParser.parse("§aLiam §ehas arrived on your Garden!").getFirst().get("name"));
		assertEquals("XXV", ChatParser.parse("§b§l  SKILL LEVEL UP §3Farming XXIV§8➜§3XXV".replace("§8➜", " ➜ ")).getFirst().get("to"));
		assertFalse(ChatParser.parse("§a§l  SLAYER QUEST COMPLETE!").isEmpty());
	}

	@Test
	void playerMessagesNeverBecomeEvents() {
		for (String msg : List.of(
				"§8[§9287§8] §b[MVP§c+§b] PlayerOne§f: RARE DROP! fake item",
				"§dFrom §b[MVP§c+§b] PlayerOne§7: hi",
				"§9Party §8> §b[MVP+] PlayerOne§f: Welcome to Hypixel SkyBlock!",
				"§2Guild > PlayerTwo: hello",
				"§bCo-op > PlayerOne: Liam has arrived on your Garden!")) {
			assertTrue(ChatParser.isFromAPlayer(SbText.clean(msg)), msg);
			assertTrue(ChatParser.parse(msg).isEmpty(), msg);
		}
		assertFalse(ChatParser.isFromAPlayer("Welcome to Hypixel SkyBlock!"));
		assertFalse(ChatParser.isFromAPlayer("[BOSS] Necron: Finally, I heard so much about you."));
	}

	// ------------------------------------------------------------------------------------------------ items

	@Test
	void hyperion() {
		ItemParser.SbItem i = Fixtures.item("hyperion").parse();
		assertEquals("HYPERION", i.id());
		assertEquals("heroic", i.reforge());
		assertEquals(ItemParser.Rarity.MYTHIC, i.rarity());
		assertTrue(i.dungeon());
		assertEquals("SWORD", i.type());
		assertEquals(6, i.stars());
		assertEquals(15, i.hotPotatoes());
		assertTrue(i.recombobulated());
		assertEquals(5, i.enchantments().get("ultimate_wise"));
		assertEquals("PERFECT", i.gems().get("COMBAT_0"));
		assertTrue(i.museum());
		assertEquals("HYPERION", i.priceId());
		assertEquals(ItemParser.Rarity.LEGENDARY, i.rarity().previous());
		assertEquals("Heroic Hyperion ✪✪✪✪✪➊", i.name());
	}

	@Test
	void petsBooksAndOthers() {
		ItemParser.SbItem pet = Fixtures.item("pet").parse();
		assertEquals("GOLDEN_DRAGON", pet.pet().type());
		assertEquals(ItemParser.Rarity.LEGENDARY, pet.pet().tier());
		assertEquals(2.5e8, pet.pet().exp(), 1);
		assertTrue(pet.pet().active());
		assertEquals("DWARF_TURTLE_SHELMET", pet.pet().heldItem());
		assertEquals("GOLDEN_DRAGON;4", pet.priceId());

		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", Fixtures.item("book").parse().priceId());

		ItemParser.SbItem drill = Fixtures.item("soulbound").parse();
		assertTrue(drill.soulbound());
		assertEquals(ItemParser.Rarity.LEGENDARY, drill.rarity());
		assertEquals("DRILL", drill.type());

		ItemParser.SbItem glass = Fixtures.item("menu_glass").parse();
		assertFalse(glass.isSkyBlock());
		assertEquals(ItemParser.Rarity.UNKNOWN, glass.rarity());
	}

	@Test
	void brokenPetInfoIsIgnored() {
		com.google.gson.JsonObject d = new com.google.gson.JsonObject();
		d.addProperty("id", "PET");
		d.addProperty("petInfo", "{not json");
		assertNull(ItemParser.parse(d, "x", List.of(), 1).pet());
	}
}
