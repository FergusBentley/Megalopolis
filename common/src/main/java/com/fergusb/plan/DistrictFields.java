package com.fergusb.plan;

/**
 * District fields
 * downtown: 0 = low-rise outskirts, 1 = dense towers
 * condition: 0 = run down, 1 = pristine. Loosely correlated with downtown
 */
public final class DistrictFields {
	private final ValueNoise downtownNoise;
	private final ValueNoise conditionNoise;
	private final ValueNoise organicNoise;
	private final ValueNoise themeNoise;

	public DistrictFields(long seed) {
		this.downtownNoise = new ValueNoise(Hashing.hash(seed, 101), 900, 3);
		this.conditionNoise = new ValueNoise(Hashing.hash(seed, 102), 260, 3);
		this.organicNoise = new ValueNoise(Hashing.hash(seed, 103), 1100, 3);
		this.themeNoise = new ValueNoise(Hashing.hash(seed, 104), 700, 2);
	}

	private static final double[] THEME_CUTS = {-0.306, -0.094, 0.098, 0.310};

	public int theme(double x, double z) {
		// Cut at the noise's quintiles, so each theme covers about a fifth of the land. (Stretching
		// the noise to 0..1 instead piled a third of it into each of the two end themes.)
		double n = themeNoise.sample(x, z);
		int t = 0;
		for (double cut : THEME_CUTS) if (n >= cut) t++;
		return t;
	}

	public double organic(double x, double z) {
		return clamp01(0.45 + organicNoise.sample(x, z) * 1.0 - (downtown(x, z) - 0.5) * 0.6);
	}

	public double downtown(double x, double z) {
		return clamp01(0.5 + downtownNoise.sample(x, z) * 1.15);
	}

	public double condition(double x, double z) {
		double d = downtown(x, z);
		return clamp01(1.1 + conditionNoise.sample(x, z) * 1.64 + (d - 0.5) * 0.1);
	}

	static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
