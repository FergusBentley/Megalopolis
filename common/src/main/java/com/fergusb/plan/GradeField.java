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
		return rawNode((int) Math.round(x / GRID), (int) Math.round(z / GRID)).water();
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
