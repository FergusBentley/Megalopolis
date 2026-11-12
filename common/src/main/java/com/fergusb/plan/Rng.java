package com.fergusb.plan;

public final class Rng {
	private long state;

	public Rng(long seed) {
		this.state = seed;
	}

	public long nextLong() {
		state += 0x9E3779B97F4A7C15L;
		return Hashing.mix(state);
	}

	public double nextDouble() {
		return Hashing.unit(nextLong());
	}

	public int nextInt(int lo, int hi) {
		if (hi <= lo) return lo;
		return lo + (int) Math.floor(nextDouble() * (hi - lo + 1));
	}

	public double range(double lo, double hi) {
		return lo + (hi - lo) * nextDouble();
	}

	public boolean chance(double p) {
		return nextDouble() < p;
	}
}
