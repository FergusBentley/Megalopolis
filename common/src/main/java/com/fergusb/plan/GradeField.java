package com.fergusb.plan;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GradeField {
	public static final int GRID = 16;

	private final TerrainSampler terrain;
	private final Map<Long, TerrainSampler.Sample> raw = lru(16384);
	private final Map<Long, Double> smooth = lru(8192);

	public GradeField(TerrainSampler terrain) {
		this.terrain = terrain;
	}

	/** Smoothed grade at a block position */
	public double grade(double x, double z) {
		int i = (int) Math.floor(x / GRID), j = (int) Math.floor(z / GRID);
		double fx = x / GRID - i, fz = z / GRID - j;
		double a = node(i, j) + (node(i + 1, j) - node(i, j)) * fx;
		double b = node(i, j + 1) + (node(i + 1, j + 1) - node(i, j + 1)) * fx;
		return a + (b - a) * fz;
	}

	public int gradeY(int x, int z) {
		return (int) Math.round(grade(x, z));
	}

	public boolean water(double x, double z) {
		int i = (int) Math.round(x / GRID), j = (int) Math.round(z / GRID);
		return rawNode(i, j).water() || smallIsland(i, j);
	}

	/** Land patches of at most this many cells count as ocean */
	private static final int ISLAND_CELLS = 25;
	private final Map<Long, Boolean> islands = lru(16384);

	/** True if the land node at (i, j) belongs to a connected land patch smaller than ISLAND_CELLS */
	public boolean smallIsland(int i, int j) {
		if (rawNode(i, j).water()) return false;
		long key = key(i, j);
		synchronized (islands) {
			Boolean b = islands.get(key);
			if (b != null) return b;
		}
		java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<>();
		java.util.HashSet<Long> seen = new java.util.HashSet<>();
		queue.add(new long[] {i, j});
		seen.add(key);
		boolean small = true;
		while (!queue.isEmpty()) {
			long[] n = queue.poll();
			if (seen.size() > ISLAND_CELLS) {
				small = false;
				break;
			}
			int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] d : dirs) {
				int ni = (int) n[0] + d[0], nj = (int) n[1] + d[1];
				long nk = key(ni, nj);
				if (!seen.contains(nk) && !rawNode(ni, nj).water()) {
					seen.add(nk);
					queue.add(new long[] {ni, nj});
				}
			}
		}
		final boolean result = small && seen.size() <= ISLAND_CELLS;
		synchronized (islands) {
			if (result) for (long k : seen) islands.put(k, true);
			else islands.put(key, false);
		}
		return result;
	}

	public boolean smallIslandAt(double x, double z) {
		return smallIsland((int) Math.round(x / GRID), (int) Math.round(z / GRID));
	}

	private double node(int i, int j) {
		long key = key(i, j);
		synchronized (smooth) {
			Double v = smooth.get(key);
			if (v != null) return v;
		}
		double sum = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				sum += rawNode(i + di, j + dj).surfaceY();
			}
		}
		double v = sum / 9.0;
		synchronized (smooth) {
			smooth.put(key, v);
		}
		return v;
	}

	private TerrainSampler.Sample rawNode(int i, int j) {
		long key = key(i, j);
		synchronized (raw) {
			TerrainSampler.Sample s = raw.get(key);
			if (s != null) return s;
		}
		TerrainSampler.Sample s = terrain.sample(i * GRID, j * GRID);
		synchronized (raw) {
			raw.put(key, s);
		}
		return s;
	}

	private static long key(int i, int j) {
		return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
	}

	private static <V> Map<Long, V> lru(int max) {
		return new LinkedHashMap<>(256, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, V> e) {
				return size() > max;
			}
		};
	}
}
