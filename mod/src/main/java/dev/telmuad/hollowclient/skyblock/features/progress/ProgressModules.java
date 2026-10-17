package dev.telmuad.hollowclient.skyblock.features.progress;

import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import dev.telmuad.hollowclient.util.AtomicFiles;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** The Progression area's modules: skill progress, your own reminders, a Booster Cookie reminder. */
public final class ProgressModules {
	private ProgressModules() {
	}

	/** The skill you're training: level progress from the action bar, XP gained, per hour and time to the next level. */
	public static final class SkillHud extends SkyBlockHudModule {
		private final Progress.Skills skills = new Progress.Skills();

		public SkillHud() {
			super("sb_skill_progress", "Skill Progress", "The skill you're training: progress to the next level, XP this session, per hour and time to level up.",
					"Progression", true, at(Anchor.CENTER, 0, Anchor.END, 98));
		}

		public Progress.Skills skills() {
			return skills;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.SkillXpGained.class, e -> skills.gained(e.skill(), e.amount(), e.progress(), System.currentTimeMillis()));
			SkyBlock.bus().on(SbEvent.ProfileChanged.class, e -> skills.reset());
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && !skills.last().isEmpty() && System.currentTimeMillis() - skills.lastAt() < 120_000;
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			String skill = skills.last();
			if (skill.isEmpty()) {
				if (preview) out.add(new Line("Farming 62%  ·  +184k XP · 920k/h · level in 1h 10m", 0xFF5BD18B));
				return;
			}
			Progress.Level l = skills.level(skill);
			StringBuilder sb = new StringBuilder(skill);
			if (l != null) sb.append(' ').append(String.format(Locale.ROOT, "%.0f%%", l.percent()));
			sb.append("  ·  +").append(SbText.shortNumber(skills.gained(skill))).append(" XP");
			double ph = skills.perHour(skill, now);
			if (!Double.isNaN(ph)) sb.append(" · ").append(SbText.shortNumber(ph)).append("/h");
			long next = skills.toNextLevel(skill, now);
			if (next >= 0) sb.append(" · level in ").append(SbText.duration(next));
			out.add(new Line(sb.toString(), 0xFF5BD18B));
		}
	}

	/** Your reminders ("/hollow remind 2h30m check the forge"): listed here, and a title and toast when one is due. */
	public static final class RemindersHud extends SkyBlockHudModule {
		private static RemindersHud instance;
		private final Progress.Reminders reminders = new Progress.Reminders();
		private boolean loaded;

		public RemindersHud() {
			super("sb_reminders", "Reminders", "Your reminders from /hollow remind <time> <text> (e.g. /hollow remind 2h30m forge): the next ones, "
					+ "and a title and toast when one is due. Kept between sessions.", "Progression", true, at(Anchor.CENTER, 0, Anchor.START, 30));
			instance = this;
		}

		public static RemindersHud get() {
			return instance;
		}

		public Progress.Reminders reminders() {
			load();
			return reminders;
		}

		private static Path file() {
			return SkyBlock.dir().resolve("reminders.json");
		}

		private void load() {
			if (loaded) return;
			loaded = true;
			try {
				Path f = file();
				if (Files.isRegularFile(f)) reminders.load(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
			} catch (IOException | RuntimeException e) {
				HollowClient.LOG.warn("Reminders couldn't be read: {}", e.toString());
			}
		}

		public void save() {
			try {
				Files.createDirectories(file().getParent());
				AtomicFiles.write(file(), reminders.toJson().toString());
			} catch (IOException e) {
				HollowClient.LOG.warn("Reminders couldn't be saved: {}", e.toString());
			}
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			// reminders fire anywhere in a world, not only on SkyBlock (the forge doesn't wait)
			if (!isEnabled() || mc.player == null || mc.player.tickCount % 20 != 0) return;
			load();
			List<Progress.Reminder> due = reminders.due(System.currentTimeMillis());
			if (due.isEmpty()) return;
			save();
			for (Progress.Reminder r : due) SkyBlockAlerts.notify("reminder", "§e§lReminder", "§f" + r.text(), "CHIME", true);
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return mc.player != null && isEnabled() && SkyBlock.active() && !reminders().upcoming().isEmpty();
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			List<Progress.Reminder> list = reminders().upcoming();
			if (list.isEmpty()) {
				if (!preview) return;
				list = List.of(new Progress.Reminder("Forge: Refined Diamond", now + 3_600_000L), new Progress.Reminder("Pick up Huntraps", now + 9 * 3_600_000L));
			}
			for (int i = 0; i < Math.min(3, list.size()); i++) {
				Progress.Reminder r = list.get(i);
				out.add(new Line(SbText.duration(Math.max(0, r.dueAt() - now)) + "  " + r.text(), i == 0 ? 0xFFF5C542 : 0));
			}
			if (list.size() > 3) out.add(new Line("+" + (list.size() - 3) + " more", 0xFFB8BCCB));
		}
	}

	/** A toast when you join SkyBlock without a Booster Cookie active (from the Cookie Buff tab widget). Off by default. */
	public static final class CookieReminder extends SkyBlockModule {
		private long joinedAt = -1;
		private boolean checked;

		public CookieReminder() {
			super("sb_cookie_reminder", "Booster Cookie Reminder", "A toast when you join SkyBlock and the Cookie Buff tab widget says it isn't active.",
					"Progression", false);
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.JoinedSkyBlock.class, e -> {
				joinedAt = System.currentTimeMillis();
				checked = false;
			});
		}

		/** Whether the Cookie Buff widget (title line plus its lines) says it isn't active; null if the widget isn't shown. */
		public static Boolean inactive(List<String> widget) {
			if (widget.isEmpty()) return null;
			for (String l : widget) if (SbText.clean(l).toLowerCase(Locale.ROOT).contains("not active")) return true;
			return false;
		}

		@Override
		public void onTick() {
			// wait a few seconds after joining so the tab list has arrived
			if (!live() || checked || joinedAt < 0 || System.currentTimeMillis() - joinedAt < 5_000) return;
			checked = true;
			Boolean off = inactive(SkyBlock.state().tab().widget("Cookie Buff"));
			if (Boolean.TRUE.equals(off)) dev.telmuad.hollowclient.gui.Toasts.showBadge("Booster Cookie", "isn't active on this profile");
		}
	}

	/** Parses "2h30m", "45m", "1d", "90s" (plain numbers are minutes); -1 if it can't. */
	public static long parseDelay(String s) {
		if (s.matches("\\d+")) return Long.parseLong(s) * 60_000L;
		return SbText.parseDuration(s);
	}

	/** Adds a reminder from the command; returns the reminder or null if the time couldn't be read. */
	public static Progress.Reminder remind(String delay, String text) {
		long ms = parseDelay(delay);
		RemindersHud h = RemindersHud.get();
		if (ms <= 0 || h == null) return null;
		Progress.Reminder r = h.reminders().add(text, System.currentTimeMillis() + ms);
		h.save();
		return r;
	}
}
