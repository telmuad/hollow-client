package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.qol.ToggleSprintModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/** Small indicator for Toggle Sprint / Sneak: "Sprinting (Toggled)", "Sneaking (Key held)", ... */
public final class ToggleStatusHud extends TextHudModule {
	public ToggleStatusHud() {
		super("toggle_status", "Sprint/Sneak Status", "Shows when you are sprinting or sneaking, and whether it is toggled.", true,
				at(Anchor.CENTER, 0, Anchor.END, 62));
	}

	@Override
	public boolean visibleInGame(Minecraft mc) {
		return mc.player != null && status(mc) != null;
	}

	private static String status(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) return null;
		ToggleSprintModule toggle = Modules.TOGGLE_SPRINT;
		boolean sneakKey = mc.options.keyShift.isDown();
		boolean sprintKey = mc.options.keySprint.isDown();
		if (p.getAbilities().flying) return null;
		if (p.isShiftKeyDown()) {
			return "Sneaking (" + (toggle != null && toggle.sneakToggled() && !sneakKey ? "Toggled" : "Key held") + ")";
		}
		if (p.isSprinting()) {
			if (toggle != null && toggle.alwaysSprint()) return "Sprinting (Always)";
			return "Sprinting (" + (toggle != null && toggle.sprintToggled() && !sprintKey ? "Toggled" : "Key held") + ")";
		}
		if (toggle != null && toggle.sprintToggled() && !toggle.alwaysSprint()) return "Sprint toggled";
		return null;
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		String s = status(mc);
		if (s == null && preview) s = "Sprinting (Toggled)";
		if (s != null) out.add(Line.of("[" + s + "]"));
	}
}
