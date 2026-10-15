package dev.telmuad.hollowclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screenshot manager: a grid of your screenshots (newest first) with thumbnails loaded in the background, and a
 * viewer with previous/next, open, show in folder, copy to clipboard and delete (moved to screenshots/deleted, so
 * nothing is lost by a misclick).
 */
public final class ScreenshotsScreen extends HollowScreen {
	private static final int THUMB_W = 192;
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
	private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Hollow screenshot thumbnails");
		t.setDaemon(true);
		return t;
	});

	/** A loaded picture: texture id and its size. */
	private record Pic(Identifier id, int w, int h) {
	}

	private static final Map<Path, Pic> THUMBS = new ConcurrentHashMap<>();
	private static final Map<Path, Boolean> LOADING = new ConcurrentHashMap<>();
	private static final Map<Path, NativeImage> READY = new ConcurrentHashMap<>();
	private static int counter;

	private final List<Path> files = new ArrayList<>();
	private final Map<Path, Float> hover = new HashMap<>();
	private final UiButton back = new UiButton("< Back", this::backPressed);
	private final UiButton folder = new UiButton("Open folder", this::openFolder);
	private final UiButton prev = new UiButton("<", () -> step(-1));
	private final UiButton next = new UiButton(">", () -> step(1));
	private final UiButton open = new UiButton("Open", () -> Util.getPlatform().openPath(current()));
	private final UiButton copy = new UiButton("Copy image", this::copyImage);
	private final UiButton delete = new UiButton("Delete", this::delete).style(UiButton.Style.DANGER);
	private Rect panel = Rect.EMPTY;
	private Rect grid = Rect.EMPTY;
	private int viewing = -1;
	private Pic full;
	private Path fullFor;
	private boolean confirmDelete;
	private float scroll;
	private float scrollTarget;
	private String status;
	private long statusAt;

	public ScreenshotsScreen(Screen parent) {
		super(Component.literal("Screenshots"), parent);
	}

	public static Path folderPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
	}

	private void scan() {
		files.clear();
		Path dir = folderPath();
		if (!Files.isDirectory(dir)) return;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
			for (Path p : stream) files.add(p);
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't list screenshots", e);
		}
		files.sort(Comparator.comparingLong(ScreenshotsScreen::modified).reversed());
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	@Override
	protected void init() {
		super.init();
		scan();
		int pw = Math.min(width - 24, 640);
		int ph = Math.min(height - 24, 380);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		folder.bounds(new Rect(panel.right() - 8 - folder.preferredWidth(font), panel.y() + 8, folder.preferredWidth(font), 16));
		grid = new Rect(panel.x() + 8, panel.y() + 32, pw - 16, ph - 32 - 20);
		if (viewing >= files.size()) viewing = files.isEmpty() ? -1 : files.size() - 1;
	}

	private Path current() {
		return viewing >= 0 && viewing < files.size() ? files.get(viewing) : null;
	}

	// ------------------------------------------------------------------------------------------------ loading

	/** Start loading a scaled copy in the background; textures are created on the render thread later. */
	private static void request(Path file, int maxW, Map<Path, Pic> into) {
		if (into.containsKey(file) || LOADING.putIfAbsent(file, true) != null) return;
		LOADER.execute(() -> {
			try (InputStream in = Files.newInputStream(file); NativeImage src = NativeImage.read(in)) {
				READY.put(file, scaled(src, maxW));
			} catch (IOException | RuntimeException e) {
				HollowClient.LOG.warn("Couldn't read screenshot {}", file.getFileName());
			} finally {
				LOADING.remove(file);
			}
		});
	}

	private static NativeImage scaled(NativeImage src, int maxW) {
		int w = Math.min(maxW, src.getWidth());
		int h = Math.max(1, Math.round(src.getHeight() * (w / (float) src.getWidth())));
		NativeImage out = new NativeImage(w, h, false);
		float sx = src.getWidth() / (float) w;
		float sy = src.getHeight() / (float) h;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				// average a 2x2 block of samples inside the source area (cheap and smooth enough)
				int r = 0;
				int g = 0;
				int b = 0;
				for (int k = 0; k < 4; k++) {
					int px = Math.min(src.getWidth() - 1, (int) ((x + 0.25f + 0.5f * (k & 1)) * sx));
					int py = Math.min(src.getHeight() - 1, (int) ((y + 0.25f + 0.5f * (k >> 1)) * sy));
					int c = src.getPixel(px, py);
					r += (c >> 16) & 255;
					g += (c >> 8) & 255;
					b += c & 255;
				}
				out.setPixel(x, y, 0xFF000000 | (r / 4) << 16 | (g / 4) << 8 | (b / 4));
			}
		}
		return out;
	}

	private static Pic upload(Path file) {
		NativeImage img = READY.remove(file);
		if (img == null) return null;
		Identifier id = HollowClient.id("screenshots/" + (counter++));
		Minecraft.getInstance().getTextureManager().register(id, new JarTexture.Smooth(id::toString, img));
		return new Pic(id, img.getWidth(), img.getHeight());
	}

	private Pic thumb(Path file) {
		Pic p = THUMBS.get(file);
		if (p != null) return p;
		if (READY.containsKey(file)) {
			p = upload(file);
			if (p != null) THUMBS.put(file, p);
			return p;
		}
		request(file, THUMB_W, THUMBS);
		return null;
	}

	private Pic fullPic(Path file) {
		if (file.equals(fullFor) && full != null) return full;
		if (!file.equals(fullFor)) {
			releaseFull();
			fullFor = file;
		}
		Path key = file.resolveSibling(file.getFileName() + "#full");
		if (READY.containsKey(key)) {
			NativeImage img = READY.remove(key);
			Identifier id = HollowClient.id("screenshots/full" + (counter++));
			Minecraft.getInstance().getTextureManager().register(id, new JarTexture.Smooth(id::toString, img));
			full = new Pic(id, img.getWidth(), img.getHeight());
			return full;
		}
		if (LOADING.putIfAbsent(key, true) == null) {
			LOADER.execute(() -> {
				try (InputStream in = Files.newInputStream(file); NativeImage src = NativeImage.read(in)) {
					READY.put(key, scaled(src, 1600));
				} catch (IOException | RuntimeException e) {
					HollowClient.LOG.warn("Couldn't read screenshot {}", file.getFileName());
				} finally {
					LOADING.remove(key);
				}
			});
		}
		return null;
	}

	private void releaseFull() {
		if (full != null) Minecraft.getInstance().getTextureManager().release(full.id());
		full = null;
	}

	// ------------------------------------------------------------------------------------------------ render

	@Override
	protected void renderContent(GuiGraphics g, int mx, int my, float a) {
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		folder.render(g, font, mx, my, dt, a);
		String title = viewing >= 0 ? (viewing + 1) + " of " + files.size() : "Screenshots";
		Ui.draw(g, font, Ui.bold(title), back.bounds().right() + 8, panel.y() + 12, Theme.fade(Theme.TEXT, a));
		if (viewing >= 0 && current() != null) renderViewer(g, mx, my, a);
		else renderGrid(g, mx, my, a);
		long age = Util.getMillis() - statusAt;
		String foot = status != null && age < 5000 ? status
				: (viewing >= 0 ? "Left/right arrows: previous/next · Esc: back to the grid" : files.size() + " screenshots · F2 takes one");
		Ui.draw(g, font, Ui.fit(font, foot, panel.w() - 20), panel.x() + 10, panel.bottom() - 14, Theme.fade(Theme.TEXT_FAINT, a));
	}

	private int cols() {
		return Math.max(1, (grid.w() + 6) / (120 + 6));
	}

	private Rect tileRect(int i) {
		int cols = cols();
		int tw = (grid.w() - 6 - (cols - 1) * 6) / cols;
		int th = Math.round(tw * 9 / 16f) + 14;
		return new Rect(grid.x() + (i % cols) * (tw + 6), grid.y() + (i / cols) * (th + 6) - Math.round(scroll), tw, th);
	}

	private float maxScroll() {
		if (files.isEmpty()) return 0;
		Rect last = tileRect(files.size() - 1);
		return Math.max(0, last.bottom() + Math.round(scroll) - grid.bottom());
	}

	private void renderGrid(GuiGraphics g, int mx, int my, float a) {
		if (files.isEmpty()) {
			Ui.center(g, font, "No screenshots yet. Press F2 in game to take one.", grid.centerX(), grid.y() + 40, Theme.fade(Theme.TEXT_FAINT, a));
			return;
		}
		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		g.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
		for (int i = 0; i < files.size(); i++) {
			Rect r = tileRect(i);
			if (r.bottom() < grid.y() || r.y() > grid.bottom()) continue;
			Path f = files.get(i);
			boolean hov = r.contains(mx, my) && grid.contains(mx, my);
			float h = Anim.approach(hover.getOrDefault(f, 0f), hov ? 1f : 0f, dt, 16f);
			hover.put(f, h);
			hoverGlow(g, r, h, a);
			Draw.roundRect(g, r, Theme.radius(4), Theme.fade(Theme.mix(Theme.CARD, Theme.CARD_HOVER, h), a));
			Rect img = new Rect(r.x() + 2, r.y() + 2, r.w() - 4, r.h() - 16);
			Pic p = thumb(f);
			if (p != null) blitFit(g, p, img, a);
			else Ui.center(g, font, "...", img.centerX(), img.centerY() - 4, Theme.fade(Theme.TEXT_FAINT, a));
			String when = WHEN.format(Instant.ofEpochMilli(modified(f)).atZone(ZoneId.systemDefault()));
			Ui.draw(g, font, Ui.fit(font, when, r.w() - 6), r.x() + 3, r.bottom() - 11, Theme.fade(h > 0.5f ? Theme.TEXT : Theme.TEXT_DIM, a));
			Draw.roundOutline(g, r, Theme.radius(4), Theme.fade(Theme.mix(Theme.STROKE, Theme.accent(), h), a));
		}
		g.disableScissor();
	}

	/** Draw a picture as large as fits inside {@code box}, keeping its shape. */
	private static void blitFit(GuiGraphics g, Pic p, Rect box, float a) {
		float s = Math.min(box.w() / (float) p.w(), box.h() / (float) p.h());
		int w = Math.max(1, Math.round(p.w() * s));
		int h = Math.max(1, Math.round(p.h() * s));
		int x = box.x() + (box.w() - w) / 2;
		int y = box.y() + (box.h() - h) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, 0f, 0f, w, h, p.w(), p.h(), p.w(), p.h(), Theme.fade(0xFFFFFFFF, a));
	}

	private void renderViewer(GuiGraphics g, int mx, int my, float a) {
		Path f = current();
		Rect area = new Rect(grid.x(), grid.y(), grid.w(), grid.h() - 22);
		Draw.roundRect(g, area, Theme.radius(4), Theme.fade(0x60000000, a));
		Pic p = fullPic(f);
		if (p == null) p = THUMBS.get(f);
		if (p != null) blitFit(g, p, area.inset(4), a);
		else Ui.center(g, font, "Loading...", area.centerX(), area.centerY() - 4, Theme.fade(Theme.TEXT_FAINT, a));
		int by = area.bottom() + 4;
		int x = area.x();
		for (UiButton b : new UiButton[]{prev, next}) {
			b.bounds(new Rect(x, by, 22, 16)).render(g, font, mx, my, dt, a);
			x += 26;
		}
		String name = f.getFileName().toString();
		Ui.draw(g, font, Ui.fit(font, name, area.w() / 3), x + 4, by + 4, Theme.fade(Theme.TEXT_DIM, a));
		int rx = area.right();
		delete.setLabel(confirmDelete ? "Sure? Delete" : "Delete");
		for (UiButton b : new UiButton[]{delete, copy, open}) {
			int w = b.preferredWidth(font);
			rx -= w;
			b.bounds(new Rect(rx, by, w, 16)).render(g, font, mx, my, dt, a);
			rx -= 4;
		}
	}

	// ------------------------------------------------------------------------------------------------ actions

	private void say(String s) {
		status = s;
		statusAt = Util.getMillis();
	}

	private void backPressed() {
		if (viewing >= 0) {
			viewing = -1;
			releaseFull();
			fullFor = null;
		} else {
			onClose();
		}
	}

	private void step(int d) {
		if (files.isEmpty()) return;
		viewing = Math.floorMod(viewing + d, files.size());
		confirmDelete = false;
	}

	private void openFolder() {
		try {
			Files.createDirectories(folderPath());
		} catch (IOException ignored) {
			// open whatever is there
		}
		Util.getPlatform().openPath(folderPath());
	}

	/** Copy the image itself (Windows) or, elsewhere, its path. */
	private void copyImage() {
		Path f = current();
		if (f == null) return;
		if (Util.getPlatform() == Util.OS.WINDOWS) {
			String path = f.toAbsolutePath().toString().replace("'", "''");
			String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
					+ "$i=[System.Drawing.Image]::FromFile('" + path + "'); [System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()";
			try {
				new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", script).redirectErrorStream(true).start();
				say("Copied the image to the clipboard.");
				Toasts.success("Screenshot copied", "Paste it anywhere with Ctrl+V.");
			} catch (IOException e) {
				say("Couldn't copy the image: " + e.getMessage());
			}
		} else if (minecraft != null) {
			minecraft.keyboardHandler.setClipboard(f.toAbsolutePath().toString());
			say("Copied the file path to the clipboard.");
		}
	}

	/** Move the screenshot to screenshots/deleted (two clicks). */
	private void delete() {
		Path f = current();
		if (f == null) return;
		if (!confirmDelete) {
			confirmDelete = true;
			say("Click again to delete. It goes to screenshots/deleted, so you can get it back.");
			return;
		}
		confirmDelete = false;
		try {
			Path bin = folderPath().resolve("deleted");
			Files.createDirectories(bin);
			Files.move(f, bin.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			Pic t = THUMBS.remove(f);
			if (t != null) Minecraft.getInstance().getTextureManager().release(t.id());
			releaseFull();
			fullFor = null;
			files.remove(viewing);
			if (files.isEmpty()) viewing = -1;
			else viewing = Math.min(viewing, files.size() - 1);
			say("Moved to screenshots/deleted.");
		} catch (IOException e) {
			say("Couldn't delete: " + e.getMessage());
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		double mx = event.x();
		double my = event.y();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
		if (back.click(mx, my) || folder.click(mx, my)) return true;
		if (viewing >= 0) {
			for (UiButton b : new UiButton[]{prev, next, open, copy, delete}) if (b.click(mx, my)) return true;
			return true;
		}
		if (grid.contains(mx, my)) {
			for (int i = 0; i < files.size(); i++) {
				Rect r = tileRect(i);
				if (r.contains(mx, my)) {
					UiSounds.click();
					ripple(mx, my, r);
					viewing = i;
					confirmDelete = false;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (viewing >= 0) {
			step(sy > 0 ? -1 : 1);
			return true;
		}
		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - sy * 30));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (viewing >= 0) {
			if (event.isEscape()) {
				backPressed();
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_LEFT) {
				step(-1);
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_RIGHT) {
				step(1);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		releaseFull();
		super.removed();
	}

	/** For tests: open the viewer on the first screenshot. */
	public void viewFirst() {
		if (!files.isEmpty()) viewing = 0;
	}
}
