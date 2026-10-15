package dev.telmuad.hollowclient.music;

import fr.delthas.javamp3.Sound;
import net.minecraft.client.sounds.JOrbisAudioStream;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Decoded audio as 16-bit little-endian PCM, interleaved. MP3 is decoded by JavaMP3 (MIT), Ogg Vorbis by the JOrbis
 * decoder Minecraft already ships.
 */
public interface PcmSource extends Closeable {
	int channels();

	int sampleRate();

	/** Fill {@code buf} with whole frames; returns the number of bytes, or -1 at the end. */
	int read(byte[] buf) throws IOException;

	static PcmSource open(Path file) throws IOException {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		InputStream in = new BufferedInputStream(Files.newInputStream(file), 64 * 1024);
		try {
			return name.endsWith(".ogg") || name.endsWith(".oga") ? new Ogg(in) : new Mp3(in);
		} catch (IOException | RuntimeException e) {
			in.close();
			throw e instanceof IOException io ? io : new IOException("Couldn't decode " + file.getFileName(), e);
		}
	}

	/** Skip whole frames by decoding and dropping them (for seeking). */
	default void skipFrames(long frames) throws IOException {
		byte[] buf = new byte[16384];
		long bytes = frames * channels() * 2L;
		while (bytes > 0) {
			int n = read(buf);
			if (n < 0) return;
			bytes -= n;
		}
	}

	final class Mp3 implements PcmSource {
		private final Sound sound;
		private final boolean bigEndian;
		private final int channels;

		Mp3(InputStream in) throws IOException {
			sound = new Sound(in);
			bigEndian = sound.getAudioFormat().isBigEndian();
			channels = sound.isStereo() ? 2 : 1;
		}

		@Override
		public int channels() {
			return channels;
		}

		@Override
		public int sampleRate() {
			return sound.getSamplingFrequency();
		}

		@Override
		public int read(byte[] buf) throws IOException {
			int frame = channels * 2;
			int want = buf.length / frame * frame;
			int got = 0;
			while (got < want) {
				int n = sound.read(buf, got, want - got);
				if (n < 0) break;
				got += n;
			}
			if (got == 0) return -1;
			got = got / frame * frame;
			if (bigEndian) {
				for (int i = 0; i + 1 < got; i += 2) {
					byte t = buf[i];
					buf[i] = buf[i + 1];
					buf[i + 1] = t;
				}
			}
			return got;
		}

		@Override
		public void close() throws IOException {
			sound.close();
		}
	}

	final class Ogg implements PcmSource {
		private final InputStream in;
		private final JOrbisAudioStream stream;
		private short[] pending = new short[0];
		private int pendingStart;
		private int pendingEnd;
		private boolean ended;

		Ogg(InputStream in) throws IOException {
			this.in = in;
			this.stream = new JOrbisAudioStream(in);
		}

		@Override
		public int channels() {
			return stream.getFormat().getChannels();
		}

		@Override
		public int sampleRate() {
			return (int) stream.getFormat().getSampleRate();
		}

		private void push(float f) {
			if (pendingEnd == pending.length) {
				short[] grown = new short[Math.max(8192, pending.length * 2)];
				System.arraycopy(pending, pendingStart, grown, 0, pendingEnd - pendingStart);
				pendingEnd -= pendingStart;
				pendingStart = 0;
				pending = grown;
			}
			pending[pendingEnd++] = (short) Math.max(-32768, Math.min(32767, Math.round(f * 32767f)));
		}

		@Override
		public int read(byte[] buf) throws IOException {
			int samplesWanted = buf.length / 2 / channels() * channels();
			while (pendingEnd - pendingStart < samplesWanted && !ended) {
				if (pendingStart > 0) {
					System.arraycopy(pending, pendingStart, pending, 0, pendingEnd - pendingStart);
					pendingEnd -= pendingStart;
					pendingStart = 0;
				}
				if (!stream.readChunk(this::push)) ended = true;
			}
			int n = Math.min(samplesWanted, pendingEnd - pendingStart);
			if (n <= 0) return -1;
			for (int i = 0; i < n; i++) {
				short s = pending[pendingStart + i];
				buf[i * 2] = (byte) s;
				buf[i * 2 + 1] = (byte) (s >> 8);
			}
			pendingStart += n;
			return n * 2;
		}

		@Override
		public void close() throws IOException {
			stream.close();
			in.close();
		}
	}
}
