package com.fergusb.world;

import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Hashing;
import com.fergusb.plan.Lot;
import com.fergusb.plan.Obb;

import java.util.*;

final class ParkPaths {

	record Plan(int x0, int z0, int w, int d, BitSet path, BitSet lamp, double level, float[] height) {

		double height(int x, int z) {
			int i = index(x, z);
			return i < 0 || height == null || Float.isNaN(height[i]) ? Double.NaN : height[i];
		}

		boolean path(int x, int z) {
			int i = index(x, z);
			return i >= 0 && path.get(i);
		}

		boolean lamp(int x, int z) {
			int i = index(x, z);
			return i >= 0 && lamp.get(i);
		}

		private int index(int x, int z) {
			int lx = x - x0, lz = z - z0;
			return lx < 0 || lz < 0 || lx >= w || lz >= d ? -1 : lx * d + lz;
		}
	}

	private static final Plan NONE = new Plan(0, 0, 0, 0, new BitSet(), new BitSet(), Double.NaN, null);
	private static final Map<Lot, Plan> PLANS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final Map<Lot, Plan> GROUNDS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final double HALF = 0.8; // half path width
	private static final int LAMP_SPACING = 14;
	private static final int MAX_CELLS = 60000;
	private static final int GAP_CROSS = 3;
	private static final int EXT = 6;
	private static final int SOLO_PATHS = 1500;

	private ParkPaths() {}

	static Plan of(CityPlanner planner, Lot lot) {
		Plan p = PLANS.get(lot);
		if (p == null) {
			Map<Lot, Plan> made = plan(planner, lot);
			PLANS.putAll(made);
			p = made.getOrDefault(lot, NONE);
		}
		return p;
	}

	static double ground(CityPlanner planner, Lot lot, int x, int z) {
		Plan p = PLANS.get(lot);
		if (p == null) p = GROUNDS.get(lot);
		if (p == null) {
			Map<Lot, Plan> made = plan(planner, lot, true);
			GROUNDS.putAll(made);
			p = made.getOrDefault(lot, NONE);
		}
		return p.height(x, z);
	}

	private static Map<Lot, Plan> plan(CityPlanner planner, Lot lot) {
		return plan(planner, lot, false);
	}

	private static Map<Lot, Plan> plan(CityPlanner planner, Lot lot, boolean groundOnly) {
		Map<Lot, Plan> out = new IdentityHashMap<>();
		Obb lb = lot.bounds();
		int sx0 = Integer.MIN_VALUE, sz0 = 0;
		for (int x = lb.minX; x <= lb.maxX && sx0 == Integer.MIN_VALUE; x++) {
			for (int z = lb.minZ; z <= lb.maxZ; z++) {
				if (planner.classify(x, z).lot() == lot) {
					sx0 = x;
					sz0 = z;
					break;
				}
			}
		}
		if (sx0 == Integer.MIN_VALUE) {
			out.put(lot, NONE);
			return out;
		}
		java.util.Set<Lot> group = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		java.util.Set<Long> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		List<int[]> cells = new ArrayList<>();
		queue.add(new int[] {sx0, sz0, 0});
		seen.add(Hashing.pack(sx0, sz0));
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty() && cells.size() < MAX_CELLS) {
			int[] c = queue.poll();
			CityPlanner.Cell cell = planner.classify(c[0], c[1]);
			int run;
			if (cell.kind() == CityPlanner.Kind.LOT && cell.lot().use() == Lot.Use.PARK) {
				group.add(cell.lot());
				run = 0;
			} else if (cell.kind() == CityPlanner.Kind.GAP && c[2] < GAP_CROSS) {
				run = c[2] + 1;
			} else {
				continue;
			}
			cells.add(c);
			for (int[] dv : dirs) {
				int nx = c[0] + dv[0], nz = c[1] + dv[1];
				if (seen.add(Hashing.pack(nx, nz))) queue.add(new int[] {nx, nz, run});
			}
		}
		java.util.Set<Long> mine = new java.util.HashSet<>();
		for (int[] c : cells) mine.add(Hashing.pack(c[0], c[1]));
		List<double[]> edge = new ArrayList<>();
		for (int[] c : cells) {
			for (int[] dv : dirs) {
				int nx = c[0] + dv[0], nz = c[1] + dv[1];
				if (mine.contains(Hashing.pack(nx, nz))) continue;
				CityPlanner.Cell n = planner.classify(nx, nz);
				if (n.isRoad()) {
					CityPlanner.RoadPoint rp = planner.roadPoint(n, nx, nz);
					if (rp.state() == com.fergusb.plan.RoadProfile.State.GROUND) edge.add(new double[] {c[0], c[1], rp.height()});
				} else if (n.kind() == CityPlanner.Kind.LOT && n.lot().use() == Lot.Use.BUILDING) {
					edge.add(new double[] {c[0], c[1], n.lot().baseY()});
				}
			}
		}
		double level;
		if (!edge.isEmpty()) {
			double[] hs = new double[edge.size()];
			for (int i = 0; i < hs.length; i++) hs[i] = edge.get(i)[2];
			java.util.Arrays.sort(hs);
			level = Math.round(hs[hs.length / 2] * 2) / 2.0;
		} else {
			double g = 0;
			for (int[] c : cells) g += planner.grade().grade(c[0], c[1]);
			level = Math.round(g / cells.size() * 2) / 2.0;
		}
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (int[] c : cells) {
			x0 = Math.min(x0, c[0]);
			z0 = Math.min(z0, c[1]);
			x1 = Math.max(x1, c[0]);
			z1 = Math.max(z1, c[1]);
		}
		x0 -= EXT;
		z0 -= EXT;
		x1 += EXT;
		z1 += EXT;
		int w = x1 - x0 + 1, d = z1 - z0 + 1;
		float[] height = grade(cells, edge, level, x0, z0, d, w);
		if (groundOnly || group.size() < 2 && cells.size() < SOLO_PATHS) {
			Plan flat = new Plan(x0, z0, w, d, new BitSet(), new BitSet(), level, height);
			for (Lot l : group) out.put(l, flat);
			out.put(lot, flat);
			return out;
		}
		BitSet in = new BitSet(w * d);
		int area = 0;
		double sx = 0, sz = 0;
		for (int[] c : cells) {
			if (planner.grade().water(c[0], c[1])) continue;
			in.set((c[0] - x0) * d + (c[1] - z0));
			area++;
			sx += c[0];
			sz += c[1];
		}
		BitSet path = new BitSet(w * d), lamp = new BitSet(w * d);
		Plan plan = new Plan(x0, z0, w, d, path, lamp, level, height);
		for (Lot l : group) out.put(l, plan);
		if (area < 300) return out;
		long seed = Hashing.hash(planner.seed(), 93, Math.round(sx / area), Math.round(sz / area));

		List<int[]> gates = new ArrayList<>(), doors = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!in.get((x - x0) * d + (z - z0))) continue;
				for (int[] dv : dirs) {
					int nx = x + dv[0], nz = z + dv[1];
					boolean inside = nx >= x0 && nz >= z0 && nx <= x1 && nz <= z1 && in.get((nx - x0) * d + (nz - z0));
					if (inside) continue;
					boolean found = false;
					for (int k = 1; k <= 5 && !found; k++) {
						int px = x + dv[0] * k, pz = z + dv[1] * k;
						CityPlanner.Cell c = planner.classify(px, pz);
						if (c.isRoad()) {
							if (Math.floorMod(x + z, 2) == 0) gates.add(new int[] {x, z, dv[0], dv[1], k - 1});
							found = true;
						} else if (c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.BUILDING
								&& !com.fergusb.plan.BuildingShape.insideAt(c.lot(), px, pz, 0)) {
							continue;
						} else if (c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.BUILDING) {
							BuildingPlacer.DoorCell dc = BuildingPlacer.doorPlan(planner, c.lot()).get(Hashing.pack(px, pz));
							if (dc != null && dc.door() && dc.outward().getStepX() == -dv[0] && dc.outward().getStepZ() == -dv[1]) {
								doors.add(new int[] {x, z, dv[0], dv[1], k - 1});
							}
							break;
						}
					}
					if (found) break;
				}
			}
		}
		if (gates.isEmpty() && doors.isEmpty()) return out;
		int want = area > 5000 ? 4 : area > 1600 ? 3 : 2;
		List<int[]> chosen = new ArrayList<>(doors);
		if (!gates.isEmpty()) {
			List<int[]> streetGates = new ArrayList<>();
			streetGates.add(gates.get((int) (Hashing.unit(Hashing.hash(seed, 1)) * gates.size())));
			while (streetGates.size() < want) {
				int[] best = null;
				double bestD = 0;
				for (int[] g : gates) {
					double m = Double.MAX_VALUE;
					for (int[] c : streetGates) m = Math.min(m, Math.hypot(g[0] - c[0], g[1] - c[1]));
					for (int[] c : doors) m = Math.min(m, Math.hypot(g[0] - c[0], g[1] - c[1]));
					if (m > bestD) {
						bestD = m;
						best = g;
					}
				}
				if (best == null || bestD < 16) break;
				streetGates.add(best);
			}
			chosen.addAll(streetGates);
		}
		for (int[] g : chosen) {
			for (int k = 1; k <= g[4]; k++) {
				int px = g[0] + g[2] * k, pz = g[1] + g[3] * k;
				path.set((px - x0) * d + (pz - z0));
			}
		}

		List<double[][]> lines = new ArrayList<>();
		double cx = sx / area, cz = sz / area;
		if (doors.isEmpty() && Hashing.unit(Hashing.hash(seed, 2)) < 0.25) {
			lines.add(new double[][] {{x0, cz}, {x1, cz}});
			lines.add(new double[][] {{cx, z0}, {cx, z1}});
		} else {
			int[] hub = null;
			double hd = Double.MAX_VALUE;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (!in.get((x - x0) * d + (z - z0))) continue;
					double dd = Math.hypot(x - cx, z - cz);
					if (dd < hd) {
						hd = dd;
						hub = new int[] {x, z};
					}
				}
			}
			for (int k = 0; k < chosen.size(); k++) lines.add(meander(chosen.get(k), hub, Hashing.hash(seed, 3 + k)));
		}
		for (double[][] line : lines) {
			double run = 0;
			for (int i = 0; i + 1 < line.length; i++) {
				double ax = line[i][0], az = line[i][1], bx = line[i + 1][0], bz = line[i + 1][1];
				double len = Math.hypot(bx - ax, bz - az);
				for (double t = 0; t <= len; t += 0.5) {
					double px = ax + (bx - ax) * t / Math.max(len, 1e-6), pz = az + (bz - az) * t / Math.max(len, 1e-6);
					for (int x = (int) Math.floor(px - HALF); x <= px + HALF; x++) {
						for (int z = (int) Math.floor(pz - HALF); z <= pz + HALF; z++) {
							if (x < x0 || z < z0 || x > x1 || z > z1) continue;
							int idx = (x - x0) * d + (z - z0);
							if (in.get(idx) && Math.hypot(x - px, z - pz) <= HALF + 0.2) path.set(idx);
						}
					}
				}
				double prev = run;
				run += len;
				for (double at = Math.ceil((prev - LAMP_SPACING / 2.0) / LAMP_SPACING) * LAMP_SPACING + LAMP_SPACING / 2.0; at < run; at += LAMP_SPACING) {
					double t = (at - prev) / Math.max(len, 1e-6);
					double px = ax + (bx - ax) * t, pz = az + (bz - az) * t;
					double nx = -(bz - az) / Math.max(len, 1e-6), nz = (bx - ax) / Math.max(len, 1e-6);
					int lx = (int) Math.round(px + nx * 2), lz = (int) Math.round(pz + nz * 2);
					if (lx < x0 || lz < z0 || lx > x1 || lz > z1) continue;
					int idx = (lx - x0) * d + (lz - z0);
					if (in.get(idx)) lamp.set(idx);
				}
			}
		}
		lamp.andNot(path);
		return out;
	}

	private static final int EDGE_SAMPLES = 256;

	private static float[] grade(List<int[]> cells, List<double[]> edge, double level, int x0, int z0, int d, int w) {
		float[] h = new float[w * d];
		java.util.Arrays.fill(h, Float.NaN);
		List<double[]> ref = edge;
		if (edge.size() > EDGE_SAMPLES) {
			ref = new ArrayList<>(EDGE_SAMPLES);
			for (int i = 0; i < EDGE_SAMPLES; i++) ref.add(edge.get((int) ((long) i * edge.size() / EDGE_SAMPLES)));
		}
		for (int[] c : cells) {
			double v = level;
			if (!ref.isEmpty()) {
				double sw = 0, sv = 0;
				for (double[] e : ref) {
					double dx = c[0] - e[0], dz = c[1] - e[1], dd = dx * dx + dz * dz;
					if (dd < 0.5) {
						sw = 1;
						sv = e[2];
						break;
					}
					double wgt = 1 / dd;
					sw += wgt;
					sv += wgt * e[2];
				}
				v = sv / sw;
			}
			h[(c[0] - x0) * d + (c[1] - z0)] = (float) (Math.round(v * 2) / 2.0);
		}
		return h;
	}

	private static double[][] meander(int[] a, int[] b, long seed) {
		double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.hypot(dx, dz);
		int n = Math.max(2, (int) Math.ceil(len / 2));
		double nx = -dz / Math.max(len, 1e-6), nz = dx / Math.max(len, 1e-6);
		double amp = Math.min(7, len / 6) * (0.5 + 0.5 * Hashing.unit(Hashing.hash(seed, 1)));
		double waves = 1 + (int) (Hashing.unit(Hashing.hash(seed, 2)) * 2);
		double phase = Hashing.unit(Hashing.hash(seed, 3)) < 0.5 ? 0 : Math.PI;
		double[][] pts = new double[n + 1][];
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			double off = amp * Math.sin(Math.PI * t) * Math.sin(Math.PI * waves * t + phase);
			pts[i] = new double[] {a[0] + dx * t + nx * off, a[1] + dz * t + nz * off};
		}
		return pts;
	}
}
