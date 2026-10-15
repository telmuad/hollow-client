package dev.telmuad.hollowclient.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpringTest {
	@Test
	void reachesTheTargetAndSettles() {
		Spring s = Spring.smooth(0f).target(1f);
		for (int i = 0; i < 120; i++) s.update(1f / 60f);
		assertEquals(1f, s.value(), 1e-3);
		assertTrue(s.settled());
	}

	@Test
	void bouncyOvershootsALittle() {
		Spring s = Spring.bouncy(0f).target(1f);
		float max = 0f;
		for (int i = 0; i < 120; i++) max = Math.max(max, s.update(1f / 60f));
		assertTrue(max > 1.01f, "should overshoot, max " + max);
		assertTrue(max < 1.15f, "but not much, max " + max);
	}

	@Test
	void sameMotionAtAnyFrameRate() {
		Spring slow = Spring.bouncy(0f).target(1f);
		Spring fast = Spring.bouncy(0f).target(1f);
		for (int i = 0; i < 9; i++) slow.update(1f / 30f);
		for (int i = 0; i < 90; i++) fast.update(1f / 300f);
		assertEquals(slow.value(), fast.value(), 0.01f);
	}

	@Test
	void hugeOrInvalidStepsAreSafe() {
		Spring s = Spring.bouncy(0f).target(1f);
		s.update(Float.NaN);
		assertEquals(0f, s.value());
		s.update(-1f);
		assertEquals(0f, s.value());
		s.update(30f); // a hitch: capped, no explosion
		assertTrue(Float.isFinite(s.value()) && Math.abs(s.value()) < 2f);
		s.snap(0.5f);
		assertEquals(0.5f, s.value());
		assertTrue(s.settled());
	}
}
