package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.StringSetting;

/**
 * Auto-text hotkeys: up to five messages (or commands starting with /) you can send with one key each. Bind the keys in
 * Options → Controls → Key Binds → Hollow Client ("Chat macro 1" ...). One message per key press, at most one a second.
 */
public final class ChatMacrosModule extends Module {
	public static final int SLOTS = 5;
	private final StringSetting[] texts = new StringSetting[SLOTS];

	public ChatMacrosModule() {
		super("chat_macros", "Chat Macros", "Send a message or command with one key. Bind keys under Controls → Hollow Client.", Category.QOL, true);
		for (int i = 0; i < SLOTS; i++) {
			texts[i] = add(new StringSetting("text" + (i + 1), "Macro " + (i + 1) + " text", "", 256));
		}
	}

	/** Text for slot 0..4 (empty = not set). */
	public String text(int slot) {
		return slot < 0 || slot >= SLOTS ? "" : texts[slot].get().trim();
	}
}
