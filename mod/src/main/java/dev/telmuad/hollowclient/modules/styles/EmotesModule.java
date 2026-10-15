package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;

/**
 * Emotes your own player plays (wave, dance, clap ...), picked from a wheel (hold B). They only animate your model on
 * your screen; nothing is sent to the server, so other players don't see them.
 */
public final class EmotesModule extends Module {
	private final BooleanSetting thirdPerson = add(new BooleanSetting("thirdPerson", "Switch to third person while emoting", true));
	private final BooleanSetting stopWhenMoving = add(new BooleanSetting("stopWhenMoving", "Stop when you move", true));

	public EmotesModule() {
		super("emotes", "Emotes", "Hold B for the emote wheel. Emotes play on your own screen only.", Category.STYLES, true);
	}

	public BooleanSetting thirdPersonSetting() {
		return thirdPerson;
	}

	public BooleanSetting stopWhenMovingSetting() {
		return stopWhenMoving;
	}

	public boolean thirdPerson() {
		return thirdPerson.on();
	}

	public boolean stopWhenMoving() {
		return stopWhenMoving.on();
	}
}
