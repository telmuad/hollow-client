package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.StringSetting;
import dev.telmuad.hollowclient.styles.CapeManager;
import net.minecraft.resources.Identifier;

/** Shows a cape on your own player (only on your screen). Pick capes in Styles → Capes. */
public final class CapeModule extends Module {
	private final StringSetting cape = add(new StringSetting("cape", "Cape", CapeManager.DEFAULT, 160));
	private final BooleanSetting elytra = add(new BooleanSetting("elytra", "Use the cape on elytra too", true));
	private final Effect glow = effect("glow", "Cape glow", false, 60, "The cape stays bright in the dark.");
	private final Effect shimmer = effect("shimmer", "Theme shimmer", false, 50, "A moving sheen in the theme colors runs over the cape.");

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

	public Effect glowEffect() {
		return glow;
	}

	public Effect shimmerEffect() {
		return shimmer;
	}

	public float glow() {
		return isEnabled() ? glow.amount() : 0f;
	}

	public float shimmer() {
		return isEnabled() ? shimmer.amount() : 0f;
	}

	/** The cape texture to show, or null when off or the cape can't be loaded. */
	public Identifier texture() {
		if (!isEnabled()) return null;
		return CapeManager.texture(cape.get());
	}
}
