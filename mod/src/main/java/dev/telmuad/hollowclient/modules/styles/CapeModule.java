package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.StringSetting;
import dev.telmuad.hollowclient.styles.CapeManager;
import net.minecraft.resources.Identifier;

/** Shows a cape on your own player (only on your screen). Pick capes in Styles → Capes. */
public final class CapeModule extends Module {
	private final StringSetting cape = add(new StringSetting("cape", "Cape", CapeManager.DEFAULT, 160));
	private final BooleanSetting elytra = add(new BooleanSetting("elytra", "Use the cape on elytra too", true));

	public CapeModule() {
		super("cape", "Cape", "Wear a built-in or imported cape. Only you can see it.", Category.STYLES, false);
	}

	public String capeId() {
		return cape.get();
	}

	public void select(String id) {
		cape.set(id);
		setEnabled(true);
	}

	public boolean onElytra() {
		return elytra.on();
	}

	public BooleanSetting elytraSetting() {
		return elytra;
	}

	/** The cape texture to show, or null when off or the cape can't be loaded. */
	public Identifier texture() {
		if (!isEnabled()) return null;
		return CapeManager.texture(cape.get());
	}
}
