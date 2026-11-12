package com.fergusb.plan;

public final class ValueNoise {
	private final long seed;
	private final double scale;
	private final int octaves;

	public ValueNoise(long seed, double scale, int octaves) {
		this.seed = seed;
		this.scale = scale;
		this.octaves = octaves;
	}

	public double sample(double x, double z) {
		double sum = 0, amp = 1, norm = 0, f = 1.0 / scale;
		for (int o = 0; o < octaves; o++) {
			sum += amp * single(x * f, z * f, o);
			norm += amp;
			amp *= 0.5;
			f *= 2;
		}
		return sum / norm;
	}

	private double single(double x, double z, int octave) {
		long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
		double u = fade(x - ix), v = fade(z - iz);
		double a = lattice(ix, iz, octave), b = lattice(ix + 1, iz, octave);
		double c = lattice(ix, iz + 1, octave), d = lattice(ix + 1, iz + 1, octave);
		return lerp(v, lerp(u, a, b), lerp(u, c, d));
	}

	private double lattice(long ix, long iz, int octave) {
		return Hashing.unit(Hashing.hash(seed, ix, iz, octave)) * 2 - 1;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}
}
