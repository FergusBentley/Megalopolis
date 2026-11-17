package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

final class CoastRoads {
	private static final int G = 8; // shoreline grid
	private static final double HALF_WIDTH = (CityPlanner.STREET_WIDTH - 1) / 2.0;
	private static final int MIN_CELLS = 3; // shorter shorelines don't get a road
	private static final double CLEAR = 10; // shoreline this close to a street is already served
	private static final double LINK = 56; // how far an end looks for a road to join

	private CoastRoads() {}

	static List<Road> build(CityPlanner planner, Rect bounds, List<Road> streets) {
		GradeField grade = planner.grade();
		TerrainSampler terrain = planner.terrain();
		int x0 = bounds.minX() + 6, z0 = bounds.minZ() + 6;
		int nx = Math.max(0, (bounds.maxX() - 6 - x0) / G + 1), nz = Math.max(0, (bounds.maxZ() - 6 - z0) / G + 1);
		if (nx < 3 || nz < 3) return List.of();
		boolean[][] water = new boolean[nx][nz];
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < nz; j++) {
				int x = x0 + i * G, z = z0 + j * G;
				water[i][j] = terrain.sample(x, z).water() || grade.smallIslandAt(x, z);
			}
		}
		// Shore cells
		boolean[][] shore = new boolean[nx][nz];
		int count = 0;
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < nz; j++) {
				if (water[i][j]) continue;
				boolean wet = (i > 0 && water[i - 1][j]) || (i < nx - 1 && water[i + 1][j])
						|| (j > 0 && water[i][j - 1]) || (j < nz - 1 && water[i][j + 1]);
				if (!wet) continue;
				int x = x0 + i * G, z = z0 + j * G;
				boolean served = false;
				for (Road r : streets) {
					if (!r.containsWithin(x, z, CLEAR)) continue;
					if (r.profile == null) {
						served = true;
						continue;
					}
					double[] n = r.nearest(x, z);
					if (r.profile.state(r.arcAt(n[0], n[1])) != RoadProfile.State.NONE) served = true;
				}
				if (served) continue;
				shore[i][j] = true;
				count++;
			}
		}
		if (count < MIN_CELLS) return List.of();

		// Chains of neighboring shore cells
		boolean[][] used = new boolean[nx][nz];
		List<List<int[]>> chains = new ArrayList<>();
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < nx; i++) {
				for (int j = 0; j < nz; j++) {
					if (!shore[i][j] || used[i][j]) continue;
					if (pass == 0 && degree(shore, used, i, j) > 1) continue;
					List<int[]> chain = new ArrayList<>();
					int ci = i, cj = j, pdi = 0, pdj = 0;
					while (true) {
						used[ci][cj] = true;
						chain.add(new int[] {ci, cj});
						int[] next = null;
						int bestTurn = Integer.MAX_VALUE;
						for (int di = -1; di <= 1; di++) {
							for (int dj = -1; dj <= 1; dj++) {
								if (di == 0 && dj == 0) continue;
								int a = ci + di, b = cj + dj;
								if (a < 0 || b < 0 || a >= nx || b >= nz || !shore[a][b] || used[a][b]) continue;
								int turn = Math.abs(di - pdi) + Math.abs(dj - pdj);
								if (turn < bestTurn) {
									bestTurn = turn;
									next = new int[] {a, b, di, dj};
								}
							}
						}
						if (next == null) break;
						ci = next[0];
						cj = next[1];
						pdi = next[2];
						pdj = next[3];
					}
					if (chain.size() >= MIN_CELLS) chains.add(chain);
				}
			}
		}

		List<Road> out = new ArrayList<>();
		RoadRouter router = new RoadRouter(grade, bounds);
		for (List<int[]> chain : chains) {
			List<double[]> pts = new ArrayList<>();
			for (int[] c : chain) pts.add(new double[] {x0 + c[0] * G, z0 + c[1] * G});
			pts = RoadRouter.chaikin(RoadRouter.chaikin(pts));
			List<Road> joinable = new ArrayList<>(streets);
			joinable.addAll(out);
			pts = link(planner, pts, true, joinable, bounds, router);
			pts = link(planner, pts, false, joinable, bounds, router);
			double[] xs = new double[pts.size()], zs = new double[pts.size()];
			for (int k = 0; k < pts.size(); k++) {
				xs[k] = pts.get(k)[0];
				zs[k] = pts.get(k)[1];
			}
			Road road = new Road(xs, zs, HALF_WIDTH, Road.Kind.STREET);
			road.coastal = true;
			if (road.length() >= 24) out.add(road);
		}
		return out;
	}

	private static int degree(boolean[][] shore, boolean[][] used, int i, int j) {
		int d = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				int a = i + di, b = j + dj;
				if ((di != 0 || dj != 0) && a >= 0 && b >= 0 && a < shore.length && b < shore[0].length && shore[a][b] && !used[a][b]) d++;
			}
		}
		return d;
	}

	private static List<double[]> link(CityPlanner planner, List<double[]> pts, boolean atStart, List<Road> roads, Rect bounds, RoadRouter router) {
		double[] e = atStart ? pts.get(0) : pts.get(pts.size() - 1);
		double best = LINK;
		double[] target = null;
		for (Road r : roads) {
			double[] n = r.nearest(e[0], e[1]);
			if (n[4] < best) {
				best = n[4];
				target = new double[] {n[0], n[1]};
			}
		}
		double[][] edges = {
				{e[0], bounds.minZ(), e[1] - bounds.minZ()}, {e[0], bounds.maxZ(), bounds.maxZ() - e[1]},
				{bounds.minX(), e[1], e[0] - bounds.minX()}, {bounds.maxX(), e[1], bounds.maxX() - e[0]}};
		for (double[] g : edges) {
			if (g[2] < best) {
				best = g[2];
				target = new double[] {g[0], g[1]};
			}
		}
		if (target == null) return pts; // nothing near
		double[][] r = straightClear(planner, roads, e, target) ? new double[][] {{e[0], target[0]}, {e[1], target[1]}}
				: router.route(e[0], e[1], target[0], target[1]);
		List<double[]> link = new ArrayList<>();
		for (int k = 0; k < r[0].length; k++) link.add(new double[] {r[0][k], r[1][k]});
		List<double[]> out = new ArrayList<>();
		if (atStart) {
			for (int k = link.size() - 1; k >= 1; k--) out.add(link.get(k));
			out.addAll(pts);
		} else {
			out.addAll(pts);
			for (int k = 1; k < link.size(); k++) out.add(link.get(k));
		}
		return out;
	}

	private static boolean straightClear(CityPlanner planner, List<Road> roads, double[] e, double[] t) {
		double len = Math.hypot(t[0] - e[0], t[1] - e[1]);
		if (len < 2) return true;
		GradeField grade = planner.grade();
		double h0 = grade.grade(e[0], e[1]), h1 = grade.grade(t[0], t[1]);
		double wet = 0;
		for (double s = 4; s <= len - 6; s += 2) {
			double f = s / len, x = e[0] + (t[0] - e[0]) * f, z = e[1] + (t[1] - e[1]) * f;
			for (Road r : roads) if (r.containsWithin(x, z, r.halfWidth + HALF_WIDTH + 1)) return false;
			if (planner.terrain().sample((int) Math.round(x), (int) Math.round(z)).water()) {
				wet += 2;
				if (wet > RoadProfile.MAX_SPAN - 4) return false;
				continue;
			}
			wet = 0;
			if (Math.abs(grade.grade(x, z) - (h0 + (h1 - h0) * f)) > 6) return false;
		}
		return Math.abs(h1 - h0) <= len * RoadProfile.STREET_SLOPE;
	}

	/** Height profiles for new roads */
	static void profile(CityPlanner planner, List<Road> roads, List<Road> existing, RoadStyle style, Rng rng) {
		List<Road> known = new ArrayList<>(existing);
		for (Road road : roads) {
			double len = road.length();
			double[] a = road.pointAt(0), a2 = road.pointAt(Math.min(2, len));
			double[] b = road.pointAt(len), b2 = road.pointAt(Math.max(0, len - 2));
			Double ps = planner.roadHeightForPin(a[0] + (a[0] - a2[0]), a[1] + (a[1] - a2[1]), known);
			Double pe = planner.roadHeightForPin(b[0] + (b[0] - b2[0]), b[1] + (b[1] - b2[1]), known);
			road.profile = RoadProfile.build(planner, road::pointAt, len, RoadProfile.endFlats(len, ps, pe, 3), RoadProfile.STREET_SLOPE, style,
					false, road.axisAligned(), rng.nextLong());
			known.add(road);
		}
	}

	static boolean overlaps(Obb lot, List<Road> roads, List<Obb> shapes) {
		for (Road road : roads) {
			if (road.maxX < lot.minX || road.minX > lot.maxX || road.maxZ < lot.minZ || road.minZ > lot.maxZ) continue;
			double len = road.length();
			for (double s = 0; s <= len; s += 1.5) {
				double[] p = road.pointAt(s), q = road.pointAt(Math.min(len, s + 1));
				double tx = q[0] - p[0], tz = q[1] - p[1], tl = Math.hypot(tx, tz);
				if (tl < 1e-6) {
					tx = 1;
					tz = 0;
					tl = 1;
				}
				for (double off = -road.halfWidth - 1; off <= road.halfWidth + 1; off += 1) {
					if (lot.contains((int) Math.round(p[0] - tz / tl * off), (int) Math.round(p[1] + tx / tl * off))) return true;
				}
			}
		}
		for (Obb o : shapes) {
			if (o.maxX < lot.minX || o.minX > lot.maxX || o.maxZ < lot.minZ || o.minZ > lot.maxZ) continue;
			for (double[] c : o.corners()) if (lot.contains((int) Math.round(c[0]), (int) Math.round(c[1]))) return true;
			for (double[] c : lot.corners()) if (o.contains((int) Math.round(c[0]), (int) Math.round(c[1]))) return true;
			if (lot.contains((int) Math.round(o.cx), (int) Math.round(o.cz))) return true;
		}
		return false;
	}
}
