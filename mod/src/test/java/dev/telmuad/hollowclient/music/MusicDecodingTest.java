package dev.telmuad.hollowclient.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Uses two short sine tones made for these tests (440 Hz MP3, 880 Hz Ogg, both tagged). */
class MusicDecodingTest {
	@TempDir
	Path dir;

	private Path fixture(String name) throws IOException {
		Path out = dir.resolve(name);
		try (InputStream in = getClass().getResourceAsStream("/audio/" + name)) {
			assertNotNull(in, "missing test file " + name);
			Files.copy(in, out);
		}
		return out;
	}

	@Test
	void mp3TagsAndLength() throws IOException {
		AudioInfo.Info info = AudioInfo.read(fixture("tone440.mp3"));
		assertEquals("Hollow Test Tone", info.title());
		assertEquals("Hollow Client", info.artist());
		assertEquals(2.0, info.seconds(), 0.1);
	}

	@Test
	void oggTagsAndLength() throws IOException {
		AudioInfo.Info info = AudioInfo.read(fixture("tone880.ogg"));
		assertEquals("Hollow Ogg Tone", info.title());
		assertEquals("Hollow Client", info.artist());
		assertEquals(2.0, info.seconds(), 0.05);
	}

	@Test
	void mp3LengthByCountingFramesWithoutXing() throws IOException {
		byte[] bytes = Files.readAllBytes(fixture("tone440.mp3"));
		// break the Xing/Info marker so the frames have to be counted
		String s = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
		int at = Math.max(s.indexOf("Xing"), s.indexOf("Info"));
		assertTrue(at > 0, "the encoder writes an Xing/Info frame");
		bytes[at] = 'Q';
		assertEquals(2.0, AudioInfo.fromMp3(bytes).seconds(), 0.15);
	}

	@Test
	void unreadableFilesFallBackToTheFileName() throws IOException {
		Path junk = dir.resolve("My Song.mp3");
		Files.write(junk, new byte[]{1, 2, 3, 4, 5});
		AudioInfo.Info info = AudioInfo.read(junk);
		assertEquals("My Song", info.title());
		assertEquals(-1, info.seconds());
	}

	private static float dominantFrequency(PcmSource src) throws IOException {
		byte[] buf = new byte[src.channels() * 2 * 8192];
		// skip the encoder's lead-in, then take 4096 frames of the first channel
		src.skipFrames(4410);
		int n = src.read(buf);
		assertTrue(n >= 4096 * src.channels() * 2);
		float[] samples = new float[4096];
		for (int i = 0; i < samples.length; i++) {
			int off = i * src.channels() * 2;
			samples[i] = (short) ((buf[off] & 255) | buf[off + 1] << 8) / 32768f;
		}
		float[] mags = new float[2048];
		Fft.magnitudes(samples, mags);
		int best = 1;
		for (int i = 1; i < mags.length; i++) if (mags[i] > mags[best]) best = i;
		return best * src.sampleRate() / 4096f;
	}

	@Test
	void decodesMp3() throws IOException {
		try (PcmSource src = PcmSource.open(fixture("tone440.mp3"))) {
			assertEquals(44100, src.sampleRate());
			assertEquals(440, dominantFrequency(src), 15);
		}
	}

	@Test
	void decodesOgg() throws IOException {
		try (PcmSource src = PcmSource.open(fixture("tone880.ogg"))) {
			assertEquals(44100, src.sampleRate());
			assertEquals(2, src.channels());
			assertEquals(880, dominantFrequency(src), 15);
		}
	}

	@Test
	void decodesToTheEnd() throws IOException {
		try (PcmSource src = PcmSource.open(fixture("tone880.ogg"))) {
			byte[] buf = new byte[16384];
			long bytes = 0;
			int n;
			while ((n = src.read(buf)) > 0) bytes += n;
			double seconds = bytes / (double) (src.channels() * 2 * src.sampleRate());
			assertEquals(2.0, seconds, 0.05);
		}
	}

	@Test
	void fftFindsASineAndBandsPeakThere() {
		int n = 2048;
		int rate = 48000;
		float[] s = new float[n];
		for (int i = 0; i < n; i++) s[i] = (float) Math.sin(2 * Math.PI * 1000 * i / rate);
		float[] mags = new float[n / 2];
		Fft.magnitudes(s, mags);
		int best = 0;
		for (int i = 1; i < mags.length; i++) if (mags[i] > mags[best]) best = i;
		assertEquals(1000, best * (float) rate / n, rate / (float) n);
		float[] bands = new float[24];
		Fft.bands(mags, rate, n, bands);
		int loudest = 0;
		for (int b = 1; b < bands.length; b++) if (bands[b] > bands[loudest]) loudest = b;
		double lo = Math.exp(Math.log(40) + (Math.log(16000) - Math.log(40)) * loudest / 24);
		double hi = Math.exp(Math.log(40) + (Math.log(16000) - Math.log(40)) * (loudest + 1) / 24);
		assertTrue(lo <= 1100 && hi >= 900, "band " + loudest + " covers " + lo + "-" + hi);
		for (float b : bands) assertTrue(b >= 0 && b <= 1);
	}

	@Test
	void timeFormat() {
		assertEquals("0:05", AudioInfo.time(5.4));
		assertEquals("3:07", AudioInfo.time(187));
		assertEquals("1:02:45", AudioInfo.time(3765));
		assertEquals("--:--", AudioInfo.time(-1));
	}
}
