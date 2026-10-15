package dev.telmuad.hollowclient.render;

import dev.telmuad.hollowclient.HollowClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;

/**
 * Hollow's own client-side particle (never registered, never sent anywhere): a tinted soft dot, sparkle or footprint
 * that can glow (full brightness), fall, shrink and fade. Footprints lie flat on the ground. Added straight to the
 * particle engine, so other players never see them.
 */
public final class HollowParticle extends SingleQuadParticle {
	public enum Kind {
		DOT("dot"), SPARKLE("sparkle"), FOOTPRINT("footprint");

		final String sprite;

		Kind(String sprite) {
			this.sprite = sprite;
		}
	}

	private final boolean glow;
	private final boolean flat;
	private final float yaw;
	private final float startSize;
	private final float startAlpha;
	private final boolean shrink;

	private HollowParticle(ClientLevel level, double x, double y, double z, Kind kind, boolean glow, float yaw, float size, float alpha, boolean shrink) {
		super(level, x, y, z, sprite(kind));
		this.glow = glow;
		this.flat = kind == Kind.FOOTPRINT;
		this.yaw = yaw;
		this.startSize = size;
		this.startAlpha = alpha;
		this.shrink = shrink;
		this.quadSize = size;
		this.alpha = alpha;
		this.hasPhysics = !flat;
	}

	private static TextureAtlasSprite sprite(Kind kind) {
		return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(HollowClient.id(kind.sprite));
	}

	/** A moving spark: color ARGB (alpha = starting opacity), size in blocks, life in ticks. */
	public static HollowParticle spark(ClientLevel level, Kind kind, double x, double y, double z, double vx, double vy, double vz,
									   int color, float size, int life, float gravity, boolean glow) {
		HollowParticle p = new HollowParticle(level, x, y, z, kind, glow, 0f, size, ((color >>> 24) & 255) / 255f, true);
		p.xd = vx;
		p.yd = vy;
		p.zd = vz;
		p.lifetime = Math.max(1, life);
		p.gravity = gravity;
		p.friction = 0.9f;
		p.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f);
		return p;
	}

	/** A footprint lying on the ground, turned to {@code yaw} radians, slowly fading. */
	public static HollowParticle footprint(ClientLevel level, double x, double y, double z, float yaw, int color, int life) {
		HollowParticle p = new HollowParticle(level, x, y, z, Kind.FOOTPRINT, false, yaw, 0.16f, ((color >>> 24) & 255) / 255f, false);
		p.xd = 0;
		p.yd = 0;
		p.zd = 0;
		p.lifetime = Math.max(1, life);
		p.gravity = 0f;
		p.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f);
		return p;
	}

	@Override
	public void tick() {
		super.tick();
		float t = lifetime <= 0 ? 1f : age / (float) lifetime;
		alpha = startAlpha * (flat ? Math.min(1f, (1f - t) * 3f) : (1f - t * t));
		if (shrink) quadSize = startSize * (1f - 0.6f * t);
	}

	@Override
	public FacingCameraMode getFacingCameraMode() {
		if (!flat) return FacingCameraMode.LOOKAT_XYZ;
		return (q, camera, partial) -> q.rotationY(yaw).rotateX((float) (-Math.PI / 2));
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return glow ? 0xF000F0 : super.getLightColor(partialTick);
	}
}
