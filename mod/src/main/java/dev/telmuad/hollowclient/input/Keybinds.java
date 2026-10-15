package dev.telmuad.hollowclient.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.module.KeyDef;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.modules.client.PerformanceModule;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
	private static final Map<Module, KeyMapping> MODULE_KEYS = new LinkedHashMap<>();
	private static Supplier<net.minecraft.client.gui.screens.Screen> menuFactory = () -> null;

	private Keybinds() {
	}

	public static void register(ModuleRegistry registry, Supplier<net.minecraft.client.gui.screens.Screen> menu) {
		menuFactory = menu;
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hollowclient.menu",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
		emoteKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hollowclient.emote_wheel",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
		for (Module m : registry.all()) {
			KeyDef def = m.keyDef();
			int key = def.defaultKey() == KeyDef.UNBOUND ? InputConstants.UNKNOWN.getValue() : def.defaultKey();
			MODULE_KEYS.put(m, KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hollowclient." + m.id(),
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
			if (mc.screen == null) {
				var screen = menuFactory.get();
				if (screen != null) mc.setScreen(screen);
			}
		}
		while (emoteKey.consumeClick()) {
			var emotes = dev.telmuad.hollowclient.modules.Modules.EMOTES;
			if (mc.screen == null && mc.player != null && emotes != null && emotes.isEnabled()) {
				mc.setScreen(new dev.telmuad.hollowclient.gui.EmoteWheelScreen());
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
					if (mc.screen == null) mc.setScreen(provider.createScreen(null));
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
					mc.gui.setOverlayMessage(Component.literal(label + ": ")
							.append(on ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
									: Component.literal("OFF").withStyle(ChatFormatting.RED)), false);
				}
			}
		}
	}
}
