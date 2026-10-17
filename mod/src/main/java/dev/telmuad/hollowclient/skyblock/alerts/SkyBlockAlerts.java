package dev.telmuad.hollowclient.skyblock.alerts;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * SkyBlock alerts in the game: the store, the engine, the built-in alerts and how an alert is shown (a title, a
 * sound and, if asked, a toast). Only the player's own screen and speakers are involved.
 */
public final class SkyBlockAlerts {
	public enum Sound {
		NONE("None"), PLING("Pling"), DING("Ding"), BELL("Bell"), CHIME("Chime"), LEVEL_UP("Level up"), ANVIL("Anvil");

		public final String label;

		Sound(String label) {
			this.label = label;
		}

		public static Sound of(String s) {
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (RuntimeException e) {
				return PLING;
			}
		}

		public Sound next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private static AlertStore store;
	private static AlertEngine engine;

	private SkyBlockAlerts() {
	}

	public static void init() {
		store = new AlertStore(SkyBlock.dir().resolve("alerts.json"));
		registerBuiltins(store);
		store.load();
		engine = new AlertEngine(System::currentTimeMillis, () -> SkyBlock.state().island().mode(), SkyBlockAlerts::show);
		engine.setRules(store.rules());
		engine.attach(SkyBlock.bus());
	}

	/** The general built-in alerts; each area adds its own here as it's built. */
	static void registerBuiltins(AlertStore s) {
		s.builtin("rare_drop", "Rare drop", "rare_drop", "§6§l{kind}!", "{item} {mf}", "PLING", true, 500);
		s.builtin("pet_drop", "Pet drop", "pet_drop", "§d§lPET DROP!", "{item} {mf}", "LEVEL_UP", true, 500);
		s.builtin("slayer_boss", "Slayer boss spawned", "slayer_boss_spawned", "§c§lBOSS SPAWNED", "{boss}", "ANVIL", false, 3000);
		s.builtin("slayer_done", "Slayer quest complete", "slayer_quest_complete", "§a§lQUEST COMPLETE", "{boss}", "DING", false, 3000);
		s.builtin("visitor", "Garden visitor", "visitor_arrived", "§aVisitor", "{name}", "CHIME", false, 1000, "garden");
		s.builtin("skill_level", "Skill level up", "skill_level_up", "§3§l{skill} {level}", "", "LEVEL_UP", false, 1000);
		s.builtin("pests", "Pests spawned", "pattern:pest_spawn", "§c§lPESTS", "Plot {plot}", "ANVIL", false, 3000, "garden");
		s.builtin("bazaar_filled", "Bazaar order filled", "pattern:bazaar_filled", "§6{kind} filled", "{amount}x {item}", "DING", true, 500);
		s.builtin("auction_sold", "Auction sold", "pattern:auction_sold", "§6Auction sold", "{item} for {coins} coins", "LEVEL_UP", true, 500);
	}

	public static List<AlertRule> rules() {
		return store.rules();
	}

	/** Saves a change to an alert and applies it right away. */
	public static void update(AlertRule r) {
		store.update(r);
		apply();
	}

	public static void remove(String id) {
		store.remove(id);
		apply();
	}

	private static void apply() {
		engine.setRules(store.rules());
		try {
			store.save();
		} catch (IOException e) {
			HollowClient.LOG.warn("SkyBlock alerts couldn't be saved: {}", e.toString());
		}
	}

	/** A feature's own alert: a title, a sound and (optionally) a toast. */
	public static void notify(String id, String title, String sub, String sound, boolean toast) {
		AlertRule r = new AlertRule(id, id, true, toast, AlertRule.Trigger.EVENT, "", title, sub, sound, false, java.util.Set.of(), 0);
		show(new AlertEngine.Fired(r, title, sub));
	}

	/** Shows an alert (also used by the editor's Test button). */
	public static void show(AlertEngine.Fired f) {
		Minecraft mc = Minecraft.getInstance();
		if (!mc.isSameThread()) {
			mc.execute(() -> show(f));
			return;
		}
		if (!f.title().isEmpty()) SkyBlock.showTitle(mc, LegacyText.parse(f.title()), LegacyText.parse(f.subtitle()));
		play(mc, Sound.of(f.rule().sound()));
		if (f.rule().toast()) {
			Toasts.showBadge(dev.telmuad.hollowclient.skyblock.text.SbText.clean(f.title()), dev.telmuad.hollowclient.skyblock.text.SbText.clean(f.subtitle()));
		}
	}

	public static void play(Minecraft mc, Sound s) {
		SimpleSoundInstance sound = switch (s) {
			case NONE -> null;
			case PLING -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f);
			case DING -> SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f);
			case BELL -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0f);
			case CHIME -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.2f);
			case LEVEL_UP -> SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f);
			case ANVIL -> anvil();
		};
		if (sound != null) mc.getSoundManager().play(sound);
	}

	private static SimpleSoundInstance anvil() {
		SoundEvent e = SoundEvents.ANVIL_LAND;
		return SimpleSoundInstance.forUI(e, 1.0f, 0.5f);
	}
}
