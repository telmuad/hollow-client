package dev.telmuad.hollowclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A slowly turning model of your own player, wearing whatever cape and cosmetics are on right now. */
public final class PlayerPreview {
	public enum View {
		/** Mostly the back, swinging left and right (capes, wings). */
		BACK,
		/** A full slow turn. */
		TURN
	}

	private PlayerPreview() {
	}

	/** Draw inside (x, y, w, h), turned to {@code bodyYaw} degrees (180 = facing you). Does nothing outside a world. */
	public static void render(GuiGraphics g, int x, int y, int w, int h, float bodyYaw) {
		draw(g, x, y, w, h, bodyYaw);
	}

	/** Draw inside (x, y, w, h). Does nothing outside a world. */
	public static void render(GuiGraphics g, int x, int y, int w, int h, View view) {
		float t = (Util.getMillis() % 14000L) / 14000f * (float) (Math.PI * 2);
		draw(g, x, y, w, h, view == View.BACK ? Mth.sin(t) * 40f : (Util.getMillis() % 14000L) / 14000f * 360f);
	}

	private static void draw(GuiGraphics g, int x, int y, int w, int h, float bodyYaw) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;
		EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, 1.0F);
		state.lightCoords = LightTexture.FULL_BRIGHT;
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			// keep an emote's spin on top of the chosen angle
			float spin = living.bodyRot - player.yBodyRot;
			living.bodyRot = bodyYaw + (dev.telmuad.hollowclient.styles.Emotes.current() != null ? spin : 0f);
			living.yRot = 0f;
			living.xRot = 0f;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1f;
		}
		float scale = Math.max(8, Math.min(h * 0.42f, w * 0.9f));
		Vector3f offset = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		g.submitEntityRenderState(state, scale, offset, rotation, new Quaternionf(), x, y, x + w, y + h);
	}
}
