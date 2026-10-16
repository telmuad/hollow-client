package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.gui.widget.UiSlider;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.qol.MusicPlayerModule;
import dev.telmuad.hollowclient.music.AudioInfo;
import dev.telmuad.hollowclient.music.MusicPlayer;
import dev.telmuad.hollowclient.music.Visualizer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The music player: your songs on the left, now playing on the right with a big visualizer, a progress bar you
 * can click to seek, previous / play / next, shuffle, repeat and volume. Drop MP3 or Ogg files on it to add them.
 */
public final class MusicScreen extends HollowScreen {
	private static final int ROW = 23;

	private final UiButton back = new UiButton("< Back", this::onClose);
	private final UiButton folder = new UiButton("Open folder", this::openFolder);
	private final UiButton settings = new UiButton("Settings", () -> open(new ModuleSettingsScreen(Modules.MUSIC, this)));
	private final UiButton prev = new UiButton("|<", MusicPlayer::previous);
	private final UiButton playPause = new UiButton("Play", MusicPlayer::togglePause).style(UiButton.Style.PRIMARY);
	private final UiButton next = new UiButton(">|", MusicPlayer::next);
	private final UiButton shuffle = new UiButton("Shuffle", () -> module().toggleShuffle());
	private final UiButton repeat = new UiButton("Repeat", () -> module().cycleRepeat());
	private final UiSlider volume = new UiSlider(() -> module().volume(), v -> module().setVolume((float) v));
	private final Visualizer viz = new Visualizer();
	private Rect panel = Rect.EMPTY;
	private Rect list = Rect.EMPTY;
	private Rect now = Rect.EMPTY;
	private Rect bar = Rect.EMPTY;
	private List<MusicPlayer.Track> tracks = List.of();
	private float scroll;
	private float scrollTarget;
	private float[] hover = new float[0];
	private String status;
	private long statusAt;

	public MusicScreen(Screen parent) {
		super(Component.literal("Music"), parent);
	}

	private static MusicPlayerModule module() {
		return Modules.MUSIC;
	}

	@Override
	protected void init() {
		super.init();
		MusicPlayer.rescan();
		tracks = MusicPlayer.tracks();
		hover = new float[tracks.size()];
		int pw = Math.min(width - 24, 620);
		int ph = Math.min(height - 24, 340);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		int fw = folder.preferredWidth(font);
		folder.bounds(new Rect(panel.right() - 8 - fw, panel.y() + 8, fw, 16));
		int sw = settings.preferredWidth(font);
		settings.bounds(new Rect(folder.bounds().x() - 6 - sw, panel.y() + 8, sw, 16));
		int listW = Math.max(150, (pw - 24) * 2 / 5);
		list = new Rect(panel.x() + 8, panel.y() + 32, listW, ph - 32 - 22);
		now = new Rect(list.right() + 8, panel.y() + 32, panel.right() - 8 - list.right() - 8, ph - 32 - 22);
	}

	private void openFolder() {
		try {
			Files.createDirectories(MusicPlayer.folder());
		} catch (IOException ignored) {
			// open what's there
		}
		Util.getPlatform().openPath(MusicPlayer.folder());
	}

	private void say(String s) {
		status = s;
		statusAt = Util.getMillis();
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		folder.render(g, font, mx, my, dt, a);
		settings.render(g, font, mx, my, dt, a);
		Ui.draw(g, font, Ui.bold("Music"), back.bounds().right() + 8, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		renderList(g, mx, my, a);
		renderNowPlaying(g, mx, my, a);
		long age = Util.getMillis() - statusAt;
		String foot = status != null && age < 5000 ? status
				: tracks.size() + (tracks.size() == 1 ? " song" : " songs") + " · drop MP3 or Ogg files here to add them · Space: play/pause";
		Ui.draw(g, font, Ui.fit(font, foot, panel.w() - 20), panel.x() + 10, panel.bottom() - 15, Theme.fade(Theme.TEXT_FAINT, a));
	}

	private void renderList(GuiGraphicsExtractor g, int mx, int my, float a) {
		Draw.roundRect(g, list, Theme.radius(5), Theme.fade(Theme.PANEL_RAISED, a * 0.7f));
		if (tracks.isEmpty()) {
			int cy = list.y() + 30;
			Ui.center(g, font, "No songs yet.", list.centerX(), cy, Theme.fade(Theme.TEXT_DIM, a));
			Ui.center(g, font, "Drop MP3 / Ogg files here,", list.centerX(), cy + 14, Theme.fade(Theme.TEXT_FAINT, a));
			Ui.center(g, font, "or use \"Open folder\".", list.centerX(), cy + 26, Theme.fade(Theme.TEXT_FAINT, a));
			return;
		}
		float max = Math.max(0, tracks.size() * ROW - list.h() + 4);
		scrollTarget = Math.min(scrollTarget, max);
		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		MusicPlayer.Track cur = MusicPlayer.current();
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		for (int i = 0; i < tracks.size(); i++) {
			Rect r = new Rect(list.x() + 2, list.y() + 2 + i * ROW - Math.round(scroll), list.w() - 4, ROW - 2);
			if (r.bottom() < list.y() || r.y() > list.bottom()) continue;
			MusicPlayer.Track t = tracks.get(i);
			boolean isCur = cur != null && cur.file().equals(t.file());
			boolean hov = r.contains(mx, my) && list.contains(mx, my);
			hover[i] = Anim.approach(hover[i], hov ? 1f : 0f, dt, 16f);
			if (isCur) Draw.roundRect(g, r, Theme.radius(4), Theme.fade(Theme.mix(Theme.CARD, Theme.accent(), 0.25f), a));
			else if (hover[i] > 0.01f) Draw.roundRect(g, r, Theme.radius(4), Theme.fade(Theme.CARD_HOVER, a * hover[i]));
			int tx = r.x() + 6;
			if (isCur) {
				// three little bars dancing next to the playing song
				long ms = Util.getMillis();
				for (int k = 0; k < 3; k++) {
					int h = MusicPlayer.playing() ? 2 + (int) (3 * (1 + Math.sin(ms / 140.0 + k * 1.9))) : 2;
					g.fill(tx + k * 3, r.y() + 14 - h, tx + k * 3 + 2, r.y() + 14, Theme.fade(Theme.accent(), a));
				}
				tx += 12;
			}
			Ui.draw(g, font, Ui.fit(font, t.info().title, r.right() - tx - 34), tx, r.y() + 2, Theme.fade(isCur ? Theme.TEXT : Theme.TEXT_DIM, a));
			String by = t.info().artist;
			if (by != null && !by.isEmpty()) Ui.draw(g, font, Ui.fit(font, by, r.right() - tx - 34), tx, r.y() + 12, Theme.fade(Theme.TEXT_FAINT, a));
			String len = AudioInfo.time(t.info().seconds);
			Ui.draw(g, font, len, r.right() - 4 - Ui.width(font, len), r.y() + 7, Theme.fade(Theme.TEXT_FAINT, a));
		}
		g.disableScissor();
	}

	private void renderNowPlaying(GuiGraphicsExtractor g, int mx, int my, float a) {
		MusicPlayerModule m = module();
		Draw.roundRect(g, now, Theme.radius(5), Theme.fade(Theme.PANEL_RAISED, a * 0.7f));
		MusicPlayer.Track t = MusicPlayer.current();
		int x = now.x() + 10;
		int w = now.w() - 20;
		int y = now.y() + 10;
		String title = t == null ? "Nothing playing" : t.info().title;
		String by = t == null ? "Pick a song on the left" : (t.info().artist == null || t.info().artist.isEmpty() ? "Unknown artist" : t.info().artist);
		Ui.drawBold(g, font, Ui.fit(font, title, w), x, y, Theme.fade(Theme.TEXT, a));
		Ui.draw(g, font, Ui.fit(font, by, w), x, y + 12, Theme.fade(Theme.TEXT_DIM, a));
		// visualizer
		int controlsH = 62;
		Rect vizBox = new Rect(x, y + 28, w, Math.max(20, now.bottom() - controlsH - (y + 28) - 4));
		viz.draw(g, vizBox.x(), vizBox.y(), vizBox.w(), vizBox.h(), Math.max(16, Math.min(48, vizBox.w() / 7)), Visualizer.Style.MIRROR,
				1.1f, a, false);
		// progress bar (click to seek)
		int by0 = now.bottom() - controlsH;
		bar = new Rect(x, by0, w, 6);
		double pos = t == null ? 0 : MusicPlayer.position();
		double len = t == null ? -1 : t.info().seconds;
		float p = len > 0 ? (float) Math.min(1, pos / len) : 0f;
		boolean barHover = bar.inset(-3).contains(mx, my) && t != null && len > 0;
		Draw.roundRect(g, x, by0 + 1, w, barHover ? 5 : 3, 2, Theme.fade(Theme.SWITCH_OFF, a));
		Draw.roundRect(g, x, by0 + 1, Math.max(4, Math.round(w * p)), barHover ? 5 : 3, 2, Theme.fade(Theme.accent(), a));
		Ui.draw(g, font, AudioInfo.time(t == null ? 0 : pos), x, by0 + 9, Theme.fade(Theme.TEXT_FAINT, a));
		String total = AudioInfo.time(len);
		Ui.draw(g, font, total, x + w - Ui.width(font, total), by0 + 9, Theme.fade(Theme.TEXT_FAINT, a));
		// controls
		int cy = by0 + 22;
		playPause.setLabel(MusicPlayer.playing() ? "Pause" : "Play");
		int pw = Math.max(52, playPause.preferredWidth(font));
		int cx = x + w / 2;
		prev.bounds(new Rect(cx - pw / 2 - 28, cy, 24, 18)).render(g, font, mx, my, dt, a);
		playPause.bounds(new Rect(cx - pw / 2, cy, pw, 18)).render(g, font, mx, my, dt, a);
		next.bounds(new Rect(cx + pw / 2 + 4, cy, 24, 18)).render(g, font, mx, my, dt, a);
		shuffle.setLabel(m.shuffle() ? "Shuffle: on" : "Shuffle: off");
		repeat.setLabel(switch (m.repeat()) {
			case OFF -> "Repeat: off";
			case ALL -> "Repeat: all";
			case ONE -> "Repeat: one";
		});
		int sy = cy + 22;
		int sw = shuffle.preferredWidth(font);
		shuffle.bounds(new Rect(x, sy, sw, 14)).render(g, font, mx, my, dt, a);
		int rw = repeat.preferredWidth(font);
		repeat.bounds(new Rect(x + sw + 4, sy, rw, 14)).render(g, font, mx, my, dt, a);
		int vx = x + sw + rw + 14;
		Ui.draw(g, font, "Vol", vx, sy + 3, Theme.fade(Theme.TEXT_FAINT, a));
		vx += Ui.width(font, "Vol") + 6;
		volume.bounds(new Rect(vx, sy + 3, Math.max(30, x + w - vx), 8)).render(g, mx, my, dt, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		double mx = event.x();
		double my = event.y();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
		for (UiButton b : new UiButton[]{back, folder, settings, prev, playPause, next, shuffle, repeat}) if (b.click(mx, my)) return true;
		if (volume.click(mx, my)) return true;
		MusicPlayer.Track cur = MusicPlayer.current();
		if (cur != null && cur.info().seconds > 0 && bar.inset(-3).contains(mx, my)) {
			MusicPlayer.seek(cur.info().seconds * Math.max(0, Math.min(1, (mx - bar.x()) / bar.w())));
			UiSounds.click();
			return true;
		}
		if (list.contains(mx, my)) {
			int i = (int) ((my - list.y() - 2 + scroll) / ROW);
			if (i >= 0 && i < tracks.size()) {
				UiSounds.click();
				ripple(mx, my, list);
				int index = MusicPlayer.tracks().indexOf(tracks.get(i));
				MusicPlayer.play(index < 0 ? i : index);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (volume.drag(event.x())) return true;
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		volume.release();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		float max = Math.max(0, tracks.size() * ROW - list.h() + 4);
		scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * 24));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_SPACE) {
			MusicPlayer.togglePause();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		int added = MusicPlayer.importFiles(paths);
		tracks = MusicPlayer.tracks();
		hover = new float[tracks.size()];
		say(added > 0 ? "Added " + added + (added == 1 ? " song." : " songs.") : "Only MP3 and Ogg files can be added.");
	}
}
