package dev.telmuad.hollowclient.modules.effects;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.Effect;

/** Dropped items lie flat on the ground instead of floating and spinning, and tumble while they fall. Looks only. */
public final class ItemPhysicsModule extends Module {
	private final Effect tumble = effect("tumble", "Tumble while falling", true, 60, "Items turn over in the air. Strength = how fast.");

	public ItemPhysicsModule() {
		super("item_physics", "Item Physics", "Dropped items lie flat on the ground and tumble while they fall, instead of floating and spinning.", Category.EFFECTS, false);
	}

	public float tumble() {
		return tumble.amount();
	}
}
