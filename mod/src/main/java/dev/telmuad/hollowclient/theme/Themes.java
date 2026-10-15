package dev.telmuad.hollowclient.theme;

import java.nio.file.Path;

/** The game's one theme store. The launcher shares the same file, so changes there show up here within a second. */
public final class Themes {
	private static ThemeStore store;
	private static int ticks;

	private Themes() {
	}

	public static void init(Path file) {
		store = new ThemeStore(file);
		store.load();
	}

	public static ThemeStore store() {
		return store;
	}

	public static HollowTheme current() {
		return store == null ? HollowTheme.VOID : store.current();
	}

	/** Called every client tick; checks the shared file once a second. */
	public static void tick() {
		if (store != null && ++ticks % 20 == 0) store.reloadIfChanged();
	}
}
