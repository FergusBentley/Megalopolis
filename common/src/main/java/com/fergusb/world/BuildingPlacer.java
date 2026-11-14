package com.fergusb.world;

import com.fergusb.plan.*;
import com.fergusb.plan.BuildingSpec.Purpose;
import com.fergusb.plan.BuildingSpec.Roof;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

final class BuildingPlacer {
	private static final int FH = CityPlanner.FLOOR_HEIGHT;
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();
	private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();
	private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
	private static final BlockState HEDGE = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState VENT = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState VENT_TOP = Blocks.IRON_BLOCK.defaultBlockState();
	private static final BlockState ANTENNA = Blocks.END_ROD.defaultBlockState();
	private static final BlockState[] HOUSE_ROOFS = {
			Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CONCRETE.gray().defaultBlockState(),
			Blocks.DYED_TERRACOTTA.brown().defaultBlockState()};
	private static final BuildingSpec.PaletteFamily[] FAMILIES = BuildingSpec.PaletteFamily.values();

	private BuildingPlacer() {}

	/** Places the column at (x, z) */
	static void place(WorldGenLevel level, BlockPos.MutableBlockPos pos, CityPlanner planner, Lot lot, int x, int z) {
		Obb fp = lot.footprint();
		BuildingSpec s = lot.spec();
		int[] cell = BuildingShape.local(fp, x, z);
		int lu = cell[0], lv = cell[1];
		int w = fp.widthU(), d = fp.depthV();
		int base = lot.baseY();
		boolean townhouses = s.purpose() == Purpose.TOWNHOUSES;
		boolean homey = townhouses || s.purpose() == Purpose.HOUSE;

		// Townhouses get their own palette
		int unit = townhouses ? BuildingShape.unit(s, lu, w) : 0;
		int floors = lot.floors() + (townhouses ? extraFloors(s, unit) : 0);
		boolean partition = townhouses && lu > 0 && BuildingShape.unit(s, lu - 1, w) != unit;
		if (partition) floors = Math.max(floors, lot.floors() + extraFloors(s, unit - 1));
		BuildingSpec.PaletteFamily family = townhouses
				? FAMILIES[(int) (Hashing.unit(Hashing.hash(s.seed(), 31, unit)) * FAMILIES.length)]
				: s.palette();
		Palettes.Palette pal = Palettes.of(family, s.biome());
		BlockState wood = Palettes.wood(s.biome());
		if (s.woodAccent()) pal = new Palettes.Palette(pal.wall(), wood, wood, pal.glass(), pal.roof(), pal.weatheredWall(), pal.weatheredGlass());
		BlockState trim = homey || s.woodAccent() ? wood : pal.trim();
		BlockState roofBlock = homey ? HOUSE_ROOFS[(int) (Hashing.unit(Hashing.hash(s.seed(), 32, unit)) * HOUSE_ROOFS.length)] : pal.roof();
		double cond = lot.condition();
		double decay = Math.max(0, 0.4 - cond) / 0.4;
		double weathering = Math.max(0, 0.3 - cond) / 0.3;

		Outside out = lookOutside(planner, lot, x, z);

		set(level, pos.set(x, base - 1, z), FLOOR);
		fillDown(level, pos, x, base - 2, z);

		int lightU = Math.floorMod(lu, 6), lightV = Math.floorMod(lv, 6);
		int top = base + floors * FH;
		for (int y = base; y < top; y++) {
			int local = y - base, f = local / FH, ly = local % FH;
			long h = Hashing.hash(s.seed(), x, y, z);
			double r = Hashing.unit(h);
			BlockState state;
			if (!BuildingShape.inside(lot, lu, lv, f)) {
				state = (ly == 0 && f > 0 && BuildingShape.inside(lot, lu, lv, f - 1)) ? roofBlock : AIR;
			} else {
				Face face = face(lot, fp, x, z, lu, lv, f);
				if (face != null) {
					state = wallBlock(s, face, f, ly, out, y, pal, trim, r, decay, weathering, lu, unit, w);
				} else if (partition) {
					state = weathered(pal.wall(), pal.weatheredWall(), r, weathering);
				} else if (ly == 0) {
					boolean light = f > 0 && lightU == 3 && lightV == 3 && r >= 0.7 * decay;
					state = light ? LIGHT : FLOOR;
				} else if (ly == FH - 1 && r < 0.08 * decay) {
					state = COBWEB;
				} else {
					state = AIR;
				}
			}
			set(level, pos.set(x, y, z), state);
		}
		int roofTop = placeRoof(level, pos, lot, s, x, z, lu, lv, w, d, floors, top, unit, pal, trim, roofBlock);
		clearUp(level, pos, x, roofTop + 1, z);
	}

	private static int extraFloors(BuildingSpec s, int unit) {
		return Hashing.unit(Hashing.hash(s.seed(), 30, unit)) < 0.4 ? 1 : 0;
	}

	// facades

	private record Face(boolean corner, int index, int length) {}

	/** Non-null if (x, z) is on the outer wall of floor f */
	private static Face face(Lot lot, Obb fp, int x, int z, int lu, int lv, int f) {
		boolean outX = false, outZ = false;
		int[] first = null;
		for (int[] dir : DIRS) {
			if (!BuildingShape.insideAt(lot, x + dir[0], z + dir[1], f)) {
				if (dir[0] != 0) outX = true;
				else outZ = true;
				if (first == null) first = dir;
			}
		}
		if (first == null) {
			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dz = -1; dz <= 1; dz += 2) {
					if (!BuildingShape.insideAt(lot, x + dx, z + dz, f)) return new Face(true, 0, 1);
				}
			}
			return null;
		}
		// Which building axis the face is perpendicular to decides how windows are counted along it
		double du = first[0] * fp.ux + first[1] * fp.uz;
		double dv = -first[0] * fp.uz + first[1] * fp.ux;
		boolean faceAlongV = Math.abs(du) > Math.abs(dv);
		return new Face(outX && outZ, faceAlongV ? lv : lu, faceAlongV ? fp.depthV() : fp.widthU());
	}

	private static BlockState wallBlock(BuildingSpec s, Face face, int f, int ly, Outside out, int y,
			Palettes.Palette pal, BlockState trim, double r, double decay, double weathering, int lu, int unit, int w) {
		BlockState wall = weathered(pal.wall(), pal.weatheredWall(), r, weathering);
		BlockState glass = r < 0.35 * decay ? AIR : weathered(pal.glass(), pal.weatheredGlass(), r, weathering);
		if (face.corner()) return trim;
		boolean groundWall = out.groundWall();
		// Walls that touch or nearly touch a neighbor stay solid up to the neighbor's height
		if (groundWall && y <= out.neighborTop()) return ly == 0 ? trim : wall;
		if (f == 0 && groundWall && out.street()) {
			if (isDoor(s, face, lu, unit, w) && (ly == 1 || ly == 2)) return AIR;
			if (s.purpose().hasStorefront()) return ly == 0 ? trim : weathered(pal.glass(), pal.weatheredGlass(), r, weathering);
		}
		int i = face.index();
		return switch (s.windows()) {
			case BANDS -> ly == 0 ? trim : glass;
			case RIBBONS -> ly == 0 ? trim : (ly == 1 || ly == 2) ? glass : wall;
			case PUNCHED -> ly == 0 ? trim : (i % 3 != 0 && ly <= 2) ? glass : wall;
			case SINGLES -> ly == 0 ? trim : (i % 2 == 1 && ly <= 2) ? glass : wall;
			case STRIPS -> i % 3 == 0 ? pal.accent() : glass;
			case GRID -> (ly == 0 || i % 2 == 0) ? pal.accent() : glass;
			case CURTAIN -> glass;
		};
	}

	private static boolean isDoor(BuildingSpec s, Face face, int lu, int unit, int w) {
		int mid = face.length() / 2;
		return switch (s.purpose()) {
			case HOUSE -> face.index() == mid;
			case TOWNHOUSES -> {
				// One door per unit
				int[] span = BuildingShape.unitSpan(s, unit, w);
				yield face.index() == lu && lu == (span[0] + span[1]) / 2;
			}
			default -> face.index() == mid || face.index() == mid - 1;
		};
	}

	private static BlockState weathered(BlockState normal, BlockState worn, double r, double weathering) {
		return r < weathering * 0.6 ? worn : normal;
	}

	// surroundings

	private record Outside(boolean groundWall, boolean street, int neighborTop) {}

	private static Outside lookOutside(CityPlanner planner, Lot lot, int x, int z) {
		boolean wall = false, street = false;
		int neighborTop = Integer.MIN_VALUE;
		int reach = lot.bounds().widthU() - lot.footprint().widthU() + 3; // setback plus a little
		for (int[] dir : DIRS) {
			if (BuildingShape.insideAt(lot, x + dir[0], z + dir[1], 0)) continue;
			wall = true;
			for (int k = 1; k <= Math.max(2, reach); k++) {
				CityPlanner.Cell c = planner.classify(x + dir[0] * k, z + dir[1] * k);
				if (c.isRoad()) {
					street = true;
					break;
				}
				if (k <= 2 && c.kind() == CityPlanner.Kind.LOT && c.lot() != lot && c.lot().use() == Lot.Use.BUILDING
						&& BuildingShape.insideAt(c.lot(), x + dir[0] * k, z + dir[1] * k, 0)) {
					neighborTop = Math.max(neighborTop, c.lot().topY());
				}
			}
		}
		return new Outside(wall, street, neighborTop);
	}

	// roofs

	/** Places the roof above the top floor and returns the highest Y used */
	private static int placeRoof(WorldGenLevel level, BlockPos.MutableBlockPos pos, Lot lot, BuildingSpec s,
			int x, int z, int lu, int lv, int w, int d, int floors, int top, int unit,
			Palettes.Palette pal, BlockState trim, BlockState roofBlock) {
		int f = floors - 1;
		if (!BuildingShape.inside(lot, lu, lv, f)) return top - 1;
		boolean edge = face(lot, lot.footprint(), x, z, lu, lv, f) != null;
		long h = Hashing.hash(s.seed(), x, top, z);
		double r = Hashing.unit(h);

		if (s.roof() == Roof.GABLE) return gable(level, pos, s, x, z, lu, lv, w, d, top, unit, pal, roofBlock);

		set(level, pos.set(x, top, z), s.roof() == Roof.GARDEN && !edge ? MOSS : roofBlock);
		if (s.roof() == Roof.FLAT) return top;
		if (edge) {
			set(level, pos.set(x, top + 1, z), trim); // parapet
			return top + 1;
		}
		switch (s.roof()) {
			case GARDEN -> {
				if (r < 0.12) {
					set(level, pos.set(x, top + 1, z), HEDGE);
					return top + 1;
				}
			}
			case EQUIPMENT -> {
				boolean box = Math.floorMod(lu, 5) >= 1 && Math.floorMod(lu, 5) <= 2 && Math.floorMod(lv, 5) >= 1
						&& Math.floorMod(lv, 5) <= 2 && Hashing.unit(Hashing.hash(s.seed(), lu / 5, lv / 5)) < 0.4;
				if (box) {
					set(level, pos.set(x, top + 1, z), VENT);
					set(level, pos.set(x, top + 2, z), VENT_TOP);
					return top + 2;
				}
			}
			case SPIRE -> {
				int cu = Math.abs(lu - w / 2), cv = Math.abs(lv - d / 2);
				if (cu == 0 && cv == 0) {
					int height = 8 + floors / 3;
					set(level, pos.set(x, top + 1, z), pal.accent());
					set(level, pos.set(x, top + 2, z), pal.accent());
					for (int k = 3; k <= height; k++) set(level, pos.set(x, top + k, z), ANTENNA);
					return top + height;
				}
				if (cu <= 1 && cv <= 1) {
					set(level, pos.set(x, top + 1, z), pal.accent());
					set(level, pos.set(x, top + 2, z), pal.accent());
					return top + 2;
				}
			}
			default -> { }
		}
		return top;
	}

	private static int gable(WorldGenLevel level, BlockPos.MutableBlockPos pos, BuildingSpec s, int x, int z,
			int lu, int lv, int w, int d, int top, int unit, Palettes.Palette pal, BlockState roofBlock) {
		int dist;
		boolean gableEnd;
		if (s.purpose() == Purpose.TOWNHOUSES) {
			int[] span = BuildingShape.unitSpan(s, unit, w);
			dist = Math.min(lu - span[0], span[1] - lu);
			gableEnd = lv == 0 || lv == d - 1;
		} else if (w >= d) {
			dist = Math.min(lv, d - 1 - lv);
			gableEnd = lu == 0 || lu == w - 1;
		} else {
			dist = Math.min(lu, w - 1 - lu);
			gableEnd = lv == 0 || lv == d - 1;
		}
		for (int k = 0; k <= dist; k++) {
			BlockState state;
			if (k == dist) state = roofBlock;
			else if (gableEnd) state = pal.wall();
			else state = k == 0 ? FLOOR : AIR;
			set(level, pos.set(x, top + k, z), state);
		}
		return top + dist;
	}

	// helpers

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.isOutsideBuildHeight(pos.getY())) return;
		level.setBlock(pos, state, FLAGS);
	}

	private static void fillDown(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z) {
		for (int y = fromY, n = 0; n < 40; y--, n++) {
			pos.set(x, y, z);
			if (level.isOutsideBuildHeight(y)) return;
			BlockState existing = level.getBlockState(pos);
			if (!existing.isAir() && existing.getFluidState().isEmpty() && !existing.canBeReplaced()) return;
			level.setBlock(pos, FOUNDATION, FLAGS);
		}
	}

	private static void clearUp(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z) {
		CityPlacer.clearUp(level, pos, x, fromY, z);
	}
}
