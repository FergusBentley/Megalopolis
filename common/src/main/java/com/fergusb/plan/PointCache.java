package com.fergusb.plan;

public final class PointCache {
	public static final long MISSING = Long.MIN_VALUE;
	private static final int STRIPES = 64;

	private final long[] keys;
	private final long[] values;
	private final int mask;
	private final Object[] locks = new Object[STRIPES];

	public PointCache(int bits) {
		int n = 1 << bits;
		keys = new long[n];
		values = new long[n];
		mask = n - 1;
		java.util.Arrays.fill(values, MISSING);
		for (int i = 0; i < STRIPES; i++) locks[i] = new Object();
	}

	private int slot(long key) {
		return (int) (key ^ (key >>> 29)) & mask;
	}

	public long get(long key) {
		int s = slot(key);
		synchronized (locks[s & (STRIPES - 1)]) {
			return keys[s] == key ? values[s] : MISSING;
		}
	}

	public void put(long key, long value) {
		int s = slot(key);
		synchronized (locks[s & (STRIPES - 1)]) {
			keys[s] = key;
			values[s] = value;
		}
	}
}
