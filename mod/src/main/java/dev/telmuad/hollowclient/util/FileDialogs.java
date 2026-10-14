package dev.telmuad.hollowclient.util;

import dev.telmuad.hollowclient.HollowClient;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Native Windows open-file / pick-folder dialogs (tinyfd ships with Minecraft). Each dialog runs on its own thread
 * so the game keeps rendering; the result comes back on the game thread, or null if cancelled.
 */
public final class FileDialogs {
	private static volatile boolean open;

	private FileDialogs() {
	}

	public static boolean isOpen() {
		return open;
	}

	/** @param patterns for example {"*.zip"} or {"*.png"} */
	public static void openFile(String title, Path startDir, String[] patterns, String description, Consumer<Path> onResult) {
		run(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(patterns.length);
				for (String p : patterns) filters.put(stack.UTF8(p));
				filters.flip();
				return TinyFileDialogs.tinyfd_openFileDialog(title, withSlash(startDir), filters, description, false);
			}
		}, onResult);
	}

	public static void pickFolder(String title, Path startDir, Consumer<Path> onResult) {
		run(() -> TinyFileDialogs.tinyfd_selectFolderDialog(title, withSlash(startDir)), onResult);
	}

	private interface DialogCall {
		String show();
	}

	private static void run(DialogCall call, Consumer<Path> onResult) {
		if (open) return;
		open = true;
		Thread t = new Thread(() -> {
			String result = null;
			try {
				result = call.show();
			} catch (Throwable e) {
				HollowClient.LOG.warn("File dialog failed", e);
			} finally {
				open = false;
			}
			String chosen = result;
			Minecraft.getInstance().execute(() -> onResult.accept(chosen == null || chosen.isBlank() ? null : Path.of(chosen)));
		}, "Hollow Client file dialog");
		t.setDaemon(true);
		t.start();
	}

	private static String withSlash(Path dir) {
		if (dir == null) return "";
		String s = dir.toAbsolutePath().toString();
		return s.endsWith("\\") || s.endsWith("/") ? s : s + File.separator;
	}
}
