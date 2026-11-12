package com.fergusb.plan;

/**
 * District fields
 * downtown: 0 = low-rise outskirts, 1 = dense towers
 * condition: 0 = run down, 1 = pristine. Loosely correlated with downtown
 */
public final class DistrictFields {
	private final ValueNoise downtownNoise;
	private final ValueNoise conditionNoise;

	public DistrictFields(long seed) {
		this.downtownNoise = new ValueNoise(Hashing.hash(seed, 101), 1400, 3);
		this.conditionNoise = new ValueNoise(Hashing.hash(seed, 102), 450, 3);
	}

	public double downtown(double x, double z) {
		return clamp01(0.5 + downtownNoise.sample(x, z) * 1.7);
	}

	public double condition(double x, double z) {
		double d = downtown(x, z);
		return clamp01(0.5 + conditionNoise.sample(x, z) * 1.5 + (d - 0.5) * 0.5);
	}

	static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
