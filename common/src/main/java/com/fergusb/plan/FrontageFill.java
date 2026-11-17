package com.fergusb.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fills in buildings around streets */
final class FrontageFill {
	private static final int DEPTH = 24; // how far back from the street the fill reaches
	private static final int SPACING = 11; // frontage per piece, roughly
	private static final int MIN_SIZE = 12; // smaller scraps stay paved
	private static final int REACH = 10, GRID_REACH = 6; // most a building grows out from its own footprint

	private FrontageFill() {}

	/** Returns the new lot list */
	static List<Lot> fill(CityPlanner planner, Rect bounds, List<Road> streets, List<Junction> junctions,
						  List<Lot> lots, java.util.BitSet wild, boolean organic, Rng rng) {
		int w = bounds.width(), d = bounds.depth(), x0 = bounds.minX(), z0 = bounds.minZ();
		// 0 free, 1 street fronting (built, not a bridge), 2 other street, 3 lot, 4 wild
		byte[] occ = new byte[w * d];
		float[] roadH = new float[w * d]; // street height where a street is at ground level
		java.util.Arrays.fill(roadH, Float.NaN);
		int[] owner = new int[w * d]; // building lot index (footprint or setback), or -1
		boolean[] foot = new boolean[w * d];
		java.util.Arrays.fill(owner, -1);
		for (int i = 0; i < w * d; i++) if (wild.get(i)) occ[i] = 4;
		WaterGrid water = new WaterGrid(planner.terrain(), planner.grade(), x0, z0, bounds.maxX(), bounds.maxZ());
		for (int i = 0; i < w * d; i++) {
			if (occ[i] != 0) continue;
			if (water.at(x0 + i / d, z0 + i % d)) occ[i] = 5;
		}
		for (Road road : streets) {
			for (int x = Math.max(x0, road.minX); x <= Math.min(bounds.maxX(), road.maxX); x++) {
				for (int z = Math.max(z0, road.minZ); z <= Math.min(bounds.maxZ(), road.maxZ); z++) {
					int i = (x - x0) * d + (z - z0);
					if (occ[i] != 0 || !road.contains(x, z)) continue;
					double s = road.arcAt(x, z);
					RoadProfile.State st = road.profile.state(s);
					if (st == RoadProfile.State.NONE) continue;
					if (st == RoadProfile.State.GROUND) roadH[i] = (float) road.profile.height(s);
					occ[i] = (byte) (st == RoadProfile.State.BRIDGE || st == RoadProfile.State.TUNNEL ? 2 : 1);
				}
			}
		}
		for (Junction j : junctions) {
			for (int x = (int) Math.floor(j.x() - j.r()); x <= j.x() + j.r(); x++) {
				for (int z = (int) Math.floor(j.z() - j.r()); z <= j.z() + j.r(); z++) {
					if (x < x0 || z < z0 || x > bounds.maxX() || z > bounds.maxZ() || !j.contains(x, z)) continue;
					occ[(x - x0) * d + (z - z0)] = 1;
					roadH[(x - x0) * d + (z - z0)] = (float) j.h();
				}
			}
		}
		for (int li = 0; li < lots.size(); li++) {
			Lot lot = lots.get(li);
			Obb b = lot.bounds();
			boolean building = lot.use() == Lot.Use.BUILDING && lot.footprint() != null;
			for (int x = Math.max(x0, b.minX); x <= Math.min(bounds.maxX(), b.maxX); x++) {
				for (int z = Math.max(z0, b.minZ); z <= Math.min(bounds.maxZ(), b.maxZ); z++) {
					int i = (x - x0) * d + (z - z0);
					if (occ[i] != 0 || !lot.contains(x, z)) continue;
					occ[i] = 3;
					if (building) {
						owner[i] = li;
						foot[i] = BuildingShape.insideAt(lot, x, z, 0);
					}
				}
			}
		}

		// Distance back from the frontage
		int[] dist = new int[w * d];
		java.util.Arrays.fill(dist, Integer.MAX_VALUE);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int i = 0; i < w * d; i++) {
			if (occ[i] == 1) {
				dist[i] = 0;
				queue.add(i);
			} else if (occ[i] == 0 && arterialFront(planner, bounds, x0 + i / d, z0 + i % d)) {
				dist[i] = 1;
				queue.add(i);
			}
		}
		while (!queue.isEmpty()) {
			int i = queue.poll();
			int x = i / d, z = i % d;
			int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
			for (int[] q : nb) {
				if (q[0] < 0 || q[1] < 0 || q[0] >= w || q[1] >= d) continue;
				int j = q[0] * d + q[1];
				if (occ[j] != 0 || dist[j] <= dist[i] + 1 || dist[i] + 1 > DEPTH) continue;
				dist[j] = dist[i] + 1;
				queue.add(j);
			}
		}

		// Every open band cell goes to the nearest building
		int nLots = lots.size();
		int[] label = new int[w * d];
		int[] reach = new int[w * d];
		java.util.Arrays.fill(label, -1);
		ArrayDeque<Integer> q = new ArrayDeque<>();
		for (int i = 0; i < w * d; i++) {
			if (foot[i]) {
				label[i] = owner[i];
				q.add(i);
			}
		}
		// Grid buildings keep their designed shapes and setbacks
		grow(q, label, reach, occ, owner, dist, w, d, organic ? REACH : GRID_REACH, organic);
		int seeds = 0;
		List<int[]> seedPos = new ArrayList<>();
		for (int round = 0; round < 4; round++) {
			boolean added = false;
			for (int i = 0; i < w * d; i++) {
				if (dist[i] != 1 || label[i] >= 0 || occ[i] != 0) continue;
				int x = i / d, z = i % d;
				boolean near = false;
				if (round == 0) {
					for (int[] sp : seedPos) {
						if (Math.abs(sp[0] - x) < SPACING && Math.abs(sp[1] - z) < SPACING) {
							near = true;
							break;
						}
					}
				}
				if (near) continue;
				seedPos.add(new int[] {x, z});
				label[i] = nLots + seeds++;
				q.add(i);
				added = true;
			}
			grow(q, label, reach, occ, owner, dist, w, d, Integer.MAX_VALUE, organic);
			if (!added) break;
		}

		// Fill in pockets
		for (int i = 0; i < w * d; i++) {
			if (label[i] >= 0) {
				reach[i] = 0;
				q.add(i);
			}
		}
		growPockets(q, label, reach, occ, w, d, REACH);

		// Trim sections too thin for interiors
		boolean[] taken = new boolean[w * d];
		for (int i = 0; i < w * d; i++) taken[i] = label[i] >= 0 && !foot[i];
		boolean[] core = new boolean[w * d];
		for (int i = 0; i < w * d; i++) core[i] = label[i] >= 0;
		for (int step = 0; step < 2; step++) {
			boolean[] next = core.clone();
			for (int i = 0; i < w * d; i++) {
				if (!core[i] || foot[i]) continue;
				int x = i / d, z = i % d;
				int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
				for (int[] c : nb) {
					if (c[0] < 0 || c[1] < 0 || c[0] >= w || c[1] >= d || !core[c[0] * d + c[1]]
							|| label[c[0] * d + c[1]] != label[i]) next[i] = false;
				}
			}
			core = next;
		}
		boolean[] opened = core;
		for (int step = 0; step < 2; step++) {
			boolean[] next = opened.clone();
			for (int i = 0; i < w * d; i++) {
				if (opened[i] || !taken[i]) continue;
				int x = i / d, z = i % d;
				int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
				for (int[] c : nb) {
					if (c[0] < 0 || c[1] < 0 || c[0] >= w || c[1] >= d) continue;
					int j = c[0] * d + c[1];
					if (opened[j] && label[j] == label[i]) next[i] = true;
				}
			}
			opened = next;
		}
		for (int i = 0; i < w * d; i++) if (foot[i]) opened[i] = false;

		// Collect the cells per label
		Map<Integer, List<Integer>> cellsOf = new HashMap<>();
		for (int i = 0; i < w * d; i++) {
			if (opened[i] || foot[i] && label[i] >= 0) cellsOf.computeIfAbsent(label[i], k -> new ArrayList<>()).add(i);
		}
		List<Lot> out = new ArrayList<>(lots);
		int[] claimed = new int[w * d];
		java.util.Arrays.fill(claimed, -1);
		boolean[] waterOk = organic ? null : waterAllowed(streets, occ, x0, z0, w, d);
		for (int i = 0; i < w * d; i++) if (label[i] >= 0 && (opened[i] || foot[i])) claimed[i] = label[i];
		Map<Integer, List<Integer>> ownCells = new HashMap<>(); // each building lot's own columns
		if (!organic) for (int i = 0; i < w * d; i++) if (occ[i] == 3 && owner[i] >= 0) ownCells.computeIfAbsent(owner[i], k -> new ArrayList<>()).add(i);
		List<Integer> order = new ArrayList<>(cellsOf.keySet());
		java.util.Collections.sort(order);
		for (int l : order) {
			List<Integer> cells = largestPart(cellsOf.get(l), w, d);
			if (!organic) {
				List<List<Integer>> rects = tiles(cells, ownCells.getOrDefault(l, List.of()), l, l < nLots, occ, owner, claimed, dist, waterOk, w, d);
				int first = 0;
				if (l < nLots) {
					first = 1;
					List<Integer> own = rects.isEmpty() ? null : rects.get(0);
					if (own != null && grewBeyond(own, lots.get(l), x0, z0, d)) {
						Lot old = lots.get(l);
						Mask m = mask(own, x0, z0, d);
						out.set(l, raise(new Lot(m.box(), m.box(), Lot.Use.BUILDING, old.baseY(), old.floors(), old.downtown(),
								old.condition(), old.seed(), old.spec(), m), frontHeight(own, roadH, w, d)));
					}
				}
				for (int k = first; k < rects.size(); k++) {
					List<Integer> r = rects.get(k);
					out.add(raise(planner.maskLot(mask(r, x0, z0, d), rng), frontHeight(r, roadH, w, d)));
				}
				continue;
			}
			double front = frontHeight(cells, roadH, w, d);
			if (l >= nLots) {
				if (cells.size() >= MIN_SIZE) {
					Lot lot = planner.maskLot(mask(cells, x0, z0, d), rng);
					out.add(raise(lot, front));
				}
				continue;
			}
			boolean grew = false;
			for (int c : cells) if (!foot[c]) grew = true;
			if (!grew) continue; // nothing to add
			Lot old = lots.get(l);
			Mask m = mask(cells, x0, z0, d);
			out.set(l, raise(new Lot(m.box(), m.box(), Lot.Use.BUILDING, old.baseY(), old.floors(), old.downtown(),
					old.condition(), old.seed(), old.spec(), m), front));
		}
		return out;
	}

	private static boolean[] waterAllowed(List<Road> streets, byte[] occ, int x0, int z0, int w, int d) {
		boolean[] ok = new boolean[w * d];
		for (int i = 0; i < w * d; i++) ok[i] = occ[i] == 5;
		for (Road road : streets) {
			if (!road.coastal) continue;
			int pad = 8 + (int) Math.ceil(road.halfWidth);
			for (int x = Math.max(x0, road.minX - pad); x <= Math.min(x0 + w - 1, road.maxX + pad); x++) {
				for (int z = Math.max(z0, road.minZ - pad); z <= Math.min(z0 + d - 1, road.maxZ + pad); z++) {
					int i = (x - x0) * d + (z - z0);
					if (ok[i] && road.containsWithin(x, z, road.halfWidth + 8)) ok[i] = false;
				}
			}
		}
		return ok;
	}

	private static boolean grewBeyond(List<Integer> rect, Lot lot, int x0, int z0, int d) {
		for (int i : rect) if (!lot.bounds().contains(x0 + i / d, z0 + i % d)) return true;
		return false;
	}

	private static List<List<Integer>> tiles(List<Integer> cells, List<Integer> own, int l, boolean existing, byte[] occ, int[] owner, int[] claimed,
			int[] dist, boolean[] waterOk, int w, int d) {
		List<List<Integer>> out = new ArrayList<>();
		int bx0 = Integer.MAX_VALUE, bz0 = Integer.MAX_VALUE, bx1 = -1, bz1 = -1;
		java.util.Set<Integer> piece = new java.util.HashSet<>(cells);
		if (existing) piece.addAll(own);
		for (int c : piece) {
			bx0 = Math.min(bx0, c / d);
			bx1 = Math.max(bx1, c / d);
			bz0 = Math.min(bz0, c % d);
			bz1 = Math.max(bz1, c % d);
		}
		int bw = bx1 - bx0 + 1, bd = bz1 - bz0 + 1;
		boolean[] used = new boolean[bw * bd];
		for (boolean water : new boolean[] {true, false}) {
			boolean[] ok = new boolean[bw * bd];
			boolean[] must = new boolean[bw * bd];
			for (int a = 0; a < bw; a++) {
				for (int b = 0; b < bd; b++) {
					int i = (bx0 + a) * d + (bz0 + b);
					boolean mine = occ[i] == 3 && owner[i] == l;
					boolean open = occ[i] == 0 || water && waterOk[i];
					ok[a * bd + b] = (mine || open) && (claimed[i] < 0 || claimed[i] == l);
					must[a * bd + b] = existing ? mine : dist[i] == 1 && piece.contains(i);
				}
			}
			int[] r = bestRect(ok, must, bw, bd, existing, MIN_SIZE);
			if (r == null) continue;
			List<Integer> rect = cellsOf(r, bx0, bz0, d);
			int wet = 0;
			for (int i : rect) if (occ[i] == 5) wet++;
			if (wet * 2 > rect.size()) continue;
			take(rect, l, claimed, used, bx0, bz0, bd, d);
			out.add(rect);
			break;
		}
		if (existing && out.isEmpty()) return out; // can't square off
		for (int round = 0; round < 8; round++) {
			boolean[] ok = new boolean[bw * bd];
			boolean[] must = new boolean[bw * bd];
			for (int a = 0; a < bw; a++) {
				for (int b = 0; b < bd; b++) {
					int i = (bx0 + a) * d + (bz0 + b);
					ok[a * bd + b] = piece.contains(i) && occ[i] == 0 && !used[a * bd + b] && (claimed[i] < 0 || claimed[i] == l);
				}
			}
			for (int a = 0; a < bw; a++) {
				for (int b = 0; b < bd; b++) {
					if (!ok[a * bd + b]) continue;
					int i = (bx0 + a) * d + (bz0 + b);
					boolean next = dist[i] == 1 || a > 0 && used[(a - 1) * bd + b] || a < bw - 1 && used[(a + 1) * bd + b]
							|| b > 0 && used[a * bd + b - 1] || b < bd - 1 && used[a * bd + b + 1];
					must[a * bd + b] = next;
				}
			}
			int[] r = bestRect(ok, must, bw, bd, false, MIN_SIZE);
			if (r == null) break;
			List<Integer> rect = cellsOf(r, bx0, bz0, d);
			take(rect, l, claimed, used, bx0, bz0, bd, d);
			out.add(rect);
		}
		return out;
	}

	private static List<Integer> cellsOf(int[] r, int bx0, int bz0, int d) {
		List<Integer> out = new ArrayList<>();
		for (int a = r[0]; a <= r[2]; a++) for (int b = r[1]; b <= r[3]; b++) out.add((bx0 + a) * d + (bz0 + b));
		return out;
	}

	private static void take(List<Integer> rect, int l, int[] claimed, boolean[] used, int bx0, int bz0, int bd, int d) {
		for (int i : rect) {
			claimed[i] = l;
			used[(i / d - bx0) * bd + (i % d - bz0)] = true;
		}
	}

	private static int[] bestRect(boolean[] ok, boolean[] must, int bw, int bd, boolean allMust, int minArea) {
		int[] pre = new int[(bw + 1) * (bd + 1)];
		int total = 0;
		for (int a = 0; a < bw; a++) {
			for (int b = 0; b < bd; b++) {
				int m = must[a * bd + b] ? 1 : 0;
				total += m;
				pre[(a + 1) * (bd + 1) + b + 1] = m + pre[a * (bd + 1) + b + 1] + pre[(a + 1) * (bd + 1) + b] - pre[a * (bd + 1) + b];
			}
		}
		if (total == 0) return null;
		int[] height = new int[bd];
		int[] best = null;
		int bestArea = Math.max(MIN_SIZE, minArea) - 1;
		for (int a = 0; a < bw; a++) {
			for (int b = 0; b < bd; b++) height[b] = ok[a * bd + b] ? height[b] + 1 : 0;
			// Every maximal rectangle ending on row a (stack of increasing heights).
			int[] stack = new int[bd + 1];
			int top = 0;
			for (int b = 0; b <= bd; b++) {
				int hgt = b == bd ? 0 : height[b];
				while (top > 0 && height[stack[top - 1]] >= hgt) {
					int hh = height[stack[--top]];
					int left = top == 0 ? 0 : stack[top - 1] + 1, right = b - 1;
					int area = hh * (right - left + 1);
					if (hh > 0 && area > bestArea) {
						int a0 = a - hh + 1;
						int cnt = pre[(a + 1) * (bd + 1) + right + 1] - pre[a0 * (bd + 1) + right + 1]
								- pre[(a + 1) * (bd + 1) + left] + pre[a0 * (bd + 1) + left];
						if (allMust ? cnt == total : cnt > 0) {
							bestArea = area;
							best = new int[] {a0, left, a, right};
						}
					}
				}
				stack[top++] = b;
			}
		}
		return best;
	}

	/** Highest ground-level street touching the cells */
	private static double frontHeight(List<Integer> cells, float[] roadH, int w, int d) {
		double best = Double.NaN;
		for (int c : cells) {
			int x = c / d, z = c % d;
			int[] nb = {x + 1 < w ? c + d : -1, x > 0 ? c - d : -1, z + 1 < d ? c + 1 : -1, z > 0 ? c - 1 : -1};
			for (int j : nb) {
				if (j >= 0 && !Float.isNaN(roadH[j]) && (Double.isNaN(best) || roadH[j] > best)) best = roadH[j];
			}
		}
		return best;
	}

	/** Lifts a building's floor to the street it faces */
	private static Lot raise(Lot lot, double front) {
		if (Double.isNaN(front) || front <= lot.baseY() || front - lot.baseY() > 8) return lot;
		return new Lot(lot.bounds(), lot.footprint(), lot.use(), (int) Math.ceil(front), lot.floors(), lot.downtown(),
				lot.condition(), lot.seed(), lot.spec(), lot.mask());
	}

	/** Growth into any open ground */
	private static void growPockets(ArrayDeque<Integer> q, int[] label, int[] reach, byte[] occ, int w, int d, int limit) {
		while (!q.isEmpty()) {
			int i = q.poll();
			if (reach[i] >= limit) continue;
			int x = i / d, z = i % d;
			int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
			for (int[] c : nb) {
				if (c[0] < 0 || c[1] < 0 || c[0] >= w || c[1] >= d) continue;
				int j = c[0] * d + c[1];
				if (label[j] >= 0 || occ[j] != 0) continue;
				label[j] = label[i];
				reach[j] = reach[i] + 1;
				q.add(j);
			}
		}
	}

	private static void grow(ArrayDeque<Integer> q, int[] label, int[] reach, byte[] occ, int[] owner, int[] dist,
			int w, int d, int limit, boolean throughSetback) {
		while (!q.isEmpty()) {
			int i = q.poll();
			if (reach[i] >= limit) continue;
			int x = i / d, z = i % d;
			int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
			for (int[] c : nb) {
				if (c[0] < 0 || c[1] < 0 || c[0] >= w || c[1] >= d) continue;
				int j = c[0] * d + c[1];
				if (label[j] >= 0) continue;
				boolean open = occ[j] == 0 && dist[j] != Integer.MAX_VALUE && dist[j] > 0;
				boolean ownSetback = throughSetback && occ[j] == 3 && owner[j] >= 0 && owner[j] == label[i];
				if (!open && !ownSetback) continue;
				label[j] = label[i];
				reach[j] = reach[i] + 1;
				q.add(j);
			}
		}
	}

	/** The largest 4-connected group of the cells */
	private static List<Integer> largestPart(List<Integer> cells, int w, int d) {
		java.util.Set<Integer> set = new java.util.HashSet<>(cells);
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		List<Integer> best = List.of();
		for (int start : cells) {
			if (seen.contains(start)) continue;
			List<Integer> part = new ArrayList<>();
			ArrayDeque<Integer> q = new ArrayDeque<>();
			q.add(start);
			seen.add(start);
			while (!q.isEmpty()) {
				int c = q.poll();
				part.add(c);
				int x = c / d, z = c % d;
				int[] nb = {x + 1 < w ? c + d : -1, x > 0 ? c - d : -1, z + 1 < d ? c + 1 : -1, z > 0 ? c - 1 : -1};
				for (int j : nb) {
					if (j < 0 || !set.contains(j) || !seen.add(j)) continue;
					q.add(j);
				}
			}
			if (part.size() > best.size()) best = part;
		}
		return best;
	}

	private static Mask mask(List<Integer> cells, int x0, int z0, int d) {
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int c : cells) {
			int x = c / d, z = c % d;
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		Mask m = new Mask(x0 + minX, z0 + minZ, maxX - minX + 1, maxZ - minZ + 1);
		for (int c : cells) m.set(x0 + c / d, z0 + c % d);
		return m;
	}

	/** True for a column on the superblock edge whose arterial alongside is built at ground level */
	private static boolean arterialFront(CityPlanner planner, Rect b, int x, int z) {
		int ox = x == b.minX() ? -1 : x == b.maxX() ? 1 : 0;
		int oz = z == b.minZ() ? -1 : z == b.maxZ() ? 1 : 0;
		if (ox == 0 && oz == 0) return false;
		CityPlanner.RoadPoint ap = planner.arterialPoint(x + ox * 2, z + oz * 2);
		if (ap == null) return false;
		RoadProfile.State st = ap.state();
		return st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED;
	}
}
