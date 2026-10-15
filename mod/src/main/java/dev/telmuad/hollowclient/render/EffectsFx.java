package dev.telmuad.hollowclient.render;

import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.effects.HitEffectsModule;
import dev.telmuad.hollowclient.modules.effects.ParticlesModule;
import dev.telmuad.hollowclient.render.HollowParticle.Kind;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Effects for the Hit Effects and Particles modules. Everything here only spawns particles in your own client and
 * draws on your own screen; it only reacts to what the game already tells the client (your attacks, crit and death
 * events, block breaks, your movement).
 */
public final class EffectsFx {
	private static final Random RANDOM = new Random();
	private static final long HIT_MEMORY_MS = 5000;
	private static final float FLASH_MS = 260f;

	private static final Map<Integer, Long> RECENT_HITS = new HashMap<>();
	private static long flashAt;
	private static float flashStrength;
	private static boolean spawningBreak;
	private static float trailBudget;
	private static double printX = Double.NaN;
	private static double printZ = Double.NaN;
	private static boolean leftFoot;
	private static boolean wasOnGround = true;
	private static double fallStartY;
	private static int sprintTicks;

	private EffectsFx() {
	}

	private static HitEffectsModule hits() {
		HitEffectsModule m = Modules.HIT_EFFECTS;
		return m != null && m.isEnabled() ? m : null;
	}

	private static ParticlesModule particles() {
		ParticlesModule m = Modules.PARTICLES;
		return m != null && m.isEnabled() ? m : null;
	}

	/** Quality factor for particle counts (Auto Quality / Performance Mode). */
	private static float q() {
		return Quality.performanceMode() ? 0.35f : Math.max(0.35f, Quality.scale());
	}

	private static int count(float base) {
		float v = base * q();
		int n = (int) v;
		if (RANDOM.nextFloat() < v - n) n++;
		return n;
	}

	private static void spark(ClientLevel level, Kind kind, double x, double y, double z, double vx, double vy, double vz,
							  int color, float size, int life, float gravity) {
		Minecraft.getInstance().particleEngine.add(HollowParticle.spark(level, kind, x, y, z, vx, vy, vz, color, size, life, gravity, true));
	}

	private static int burstColor(HitEffectsModule m, float t) {
		return m.themeColor() ? Theme.gradient(t) : ColorUtil.mix(m.color(), 0xFFFFFFFF, t * 0.3f);
	}

	// ------------------------------------------------------------------------------------------------ hits

	/** Number of footprints placed so far (for tests). */
	public static int footprintsPlaced;

	/**
	 * Where hit effects appear: in front of the entity, toward you, at 70 % of its height. Models often stick out of
	 * their hitbox (heads, snouts), so the point sits clearly outside the box, but never past half the way to you.
	 */
	private static Vec3 facingPoint(Minecraft mc, Entity target) {
		AABB box = target.getBoundingBox();
		Vec3 center = box.getCenter();
		Vec3 toPlayer = mc.player.getEyePosition().subtract(center).multiply(1, 0, 1);
		double dist = toPlayer.length();
		if (dist < 1e-3) return center;
		double out = Math.min(box.getXsize() / 2 + 0.5, dist * 0.5);
		Vec3 p = center.add(toPlayer.scale(out / dist));
		return new Vec3(p.x, box.minY + box.getYsize() * 0.7, p.z);
	}

	/** You attacked an entity (called before vanilla sends the attack; nothing about the attack changes). */
	public static void onLocalAttack(Entity target) {
		RECENT_HITS.put(target.getId(), Util.getMillis());
		HitEffectsModule m = hits();
		Minecraft mc = Minecraft.getInstance();
		if (m == null || m.hitBurst() <= 0f || mc.level == null || mc.player == null) return;
		Vec3 hit = facingPoint(mc, target);
		int n = count(4 + 10 * m.hitBurst());
		for (int i = 0; i < n; i++) {
			double vx = RANDOM.nextGaussian() * 0.08;
			double vy = RANDOM.nextDouble() * 0.12 + 0.02;
			double vz = RANDOM.nextGaussian() * 0.08;
			int c = burstColor(m, RANDOM.nextFloat());
			spark(mc.level, Kind.DOT, hit.x, hit.y, hit.z, vx, vy, vz, c, 0.07f + RANDOM.nextFloat() * 0.07f, 8 + RANDOM.nextInt(8), 0.6f);
		}
	}

	private static boolean recentlyHitByMe(Entity e, long withinMs) {
		Long t = RECENT_HITS.get(e.getId());
		return t != null && Util.getMillis() - t <= withinMs;
	}

	/** The server said an entity took a critical hit (action 4) or magic hit (5). */
	public static void onCritPacket(Entity e, int action) {
		HitEffectsModule m = hits();
		Minecraft mc = Minecraft.getInstance();
		if (m == null || mc.level == null) return;
		int extra = m.critMultiplier() - 1;
		for (int i = 0; i < extra; i++) {
			mc.particleEngine.createTrackingEmitter(e, action == 4 ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT);
		}
		if (action != 4 || !recentlyHitByMe(e, 1000)) return;
		if (m.critSparks() > 0f && mc.player != null) {
			Vec3 c = facingPoint(mc, e);
			int n = count(6 + 14 * m.critSparks());
			for (int i = 0; i < n; i++) {
				double vx = RANDOM.nextGaussian() * 0.16;
				double vy = RANDOM.nextGaussian() * 0.12 + 0.05;
				double vz = RANDOM.nextGaussian() * 0.16;
				int color = ColorUtil.mix(0xFFFFFFFF, i % 2 == 0 ? Theme.accent() : Theme.accent2(), 0.5f + RANDOM.nextFloat() * 0.4f);
				spark(mc.level, Kind.SPARKLE, c.x, c.y, c.z, vx, vy, vz, color, 0.2f + RANDOM.nextFloat() * 0.14f, 8 + RANDOM.nextInt(6), 0.2f);
			}
		}
		if (m.critFlash() > 0f && !Quality.reducedMotion()) {
			flashAt = Util.getMillis();
			flashStrength = m.critFlash();
		}
	}

	/** A living entity's death event arrived; burst if you hit it in the last few seconds. */
	public static void onDeath(LivingEntity e) {
		HitEffectsModule m = hits();
		Minecraft mc = Minecraft.getInstance();
		if (m == null || m.killBurst() <= 0f || mc.level == null || !recentlyHitByMe(e, HIT_MEMORY_MS)) return;
		RECENT_HITS.remove(e.getId());
		Vec3 c = e.getBoundingBox().getCenter();
		int ring = count(14 + 26 * m.killBurst());
		for (int i = 0; i < ring; i++) {
			double a = i * Math.PI * 2 / ring;
			double speed = 0.18 + RANDOM.nextDouble() * 0.05;
			int color = burstColor(m, (float) i / ring);
			spark(mc.level, Kind.DOT, c.x, c.y, c.z, Math.cos(a) * speed, 0.02, Math.sin(a) * speed, color, 0.12f, 14 + RANDOM.nextInt(6), 0f);
		}
		int up = count(6 + 10 * m.killBurst());
		for (int i = 0; i < up; i++) {
			spark(mc.level, Kind.SPARKLE, c.x + RANDOM.nextGaussian() * 0.2, c.y, c.z + RANDOM.nextGaussian() * 0.2,
					RANDOM.nextGaussian() * 0.03, 0.12 + RANDOM.nextDouble() * 0.12, RANDOM.nextGaussian() * 0.03,
					ColorUtil.mix(burstColor(m, RANDOM.nextFloat()), 0xFFFFFFFF, 0.4f), 0.16f, 16 + RANDOM.nextInt(8), -0.05f);
		}
	}

	/** Crit flash: a quick glow in from the screen edges. Drawn with the HUD. */
	public static void renderFlash(GuiGraphics g) {
		if (flashAt == 0) return;
		float t = (Util.getMillis() - flashAt) / FLASH_MS;
		if (t >= 1f) {
			flashAt = 0;
			return;
		}
		float a = Math.min(1f, flashStrength * 1.1f) * (1f - t) * (1f - t);
		int w = g.guiWidth();
		int h = g.guiHeight();
		int band = Math.max(12, Math.min(w, h) / 6);
		int c = ColorUtil.mix(Theme.accent(), 0xFFFFFFFF, 0.35f);
		g.fillGradient(0, 0, w, band, ColorUtil.fade(c, a), ColorUtil.fade(c, 0f));
		g.fillGradient(0, h - band, w, h, ColorUtil.fade(c, 0f), ColorUtil.fade(c, a));
		int strips = 12;
		for (int i = 0; i < strips; i++) {
			float k = 1f - i / (float) strips;
			int x0 = band * i / strips;
			int x1 = band * (i + 1) / strips;
			int col = ColorUtil.fade(c, a * k * k);
			g.fill(x0, 0, x1, h, col);
			g.fill(w - x1, 0, w - x0, h, col);
		}
	}

	// ------------------------------------------------------------------------------------------------ block breaks

	/** A block broke (vanilla just spawned its pieces). */
	public static void onBlockBroken(ClientLevel level, BlockPos pos, BlockState state) {
		ParticlesModule m = particles();
		if (m == null || spawningBreak || state.isAir()) return;
		spawningBreak = true;
		try {
			int rounds = Math.round(m.extraBreakRounds() * q());
			for (int i = 0; i < rounds; i++) level.addDestroyBlockEffect(pos, state);
		} finally {
			spawningBreak = false;
		}
		if (m.breakSparkles() > 0f) {
			int n = count(4 + 10 * m.breakSparkles());
			for (int i = 0; i < n; i++) {
				double x = pos.getX() + 0.5 + RANDOM.nextGaussian() * 0.25;
				double y = pos.getY() + 0.5 + RANDOM.nextGaussian() * 0.25;
				double z = pos.getZ() + 0.5 + RANDOM.nextGaussian() * 0.25;
				spark(level, i % 3 == 0 ? Kind.SPARKLE : Kind.DOT, x, y, z, RANDOM.nextGaussian() * 0.06, 0.06 + RANDOM.nextDouble() * 0.08,
						RANDOM.nextGaussian() * 0.06, Theme.gradient(RANDOM.nextFloat()), 0.08f + RANDOM.nextFloat() * 0.06f, 10 + RANDOM.nextInt(10), 0.5f);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ movement

	/** Every client tick: trail, footprints and footstep dust for your own player. */
	public static void tick(Minecraft mc) {
		RECENT_HITS.values().removeIf(t -> Util.getMillis() - t > HIT_MEMORY_MS);
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		ParticlesModule m = particles();
		if (p == null || level == null || m == null || mc.isPaused()) {
			printX = Double.NaN;
			return;
		}
		Vec3 motion = p.getDeltaMovement();
		double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		trail(mc, p, level, m, speed);
		footsteps(p, level, m, speed);
	}

	private static void trail(Minecraft mc, LocalPlayer p, ClientLevel level, ParticlesModule m, double speed) {
		if (m.trail() <= 0f || p.isSpectator() || p.isInvisible()) return;
		if (mc.options.getCameraType() == CameraType.FIRST_PERSON && !m.trailFirstPerson()) return;
		boolean moving = speed > 0.03 || !p.onGround();
		trailBudget += (moving ? 4.5f : 0.6f) * m.trail() * q();
		while (trailBudget >= 1f) {
			trailBudget -= 1f;
			// a little behind you (against the way you move), spread around your body
			double back = 1.5;
			double x = p.getX() + RANDOM.nextGaussian() * 0.22 - p.getDeltaMovement().x * back;
			double y = p.getY() + 0.1 + RANDOM.nextDouble() * 1.3;
			double z = p.getZ() + RANDOM.nextGaussian() * 0.22 - p.getDeltaMovement().z * back;
			switch (m.trailStyle()) {
				case THEME -> spark(level, Kind.DOT, x, y, z, 0, 0.01, 0,
						Theme.gradient((float) ((Util.getMillis() / 1500.0 + RANDOM.nextDouble() * 0.2) % 1.0)), 0.14f, 20 + RANDOM.nextInt(10), -0.02f);
				case SPARKLES -> spark(level, Kind.SPARKLE, x, y, z, 0, 0.01, 0,
						ColorUtil.mix(0xFFFFFFFF, Theme.accent(), RANDOM.nextFloat() * 0.6f), 0.12f, 14 + RANDOM.nextInt(8), 0f);
				case FLAMES -> level.addParticle(RANDOM.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, x, p.getY() + 0.1 + RANDOM.nextDouble() * 0.6, z, 0, 0.01, 0);
				case HEARTS -> level.addParticle(ParticleTypes.HEART, x, y, z, 0, 0.02, 0);
				case NOTES -> level.addParticle(ParticleTypes.NOTE, x, y, z, RANDOM.nextInt(25) / 24.0, 0, 0);
				case CLOUDS -> level.addParticle(ParticleTypes.CLOUD, x, p.getY() + 0.1, z, 0, 0.005, 0);
			}
		}
	}

	private static void footsteps(LocalPlayer p, ClientLevel level, ParticlesModule m, double speed) {
		boolean onGround = p.onGround();
		// landing puff
		if (!onGround && wasOnGround) fallStartY = p.getY();
		if (onGround && !wasOnGround && m.dust() > 0f && fallStartY - p.getY() > 1.2) {
			int n = count(6 + 12 * m.dust());
			for (int i = 0; i < n; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				level.addParticle(ParticleTypes.POOF, p.getX() + Math.cos(a) * 0.3, p.getY() + 0.05, p.getZ() + Math.sin(a) * 0.3,
						Math.cos(a) * 0.06, 0.01, Math.sin(a) * 0.06);
			}
		}
		wasOnGround = onGround;
		// a puff now and then while sprinting
		if (onGround && p.isSprinting() && m.dust() > 0f && ++sprintTicks % 5 == 0) {
			level.addParticle(ParticleTypes.POOF, p.getX() - p.getDeltaMovement().x * 2, p.getY() + 0.05, p.getZ() - p.getDeltaMovement().z * 2, 0, 0.01, 0);
		}
		// footprints every ~0.65 blocks walked
		if (m.footprints() <= 0f || !onGround || p.isInWater() || speed < 0.01) {
			if (!onGround) printX = Double.NaN;
			return;
		}
		if (Double.isNaN(printX)) {
			printX = p.getX();
			printZ = p.getZ();
			return;
		}
		double dx = p.getX() - printX;
		double dz = p.getZ() - printZ;
		if (dx * dx + dz * dz < 0.65 * 0.65) return;
		printX = p.getX();
		printZ = p.getZ();
		float yaw = (float) Math.atan2(-dx, dz);
		leftFoot = !leftFoot;
		double side = leftFoot ? 0.14 : -0.14;
		double px = p.getX() + Math.cos(yaw) * side;
		double pz = p.getZ() + Math.sin(yaw) * side;
		int alpha = Math.round(70 + 110 * m.footprints());
		int life = Math.round(60 + 140 * m.footprints());
		Minecraft.getInstance().particleEngine.add(HollowParticle.footprint(level, px, p.getY() + 0.015, pz, -yaw, ColorUtil.withAlpha(0x1E1A26, alpha), life));
		footprintsPlaced++;
	}
}
