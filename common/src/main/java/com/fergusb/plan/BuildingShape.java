package com.fergusb.plan;

public final class BuildingShape {
	private BuildingShape() {}

	/** Local cell of a world column */
	public static int[] local(Obb fp, int x, int z) {
		if (!fp.contains(x, z)) return null;
		int lu = (int) Math.round(fp.u(x, z) + fp.halfU);
		int lv = (int) Math.round(fp.v(x, z) + fp.halfV);
		return new int[] {Math.max(0, Math.min(fp.widthU() - 1, lu)), Math.max(0, Math.min(fp.depthV() - 1, lv))};
	}

	public static final int MIN_WIDTH = 4;

	public static boolean insideAt(Lot lot, int x, int z, int floor) {
		Obb fp = lot.footprint();
		if (fp == null || x < fp.minX || x > fp.maxX || z < fp.minZ || z > fp.maxZ) return false;
		int inset = lot.mask() != null ? 0 : inset(lot, floor);
		boolean[] g = grid(lot, fp, inset);
		return g[(x - fp.minX) * (fp.maxZ - fp.minZ + 1) + (z - fp.minZ)];
	}

	public static boolean inside(Lot lot, int lu, int lv, int floor) {
		return lot.mask() != null ? lot.mask().local(lu, lv) : insideInset(lot, lu, lv, inset(lot, floor));
	}

	private static int inset(Lot lot, int floor) {
		return inset(lot.spec(), lot.footprint().widthU(), lot.footprint().depthV(),
				Math.max(0, floor - lot.lift()), Math.max(1, lot.floors() - lot.lift()));
	}

	private static boolean insideInset(Lot lot, int lu, int lv, int inset) {
		int w = lot.footprint().widthU(), d = lot.footprint().depthV();
		int u = lu - inset, v = lv - inset, iw = w - 2 * inset, id = d - 2 * inset;
		if (u < 0 || v < 0 || u >= iw || v >= id) return false;
		return insideFootprint(lot.spec(), u, v, iw, id);
	}

	private static final class Shapes {
		final Lot lot;
		final java.util.concurrent.ConcurrentHashMap<Integer, boolean[]> byInset = new java.util.concurrent.ConcurrentHashMap<>();
		volatile int[] uRange;
		volatile int[] starts;

		Shapes(Lot lot) {
			this.lot = lot;
		}
	}

	private static Shapes shapes(Lot lot, Obb fp) {
		Object c = fp.shapeCache;
		if (c instanceof Shapes sh && sh.lot == lot) return sh;
		Shapes shapes = new Shapes(lot);
		fp.shapeCache = shapes;
		return shapes;
	}

	private static boolean[] grid(Lot lot, Obb fp, int inset) {
		Shapes shapes = shapes(lot, fp);
		boolean[] g = shapes.byInset.get(inset);
		if (g == null) {
			g = buildGrid(lot, fp, inset, shapes);
			boolean[] prev = shapes.byInset.putIfAbsent(inset, g);
			if (prev != null) g = prev;
		}
		return g;
	}

	private static boolean[] buildGrid(Lot lot, Obb fp, int inset, Shapes shapes) {
		int nx = fp.maxX - fp.minX + 1, nz = fp.maxZ - fp.minZ + 1;
		int[] lus = new int[nx * nz];
		int[][] sum = new int[nx + 1][nz + 1];
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < nz; j++) {
				int[] l = local(fp, fp.minX + i, fp.minZ + j);
				lus[i * nz + j] = l == null ? -1 : l[0];
				boolean in = l != null && (lot.mask() != null ? lot.mask().local(l[0], l[1]) : insideInset(lot, l[0], l[1], inset));
				sum[i + 1][j + 1] = (in ? 1 : 0) + sum[i][j + 1] + sum[i + 1][j] - sum[i][j];
			}
		}
		int k = MIN_WIDTH;
		boolean[] g = new boolean[nx * nz];
		for (int i = 0; i + k <= nx; i++) {
			for (int j = 0; j + k <= nz; j++) {
				if (sum[i + k][j + k] - sum[i][j + k] - sum[i + k][j] + sum[i][j] != k * k) continue;
				for (int a = i; a < i + k; a++) {
					for (int b = j; b < j + k; b++) g[a * nz + b] = true;
				}
			}
		}
		BuildingSpec s = lot.spec();
		if (s == null || s.unitWidth() <= 0) return g;
		if (inset != 0) return g;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int c = 0; c < g.length; c++) {
			if (!g[c]) continue;
			lo = Math.min(lo, lus[c]);
			hi = Math.max(hi, lus[c]);
		}
		if (lo > hi) {
			lo = 0;
			hi = fp.widthU() - 1;
		}
		shapes.uRange = new int[] {lo, hi};
		int width = hi - lo + 1;
		java.util.List<Integer> starts = new java.util.ArrayList<>();
		for (int u = 0; u < Math.max(1, width / s.unitWidth()); u++) starts.add(lo + u * s.unitWidth());
		while (starts.size() > 1) {
			int bad = narrowUnit(g, lus, nx, nz, starts);
			if (bad < 0) break;
			starts.remove(bad == 0 ? 1 : bad); // into the unit before it
		}
		shapes.starts = starts.stream().mapToInt(Integer::intValue).toArray();
		return g;
	}

	private static int narrowUnit(boolean[] g, int[] lus, int nx, int nz, java.util.List<Integer> starts) {
		int k = MIN_WIDTH;
		int[] unit = new int[g.length];
		boolean[] party = new boolean[g.length];
		for (int c = 0; c < g.length; c++) {
			unit[c] = unitOf(starts, lus[c]);
			party[c] = unit[c] > 0 && lus[c] == starts.get(unit[c]);
		}
		boolean[] covered = new boolean[g.length];
		for (int i = 0; i + k <= nx; i++) {
			for (int j = 0; j + k <= nz; j++) {
				int lowest = Integer.MAX_VALUE;
				boolean all = true;
				for (int a = i; a < i + k && all; a++) {
					for (int b = j; b < j + k; b++) {
						if (!g[a * nz + b]) {
							all = false;
							break;
						}
						lowest = Math.min(lowest, unit[a * nz + b]);
					}
				}
				if (!all) continue;
				for (int lowUnit : new int[] {lowest, lowest - 1}) {
					boolean one = true;
					for (int a = i; a < i + k && one; a++) {
						for (int b = j; b < j + k; b++) {
							int c = a * nz + b;
							if (unit[c] != lowUnit && !(party[c] && unit[c] == lowUnit + 1)) {
								one = false;
								break;
							}
						}
					}
					if (!one) continue;
					for (int a = i; a < i + k; a++) {
						for (int b = j; b < j + k; b++) covered[a * nz + b] = true;
					}
				}
			}
		}
		for (int c = 0; c < g.length; c++) {
			if (g[c] && !covered[c]) return unit[c] > 0 && party[c] ? unit[c] : unit[c];
		}
		return -1;
	}

	private static int unitOf(java.util.List<Integer> starts, int lu) {
		int u = 0;
		while (u + 1 < starts.size() && lu >= starts.get(u + 1)) u++;
		return u;
	}

	/** Townhouse unit */
	public static int unit(Lot lot, int lu) {
		int[] st = starts(lot);
		int u = 0;
		while (u + 1 < st.length && lu >= st[u + 1]) u++;
		return u;
	}

	public static int[] unitSpan(Lot lot, int unit) {
		int[] st = starts(lot);
		int u = Math.max(0, Math.min(st.length - 1, unit));
		return new int[] {st[u], u + 1 < st.length ? st[u + 1] - 1 : uRange(lot)[1]};
	}

	private static int[] starts(Lot lot) {
		Obb fp = lot.footprint();
		Shapes shapes = shapes(lot, fp);
		if (shapes.starts == null) grid(lot, fp, 0);
		return shapes.starts != null ? shapes.starts : new int[] {0};
	}

	private static int[] uRange(Lot lot) {
		Obb fp = lot.footprint();
		Shapes shapes = shapes(lot, fp);
		if (shapes.uRange == null) grid(lot, fp, 0);
		int[] r = shapes.uRange;
		return r != null ? r : new int[] {0, fp.widthU() - 1};
	}

	public static int unit(BuildingSpec s, int lu, int w) {
		if (s.unitWidth() <= 0) return 0;
		int units = Math.max(1, w / s.unitWidth());
		return Math.min(lu / s.unitWidth(), units - 1);
	}

	public static int[] unitSpan(BuildingSpec s, int unit, int w) {
		int units = Math.max(1, w / s.unitWidth());
		int start = unit * s.unitWidth();
		int end = unit == units - 1 ? w - 1 : start + s.unitWidth() - 1;
		return new int[] {start, end};
	}

	static int inset(BuildingSpec s, int w, int d, int floor, int floors) {
		int minDim = Math.min(w, d);
		int maxInset = Math.max(0, (minDim - 7) / 2); // never thinner than 7 blocks
		int inset = switch (s.massing()) {
			case STRAIGHT -> 0;
			case SETBACKS -> {
				int tier = Math.max(4, floors / 4);
				yield (floor / tier) * 2;
			}
			case PODIUM_TOWER -> {
				int podium = Math.min(4, Math.max(2, floors / 5));
				yield floor < podium ? 0 : Math.max(2, (int) (minDim * 0.22));
			}
			case TAPERED -> (int) Math.round((double) floor / Math.max(1, floors) * minDim * 0.25);
		};
		return Math.min(inset, maxInset);
	}

	private static boolean insideFootprint(BuildingSpec s, int u, int v, int w, int d) {
		long h = s.seed();
		switch (s.shape()) {
			case RECT:
				return true;
			case L: {
				int nu = (int) (w * (0.45 + 0.15 * Hashing.unit(Hashing.hash(h, 11))));
				int nv = (int) (d * (0.45 + 0.15 * Hashing.unit(Hashing.hash(h, 12))));
				int corner = (int) (Hashing.unit(Hashing.hash(h, 13)) * 4);
				boolean hu = (corner & 1) == 0 ? u >= nu : u < w - nu;
				boolean hv = (corner & 2) == 0 ? v >= nv : v < d - nv;
				return !(hu && hv);
			}
			case U: {
				int slotLo = w / 3, slotHi = w - w / 3;
				int depth = (int) (d * 0.5);
				boolean back = Hashing.unit(Hashing.hash(h, 14)) < 0.5;
				boolean inSlot = u >= slotLo && u < slotHi && (back ? v >= d - depth : v < depth);
				return !inSlot;
			}
			case COURTYARD: {
				int ring = Math.max(4, Math.min(w, d) / 3);
				return u < ring || v < ring || u >= w - ring || v >= d - ring;
			}
			case ROUND: {
				double a = (u + 0.5 - w / 2.0) / (w / 2.0), b = (v + 0.5 - d / 2.0) / (d / 2.0);
				return a * a + b * b <= 1.0;
			}
			case CHAMFERED: {
				int c = Math.max(2, Math.min(w, d) / 4);
				return u + v >= c && (w - 1 - u) + v >= c && u + (d - 1 - v) >= c && (w - 1 - u) + (d - 1 - v) >= c;
			}
			default:
				return true;
		}
	}
}
