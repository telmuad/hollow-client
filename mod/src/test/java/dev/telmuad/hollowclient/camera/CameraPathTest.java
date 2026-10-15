package dev.telmuad.hollowclient.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CameraPathTest {
	private static CameraPath.Key k(double x, double y, double z, float yaw, float pitch) {
		return new CameraPath.Key(x, y, z, yaw, pitch);
	}

	@Test
	void startsAndEndsOnTheFirstAndLastPoint() {
		CameraPath path = new CameraPath(List.of(k(0, 64, 0, 0, 0), k(10, 70, 5, 45, -10), k(20, 64, 0, 90, 20)));
		CameraPath.Pose start = path.sample(0);
		CameraPath.Pose end = path.sample(1);
		assertEquals(0, start.x(), 1e-6);
		assertEquals(64, start.y(), 1e-6);
		assertEquals(20, end.x(), 1e-6);
		assertEquals(90, end.yaw(), 1e-3);
		assertEquals(20, end.pitch(), 1e-3);
	}

	@Test
	void passesThroughTheMiddlePoint() {
		CameraPath path = new CameraPath(List.of(k(0, 0, 0, 0, 0), k(10, 0, 0, 0, 0), k(10, 0, 10, 0, 0)));
		double best = Double.MAX_VALUE;
		for (int i = 0; i <= 1000; i++) {
			CameraPath.Pose p = path.sample(i / 1000.0);
			best = Math.min(best, Math.hypot(p.x() - 10, p.z()));
		}
		assertTrue(best < 0.05, "closest approach to the middle point was " + best);
	}

	@Test
	void movesAtAnEvenSpeed() {
		CameraPath path = new CameraPath(List.of(k(0, 0, 0, 0, 0), k(1, 0, 0, 0, 0), k(30, 0, 0, 0, 0)));
		double total = path.length();
		CameraPath.Pose prev = path.sample(0);
		for (int i = 1; i <= 20; i++) {
			CameraPath.Pose p = path.sample(i / 20.0);
			double step = Math.abs(p.x() - prev.x());
			assertEquals(total / 20, step, total / 20 * 0.15, "step " + i);
			prev = p;
		}
	}

	@Test
	void turnsTheShortWayAcrossMinus180() {
		CameraPath path = new CameraPath(List.of(k(0, 0, 0, 170, 0), k(10, 0, 0, -170, 0)));
		float mid = path.sample(0.5).yaw();
		float wrapped = ((mid % 360) + 360) % 360;
		assertEquals(180, wrapped, 2, "yaw in the middle should be about 180, was " + mid);
	}

	@Test
	void onePointJustHolds() {
		CameraPath path = new CameraPath(List.of(k(3, 4, 5, 10, 20)));
		CameraPath.Pose p = path.sample(0.7);
		assertEquals(3, p.x(), 1e-9);
		assertEquals(10, p.yaw(), 1e-6);
	}

	@Test
	void turningOnTheSpotStillAnimates() {
		CameraPath path = new CameraPath(List.of(k(0, 0, 0, 0, 0), k(0, 0, 0, 90, 0)));
		assertEquals(45, path.sample(0.5).yaw(), 5);
	}

	@Test
	void easingIsSlowAtTheEnds() {
		assertEquals(0, CameraPath.ease(0), 1e-9);
		assertEquals(1, CameraPath.ease(1), 1e-9);
		assertEquals(0.5, CameraPath.ease(0.5), 1e-9);
		assertTrue(CameraPath.ease(0.1) < 0.1);
	}
}
