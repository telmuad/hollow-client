package dev.telmuad.hollowclient.waypoints;

/** One saved place in a singleplayer world. */
public final class Waypoint {
	/** Colors offered in the editor (the first one follows the theme accent). */
	public static final int[] PALETTE = {0, 0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF22D3EE, 0xFF3B82F6, 0xFFA855F7, 0xFFEC4899, 0xFFF5F5F5};

	public String name;
	public double x;
	public double y;
	public double z;
	public String dimension;
	/** ARGB, or 0 for the theme accent. */
	public int color;
	public boolean visible = true;
	/** Made automatically where you died. */
	public boolean death;

	public Waypoint(String name, double x, double y, double z, String dimension, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.color = color;
	}

	public double distanceTo(double px, double py, double pz) {
		double dx = x - px;
		double dy = y - py;
		double dz = z - pz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The next palette color after this one. */
	public int nextColor() {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == color) return PALETTE[(i + 1) % PALETTE.length];
		}
		return PALETTE[0];
	}
}
