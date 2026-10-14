package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toggle sprint / sneak: after vanilla reads the keys, keep sprint/sneak "held" while toggled on. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void hollowclient$toggles(CallbackInfo ci) {
		if (Modules.TOGGLE_SPRINT == null) return;
		ClientInput self = (ClientInput) (Object) this;
		self.keyPresses = Modules.TOGGLE_SPRINT.apply(self.keyPresses, Minecraft.getInstance().player);
	}
}
