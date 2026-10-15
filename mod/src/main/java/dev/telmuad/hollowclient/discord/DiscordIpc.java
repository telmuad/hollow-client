package dev.telmuad.hollowclient.discord;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A minimal client for the Discord desktop app's local IPC (the same connection Rich Presence libraries use): a
 * named pipe on Windows, a Unix socket elsewhere. Frames are an opcode and a length (little-endian) followed by
 * JSON. Nothing leaves your computer except through the Discord app itself.
 */
public final class DiscordIpc implements Closeable {
	public static final int HANDSHAKE = 0;
	public static final int FRAME = 1;
	public static final int CLOSE = 2;
	public static final int PING = 3;
	public static final int PONG = 4;

	/** One received frame. */
	public record Frame(int op, String json) {
	}

	private interface Pipe extends Closeable {
		void write(byte[] bytes) throws IOException;

		void readFully(byte[] bytes) throws IOException;
	}

	private final Pipe pipe;

	private DiscordIpc(Pipe pipe) {
		this.pipe = pipe;
	}

	/** Connect to the first Discord IPC endpoint that answers, or throw. */
	public static DiscordIpc connect() throws IOException {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		IOException last = null;
		for (int i = 0; i < 10; i++) {
			try {
				if (windows) return new DiscordIpc(windowsPipe("\\\\.\\pipe\\discord-ipc-" + i));
				for (String base : unixDirs()) {
					Path p = Path.of(base, "discord-ipc-" + i);
					if (Files.exists(p)) return new DiscordIpc(unixSocket(p));
				}
			} catch (IOException e) {
				last = e;
			}
		}
		throw last != null ? last : new IOException("Discord isn't running");
	}

	private static String[] unixDirs() {
		String[] vars = {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String v : vars) {
			String d = System.getenv(v);
			if (d != null && !d.isBlank()) {
				out.add(d);
				out.add(d + "/app/com.discordapp.Discord");
				out.add(d + "/snap.discord");
			}
		}
		out.add("/tmp");
		return out.toArray(String[]::new);
	}

	private static Pipe windowsPipe(String name) throws IOException {
		RandomAccessFile file = new RandomAccessFile(name, "rw");
		return new Pipe() {
			@Override
			public void write(byte[] bytes) throws IOException {
				file.write(bytes);
			}

			@Override
			public void readFully(byte[] bytes) throws IOException {
				file.readFully(bytes);
			}

			@Override
			public void close() throws IOException {
				file.close();
			}
		};
	}

	private static Pipe unixSocket(Path path) throws IOException {
		SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
		channel.connect(UnixDomainSocketAddress.of(path));
		return new Pipe() {
			@Override
			public void write(byte[] bytes) throws IOException {
				ByteBuffer b = ByteBuffer.wrap(bytes);
				while (b.hasRemaining()) channel.write(b);
			}

			@Override
			public void readFully(byte[] bytes) throws IOException {
				ByteBuffer b = ByteBuffer.wrap(bytes);
				while (b.hasRemaining()) {
					if (channel.read(b) < 0) throw new EOFException();
				}
			}

			@Override
			public void close() throws IOException {
				channel.close();
			}
		};
	}

	public static byte[] encode(int op, String json) {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(op).putInt(body.length).put(body);
		return b.array();
	}

	public static Frame decode(byte[] header, byte[] body) {
		ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
		return new Frame(b.getInt(), new String(body, StandardCharsets.UTF_8));
	}

	public void send(int op, String json) throws IOException {
		pipe.write(encode(op, json));
	}

	/** Read the next frame, answering pings on the way. */
	public Frame receive() throws IOException {
		while (true) {
			byte[] header = new byte[8];
			pipe.readFully(header);
			int len = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
			if (len < 0 || len > 1 << 20) throw new IOException("Bad frame from Discord");
			byte[] body = new byte[len];
			pipe.readFully(body);
			Frame f = decode(header, body);
			if (f.op() == PING) {
				send(PONG, f.json());
				continue;
			}
			return f;
		}
	}

	@Override
	public void close() throws IOException {
		pipe.close();
	}
}
