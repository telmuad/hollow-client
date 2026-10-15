package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.StringSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chat upgrades, all on your side only: timestamps, stacking repeated messages ("x3"), right-click a message to copy
 * it, Ctrl+F to search the chat history, and highlighting (with a sound) when a message mentions one of your words.
 */
public final class ChatPlusModule extends Module {
	private final BooleanSetting timestamps = add(new BooleanSetting("timestamps", "Timestamps", true));
	private final BooleanSetting seconds = add(new BooleanSetting("seconds", "Show seconds", false));
	private final BooleanSetting twentyFour = add(new BooleanSetting("twentyFour", "24-hour time", true));
	private final BooleanSetting stack = add(new BooleanSetting("stack", "Stack repeated messages (x3)", true));
	private final BooleanSetting copy = add(new BooleanSetting("copy", "Right-click a message to copy it", true));
	private final BooleanSetting search = add(new BooleanSetting("search", "Ctrl+F in chat to search", true));
	private final BooleanSetting highlight = add(new BooleanSetting("highlight", "Highlight my words", true));
	private final BooleanSetting myName = add(new BooleanSetting("myName", "My name counts as a word", true));
	private final StringSetting words = add(new StringSetting("words", "Words (comma separated)", "", 200));
	private final BooleanSetting sound = add(new BooleanSetting("sound", "Play a sound on a highlight", true));

	public ChatPlusModule() {
		super("chat_plus", "Chat+", "Timestamps, x3 stacking, right-click to copy, Ctrl+F search and keyword highlights with a sound.", Category.QOL, true);
		seconds.visibleWhen(timestamps::get);
		twentyFour.visibleWhen(timestamps::get);
		myName.visibleWhen(highlight::get);
		words.visibleWhen(highlight::get);
		sound.visibleWhen(highlight::get);
	}

	public boolean timestamps() {
		return timestamps.on();
	}

	public boolean seconds() {
		return seconds.on();
	}

	public boolean twentyFour() {
		return twentyFour.on();
	}

	public boolean stack() {
		return stack.on();
	}

	public boolean copy() {
		return copy.on();
	}

	public boolean search() {
		return search.on();
	}

	public boolean highlight() {
		return highlight.on();
	}

	public boolean myName() {
		return myName.on();
	}

	public boolean sound() {
		return sound.on();
	}

	/** The highlight words, trimmed, lower case, at least two characters each. */
	public List<String> words() {
		List<String> out = new ArrayList<>();
		for (String w : words.get().split(",")) {
			String t = w.trim().toLowerCase(Locale.ROOT);
			if (t.length() >= 2) out.add(t);
		}
		return out;
	}
}
