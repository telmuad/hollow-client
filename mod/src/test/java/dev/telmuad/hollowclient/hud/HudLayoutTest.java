package dev.telmuad.hollowclient.hud;

import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.hud.HudLayout.Box;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
	private static final double EPS = 1e-9;

	@Test
	void storeThenPlaceGivesTheSamePixels() {
		HudPosition p = new HudPosition(Anchor.START, 0, Anchor.START, 0, 1f);
		double[][] spots = {{3, 4}, {200, 120}, {400, 250}, {460, 255}, {0, 0}};
		for (double[] s : spots) {
			HudLayout.store(p, s[0], s[1], 20, 10, 480, 270);
			Box b = HudLayout.place(p, 480, 270, 20, 10);
			assertEquals(s[0], b.x(), 1e-6);
			assertEquals(s[1], b.y(), 1e-6);
		}
	}

	@Test
	void anchorFollowsTheElementCenter() {
		HudPosition p = new HudPosition(Anchor.START, 0, Anchor.START, 0, 1f);
		HudLayout.store(p, 10, 10, 40, 10, 480, 270);
		assertEquals(Anchor.START, p.anchorX);
		HudLayout.store(p, 220, 130, 40, 10, 480, 270);
		assertEquals(Anchor.CENTER, p.anchorX);
		assertEquals(Anchor.CENTER, p.anchorY);
		HudLayout.store(p, 440, 260, 40, 10, 480, 270);
		assertEquals(Anchor.END, p.anchorX);
		assertEquals(Anchor.END, p.anchorY);
	}

	@Test
	void rightFlushElementStaysFlushAfterResize() {
		HudPosition p = new HudPosition(Anchor.START, 0, Anchor.START, 0, 1f);
		HudLayout.store(p, 480 - 40, 0, 40, 10, 480, 270);
		Box wide = HudLayout.place(p, 960, 540, 40, 10);
		assertEquals(960 - 40, wide.x(), EPS);
		assertEquals(0, wide.y(), EPS);
		Box small = HudLayout.place(p, 320, 240, 40, 10);
		assertEquals(320 - 40, small.x(), EPS);
	}

	@Test
	void centeredElementStaysCentered() {
		HudPosition p = new HudPosition(Anchor.START, 0, Anchor.START, 0, 1f);
		HudLayout.store(p, 240 - 30, 100, 60, 12, 480, 270);
		Box b = HudLayout.place(p, 1000, 600, 60, 12);
		assertEquals(500 - 30, b.x(), EPS);
	}

	@Test
	void placeClampsOnScreen() {
		HudPosition p = new HudPosition(Anchor.START, 0.99, Anchor.END, -0.5, 1f);
		Box b = HudLayout.place(p, 480, 270, 50, 20);
		assertEquals(430, b.x(), EPS);
		assertEquals(250, b.y(), EPS);
		// Element bigger than the screen sits at 0.
		Box huge = HudLayout.place(p, 100, 100, 300, 300);
		assertEquals(0, huge.x(), EPS);
		assertEquals(0, huge.y(), EPS);
	}

	@Test
	void snapsToScreenEdgesAndCenter() {
		HudLayout.Snap s = HudLayout.snap(3, 260 - 4, 40, 10, 480, 270, List.of(), 5);
		assertEquals(0, s.x(), EPS);
		assertEquals(0, s.guideX(), EPS);
		assertEquals(260, s.y(), EPS, "bottom edge snapped to 270");
		assertEquals(270, s.guideY(), EPS);
		HudLayout.Snap c = HudLayout.snap(222, 100, 40, 10, 480, 270, List.of(), 5);
		assertEquals(220, c.x(), EPS, "center snapped to 240");
		assertTrue(Double.isNaN(c.guideY()));
	}

	@Test
	void snapsToOtherElements() {
		Box other = new Box(100, 50, 60, 12);
		// Left edge 4px right of the other's right edge -> touches it.
		HudLayout.Snap s = HudLayout.snap(164, 90, 30, 10, 480, 270, List.of(other), 5);
		assertEquals(160, s.x(), EPS);
		// Top edge lines up with the other's bottom.
		HudLayout.Snap t = HudLayout.snap(300, 64, 30, 10, 480, 270, List.of(other), 5);
		assertEquals(62, t.y(), EPS);
	}

	@Test
	void farFromEverythingDoesNotSnap() {
		HudLayout.Snap s = HudLayout.snap(150, 77, 30, 10, 480, 270, List.of(), 4);
		assertEquals(150, s.x(), EPS);
		assertEquals(77, s.y(), EPS);
		assertTrue(Double.isNaN(s.guideX()));
	}

	@Test
	void jsonRoundTripAndFallback() {
		HudPosition p = new HudPosition(Anchor.END, 0.1, Anchor.CENTER, -0.2, 1.75f);
		HudPosition back = HudPosition.fromJson(p.toJson(), new HudPosition(Anchor.START, 0, Anchor.START, 0, 1f));
		assertEquals(p, back);
		HudPosition fallback = new HudPosition(Anchor.START, 0.3, Anchor.START, 0.4, 1f);
		HudPosition bad = HudPosition.fromJson(JsonParser.parseString("{\"x\": \"no\", \"scale\": 0.01, \"anchorY\": \"end\"}"), fallback);
		assertEquals(0.3, bad.offsetX, EPS);
		assertEquals(Anchor.END, bad.anchorY);
		assertEquals(HudPosition.MIN_SCALE, bad.scale);
	}

	@Test
	void scaleIsClampedAndRounded() {
		assertEquals(1.25f, HudPosition.clampScale(1.26f));
		assertEquals(HudPosition.MAX_SCALE, HudPosition.clampScale(10f));
		assertEquals(1f, HudPosition.clampScale(Float.NaN));
	}
}
