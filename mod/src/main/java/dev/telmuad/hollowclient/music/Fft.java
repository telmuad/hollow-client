package dev.telmuad.hollowclient.music;

/** A small radix-2 FFT for the music visualizer (pure Java). */
public final class Fft {
	private Fft() {
	}

	/**
	 * Magnitude spectrum of {@code samples} (length a power of two) with a Hann window. Writes {@code n/2} values to
	 * {@code out}; bin {@code i} is the frequency {@code i * sampleRate / n}.
	 */
	public static void magnitudes(float[] samples, float[] out) {
		int n = samples.length;
		if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("Length must be a power of two");
		float[] re = new float[n];
		float[] im = new float[n];
		for (int i = 0; i < n; i++) {
			float w = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (n - 1));
			re[i] = samples[i] * w;
		}
		// bit reversal
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) j ^= bit;
			j ^= bit;
			if (i < j) {
				float t = re[i];
				re[i] = re[j];
				re[j] = t;
			}
		}
		for (int len = 2; len <= n; len <<= 1) {
			double ang = -2 * Math.PI / len;
			float wr = (float) Math.cos(ang);
			float wi = (float) Math.sin(ang);
			for (int i = 0; i < n; i += len) {
				float cr = 1f;
				float ci = 0f;
				for (int k = 0; k < len / 2; k++) {
					int a = i + k;
					int b = a + len / 2;
					float xr = re[b] * cr - im[b] * ci;
					float xi = re[b] * ci + im[b] * cr;
					re[b] = re[a] - xr;
					im[b] = im[a] - xi;
					re[a] += xr;
					im[a] += xi;
					float ncr = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = ncr;
				}
			}
		}
		int half = Math.min(out.length, n / 2);
		for (int i = 0; i < half; i++) out[i] = (float) Math.sqrt(re[i] * re[i] + im[i] * im[i]) * 2f / n;
	}

	/**
	 * Group a magnitude spectrum into {@code bands.length} bands spaced evenly in pitch (log frequency) from 40 Hz to
	 * 16 kHz, each 0..1 (a -60..0 dB scale).
	 */
	public static void bands(float[] mags, int sampleRate, int fftSize, float[] bands) {
		int count = bands.length;
		double lo = Math.log(40);
		double hi = Math.log(Math.min(16000, sampleRate / 2.0));
		for (int b = 0; b < count; b++) {
			double f0 = Math.exp(lo + (hi - lo) * b / count);
			double f1 = Math.exp(lo + (hi - lo) * (b + 1) / count);
			int i0 = Math.max(1, (int) Math.floor(f0 * fftSize / sampleRate));
			int i1 = Math.max(i0 + 1, (int) Math.ceil(f1 * fftSize / sampleRate));
			float peak = 0f;
			for (int i = i0; i < i1 && i < mags.length; i++) peak = Math.max(peak, mags[i]);
			double db = 20 * Math.log10(Math.max(1e-6, peak));
			bands[b] = (float) Math.max(0, Math.min(1, (db + 60) / 60));
		}
	}
}
