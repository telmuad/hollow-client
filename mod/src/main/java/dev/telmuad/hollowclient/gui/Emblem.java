package dev.telmuad.hollowclient.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The Hollow emblem (a cut crystal with an "H" cut out of its middle), drawn from separate white/gray layers made by
 * branding/tools/emblem.py so every piece takes the current theme's colors. It can play an intro: the facets fly in
 * and lock together, the H "hollows out", then the glow keeps breathing.
 */
public final class Emblem {
	private static final String DIR = "textures/gui/emblem/";
	private static final JarTexture EDGE = new JarTexture(DIR + "edge.png", "emblem_edge");
	private static final JarTexture RIM = new JarTexture(DIR + "hole_rim.png", "emblem_rim");
	private static final JarTexture FILL = new JarTexture(DIR + "hole_fill.png", "emblem_fill");
	private static final JarTexture SPARK = new JarTexture(DIR + "spark.png", "emblem_spark");
	private static final JarTexture GLOW = new JarTexture(DIR + "glow.png", "emblem_glow");

	/** One facet: its texture and where its center sits (0..1 of the layer). */
	private record Facet(JarTexture texture, float cx, float cy) {
	}

	private static List<Facet> facets;

	private Emblem() {
	}

	private static List<Facet> facets() {
		if (facets != null) return facets;
		List<Facet> out = new ArrayList<>();
		try (InputStream in = Emblem.class.getResourceAsStream("/assets/hollowclient/" + DIR + "facets.json")) {
			if (in != null) {
				JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				float pad = root.has("pad") ? root.get("pad").getAsFloat() : 0.08f;
				JsonArray list = root.getAsJsonArray("facets");
				for (int i = 0; i < list.size(); i++) {
					JsonElement e = list.get(i);
					JsonObject o = e.getAsJsonObject();
					float cx = pad + o.get("x").getAsFloat() * (1 - 2 * pad);
					float cy = pad + o.get("y").getAsFloat() * (1 - 2 * pad);
					out.add(new Facet(new JarTexture(DIR + "facet_" + i + ".png", "emblem_facet_" + i), cx, cy));
				}
			}
		} catch (Exception e) {
			HollowClient.LOG.warn("Couldn't read the emblem layout; drawing the plain sigil instead", e);
		}
		facets = out;
		return out;
	}

	/**
	 * Draw the emblem in the square (x, y, size). {@code intro} runs 0 → 1 (1 = fully assembled), {@code glow} is the
	 * glow strength 0..1 and {@code pulse} how much it breathes.
	 */
	public static void draw(GuiGraphicsExtractor g, float x, float y, float size, float alpha, float intro, float glow, float pulse) {
		List<Facet> list = facets();
		if (list.isEmpty()) {
			Fx.SIGIL.draw(g, x, y, size, size, ColorUtil.fade(Theme.accent(), alpha));
			return;
		}
		float t = Anim.clamp01(intro);
		long now = Util.getMillis();
		float breathe = 0.8f + pulse * 0.35f * ((float) Math.sin(now * Theme.speed() / 520.0) - 0.43f);
		// glow comes up as the pieces lock together
		float settle = Anim.easeOutCubic(Anim.clamp01((t - 0.45f) / 0.55f));
		if (glow > 0f) {
			// the glow layer is already a soft halo around the crystal's edge: draw it at the emblem's own size
			float pad = size * 0.04f;
			GLOW.draw(g, x - pad, y - pad, size + 2 * pad, size + 2 * pad, ColorUtil.fade(Theme.accent2(), alpha * glow * breathe * settle));
			GLOW.draw(g, x, y, size, size, ColorUtil.fade(Theme.accent(), alpha * glow * breathe * settle * 0.8f));
		}
		Matrix3x2fStack pose = g.pose();
		int n = list.size();
		for (int i = 0; i < n; i++) {
			Facet f = list.get(i);
			// staggered: each facet starts a little after the previous one
			float local = Anim.clamp01((t * 1.5f - i * 0.07f) / 0.6f);
			if (local <= 0f) continue;
			float e = Anim.easeOutBack(local);
			float dx = (f.cx() - 0.5f) * size;
			float dy = (f.cy() - 0.5f) * size;
			float fly = (1f - e) * 1.6f;
			int color = facetColor(f);
			pose.pushMatrix();
			// fly in from outside along the facet's direction, turning into place
			pose.translate(x + size / 2f + dx * (1 + fly), y + size / 2f + dy * (1 + fly));
			pose.rotate((1f - e) * (i % 2 == 0 ? 0.9f : -0.9f));
			pose.translate(-size / 2f - dx, -size / 2f - dy);
			f.texture().draw(g, 0, 0, size, size, ColorUtil.fade(color, alpha * Anim.clamp01(local * 1.6f)));
			pose.popMatrix();
		}
		if (settle > 0f) EDGE.draw(g, x, y, size, size, ColorUtil.fade(ColorUtil.mix(Theme.accent(), 0xFFFFFFFF, 0.2f), alpha * settle));
		// the H starts filled in and hollows out once the facets have landed
		float hollow = Anim.easeInOutCubic(Anim.clamp01((t - 0.62f) / 0.3f));
		if (hollow < 1f && t > 0.3f) {
			FILL.draw(g, x, y, size, size, ColorUtil.fade(Theme.gradient(0.5f), alpha * (1f - hollow) * Anim.clamp01((t - 0.3f) / 0.2f)));
		}
		if (hollow > 0f) RIM.draw(g, x, y, size, size, ColorUtil.fade(ColorUtil.mix(Theme.accent2(), 0xFFFFFFFF, 0.15f), alpha * hollow));
		// the little spark twinkles
		float tw = 0.75f + 0.25f * (float) Math.sin(now / 340.0);
		if (hollow > 0f) SPARK.draw(g, x, y, size, size, ColorUtil.fade(0xFFFFFFFF, alpha * hollow * tw));
	}

	/** Each facet takes the theme gradient at its position (violet top-left to cyan bottom-right in the Void theme). */
	private static int facetColor(Facet f) {
		float pos = Anim.clamp01((f.cx() * 0.6f + f.cy() * 0.4f - 0.15f) * 1.4f);
		return Theme.gradient(pos);
	}
}
