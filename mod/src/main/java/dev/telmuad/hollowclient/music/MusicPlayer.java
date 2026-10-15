package dev.telmuad.hollowclient.music;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.qol.MusicPlayerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays your own MP3 and Ogg files from {@code config/hollowclient/music}. Decoding and playback run on a
 * background thread through Java's sound output (separate from Minecraft's sounds, so nothing in the game is
 * changed); volume follows the module's slider times the game's master volume. A copy of what is playing is kept
 * for the visualizer.
 */
public final class MusicPlayer {
	public record Track(Path file, Info info) {
	}

	/** Track details, filled in on a background thread (the file name until then). */
	public static final class Info {
		public volatile String title;
		public volatile String artist = "";
		public volatile double seconds = -1;

		Info(String title) {
			this.title = title;
		}
	}

	private static final int RING = 16384;
	private static final int FFT = 2048;
	private static final ExecutorService INFO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Hollow music info");
		t.setDaemon(true);
		return t;
	});
	private static final Map<Path, Info> INFO_CACHE = new ConcurrentHashMap<>();
	private static final Random RANDOM = new Random();

	private static final List<Track> TRACKS = new ArrayList<>();
	private static final float[] RING_SAMPLES = new float[RING];
	private static volatile long ringWritten;
	private static volatile int ringRate = 44100;

	private static Thread thread;
	private static volatile Track current;
	private static volatile boolean paused;
	private static volatile boolean stopRequested;
	private static volatile double seekRequest = -1;
	private static volatile SourceDataLine line;
	/** Frames that went into the line before the current one started (after a seek: where it started). */
	private static volatile long baseFrames;
	private static volatile long lineStartWritten;
	private static volatile int currentRate = 44100;
	private static final List<Integer> history = new ArrayList<>();
	private static String lastError;

	private MusicPlayer() {
	}

	public static Path folder() {
		return HollowClient.dataDir().resolve("music");
	}

	private static MusicPlayerModule module() {
		return Modules.MUSIC;
	}

	// ------------------------------------------------------------------------------------------------ library

	public static synchronized List<Track> tracks() {
		return new ArrayList<>(TRACKS);
	}

	/** Look for MP3 and Ogg files in the music folder (and one level of subfolders). */
	public static synchronized void rescan() {
		List<Path> files = new ArrayList<>();
		Path dir = folder();
		try {
			Files.createDirectories(dir);
			collect(dir, files, 1);
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't read the music folder", e);
		}
		files.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
		TRACKS.clear();
		for (Path f : files) {
			Info info = INFO_CACHE.computeIfAbsent(f, p -> {
				Info i = new Info(AudioInfo.stripExtension(p.getFileName().toString()));
				INFO.execute(() -> {
					AudioInfo.Info read = AudioInfo.read(p);
					i.title = read.title();
					i.artist = read.artist();
					i.seconds = read.seconds();
				});
				return i;
			});
			TRACKS.add(new Track(f, info));
		}
	}

	private static void collect(Path dir, List<Path> out, int depth) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path p : stream) {
				if (Files.isDirectory(p)) {
					if (depth > 0) collect(p, out, depth - 1);
					continue;
				}
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".oga")) out.add(p);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ controls

	public static Track current() {
		return current;
	}

	public static boolean playing() {
		return current != null && !paused;
	}

	public static boolean paused() {
		return current != null && paused;
	}

	public static synchronized void play(int index) {
		if (TRACKS.isEmpty()) rescan();
		if (TRACKS.isEmpty()) {
			Toasts.info("No music yet", "Put MP3 or Ogg files in config/hollowclient/music (Music Player → Open folder).");
			return;
		}
		index = Math.floorMod(index, TRACKS.size());
		history.add(index);
		if (history.size() > 100) history.remove(0);
		start(TRACKS.get(index), 0);
	}

	private static synchronized void start(Track track, double atSeconds) {
		stopThread();
		current = track;
		paused = false;
		stopRequested = false;
		seekRequest = atSeconds > 0 ? atSeconds : -1;
		Thread t = new Thread(() -> run(track), "Hollow music player");
		t.setDaemon(true);
		thread = t;
		t.start();
	}

	public static void togglePause() {
		if (current == null) {
			play(0);
			return;
		}
		paused = !paused;
		SourceDataLine l = line;
		if (l != null) {
			if (paused) l.stop();
			else l.start();
		}
	}

	public static synchronized void next() {
		int i = indexOf(current);
		MusicPlayerModule m = module();
		if (m != null && m.shuffle() && TRACKS.size() > 1) {
			int n;
			do {
				n = RANDOM.nextInt(TRACKS.size());
			} while (n == i);
			play(n);
		} else {
			play(i + 1);
		}
	}

	public static synchronized void previous() {
		// restart the song if it has played a few seconds, otherwise go back
		if (current != null && position() > 3) {
			seek(0);
			return;
		}
		if (history.size() >= 2) {
			history.remove(history.size() - 1);
			int prev = history.remove(history.size() - 1);
			play(prev);
		} else {
			play(indexOf(current) - 1);
		}
	}

	public static void seek(double seconds) {
		Track t = current;
		if (t == null) return;
		start(t, Math.max(0, seconds));
	}

	public static synchronized void stop() {
		stopThread();
		current = null;
		paused = false;
	}

	private static int indexOf(Track t) {
		if (t == null) return -1;
		for (int i = 0; i < TRACKS.size(); i++) if (TRACKS.get(i).file().equals(t.file())) return i;
		return -1;
	}

	private static void stopThread() {
		Thread t = thread;
		if (t == null) return;
		stopRequested = true;
		SourceDataLine l = line;
		if (l != null) {
			l.stop();
			l.flush();
		}
		try {
			t.join(1500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		thread = null;
	}

	/** Seconds into the current track (what you hear, not what was decoded). */
	public static double position() {
		SourceDataLine l = line;
		long frames = baseFrames + (l == null ? 0 : l.getLongFramePosition());
		return frames / (double) Math.max(1, currentRate);
	}

	public static String lastError() {
		return lastError;
	}

	// ------------------------------------------------------------------------------------------------ playback

	private static float gain() {
		MusicPlayerModule m = module();
		float own = m == null ? 0.7f : m.volume();
		float master = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
		return own * own * master;
	}

	private static void run(Track track) {
		boolean finished = false;
		try (PcmSource src = PcmSource.open(track.file())) {
			int channels = src.channels();
			int rate = src.sampleRate();
			currentRate = rate;
			ringRate = rate;
			baseFrames = 0;
			double seek = seekRequest;
			seekRequest = -1;
			if (seek > 0) {
				long skip = (long) (seek * rate);
				src.skipFrames(skip);
				baseFrames = skip;
			}
			AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
			SourceDataLine l = AudioSystem.getSourceDataLine(format);
			int frameBytes = channels * 2;
			l.open(format, rate / 8 * frameBytes);
			if (!paused) l.start();
			line = l;
			lineStartWritten = ringWritten;
			byte[] buf = new byte[rate / 20 * frameBytes];
			try {
				while (!stopRequested) {
					if (paused) {
						Thread.sleep(20);
						continue;
					}
					int n = src.read(buf);
					if (n < 0) {
						finished = true;
						break;
					}
					float g = gain();
					int frames = n / frameBytes;
					long w = ringWritten;
					for (int f = 0; f < frames; f++) {
						float mono = 0f;
						for (int c = 0; c < channels; c++) {
							int i = f * frameBytes + c * 2;
							int s = (short) ((buf[i] & 255) | buf[i + 1] << 8);
							mono += s / 32768f;
							int v = Math.round(s * g);
							buf[i] = (byte) v;
							buf[i + 1] = (byte) (v >> 8);
						}
						RING_SAMPLES[(int) ((w + f) % RING)] = mono / channels;
					}
					ringWritten = w + frames;
					int off = 0;
					while (off < n && !stopRequested) off += l.write(buf, off, n - off);
				}
				if (finished && !stopRequested) l.drain();
			} finally {
				line = null;
				l.close();
			}
		} catch (IOException | LineUnavailableException | RuntimeException e) {
			lastError = e.getMessage();
			HollowClient.LOG.warn("Couldn't play {}", track.file().getFileName(), e);
			Minecraft.getInstance().execute(() -> Toasts.error("Couldn't play this song", track.info().title));
			finished = false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (finished && !stopRequested) Minecraft.getInstance().execute(MusicPlayer::onTrackEnd);
	}

	private static void onTrackEnd() {
		MusicPlayerModule m = module();
		if (current == null) return;
		MusicPlayerModule.Repeat repeat = m == null ? MusicPlayerModule.Repeat.ALL : m.repeat();
		int i = indexOf(current);
		if (repeat == MusicPlayerModule.Repeat.ONE) {
			start(current, 0);
		} else if (repeat == MusicPlayerModule.Repeat.ALL || (m != null && m.shuffle()) || i < TRACKS.size() - 1) {
			next();
		} else {
			current = null;
		}
	}

	/** Every client tick: keep vanilla's music quiet while ours plays (if wanted). */
	public static void tick(Minecraft mc) {
		MusicPlayerModule m = module();
		if (m == null) return;
		if (!m.isEnabled() && current != null) stop();
		if (playing() && m.quietVanillaMusic()) mc.getMusicManager().stopPlaying();
	}

	// ------------------------------------------------------------------------------------------------ visualizer

	/**
	 * The spectrum of what you hear right now, in {@code out.length} bands (0..1). Returns false when nothing plays.
	 */
	public static boolean spectrum(float[] out) {
		SourceDataLine l = line;
		if (l == null || paused || current == null) return false;
		long heard = lineStartWritten + l.getLongFramePosition();
		long written = ringWritten;
		long end = Math.min(heard, written);
		if (written - end > RING - FFT || end < FFT) return false;
		float[] samples = new float[FFT];
		for (int i = 0; i < FFT; i++) samples[i] = RING_SAMPLES[(int) ((end - FFT + i) % RING)];
		float[] mags = new float[FFT / 2];
		Fft.magnitudes(samples, mags);
		Fft.bands(mags, ringRate, FFT, out);
		return true;
	}

	/** The last {@code out.length} samples you heard (a waveform, -1..1). */
	public static boolean waveform(float[] out) {
		SourceDataLine l = line;
		if (l == null || paused || current == null) return false;
		long end = Math.min(lineStartWritten + l.getLongFramePosition(), ringWritten);
		if (end < out.length) return false;
		for (int i = 0; i < out.length; i++) out[i] = RING_SAMPLES[(int) ((end - out.length + i) % RING)];
		return true;
	}

	/** Copy files into the music folder (drag and drop). Returns how many were added. */
	public static int importFiles(List<Path> paths) {
		int added = 0;
		for (Path p : paths) {
			String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
			if (!(n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".oga")) || !Files.isRegularFile(p)) continue;
			try {
				Files.createDirectories(folder());
				Path target = dev.telmuad.hollowclient.util.FileNames.unique(folder(), p.getFileName().toString());
				Files.copy(p, target);
				added++;
			} catch (IOException e) {
				HollowClient.LOG.warn("Couldn't import {}", p.getFileName(), e);
			}
		}
		if (added > 0) rescan();
		return added;
	}
}
