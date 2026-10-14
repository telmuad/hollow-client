package dev.telmuad.hollowclient.module;

/**
 * Describes a module's keybind without touching Minecraft classes. The real {@code KeyMapping} is created from this
 * at startup (see {@code Keybinds}), so it shows up in Options → Controls under "Hollow Client".
 *
 * @param defaultKey GLFW key code, or -1 for unbound
 * @param mode       what pressing the key does
 */
public record KeyDef(int defaultKey, Mode mode) {
	public static final int UNBOUND = -1;

	public enum Mode {
		/** Each press switches the module on or off. */
		TOGGLE,
		/** The module reads whether the key is held (zoom). */
		HOLD
	}

	public static KeyDef toggle() {
		return new KeyDef(UNBOUND, Mode.TOGGLE);
	}

	public static KeyDef hold(int defaultKey) {
		return new KeyDef(defaultKey, Mode.HOLD);
	}
}
