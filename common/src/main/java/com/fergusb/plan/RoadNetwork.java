package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

/** Creates a network of streets for a superblock */
final class RoadNetwork {
	private RoadNetwork() {}

	record DeadEnd(double x, double z, double tx, double tz, double halfWidth, int road, int sample, boolean atStart) {}

	static List<DeadEnd> connect(CityPlanner planner, List<Road> streets) {
		for (int round = 0; ; round++) {
			List<DeadEnd> ends = connectOnce(planner, streets);
			boolean trimmed = false;
			for (DeadEnd d : ends) {
				RoadProfile p = streets.get(d.road()).profile;
				if (p.stateAt(d.sample()) != RoadProfile.State.BRIDGE) continue;
				int k = d.sample(), step = d.atStart() ? 1 : -1;
				int a = k;
				while (k >= 0 && k < p.samples() && p.stateAt(k) == RoadProfile.State.BRIDGE) k += step;
				p.clear(Math.min(a, k - step), Math.max(a, k - step));
				trimmed = true;
			}
			if (!trimmed || round >= 6) return ends;
		}
	}

	private static List<DeadEnd> connectOnce(CityPlanner planner, List<Road> streets) {
		int n = streets.size();
		// pieceOf[r][k] = piece id (-1 where unbuilt)
		int[][] pieceOf = new int[n][];
		List<int[]> pieces = new ArrayList<>(); // {road, a, b}
		for (int r = 0; r < n; r++) {
			RoadProfile p = streets.get(r).profile;
			int m = p.samples();
			pieceOf[r] = new int[m];
			java.util.Arrays.fill(pieceOf[r], -1);
			int k = 0;
			while (k < m) {
				if (p.stateAt(k) == RoadProfile.State.NONE) {
					k++;
					continue;
				}
				int a = k;
				while (k < m && p.stateAt(k) != RoadProfile.State.NONE) k++;
				int id = pieces.size();
				pieces.add(new int[] {r, a, k - 1});
				for (int j = a; j < k; j++) pieceOf[r][j] = id;
			}
		}
		int root = pieces.size();
		int[] parent = new int[root + 1];
		for (int i = 0; i <= root; i++) parent[i] = i;

		// Joins at each piece end
		List<int[]> loose = new ArrayList<>(); // {piece, end sample}
		for (int id = 0; id < root; id++) {
			int[] pc = pieces.get(id);
			Road road = streets.get(pc[0]);
			int last = road.profile.samples() - 1;
			for (int e : new int[] {pc[1], pc[2]}) {
				boolean roadEnd = e == 0 || e == last;
				if (!roadEnd) {
					loose.add(new int[] {id, e}); // runs into unbuilt water
					continue;
				}
				double[] end = road.pointAt(Math.min(e, road.length()));
				double[] t = outward(road, e == 0);
				double reach = road.halfWidth + 1.5;
				double qx = end[0] + t[0] * reach, qz = end[1] + t[1] * reach;
				boolean joined = false;
				CityPlanner.RoadPoint ap = planner.arterialPoint((int) Math.round(qx), (int) Math.round(qz));
				if (ap != null && ap.state() != RoadProfile.State.NONE) {
					union(parent, id, root);
					joined = true;
				}
				for (int o = 0; o < n; o++) {
					if (o == pc[0]) continue;
					Road other = streets.get(o);
					for (double[] q : new double[][] {end, {qx, qz}}) {
						if (!other.containsWithin(q[0], q[1], other.halfWidth + 1.5)) continue;
						int k = (int) Math.round(other.arcAt(q[0], q[1]));
						k = Math.max(0, Math.min(pieceOf[o].length - 1, k));
						if (pieceOf[o][k] >= 0) {
							union(parent, id, pieceOf[o][k]);
							joined = true;
							break;
						}
					}
				}
				if (!joined) loose.add(new int[] {id, e});
			}
		}

		// Remove what can't reach an arterial
		List<DeadEnd> deadEnds = new ArrayList<>();
		for (int id = 0; id < root; id++) {
			int[] pc = pieces.get(id);
			if (find(parent, id) != find(parent, root)) streets.get(pc[0]).profile.clear(pc[1], pc[2]);
		}
		for (int[] l : loose) {
			int[] pc = pieces.get(l[0]);
			if (find(parent, l[0]) != find(parent, root)) continue;
			Road road = streets.get(pc[0]);
			boolean atStart = l[1] == pc[1];
			double[] p = road.pointAt(Math.min(l[1], road.length()));
			double[] t = outward(road, atStart);
			deadEnds.add(new DeadEnd(p[0], p[1], t[0], t[1], road.halfWidth, pc[0], l[1], atStart));
		}
		return deadEnds;
	}

	/** Unit direction pointing out of the road at its start or end */
	private static double[] outward(Road road, boolean start) {
		double len = road.length();
		double[] inner = road.pointAt(start ? Math.min(3, len) : Math.max(0, len - 3));
		double[] end = road.pointAt(start ? 0 : len);
		double dx = end[0] - inner[0], dz = end[1] - inner[1];
		double l = Math.hypot(dx, dz);
		return l < 1e-6 ? new double[] {1, 0} : new double[] {dx / l, dz / l};
	}

	private static int find(int[] p, int i) {
		while (p[i] != i) {
			p[i] = p[p[i]];
			i = p[i];
		}
		return i;
	}

	private static void union(int[] p, int a, int b) {
		p[find(p, a)] = find(p, b);
	}

	static Obb terminalLot(DeadEnd d) {
		double len = 12;
		double cx = d.x() + d.tx() * (len / 2 + 0.5), cz = d.z() + d.tz() * (len / 2 + 0.5);
		return new Obb(cx, cz, d.tx(), d.tz(), (len - 1) / 2.0, d.halfWidth() + 2.5);
	}
}
