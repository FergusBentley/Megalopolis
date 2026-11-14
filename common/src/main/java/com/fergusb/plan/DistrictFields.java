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
		this.downtownNoise = new ValueNoise(Hashing.hash(seed, 101), 1400, 3);
		this.conditionNoise = new ValueNoise(Hashing.hash(seed, 102), 260, 3);
		this.organicNoise = new ValueNoise(Hashing.hash(seed, 103), 1100, 3);
		this.themeNoise = new ValueNoise(Hashing.hash(seed, 104), 700, 2);
	}

	public double organic(double x, double z) {
		return clamp01(0.5 + organicNoise.sample(x, z) * 1.6 - (downtown(x, z) - 0.5) * 0.8);
	}

	public double downtown(double x, double z) {
		return clamp01(0.5 + downtownNoise.sample(x, z) * 1.7);
	}

	public double condition(double x, double z) {
		double d = downtown(x, z);
		return clamp01(1.1 + conditionNoise.sample(x, z) * 1.64 + (d - 0.5) * 0.1);
	}

	public int theme(double x, double z) {
		double t = clamp01(0.5 + themeNoise.sample(x, z) * 1.8);
		return Math.min(4, (int) (t * 5));
	}

	static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
