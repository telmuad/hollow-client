package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.render.EffectsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit Effects: notice your own attacks (to spawn a hit burst). The attack itself is not changed. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void hollowclient$onAttack(Player player, Entity target, CallbackInfo ci) {
		if (player == Minecraft.getInstance().player) EffectsFx.onLocalAttack(target);
	}
}
