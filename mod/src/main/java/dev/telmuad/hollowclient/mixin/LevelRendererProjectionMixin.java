package dev.telmuad.hollowclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.telmuad.hollowclient.render.WorldProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Waypoints and camera paths: remember this frame's camera matrices for HUD labels. Nothing is changed. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererProjectionMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void hollowclient$capture(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean outline, CameraRenderState camera,
									  Matrix4fc modelView, GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
		WorldProjection.capture(camera.pos, modelView, camera.projectionMatrix);
	}
}
