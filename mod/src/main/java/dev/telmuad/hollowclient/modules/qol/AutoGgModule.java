package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.setting.StringSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Says "gg" for you when a game ends: when a chat line contains one of the end-of-game phrases, your message is sent
 * once after a short delay, never more than once per cooldown. It sends a normal chat message, like typing it.
 */
public final class AutoGgModule extends Module {
	public static final String DEFAULT_PHRASES = "1st Killer,Winner:,won the game,Game over,GAME OVER,You won,VICTORY,Your team won";

	private final StringSetting message = add(new StringSetting("message", "Message", "gg", 100));
	private final StringSetting phrases = add(new StringSetting("phrases", "End-of-game phrases (comma separated)", DEFAULT_PHRASES, 400));
	private final NumberSetting delay = add(new NumberSetting("delay", "Wait before sending", 1.5, 0.5, 5, 0.5).format(1, " s"));
	private final NumberSetting cooldown = add(new NumberSetting("cooldown", "At most once every", 15, 5, 120, 5).format(0, " s"));

	public AutoGgModule() {
		super("auto_gg", "Auto GG", "Sends \"gg\" (or your own message) once when a game ends.", Category.QOL, false);
	}

	public String message() {
		return message.get().trim();
	}

	public List<String> phrases() {
		List<String> out = new ArrayList<>();
		for (String p : phrases.get().split(",")) {
			String t = p.trim().toLowerCase(Locale.ROOT);
			if (t.length() >= 3) out.add(t);
		}
		return out;
	}

	public long delayMs() {
		return Math.round(delay.get() * 1000);
	}

	public long cooldownMs() {
		return Math.round(cooldown.get() * 1000);
	}
}
