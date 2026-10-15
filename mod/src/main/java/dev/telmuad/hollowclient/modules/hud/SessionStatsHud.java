package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * Stats for this play session (since you joined the world): time played, kills (mobs or players you hit that died
 * within a few seconds), deaths, blocks you broke and distance walked. Counted from what your client already sees.
 */
public final class SessionStatsHud extends TextHudModule {
	private final BooleanSetting showTime = add(new BooleanSetting("time", "Time played", true));
	private final BooleanSetting showKills = add(new BooleanSetting("kills", "Kills and deaths", true));
	private final BooleanSetting showBlocks = add(new BooleanSetting("blocks", "Blocks broken", true));
	private final BooleanSetting showDistance = add(new BooleanSetting("distance", "Distance walked", true));

	private static ClientLevel level;
	private static long startedAt;
	private static int kills;
	private static int deaths;
	private static int blocks;
	private static double distance;
	private static double lastX = Double.NaN;
	private static double lastZ;
	private static boolean wasDead;

	public SessionStatsHud() {
		super("session_stats", "Session Stats", "Time played, kills, deaths, blocks broken and distance walked this session.", false,
				at(Anchor.END, 2, Anchor.START, 60));
	}

	/** Every client tick: start a new session when the world changes, count deaths and distance. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			startedAt = Util.getMillis();
			kills = deaths = blocks = 0;
			distance = 0;
			lastX = Double.NaN;
			wasDead = false;
		}
		if (mc.player == null || mc.isPaused()) return;
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead) deaths++;
		wasDead = dead;
		if (!mc.player.isPassenger() && !mc.player.getAbilities().flying && !dead) {
			if (!Double.isNaN(lastX)) {
				double dx = mc.player.getX() - lastX;
				double dz = mc.player.getZ() - lastZ;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < 5) distance += d; // ignore teleports
			}
		}
		lastX = mc.player.getX();
		lastZ = mc.player.getZ();
	}

	public static void addKill() {
		kills++;
	}

	public static void addBlock() {
		blocks++;
	}

	public static int kills() {
		return kills;
	}

	public static int blocks() {
		return blocks;
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		boolean demo = preview && mc.player == null;
		long ms = demo ? 4_512_000 : Util.getMillis() - startedAt;
		if (showTime.on()) {
			long s = ms / 1000;
			out.add(Line.of(String.format(Locale.ROOT, "Session %d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)));
		}
		if (showKills.on()) out.add(Line.of("Kills " + (demo ? 7 : kills) + "  Deaths " + (demo ? 1 : deaths)));
		if (showBlocks.on()) out.add(Line.of("Blocks " + (demo ? 312 : blocks)));
		if (showDistance.on()) {
			double d = demo ? 1840 : distance;
			out.add(Line.of(d >= 1000 ? String.format(Locale.ROOT, "Walked %.2f km", d / 1000) : String.format(Locale.ROOT, "Walked %d m", Math.round(d))));
		}
	}
}
