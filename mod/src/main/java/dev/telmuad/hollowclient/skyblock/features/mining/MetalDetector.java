package dev.telmuad.hollowclient.skyblock.features.mining;

import java.util.ArrayList;
import java.util.List;

/**
 * Guesses where the Mines of Divan treasure is from the metal detector's distance readings (shown on the action bar)
 * taken at different spots: the point whose distances to those spots best match the readings. It only ever reads
 * what the detector shows you. Plain Java.
 */
public final class MetalDetector {
	/** A reading: where you stood and the distance the detector showed. */
	public record Reading(double x, double y, double z, double dist) {
	}

	/** The best guess and how well it fits (root mean square of the misses, in blocks). */
	public record Guess(double x, double y, double z, double error, int readings) {
	}

	private static final int MAX = 40;
	private final List<Reading> readings = new ArrayList<>();

	/** Adds a reading; one taken on the same spot as the last replaces it. */
	public void add(Reading r) {
		if (!readings.isEmpty()) {
			Reading last = readings.get(readings.size() - 1);
			if (sq(last.x() - r.x()) + sq(last.y() - r.y()) + sq(last.z() - r.z()) < 0.25) {
				readings.set(readings.size() - 1, r);
				return;
			}
		}
		readings.add(r);
		if (readings.size() > MAX) readings.remove(0);
		// a reading that doesn't fit the others means a new treasure: start again from it
		// (only once the earlier readings pin a point down: along a line, many points fit)
		List<Reading> before = readings.subList(0, readings.size() - 1);
		if (spread(before)) {
			Guess g = solve(before);
			if (g != null && g.error() < 1.0) {
				double miss = Math.abs(Math.sqrt(sq(g.x() - r.x()) + sq(g.y() - r.y()) + sq(g.z() - r.z())) - r.dist());
				if (miss > 4) {
					readings.clear();
					readings.add(r);
				}
			}
		}
	}

	public void clear() {
		readings.clear();
	}

	public int count() {
		return readings.size();
	}

	/** Whether the readings are spread enough for a guess (four, not all along one line). */
	public boolean enough() {
		return spread(readings);
	}

	/** The guess, or null until there are enough readings. */
	public Guess guess() {
		return spread(readings) ? solve(readings) : null;
	}

	private static boolean spread(List<Reading> rs) {
		if (rs.size() < 4) return false;
		// horizontal spread in two directions: the smaller eigenvalue of the positions' covariance
		double mx = 0, mz = 0;
		for (Reading r : rs) {
			mx += r.x();
			mz += r.z();
		}
		mx /= rs.size();
		mz /= rs.size();
		double sxx = 0, szz = 0, sxz = 0;
		for (Reading r : rs) {
			sxx += sq(r.x() - mx);
			szz += sq(r.z() - mz);
			sxz += (r.x() - mx) * (r.z() - mz);
		}
		sxx /= rs.size();
		szz /= rs.size();
		sxz /= rs.size();
		double tr = sxx + szz, det = sxx * szz - sxz * sxz;
		double small = tr / 2 - Math.sqrt(Math.max(0, tr * tr / 4 - det));
		return small >= 0.5;
	}

	/**
	 * Least squares on the distances (Levenberg–Marquardt). Readings taken at about the same height can't tell above
	 * from below, so the guess is put under you: the treasure is buried.
	 */
	static Guess solve(List<Reading> rs) {
		if (rs.size() < 4) return null;
		double cx = 0, cy = 0, cz = 0, maxY = -1e9;
		for (Reading r : rs) {
			cx += r.x();
			cy += r.y();
			cz += r.z();
			maxY = Math.max(maxY, r.y());
		}
		cx /= rs.size();
		cy /= rs.size();
		cz /= rs.size();
		double[] best = null;
		double bestCost = Double.MAX_VALUE;
		for (double[] start : new double[][]{{cx, cy - 3, cz}, {cx + 5, cy - 3, cz}, {cx, cy - 3, cz + 5}, {cx - 5, cy - 8, cz - 5}}) {
			double[] p = fit(rs, start);
			if (p[1] > maxY + 0.5) p[1] = 2 * cy - p[1];
			double c = cost(rs, p);
			if (c < bestCost) {
				bestCost = c;
				best = p;
			}
		}
		return new Guess(best[0], best[1], best[2], Math.sqrt(bestCost / rs.size()), rs.size());
	}

	private static double[] fit(List<Reading> rs, double[] start) {
		double[] p = start.clone();
		double lambda = 1e-2;
		double cost = cost(rs, p);
		for (int it = 0; it < 100; it++) {
			double[][] a = new double[3][3];
			double[] g = new double[3];
			for (Reading r : rs) {
				double dx = p[0] - r.x(), dy = p[1] - r.y(), dz = p[2] - r.z();
				double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (d < 1e-6) continue;
				double[] j = {dx / d, dy / d, dz / d};
				double res = d - r.dist();
				for (int i = 0; i < 3; i++) {
					g[i] += j[i] * res;
					for (int k = 0; k < 3; k++) a[i][k] += j[i] * j[k];
				}
			}
			for (int i = 0; i < 3; i++) a[i][i] += lambda * (1 + a[i][i]);
			double[] step = solve3(a, new double[]{-g[0], -g[1], -g[2]});
			if (step == null) break;
			double[] q = {p[0] + step[0], p[1] + step[1], p[2] + step[2]};
			double c = cost(rs, q);
			if (c < cost) {
				p = q;
				lambda = Math.max(1e-7, lambda / 3);
				if (cost - c < 1e-10) {
					cost = c;
					break;
				}
				cost = c;
			} else {
				lambda *= 4;
				if (lambda > 1e6) break;
			}
		}
		return p;
	}

	private static double cost(List<Reading> rs, double[] p) {
		double c = 0;
		for (Reading r : rs) c += sq(Math.sqrt(sq(p[0] - r.x()) + sq(p[1] - r.y()) + sq(p[2] - r.z())) - r.dist());
		return c;
	}

	private static double[] solve3(double[][] a, double[] b) {
		double det = det(a);
		if (Math.abs(det) < 1e-12) return null;
		double[] out = new double[3];
		for (int c = 0; c < 3; c++) {
			double[][] m = {a[0].clone(), a[1].clone(), a[2].clone()};
			for (int r = 0; r < 3; r++) m[r][c] = b[r];
			out[c] = det(m) / det;
		}
		return out;
	}

	private static double det(double[][] m) {
		return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
				- m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
				+ m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
	}

	private static double sq(double v) {
		return v * v;
	}
}
