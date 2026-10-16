package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.visual.PostProcessingModule;
import dev.telmuad.hollowclient.modules.visual.PostProcessingModule.Grade;
import dev.telmuad.hollowclient.util.AutoQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/**
 * Hollow's screen effects, run right after the world is drawn and before the HUD and menus:
 * <ol>
 *   <li>copy the frame to a work texture;</li>
 *   <li>bloom: bright parts → a smaller texture → horizontal blur → vertical blur;</li>
 *   <li>composite back into the frame: bloom, chromatic aberration, color grading, vignette, grain and motion blur
 *   (blend with the previous frame, kept in a persistent texture).</li>
 * </ol>
 * The passes are drawn directly (full-screen triangles with vanilla's screenquad vertex shader and Hollow's fragment
 * shaders in shaders/post/), so the settings can change every frame through small uniform buffers.
 */
public final class PostFx {
	private static final Identifier SCREENQUAD = Identifier.withDefaultNamespace("core/screenquad");
	private static final int UBO_SIZE = 96;

	private static final RenderPipeline BRIGHT = pipeline("bright", "In");
	private static final RenderPipeline BLUR = pipeline("blur", "In");
	private static final RenderPipeline COMPOSITE = pipeline("composite", "Main", "Bloom", "Prev");

	private static TextureTarget work;
	private static TextureTarget bloomA;
	private static TextureTarget bloomB;
	private static TextureTarget previous;
	private static boolean previousValid;
	private static final GpuBuffer[] ubos = new GpuBuffer[6];
	private static boolean failed;
	private static boolean warned;

	private PostFx() {
	}

	private static RenderPipeline pipeline(String name, String... samplers) {
		// 26.2: samplers and uniform blocks are declared as a bind group layout (works on OpenGL and Vulkan)
		BindGroupLayout.Builder layout = BindGroupLayout.builder();
		for (String s : samplers) layout.withSampler(s + "Sampler");
		layout.withUniform("HollowPost", UniformType.UNIFORM_BUFFER);
		return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(HollowClient.id("pipeline/post_" + name))
				.withVertexShader(SCREENQUAD)
				.withFragmentShader(HollowClient.id("post/" + name))
				.withBindGroupLayout(layout.build())
				.withColorTargetState(ColorTargetState.DEFAULT)
				.build();
	}

	/** False once the shaders failed to compile or a pass threw (then the effects stay off). */
	public static boolean healthy() {
		return !failed && !warned;
	}

	private static PostProcessingModule module() {
		PostProcessingModule m = Modules.POST_PROCESSING;
		return m != null && m.isEnabled() ? m : null;
	}

	/** Whether the screen passes run this frame (module on, something to do, allowed by Performance Mode / Iris). */
	public static boolean active() {
		PostProcessingModule m = module();
		return m != null && !failed && m.anyScreenEffect() && Quality.postProcessingAllowed();
	}

	/** Run the effects on the main frame. Called from GameRendererMixin after the world is drawn. */
	public static void process(Minecraft mc) {
		if (!active()) {
			previousValid = false;
			return;
		}
		PostProcessingModule m = module();
		GpuDevice device = RenderSystem.getDevice();
		if (!device.precompilePipeline(BRIGHT).isValid() || !device.precompilePipeline(BLUR).isValid()
				|| !device.precompilePipeline(COMPOSITE).isValid()) {
			if (!warned) HollowClient.LOG.warn("Hollow post-processing shaders failed to compile; effects are off");
			warned = true;
			return;
		}
		try {
			run(mc, m, device);
		} catch (RuntimeException e) {
			failed = true;
			HollowClient.LOG.error("Hollow post-processing failed and was turned off for this session", e);
		}
	}

	private static void run(Minecraft mc, PostProcessingModule m, GpuDevice device) {
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		int w = main.width;
		int h = main.height;
		if (w <= 0 || h <= 0) return;
		// bloom works on a small copy: cheaper, and a wider glow for the same blur size
		int div = switch (Quality.level()) {
			case HIGH -> 4;
			case MEDIUM -> 5;
			case LOW -> 6;
		};
		int bw = Math.max(1, w / div);
		int bh = Math.max(1, h / div);
		work = sized(work, "Hollow post work", w, h);
		boolean bloomOn = m.bloom() > 0f;
		if (bloomOn) {
			bloomA = sized(bloomA, "Hollow bloom A", bw, bh);
			bloomB = sized(bloomB, "Hollow bloom B", bw, bh);
		}
		boolean motion = m.motionBlur() > 0f;
		if (motion) {
			TextureTarget before = previous;
			previous = sized(previous, "Hollow previous frame", w, h);
			if (previous != before) previousValid = false;
		} else {
			previousValid = false;
		}
		for (int i = 0; i < ubos.length; i++) {
			if (ubos[i] == null) {
				int n = i;
				ubos[i] = device.createBuffer(() -> "Hollow post uniforms " + n, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
			}
		}

		CommandEncoder enc = device.createCommandEncoder();
		enc.copyTextureToTexture(main.getColorTexture(), work.getColorTexture(), 0, 0, 0, 0, 0, w, h);

		float time = (Util.getMillis() % 100_000L) / 1000f;
		if (bloomOn) {
			// two blur rounds, the second wider; sizes are in bloom-texture pixels
			float r1 = 1.0f + 1.0f * m.bloom();
			float r2 = 2.0f + 2.5f * m.bloom();
			write(enc, ubos[0], m, 0f, 0f, bw, bh, time);
			pass(enc, BRIGHT, bloomA, ubos[0], "In", work, true);
			write(enc, ubos[1], m, r1 / bw, 0f, bw, bh, time);
			pass(enc, BLUR, bloomB, ubos[1], "In", bloomA, true);
			write(enc, ubos[2], m, 0f, r1 / bh, bw, bh, time);
			pass(enc, BLUR, bloomA, ubos[2], "In", bloomB, true);
			write(enc, ubos[3], m, r2 / bw, 0f, bw, bh, time);
			pass(enc, BLUR, bloomB, ubos[3], "In", bloomA, true);
			write(enc, ubos[4], m, 0f, r2 / bh, bw, bh, time);
			pass(enc, BLUR, bloomA, ubos[4], "In", bloomB, true);
		}
		RenderTarget prev = motion && previousValid ? previous : work;
		float blend = motion && previousValid ? m.motionBlur() * 0.9f : 0f;
		writeMotion(enc, ubos[5], m, w, h, time, blend);
		pass(enc, COMPOSITE, main, ubos[5], "Main", work, true, "Bloom", bloomOn ? bloomA : work, true, "Prev", prev, false);
		if (motion) {
			enc.copyTextureToTexture(main.getColorTexture(), previous.getColorTexture(), 0, 0, 0, 0, 0, w, h);
			previousValid = true;
		}
	}

	private static TextureTarget sized(TextureTarget t, String label, int w, int h) {
		if (t == null) return new TextureTarget(label, w, h, false, GpuFormat.RGBA8_UNORM);
		if (t.width != w || t.height != h) t.resize(w, h);
		return t;
	}

	/** Draw one full-screen pass into {@code out}. Inputs come in triples: sampler name, target, bilinear. */
	private static void pass(CommandEncoder enc, RenderPipeline pipeline, RenderTarget out, GpuBuffer ubo, Object... inputs) {
		try (RenderPass rp = enc.createRenderPass(() -> "Hollow post " + pipeline.getLocation(), out.getColorTextureView(), Optional.empty())) {
			rp.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(rp);
			rp.setUniform("HollowPost", ubo);
			for (int i = 0; i < inputs.length; i += 3) {
				String name = (String) inputs[i];
				RenderTarget t = (RenderTarget) inputs[i + 1];
				boolean bilinear = (Boolean) inputs[i + 2];
				rp.bindTexture(name + "Sampler", t.getColorTextureView(),
						RenderSystem.getSamplerCache().getClampToEdge(bilinear ? FilterMode.LINEAR : FilterMode.NEAREST));
			}
			rp.draw(3, 1, 0, 0);
		}
	}

	private static void write(CommandEncoder enc, GpuBuffer ubo, PostProcessingModule m, float dx, float dy, int outW, int outH, float time) {
		writeAll(enc, ubo, m, dx, dy, outW, outH, time, 0f);
	}

	private static void writeMotion(CommandEncoder enc, GpuBuffer ubo, PostProcessingModule m, int outW, int outH, float time, float blend) {
		writeAll(enc, ubo, m, 0f, 0f, outW, outH, time, blend);
	}

	/**
	 * The uniform block every pass shares (std140):
	 * Bloom (threshold, intensity, -, -), Effects (vignette, chroma, grain, time), Grade (saturation, contrast,
	 * exposure, -), Tint (rgb, -), Lift (rgb, motion blend), Direction (blur step), OutSize (output pixels).
	 */
	private static void writeAll(CommandEncoder enc, GpuBuffer ubo, PostProcessingModule m, float dx, float dy, int outW, int outH, float time, float blend) {
		Grade g = m.grade();
		float k = m.gradeStrength();
		float scale = AutoQuality.scale(Quality.level());
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Std140Builder b = Std140Builder.onStack(stack, UBO_SIZE);
			b.putVec4(0.15f + 0.75f * m.bloomThreshold(), m.bloom() * 2.0f * (0.6f + 0.4f * scale), 0f, 0f);
			b.putVec4(m.vignette(), m.chromatic(), m.grain(), time);
			b.putVec4(lerp(1f, g.saturation, k), lerp(1f, g.contrast, k), lerp(1f, g.exposure, k), 0f);
			b.putVec4(lerp(1f, g.tintR, k), lerp(1f, g.tintG, k), lerp(1f, g.tintB, k), 0f);
			b.putVec4(g.liftR * k, g.liftG * k, g.liftB * k, blend);
			b.putVec2(dx, dy);
			b.putVec2(outW, outH);
			enc.writeToBuffer(ubo.slice(), b.get());
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
