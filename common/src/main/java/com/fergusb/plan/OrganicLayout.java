package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

final class OrganicLayout {
	private static final double HALF_WIDTH = (CityPlanner.STREET_WIDTH - 1) / 2.0;
	private static final int INTERIOR = -1;

	private final CityPlanner planner;
	private final Rect bounds;
	private final Rng rng;
	private final double dt;
	private final double spacing;

	private final List<double[]> nodes = new ArrayList<>();
	private final List<Integer> side = new ArrayList<>(); // INTERIOR or 0..3 for boundary entries
	private final List<int[]> edges = new ArrayList<>();

	OrganicLayout(CityPlanner planner, Rect bounds, Rng rng) {
		this.planner = planner;
		this.bounds = bounds;
		this.rng = rng;
		this.dt = planner.fields().downtown(bounds.centerX(), bounds.centerZ());
		this.spacing = ValueNoise.lerp(dt, 64, 48);
	}

	void plan(List<Road> streets, List<Lot> lots) {
		boolean spiderweb = Math.min(bounds.width(), bounds.depth()) > 260 && rng.chance(0.35);
		if (spiderweb) buildSpiderweb();
		else buildWeb();

		RoadRouter router = new RoadRouter(planner.grade(), bounds);
		for (int[] e : edges) {
			double[] a = nodes.get(e[0]), b = nodes.get(e[1]);
			if (Math.hypot(a[0] - b[0], a[1] - b[1]) < 2) continue;
			double[][] line = router.route(a[0], a[1], b[0], b[1]);
			streets.add(new Road(line[0], line[1], HALF_WIDTH, Road.Kind.STREET));
		}
		placeLots(streets, lots);
	}

	// graphs

	private void buildWeb() {
		double s = spacing;
		for (double x = bounds.minX() + s / 2; x < bounds.maxX() - s / 3; x += s) {
			for (double z = bounds.minZ() + s / 2; z < bounds.maxZ() - s / 3; z += s) {
				addNode(x + rng.range(-0.35, 0.35) * s, z + rng.range(-0.35, 0.35) * s, INTERIOR);
			}
		}
		addEntries();
		int n = nodes.size();
		double maxLen = 2.2 * s;
		for (int a = 0; a < n; a++) {
			for (int b = a + 1; b < n; b++) {
				if (side.get(a) != INTERIOR && side.get(b) != INTERIOR) continue; // never run along an arterial
				double dab = dist(a, b);
				if (dab > maxLen) continue;
				boolean blocked = false;
				for (int k = 0; k < n && !blocked; k++) {
					if (k != a && k != b && Math.max(dist(a, k), dist(b, k)) < dab) blocked = true;
				}
				if (!blocked) edges.add(new int[] {a, b});
			}
		}
	}

	private void buildSpiderweb() {
		double[] hub = {bounds.centerX(), bounds.centerZ()};
		double best = -Double.MAX_VALUE;
		for (int k = 0; k < 9; k++) {
			double x = bounds.centerX() + (k % 3 - 1) * bounds.width() * 0.15;
			double z = bounds.centerZ() + (k / 3 - 1) * bounds.depth() * 0.15;
			double g = planner.grade().grade(x, z);
			if (g > best) {
				best = g;
				hub = new double[] {x, z};
			}
		}
		int hubIdx = addNode(hub[0], hub[1], INTERIOR);
		List<Integer> prevRing = List.of(hubIdx);
		double ringStep = spacing * 0.9;
		double maxR = Math.max(bounds.width(), bounds.depth()) * 0.75;
		for (int ring = 1; ring * ringStep < maxR; ring++) {
			double r = ring * ringStep;
			int count = Math.max(6, (int) Math.round(2 * Math.PI * r / spacing));
			double offset = rng.range(0, 2 * Math.PI);
			Integer[] slots = new Integer[count];
			for (int k = 0; k < count; k++) {
				double ang = offset + k * 2 * Math.PI / count;
				double x = hub[0] + Math.cos(ang) * r, z = hub[1] + Math.sin(ang) * r;
				if (x < bounds.minX() + 8 || x > bounds.maxX() - 8 || z < bounds.minZ() + 8 || z > bounds.maxZ() - 8) continue;
				if (planner.grade().water(x, z)) continue;
				int idx = addNode(x, z, INTERIOR);
				if (idx >= 0) slots[k] = idx;
			}
			List<Integer> ringNodes = new ArrayList<>();
			for (int k = 0; k < count; k++) {
				if (slots[k] == null) continue;
				ringNodes.add(slots[k]);
				Integer next = slots[(k + 1) % count];
				if (next != null && count > 1) edges.add(new int[] {slots[k], next});
				int spoke = nearest(slots[k], prevRing);
				if (spoke >= 0 && (k % 2 == 0 || ring == 1)) edges.add(new int[] {slots[k], spoke});
			}
			if (!ringNodes.isEmpty()) prevRing = ringNodes;
		}
		// Connect the web to the arterials
		List<Integer> interior = new ArrayList<>();
		for (int k = 0; k < nodes.size(); k++) interior.add(k);
		int before = nodes.size();
		addEntries();
		for (int k = before; k < nodes.size(); k++) {
			int t = nearest(k, interior);
			if (t >= 0 && dist(k, t) < 2.5 * spacing) edges.add(new int[] {k, t});
		}
	}

	/** Points on the superblock edge where streets meet the arterials */
	private void addEntries() {
		double s = spacing * 1.3;
		for (double x = bounds.minX() + s / 2; x < bounds.maxX() - s / 3; x += s) {
			double jx = x + rng.range(-0.3, 0.3) * s;
			addNode(jx, bounds.minZ(), 0);
			addNode(x + rng.range(-0.3, 0.3) * s, bounds.maxZ(), 1);
		}
		for (double z = bounds.minZ() + s / 2; z < bounds.maxZ() - s / 3; z += s) {
			addNode(bounds.minX(), z + rng.range(-0.3, 0.3) * s, 2);
			addNode(bounds.maxX(), z + rng.range(-0.3, 0.3) * s, 3);
		}
	}

	private int addNode(double x, double z, int s) {
		x = Math.max(bounds.minX(), Math.min(bounds.maxX(), x));
		z = Math.max(bounds.minZ(), Math.min(bounds.maxZ(), z));
		if (s == INTERIOR && planner.grade().water(x, z)) return -1;
		nodes.add(new double[] {x, z});
		side.add(s);
		return nodes.size() - 1;
	}

	private int nearest(int from, List<Integer> candidates) {
		int best = -1;
		double bd = Double.MAX_VALUE;
		for (int c : candidates) {
			if (c == from || c < 0) continue;
			double d = dist(from, c);
			if (d < bd) {
				bd = d;
				best = c;
			}
		}
		return best;
	}

	private double dist(int a, int b) {
		double[] p = nodes.get(a), q = nodes.get(b);
		return Math.hypot(p[0] - q[0], p[1] - q[1]);
	}

	// lots

	private void placeLots(List<Road> streets, List<Lot> lots) {
		List<int[]> boxes = new ArrayList<>();
		for (Road r : streets) boxes.add(new int[] {r.minX, r.minZ, r.maxX, r.maxZ});
		SpatialIndex roadIndex = new SpatialIndex(boxes);
		List<Obb> accepted = new ArrayList<>();

		int minW = (int) ValueNoise.lerp(dt, 8, 12), maxW = (int) ValueNoise.lerp(dt, 16, 30);
		int maxD = (int) ValueNoise.lerp(dt, 26, 36);
		double parkChance = 0.02 + 0.05 * (1 - dt);

		// Pass 1: frontage lots
		for (Road road : streets) {
			double[] arc = arcLengths(road);
			double total = arc[arc.length - 1];
			for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
				double cursor = 2;
				while (cursor < total - 4) {
					int w = rng.nextInt(minW, maxW);
					Obb lot = null;
					for (int tryW = w; tryW >= minW && lot == null; tryW -= 4) {
						double mid = cursor + tryW / 2.0;
						if (mid > total - 2) break;
						for (int d = maxD; d >= 8 && lot == null; d -= 3) {
							Obb c = frontLot(road, arc, mid, sideSign, tryW, d);
							if (c != null && fits(c, streets, roadIndex, accepted)) {
								lot = c;
								w = tryW;
							}
						}
					}
					if (lot != null) {
						accepted.add(lot);
						lots.add(planner.finishLot(lot, rng, rng.chance(parkChance)));
						cursor += w;
					} else {
						cursor += 3;
					}
				}
			}
		}

		// Pass 2: infill
		int step = 6;
		for (int x = bounds.minX() + step / 2; x <= bounds.maxX(); x += step) {
			for (int z = bounds.minZ() + step / 2; z <= bounds.maxZ(); z += step) {
				if (claimed(x, z, accepted)) continue;
				double[] near = nearestStreet(x, z, streets, roadIndex);
				double ux = 1, uz = 0;
				if (near != null) {
					if (near[4] < HALF_WIDTH + 3) continue; // too close to a street; frontage pass owns this
					double[] snapped = snapDirection(near[2], near[3]);
					ux = snapped[0];
					uz = snapped[1];
				}
				Obb lot = null;
				for (int size = maxD; size >= 10 && lot == null; size -= 4) {
					int w = Math.max(10, size - rng.nextInt(0, 6));
					Obb c = new Obb(x, z, ux, uz, (w - 1) / 2.0, (size - 1) / 2.0);
					if (fits(c, streets, roadIndex, accepted)) lot = c;
				}
				if (lot != null) {
					accepted.add(lot);
					lots.add(planner.finishLot(lot, rng, rng.chance(parkChance * 2)));
				}
			}
		}
	}

	private static boolean claimed(int x, int z, List<Obb> accepted) {
		for (Obb o : accepted) {
			if (o.contains(x, z)) return true;
		}
		return false;
	}

	/** {px, pz, tx, tz, distance} for the closest street within the index cell */
	private static double[] nearestStreet(int x, int z, List<Road> streets, SpatialIndex index) {
		double[] best = null;
		for (int dx = -32; dx <= 32; dx += 32) {
			for (int dz = -32; dz <= 32; dz += 32) {
				for (int k : index.query(x + dx, z + dz)) {
					double[] n = streets.get(k).nearest(x, z);
					if (n != null && (best == null || n[4] < best[4])) best = n;
				}
			}
		}
		return best;
	}

	private Obb frontLot(Road road, double[] arc, double s, int sideSign, int w, int d) {
		int seg = 0;
		while (seg < arc.length - 2 && arc[seg + 1] < s) seg++;
		double ax = road.x(seg), az = road.z(seg), bx = road.x(seg + 1), bz = road.z(seg + 1);
		double segLen = arc[seg + 1] - arc[seg];
		if (segLen <= 0) return null;
		double t = (s - arc[seg]) / segLen;
		double px = ax + (bx - ax) * t, pz = az + (bz - az) * t;
		double tx = (bx - ax) / segLen, tz = (bz - az) / segLen;
		double nx = -tz * sideSign, nz = tx * sideSign;
		double off = road.halfWidth + 1.0 + (d - 1) / 2.0;
		double[] u = snapDirection(tx, tz);
		return new Obb(px + nx * off, pz + nz * off, u[0], u[1], (w - 1) / 2.0, (d - 1) / 2.0);
	}

	private static double[] snapDirection(double tx, double tz) {
		double ang = Math.atan2(tz, tx);
		double step = Math.PI / 4;
		double snapped = Math.round(ang / step) * step;
		if (Math.abs(ang - snapped) < Math.toRadians(12)) ang = snapped;
		return new double[] {Math.cos(ang), Math.sin(ang)};
	}

	private boolean fits(Obb lot, List<Road> streets, SpatialIndex roadIndex, List<Obb> accepted) {
		for (double[] c : lot.corners()) {
			if (c[0] < bounds.minX() || c[0] > bounds.maxX() || c[1] < bounds.minZ() || c[1] > bounds.maxZ()) return false;
		}
		// Sample the lot every 2 blocks against nearby streets
		for (double u = -lot.halfU; u <= lot.halfU + 1e-6; u += Math.min(2, Math.max(lot.halfU, 0.5))) {
			for (double v = -lot.halfV; v <= lot.halfV + 1e-6; v += Math.min(2, Math.max(lot.halfV, 0.5))) {
				double[] p = lot.point(u, v);
				for (int k : roadIndex.query((int) Math.round(p[0]), (int) Math.round(p[1]))) {
					Road r = streets.get(k);
					if (r.containsWithin(p[0], p[1], r.halfWidth + 0.9)) return false;
				}
			}
		}
		for (Obb o : accepted) {
			if (o.overlaps(lot)) return false;
		}
		return true;
	}

	private static double[] arcLengths(Road r) {
		double[] arc = new double[r.points()];
		for (int i = 1; i < r.points(); i++) {
			arc[i] = arc[i - 1] + Math.hypot(r.x(i) - r.x(i - 1), r.z(i) - r.z(i - 1));
		}
		return arc;
	}
}
