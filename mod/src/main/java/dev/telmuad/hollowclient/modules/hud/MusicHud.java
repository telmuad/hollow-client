package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.music.AudioInfo;
import dev.telmuad.hollowclient.music.MusicPlayer;
import dev.telmuad.hollowclient.music.Visualizer;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/** Now playing: song, artist, a progress bar and the visualizer. Shows while the Music Player has a song loaded. */
public final class MusicHud extends HudModule {
	private static final int W = 150;

	private final Effect visualizer = effect("visualizer", "Visualizer", true, 80, "Bars that move with the music.");
	private final EnumSetting<Visualizer.Style> style = add(new EnumSetting<>("style", "Visualizer style", Visualizer.Style.BARS));
	private final NumberSetting bars = add(new NumberSetting("bars", "Bars", 24, 8, 48, 2).format(0, ""));
	private final BooleanSetting artist = add(new BooleanSetting("artist", "Show the artist", true));
	private final BooleanSetting time = add(new BooleanSetting("time", "Show the time", true));
	private final Visualizer viz = new Visualizer();

	public MusicHud() {
		super("music_hud", "Music", "Now playing from the Music Player, with a visualizer.", true, at(Anchor.END, 2, Anchor.START, 2));
		style.visibleWhen(visualizer.switchSetting()::get);
		bars.visibleWhen(visualizer.switchSetting()::get);
	}

	@Override
	public boolean visibleInGame(Minecraft mc) {
		return mc.player != null && MusicPlayer.current() != null;
	}

	private int vizHeight() {
		return visualizer.on() ? 18 : 0;
	}

	@Override
	public Size measure(Minecraft mc, Font font, boolean preview) {
		int h = PAD + 10;
		if (artist.on()) h += 10;
		h += 6;
		if (time.on()) h += 9;
		if (visualizer.on()) h += vizHeight() + 3;
		return new Size(W, h + PAD);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, Font font, boolean preview) {
		Size size = measure(mc, font, preview);
		drawBackground(g, size.w(), size.h());
		MusicPlayer.Track t = MusicPlayer.current();
		boolean demo = t == null;
		String title = demo ? "Your song here" : t.info().title;
		String by = demo ? "Music Player" : t.info().artist;
		double pos = demo ? 74 : MusicPlayer.position();
		double len = demo ? 201 : t.info().seconds;
		int x = PAD + 1;
		int y = PAD + 1;
		int inner = W - 2 * PAD - 2;
		// state dot: pulsing while it plays
		boolean playing = demo || MusicPlayer.playing();
		float pulse = playing ? 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / 300.0) : 0.3f;
		Draw.circle(g, x, y + 2, 5, Theme.fade(Theme.accent(), alpha * pulse));
		marquee(g, font, title, x + 8, y, inner - 8, textColor.argb());
		y += 10;
		if (artist.on()) {
			marquee(g, font, by == null || by.isEmpty() ? "Unknown artist" : by, x, y, inner, Theme.mix(textColor.argb(), 0xFF000000, 0.35f));
			y += 10;
		}
		y += 1;
		float progress = len > 0 ? (float) Math.min(1, pos / len) : 0f;
		Draw.roundRect(g, x, y, inner, 3, 1, Theme.fade(0x40FFFFFF, alpha));
		Draw.roundRect(g, x, y, Math.max(3, Math.round(inner * progress)), 3, 1, Theme.fade(Theme.accent(), alpha));
		y += 5;
		if (time.on()) {
			String left = AudioInfo.time(pos);
			String right = len > 0 ? AudioInfo.time(len) : (playing ? "playing" : "paused");
			text(g, font, left, x, y, Theme.mix(textColor.argb(), 0xFF000000, 0.35f));
			text(g, font, right, x + inner - width(font, right), y, Theme.mix(textColor.argb(), 0xFF000000, 0.35f));
			y += 9;
		}
		if (visualizer.on()) {
			y += 2;
			viz.draw(g, x, y, inner, vizHeight(), bars.getInt(), style.get(), visualizer.amount() * 1.15f, alpha, demo);
		}
	}

	/** Text that slides back and forth when it is too long for the box. */
	private void marquee(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w, int color) {
		int tw = width(font, s);
		if (tw <= w) {
			text(g, font, s, x, y, color);
			return;
		}
		double t = Util.getMillis() / 1000.0;
		float travel = tw - w;
		double cycle = 2 + travel / 25.0;
		double phase = (t % (cycle * 2)) / cycle;
		float f = (float) (phase < 1 ? phase : 2 - phase);
		f = Math.max(0, Math.min(1, (f - 0.15f) / 0.7f));
		int off = Math.round(travel * f);
		g.enableScissor(x, y - 1, x + w, y + 10);
		text(g, font, s, x - off, y, color);
		g.disableScissor();
	}
}
