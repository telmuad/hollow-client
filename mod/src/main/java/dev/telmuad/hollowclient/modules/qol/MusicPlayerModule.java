package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.gui.MusicScreen;
import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.music.MusicPlayer;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import net.minecraft.client.gui.screens.Screen;

/** Plays your own MP3 and Ogg files while you play. Open the player from this card; the Music HUD shows what plays. */
public final class MusicPlayerModule extends Module implements ScreenProvider {
	public enum Repeat {OFF, ALL, ONE}

	private final NumberSetting volume = add(new NumberSetting("volume", "Volume", 70, 0, 100, 1).format(0, "%"));
	private final BooleanSetting shuffle = add(new BooleanSetting("shuffle", "Shuffle", false));
	private final EnumSetting<Repeat> repeat = add(new EnumSetting<>("repeat", "Repeat", Repeat.ALL));
	private final BooleanSetting quietVanilla = add(new BooleanSetting("quietVanilla", "Pause the game's music while yours plays", true));

	public MusicPlayerModule() {
		super("music", "Music Player", "Play your own MP3 and Ogg files (config/hollowclient/music) with a visualizer.", Category.QOL, true);
	}

	/** 0..1. */
	public float volume() {
		return volume.getFloat() / 100f;
	}

	public void setVolume(float v) {
		volume.set(Math.round(Math.max(0, Math.min(1, v)) * 100));
	}

	public boolean shuffle() {
		return shuffle.on();
	}

	public void toggleShuffle() {
		shuffle.set(!shuffle.on());
	}

	public Repeat repeat() {
		return repeat.get();
	}

	public void cycleRepeat() {
		repeat.cycle(1);
	}

	public boolean quietVanillaMusic() {
		return quietVanilla.on();
	}

	@Override
	protected void onDisable() {
		MusicPlayer.stop();
	}

	@Override
	public Screen createScreen(Screen parent) {
		return new MusicScreen(parent);
	}
}
