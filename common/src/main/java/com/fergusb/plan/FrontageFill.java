package com.fergusb.plan;

import java.util.*;

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
		TerrainSampler terrain = planner.terrain();
		for (int i = 0; i < w * d; i++) {
			if (occ[i] != 0) continue;
			int x = x0 + i / d, z = z0 + i % d;
			if (terrain.sample(Math.round(x / 4f) * 4, Math.round(z / 4f) * 4).water()) occ[i] = 5;
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
		for (Map.Entry<Integer, List<Integer>> e : cellsOf.entrySet()) {
			int l = e.getKey();
			List<Integer> cells = largestPart(e.getValue(), w, d);
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
