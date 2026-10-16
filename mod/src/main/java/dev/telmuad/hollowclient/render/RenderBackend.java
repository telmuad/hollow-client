package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Which graphics backend the game is running on. Minecraft 26.2 can draw with OpenGL (the default) or, if chosen in
 * Video Settings → Graphics API, Vulkan. Hollow draws everything through the Blaze3D API, so the same code runs on
 * both; effects that fail anyway turn themselves off (see {@link PostFx#healthy()}).
 */
public final class RenderBackend {
	private RenderBackend() {
	}

	/** "OpenGL", "Vulkan", or "unknown" before the window exists. */
	public static String name() {
		try {
			return RenderSystem.getDevice().getDeviceInfo().backendName();
		} catch (RuntimeException e) {
			return "unknown";
		}
	}

	public static boolean isVulkan() {
		return "Vulkan".equalsIgnoreCase(name());
	}
}
