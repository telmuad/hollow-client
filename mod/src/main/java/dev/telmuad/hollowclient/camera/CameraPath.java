package dev.telmuad.hollowclient.camera;

import java.util.ArrayList;
import java.util.List;

/**
 * A smooth camera path through a list of points (pure math, no Minecraft). Positions and angles follow a
 * centripetal Catmull-Rom curve that passes through every point, and the camera moves at a constant speed along it. Yaw is
 * unwrapped first, so going from 170° to -170° turns 20° instead of 340°.
 */
public final class CameraPath {
	/** A point of the path: where the camera is and where it looks. */
	public record Key(double x, double y, double z, float yaw, float pitch) {
	}

	/** A camera pose on the path. */
	public record Pose(double x, double y, double z, float yaw, float pitch) {
	}

	private static final int STEPS = 32;

	private final List<Key> keys = new ArrayList<>();
	/** Curve parameter (segment + fraction) and length so far, for each sample. */
	private final double[] params;
	private final double[] lengths;

	public CameraPath(List<Key> points) {
		if (points.isEmpty()) throw new IllegalArgumentException("A camera path needs at least one point");
		float yaw = points.get(0).yaw();
		for (int i = 0; i < points.size(); i++) {
			Key k = points.get(i);
			if (i > 0) yaw += wrap(k.yaw() - points.get(i - 1).yaw());
			else yaw = k.yaw();
			keys.add(new Key(k.x(), k.y(), k.z(), yaw, k.pitch()));
		}
		int segments = keys.size() - 1;
		int n = segments * STEPS + 1;
		params = new double[n];
		lengths = new double[n];
		double[] prev = null;
		for (int i = 0; i < n; i++) {
			double p = (double) i / STEPS;
			params[i] = p;
			double[] pos = position(p);
			lengths[i] = prev == null ? 0 : lengths[i - 1] + Math.sqrt(sq(pos[0] - prev[0]) + sq(pos[1] - prev[1]) + sq(pos[2] - prev[2]));
			prev = pos;
		}
	}

	public int size() {
		return keys.size();
	}

	/** Length of the path in blocks. */
	public double length() {
		return lengths[lengths.length - 1];
	}

	/** The pose at {@code t} (0 = first point, 1 = last), at a constant speed along the path. */
	public Pose sample(double t) {
		t = Math.max(0, Math.min(1, t));
		if (keys.size() == 1) return pose(0);
		double total = length();
		double p;
		if (total < 1e-4) {
			// the camera only turns: spread the time evenly over the points
			p = t * (keys.size() - 1);
		} else {
			double target = t * total;
			int lo = 0;
			int hi = lengths.length - 1;
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (lengths[mid] < target) lo = mid;
				else hi = mid;
			}
			double span = lengths[hi] - lengths[lo];
			double f = span < 1e-9 ? 0 : (target - lengths[lo]) / span;
			p = params[lo] + (params[hi] - params[lo]) * f;
		}
		return pose(p);
	}

	private Pose pose(double p) {
		double[] pos = position(p);
		int seg = Math.min(keys.size() - 2, (int) Math.floor(p));
		if (keys.size() == 1) {
			Key k = keys.get(0);
			return new Pose(k.x(), k.y(), k.z(), k.yaw(), k.pitch());
		}
		double u = p - seg;
		Key a = key(seg - 1);
		Key b = key(seg);
		Key c = key(seg + 1);
		Key d = key(seg + 2);
		double[] t = knots(a, b, c, d);
		float yaw = (float) centripetal(a.yaw(), b.yaw(), c.yaw(), d.yaw(), t, u);
		float pitch = (float) Math.max(-90, Math.min(90, centripetal(a.pitch(), b.pitch(), c.pitch(), d.pitch(), t, u)));
		return new Pose(pos[0], pos[1], pos[2], yaw, pitch);
	}

	private double[] position(double p) {
		if (keys.size() == 1) {
			Key k = keys.get(0);
			return new double[]{k.x(), k.y(), k.z()};
		}
		int seg = Math.min(keys.size() - 2, (int) Math.floor(p));
		double u = p - seg;
		Key a = key(seg - 1);
		Key b = key(seg);
		Key c = key(seg + 1);
		Key d = key(seg + 2);
		double[] t = knots(a, b, c, d);
		return new double[]{centripetal(a.x(), b.x(), c.x(), d.x(), t, u), centripetal(a.y(), b.y(), c.y(), d.y(), t, u),
				centripetal(a.z(), b.z(), c.z(), d.z(), t, u)};
	}

	/** Neighbouring points; the ends are mirrored so the path starts and ends without a kink. */
	private Key key(int i) {
		int last = keys.size() - 1;
		if (i < 0) return mirror(keys.get(0), keys.get(Math.min(1, last)));
		if (i > last) return mirror(keys.get(last), keys.get(Math.max(0, last - 1)));
		return keys.get(i);
	}

	private static Key mirror(Key end, Key inner) {
		return new Key(2 * end.x() - inner.x(), 2 * end.y() - inner.y(), 2 * end.z() - inner.z(),
				2 * end.yaw() - inner.yaw(), 2 * end.pitch() - inner.pitch());
	}

	/**
	 * Knot spacing for centripetal Catmull-Rom: the square root of the distance between points (turning counts a
	 * little too, so a camera that only turns still moves along). Unlike evenly spaced knots, this never overshoots
	 * or loops when points are unevenly far apart.
	 */
	private static double[] knots(Key a, Key b, Key c, Key d) {
		double t1 = Math.sqrt(gap(a, b));
		double t2 = t1 + Math.sqrt(gap(b, c));
		double t3 = t2 + Math.sqrt(gap(c, d));
		return new double[]{0, t1, t2, t3};
	}

	private static double gap(Key p, Key q) {
		double d = Math.sqrt(sq(p.x() - q.x()) + sq(p.y() - q.y()) + sq(p.z() - q.z()) + sq((p.yaw() - q.yaw()) / 45) + sq((p.pitch() - q.pitch()) / 45));
		return Math.max(1e-4, d);
	}

	/** Barry-Goldman evaluation of the segment between p1 and p2 at fraction u. */
	static double centripetal(double p0, double p1, double p2, double p3, double[] k, double u) {
		double t0 = k[0];
		double t1 = k[1];
		double t2 = k[2];
		double t3 = k[3];
		double t = t1 + (t2 - t1) * u;
		double a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1;
		double a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2;
		double a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3;
		double b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2;
		double b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3;
		return (t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2;
	}

	/** An angle difference brought into -180..180. */
	static float wrap(float degrees) {
		float d = degrees % 360f;
		if (d >= 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}

	private static double sq(double v) {
		return v * v;
	}

	/** Slow start and end (sine ease in and out). */
	public static double ease(double t) {
		return 0.5 - 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, t)));
	}
}
