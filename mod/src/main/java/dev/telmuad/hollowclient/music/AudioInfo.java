package dev.telmuad.hollowclient.music;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Title, artist and length of an MP3 or Ogg Vorbis file, read from the file itself (pure Java, no decoding).
 * MP3: ID3v2 tags (v2.2-v2.4) and the Xing/Info frame count, or else every frame header is counted. Ogg: the
 * Vorbis comment header and the last page's sample position. Missing information falls back to the file name and
 * an unknown length (-1).
 */
public final class AudioInfo {
	/** What we know about a track. {@code seconds} is -1 when unknown. */
	public record Info(String title, String artist, double seconds) {
	}

	private static final int[][] BITRATES = {
			// MPEG1 layer 1, 2, 3
			{0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
			{0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
			{0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
			// MPEG2/2.5 layer 1, layer 2 and 3
			{0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
			{0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160}};
	private static final int[][] RATES = {{11025, 12000, 8000}, {0, 0, 0}, {22050, 24000, 16000}, {44100, 48000, 32000}};

	private AudioInfo() {
	}

	public static Info read(Path file) {
		String fallback = stripExtension(file.getFileName().toString());
		try {
			byte[] bytes = Files.readAllBytes(file);
			String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
			Info info = name.endsWith(".ogg") || name.endsWith(".oga") ? fromOgg(bytes) : fromMp3(bytes);
			String title = info.title() == null || info.title().isBlank() ? fallback : info.title();
			return new Info(title, info.artist() == null ? "" : info.artist(), info.seconds());
		} catch (IOException | RuntimeException e) {
			return new Info(fallback, "", -1);
		}
	}

	public static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	// ------------------------------------------------------------------------------------------------ mp3

	public static Info fromMp3(byte[] b) {
		String title = null;
		String artist = null;
		int pos = 0;
		if (b.length > 10 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
			int major = b[3] & 255;
			int flags = b[5] & 255;
			int size = syncsafe(b, 6);
			int end = Math.min(b.length, 10 + size);
			int p = 10;
			if ((flags & 0x40) != 0 && major >= 3 && p + 4 <= end) {
				// extended header: skip it
				int ext = major == 4 ? syncsafe(b, p) : be32(b, p) + 4;
				p += Math.max(0, ext);
			}
			int headerLen = major == 2 ? 6 : 10;
			while (p + headerLen <= end) {
				String id = new String(b, p, major == 2 ? 3 : 4, StandardCharsets.ISO_8859_1);
				if (id.charAt(0) == 0) break;
				int frameSize = major == 2 ? ((b[p + 3] & 255) << 16 | (b[p + 4] & 255) << 8 | (b[p + 5] & 255))
						: major == 4 ? syncsafe(b, p + 4) : be32(b, p + 4);
				int data = p + headerLen;
				if (frameSize <= 0 || data + frameSize > end) break;
				if (id.equals("TIT2") || id.equals("TT2")) title = id3Text(b, data, frameSize);
				else if (id.equals("TPE1") || id.equals("TP1")) artist = id3Text(b, data, frameSize);
				p = data + frameSize;
			}
			pos = 10 + size + ((flags & 0x10) != 0 ? 10 : 0);
		}
		return new Info(title, artist, mp3Seconds(b, pos));
	}

	private static String id3Text(byte[] b, int off, int len) {
		int enc = b[off] & 255;
		Charset cs = switch (enc) {
			case 1 -> StandardCharsets.UTF_16;
			case 2 -> StandardCharsets.UTF_16BE;
			case 3 -> StandardCharsets.UTF_8;
			default -> StandardCharsets.ISO_8859_1;
		};
		String s = new String(b, off + 1, len - 1, cs);
		int nul = s.indexOf('\0');
		return (nul >= 0 ? s.substring(0, nul) : s).trim();
	}

	/** One MPEG audio frame header, or null if the bytes at {@code p} aren't one. */
	private record Frame(int length, int samples, int rate, int version, boolean mono) {
	}

	private static Frame frame(byte[] b, int p) {
		if (p + 4 > b.length) return null;
		int b1 = b[p + 1] & 255;
		int b2 = b[p + 2] & 255;
		if ((b[p] & 255) != 0xFF || (b1 & 0xE0) != 0xE0) return null;
		int version = (b1 >> 3) & 3; // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
		int layer = (b1 >> 1) & 3; // 3 = layer I, 2 = II, 1 = III
		int bitrateIndex = (b2 >> 4) & 15;
		int rateIndex = (b2 >> 2) & 3;
		if (version == 1 || layer == 0 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null;
		int padding = (b2 >> 1) & 1;
		int rate = RATES[version][rateIndex];
		int layerNo = 4 - layer; // 1, 2 or 3
		int table = version == 3 ? layerNo - 1 : (layerNo == 1 ? 3 : 4);
		int bitrate = BITRATES[table][bitrateIndex] * 1000;
		int length;
		int samples;
		if (layerNo == 1) {
			length = (12 * bitrate / rate + padding) * 4;
			samples = 384;
		} else {
			boolean lsf = version != 3 && layerNo == 3;
			length = (lsf ? 72 : 144) * bitrate / rate + padding;
			samples = lsf ? 576 : 1152;
		}
		boolean mono = ((b[p + 3] & 255) >> 6) == 3;
		return length < 4 ? null : new Frame(length, samples, rate, version, mono);
	}

	private static double mp3Seconds(byte[] b, int start) {
		// find the first frame (followed by another valid frame, so random 0xFF bytes don't count)
		int p = Math.max(0, start);
		Frame first = null;
		int limit = Math.min(b.length - 4, p + 256 * 1024);
		while (p < limit) {
			Frame f = frame(b, p);
			if (f != null && (p + f.length() >= b.length || frame(b, p + f.length()) != null)) {
				first = f;
				break;
			}
			p++;
		}
		if (first == null) return -1;
		// Xing / Info header: the encoder already counted the frames
		int side = first.version() == 3 ? (first.mono() ? 17 : 32) : (first.mono() ? 9 : 17);
		int x = p + 4 + side;
		if (x + 12 <= b.length) {
			String tag = new String(b, x, 4, StandardCharsets.ISO_8859_1);
			if ((tag.equals("Xing") || tag.equals("Info")) && (be32(b, x + 4) & 1) != 0) {
				long frames = be32(b, x + 8) & 0xFFFFFFFFL;
				if (frames > 0) return frames * (double) first.samples() / first.rate();
			}
		}
		// otherwise count every frame
		long samples = 0;
		int rate = first.rate();
		while (p < b.length - 4) {
			Frame f = frame(b, p);
			if (f == null) {
				// lost sync (an ID3v1 tag at the end, or damage): look a little further
				int next = p + 1;
				int max = Math.min(b.length - 4, p + 4096);
				while (next < max && frame(b, next) == null) next++;
				if (next >= max) break;
				p = next;
				continue;
			}
			samples += f.samples();
			p += f.length();
		}
		return rate > 0 && samples > 0 ? samples / (double) rate : -1;
	}

	// ------------------------------------------------------------------------------------------------ ogg

	public static Info fromOgg(byte[] b) {
		// rebuild the first packets from the pages (headers can span pages)
		ByteArrayOutputStream packet = new ByteArrayOutputStream();
		int p = 0;
		int found = 0;
		int rate = 0;
		String title = null;
		String artist = null;
		while (found < 2 && p + 27 <= b.length && isPage(b, p)) {
			int segments = b[p + 26] & 255;
			int data = p + 27 + segments;
			for (int i = 0; i < segments && found < 2; i++) {
				int len = b[p + 27 + i] & 255;
				if (data + len > b.length) return new Info(null, null, -1);
				packet.write(b, data, len);
				data += len;
				if (len < 255) {
					byte[] pk = packet.toByteArray();
					packet.reset();
					if (pk.length > 16 && pk[0] == 1 && isVorbis(pk)) {
						rate = le32(pk, 12);
					} else if (pk.length > 11 && pk[0] == 3 && isVorbis(pk)) {
						int q = 7;
						int vendor = le32(pk, q);
						q += 4 + vendor;
						int count = q + 4 <= pk.length ? le32(pk, q) : 0;
						q += 4;
						for (int c = 0; c < count && q + 4 <= pk.length; c++) {
							int l = le32(pk, q);
							q += 4;
							if (l < 0 || q + l > pk.length) break;
							String entry = new String(pk, q, l, StandardCharsets.UTF_8);
							q += l;
							int eq = entry.indexOf('=');
							if (eq <= 0) continue;
							String key = entry.substring(0, eq).toUpperCase(Locale.ROOT);
							if (key.equals("TITLE") && title == null) title = entry.substring(eq + 1).trim();
							if (key.equals("ARTIST") && artist == null) artist = entry.substring(eq + 1).trim();
						}
					}
					found += pk.length > 0 && (pk[0] == 1 || pk[0] == 3) ? 1 : 0;
				}
			}
			p = data;
		}
		double seconds = -1;
		if (rate > 0) {
			for (int q = b.length - 27; q >= 0 && q >= b.length - 256 * 1024; q--) {
				if (isPage(b, q)) {
					long granule = le64(b, q + 6);
					if (granule > 0) seconds = granule / (double) rate;
					break;
				}
			}
		}
		return new Info(title, artist, seconds);
	}

	private static boolean isPage(byte[] b, int p) {
		return b[p] == 'O' && b[p + 1] == 'g' && b[p + 2] == 'g' && b[p + 3] == 'S';
	}

	private static boolean isVorbis(byte[] pk) {
		return pk[1] == 'v' && pk[2] == 'o' && pk[3] == 'r' && pk[4] == 'b' && pk[5] == 'i' && pk[6] == 's';
	}

	// ------------------------------------------------------------------------------------------------ bytes

	private static int syncsafe(byte[] b, int p) {
		return (b[p] & 0x7F) << 21 | (b[p + 1] & 0x7F) << 14 | (b[p + 2] & 0x7F) << 7 | (b[p + 3] & 0x7F);
	}

	private static int be32(byte[] b, int p) {
		return (b[p] & 255) << 24 | (b[p + 1] & 255) << 16 | (b[p + 2] & 255) << 8 | (b[p + 3] & 255);
	}

	private static int le32(byte[] b, int p) {
		return (b[p] & 255) | (b[p + 1] & 255) << 8 | (b[p + 2] & 255) << 16 | (b[p + 3] & 255) << 24;
	}

	private static long le64(byte[] b, int p) {
		return (le32(b, p) & 0xFFFFFFFFL) | ((long) le32(b, p + 4)) << 32;
	}

	/** "3:07" or "1:02:45". */
	public static String time(double seconds) {
		if (seconds < 0) return "--:--";
		long s = (long) seconds;
		return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
				: String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}
}
