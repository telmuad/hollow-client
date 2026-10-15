package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.render.EffectsFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particles: extra pieces and sparkles right after vanilla's block-break particles. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("TAIL"))
	private void hollowclient$blockBroken(BlockPos pos, BlockState state, CallbackInfo ci) {
		EffectsFx.onBlockBroken((ClientLevel) (Object) this, pos, state);
	}
}
