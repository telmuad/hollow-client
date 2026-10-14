package dev.telmuad.hollowclient.modules.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.mixin.OverlayTextureAccessor;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Changes the color of the red flash on entities that take damage. Vanilla keeps that tint in a tiny 16x16
 * "overlay" texture (top half = damage color); this rewrites those pixels and uploads the texture again.
 * Visual only.
 */
public final class HitColorModule extends Module {
	/** Vanilla's damage tint: red at 70% opacity. */
	public static final int VANILLA = 0xB2FF0000;

	private final ColorSetting color = add(new ColorSetting("color", "Hit color", 0x998B6CFF));
	private boolean dirty = true;
	private int applied = VANILLA;

	public HitColorModule() {
		super("hit_color", "Hit Color", "Change the red flash on mobs and players that get hit.", Category.VISUAL, false);
		color.describe("The alpha slider sets how strong the tint is.");
	}

	@Override
	public void onLoaded() {
		dirty = true;
	}

	@Override
	protected void onEnable() {
		dirty = true;
	}

	@Override
	protected void onDisable() {
		dirty = true;
	}

	@Override
	protected void onSettingChanged(Setting<?> setting) {
		dirty = true;
	}

	@Override
	public void onTick() {
		if (dirty) applyNow();
	}

	private void applyNow() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameRenderer == null) return;
		int want = isEnabled() ? color.argb() : VANILLA;
		dirty = false;
		if (want == applied) return;
		try {
			DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).hollowclient$texture();
			NativeImage pixels = texture.getPixels();
			if (pixels == null) return;
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 16; x++) pixels.setPixel(x, y, want);
			}
			texture.upload();
			applied = want;
		} catch (RuntimeException e) {
			HollowClient.LOG.warn("Could not change the hit color", e);
		}
	}
}
