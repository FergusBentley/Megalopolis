package com.fergusb.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CityPlanner {
	public static final int CELL = 512;
	public static final int ARTERIAL_HALF_WIDTH = 5; // 11 blocks wide
	public static final int STREET_WIDTH = 5;
	public static final int FLOOR_HEIGHT = 4;
	public static final int MAX_FLOORS = 40;
	private static final int MAX_EXTRA_FLOORS = 16; // for dropdowns
	private static final int WILD_REACH = 12;

	private final Set<Long> neighborsQueued = ConcurrentHashMap.newKeySet();

	private final long seed;
	private final TerrainSampler terrain;
	private final DistrictFields fields;
	private final GradeField grade;
	private final Map<Long, CompletableFuture<Superblock>> cache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<Superblock>> e) {
			return size() > 96;
		}
	};

	public CityPlanner(long seed, TerrainSampler terrain) {
		this.seed = seed;
		this.terrain = terrain;
		this.fields = new DistrictFields(seed);
		this.grade = new GradeField(terrain);
	}

	public long seed() {
		return seed;
	}

	public GradeField grade() {
		return grade;
	}

	public DistrictFields fields() {
		return fields;
	}

	public TerrainSampler terrain() {
		return terrain;
	}

	// arterials

	public int arterialX(int col) {
		return col * CELL + 64 + (int) (Hashing.unit(Hashing.hash(seed, 1, col)) * (CELL - 128));
	}

	public int arterialZ(int row) {
		return row * CELL + 64 + (int) (Hashing.unit(Hashing.hash(seed, 2, row)) * (CELL - 128));
	}

	public int arterialOffsetX(int x) {
		int c = Math.floorDiv(x, CELL);
		for (int k = c - 1; k <= c + 1; k++) {
			int d = x - arterialX(k);
			if (Math.abs(d) <= ARTERIAL_HALF_WIDTH) return d;
		}
		return Integer.MAX_VALUE;
	}

	public int arterialOffsetZ(int z) {
		int c = Math.floorDiv(z, CELL);
		for (int k = c - 1; k <= c + 1; k++) {
			int d = z - arterialZ(k);
			if (Math.abs(d) <= ARTERIAL_HALF_WIDTH) return d;
		}
		return Integer.MAX_VALUE;
	}

	private int superColumn(int x) {
		int c = Math.floorDiv(x, CELL);
		return x < arterialX(c) ? c - 1 : c;
	}

	private int superRow(int z) {
		int r = Math.floorDiv(z, CELL);
		return z < arterialZ(r) ? r - 1 : r;
	}

	// road heights

	private int arterialColAt(int x) {
		int c = Math.floorDiv(x, CELL);
		for (int k = c - 1; k <= c + 1; k++) if (Math.abs(x - arterialX(k)) <= ARTERIAL_HALF_WIDTH) return k;
		return Integer.MIN_VALUE;
	}

	private int arterialRowAt(int z) {
		int r = Math.floorDiv(z, CELL);
		for (int k = r - 1; k <= r + 1; k++) if (Math.abs(z - arterialZ(k)) <= ARTERIAL_HALF_WIDTH) return k;
		return Integer.MIN_VALUE;
	}

	private final Map<Long, RoadProfile> arterialProfiles = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, RoadProfile> e) {
			return size() > 4096;
		}
	};

	/** Arterials are level for this far either side of an arterial crossing */
	private static final double JUNCTION_FLAT = ARTERIAL_HALF_WIDTH + 2;

	private double intersectionHeight(int c, int r) {
		return RoadProfile.junctionHeight(this, arterialX(c), arterialZ(r));
	}

	private RoadProfile arterialProfile(boolean alongZ, int line, int seg) {
		long key = Hashing.hash(alongZ ? 11 : 12, line, seg);
		synchronized (arterialProfiles) {
			RoadProfile p = arterialProfiles.get(key);
			if (p != null) return p;
		}
		RoadProfile p;
		if (alongZ) {
			int x = arterialX(line), z0 = arterialZ(seg), z1 = arterialZ(seg + 1);
			RoadStyle style = RoadStyle.forArterial(terrain.biome(x, (z0 + z1) / 2), Hashing.unit(Hashing.hash(seed, 13, line, seg)));
			p = RoadProfile.build(this, s -> new double[] {x, z0 + s}, z1 - z0,
					RoadProfile.endFlats(z1 - z0, intersectionHeight(line, seg), intersectionHeight(line, seg + 1), JUNCTION_FLAT),
					0.25, style, true, true, Hashing.hash(seed, 15, line, seg));
		} else {
			int z = arterialZ(line), x0 = arterialX(seg), x1 = arterialX(seg + 1);
			RoadStyle style = RoadStyle.forArterial(terrain.biome((x0 + x1) / 2, z), Hashing.unit(Hashing.hash(seed, 14, line, seg)));
			p = RoadProfile.build(this, s -> new double[] {x0 + s, z}, x1 - x0,
					RoadProfile.endFlats(x1 - x0, intersectionHeight(seg, line), intersectionHeight(seg + 1, line), JUNCTION_FLAT),
					0.25, style, true, true, Hashing.hash(seed, 16, line, seg));
		}
		synchronized (arterialProfiles) {
			arterialProfiles.put(key, p);
		}
		return p;
	}

	/** Where a column sits on the road network */
	public record RoadPoint(double height, RoadProfile.State state, RoadProfile profile, double s, boolean centerLine,
							boolean arterial, boolean straight) {
		public RoadStyle style() {
			return profile.style;
		}

		/** The bridge here, or null */
		public RoadProfile.Bridge bridge() {
			return state == RoadProfile.State.BRIDGE ? profile.bridge(s) : null;
		}

		public RoadProfile.Railing railing() {
			return profile.railing;
		}

		public String biome() {
			return profile.biome;
		}
	}

	public RoadPoint arterialPoint(int x, int z) {
		int c = arterialColAt(x), r = arterialRowAt(z);
		if (c != Integer.MIN_VALUE && r != Integer.MIN_VALUE) {
			RoadProfile[] arms = {arterialProfile(true, c, r), arterialProfile(true, c, r - 1),
					arterialProfile(false, r, c), arterialProfile(false, r, c - 1)};
			RoadProfile p = arms[0];
			int bestVotes = 0;
			for (RoadProfile a : arms) {
				int votes = 0;
				for (RoadProfile b : arms) if (b.style == a.style) votes++;
				if (votes > bestVotes) {
					bestVotes = votes;
					p = a;
				}
			}
			double h = arms[0].height(0); // every arm is held at the crossing's height here
			return new RoadPoint(h, arms[0].state(0), p, 0, false, true, true);
		}
		if (c != Integer.MIN_VALUE) {
			int r0 = Math.floorDiv(z, CELL), row = z < arterialZ(r0) ? r0 - 1 : r0;
			RoadProfile p = arterialProfile(true, c, row);
			double s = z - arterialZ(row);
			return new RoadPoint(p.height(s), p.state(s), p, s, x == arterialX(c), true, true);
		}
		if (r == Integer.MIN_VALUE) return null;
		int c0 = Math.floorDiv(x, CELL), seg = x < arterialX(c0) ? c0 - 1 : c0;
		RoadProfile p = arterialProfile(false, r, seg);
		double s = x - arterialX(seg);
		return new RoadPoint(p.height(s), p.state(s), p, s, z == arterialZ(r), true, true);
	}

	/** Road point for any road cell */
	public RoadPoint roadPoint(Cell cell, int x, int z) {
		if (cell.kind() == Kind.ARTERIAL) return arterialPoint(x, z);
		Road road = cell.road();
		RoadProfile p = road.profile;
		RoadMap map = cell.block().map;
		RoadProfile.State st = map.state(x, z);
		double s = st == RoadProfile.State.BRIDGE ? road.arcAt(x, z) : 0;
		return new RoadPoint(map.height(x, z), st, p, s, false, false, road.axisAligned());
	}

	/** The finished ground height at a column */
	public double groundLevel(int x, int z) {
		Cell c = classify(x, z);
		if (c.isRoad()) return roadPoint(c, x, z).height();
		return grade.grade(x, z);
	}

	/** Height of whatever road is at (x, z) during planning */
	Double roadHeightForPin(double x, double z, List<Road> streets) {
		int ix = (int) Math.round(x), iz = (int) Math.round(z);
		RoadPoint ap = arterialPoint(ix, iz);
		if (ap != null) return ap.height();
		for (Road r : streets) {
			if (r.profile != null && r.containsWithin(x, z, r.halfWidth + 0.5)) return r.profile.height(r.arcAt(x, z));
		}
		return null;
	}

	// queries

	public enum Kind { ARTERIAL, STREET, LOT, GAP, WILD }

	public record Cell(Kind kind, Road road, Lot lot, int arterialDx, int arterialDz, Junction junction, Superblock block) {
		public boolean isRoad() {
			return kind == Kind.ARTERIAL || kind == Kind.STREET;
		}
	}

	public Cell classify(int x, int z) {
		int dx = arterialOffsetX(x), dz = arterialOffsetZ(z);
		if (dx != Integer.MAX_VALUE || dz != Integer.MAX_VALUE) {
			return new Cell(Kind.ARTERIAL, null, null, dx, dz, null, null);
		}
		Superblock sb = superblock(superColumn(x), superRow(z));
		Road road = sb.streetAt(x, z);
		Junction jn = sb.junctionAt(x, z);
		if (jn != null) return new Cell(Kind.STREET, road != null ? road : jn.road(), null, 0, 0, jn, sb);
		if (road != null) return new Cell(Kind.STREET, road, null, 0, 0, null, sb);
		if (sb.wildAt(x, z)) return new Cell(Kind.WILD, null, null, 0, 0, null, sb);
		Lot lot = sb.lotAt(x, z);
		if (lot != null) return new Cell(Kind.LOT, null, lot, 0, 0, null, sb);
		return new Cell(Kind.GAP, null, null, 0, 0, null, sb);
	}

	public Superblock superblockAt(int x, int z) {
		return superblock(superColumn(x), superRow(z));
	}

	public Superblock superblock(int i, int j) {
		long key = key(i, j);
		CompletableFuture<Superblock> f;
		boolean mine = false;
		synchronized (cache) {
			f = cache.get(key);
			if (f == null) {
				f = new CompletableFuture<>();
				cache.put(key, f);
				mine = true;
			}
		}
		// The first time a superblock is used, queue its neighbors
		if (neighborsQueued.add(key)) {
			for (int di = -1; di <= 1; di++) {
				for (int dj = -1; dj <= 1; dj++) {
					if (di != 0 || dj != 0) prefetch(i + di, j + dj);
				}
			}
		}
		if (mine) complete(f, key, i, j);
		return f.join();
	}

	/** Plans a superblock on the background executor unless it is already planned or in progress */
	public void prefetch(int i, int j) {
		long key = key(i, j);
		CompletableFuture<Superblock> f;
		synchronized (cache) {
			if (cache.containsKey(key)) return;
			f = new CompletableFuture<>();
			cache.put(key, f);
		}
		final CompletableFuture<Superblock> future = f;
		PREFETCH.execute(() -> complete(future, key, i, j));
	}

	private void complete(CompletableFuture<Superblock> f, long key, int i, int j) {
		try {
			f.complete(plan(i, j));
		} catch (Throwable t) {
			f.completeExceptionally(t);
			synchronized (cache) {
				cache.remove(key);
			}
		}
	}

	private static long key(int i, int j) {
		return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
	}

	/** Low-priority daemon threads */
	private static final ExecutorService PREFETCH = Executors.newFixedThreadPool(
			Math.max(1, Runtime.getRuntime().availableProcessors() / 4), r -> {
				Thread t = new Thread(r, "Megalopolis planner");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY + 1);
				return t;
			});

	// planning

	private Superblock plan(int i, int j) {
		Rect bounds = new Rect(
				arterialX(i) + ARTERIAL_HALF_WIDTH + 1, arterialZ(j) + ARTERIAL_HALF_WIDTH + 1,
				arterialX(i + 1) - ARTERIAL_HALF_WIDTH - 1, arterialZ(j + 1) - ARTERIAL_HALF_WIDTH - 1);
		Rng rng = new Rng(Hashing.hash(seed, 3, i, j));
		List<Road> streets = new ArrayList<>();
		List<Lot> lots = new ArrayList<>();
		List<Junction> junctions = new ArrayList<>();
		boolean organic = isOrganic(bounds);
		if (organic) {
			new OrganicLayout(this, bounds, rng).plan(streets, lots, junctions);
		} else {
			new GridLayout(this, bounds, rng, diagonals(i, j)).plan(streets, lots, junctions);
		}
		lots = ParkSpreader.spread(lots, new Rng(Hashing.hash(seed, 5, i, j)));
		java.util.BitSet wild = wildAroundTunnels(bounds, streets);
		lots.removeIf(l -> touchesWild(l, bounds, wild));
		lots = FrontageFill.fill(this, bounds, streets, junctions, lots, wild, organic, new Rng(Hashing.hash(seed, 6, i, j)));
		RoadMap map = RoadMap.build(bounds, streets, junctions);
		lots = downToStreets(lots, map);
		return new Superblock(i, j, bounds, organic, streets, BuildingDesigner.design(lots, fields, terrain), junctions, wild, map);
	}

	static boolean walkable(RoadProfile.State st) {
		return st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED;
	}

	/** Lowest walkable street height outside a building's first floor */
	double lowestStreet(Lot lot, RoadMap map, java.util.Map<String, Integer> tally, double[] highest) {
		if (highest != null) highest[0] = Double.NEGATIVE_INFINITY;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		double lo = Double.MAX_VALUE;
		Obb b = lot.bounds();
		int reach = Math.max(b.widthU() - lot.footprint().widthU(), b.depthV() - lot.footprint().depthV()) / 2 + 4;
		for (int x = b.minX; x <= b.maxX; x++) {
			for (int z = b.minZ; z <= b.maxZ; z++) {
				if (!BuildingShape.insideAt(lot, x, z, 0)) continue;
				for (int[] d : dirs) {
					if (BuildingShape.insideAt(lot, x + d[0], z + d[1], 0)) continue;
					String hit = "nothing";
					for (int k = 1; k <= reach; k++) {
						int px = x + d[0] * k, pz = z + d[1] * k;
						if (map.built(px, pz)) {
							RoadProfile.State st = map.state(px, pz);
							hit = "street " + st;
							if (walkable(st)) {
								lo = Math.min(lo, map.height(px, pz));
								if (highest != null) highest[0] = Math.max(highest[0], map.height(px, pz));
							}
							break;
						}
						RoadPoint ap = arterialPoint(px, pz);
						if (ap != null) {
							hit = "arterial " + ap.state();
							if (walkable(ap.state())) {
								lo = Math.min(lo, ap.height());
								if (highest != null) highest[0] = Math.max(highest[0], ap.height());
							}
							break;
						}
					}
					if (tally != null) tally.merge(hit, 1, Integer::sum);
				}
			}
		}
		return lo;
	}

	public String streetsAround(Lot lot) {
		if (lot.use() != Lot.Use.BUILDING || lot.footprint() == null) return "";
		java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
		double lo = lowestStreet(lot, superblockAt((int) lot.bounds().cx, (int) lot.bounds().cz).map, tally, null);
		return String.format(" | lowest street %s, around: %s", lo == Double.MAX_VALUE ? "none" : String.valueOf(lo), tally);
	}

	private List<Lot> downToStreets(List<Lot> lots, RoadMap map) {
		List<Lot> out = new ArrayList<>(lots.size());
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (Lot lot : lots) {
			if (lot.use() != Lot.Use.BUILDING || lot.footprint() == null) {
				out.add(lot);
				continue;
			}
			double[] hiOut = new double[1];
			double lo = lowestStreet(lot, map, null, hiOut);
			if (lo == Double.MAX_VALUE) {
				out.add(lot);
				continue;
			}
			int base = (int) Math.ceil(lo) - 1;
			int drop = lot.baseY() - base;
			int extra = Math.max(0, Math.min(MAX_EXTRA_FLOORS, (int) Math.ceil(drop / (double) FLOOR_HEIGHT)));
			if (drop > extra * FLOOR_HEIGHT) base = lot.baseY() - extra * FLOOR_HEIGHT;
			int lift = Math.max(0, (int) Math.ceil((hiOut[0] - 1 - base) / (double) FLOOR_HEIGHT));
			out.add(new Lot(lot.bounds(), lot.footprint(), lot.use(), base, Math.min(MAX_FLOORS, lot.floors() + extra),
					lot.downtown(), lot.condition(), lot.seed(), lot.spec(), lot.mask(), lift));
		}
		return out;
	}

	private java.util.BitSet wildAroundTunnels(Rect b, List<Road> streets) {
		int d = b.depth();
		java.util.BitSet wild = new java.util.BitSet(b.width() * d);
		List<double[]> centers = new ArrayList<>(); // {x, z, radius}
		for (Road road : streets) {
			RoadProfile p = road.profile;
			for (int k = 0; k < p.samples(); k += 2) {
				if (p.stateAt(k) != RoadProfile.State.TUNNEL) continue;
				double[] c = road.pointAt(Math.min(k, road.length()));
				centers.add(new double[] {c[0], c[1], WILD_REACH + road.halfWidth});
			}
		}
		for (int t = 0; t < Math.max(b.width(), b.depth()); t += 2) {
			int[][] outside = {
					{b.minX() + t, b.minZ() - 1 - ARTERIAL_HALF_WIDTH}, {b.minX() + t, b.maxZ() + 1 + ARTERIAL_HALF_WIDTH},
					{b.minX() - 1 - ARTERIAL_HALF_WIDTH, b.minZ() + t}, {b.maxX() + 1 + ARTERIAL_HALF_WIDTH, b.minZ() + t}};
			for (int[] o : outside) {
				RoadPoint ap = arterialPoint(o[0], o[1]);
				if (ap != null && ap.state() == RoadProfile.State.TUNNEL) centers.add(new double[] {o[0], o[1], WILD_REACH + ARTERIAL_HALF_WIDTH});
			}
		}
		for (double[] c : centers) {
			int r = (int) Math.ceil(c[2]);
			for (int x = (int) c[0] - r; x <= c[0] + r; x++) {
				for (int z = (int) c[1] - r; z <= c[1] + r; z++) {
					if (x < b.minX() || z < b.minZ() || x > b.maxX() || z > b.maxZ()) continue;
					if ((x - c[0]) * (x - c[0]) + (z - c[1]) * (z - c[1]) > c[2] * c[2]) continue;
					wild.set((x - b.minX()) * d + (z - b.minZ()));
				}
			}
		}
		return wild;
	}

	private static boolean touchesWild(Lot lot, Rect b, java.util.BitSet wild) {
		if (wild.isEmpty()) return false;
		Obb o = lot.bounds();
		for (int x = o.minX; x <= o.maxX; x += 2) {
			for (int z = o.minZ; z <= o.maxZ; z += 2) {
				if (x < b.minX() || z < b.minZ() || x > b.maxX() || z > b.maxZ() || !lot.contains(x, z)) continue;
				if (wild.get((x - b.minX()) * b.depth() + (z - b.minZ()))) return true;
			}
		}
		return false;
	}

	static final double ORGANIC_THRESHOLD = 0.85;

	int diagonals(int i, int j) {
		double r = Hashing.unit(Hashing.hash(seed, 21, i, j));
		return r < 0.2 ? 1 : r < 0.32 ? 2 : 0;
	}

	/** What kind of street layout a superblock has */
	public String layoutType(int i, int j) {
		Rect b = new Rect(arterialX(i) + ARTERIAL_HALF_WIDTH + 1, arterialZ(j) + ARTERIAL_HALF_WIDTH + 1,
				arterialX(i + 1) - ARTERIAL_HALF_WIDTH - 1, arterialZ(j + 1) - ARTERIAL_HALF_WIDTH - 1);
		if (isOrganic(b)) return "organic";
		return diagonals(i, j) > 0 ? "diagonal" : "grid";
	}

	/** Superblock indices (i, j) containing a world position */
	public int[] superblockIndex(int x, int z) {
		return new int[] {superColumn(x), superRow(z)};
	}

	private boolean isOrganic(Rect b) {
		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
		for (int a = 0; a <= 2; a++) {
			for (int c = 0; c <= 2; c++) {
				double g = grade.grade(b.minX() + a * (b.width() - 1) / 2.0, b.minZ() + c * (b.depth() - 1) / 2.0);
				min = Math.min(min, g);
				max = Math.max(max, g);
			}
		}
		double reliefBoost = DistrictFields.clamp01((max - min - 16) / 60.0) * 0.25;
		return fields.organic(b.centerX(), b.centerZ()) + reliefBoost > ORGANIC_THRESHOLD;
	}

	Lot finishLot(Obb r, Rng rng, boolean park, List<Road> streets) {
		long lotSeed = rng.nextLong();
		double dt = fields.downtown(r.cx, r.cz);
		double cond = fields.condition(r.cx, r.cz);

		// Floor on same smoothed grade
		int baseY = (int) Math.round(grade.grade(r.cx, r.cz));
		int maxDeviation = 0;
		int waterSamples = 0;
		List<double[]> pts = new ArrayList<>(List.of(r.corners()));
		pts.add(new double[] {r.cx, r.cz});
		for (double[] p : pts) {
			int sx = (int) Math.round(p[0] / 4) * 4, sz = (int) Math.round(p[1] / 4) * 4;
			TerrainSampler.Sample s = terrain.sample(sx, sz);
			if (s.water() || grade.smallIslandAt(p[0], p[1])) waterSamples++;
			maxDeviation = Math.max(maxDeviation, Math.abs(s.groundY() - baseY));
		}
		boolean fronts = frontsBuiltRoad(r, streets) || (waterSamples < 3 && nearBuiltStreet(r, streets, 16));
		if (waterSamples > 0) baseY = Math.max(baseY, terrain.seaLevel() + 2);
		baseY = Math.max(baseY, frontFloor(r, streets, baseY));
		if (waterSamples >= 4 && !fronts) {
			return new Lot(r, null, Lot.Use.UNBUILDABLE, baseY, 0, dt, cond, lotSeed, null, null);
		}
		if (park) return new Lot(r, null, Lot.Use.PARK, baseY, 0, dt, cond, lotSeed, null, null);
		if (!fronts) return new Lot(r, null, Lot.Use.GREEN, baseY, 0, dt, cond, lotSeed, null, null);

		Rng lr = new Rng(lotSeed);
		BuildingSpec.Purpose purpose = BuildingDesigner.purpose(lr, dt, r.widthU(), r.depthV());
		Obb fp = r.shrink(BuildingDesigner.setback(purpose, dt));
		// Small lots skip the setback so a small building still fits
		if (fp == null || fp.widthU() < 5 || fp.depthV() < 5) fp = r;
		if (fp.widthU() < 5 || fp.depthV() < 5) {
			return new Lot(r, null, Lot.Use.GREEN, baseY, 0, dt, cond, lotSeed, null, null);
		}

		int floors = BuildingDesigner.floors(lr, purpose, dt, Math.min(fp.widthU(), fp.depthV()));
		BuildingSpec draft = new BuildingSpec(purpose, BuildingSpec.Shape.RECT, BuildingSpec.Massing.STRAIGHT,
				BuildingSpec.Roof.FLAT, BuildingSpec.WindowStyle.BANDS, BuildingSpec.PaletteFamily.WHITE_MODERN, 0, lotSeed, "plains", false);
		return new Lot(r, fp, Lot.Use.BUILDING, baseY, floors, dt, cond, lotSeed, draft, null);
	}

	/** True if a built road runs along any side of the shape */
	boolean frontsBuiltRoad(Obb r, List<Road> streets) {
		double[][] dirs = {{r.ux, r.uz, r.halfU}, {-r.ux, -r.uz, r.halfU}, {-r.uz, r.ux, r.halfV}, {r.uz, -r.ux, r.halfV}};
		for (double[] dv : dirs) {
			for (int step = 1; step <= 4; step++) {
				double px = r.cx + dv[0] * (dv[2] + step), pz = r.cz + dv[1] * (dv[2] + step);
				RoadPoint ap = arterialPoint((int) Math.round(px), (int) Math.round(pz));
				if (ap != null) {
					if (ap.state() != RoadProfile.State.BRIDGE && ap.state() != RoadProfile.State.NONE) return true;
					continue;
				}
				for (Road road : streets) {
					if (road.profile == null || !road.containsWithin(px, pz, road.halfWidth + 0.5)) continue;
					RoadProfile.State st = road.profile.state(road.arcAt(px, pz));
					if (st != RoadProfile.State.BRIDGE && st != RoadProfile.State.NONE) return true;
				}
			}
		}
		return false;
	}

	/** True if a built street passes within 'reach' blocks of the shape's edge */
	private static boolean nearBuiltStreet(Obb r, List<Road> streets, double reach) {
		double rad = Math.max(r.halfU, r.halfV) + reach;
		for (Road road : streets) {
			if (road.profile == null || !road.containsWithin(r.cx, r.cz, rad)) continue;
			double[] n = road.nearest(r.cx, r.cz);
			RoadProfile.State st = road.profile.state(road.arcAt(n[0], n[1]));
			if (st == RoadProfile.State.GROUND || st == RoadProfile.State.ELEVATED) return true;
		}
		return false;
	}

	int frontFloor(Obb r, List<Road> streets, int fallback) {
		double best = Double.NEGATIVE_INFINITY;
		double[][] dirs = {{r.ux, r.uz, r.halfU}, {-r.ux, -r.uz, r.halfU}, {-r.uz, r.ux, r.halfV}, {r.uz, -r.ux, r.halfV}};
		for (double[] dv : dirs) {
			for (int step = 1; step <= 3; step++) {
				double px = r.cx + dv[0] * (dv[2] + step), pz = r.cz + dv[1] * (dv[2] + step);
				RoadPoint ap = arterialPoint((int) Math.round(px), (int) Math.round(pz));
				if (ap != null) {
					if (ap.state() == RoadProfile.State.GROUND) best = Math.max(best, ap.height());
					continue;
				}
				for (Road road : streets) {
					if (road.profile == null || !road.containsWithin(px, pz, road.halfWidth + 0.5)) continue;
					double s = road.arcAt(px, pz);
					if (road.profile.state(s) == RoadProfile.State.GROUND) best = Math.max(best, road.profile.height(s));
				}
			}
		}
		if (best == Double.NEGATIVE_INFINITY || best - fallback > 8) return fallback;
		return (int) Math.ceil(best);
	}

	Lot maskLot(Mask m, Rng rng) {
		Obb box = m.box();
		long lotSeed = rng.nextLong();
		double dt = fields.downtown(box.cx, box.cz);
		double cond = fields.condition(box.cx, box.cz);
		int baseY = (int) Math.round(grade.grade(box.cx, box.cz));
		if (terrain.sample((int) Math.round(box.cx / 4) * 4, (int) Math.round(box.cz / 4) * 4).water()) {
			baseY = Math.max(baseY, terrain.seaLevel() + 2);
		}
		Rng lr = new Rng(lotSeed);
		int minDim = Math.max(3, Math.min(m.width, m.depth));
		BuildingSpec.Purpose purpose = BuildingDesigner.purpose(lr, dt, m.width, m.depth);
		int floors = BuildingDesigner.floors(lr, purpose, dt, minDim);
		BuildingSpec draft = new BuildingSpec(purpose, BuildingSpec.Shape.RECT, BuildingSpec.Massing.STRAIGHT,
				BuildingSpec.Roof.FLAT, BuildingSpec.WindowStyle.BANDS, BuildingSpec.PaletteFamily.WHITE_MODERN, 0, lotSeed, "plains", false);
		return new Lot(box, box, Lot.Use.BUILDING, baseY, floors, dt, cond, lotSeed, draft, m);
	}
}
