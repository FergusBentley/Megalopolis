package com.fergusb.world;

import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.GradeField;
import com.fergusb.plan.Hashing;
import com.fergusb.plan.Lot;
import com.fergusb.plan.Obb;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Writes one chunk's slice of the plan */
public final class CityPlacer {
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	private static final int MAX_FILL = 40;
	private static final int MAX_CLEAR = 64;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();
	private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState SIDEWALK = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState ASPHALT = Blocks.CONCRETE.gray().defaultBlockState();
	private static final BlockState ARTERIAL_ASPHALT = Blocks.CONCRETE.black().defaultBlockState();
	private static final BlockState LANE_LINE = Blocks.CONCRETE.yellow().defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState CURB = Blocks.CONCRETE.lightGray().defaultBlockState();
	private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();

	private static final BlockState[] WALLS_GOOD = {
			Blocks.CONCRETE.white().defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState(),
			Blocks.CONCRETE.lightGray().defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState()};
	private static final BlockState[] WALLS_MID = {
			Blocks.CONCRETE.lightGray().defaultBlockState(), Blocks.DYED_TERRACOTTA.cyan().defaultBlockState(),
			Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.DYED_TERRACOTTA.lightGray().defaultBlockState()};
	private static final BlockState[] WALLS_BAD = {
			Blocks.CONCRETE.gray().defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
			Blocks.STONE_BRICKS.defaultBlockState(), Blocks.DYED_TERRACOTTA.gray().defaultBlockState()};
	private static final BlockState[] GLASS_GOOD = {
			Blocks.STAINED_GLASS.lightBlue().defaultBlockState(), Blocks.GLASS.defaultBlockState(),
			Blocks.STAINED_GLASS.cyan().defaultBlockState()};
	private static final BlockState[] GLASS_BAD = {
			Blocks.GLASS.defaultBlockState(), Blocks.STAINED_GLASS.gray().defaultBlockState(),
			Blocks.STAINED_GLASS.brown().defaultBlockState()};

	private CityPlacer() {}

	public static void placeChunk(WorldGenLevel level, CityPlanner planner, int chunkX, int chunkZ) {
		int x0 = chunkX << 4, z0 = chunkZ << 4;
		GradeField grade = planner.grade();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean[][] water = waterMask(level, pos, x0, z0);
		CityPlanner.Cell[][] cells = new CityPlanner.Cell[18][18];
		for (int dx = -1; dx <= 16; dx++) {
			for (int dz = -1; dz <= 16; dz++) {
				cells[dx + 1][dz + 1] = planner.classify(x0 + dx, z0 + dz);
			}
		}
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = x0 + dx, z = z0 + dz;
				CityPlanner.Cell cell = cells[dx + 1][dz + 1];
				switch (cell.kind()) {
					case ARTERIAL, STREET -> {
						if (water[dx + 1][dz + 1]) continue; // TODO: bridges
						int y = grade.gradeY(x, z) - 1;
						boolean bordersLand = !cells[dx][dz + 1].isRoad() || !cells[dx + 2][dz + 1].isRoad()
								|| !cells[dx + 1][dz].isRoad() || !cells[dx + 1][dz + 2].isRoad();
						placeRoadColumn(level, pos, x, y, z, bordersLand ? SIDEWALK : roadSurface(cell, x, z));
						boolean shore = water[dx][dz + 1] || water[dx + 2][dz + 1] || water[dx + 1][dz] || water[dx + 1][dz + 2];
						if (shore) set(level, pos.set(x, y + 1, z), CURB); // seawall where road meets water
					}
					case LOT -> placeLotColumn(level, pos, grade, cell.lot(), x, z);
					case GAP -> {
						if (!water[dx + 1][dz + 1]) placeGreen(level, pos, grade.gradeY(x, z), x, z);
					}
				}
			}
		}
	}

	private static boolean[][] waterMask(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x0, int z0) {
		int y = level.getSeaLevel() - 1;
		boolean[][] mask = new boolean[18][18];
		for (int dx = -1; dx <= 16; dx++) {
			for (int dz = -1; dz <= 16; dz++) {
				pos.set(x0 + dx, y, z0 + dz);
				mask[dx + 1][dz + 1] = !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).is(BlockTags.ICE);
			}
		}
		return mask;
	}

	// roads

	private static BlockState roadSurface(CityPlanner.Cell cell, int x, int z) {
		if (cell.kind() == CityPlanner.Kind.STREET) return ASPHALT;
		boolean onX = cell.arterialDx() != Integer.MAX_VALUE;
		boolean onZ = cell.arterialDz() != Integer.MAX_VALUE;
		if (onX && onZ) return ARTERIAL_ASPHALT; // intersection
		int off = onX ? cell.arterialDx() : cell.arterialDz();
		int along = onX ? z : x;
		if (off == 0 && Math.floorMod(along, 8) < 4) return LANE_LINE;
		return ARTERIAL_ASPHALT;
	}

	private static void placeRoadColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState top) {
		set(level, pos.set(x, y, z), top);
		fillDown(level, pos, x, y - 1, z, FOUNDATION);
		clearUp(level, pos, x, y + 1, z);
	}

	// lots

	private static void placeLotColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, GradeField grade, Lot lot, int x, int z) {
		int base = lot.baseY();
		switch (lot.use()) {
			case UNBUILDABLE -> { }
			case PARK -> placeGreen(level, pos, grade.gradeY(x, z), x, z);
			case PLAZA -> {
				set(level, pos.set(x, base - 1, z), SIDEWALK);
				fillDown(level, pos, x, base - 2, z, FOUNDATION);
				clearUp(level, pos, x, base, z);
			}
			case BUILDING -> {
				if (lot.footprint().contains(x, z)) {
					placeBuildingColumn(level, pos, lot, x, z);
				} else {
					set(level, pos.set(x, base - 1, z), lot.downtown() > 0.35 ? SIDEWALK : GRASS);
					fillDown(level, pos, x, base - 2, z, FOUNDATION);
					clearUp(level, pos, x, base, z);
				}
			}
		}
	}

	private static void placeGreen(WorldGenLevel level, BlockPos.MutableBlockPos pos, int surfaceY, int x, int z) {
		set(level, pos.set(x, surfaceY - 1, z), GRASS);
		fillDown(level, pos, x, surfaceY - 2, z, DIRT);
		clearUp(level, pos, x, surfaceY, z);
	}

	private static void placeBuildingColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, Lot lot, int x, int z) {
		Obb fp = lot.footprint();
		int base = lot.baseY();
		int top = lot.topY();
		double cond = lot.condition();
		BlockState wall = pick(cond > 0.66 ? WALLS_GOOD : cond > 0.33 ? WALLS_MID : WALLS_BAD, lot.seed(), 1);
		BlockState glass = pick(cond > 0.5 ? GLASS_GOOD : GLASS_BAD, lot.seed(), 2);
		boolean edge = fp.onEdge(x, z);
		boolean corner = fp.onCorner(x, z);
		int lu = (int) Math.round(fp.u(x, z) + fp.halfU), lv = (int) Math.round(fp.v(x, z) + fp.halfV);
		boolean lightColumn = Math.floorMod(lu, 6) == 3 && Math.floorMod(lv, 6) == 3;
		double decay = Math.max(0, 0.4 - cond) / 0.4; // 0 above condition 0.4, up to 1 at 0

		set(level, pos.set(x, base - 1, z), FLOOR);
		fillDown(level, pos, x, base - 2, z, FOUNDATION);

		for (int y = base; y <= top; y++) {
			int local = y - base;
			boolean floorLayer = local % CityPlanner.FLOOR_HEIGHT == 0;
			long h = Hashing.hash(lot.seed(), x, y, z);
			BlockState state;
			if (y == top) {
				state = wall; // roof
			} else if (edge) {
				if (corner || floorLayer) {
					state = wall;
				} else {
					state = Hashing.unit(h) < 0.35 * decay ? AIR : glass;
				}
			} else if (floorLayer) {
				if (local > 0 && lightColumn && Hashing.unit(h) >= 0.7 * decay) state = LIGHT;
				else state = local == 0 ? FLOOR : wall;
			} else if (local % CityPlanner.FLOOR_HEIGHT == CityPlanner.FLOOR_HEIGHT - 1
					&& Hashing.unit(h) < 0.08 * decay) {
				state = COBWEB;
			} else {
				state = AIR;
			}
			if (!level.isOutsideBuildHeight(y)) set(level, pos.set(x, y, z), state);
		}
		clearUp(level, pos, x, top + 1, z);
	}

	private static BlockState pick(BlockState[] options, long seed, int salt) {
		return options[(int) (Hashing.unit(Hashing.hash(seed, salt)) * options.length)];
	}

	// helpers

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.isOutsideBuildHeight(pos.getY())) return;
		level.setBlock(pos, state, FLAGS);
	}

	private static void fillDown(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z, BlockState state) {
		for (int y = fromY, n = 0; n < MAX_FILL; y--, n++) {
			pos.set(x, y, z);
			if (level.isOutsideBuildHeight(y)) return;
			BlockState existing = level.getBlockState(pos);
			if (!existing.isAir() && existing.getFluidState().isEmpty() && !existing.canBeReplaced()) return;
			level.setBlock(pos, state, FLAGS);
		}
	}

	private static void clearUp(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z) {
		int airRun = 0;
		for (int y = fromY, n = 0; n < MAX_CLEAR && airRun < 3; y++, n++) {
			pos.set(x, y, z);
			if (level.isOutsideBuildHeight(y)) return;
			if (level.getBlockState(pos).isAir()) {
				airRun++;
			} else {
				airRun = 0;
				level.setBlock(pos, AIR, FLAGS);
			}
		}
	}
}
