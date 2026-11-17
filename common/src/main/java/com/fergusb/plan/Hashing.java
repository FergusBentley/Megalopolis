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

	public static long pack(int x, int z) {
		return mix(((long) x << 32) | (z & 0xFFFFFFFFL));
	}

	public static int unpackX(long key) {
		return (int) (unmix(key) >> 32);
	}
	public static int unpackZ(long key) {
		return (int) unmix(key);
	}

	/** Inverse mix */
	static long unmix(long z) {
		z = z ^ (z >>> 31) ^ (z >>> 62);
		z *= 0x319642B2D24D8EC3L;
		z = z ^ (z >>> 27) ^ (z >>> 54);
		z *= 0x96DE1B173F119089L;
		return z ^ (z >>> 30) ^ (z >>> 60);
	}

	/** Maps a hash to [0, 1) */
	public static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}
}
