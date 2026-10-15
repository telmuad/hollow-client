package dev.telmuad.hollowclient.modules.ui;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;

/** Chat with a rounded, translucent background, an accent edge and new messages that slide in. Text is unchanged. */
public final class ChatStyleModule extends Module {
	private final Effect background = effect("background", "Rounded background", true, 60, "One rounded, theme-tinted panel instead of black bars.");
	private final Effect slide = effect("slide", "Slide-in", true, 60, "New messages slide in from the left and fade in.");
	private final BooleanSetting accentEdge = add(new BooleanSetting("accentEdge", "Accent edge", true));
	private final BooleanSetting inputBar = add(new BooleanSetting("inputBar", "Restyle the typing bar", true));

	public ChatStyleModule() {
		super("chat_style", "Chat Style", "Rounded, translucent chat with an accent edge, and new messages that slide in.", Category.INTERFACE, true);
	}

	public Effect background() {
		return background;
	}

	public float slide() {
		return slide.amount();
	}

	public boolean accentEdge() {
		return accentEdge.on();
	}

	public boolean inputBar() {
		return inputBar.on();
	}
}
