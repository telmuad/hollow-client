package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.render.EffectsFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit Effects: react to the critical-hit animations the server already sends (more particles, sparks, flash). */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Shadow
	private ClientLevel level;

	@Inject(method = "handleAnimate", at = @At("TAIL"))
	private void hollowclient$crit(ClientboundAnimatePacket packet, CallbackInfo ci) {
		int action = packet.getAction();
		if (action != 4 && action != 5 || level == null) return;
		Entity e = level.getEntity(packet.getId());
		if (e != null) EffectsFx.onCritPacket(e, action);
	}
}
