package dev.telmuad.hollowclient.skyblock.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The optional Hypixel API key. Off unless you add one; nothing in SkyBlock Mode needs it today (every price and
 * event source is keyless), it's only for future extras that say so. On Windows it's encrypted with DPAPI (only your
 * Windows account can read it back) using the JNA library Minecraft already ships; elsewhere it falls back to a plain
 * file and says so. The key is never logged or shown in full.
 */
public final class ApiKeyStore {
	/** Encrypts/decrypts bytes for the current user (DPAPI on Windows). */
	public interface Protector {
		byte[] protect(byte[] data) throws IOException;

		byte[] unprotect(byte[] data) throws IOException;

		boolean secure();
	}

	private final Path file;
	private final Protector protector;

	public ApiKeyStore(Path file, Protector protector) {
		this.file = file;
		this.protector = protector;
	}

	/** DPAPI on Windows when JNA is there, else a plain file (not secure). */
	public static Protector systemProtector() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		if (windows) {
			try {
				Class.forName("com.sun.jna.platform.win32.Crypt32Util");
				return new Protector() {
					@Override
					public byte[] protect(byte[] data) throws IOException {
						try {
							return com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(data);
						} catch (RuntimeException | LinkageError e) {
							throw new IOException("Windows couldn't encrypt the key");
						}
					}

					@Override
					public byte[] unprotect(byte[] data) throws IOException {
						try {
							return com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(data);
						} catch (RuntimeException | LinkageError e) {
							throw new IOException("Windows couldn't decrypt the key");
						}
					}

					@Override
					public boolean secure() {
						return true;
					}
				};
			} catch (ClassNotFoundException | LinkageError ignored) {
				// fall through to the plain file
			}
		}
		return new Protector() {
			@Override
			public byte[] protect(byte[] data) {
				return data.clone();
			}

			@Override
			public byte[] unprotect(byte[] data) {
				return data.clone();
			}

			@Override
			public boolean secure() {
				return false;
			}
		};
	}

	public boolean secure() {
		return protector.secure();
	}

	/** A Hypixel API key looks like a UUID. */
	public static boolean looksValid(String key) {
		return key != null && key.strip().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	}

	public boolean present() {
		return Files.isRegularFile(file);
	}

	public void save(String key) throws IOException {
		if (!looksValid(key)) throw new IOException("That doesn't look like a Hypixel API key.");
		Files.createDirectories(file.getParent());
		Files.write(file, protector.protect(key.strip().getBytes(StandardCharsets.UTF_8)));
	}

	/** The key, or null if there's none or it can't be read (another Windows account, a damaged file). */
	public String read() {
		try {
			if (!present()) return null;
			String key = new String(protector.unprotect(Files.readAllBytes(file)), StandardCharsets.UTF_8);
			return looksValid(key) ? key : null;
		} catch (IOException e) {
			return null;
		}
	}

	public void clear() throws IOException {
		Files.deleteIfExists(file);
	}

	/** "••••-1a2b" (only the last 4 characters). */
	public static String masked(String key) {
		if (key == null || key.length() < 4) return "";
		return "••••••••-" + key.substring(key.length() - 4);
	}
}
