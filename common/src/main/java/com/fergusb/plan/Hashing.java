package com.fergusb.plan;

public final class Hashing {
	private Hashing() {}

	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static long hash(long seed, long... parts) {
		long h = mix(seed + 0x9E3779B97F4A7C15L);
		for (long p : parts) {
			h = mix(h ^ (p + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2)));
		}
		return h;
	}

	// Maps a hash to [0, 1)
	public static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}
}
