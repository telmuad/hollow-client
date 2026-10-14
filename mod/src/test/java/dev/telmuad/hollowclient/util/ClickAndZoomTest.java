package dev.telmuad.hollowclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickAndZoomTest {
	@Test
	void clicksExpireAfterOneSecond() {
		ClickTracker t = new ClickTracker();
		t.press(1000);
		t.press(1200);
		t.press(1900);
		assertEquals(3, t.cps(1950));
		assertEquals(2, t.cps(2000), "the press at 1000 is exactly one second old");
		assertEquals(1, t.cps(2500));
		assertEquals(0, t.cps(3000));
	}

	@Test
	void clickQueueIsCapped() {
		ClickTracker t = new ClickTracker();
		for (int i = 0; i < 1000; i++) t.press(5000);
		assertTrue(t.cps(5000) <= 200);
	}

	@Test
	void zoomStartsAtBaseAndScrollChangesIt() {
		ZoomState z = new ZoomState();
		assertEquals(1.0, z.fovMultiplier());
		z.start(4);
		z.update(0.016, false, 10);
		assertEquals(0.25, z.fovMultiplier(), 1e-9);
		z.scroll(1);
		z.update(0.016, false, 10);
		assertEquals(4 * ZoomState.SCROLL_STEP, z.current(), 1e-9);
		z.scroll(-1000);
		z.update(0.016, false, 10);
		assertEquals(ZoomState.MIN_ZOOM, z.current(), 1e-9);
		z.scroll(1000);
		z.update(0.016, false, 10);
		assertEquals(ZoomState.MAX_ZOOM, z.current(), 1e-9);
	}

	@Test
	void scrollIsIgnoredWhenNotZooming() {
		ZoomState z = new ZoomState();
		z.scroll(5);
		z.update(0.016, false, 10);
		assertEquals(1.0, z.current());
		assertFalse(z.isZoomed());
	}

	@Test
	void smoothZoomApproachesTargetAndReturns() {
		ZoomState z = new ZoomState();
		z.start(4);
		z.update(0.05, true, 12);
		assertTrue(z.current() > 1 && z.current() < 4, "partway after one frame");
		for (int i = 0; i < 200; i++) z.update(0.016, true, 12);
		assertEquals(4.0, z.current(), 1e-6);
		z.stop();
		z.update(0.016, true, 12);
		assertTrue(z.isZoomed(), "still easing out");
		for (int i = 0; i < 200; i++) z.update(0.016, true, 12);
		assertFalse(z.isZoomed());
		assertEquals(1.0, z.fovMultiplier(), 1e-6);
	}

	@Test
	void eachZoomStartsFromTheConfiguredBase() {
		ZoomState z = new ZoomState();
		z.start(4);
		z.scroll(5);
		z.stop();
		z.start(4);
		assertEquals(4.0, z.held(), 1e-9);
	}
}
