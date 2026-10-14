package dev.telmuad.hollowclient.modules.qol;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

/**
 * Press sprint once to keep sprinting, press again to stop (same for sneak if turned on). This only changes how
 * your own keys are read, exactly like the vanilla "Sprint: Toggle" option; nothing extra is sent to servers.
 */
public final class ToggleSprintModule extends Module {
	private final BooleanSetting sprint = add(new BooleanSetting("sprint", "Toggle sprint", true));
	private final BooleanSetting sneak = add(new BooleanSetting("sneak", "Toggle sneak", false));
	private final BooleanSetting alwaysSprint = add(new BooleanSetting("alwaysSprint", "Always sprint", false));

	private boolean sprintToggled;
	private boolean sneakToggled;

	public ToggleSprintModule() {
		super("toggle_sprint", "Toggle Sprint / Sneak", "Press sprint (or sneak) once to keep it on.", Category.QOL, false);
		alwaysSprint.describe("Sprint whenever you walk forward, without pressing anything.");
		sneak.describe("Not applied while flying or riding, so you don't sink or dismount.");
	}

	public boolean sprintToggled() {
		return isEnabled() && sprint.on() && (sprintToggled || alwaysSprint.on());
	}

	public boolean sneakToggled() {
		return isEnabled() && sneak.on() && sneakToggled;
	}

	public boolean alwaysSprint() {
		return isEnabled() && sprint.on() && alwaysSprint.on();
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			sprintToggled = false;
			sneakToggled = false;
			return;
		}
		// Count presses of the vanilla sprint/sneak keys (vanilla itself never consumes these clicks).
		while (mc.options.keySprint.consumeClick()) {
			if (isEnabled() && sprint.on() && !alwaysSprint.on()) sprintToggled = !sprintToggled;
		}
		while (mc.options.keyShift.consumeClick()) {
			if (isEnabled() && sneak.on()) sneakToggled = !sneakToggled;
		}
		if (mc.player.isDeadOrDying()) {
			sprintToggled = false;
			sneakToggled = false;
		}
	}

	@Override
	protected void onDisable() {
		sprintToggled = false;
		sneakToggled = false;
	}

	/** Called by the input mixin after vanilla read the keys. */
	public Input apply(Input in, LocalPlayer player) {
		if (!isEnabled()) return in;
		boolean wantSprint = in.sprint() || sprintToggled();
		boolean wantSneak = in.shift();
		if (sneakToggled() && player != null && !player.getAbilities().flying && !player.isPassenger()) wantSneak = true;
		if (wantSprint == in.sprint() && wantSneak == in.shift()) return in;
		return new Input(in.forward(), in.backward(), in.left(), in.right(), in.jump(), wantSneak, wantSprint);
	}
}
