package dev.telmuad.hollowclient.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.module.KeyDef;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.module.ModuleRegistry;
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
	private static final Map<Module, KeyMapping> MODULE_KEYS = new LinkedHashMap<>();
	private static Supplier<net.minecraft.client.gui.screens.Screen> menuFactory = () -> null;

	private Keybinds() {
	}

	public static void register(ModuleRegistry registry, Supplier<net.minecraft.client.gui.screens.Screen> menu) {
		menuFactory = menu;
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hollowclient.menu",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
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
		for (Map.Entry<Module, KeyMapping> e : MODULE_KEYS.entrySet()) {
			Module m = e.getKey();
			if (m.keyDef().mode() != KeyDef.Mode.TOGGLE) continue;
			while (e.getValue().consumeClick()) {
				m.toggle();
				if (mc.player != null) {
					mc.gui.setOverlayMessage(Component.literal(m.name() + ": ")
							.append(m.isEnabled() ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
									: Component.literal("OFF").withStyle(ChatFormatting.RED)), false);
				}
			}
		}
	}
}
