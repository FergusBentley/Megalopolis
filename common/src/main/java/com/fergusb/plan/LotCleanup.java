package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

final class LotCleanup {
	static final int MIN_SIDE = 6;
	static final int MIN_AREA = 40;
	private static final int TOUCH = 4; // how far away a neighbor counts as touching

	private LotCleanup() {}

	static List<Lot> apply(CityPlanner planner, Rect bounds, List<Road> streets, List<Junction> junctions, List<Lot> lots,
			boolean organic, Rng rng) {
		Grid g = new Grid(bounds, streets, junctions);
		g.organic = organic;
		List<Lot> out = new ArrayList<>(lots);
		g.own(out);

		// Buildings a street runs across
		for (int li = 0; li < out.size(); li++) {
			Lot lot = out.get(li);
			if (lot.use() != Lot.Use.BUILDING || lot.footprint() == null || !g.crossed(lot)) continue;
			List<int[]> piece = g.largestPiece(g.footprint(lot, li));
			if (!organic) piece = g.rectangleIn(piece);
			if (piece.isEmpty()) {
				out.set(li, new Lot(lot.bounds(), null, Lot.Use.GREEN, lot.baseY(), 0, lot.downtown(), lot.condition(),
						lot.seed(), null, g.maskOf(g.cells(lot, li))));
				continue;
			}
			Mask m = g.maskOf(piece);
			out.set(li, new Lot(m.box(), m.box(), Lot.Use.BUILDING, lot.baseY(), lot.floors(), lot.downtown(), lot.condition(),
					lot.seed(), lot.spec(), m, lot.lift()));
		}
		g.own(out);

		// Cut-off land beside a building
		java.util.Set<Integer> converted = new java.util.HashSet<>();
		for (int li = 0; li < out.size(); li++) {
			Lot lot = out.get(li);
			if (lot.use() != Lot.Use.GREEN || lot.mask() != null) continue;
			if (planner.frontsBuiltRoad(lot.bounds(), streets)) continue;
			boolean reachable = false;
			for (int o : g.touching(out, li).keySet()) {
				if (!converted.contains(o) && g.onStreet(planner, out.get(o), o)) {
					reachable = true;
					break;
				}
			}
			if (!reachable) continue;
			Lot b = planner.finishLot(lot.bounds(), rng, false, streets, true);
			if (b.use() == Lot.Use.BUILDING) {
				out.set(li, b);
				converted.add(li);
			}
		}
		g.own(out);

		// Buildings too small for rooms
		for (int round = 0; round < 3; round++) {
			List<Integer> small = new ArrayList<>();
			java.util.Map<Integer, Integer> size = new java.util.HashMap<>();
			for (int li = 0; li < out.size(); li++) {
				Lot lot = out.get(li);
				if (lot.use() != Lot.Use.BUILDING || lot.footprint() == null) continue;
				Obb f = lot.footprint();
				if (lot.mask() == null && round == 0 && f.widthU() >= MIN_SIDE + 4 && f.depthV() >= MIN_SIDE + 4
						&& f.widthU() * f.depthV() >= 3 * MIN_AREA) continue;
				List<int[]> fp = g.footprint(lot, li);
				if (tooSmall(fp)) {
					small.add(li);
					size.put(li, fp.size());
				}
			}
			if (small.isEmpty()) break;
			small.sort((a, b) -> size.get(a).equals(size.get(b)) ? Integer.compare(a, b) : Integer.compare(size.get(a), size.get(b)));
			boolean changed = false;
			java.util.Set<Integer> removed = new java.util.HashSet<>();
			for (int li : small) {
				if (removed.contains(li)) continue;
				if (!tooSmall(g.footprint(out.get(li), li))) continue; // grown by an earlier merge
				java.util.Map<Integer, Integer> near = g.touching(out, li);
				near.keySet().removeAll(removed);
				int best = -1, bestCount = 0;
				for (var e : near.entrySet()) {
					if (e.getValue() > bestCount || e.getValue() == bestCount && e.getKey() < best) {
						best = e.getKey();
						bestCount = e.getValue();
					}
				}
				Lot lot = out.get(li);
				if (best < 0) {
					out.set(li, new Lot(lot.bounds(), null, Lot.Use.GREEN, lot.baseY(), 0, lot.downtown(), lot.condition(),
							lot.seed(), null, lot.mask()));
					changed = true;
					continue;
				}
				Lot merged = g.merge(out.get(best), best, lot, li);
				g.update(out.get(best), best, lot, li, merged);
				out.set(best, merged);
				removed.add(li);
				changed = true;
			}
			if (!removed.isEmpty()) {
				List<Lot> kept = new ArrayList<>();
				for (int li = 0; li < out.size(); li++) if (!removed.contains(li)) kept.add(out.get(li));
				out.clear();
				out.addAll(kept);
				g.own(out);
			}
			if (!changed) break;
		}

		return out;
	}

	static boolean tooSmall(List<int[]> cells) {
		if (cells.size() < MIN_AREA) return true;
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (int[] c : cells) {
			x0 = Math.min(x0, c[0]);
			z0 = Math.min(z0, c[1]);
			x1 = Math.max(x1, c[0]);
			z1 = Math.max(z1, c[1]);
		}
		int w = x1 - x0 + 1, d = z1 - z0 + 1;
		if (w < MIN_SIDE || d < MIN_SIDE) return true;
		// Largest square ending at each cell
		int[] sq = new int[w * d];
		boolean[] in = new boolean[w * d];
		for (int[] c : cells) in[(c[0] - x0) * d + (c[1] - z0)] = true;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				int i = x * d + z;
				if (!in[i]) continue;
				int v = x == 0 || z == 0 ? 1 : 1 + Math.min(sq[i - d], Math.min(sq[i - 1], sq[i - d - 1]));
				sq[i] = v;
				if (v >= MIN_SIDE) return false;
			}
		}
		return true;
	}

	private static final class Grid {
		final int x0, z0, w, d;
		boolean organic;
		final boolean[] street;
		final boolean[] walk;
		final int[] owner;

		Grid(Rect b, List<Road> streets, List<Junction> junctions) {
			x0 = b.minX();
			z0 = b.minZ();
			w = b.width();
			d = b.depth();
			street = new boolean[w * d];
			walk = new boolean[w * d];
			owner = new int[w * d];
			for (Road road : streets) {
				for (int x = Math.max(x0, road.minX); x <= Math.min(b.maxX(), road.maxX); x++) {
					for (int z = Math.max(z0, road.minZ); z <= Math.min(b.maxZ(), road.maxZ); z++) {
						int i = (x - x0) * d + (z - z0);
						if (street[i] || !road.contains(x, z)) continue;
						RoadProfile.State st = road.profile.state(road.arcAt(x, z));
						if (st != RoadProfile.State.NONE) street[i] = true;
						if (st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED) walk[i] = true;
					}
				}
			}
			for (Junction j : junctions) {
				for (int x = (int) Math.floor(j.x() - j.r()); x <= j.x() + j.r(); x++) {
					for (int z = (int) Math.floor(j.z() - j.r()); z <= j.z() + j.r(); z++) {
						int i = index(x, z);
						if (i >= 0 && j.contains(x, z)) {
							street[i] = true;
							walk[i] = true;
						}
					}
				}
			}
		}

		int index(int x, int z) {
			int lx = x - x0, lz = z - z0;
			return lx < 0 || lz < 0 || lx >= w || lz >= d ? -1 : lx * d + lz;
		}

		void own(List<Lot> lots) {
			own(lots, java.util.Set.of());
		}

		void own(List<Lot> lots, java.util.Set<Integer> skip) {
			java.util.Arrays.fill(owner, -1);
			for (int li = 0; li < lots.size(); li++) {
				if (skip.contains(li)) continue;
				Lot lot = lots.get(li);
				Obb bb = lot.bounds();
				for (int x = Math.max(x0, bb.minX); x <= Math.min(x0 + w - 1, bb.maxX); x++) {
					for (int z = Math.max(z0, bb.minZ); z <= Math.min(z0 + d - 1, bb.maxZ); z++) {
						int i = (x - x0) * d + (z - z0);
						if (owner[i] < 0 && !street[i] && lot.contains(x, z)) owner[i] = li;
					}
				}
			}
		}

		void update(Lot big, int bi, Lot small, int si, Lot merged) {
			for (Lot l : new Lot[] {big, small}) {
				Obb bb = l.bounds();
				for (int x = Math.max(x0, bb.minX); x <= Math.min(x0 + w - 1, bb.maxX); x++) {
					for (int z = Math.max(z0, bb.minZ); z <= Math.min(z0 + d - 1, bb.maxZ); z++) {
						int i = (x - x0) * d + (z - z0);
						if (owner[i] == bi || owner[i] == si) owner[i] = -1;
					}
				}
			}
			Obb mb = merged.bounds();
			for (int x = Math.max(x0, mb.minX); x <= Math.min(x0 + w - 1, mb.maxX); x++) {
				for (int z = Math.max(z0, mb.minZ); z <= Math.min(z0 + d - 1, mb.maxZ); z++) {
					if (merged.contains(x, z)) owner[(x - x0) * d + (z - z0)] = bi;
				}
			}
		}

		boolean onStreet(CityPlanner planner, Lot lot, int li) {
			int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] c : cells(lot, li)) {
				for (int[] dv : dirs) {
					for (int k = 1; k <= 3; k++) {
						int x = c[0] + dv[0] * k, z = c[1] + dv[1] * k;
						int i = index(x, z);
						if (i < 0) {
							CityPlanner.RoadPoint ap = planner.arterialPoint(x + dv[0], z + dv[1]);
							if (ap != null && (ap.state() == RoadProfile.State.GROUND || ap.state() == RoadProfile.State.ELEVATED)) return true;
							break;
						}
						if (walk[i]) return true;
						if (street[i] || owner[i] >= 0 && owner[i] != li) break;
					}
				}
			}
			return false;
		}

		private int[] streetSum;

		private int streetsIn(int ax, int az, int bx, int bz) {
			if (streetSum == null) {
				streetSum = new int[(w + 1) * (d + 1)];
				for (int x = 0; x < w; x++) {
					for (int z = 0; z < d; z++) {
						streetSum[(x + 1) * (d + 1) + z + 1] = (street[x * d + z] ? 1 : 0) + streetSum[x * (d + 1) + z + 1]
								+ streetSum[(x + 1) * (d + 1) + z] - streetSum[x * (d + 1) + z];
					}
				}
			}
			ax = Math.max(0, ax - x0);
			az = Math.max(0, az - z0);
			bx = Math.min(w - 1, bx - x0);
			bz = Math.min(d - 1, bz - z0);
			if (ax > bx || az > bz) return 0;
			return streetSum[(bx + 1) * (d + 1) + bz + 1] - streetSum[ax * (d + 1) + bz + 1] - streetSum[(bx + 1) * (d + 1) + az]
					+ streetSum[ax * (d + 1) + az];
		}

		/** True if a built street runs over part of the lot's building */
		boolean crossed(Lot lot) {
			Obb bb = lot.bounds();
			if (streetsIn(bb.minX, bb.minZ, bb.maxX, bb.maxZ) == 0) return false;
			for (int x = Math.max(x0, bb.minX); x <= Math.min(x0 + w - 1, bb.maxX); x++) {
				for (int z = Math.max(z0, bb.minZ); z <= Math.min(z0 + d - 1, bb.maxZ); z++) {
					if (street[(x - x0) * d + (z - z0)] && lot.contains(x, z) && BuildingShape.insideAt(lot, x, z, 0)) return true;
				}
			}
			return false;
		}

		/** The largest 4-connected group of the columns */
		List<int[]> largestPiece(List<int[]> cells) {
			java.util.Map<Long, int[]> left = new java.util.LinkedHashMap<>();
			for (int[] c : cells) left.put(key(c[0], c[1]), c);
			List<int[]> best = new ArrayList<>();
			while (!left.isEmpty()) {
				int[] start = left.values().iterator().next();
				left.remove(key(start[0], start[1]));
				List<int[]> part = new ArrayList<>();
				java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
				q.add(start);
				while (!q.isEmpty()) {
					int[] c = q.poll();
					part.add(c);
					int[][] nb = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
					for (int[] n : nb) {
						int[] m = left.remove(key(n[0], n[1]));
						if (m != null) q.add(m);
					}
				}
				if (part.size() > best.size()) best = part;
			}
			return best;
		}

		/** The largest axis-aligned rectangle inside the columns */
		List<int[]> rectangleIn(List<int[]> cells) {
			if (cells.isEmpty()) return cells;
			int mx = Integer.MAX_VALUE, mz = Integer.MAX_VALUE, Mx = Integer.MIN_VALUE, Mz = Integer.MIN_VALUE;
			for (int[] c : cells) {
				mx = Math.min(mx, c[0]);
				Mx = Math.max(Mx, c[0]);
				mz = Math.min(mz, c[1]);
				Mz = Math.max(Mz, c[1]);
			}
			int bw = Mx - mx + 1, bd = Mz - mz + 1;
			boolean[] in = new boolean[bw * bd];
			for (int[] c : cells) in[(c[0] - mx) * bd + (c[1] - mz)] = true;
			int[] height = new int[bd];
			int bestArea = 0;
			int[] best = null;
			for (int a = 0; a < bw; a++) {
				for (int b = 0; b < bd; b++) height[b] = in[a * bd + b] ? height[b] + 1 : 0;
				int[] stack = new int[bd + 1];
				int top = 0;
				for (int b = 0; b <= bd; b++) {
					int hgt = b == bd ? 0 : height[b];
					while (top > 0 && height[stack[top - 1]] >= hgt) {
						int hh = height[stack[--top]];
						int left = top == 0 ? 0 : stack[top - 1] + 1;
						int area = hh * (b - left);
						if (area > bestArea) {
							bestArea = area;
							best = new int[] {a - hh + 1, left, a, b - 1};
						}
					}
					stack[top++] = b;
				}
			}
			List<int[]> out = new ArrayList<>();
			if (best == null) return out;
			for (int a = best[0]; a <= best[2]; a++) for (int b = best[1]; b <= best[3]; b++) out.add(new int[] {mx + a, mz + b});
			return out;
		}

		Mask maskOf(List<int[]> cells) {
			int mx = Integer.MAX_VALUE, mz = Integer.MAX_VALUE, Mx = Integer.MIN_VALUE, Mz = Integer.MIN_VALUE;
			for (int[] c : cells) {
				mx = Math.min(mx, c[0]);
				Mx = Math.max(Mx, c[0]);
				mz = Math.min(mz, c[1]);
				Mz = Math.max(Mz, c[1]);
			}
			if (cells.isEmpty()) return null;
			Mask m = new Mask(mx, mz, Mx - mx + 1, Mz - mz + 1);
			for (int[] c : cells) m.set(c[0], c[1]);
			return m;
		}

		/** The columns a lot owns */
		List<int[]> cells(Lot lot, int li) {
			List<int[]> out = new ArrayList<>();
			Obb bb = lot.bounds();
			for (int x = Math.max(x0, bb.minX); x <= Math.min(x0 + w - 1, bb.maxX); x++) {
				for (int z = Math.max(z0, bb.minZ); z <= Math.min(z0 + d - 1, bb.maxZ); z++) {
					if (owner[(x - x0) * d + (z - z0)] == li) out.add(new int[] {x, z});
				}
			}
			return out;
		}

		/** The ground-floor columns of a lot's building */
		List<int[]> footprint(Lot lot, int li) {
			List<int[]> out = new ArrayList<>();
			for (int[] c : cells(lot, li)) if (BuildingShape.insideAt(lot, c[0], c[1], 0)) out.add(c);
			return out;
		}

		java.util.Map<Integer, Integer> touching(List<Lot> lots, int li) {
			java.util.Map<Integer, Integer> out = new java.util.TreeMap<>();
			int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] c : cells(lots.get(li), li)) {
				for (int[] dv : dirs) {
					for (int k = 1; k <= TOUCH; k++) {
						int i = index(c[0] + dv[0] * k, c[1] + dv[1] * k);
						if (i < 0 || street[i]) break;
						int o = owner[i];
						if (o == li) break;
						if (o < 0) continue;
						Lot other = lots.get(o);
						if (other.use() == Lot.Use.BUILDING && other.footprint() != null) out.merge(o, 1, Integer::sum);
						break;
					}
				}
			}
			return out;
		}

		Lot merge(Lot big, int bi, Lot small, int si) {
			java.util.Set<Long> set = new java.util.HashSet<>();
			for (int[] c : footprint(big, bi)) set.add(key(c[0], c[1]));
			java.util.Set<Long> smallSet = new java.util.HashSet<>();
			for (int[] c : cells(small, si)) {
				set.add(key(c[0], c[1]));
				smallSet.add(key(c[0], c[1]));
			}
			int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (long k : set) {
				int x = Hashing.unpackX(k), z = Hashing.unpackZ(k);
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
			}
			List<long[]> fill = new ArrayList<>();
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (set.contains(key(x, z))) continue;
					int i = index(x, z);
					if (i < 0 || street[i] || owner[i] >= 0 && owner[i] != bi && owner[i] != si) continue;
					if (!near(smallSet, x, z)) continue;
					if (bothSides(set, x, z, 1, 0) || bothSides(set, x, z, 0, 1)) fill.add(new long[] {x, z});
				}
			}
			for (long[] f : fill) set.add(key((int) f[0], (int) f[1]));
			if (!organic) {
				int ax = Integer.MAX_VALUE, az = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
				for (long k : set) {
					ax = Math.min(ax, Hashing.unpackX(k));
					bx = Math.max(bx, Hashing.unpackX(k));
					az = Math.min(az, Hashing.unpackZ(k));
					bz = Math.max(bz, Hashing.unpackZ(k));
				}
				boolean free = true;
				for (int x = ax; x <= bx && free; x++) {
					for (int z = az; z <= bz && free; z++) {
						int i = index(x, z);
						if (i < 0 || street[i] || owner[i] >= 0 && owner[i] != bi && owner[i] != si) free = false;
					}
				}
				if (free) for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) set.add(key(x, z));
			}
			int mx = Integer.MAX_VALUE, mz = Integer.MAX_VALUE, Mx = Integer.MIN_VALUE, Mz = Integer.MIN_VALUE;
			for (long k : set) {
				int x = Hashing.unpackX(k), z = Hashing.unpackZ(k);
				mx = Math.min(mx, x);
				Mx = Math.max(Mx, x);
				mz = Math.min(mz, z);
				Mz = Math.max(Mz, z);
			}
			Mask m = new Mask(mx, mz, Mx - mx + 1, Mz - mz + 1);
			for (long k : set) m.set(Hashing.unpackX(k), Hashing.unpackZ(k));
			m = m.opened();
			Obb box = m.box();
			return new Lot(box, box, Lot.Use.BUILDING, big.baseY(), big.floors(), big.downtown(),
					big.condition(), big.seed(), big.spec(), m);
		}

		private static boolean near(java.util.Set<Long> cells, int x, int z) {
			for (int a = -TOUCH - 1; a <= TOUCH + 1; a++) {
				for (int b = -TOUCH - 1; b <= TOUCH + 1; b++) if (cells.contains(key(x + a, z + b))) return true;
			}
			return false;
		}

		private static boolean bothSides(java.util.Set<Long> set, int x, int z, int dx, int dz) {
			boolean a = false, b = false;
			for (int k = 1; k <= TOUCH + 1 && !a; k++) a = set.contains(key(x - dx * k, z - dz * k));
			for (int k = 1; k <= TOUCH + 1 && !b; k++) b = set.contains(key(x + dx * k, z + dz * k));
			return a && b;
		}

		private static long key(int x, int z) {
			return Hashing.pack(x, z);
		}
	}
}
