package dev.telmuad.hollowclient;

import dev.telmuad.hollowclient.config.ConfigManager;
import dev.telmuad.hollowclient.config.StylesConfig;
import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.render.Quality;
import dev.telmuad.hollowclient.theme.Themes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class HollowClient implements ClientModInitializer {
	public static final String MOD_ID = "hollowclient";
	public static final Logger LOG = LoggerFactory.getLogger("Hollow Client");

	private static ModuleRegistry modules;
	private static StylesConfig styles;
	private static ConfigManager config;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static ModuleRegistry modules() {
		return modules;
	}

	public static StylesConfig styles() {
		return styles;
	}

	public static ConfigManager config() {
		return config;
	}

	/** {@code .minecraft/config/hollowclient/}: imported capes and other files Hollow keeps. */
	public static Path dataDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
	}

	@Override
	public void onInitializeClient() {
		// Shared with the launcher, which reads and writes the same file
		Themes.init(dataDir().resolve("theme.json"));
		modules = new ModuleRegistry();
		Modules.registerAll(modules);

		styles = new StylesConfig();
		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"), modules, styles);
		config.setWarningSink(LOG::warn);
		config.load();
		modules.setChangeListener(m -> config.markDirty());
		for (Module m : modules.all()) m.onLoaded();

		Keybinds.register(modules, Modules::createMenu);

		ClientTickEvents.END_CLIENT_TICK.register(HollowClient::onEndTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.saveNow());
		Modules.registerHooks();

		LOG.info("Hollow Client ready: {} modules", modules.all().size());
	}

	private static void onEndTick(Minecraft mc) {
		Keybinds.tick(mc);
		Themes.tick();
		Quality.tick();
		for (Module m : modules.all()) {
			try {
				m.onTick();
			} catch (RuntimeException e) {
				LOG.error("Module {} failed to tick", m.id(), e);
			}
		}
	}
}
