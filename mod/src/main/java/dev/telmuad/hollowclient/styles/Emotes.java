package dev.telmuad.hollowclient.styles;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.styles.EmotesModule;
import dev.telmuad.hollowclient.setting.Labeled;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Emotes for your own player. Playing one only changes how your model is posed on your screen (through the render
 * state, see PlayerModelMixin); nothing is sent to the server, so other players never see it.
 */
public final class Emotes {
	public enum Emote implements Labeled {
		WAVE("Wave", 2600), CHEER("Cheer", 2400), CLAP("Clap", 2800), DANCE("Dance", 6000),
		FACEPALM("Facepalm", 2400), SALUTE("Salute", 2200), DAB("Dab", 1800), SPIN("Spin", 1700);

		private final String label;
		final long durationMs;

		Emote(String label, long durationMs) {
			this.label = label;
			this.durationMs = durationMs;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** The emote on a render state: which one and how far into it (seconds). */
	public record Playing(Emote emote, float seconds, float blend) {
	}

	public static final RenderStateDataKey<Playing> KEY = RenderStateDataKey.create(() -> "hollowclient:emote");
	private static final float BLEND_S = 0.25f;

	private static Emote current;
	private static long startedAt;
	private static Vec3 startPos;
	private static CameraType restoreCamera;

	private Emotes() {
	}

	private static EmotesModule module() {
		EmotesModule m = Modules.EMOTES;
		return m != null && m.isEnabled() ? m : null;
	}

	public static Emote current() {
		return current;
	}

	public static void play(Emote emote) {
		Minecraft mc = Minecraft.getInstance();
		EmotesModule m = module();
		if (m == null || mc.player == null || emote == null) return;
		current = emote;
		startedAt = Util.getMillis();
		startPos = mc.player.position();
		if (m.thirdPerson() && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			restoreCamera = CameraType.FIRST_PERSON;
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}
	}

	public static void stop() {
		if (current == null) return;
		current = null;
		Minecraft mc = Minecraft.getInstance();
		if (restoreCamera != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(restoreCamera);
		restoreCamera = null;
	}

	/** Every client tick: end the emote when its time is up, or when you walk away (if that option is on). */
	public static void tick(Minecraft mc) {
		if (current == null) return;
		EmotesModule m = module();
		if (m == null || mc.player == null) {
			stop();
			return;
		}
		long age = Util.getMillis() - startedAt;
		if (age > current.durationMs) {
			stop();
			return;
		}
		if (m.stopWhenMoving() && age > 300 && startPos != null && mc.player.position().distanceToSqr(startPos) > 0.25 * 0.25) stop();
	}

	/** Put the playing emote on your own render state (called by AvatarRendererMixin for the local player only). */
	public static void apply(AvatarRenderState state) {
		if (current == null) {
			state.setData(KEY, null);
			return;
		}
		float s = (Util.getMillis() - startedAt) / 1000f;
		float total = current.durationMs / 1000f;
		float blend = Mth.clamp(Math.min(s / BLEND_S, (total - s) / BLEND_S), 0f, 1f);
		state.setData(KEY, new Playing(current, s, blend));
		if (current == Emote.SPIN) state.bodyRot += easeSpin(s / total) * 720f;
	}

	private static float easeSpin(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static void rot(ModelPart part, float x, float y, float z, float blend) {
		part.xRot += (x - part.xRot) * blend;
		part.yRot += (y - part.yRot) * blend;
		part.zRot += (z - part.zRot) * blend;
	}

	/** Pose the model for the emote (after vanilla's own animation). Angles in radians; +x is the player's left. */
	public static void pose(PlayerModel model, Playing p) {
		float t = p.seconds();
		float b = p.blend();
		switch (p.emote()) {
			case WAVE -> {
				rot(model.rightArm, -0.2f, 0f, 2.35f + Mth.sin(t * 10f) * 0.35f, b);
				rot(model.head, model.head.xRot, model.head.yRot, Mth.sin(t * 5f) * 0.08f, b);
			}
			case CHEER -> {
				float bounce = Math.abs(Mth.sin(t * 7f)) * 0.25f;
				rot(model.rightArm, 0f, 0f, 2.75f + bounce, b);
				rot(model.leftArm, 0f, 0f, -2.75f - bounce, b);
				rot(model.head, -0.25f, model.head.yRot, 0f, b);
			}
			case CLAP -> {
				float c = 0.12f + 0.42f * Math.abs(Mth.sin(t * 9f));
				rot(model.rightArm, -1.35f, -c, 0f, b);
				rot(model.leftArm, -1.35f, c, 0f, b);
			}
			case DANCE -> {
				float beat = t * 8f;
				rot(model.rightLeg, Mth.sin(beat) * 0.55f, 0f, 0.05f, b);
				rot(model.leftLeg, -Mth.sin(beat) * 0.55f, 0f, -0.05f, b);
				rot(model.rightArm, -0.3f * Mth.cos(beat), 0f, 0.4f + 1.2f * Math.max(0f, Mth.sin(t * 4f)), b);
				rot(model.leftArm, 0.3f * Mth.cos(beat), 0f, -0.4f - 1.2f * Math.max(0f, -Mth.sin(t * 4f)), b);
				rot(model.head, Mth.sin(beat) * 0.12f, Mth.sin(t * 4f) * 0.35f, 0f, b);
			}
			case FACEPALM -> {
				rot(model.rightArm, -2.15f, -0.55f, 0f, b);
				rot(model.head, 0.45f + Mth.sin(t * 3f) * 0.05f, 0.1f, 0f, b);
			}
			case SALUTE -> {
				rot(model.rightArm, -2.45f, -0.75f, 0.25f, b);
				rot(model.head, -0.05f, 0f, 0f, b);
			}
			case DAB -> {
				rot(model.rightArm, -0.45f, 0f, 2.3f, b);
				rot(model.leftArm, -1.9f, 0.95f, 0f, b);
				rot(model.head, 0.55f, -0.55f, 0f, b);
			}
			case SPIN -> {
				rot(model.rightArm, 0f, 0f, 1.55f, b);
				rot(model.leftArm, 0f, 0f, -1.55f, b);
				rot(model.head, -0.2f, 0f, 0f, b);
			}
		}
	}
}
