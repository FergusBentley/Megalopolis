package com.fergusb.world;

import com.fergusb.plan.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Writes one chunk's slice of the plan */
public final class CityPlacer {
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	private static final int MAX_FILL = 40;
	private static final int MAX_CLEAR = 64;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();
	private static final BlockState SIDEWALK = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState ASPHALT = Blocks.CONCRETE.gray().defaultBlockState();
	private static final BlockState ARTERIAL_ASPHALT = Blocks.CONCRETE.black().defaultBlockState();
	private static final BlockState CENTER_LINE = Blocks.POLISHED_ANDESITE.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState CURB = Blocks.CONCRETE.lightGray().defaultBlockState();

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
				boolean wet = water[dx + 1][dz + 1] || surfaceWater(level, pos, x, z);
				switch (cell.kind()) {
					case ARTERIAL, STREET -> {
						if (wet) continue; // TODO: bridges
						int y = grade.gradeY(x, z) - 1;
						boolean bordersLand = !cells[dx][dz + 1].isRoad() || !cells[dx + 2][dz + 1].isRoad()
								|| !cells[dx + 1][dz].isRoad() || !cells[dx + 1][dz + 2].isRoad();
						placeRoadColumn(level, pos, x, y, z, bordersLand ? SIDEWALK : roadSurface(cell, x, z));
						boolean shore = water[dx][dz + 1] || water[dx + 2][dz + 1] || water[dx + 1][dz] || water[dx + 1][dz + 2];
						if (shore) set(level, pos.set(x, y + 1, z), CURB); // seawall where road meets water
					}
					case LOT -> placeLotColumn(level, pos, planner, grade, cell.lot(), x, z, wet);
					case GAP -> {
						if (!wet) placeGreen(level, pos, grade.gradeY(x, z), x, z);
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
		if (off == 0) return CENTER_LINE;
		return ARTERIAL_ASPHALT;
	}

	private static void placeRoadColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState top) {
		set(level, pos.set(x, y, z), top);
		fillDown(level, pos, x, y - 1, z, FOUNDATION);
		clearUp(level, pos, x, y + 1, z);
	}

	// lots

	private static void placeLotColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, CityPlanner planner, GradeField grade,
									   Lot lot, int x, int z, boolean wet) {
		int base = lot.baseY();
		switch (lot.use()) {
			case UNBUILDABLE, PARK -> {
				if (!wet) placeGreen(level, pos, grade.gradeY(x, z), x, z);
			}
			case PLAZA -> {
				set(level, pos.set(x, base - 1, z), SIDEWALK);
				fillDown(level, pos, x, base - 2, z, FOUNDATION);
				clearUp(level, pos, x, base, z);
			}
			case BUILDING -> {
				if (BuildingShape.insideAt(lot, x, z, 0)) {
					BuildingPlacer.place(level, pos, planner, lot, x, z);
				} else {
					boolean homey = lot.spec().purpose() == BuildingSpec.Purpose.HOUSE || lot.spec().purpose() == BuildingSpec.Purpose.TOWNHOUSES;
					set(level, pos.set(x, base - 1, z), lot.downtown() > 0.35 && !homey ? SIDEWALK : GRASS);
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

	/** True if the top block of this column is water or ice */
	private static boolean surfaceWater(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		pos.set(x, top, z);
		return !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).is(BlockTags.ICE);
	}

	static void clearUp(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z) {
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
		for (int y = fromY; y < surface && y < fromY + 160; y++) {
			pos.set(x, y, z);
			if (level.isOutsideBuildHeight(y)) return;
			if (!level.getBlockState(pos).isAir()) level.setBlock(pos, AIR, FLAGS);
		}
		int airRun = 0;
		for (int y = Math.max(fromY, surface), n = 0; n < MAX_CLEAR && airRun < 3; y++, n++) {
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
