package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

final class GridLayout {
	private final CityPlanner planner;
	private final Rect bounds;
	private final Rng rng;
	private final int diagonals;

	GridLayout(CityPlanner planner, Rect bounds, Rng rng, int diagonals) {
		this.planner = planner;
		this.bounds = bounds;
		this.rng = rng;
		this.diagonals = diagonals;
	}
	private List<Road> external = List.of(); // links to neighboring superblocks
	private boolean streetsOnly;

	GridLayout options(List<Road> external, boolean streetsOnly) {
		this.external = external;
		this.streetsOnly = streetsOnly;
		return this;
	}

	void plan(List<Road> streets, List<Lot> lots, List<Junction> junctions) {
		List<Rect> blocks = new ArrayList<>();
		split(bounds, streets, blocks);
		RoadStyle style = profileStreets(planner, bounds, streets, rng);
		// Diagonals
		streetsSoFar = new ArrayList<>(streets);
		List<Road> diag = diagonalAvenues(style);
		streets.addAll(diag);
		// Waterfront roads
		List<Road> coast = CoastRoads.build(planner, bounds, streets);
		CoastRoads.profile(planner, coast, streets, style, rng);
		streets.addAll(coast);
		if (streetsOnly) return;
		streets.addAll(external);
		for (Road link : external) RoadNetwork.clearStraysUnder(streets, link);
		List<Obb> taken = new ArrayList<>();
		List<Road> links = new ArrayList<>(external);
		for (RoadNetwork.DeadEnd d : RoadNetwork.connect(planner, streets, bounds, links)) {
			Obb t = RoadNetwork.terminalLot(d);
			taken.add(t);
			lots.add(planner.finishLot(t, rng, false, streets));
		}
		List<Lot> blockLots = new ArrayList<>();
		for (Rect block : blocks) makeLots(block, blockLots, streets);
		List<Road> through = new ArrayList<>(coast);
		through.addAll(diag);
		through.addAll(links);
		for (Lot lot : blockLots) {
			if (!CoastRoads.overlaps(lot.bounds(), through, taken)) lots.add(lot);
		}
	}

	/** Gives every street a height profile */
	static RoadStyle profileStreets(CityPlanner planner, Rect bounds, List<Road> streets, Rng rng) {
		String biome = planner.terrain().biome(bounds.centerX(), bounds.centerZ());
		RoadStyle style = RoadStyle.forStreets(biome, planner.fields().downtown(bounds.centerX(), bounds.centerZ()),
				planner.fields().condition(bounds.centerX(), bounds.centerZ()), rng.nextDouble());
		int n = streets.size();
		// Pass 1
		List<List<RoadProfile.Flat>> endFlats = new ArrayList<>();
		List<List<double[]>> crossings = new ArrayList<>();
		int[][] parentOf = new int[n][2];
		for (int i = 0; i < n; i++) {
			endFlats.add(new ArrayList<>());
			crossings.add(new ArrayList<>());
		}
		for (int i = 0; i < n; i++) {
			Road road = streets.get(i);
			double len = road.length();
			for (int end = 0; end < 2; end++) {
				parentOf[i][end] = -1;
				double[] p = probe(road, end);
				double s0 = end == 0 ? 0 : len - 2, s1 = end == 0 ? 2 : len;
				CityPlanner.RoadPoint ap = planner.arterialPoint((int) Math.round(p[0]), (int) Math.round(p[1]));
				if (ap != null) {
					endFlats.get(i).add(new RoadProfile.Flat(s0, s1, ap.height()));
					continue;
				}
				for (int j = 0; j < i; j++) {
					Road parent = streets.get(j);
					if (!parent.containsWithin(p[0], p[1], parent.halfWidth + 0.5)) continue;
					crossings.get(j).add(new double[] {parent.arcAt(p[0], p[1]), road.halfWidth + 2, i, end});
					parentOf[i][end] = j;
					break;
				}
			}
		}
		// Pass 2
		for (int i = 0; i < n; i++) {
			Road road = streets.get(i);
			long seed = rng.nextLong();
			RoadProfile free = RoadProfile.build(planner, road::pointAt, road.length(), endFlats.get(i), RoadProfile.STREET_SLOPE, style, false, true, seed);
			List<RoadProfile.Flat> flats = new ArrayList<>(endFlats.get(i));
			for (double[] c : crossings.get(i)) {
				double s = c[0], half = c[1];
				Double h = null;
				for (RoadProfile.Flat f : flats) {
					if (f.s1() >= s - half && f.s0() <= s + half) h = f.h(); // a crossing already there
				}
				if (h == null) h = free.height(s);
				flats.add(new RoadProfile.Flat(s - half, s + half, h));
				int child = (int) c[2], end = (int) c[3];
				double clen = streets.get(child).length();
				endFlats.get(child).add(end == 0 ? new RoadProfile.Flat(0, 2, h) : new RoadProfile.Flat(clen - 2, clen, h));
			}
			road.profile = crossings.get(i).isEmpty() ? free
					: RoadProfile.build(planner, road::pointAt, road.length(), flats, RoadProfile.STREET_SLOPE, style, false, true, seed);
		}
		return style;
	}

	/** A point just past the given end (0 = start) of a street, inside the road it joins */
	private static double[] probe(Road road, int end) {
		double len = road.length();
		double[] a = road.pointAt(0), b = road.pointAt(len);
		double dx = (b[0] - a[0]) / Math.max(1e-6, len), dz = (b[1] - a[1]) / Math.max(1e-6, len);
		double reach = road.halfWidth + 1.5;
		return end == 0 ? new double[] {a[0] - dx * reach, a[1] - dz * reach} : new double[] {b[0] + dx * reach, b[1] + dz * reach};
	}

	private List<Road> diagonalAvenues(RoadStyle style) {
		List<Road> out = new ArrayList<>();
		if (diagonals == 0) return out;
		double cx = bounds.centerX() + rng.range(-0.15, 0.15) * bounds.width();
		double cz = bounds.centerZ() + rng.range(-0.15, 0.15) * bounds.depth();
		boolean flip = rng.chance(0.5);
		for (int k = 0; k < diagonals; k++) {
			double dx = 1, dz = (k == 0) == flip ? 1 : -1;
			double[] a = clip(cx, cz, -dx, -dz), b = clip(cx, cz, dx, dz);
			Road road = new Road(new double[] {a[0], b[0]}, new double[] {a[1], b[1]}, (CityPlanner.STREET_WIDTH - 1) / 2.0, Road.Kind.STREET);
			double len = road.length();
			if (len < 40) continue;
			List<RoadProfile.Flat> flats = new ArrayList<>();
			List<Road> crossed = new ArrayList<>(streetsSoFar);
			crossed.addAll(out);
			for (Road g : crossed) {
				if (g.profile == null) continue;
				for (double s = 0; s <= len; s += 1) {
					double[] p = road.pointAt(s);
					if (!g.containsWithin(p[0], p[1], 0.75)) continue;
					double h = g.profile.height(g.arcAt(p[0], p[1]));
					double half = g.halfWidth * 1.5 + 2;
					flats.add(new RoadProfile.Flat(Math.max(0, s - half), Math.min(len, s + half), h));
					break;
				}
			}
			double[] t = {dx / Math.sqrt(2), dz / Math.sqrt(2)};
			Double ps = planner.roadHeightForPin(a[0] - t[0] * 4, a[1] - t[1] * 4, List.of());
			Double pe = planner.roadHeightForPin(b[0] + t[0] * 4, b[1] + t[1] * 4, List.of());
			flats.addAll(RoadProfile.endFlats(len, ps, pe, 3));
			road.profile = RoadProfile.build(planner, road::pointAt, len, flats, RoadProfile.STREET_SLOPE, style, false, false, rng.nextLong());
			out.add(road);
		}
		streetsSoFar.addAll(out);
		return out;
	}

	private List<Road> streetsSoFar = new ArrayList<>();

	/** Where a ray from (x, z) along (dx, dz) leaves the superblock */
	private double[] clip(double x, double z, double dx, double dz) {
		double tx = dx > 0 ? (bounds.maxX() - x) / dx : (bounds.minX() - x) / dx;
		double tz = dz > 0 ? (bounds.maxZ() - z) / dz : (bounds.minZ() - z) / dz;
		double t = Math.min(tx, tz);
		return new double[] {x + dx * t, z + dz * t};
	}

	private void split(Rect r, List<Road> streets, List<Rect> blocks) {
		double dt = planner.fields().downtown(r.centerX(), r.centerZ());
		int maxSize = (int) ValueNoise.lerp(dt, 52, 100);
		if (r.width() <= maxSize && r.depth() <= maxSize) {
			blocks.add(r);
			return;
		}
		int w = CityPlanner.STREET_WIDTH;
		boolean cutAcrossX = r.width() >= r.depth(); // street runs along Z, cutting the X extent
		int len = cutAcrossX ? r.width() : r.depth();
		int offset = (int) (len * rng.range(0.38, 0.62));
		if (offset < 20 || len - offset - w < 20) {
			blocks.add(r);
			return;
		}
		Rect a, b, street;
		if (cutAcrossX) {
			int s = r.minX() + offset;
			street = new Rect(s, r.minZ(), s + w - 1, r.maxZ());
			a = new Rect(r.minX(), r.minZ(), s - 1, r.maxZ());
			b = new Rect(s + w, r.minZ(), r.maxX(), r.maxZ());
		} else {
			int s = r.minZ() + offset;
			street = new Rect(r.minX(), s, r.maxX(), s + w - 1);
			a = new Rect(r.minX(), r.minZ(), r.maxX(), s - 1);
			b = new Rect(r.minX(), s + w, r.maxX(), r.maxZ());
		}
		streets.add(Road.ofRect(street));
		split(a, streets, blocks);
		split(b, streets, blocks);
	}

	private void makeLots(Rect block, List<Lot> out, List<Road> streets) {
		double dt = planner.fields().downtown(block.centerX(), block.centerZ());
		if (planner.parkRoll(block.centerX(), block.centerZ(), 0.006 + 0.015 * (1 - dt))) {
			out.add(planner.finishLot(Obb.ofRect(block), rng, true, streets));
			return;
		}
		if (dt > 0.5 && Math.max(block.width(), block.depth()) <= 80 && rng.chance(0.4 * dt)) {
			out.add(planner.finishLot(Obb.ofRect(block), rng, false, streets));
			return;
		}
		boolean alongX = block.width() >= block.depth(); // lots line up along the long axis
		int shortLen = alongX ? block.depth() : block.width();
		int shortMin = alongX ? block.minZ() : block.minX();
		int shortMax = alongX ? block.maxZ() : block.maxX();
		List<int[]> rows = new ArrayList<>(); // [lo, hi] on the short axis
		if (shortLen >= 20) {
			int mid = shortMin + shortLen / 2 + rng.nextInt(-2, 2);
			rows.add(new int[] {shortMin, mid - 1});
			rows.add(new int[] {mid, shortMax});
		} else {
			rows.add(new int[] {shortMin, shortMax});
		}
		int longMin = alongX ? block.minX() : block.minZ();
		int longMax = alongX ? block.maxX() : block.maxZ();
		int minW = (int) ValueNoise.lerp(dt, 9, 16);
		int maxW = (int) ValueNoise.lerp(dt, 20, 56);
		for (int[] row : rows) {
			int cursor = longMin;
			while (cursor <= longMax) {
				int remaining = longMax - cursor + 1;
				int w = rng.nextInt(minW, maxW);
				if (remaining - w < minW) w = remaining;
				Rect lot = alongX
						? new Rect(cursor, row[0], cursor + w - 1, row[1])
						: new Rect(row[0], cursor, row[1], cursor + w - 1);
				out.add(planner.finishLot(Obb.ofRect(lot), rng, false, streets));
				cursor += w;
			}
		}
	}
}
