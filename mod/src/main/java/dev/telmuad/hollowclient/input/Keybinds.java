package dev.telmuad.hollowclient.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.module.KeyDef;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.modules.client.PerformanceModule;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * All Hollow keybinds. They are registered through Fabric's keybinding API, so they appear (and can be changed)
 * in Options → Controls → Key Binds under "Hollow Client". The settings screen's keybind picker edits the same
 * {@link KeyMapping}s.
 */
public final class Keybinds {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(HollowClient.id("main"));

	private static KeyMapping menuKey;
	private static KeyMapping emoteKey;
	private static KeyMapping hubKey;
	private static KeyMapping dumpKey;
	private static final String[] ACTIONS = {"waypoint_add", "camera_point", "camera_play", "music_play", "music_next", "music_previous"};
	private static final KeyMapping[] ACTION_KEYS = new KeyMapping[ACTIONS.length];
	private static final KeyMapping[] MACRO_KEYS = new KeyMapping[dev.telmuad.hollowclient.modules.qol.ChatMacrosModule.SLOTS];
	private static final Map<Module, KeyMapping> MODULE_KEYS = new LinkedHashMap<>();
	private static Supplier<net.minecraft.client.gui.screens.Screen> menuFactory = () -> null;

	private Keybinds() {
	}

	public static void register(ModuleRegistry registry, Supplier<net.minecraft.client.gui.screens.Screen> menu) {
		menuFactory = menu;
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hollowclient.menu",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
		emoteKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hollowclient.emote_wheel",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
		hubKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hollowclient.skyblock_hub",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
		dumpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hollowclient.skyblock_dump",
				InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		for (int i = 0; i < ACTIONS.length; i++) {
			ACTION_KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hollowclient." + ACTIONS[i],
					InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		}
		for (int i = 0; i < MACRO_KEYS.length; i++) {
			MACRO_KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hollowclient.macro" + (i + 1),
					InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		}
		for (Module m : registry.all()) {
			KeyDef def = m.keyDef();
			int key = def.defaultKey() == KeyDef.UNBOUND ? InputConstants.UNKNOWN.getValue() : def.defaultKey();
			MODULE_KEYS.put(m, KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hollowclient." + m.id(),
					InputConstants.Type.KEYSYM, key, CATEGORY)));
		}
	}

	public static KeyMapping menuKey() {
		return menuKey;
	}

	/** Hold to open the emote wheel (B by default). */
	public static KeyMapping emoteKey() {
		return emoteKey;
	}

	/** Opens the SkyBlock Hub (H by default). */
	public static KeyMapping hubKey() {
		return hubKey;
	}

	/** Saves the hovered (or held) item's SkyBlock data for parser fixes (unbound by default). */
	public static KeyMapping dumpKey() {
		return dumpKey;
	}

	public static KeyMapping of(Module module) {
		return MODULE_KEYS.get(module);
	}

	/** Change a key and save it the same way the Controls screen does. Pass {@code InputConstants.UNKNOWN} to unbind. */
	public static void rebind(KeyMapping mapping, InputConstants.Key key) {
		mapping.setKey(key);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	public static void tick(Minecraft mc) {
		if (menuKey == null) return;
		while (menuKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				var screen = menuFactory.get();
				if (screen != null) mc.gui.setScreen(screen);
			}
		}
		while (emoteKey.consumeClick()) {
			var emotes = dev.telmuad.hollowclient.modules.Modules.EMOTES;
			if (mc.gui.screen() == null && mc.player != null && emotes != null && emotes.isEnabled()) {
				mc.gui.setScreen(new dev.telmuad.hollowclient.gui.EmoteWheelScreen());
			}
		}
		while (hubKey.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new dev.telmuad.hollowclient.skyblock.ui.SkyBlockHubScreen(null));
		}
		while (dumpKey.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null) dev.telmuad.hollowclient.skyblock.ui.SkyBlockUi.dump(mc, mc.player.getMainHandItem());
		}
		for (int i = 0; i < ACTION_KEYS.length; i++) {
			while (ACTION_KEYS[i].consumeClick()) {
				if (mc.gui.screen() == null) action(mc, ACTIONS[i]);
			}
		}
		for (int i = 0; i < MACRO_KEYS.length; i++) {
			while (MACRO_KEYS[i].consumeClick()) {
				if (mc.gui.screen() == null) dev.telmuad.hollowclient.chat.ChatFx.macro(mc, i);
			}
		}
		for (Map.Entry<Module, KeyMapping> e : MODULE_KEYS.entrySet()) {
			Module m = e.getKey();
			if (m.keyDef().mode() != KeyDef.Mode.TOGGLE) {
				// Held keys are read with isDown(); drop their click counts so they don't pile up.
				while (e.getValue().consumeClick()) {
				}
				continue;
			}
			while (e.getValue().consumeClick()) {
				if (m instanceof ScreenProvider provider) {
					// Theme: the key opens its editor
					if (mc.gui.screen() == null) mc.gui.setScreen(provider.createScreen(null));
					continue;
				}
				String label = m.name();
				boolean on;
				if (m instanceof PerformanceModule performance) {
					performance.performanceModeSetting().toggle();
					label = "Performance Mode";
					on = performance.performanceMode();
				} else {
					m.toggle();
					on = m.isEnabled();
				}
				if (mc.player != null) {
					dev.telmuad.hollowclient.skyblock.SkyBlock.showOverlay(mc, Component.literal(label + ": ")
							.append(on ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
									: Component.literal("OFF").withStyle(ChatFormatting.RED)));
				}
			}
		}
	}

	private static void action(Minecraft mc, String action) {
		switch (action) {
			case "waypoint_add" -> {
				var w = dev.telmuad.hollowclient.waypoints.Waypoints.addHere(mc, null);
				if (w != null) dev.telmuad.hollowclient.gui.Toasts.success("Waypoint added", w.name + " (rename it in Waypoints)");
			}
			case "camera_point" -> dev.telmuad.hollowclient.camera.CameraPaths.addPoint(mc);
			case "camera_play" -> dev.telmuad.hollowclient.camera.CameraPaths.toggle(mc);
			case "music_play" -> dev.telmuad.hollowclient.music.MusicPlayer.togglePause();
			case "music_next" -> dev.telmuad.hollowclient.music.MusicPlayer.next();
			case "music_previous" -> dev.telmuad.hollowclient.music.MusicPlayer.previous();
			default -> {
			}
		}
	}
}
