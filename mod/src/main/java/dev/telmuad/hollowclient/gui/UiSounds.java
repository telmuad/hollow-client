package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;

/**
 * Hollow's UI sounds (original, see tools/soundgen). They are client-only sound events defined in sounds.json, so
 * nothing is registered and servers never hear about them.
 */
public final class UiSounds {
	public static final SoundEvent CLICK = event("click");
	public static final SoundEvent HOVER = event("hover");
	public static final SoundEvent TOGGLE_ON = event("toggle_on");
	public static final SoundEvent TOGGLE_OFF = event("toggle_off");
	public static final SoundEvent OPEN = event("whoosh_open");
	public static final SoundEvent CLOSE = event("whoosh_close");
	public static final SoundEvent TOAST = event("toast");
	public static final SoundEvent NOTIFY = event("notify");
	public static final SoundEvent SHUTTER = event("shutter");
	public static final SoundEvent CHARGE = event("charge");
	public static final SoundEvent POP = event("pop");

	private static long lastHover;

	private UiSounds() {
	}

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(HollowClient.id("ui." + name));
	}

	public static boolean enabled() {
		return Modules.UI_SOUNDS != null && Modules.UI_SOUNDS.isEnabled() && Modules.UI_SOUNDS.volume() > 0;
	}

	public static void play(SoundEvent sound) {
		play(sound, 1f, 1f);
	}

	public static void play(SoundEvent sound, float pitch, float volume) {
		if (!enabled()) return;
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume * Modules.UI_SOUNDS.volume()));
	}

	/** Hover sounds are rate limited so sweeping the mouse over a grid doesn't rattle. */
	public static void hover() {
		if (!enabled() || !Modules.UI_SOUNDS.hoverSounds()) return;
		long now = Util.getMillis();
		if (now - lastHover < 60) return;
		lastHover = now;
		play(HOVER, 1f, 0.5f);
	}

	/** The normal button click: Hollow's sound if UI sounds are on, otherwise vanilla's. */
	public static void click() {
		if (enabled()) play(CLICK);
		else net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
	}

	public static void toggle(boolean on) {
		if (enabled()) play(on ? TOGGLE_ON : TOGGLE_OFF);
		else click();
	}
}
