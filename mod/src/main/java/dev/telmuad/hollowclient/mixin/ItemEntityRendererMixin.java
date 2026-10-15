package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.effects.ItemPhysicsModule;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics: on the ground, flat items (sprites) lie down and blocks sit still, both turned to a fixed random
 * angle; in the air, items tumble. Replaces vanilla's float-and-spin. Rendering only.
 */
@Mixin(ItemEntityRenderer.class)
abstract class ItemEntityRendererMixin {
	@Unique
	private static final RenderStateDataKey<Boolean> ON_GROUND = RenderStateDataKey.create(() -> "hollowclient:item_on_ground");

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
	private void hollowclient$ground(ItemEntity item, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
		state.setData(ON_GROUND, item.onGround());
	}

	@Unique
	private static ItemPhysicsModule hollowclient$module() {
		ItemPhysicsModule m = Modules.ITEM_PHYSICS;
		return m != null && m.isEnabled() ? m : null;
	}

	/** Vanilla's bob translate: replaced by our resting/tumbling pose. */
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", ordinal = 0, target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void hollowclient$pose(PoseStack pose, float x, float y, float z, Operation<Void> original,
								   @Local(argsOnly = true) ItemEntityRenderState state) {
		ItemPhysicsModule m = hollowclient$module();
		if (m == null) {
			original.call(pose, x, y, z);
			return;
		}
		AABB box = state.item.getModelBoundingBox();
		boolean flat = box.getZsize() <= 0.0625f;
		float lift = (float) -box.minY + 0.0625f;
		boolean ground = Boolean.TRUE.equals(state.getData(ON_GROUND));
		float yaw = state.bobOffset;
		if (ground) {
			if (flat) {
				// lie down on the ground, a fixed angle per item
				original.call(pose, 0f, 0.04f, 0f);
				pose.mulPose(Axis.YP.rotation(yaw));
				pose.mulPose(Axis.XP.rotationDegrees(90f));
			} else {
				original.call(pose, 0f, lift - 0.06f, 0f);
				pose.mulPose(Axis.YP.rotation(yaw));
			}
		} else {
			original.call(pose, 0f, lift, 0f);
			float spin = state.ageInTicks * (0.15f + 0.35f * m.tumble());
			pose.mulPose(Axis.YP.rotation(yaw + spin * 0.6f));
			pose.mulPose(Axis.XP.rotation(spin));
		}
	}

	/** Vanilla's spin: skipped with Item Physics. */
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", ordinal = 0, target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void hollowclient$spin(PoseStack pose, Quaternionfc rotation, Operation<Void> original) {
		if (hollowclient$module() == null) original.call(pose, rotation);
	}
}
