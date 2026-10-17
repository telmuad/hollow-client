package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.mc.GameReader;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small SkyBlock UI hooks: the item-dump key inside menus, and a note on joining when other SkyBlock mods overlap. */
public final class SkyBlockUi {
	private static boolean compatNoted;

	private SkyBlockUi() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) return;
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
				if (Keybinds.dumpKey() == null || !Keybinds.dumpKey().matches(event)) return;
				var slot = container.hoveredSlot;
				if (slot != null && slot.hasItem()) dump(client, slot.getItem());
			});
		});
		SkyBlock.bus().on(SbEvent.JoinedSkyBlock.class, e -> noteOverlaps());
	}

	/** Saves an item's SkyBlock data to hollow-debug/ (for fixing parsers). */
	public static void dump(Minecraft mc, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			Toasts.warning("Nothing to save", "Hover over an item in a menu, or hold one.");
			return;
		}
		try {
			Path f = SkyBlock.recorder().writeDump("item", GameReader.raw(-1, stack).toJson());
			Toasts.success("Item saved", "hollow-debug/" + f.getFileName());
		} catch (IOException e) {
			Toasts.error("Couldn't save the item", e.getMessage());
		}
	}

	/** Once per game: if another SkyBlock mod has features Hollow also has switched on, say where to sort it out. */
	private static void noteOverlaps() {
		if (compatNoted) return;
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, String> e : SetupCheck.OTHER_MODS.entrySet()) {
			if (!FabricLoader.getInstance().isModLoaded(e.getKey())) continue;
			List<Module> overlap = CompatibilityScreen.overlapping(e.getKey());
			if (!overlap.isEmpty()) names.add(e.getValue());
		}
		if (names.isEmpty()) return;
		compatNoted = true;
		Toasts.warning(String.join(" and ", names) + " found", "Some Hollow features overlap. H → Compatibility.");
	}
}
