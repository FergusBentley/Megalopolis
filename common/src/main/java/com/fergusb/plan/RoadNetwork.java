package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

/** Creates a network of streets for a superblock */
final class RoadNetwork {
	private RoadNetwork() {}

	record DeadEnd(double x, double z, double tx, double tz, double halfWidth, int road, int sample, boolean atStart) {}

	private static final double STITCH = 18;
	private static final double ISLAND_LINK = RoadProfile.MAX_SPAN + 8;
	private static final int KEEP_ISOLATED = 240;
	private static final int MIN_GROUP_GROUND = 48;
	private static final double GRAND_LINK = 300;
	private static final double BIG_LINK = 64;
	private static final int BIG_ISLAND = 400;
	private static final double SECOND_APART = 150;
	private static final int LINK_STEP = 3;

	static List<DeadEnd> connect(CityPlanner planner, List<Road> streets, Rect bounds, List<Road> added) {
		for (int round = 0; round < 6; round++) {
			if (!link(planner, streets, bounds, added)) break;
		}
		for (int round = 0; ; round++) {
			List<DeadEnd> ends = connectOnce(planner, streets, bounds);
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

	private static final class Net {
		int[][] pieceOf;
		final List<int[]> pieces = new ArrayList<>();
		int[] parent;
		int root;
		int scrap;
		final List<int[]> loose = new ArrayList<>();
		final java.util.Map<Long, Integer> stretches = new java.util.TreeMap<>();

		boolean rooted(int piece) {
			return find(parent, piece) == find(parent, root);
		}

		boolean attached(int piece) {
			int f = find(parent, piece);
			if (f == find(parent, root) || f == find(parent, scrap)) return true;
			for (int node : stretches.values()) if (find(parent, node) == f) return true;
			return false;
		}

		java.util.Set<Long> keysOf(int piece) {
			java.util.Set<Long> out = new java.util.TreeSet<>();
			int f = find(parent, piece);
			for (var e : stretches.entrySet()) if (find(parent, e.getValue()) == f) out.add(e.getKey());
			return out;
		}

		int stretchNode(long key) {
			return stretches.computeIfAbsent(key, k -> scrap + 1 + stretches.size());
		}
	}

	private static boolean built(RoadProfile.State st) {
		return st != RoadProfile.State.NONE && st != RoadProfile.State.TUNNEL;
	}

	static boolean onSurface(Road road, double x, double z) {
		if (!road.containsWithin(x, z, road.halfWidth + 0.5)) return false;
		double len = road.length(), s = road.arcAt(x, z);
		if (s > 0.01 && s < len - 0.01) return true;
		double[] p = road.pointAt(s), q = road.pointAt(s < 0.01 ? Math.min(len, 1) : Math.max(0, len - 1));
		double tx = s < 0.01 ? q[0] - p[0] : p[0] - q[0], tz = s < 0.01 ? q[1] - p[1] : p[1] - q[1];
		double tl = Math.hypot(tx, tz);
		if (tl < 1e-6) return true;
		double along = ((x - p[0]) * tx + (z - p[1]) * tz) / tl;
		return s < 0.01 ? along >= -1 : along <= 1;
	}

	private static Net analyse(CityPlanner planner, List<Road> streets, Rect bounds) {
		return analyse(planner, streets, bounds, true);
	}

	private static Net analyse(CityPlanner planner, List<Road> streets, Rect bounds, boolean externalRoots) {
		Net net = new Net();
		int n = streets.size();
		// pieceOf[r][k] = piece id
		net.pieceOf = new int[n][];
		for (int r = 0; r < n; r++) {
			RoadProfile p = streets.get(r).profile;
			int m = p.samples();
			net.pieceOf[r] = new int[m];
			java.util.Arrays.fill(net.pieceOf[r], -1);
			int k = 0;
			while (k < m) {
				if (p.stateAt(k) == RoadProfile.State.NONE) {
					k++;
					continue;
				}
				int a = k;
				while (k < m && p.stateAt(k) != RoadProfile.State.NONE) k++;
				int id = net.pieces.size();
				net.pieces.add(new int[] {r, a, k - 1});
				for (int j = a; j < k; j++) net.pieceOf[r][j] = id;
			}
		}
		int root = net.pieces.size();
		net.root = root;
		net.scrap = root + 1;
		net.parent = new int[root + 2 + 2 * root + 1];
		for (int i = 0; i < net.parent.length; i++) net.parent[i] = i;

		// Joins at each piece end
		for (int id = 0; id < root; id++) {
			int[] pc = net.pieces.get(id);
			Road road = streets.get(pc[0]);
			int last = road.profile.samples() - 1;
			for (int e : new int[] {pc[1], pc[2]}) {
				boolean roadEnd = e == 0 || e == last;
				if (!roadEnd) {
					net.loose.add(new int[] {id, e}); // runs into unbuilt water
					continue;
				}
				double[] end = road.pointAt(Math.min(e, road.length()));
				double[] t = outward(road, e == 0);
				double reach = road.halfWidth + 1.5;
				double qx = end[0] + t[0] * reach, qz = end[1] + t[1] * reach;
				boolean joined = false;
				if (externalRoots && road.external && !bounds.contains((int) Math.round(end[0]), (int) Math.round(end[1]))) {
					union(net.parent, id, root);
					joined = true;
				}
				CityPlanner.RoadPoint ap = planner.arterialPoint((int) Math.round(qx), (int) Math.round(qz));
				if (ap != null && ap.state() != RoadProfile.State.NONE && ap.profile().isMinor(ap.s())) {
					joined = true;
				} else if (ap != null && ap.state() != RoadProfile.State.NONE) {
					int qx_ = (int) Math.round(qx), qz_ = (int) Math.round(qz);
					long key = planner.isolatedStretch(qx_, qz_);
					union(net.parent, id, !planner.arterialAnchored(qx_, qz_) ? net.scrap : key != 0 ? net.stretchNode(key) : root);
					joined = true;
				}
				for (int o = 0; o < n; o++) {
					if (o == pc[0]) continue;
					Road other = streets.get(o);
					for (double[] q : new double[][] {end, {qx, qz}}) {
						if (!onSurface(other, q[0], q[1])) continue;
						int k = (int) Math.round(other.arcAt(q[0], q[1]));
						k = Math.max(0, Math.min(net.pieceOf[o].length - 1, k));
						if (net.pieceOf[o][k] >= 0) {
							union(net.parent, id, net.pieceOf[o][k]);
							joined = true;
							break;
						}
					}
				}
				if (!joined) net.loose.add(new int[] {id, e});
			}
		}
		return net;
	}

	private static List<DeadEnd> connectOnce(CityPlanner planner, List<Road> streets, Rect bounds) {
		Net net = analyse(planner, streets, bounds);
		// Remove what can't reach an arterial
		List<DeadEnd> deadEnds = new ArrayList<>();
		java.util.Map<Integer, Integer> ground = new java.util.HashMap<>();
		for (int id = 0; id < net.root; id++) {
			if (net.attached(id)) continue;
			int[] pc = net.pieces.get(id);
			RoadProfile p = streets.get(pc[0]).profile;
			for (int k = pc[1]; k <= pc[2]; k++) {
				RoadProfile.State st = p.stateAt(k);
				if (st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED) ground.merge(find(net.parent, id), 1, Integer::sum);
			}
		}
		java.util.function.IntPredicate kept = id -> net.attached(id) || ground.getOrDefault(find(net.parent, id), 0) >= KEEP_ISOLATED;
		for (int id = 0; id < net.root; id++) {
			int[] pc = net.pieces.get(id);
			if (!kept.test(id)) streets.get(pc[0]).profile.clear(pc[1], pc[2]);
		}
		for (int[] l : net.loose) {
			int[] pc = net.pieces.get(l[0]);
			if (!kept.test(l[0])) continue;
			Road road = streets.get(pc[0]);
			boolean atStart = l[1] == pc[1];
			double[] p = road.pointAt(Math.min(l[1], road.length()));
			double[] t = outward(road, atStart);
			deadEnds.add(new DeadEnd(p[0], p[1], t[0], t[1], road.halfWidth, pc[0], l[1], atStart));
		}
		return deadEnds;
	}

	// closing gaps

	/** A place a connecting street can end */
	private record Target(double x, double z, double h, int road, int piece) {}

	private static boolean link(CityPlanner planner, List<Road> streets, Rect bounds, List<Road> added) {
		Net net = analyse(planner, streets, bounds, false);
		boolean any = false;
		// Near misses
		for (int[] l : net.loose) {
			int[] pc = net.pieces.get(l[0]);
			Road road = streets.get(pc[0]);
			if (road.external) continue;
			boolean atStart = l[1] == pc[1];
			double s = Math.min(l[1], road.length());
			double[] e = road.pointAt(s), t = outward(road, atStart);
			Target best = null;
			double bestD = STITCH + road.halfWidth;
			for (Target c : targets(planner, streets, net, bounds, e[0], e[1], bestD, 1)) {
				if (c.road() == pc[0]) continue; // its own continuation was left unbuilt for a reason
				double dx = c.x() - e[0], dz = c.z() - e[1], d = Math.hypot(dx, dz);
				if (d < 1 || (dx * t[0] + dz * t[1]) < 0.5 * d) continue;
				if (d < bestD) {
					bestD = d;
					best = c;
				}
			}
			if (best == null) continue;
			double sx = e[0] - t[0], sz = e[1] - t[1];
			if (best.road() >= 0 && road.axisAligned() && (Math.abs(t[0]) > 0.999 || Math.abs(t[1]) > 0.999)) {
				double along = (best.x() - e[0]) * t[0] + (best.z() - e[1]) * t[1];
				double px = e[0] + t[0] * along, pz = e[1] + t[1] * along;
				Road other = streets.get(best.road());
				if (along > 0 && onSurface(other, px, pz)) {
					double oh = other.profile.height(other.arcAt(px, pz));
					best = new Target(px, pz, oh, best.road(), best.piece());
				}
			}
			Road c = connector(planner, streets, sx, sz, road.profile.height(s), best, road, pc[0], RoadProfile.MAX_SPAN, false);
			if (c == null) continue;
			add(streets, added, c);
			any = true;
		}
		if (any) return true;

		java.util.Map<Integer, List<Integer>> groups = new java.util.TreeMap<>();
		for (int id = 0; id < net.root; id++) {
			if (!net.rooted(id)) groups.computeIfAbsent(find(net.parent, id), k -> new ArrayList<>()).add(id);
		}
		java.util.Map<Integer, Integer> groundOf = new java.util.HashMap<>();
		for (var e : groups.entrySet()) {
			int ground = 0;
			for (int id : e.getValue()) {
				int[] pc = net.pieces.get(id);
				RoadProfile p = streets.get(pc[0]).profile;
				for (int k = pc[1]; k <= pc[2]; k++) {
					RoadProfile.State st = p.stateAt(k);
					if (st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED) ground++;
				}
			}
			groundOf.put(e.getKey(), ground);
		}
		for (var e : groups.entrySet()) {
			List<Integer> group = e.getValue();
			int g = e.getKey(), ground = groundOf.get(g);
			if (ground < MIN_GROUP_GROUND || !roomToBuild(planner, streets, net, group)) continue;
			if (linkGroup(planner, streets, net, bounds, added, group, ISLAND_LINK, LINK_STEP, RoadProfile.MAX_SPAN, false, null)
					|| linkGroup(planner, streets, net, bounds, added, group, GRAND_LINK, 8, RoadProfile.GRAND_SPAN, true, null)
					|| linkGroup(planner, streets, net, bounds, added, group, GRAND_LINK, 8, RoadProfile.GRAND_SPAN, false, null)) {
				any = true;
				continue;
			}
			java.util.function.IntPredicate bigger = piece -> {
				int h = find(net.parent, piece);
				Integer gh = groundOf.get(h);
				return h != g && gh != null && gh >= MIN_GROUP_GROUND && (gh > ground || gh == ground && h < g);
			};
			if (linkGroup(planner, streets, net, bounds, added, group, ISLAND_LINK, LINK_STEP, RoadProfile.MAX_SPAN, false, bigger)
					|| linkGroup(planner, streets, net, bounds, added, group, GRAND_LINK, 8, RoadProfile.GRAND_SPAN, true, bigger)
					|| linkGroup(planner, streets, net, bounds, added, group, GRAND_LINK, 8, RoadProfile.GRAND_SPAN, false, bigger)) {
				any = true;
			}
		}
		return any;
	}

	private static final int LINK_PATCH = 500; // fewest dry points allowed for link

	private static boolean roomToBuild(CityPlanner planner, List<Road> streets, Net net, List<Integer> group) {
		for (int id : group) {
			int[] pc = net.pieces.get(id);
			Road road = streets.get(pc[0]);
			for (int k = pc[1]; k <= pc[2]; k += 8) {
				if (!onLand(road.profile.stateAt(k))) continue;
				double[] q = road.pointAt(k);
				if (planner.grade().patchSize(q[0], q[1]) >= LINK_PATCH) return true;
			}
		}
		return false;
	}

	private static boolean linkGroup(CityPlanner planner, List<Road> streets, Net net, Rect bounds, List<Road> added,
			List<Integer> group, double reach, int step, int maxSpan, boolean big, java.util.function.IntPredicate toward) {
		List<double[]> cands = new ArrayList<>();
		for (int id : group) {
			int[] pc = net.pieces.get(id);
			Road road = streets.get(pc[0]);
			for (int k = pc[1]; k <= pc[2]; k += step) {
				if (!onLand(road.profile.stateAt(k))) continue;
				double[] p = road.pointAt(k);
				for (Target c : targets(planner, streets, net, bounds, p[0], p[1], reach, 2, toward)) {
					double d = Math.hypot(c.x() - p[0], c.z() - p[1]);
					cands.add(new double[] {d, pc[0], k, c.x(), c.z(), c.h(), c.road()});
				}
			}
		}
		cands.sort(java.util.Comparator.comparingDouble((double[] c) -> c[0]).thenComparingDouble(c -> c[1])
				.thenComparingDouble(c -> c[2]));
		List<double[]> failed = new ArrayList<>();
		int tries = 0;
		for (double[] c : cands) {
			if (big && c[0] <= BIG_LINK) continue;
			Road from = streets.get((int) c[1]);
			double[] p = from.pointAt(c[2]);
			boolean repeat = false;
			for (double[] f : failed) {
				if (Math.hypot(f[0] - p[0], f[1] - p[1]) < 8 && Math.hypot(f[2] - c[3], f[3] - c[4]) < 8) {
					repeat = true;
					break;
				}
			}
			if (repeat) continue;
			if (++tries > 40) break;
			Road conn = connector(planner, streets, p[0], p[1], from.profile.height(c[2]),
					new Target(c[3], c[4], c[5], (int) c[6], -1), from, (int) c[1], maxSpan, big);
			if (conn == null) {
				failed.add(new double[] {p[0], p[1], c[3], c[4]});
				continue;
			}
			add(streets, added, conn);
			return true;
		}
		return false;
	}

	private static final double SHORE_RUN = 24;

	private static boolean leavesFromShore(Road c, boolean big) {
		RoadProfile p = c.profile;
		int n = p.samples(), first = -1, last = -1;
		for (int k = 0; k < n; k++) {
			if (p.stateAt(k) != RoadProfile.State.BRIDGE) continue;
			if (first < 0) first = k;
			last = k;
		}
		if (first < 0) return !big && c.length() <= ISLAND_LINK;
		double run = SHORE_RUN + c.halfWidth;
		if (first > run || (n - 1 - last) > run) return false;
		int land = 0;
		for (int k = first; k <= last; k++) if (p.stateAt(k) != RoadProfile.State.BRIDGE) land++;
		return land <= run;
	}

	static boolean strayBridge(RoadProfile p, int k) {
		int n = p.samples();
		if (k < 0 || k >= n || p.stateAt(k) != RoadProfile.State.BRIDGE) return false;
		int a = k, b = k;
		while (a > 0 && p.stateAt(a - 1) == RoadProfile.State.BRIDGE) a--;
		while (b < n - 1 && p.stateAt(b + 1) == RoadProfile.State.BRIDGE) b++;
		return a > 0 && p.stateAt(a - 1) == RoadProfile.State.NONE || b < n - 1 && p.stateAt(b + 1) == RoadProfile.State.NONE;
	}

	static void clearStraysUnder(List<Road> streets, Road link) {
		for (Road o : streets) {
			if (o == link || o.profile == null || !o.containsWithin(link.pointAt(link.length() / 2)[0], link.pointAt(link.length() / 2)[1], link.length() / 2 + o.halfWidth + 4)) continue;
			RoadProfile p = o.profile;
			for (int k = 0; k < p.samples(); k++) {
				if (!strayBridge(p, k)) continue;
				double[] q = o.pointAt(k);
				if (!link.containsWithin(q[0], q[1], link.halfWidth + o.halfWidth)) continue;
				int a = k, b = k;
				while (a > 0 && p.stateAt(a - 1) == RoadProfile.State.BRIDGE) a--;
				while (b < p.samples() - 1 && p.stateAt(b + 1) == RoadProfile.State.BRIDGE) b++;
				p.clear(a, b);
				k = b;
			}
		}
	}

	private static void add(List<Road> streets, List<Road> added, Road c) {
		streets.add(c);
		clearStraysUnder(streets, c);
		if (added != null) added.add(c);
	}

	private static boolean onLand(RoadProfile.State st) {
		return st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED;
	}

	private static List<Target> targets(CityPlanner planner, List<Road> streets, Net net, Rect bounds,
			double x, double z, double reach, int rootedOnly) {
		return targets(planner, streets, net, bounds, x, z, reach, rootedOnly, null);
	}

	private static List<Target> targets(CityPlanner planner, List<Road> streets, Net net, Rect bounds,
			double x, double z, double reach, int rootedOnly, java.util.function.IntPredicate toward) {
		List<Target> out = new ArrayList<>();
		for (int r = 0; r < net.pieceOf.length; r++) {
			Road o = streets.get(r);
			if (!o.containsWithin(x, z, reach)) continue;
			// Nearest built sample of this road
			RoadProfile p = o.profile;
			double s0 = o.arcAt(x, z);
			Target best = null;
			double bestD = reach;
			int m = p.samples();
			for (int k = 0; k < m; k++) {
				int piece = net.pieceOf[r][k];
				if (piece < 0 || !built(p.stateAt(k)) || (rootedOnly > 0 && !(toward != null ? toward.test(piece) : net.rooted(piece)))) continue;
				if (rootedOnly == 2 && !onLand(p.stateAt(k))) continue;
				if (Math.abs(k - s0) > reach + 2) continue;
				double[] q = o.pointAt(k);
				double d = Math.hypot(q[0] - x, q[1] - z);
				if (d < bestD) {
					bestD = d;
					best = new Target(q[0], q[1], p.height(k), r, piece);
				}
			}
			if (best != null) out.add(best);
		}
		if (toward != null) return out;
		double[][] edges = {{x, bounds.minZ(), x, bounds.minZ() - 2}, {x, bounds.maxZ(), x, bounds.maxZ() + 2},
				{bounds.minX(), z, bounds.minX() - 2, z}, {bounds.maxX(), z, bounds.maxX() + 2, z}};
		for (double[] g : edges) {
			if (Math.hypot(g[0] - x, g[1] - z) > reach) continue;
			CityPlanner.RoadPoint ap = planner.arterialPoint((int) Math.round(g[2]), (int) Math.round(g[3]));
			if (ap == null || !built(ap.state())) continue;
			if (rootedOnly > 0 && !planner.arterialConnected((int) Math.round(g[2]), (int) Math.round(g[3]))) continue;
			out.add(new Target(g[0], g[1], ap.height(), -1, -1));
		}
		return out;
	}

	/** A straight connecting street from (ax, az) to a target */
	private static Road connector(CityPlanner planner, List<Road> streets, double ax, double az, double ha,
			Target t, Road from, int fromIndex, int maxSpan, boolean big) {
		double len = Math.hypot(t.x() - ax, t.z() - az);
		if (len < 2) return null;
		double hb = t.h();
		if (Math.abs(hb - ha) > Math.max(1, len * RoadProfile.STREET_SLOPE - 1)) return null;
		boolean axis = Math.abs(t.x() - ax) < 0.5 || Math.abs(t.z() - az) < 0.5;
		double half = big ? CityPlanner.ARTERIAL_HALF_WIDTH : from.halfWidth;
		Road c = new Road(new double[] {ax, t.x()}, new double[] {az, t.z()}, half + (axis ? 0 : 0.5), Road.Kind.STREET);
		c.link = true;
		double hw = c.halfWidth + 1;
		for (double s = hw + 1; s <= len - hw - 1; s += 1) {
			double[] p = c.pointAt(s);
			for (int r = 0; r < streets.size(); r++) {
				if (r == fromIndex || r == t.road()) continue;
				Road o = streets.get(r);
				if (!o.containsWithin(p[0], p[1], o.halfWidth + hw)) continue;
				double oa = o.arcAt(p[0], p[1]);
				RoadProfile.State st = o.profile.state(oa);
				if (st != RoadProfile.State.NONE && !strayBridge(o.profile, (int) Math.round(oa))) return null;
			}
			if (planner.arterialPoint((int) Math.round(p[0]), (int) Math.round(p[1])) != null) return null;
		}
		long seed = Hashing.hash(planner.seed(), 91, (long) Math.floor(ax * 7 + t.x()), (long) Math.floor(az * 7 + t.z()));
		c.profile = RoadProfile.build(planner, c::pointAt, len, RoadProfile.endFlats(len, ha, hb, Math.min(3, len / 3)),
				RoadProfile.STREET_SLOPE, from.profile.style, false, c.axisAligned(), seed, maxSpan, from.profile.railing);
		for (int k = 0; k < c.profile.samples(); k++) if (!built(c.profile.stateAt(k))) return null;
		if (maxSpan > RoadProfile.MAX_SPAN && !leavesFromShore(c, big)) return null; // island links
		return c;
	}

	// links between superblocks

	/** What neighboring superblocks need to know to link across the arterials */
	record Summary(Rect bounds, List<Road> streets, List<List<double[]>> stranded, List<double[]> rooted,
			List<java.util.Set<Long>> strandedKeys, java.util.Set<Long> rootedKeys) {}

	static Summary summarize(CityPlanner planner, List<Road> streets, Rect bounds) {
		for (int round = 0; round < 6; round++) {
			if (!link(planner, streets, bounds, null)) break;
		}
		Net net = analyse(planner, streets, bounds);
		java.util.Map<Integer, List<double[]>> groups = new java.util.TreeMap<>();
		java.util.Map<Integer, Integer> ground = new java.util.HashMap<>();
		List<double[]> rooted = new ArrayList<>();
		for (int id = 0; id < net.root; id++) {
			int[] pc = net.pieces.get(id);
			Road road = streets.get(pc[0]);
			RoadProfile p = road.profile;
			boolean ok = net.rooted(id);
			int g = find(net.parent, id);
			for (int k = pc[1]; k <= pc[2]; k++) {
				RoadProfile.State st = p.stateAt(k);
				if (!ok && (st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED)) ground.merge(g, 1, Integer::sum);
				if (ok ? k % 4 != 0 : k % 8 != 0) continue;
				if (st != RoadProfile.State.GROUND && st != RoadProfile.State.ELEVATED) continue; // links start and end on land
				double[] q = road.pointAt(k);
				if (ok) {
					double edge = Math.min(Math.min(q[0] - bounds.minX(), bounds.maxX() - q[0]), Math.min(q[1] - bounds.minZ(), bounds.maxZ() - q[1]));
					if (edge <= GRAND_LINK) rooted.add(new double[] {q[0], q[1], p.height(k), pc[0]});
				} else {
					groups.computeIfAbsent(g, x -> new ArrayList<>()).add(new double[] {q[0], q[1], p.height(k), pc[0]});
				}
			}
		}
		List<List<double[]>> stranded = new ArrayList<>();
		List<java.util.Set<Long>> strandedKeys = new ArrayList<>();
		for (var e : groups.entrySet()) {
			if (ground.getOrDefault(e.getKey(), 0) < MIN_GROUP_GROUND) continue;
			boolean room = false;
			for (double[] q : e.getValue()) if (planner.grade().patchSize(q[0], q[1]) >= LINK_PATCH) room = true;
			if (!room) continue; // too little land to be worth it
			stranded.add(e.getValue());
			strandedKeys.add(net.keysOf(e.getKey()));
		}
		java.util.Set<Long> rootedKeys = new java.util.TreeSet<>();
		for (var e : net.stretches.entrySet()) if (find(net.parent, e.getValue()) == find(net.parent, net.root)) rootedKeys.add(e.getKey());
		List<Road> copies = new ArrayList<>();
		for (Road r : streets) copies.add(r.copy());
		return new Summary(bounds, copies, stranded, rooted, strandedKeys, rootedKeys);
	}

	/** Links from this superblock's stranded groups to the connected network of a neighbor */
	static List<Road> crossLinks(CityPlanner planner, Summary self, List<Summary> neighbors, List<Boolean> mayJoinStranded) {
		List<Road> out = new ArrayList<>();
		for (int g = 0; g < self.stranded().size(); g++) {
			List<double[]> group = self.stranded().get(g);
			java.util.Set<Long> keys = self.strandedKeys().get(g);
			List<List<double[]>> targets = new ArrayList<>();
			for (int ni = 0; ni < neighbors.size(); ni++) {
				Summary nb = neighbors.get(ni);
				List<double[]> pts = new ArrayList<>();
				if (java.util.Collections.disjoint(keys, nb.rootedKeys())) pts.addAll(nb.rooted());
				if (mayJoinStranded.get(ni)) {
					for (int h = 0; h < nb.stranded().size(); h++) {
						if (java.util.Collections.disjoint(keys, nb.strandedKeys().get(h))) pts.addAll(nb.stranded().get(h));
					}
				}
				targets.add(pts);
			}
			List<double[]> cands = new ArrayList<>();
			for (int gi = 0; gi < group.size(); gi++) {
				double[] a = group.get(gi);
				for (int ni = 0; ni < neighbors.size(); ni++) {
					List<double[]> pts = targets.get(ni);
					for (int ri = 0; ri < pts.size(); ri++) {
						double[] b = pts.get(ri);
						double d = Math.hypot(b[0] - a[0], b[1] - a[1]);
						if (d <= GRAND_LINK) cands.add(new double[] {d, gi, ni, ri});
					}
				}
			}
			cands.sort(java.util.Comparator.comparingDouble((double[] c) -> c[0]).thenComparingDouble(c -> c[1])
					.thenComparingDouble(c -> c[2]).thenComparingDouble(c -> c[3]));
			int want = group.size() * 8 >= BIG_ISLAND ? 2 : 1;
			List<double[]> made = new ArrayList<>(); // {fromX, fromZ, toX, toZ, neighbor}
			for (int slot = 0; slot < want; slot++) {
				Road link = null;
				for (boolean big : new boolean[] {true, false}) {
					List<double[]> failed = new ArrayList<>();
					int tries = 0;
					for (double[] c : cands) {
						if (big && c[0] <= BIG_LINK) continue;
						double[] a = group.get((int) c[1]);
						Summary nb = neighbors.get((int) c[2]);
						double[] b = targets.get((int) c[2]).get((int) c[3]);
						boolean skip = false;
						for (double[] m : made) {
							if (Math.hypot(m[0] - a[0], m[1] - a[1]) < SECOND_APART || m[4] == c[2]) skip = true;
						}
						for (double[] f : failed) {
							if (Math.hypot(f[0] - a[0], f[1] - a[1]) < 12 && Math.hypot(f[2] - b[0], f[3] - b[1]) < 12) skip = true;
						}
						if (skip) continue;
						if (++tries > 30) break;
						link = crossConnector(planner, self, (int) a[3], a, nb, (int) b[3], b, big);
						if (link == null) {
							failed.add(new double[] {a[0], a[1], b[0], b[1]});
							continue;
						}
						made.add(new double[] {a[0], a[1], b[0], b[1], c[2]});
						break;
					}
					if (link != null) break;
				}
				if (link == null) break;
				out.add(link);
			}
		}
		return out;
	}

	private static List<double[]> targetsOf(Summary nb, boolean stranded) {
		if (!stranded || nb.stranded().isEmpty()) return nb.rooted();
		List<double[]> out = new ArrayList<>(nb.rooted());
		for (List<double[]> g : nb.stranded()) out.addAll(g);
		return out;
	}

	private static Road crossConnector(CityPlanner planner, Summary from, int fromRoad, double[] a, Summary to, int toRoad, double[] b,
			boolean big) {
		double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
		if (len < 2) return null;
		if (Math.abs(b[2] - a[2]) > Math.max(1, len * RoadProfile.STREET_SLOPE - 1)) return null;
		Road src = from.streets().get(fromRoad);
		boolean axis = Math.abs(b[0] - a[0]) < 0.5 || Math.abs(b[1] - a[1]) < 0.5;
		double half = big ? CityPlanner.ARTERIAL_HALF_WIDTH : src.halfWidth;
		Road c = new Road(new double[] {a[0], b[0]}, new double[] {a[1], b[1]}, half + (axis ? 0 : 0.5), Road.Kind.STREET);
		c.external = true;
		c.link = true;
		double hw = c.halfWidth + 1;
		for (double s = hw + 1; s <= len - hw - 1; s += 1) {
			double[] p = c.pointAt(s);
			for (Summary sm : new Summary[] {from, to}) {
				for (int r = 0; r < sm.streets().size(); r++) {
					if (sm == from && r == fromRoad || sm == to && r == toRoad) continue;
					Road o = sm.streets().get(r);
					if (!o.containsWithin(p[0], p[1], o.halfWidth + hw)) continue;
					double oa = o.arcAt(p[0], p[1]);
					if (o.profile.state(oa) != RoadProfile.State.NONE && !strayBridge(o.profile, (int) Math.round(oa))) return null;
				}
			}
			CityPlanner.RoadPoint ap = planner.arterialPoint((int) Math.round(p[0]), (int) Math.round(p[1]));
			if (ap != null && ap.state() != RoadProfile.State.NONE) return null;
		}
		long seed = Hashing.hash(planner.seed(), 93, (long) Math.floor(a[0] * 7 + b[0]), (long) Math.floor(a[1] * 7 + b[1]));
		c.profile = RoadProfile.build(planner, c::pointAt, len, RoadProfile.endFlats(len, a[2], b[2], Math.min(3, len / 3)),
				RoadProfile.STREET_SLOPE, src.profile.style, false, c.axisAligned(), seed, RoadProfile.GRAND_SPAN, src.profile.railing);
		for (int k = 0; k < c.profile.samples(); k++) if (!built(c.profile.stateAt(k))) return null;
		if (!leavesFromShore(c, big)) return null;
		return c;
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
