package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.modules.hud.SessionStatsHud;
import dev.telmuad.hollowclient.render.EffectsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hit Effects and Session Stats: notice your own attacks and broken blocks. Neither action is changed. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void hollowclient$onAttack(Player player, Entity target, CallbackInfo ci) {
		if (player == Minecraft.getInstance().player) EffectsFx.onLocalAttack(target);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void hollowclient$onBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) SessionStatsHud.addBlock();
	}
}
