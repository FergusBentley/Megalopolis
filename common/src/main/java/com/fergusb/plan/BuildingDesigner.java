package com.fergusb.plan;

import com.fergusb.plan.BuildingSpec.*;

import java.util.ArrayList;
import java.util.List;

/** Decides what each building is and how it looks */
final class BuildingDesigner {
	private static final PaletteFamily[][] THEMES = {
			{PaletteFamily.WHITE_MODERN, PaletteFamily.QUARTZ_GLASS, PaletteFamily.BLUE_ACCENT},
			{PaletteFamily.DARK_GLASS, PaletteFamily.SLATE, PaletteFamily.BLACKSTONE},
			{PaletteFamily.WARM_TERRACOTTA, PaletteFamily.SANDSTONE, PaletteFamily.RED_ACCENT},
			{PaletteFamily.COPPER, PaletteFamily.TEAL_TECH, PaletteFamily.PRISMARINE},
			{PaletteFamily.PASTEL, PaletteFamily.PURPUR, PaletteFamily.TUFF}};

	private BuildingDesigner() {}

	// purpose and height

	/** Purpose depends on how downtown the lot is and how big it is */
	static Purpose purpose(Rng rng, double dt, int w, int d) {
		int minDim = Math.min(w, d), area = w * d;
		double office = 0.05 + 0.9 * Math.pow(dt, 1.5);
		double apartment = 0.25 + 0.35 * (1 - Math.abs(dt - 0.5) * 2);
		double mixed = 0.1 + 0.35 * dt;
		double store = 0.15;
		double townhouses = minDim >= 8 && Math.max(w, d) >= 15 ? 0.45 * (1 - dt) : 0;
		double house = area <= 400 ? 0.7 * Math.pow(1 - dt, 2) : 0;
		double[] weights = {office, apartment, mixed, store, townhouses, house};
		double total = 0;
		for (double wgt : weights) total += wgt;
		double r = rng.nextDouble() * total;
		for (int i = 0; i < weights.length; i++) {
			r -= weights[i];
			if (r < 0) return Purpose.values()[i];
		}
		return Purpose.APARTMENT;
	}

	static int floors(Rng rng, Purpose p, double dt, int minDim) {
		int floors = switch (p) {
			case HOUSE -> rng.nextInt(1, 2);
			case TOWNHOUSES -> rng.nextInt(2, 3);
			case STORE -> rng.nextInt(1, 2);
			case MIXED_USE -> (int) Math.round(ValueNoise.lerp(dt, 2, 7) * rng.range(0.7, 1.3));
			case APARTMENT -> (int) Math.round(ValueNoise.lerp(Math.pow(dt, 1.4), 2, 10) * rng.range(0.7, 1.3));
			case OFFICE -> {
				double base = ValueNoise.lerp(Math.pow(dt, 1.8), 2, 12) * rng.range(0.7, 1.3);
				if (dt > 0.65 && rng.chance(0.05)) base *= 2.5;
				yield (int) Math.round(base);
			}
		};
		if (minDim < 9) floors = Math.min(floors, 6);
		return Math.max(1, Math.min(floors, CityPlanner.MAX_FLOORS));
	}

	static int setback(Purpose p, double dt) {
		if (p == Purpose.HOUSE) return dt > 0.3 ? 1 : 2;
		if (p == Purpose.TOWNHOUSES) return 0;
		return dt > 0.45 ? 0 : 1;
	}

	// appearance

	static List<Lot> design(List<Lot> lots, DistrictFields fields, TerrainSampler terrain) {
		List<Lot> out = new ArrayList<>(lots.size());
		for (Lot lot : lots) {
			if (lot.use() != Lot.Use.BUILDING) {
				out.add(lot);
				continue;
			}
			List<BuildingSpec> neighbors = new ArrayList<>();
			for (Lot o : out) {
				if (o.spec() != null && near(o.bounds(), lot.bounds())) neighbors.add(o.spec());
			}
			Rng rng = new Rng(Hashing.hash(lot.seed(), 77));
			String biome = terrain.biome((int) Math.round(lot.footprint().cx), (int) Math.round(lot.footprint().cz));
			BuildingSpec best = null;
			int bestClashes = Integer.MAX_VALUE;
			for (int attempt = 0; attempt < 6 && bestClashes > 0; attempt++) {
				BuildingSpec s = appearance(lot, rng, fields, biome);
				int clashes = 0;
				for (BuildingSpec n : neighbors) {
					if (n.palette() == s.palette()) clashes += 2;
					if (n.windows() == s.windows()) clashes++;
					if (n.shape() == s.shape() && s.shape() != Shape.RECT) clashes++;
				}
				if (clashes < bestClashes) {
					best = s;
					bestClashes = clashes;
				}
			}
			out.add(new Lot(lot.bounds(), lot.footprint(), lot.use(), lot.baseY(), lot.floors(),
					lot.downtown(), lot.condition(), lot.seed(), best));
		}
		return out;
	}

	private static boolean near(Obb a, Obb b) {
		return a.minX <= b.maxX + 3 && a.maxX >= b.minX - 3 && a.minZ <= b.maxZ + 3 && a.maxZ >= b.minZ - 3;
	}

	private static BuildingSpec appearance(Lot lot, Rng rng, DistrictFields fields, String biome) {
		Purpose p = lot.spec().purpose();
		Obb fp = lot.footprint();
		int w = fp.widthU(), d = fp.depthV(), minDim = Math.min(w, d);
		int floors = lot.floors();
		double dt = lot.downtown();

		Shape shape = Shape.RECT;
		Massing massing = Massing.STRAIGHT;
		Roof roof;
		WindowStyle windows;
		int unitWidth = 0;

		switch (p) {
			case HOUSE -> {
				shape = minDim >= 10 && rng.chance(0.35) ? Shape.L : Shape.RECT;
				roof = shape == Shape.RECT && rng.chance(0.7) ? Roof.GABLE : pick(rng, Roof.GARDEN, Roof.PARAPET);
				windows = rng.chance(0.7) ? WindowStyle.SINGLES : WindowStyle.PUNCHED;
			}
			case TOWNHOUSES -> {
				unitWidth = rng.nextInt(5, 7);
				roof = rng.chance(0.5) ? Roof.GABLE : Roof.PARAPET;
				windows = rng.chance(0.6) ? WindowStyle.SINGLES : WindowStyle.PUNCHED;
			}
			case STORE -> {
				shape = minDim >= 12 && rng.chance(0.2) ? Shape.CHAMFERED : Shape.RECT;
				roof = pick(rng, Roof.PARAPET, Roof.FLAT, Roof.EQUIPMENT, Roof.GARDEN);
				windows = pick(rng, WindowStyle.RIBBONS, WindowStyle.BANDS, WindowStyle.GRID);
			}
			case APARTMENT, MIXED_USE -> {
				shape = shapeFor(rng, minDim, floors);
				massing = floors >= 8 ? pick(rng, Massing.STRAIGHT, Massing.STRAIGHT, Massing.SETBACKS, Massing.PODIUM_TOWER) : Massing.STRAIGHT;
				roof = pick(rng, Roof.PARAPET, Roof.GARDEN, Roof.EQUIPMENT, Roof.FLAT);
				windows = pick(rng, WindowStyle.PUNCHED, WindowStyle.PUNCHED, WindowStyle.GRID, WindowStyle.RIBBONS, WindowStyle.STRIPS);
			}
			default -> { // OFFICE
				shape = shapeFor(rng, minDim, floors);
				massing = floors >= 10
						? pick(rng, Massing.STRAIGHT, Massing.SETBACKS, Massing.PODIUM_TOWER, Massing.TAPERED)
						: Massing.STRAIGHT;
				roof = floors >= 20 && rng.chance(0.4) ? Roof.SPIRE : pick(rng, Roof.PARAPET, Roof.EQUIPMENT, Roof.FLAT, Roof.GARDEN);
				double condition = lot.condition();
				windows = condition > 0.5 && dt > 0.5
						? pick(rng, WindowStyle.CURTAIN, WindowStyle.BANDS, WindowStyle.STRIPS, WindowStyle.GRID)
						: pick(rng, WindowStyle.BANDS, WindowStyle.RIBBONS, WindowStyle.STRIPS, WindowStyle.GRID);
			}
		}

		PaletteFamily palette = palette(rng, fields, fp, biome, lot.condition());
		boolean woodAccent = palette == PaletteFamily.TIMBER
				|| (p != Purpose.OFFICE && rng.chance(BiomePalettes.woody(biome) ? 0.45 : 0.25));
		return new BuildingSpec(p, shape, massing, roof, windows, palette, unitWidth, rng.nextLong(), biome, woodAccent);
	}

	private static PaletteFamily palette(Rng rng, DistrictFields fields, Obb fp, String biome, double condition) {
		if (condition < 0.3 && rng.chance(0.85)) {
			return rng.chance(0.5) ? PaletteFamily.DINGY_GRAY : PaletteFamily.DINGY_BROWN;
		}
		double r = rng.nextDouble();
		PaletteFamily[] options;
		if (r < 0.7) options = BiomePalettes.families(biome);
		else if (r < 0.9) options = THEMES[fields.theme(fp.cx, fp.cz)];
		else options = BiomePalettes.ANY;
		return options[rng.nextInt(0, options.length - 1)];
	}

	private static Shape shapeFor(Rng rng, int minDim, int floors) {
		if (minDim >= 16 && rng.chance(0.15)) return Shape.COURTYARD;
		if (minDim >= 12 && rng.chance(0.25)) return pick(rng, Shape.L, Shape.U);
		if (minDim >= 9 && floors >= 6 && rng.chance(0.15)) return Shape.ROUND;
		if (minDim >= 10 && rng.chance(0.15)) return Shape.CHAMFERED;
		return Shape.RECT;
	}

	@SafeVarargs
	private static <T> T pick(Rng rng, T... options) {
		return options[rng.nextInt(0, options.length - 1)];
	}
}
