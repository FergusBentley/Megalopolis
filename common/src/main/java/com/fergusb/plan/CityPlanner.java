package com.fergusb.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CityPlanner {
	public static final int CELL = 512;
	public static final int ARTERIAL_HALF_WIDTH = 5; // 11 blocks wide
	public static final int STREET_WIDTH = 5;
	public static final int FLOOR_HEIGHT = 4;
	public static final int MAX_FLOORS = 55;

	private final long seed;
	private final TerrainSampler terrain;
	private final DistrictFields fields;
	private final Map<Long, Superblock> cache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Superblock> e) {
			return size() > 512;
		}
	};

	public CityPlanner(long seed, TerrainSampler terrain) {
		this.seed = seed;
		this.terrain = terrain;
		this.fields = new DistrictFields(seed);
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

	public record Cell(Kind kind, Road road, Lot lot, int arterialDx, int arterialDz) {}

	public Cell classify(int x, int z) {
		int dx = arterialOffsetX(x), dz = arterialOffsetZ(z);
		if (dx != Integer.MAX_VALUE || dz != Integer.MAX_VALUE) {
			return new Cell(Kind.ARTERIAL, null, null, dx, dz);
		}
		Superblock sb = superblock(superColumn(x), superRow(z));
		for (Road r : sb.streets()) {
			if (r.rect().contains(x, z)) return new Cell(Kind.STREET, r, null, 0, 0);
		}
		for (Lot l : sb.lots()) {
			if (l.bounds().contains(x, z)) return new Cell(Kind.LOT, null, l, 0, 0);
		}
		return new Cell(Kind.GAP, null, null, 0, 0);
	}

	public Superblock superblock(int i, int j) {
		long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
		synchronized (cache) {
			Superblock sb = cache.get(key);
			if (sb != null) return sb;
		}
		Superblock sb = plan(i, j);
		synchronized (cache) {
			cache.put(key, sb);
		}
		return sb;
	}

	// planning

	private Superblock plan(int i, int j) {
		Rect bounds = new Rect(
				arterialX(i) + ARTERIAL_HALF_WIDTH + 1, arterialZ(j) + ARTERIAL_HALF_WIDTH + 1,
				arterialX(i + 1) - ARTERIAL_HALF_WIDTH - 1, arterialZ(j + 1) - ARTERIAL_HALF_WIDTH - 1);
		Rng rng = new Rng(Hashing.hash(seed, 3, i, j));
		List<Road> streets = new ArrayList<>();
		List<Rect> blocks = new ArrayList<>();
		split(bounds, rng, streets, blocks);
		List<Lot> lots = new ArrayList<>();
		for (Rect block : blocks) {
			makeLots(block, rng, lots);
		}
		return new Superblock(i, j, bounds, List.copyOf(streets), List.copyOf(lots));
	}

	private void split(Rect r, Rng rng, List<Road> streets, List<Rect> blocks) {
		double dt = fields.downtown(r.centerX(), r.centerZ());
		int maxSize = (int) ValueNoise.lerp(dt, 52, 100);
		if (r.width() <= maxSize && r.depth() <= maxSize) {
			blocks.add(r);
			return;
		}
		boolean cutAcrossX = r.width() >= r.depth();
		int len = cutAcrossX ? r.width() : r.depth();
		int offset = (int) (len * rng.range(0.38, 0.62));
		int before = offset;
		int after = len - offset - STREET_WIDTH;
		if (before < 20 || after < 20) {
			blocks.add(r);
			return;
		}
		Rect a, b, street;
		if (cutAcrossX) {
			int s = r.minX() + offset;
			street = new Rect(s, r.minZ(), s + STREET_WIDTH - 1, r.maxZ());
			a = new Rect(r.minX(), r.minZ(), s - 1, r.maxZ());
			b = new Rect(s + STREET_WIDTH, r.minZ(), r.maxX(), r.maxZ());
		} else {
			int s = r.minZ() + offset;
			street = new Rect(r.minX(), s, r.maxX(), s + STREET_WIDTH - 1);
			a = new Rect(r.minX(), r.minZ(), r.maxX(), s - 1);
			b = new Rect(r.minX(), s + STREET_WIDTH, r.maxX(), r.maxZ());
		}
		streets.add(new Road(street, Road.Kind.STREET));
		split(a, rng, streets, blocks);
		split(b, rng, streets, blocks);
	}

	private void makeLots(Rect block, Rng rng, List<Lot> out) {
		double dt = fields.downtown(block.centerX(), block.centerZ());
		double parkChance = 0.03 + 0.10 * (1 - dt);
		if (rng.chance(parkChance)) {
			out.add(finishLot(block, rng, true));
			return;
		}
		boolean alongX = block.width() >= block.depth();
		int shortLen = alongX ? block.depth() : block.width();
		List<int[]> rows = new ArrayList<>();
		int shortMin = alongX ? block.minZ() : block.minX();
		int shortMax = alongX ? block.maxZ() : block.maxX();
		if (shortLen >= 20) {
			int mid = shortMin + shortLen / 2 + rng.nextInt(-2, 2);
			rows.add(new int[] {shortMin, mid - 1});
			rows.add(new int[] {mid, shortMax});
		} else {
			rows.add(new int[] {shortMin, shortMax});
		}
		int longMin = alongX ? block.minX() : block.minZ();
		int longMax = alongX ? block.maxX() : block.maxZ();
		int minW = (int) ValueNoise.lerp(dt, 8, 14);
		int maxW = (int) ValueNoise.lerp(dt, 18, 40);
		for (int[] row : rows) {
			int cursor = longMin;
			while (cursor <= longMax) {
				int remaining = longMax - cursor + 1;
				int w = rng.nextInt(minW, maxW);
				if (remaining - w < minW) w = remaining;
				Rect lot = alongX
						? new Rect(cursor, row[0], cursor + w - 1, row[1])
						: new Rect(row[0], cursor, row[1], cursor + w - 1);
				out.add(finishLot(lot, rng, false));
				cursor += w;
			}
		}
	}

	private Lot finishLot(Rect r, Rng rng, boolean park) {
		long lotSeed = rng.nextLong();
		double dt = fields.downtown(r.centerX(), r.centerZ());
		double cond = fields.condition(r.centerX(), r.centerZ());

		int[][] pts = {{r.minX(), r.minZ()}, {r.maxX(), r.minZ()}, {r.minX(), r.maxZ()}, {r.maxX(), r.maxZ()},
				{r.centerX(), r.centerZ()}};
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0;
		boolean water = false;
		for (int[] p : pts) {
			TerrainSampler.Sample s = terrain.sample(p[0], p[1]);
			water |= s.water();
			min = Math.min(min, s.groundY());
			max = Math.max(max, s.groundY());
			sum += s.groundY();
		}
		int baseY = Math.round(sum / (float) pts.length);
		if (water || max - min > 14) {
			return new Lot(r, null, Lot.Use.UNBUILDABLE, baseY, 0, dt, cond, lotSeed);
		}
		if (park) {
			return new Lot(r, null, Lot.Use.PARK, baseY, 0, dt, cond, lotSeed);
		}

		int setback = dt > 0.65 ? 0 : (dt > 0.35 ? 1 : 2);
		Rect fp = r.shrink(setback);
		if (fp == null || fp.width() < 5 || fp.depth() < 5) {
			return new Lot(r, null, Lot.Use.PLAZA, baseY, 0, dt, cond, lotSeed);
		}

		Rng lr = new Rng(lotSeed);
		double base = ValueNoise.lerp(Math.pow(dt, 1.6), 2, 22);
		double mult = lr.range(0.6, 1.4);
		if (dt > 0.7 && lr.chance(0.06)) mult *= 2.2; // occasional tower
		int floors = (int) Math.max(1, Math.round(base * mult));
		if (Math.min(fp.width(), fp.depth()) < 9) floors = Math.min(floors, 8);
		floors = Math.min(floors, MAX_FLOORS);
		return new Lot(r, fp, Lot.Use.BUILDING, baseY, floors, dt, cond, lotSeed);
	}
}
