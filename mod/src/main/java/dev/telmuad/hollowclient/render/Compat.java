package dev.telmuad.hollowclient.render;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.lang.reflect.Method;

/** Other mods Hollow adapts to. Iris is checked through its public API by reflection, so it is not a dependency. */
public final class Compat {
	private static Boolean irisLoaded;
	private static Object irisApi;
	private static Method shaderPackInUse;
	private static boolean lastActive;
	private static long checkedAt;

	private Compat() {
	}

	public static boolean sodiumLoaded() {
		return FabricLoader.getInstance().isModLoaded("sodium");
	}

	public static boolean irisLoaded() {
		if (irisLoaded == null) irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
		return irisLoaded;
	}

	/** True while Iris has a shader pack switched on (checked at most twice a second). */
	public static boolean irisShadersActive() {
		if (!irisLoaded()) return false;
		long now = Util.getMillis();
		if (now - checkedAt < 500) return lastActive;
		checkedAt = now;
		try {
			if (irisApi == null) {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				irisApi = api.getMethod("getInstance").invoke(null);
				shaderPackInUse = api.getMethod("isShaderPackInUse");
			}
			lastActive = (Boolean) shaderPackInUse.invoke(irisApi);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Iris changed its API: assume shaders are on, which is the safe choice.
			lastActive = true;
		}
		return lastActive;
	}
}
