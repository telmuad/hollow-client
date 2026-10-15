package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.styles.Emotes;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: pose your own model after vanilla's animation. Only render states that carry an emote are touched. */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void hollowclient$emote(AvatarRenderState state, CallbackInfo ci) {
		Emotes.Playing playing = state.getData(Emotes.KEY);
		if (playing != null) Emotes.pose((PlayerModel) (Object) this, playing);
	}
}
