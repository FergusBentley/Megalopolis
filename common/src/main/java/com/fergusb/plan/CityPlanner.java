package com.fergusb.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CityPlanner {
	public static final int CELL = 512;
	public static final int ARTERIAL_HALF_WIDTH = 5; // 11 blocks wide
	public static final int STREET_WIDTH = 5;
	public static final int FLOOR_HEIGHT = 4;
	public static final int MAX_FLOORS = 40;

	private final long seed;
	private final TerrainSampler terrain;
	private final DistrictFields fields;
	private final GradeField grade;
	private final Map<Long, CompletableFuture<Superblock>> cache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<Superblock>> e) {
			return size() > 256;
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

	// queries

	public enum Kind { ARTERIAL, STREET, LOT, GAP }

	public record Cell(Kind kind, Road road, Lot lot, int arterialDx, int arterialDz) {
		public boolean isRoad() {
			return kind == Kind.ARTERIAL || kind == Kind.STREET;
		}
	}

	public Cell classify(int x, int z) {
		int dx = arterialOffsetX(x), dz = arterialOffsetZ(z);
		if (dx != Integer.MAX_VALUE || dz != Integer.MAX_VALUE) {
			return new Cell(Kind.ARTERIAL, null, null, dx, dz);
		}
		Superblock sb = superblock(superColumn(x), superRow(z));
		Road road = sb.streetAt(x, z);
		if (road != null) return new Cell(Kind.STREET, road, null, 0, 0);
		Lot lot = sb.lotAt(x, z);
		if (lot != null) return new Cell(Kind.LOT, null, lot, 0, 0);
		return new Cell(Kind.GAP, null, null, 0, 0);
	}

	public Superblock superblockAt(int x, int z) {
		return superblock(superColumn(x), superRow(z));
	}

	public Superblock superblock(int i, int j) {
		long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
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
		if (mine) {
			try {
				f.complete(plan(i, j));
			} catch (Throwable t) {
				f.completeExceptionally(t);
				synchronized (cache) {
					cache.remove(key);
				}
			}
		}
		return f.join();
	}

	// planning

	private Superblock plan(int i, int j) {
		Rect bounds = new Rect(
				arterialX(i) + ARTERIAL_HALF_WIDTH + 1, arterialZ(j) + ARTERIAL_HALF_WIDTH + 1,
				arterialX(i + 1) - ARTERIAL_HALF_WIDTH - 1, arterialZ(j + 1) - ARTERIAL_HALF_WIDTH - 1);
		Rng rng = new Rng(Hashing.hash(seed, 3, i, j));
		List<Road> streets = new ArrayList<>();
		List<Lot> lots = new ArrayList<>();
		boolean organic = isOrganic(bounds);
		if (organic) {
			new OrganicLayout(this, bounds, rng).plan(streets, lots);
		} else {
			new GridLayout(this, bounds, rng).plan(streets, lots);
		}
		return new Superblock(i, j, bounds, organic, streets, BuildingDesigner.design(lots, fields, terrain));
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
		double reliefBoost = DistrictFields.clamp01((max - min - 16) / 60.0) * 0.4;
		return fields.organic(b.centerX(), b.centerZ()) + reliefBoost > 0.5;
	}

	Lot finishLot(Obb r, Rng rng, boolean park) {
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
			TerrainSampler.Sample s = terrain.sample((int) Math.round(p[0]), (int) Math.round(p[1]));
			if (s.water()) waterSamples++;
			maxDeviation = Math.max(maxDeviation, Math.abs(s.groundY() - baseY));
		}
		if (waterSamples >= 3) {
			return new Lot(r, null, Lot.Use.UNBUILDABLE, baseY, 0, dt, cond, lotSeed, null);
		}
		if (park || waterSamples > 0 || maxDeviation > 12) {
			// Too steep or water
			return new Lot(r, null, Lot.Use.PARK, baseY, 0, dt, cond, lotSeed, null);
		}

		Rng lr = new Rng(lotSeed);
		BuildingSpec.Purpose purpose = BuildingDesigner.purpose(lr, dt, r.widthU(), r.depthV());
		Obb fp = r.shrink(BuildingDesigner.setback(purpose, dt));
		if (fp == null || fp.widthU() < 5 || fp.depthV() < 5) {
			return new Lot(r, null, Lot.Use.PLAZA, baseY, 0, dt, cond, lotSeed, null);
		}

		int floors = BuildingDesigner.floors(lr, purpose, dt, Math.min(fp.widthU(), fp.depthV()));
		BuildingSpec draft = new BuildingSpec(purpose, BuildingSpec.Shape.RECT, BuildingSpec.Massing.STRAIGHT,
				BuildingSpec.Roof.FLAT, BuildingSpec.WindowStyle.BANDS, BuildingSpec.PaletteFamily.WHITE_MODERN, 0, lotSeed, "plains", false);
		return new Lot(r, fp, Lot.Use.BUILDING, baseY, floors, dt, cond, lotSeed, draft);
	}
}
