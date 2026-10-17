package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.skyblock.features.items.ItemProtectionModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item protection: the drop key does nothing while you hold an item you locked. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerDropMixin {
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void hollowclient$protect(boolean all, CallbackInfoReturnable<Boolean> cir) {
		if (ItemProtectionModule.blockDrop()) cir.setReturnValue(false);
	}
}
