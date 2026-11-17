package com.fergusb.plan;

public final class GradeField {
	public static final int GRID = 16;

	private final TerrainSampler terrain;
	private final PointCache raw = new PointCache(16);
	private final PointCache smooth = new PointCache(16);

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
	public static final int ISLAND_POINTS = 400;
	public static final int PATCH_CAP = 800;
	private static final int STEP = 4;
	private final PointCache patches = new PointCache(20);
	private final PointCache nodePatches = new PointCache(16);
	private static final int BIG_NODES = 64;

	public int patchSize(double x, double z) {
		int i = (int) Math.round(x / STEP), j = (int) Math.round(z / STEP);
		long key = key(i, j);
		long hit = patches.get(key);
		if (hit != PointCache.MISSING) return (int) hit;
		if (wet(i, j)) {
			patches.put(key, 0);
			return 0;
		}
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		java.util.HashSet<Long> seen = new java.util.HashSet<>();
		queue.add(new int[] {i, j});
		seen.add(key);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		boolean big = false;
		while (!queue.isEmpty() && seen.size() < PATCH_CAP && !big) {
			int[] n = queue.poll();
			if ((n[0] & 3) == 0 && (n[1] & 3) == 0 && bigNode(n[0] >> 2, n[1] >> 2)) {
				big = true;
				break;
			}
			for (int[] d : dirs) {
				int ai = n[0] + d[0], aj = n[1] + d[1];
				long ak = key(ai, aj);
				if (!seen.contains(ak) && !wet(ai, aj)) {
					seen.add(ak);
					queue.add(new int[] {ai, aj});
				}
			}
		}
		int size = big ? PATCH_CAP : Math.min(seen.size(), PATCH_CAP);
		for (long k : seen) patches.put(k, size);
		return size;
	}

	private boolean bigNode(int i, int j) {
		if (rawNode(i, j).water()) return false;
		long key = key(i, j);
		long hit = nodePatches.get(key);
		if (hit != PointCache.MISSING) return hit != 0;
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		java.util.HashSet<Long> seen = new java.util.HashSet<>();
		queue.add(new int[] {i, j});
		seen.add(key);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty() && seen.size() < BIG_NODES) {
			int[] n = queue.poll();
			for (int[] d : dirs) {
				int ai = n[0] + d[0], aj = n[1] + d[1];
				long ak = key(ai, aj);
				if (!seen.contains(ak) && !rawNode(ai, aj).water()) {
					seen.add(ak);
					queue.add(new int[] {ai, aj});
				}
			}
		}
		boolean result = seen.size() >= BIG_NODES;
		for (long k : seen) nodePatches.put(k, result ? 1 : 0);
		return result;
	}

	private boolean wet(int i, int j) {
		return terrain.sample(i * STEP, j * STEP).water();
	}

	public boolean smallIslandAt(double x, double z) {
		int n = patchSize(x, z);
		return n > 0 && n <= ISLAND_POINTS;
	}

	public boolean smallIsland(int i, int j) {
		return smallIslandAt(i * GRID, j * GRID);
	}

	private double node(int i, int j) {
		long key = key(i, j);
		long hit = smooth.get(key);
		if (hit != PointCache.MISSING) return Double.longBitsToDouble(hit);
		double sum = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				sum += rawNode(i + di, j + dj).surfaceY();
			}
		}
		double v = sum / 9.0;
		smooth.put(key, Double.doubleToRawLongBits(v));
		return v;
	}

	private TerrainSampler.Sample rawNode(int i, int j) {
		long key = key(i, j);
		long hit = raw.get(key);
		if (hit != PointCache.MISSING) return new TerrainSampler.Sample((int) (hit >> 32), (int) hit);
		TerrainSampler.Sample s = terrain.sample(i * GRID, j * GRID);
		raw.put(key, ((long) s.groundY() << 32) | (s.surfaceY() & 0xFFFFFFFFL));
		return s;
	}

	private static long key(int i, int j) {
		return Hashing.pack(i, j);
	}
}
