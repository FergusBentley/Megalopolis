package com.fergusb.world;

import com.fergusb.plan.BuildingShape;
import com.fergusb.plan.BuildingSpec;
import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Lot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
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
	private static final BlockState SIDEWALK = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState SIDEWALK_SLAB = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

	private CityPlacer() {}

	public static void placeChunk(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
								  CityPlanner planner, int chunkX, int chunkZ) {
		int x0 = chunkX << 4, z0 = chunkZ << 4;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// Read water before writing anything
		boolean[][] wide = waterMask(level, pos, x0, z0, Surface.B);
		Surface[] built = new Surface[1];
		BuildingShape.OWNER.set((x, z) -> {
			Surface s = built[0];
			int dx = x - x0, dz = z - z0;
			if (s != null && dx >= -Surface.B && dz >= -Surface.B && dx < 16 + Surface.B && dz < 16 + Surface.B) return s.cell(dx, dz).lot();
			return planner.classify(x, z).lot();
		});
		try {
			placeChunk(level, generator, random, planner, x0, z0, pos, wide, built);
		} finally {
			BuildingShape.OWNER.remove();
		}
	}

	private static void placeChunk(WorldGenLevel level, ChunkGenerator generator, RandomSource random, CityPlanner planner,
								   int x0, int z0, BlockPos.MutableBlockPos pos, boolean[][] wide, Surface[] built) {
		boolean[][] water = new boolean[18][18];
		for (int i = 0; i < 18; i++) {
			for (int j = 0; j < 18; j++) water[i][j] = wide[i - 1 + Surface.B][j - 1 + Surface.B];
		}
		Surface surf = new Surface(planner, wide, x0, z0);
		built[0] = surf;
		RoadPlacer roads = new RoadPlacer(level, surf);
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = x0 + dx, z = z0 + dz;
				CityPlanner.Cell cell = surf.cell(dx, dz);
				boolean wet = (water[dx + 1][dz + 1] || surfaceWater(level, pos, x, z)) && !surf.filled(dx, dz);
				if (surf.builtRoad(dx, dz)) {
					roads.placeColumn(dx, dz, wet);
					continue;
				}
				if (surf.natural(dx, dz)) {
					roads.lineTunnel(dx, dz);
					continue;
				}
				roads.lineTunnel(dx, dz);
				switch (cell.kind()) {
					case LOT -> placeLotColumn(level, pos, planner, surf, cell.lot(), dx, dz, wet);
					default -> {
						if (wet) break;
						if (surf.parkSide(dx, dz)) placeGreen(level, pos, surf.groundY(dx, dz), x, z);
						else placePaved(level, pos, surf.height(dx, dz), x, z);
					}
				}
			}
		}
		roads.finish();
		ParkDecorator.decorate(level, generator, random, planner, surf, water);
		DoorSteps.place(level, pos, planner, surf);
	}

	private static boolean[][] waterMask(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x0, int z0, int border) {
		int y = level.getSeaLevel() - 1;
		boolean[][] mask = new boolean[16 + 2 * border][16 + 2 * border];
		for (int dx = -border; dx < 16 + border; dx++) {
			for (int dz = -border; dz < 16 + border; dz++) {
				mask[dx + border][dz + border] = seaColumn(level, pos, x0 + dx, z0 + dz, y);
			}
		}
		return mask;
	}

	/** True if the column is open sea */
	private static boolean seaColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y) {
		if (!waterOrIce(level.getBlockState(pos.set(x, y, z)))) return false;
		for (int up = y + 1; up < y + 64; up++) {
			BlockState b = level.getBlockState(pos.set(x, up, z));
			if (b.isAir()) return true;
			if (waterOrIce(b) || b.is(Blocks.SNOW) || b.is(Blocks.SNOW_BLOCK) || b.is(Blocks.POWDER_SNOW)) continue;
			return false;
		}
		return true;
	}

	private static boolean waterOrIce(BlockState b) {
		return !b.getFluidState().isEmpty() || b.is(BlockTags.ICE);
	}

	// lots

	private static void placeLotColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, CityPlanner planner, Surface surf,
									   Lot lot, int dx, int dz, boolean wet) {
		int x = surf.x0 + dx, z = surf.z0 + dz;
		switch (lot.use()) {
			case PARK -> {
				if (!wet) placeGreen(level, pos, surf.groundY(dx, dz), x, z);
			}
			case UNBUILDABLE, GREEN -> {
				if (wet) break;
				if (surf.parkSide(dx, dz)) placeGreen(level, pos, surf.groundY(dx, dz), x, z);
				else placePaved(level, pos, surf.height(dx, dz), x, z);
			}
			case PLAZA -> placePaved(level, pos, surf.height(dx, dz), x, z);
			case BUILDING -> {
				if (surf.building(dx, dz)) {
					BuildingPlacer.place(level, pos, planner, surf, lot, x, z);
				} else {
					if (grassy(lot)) placeGreen(level, pos, surf.groundY(dx, dz), x, z);
					else placePaved(level, pos, surf.height(dx, dz), x, z);
				}
			}
		}
	}

	static boolean grassy(Lot lot) {
		if (lot.use() == Lot.Use.PARK) return true;
		return lot.downtown() < 0.12 && com.fergusb.plan.Hashing.unit(com.fergusb.plan.Hashing.hash(lot.seed(), 404)) < 0.3;
	}

	static void placePaved(WorldGenLevel level, BlockPos.MutableBlockPos pos, double height, int x, int z) {
		int k = (int) Math.floor(height);
		boolean half = height - k > 0.25;
		set(level, pos.set(x, k - 1, z), SIDEWALK);
		if (half) set(level, pos.set(x, k, z), SIDEWALK_SLAB);
		fillDown(level, pos, x, k - 2, z, FOUNDATION);
		clearUp(level, pos, x, half ? k + 1 : k, z);
	}

	private static void placeGreen(WorldGenLevel level, BlockPos.MutableBlockPos pos, int surfaceY, int x, int z) {
		set(level, pos.set(x, surfaceY - 1, z), GRASS);
		fillDown(level, pos, x, surfaceY - 2, z, DIRT);
		clearUp(level, pos, x, surfaceY, z);
	}

	// helpers

	static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.isOutsideBuildHeight(pos.getY())) return;
		level.setBlock(pos, state, FLAGS);
	}

	static void fillDown(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z, BlockState state) {
		for (int y = fromY, n = 0; n < MAX_FILL; y--, n++) {
			pos.set(x, y, z);
			if (level.isOutsideBuildHeight(y)) return;
			if (!fillable(level.getBlockState(pos))) return;
			level.setBlock(pos, state, FLAGS);
		}
	}

	static boolean fillable(BlockState existing) {
		return existing.isAir() || !existing.getFluidState().isEmpty() || existing.canBeReplaced() || existing.is(BlockTags.ICE);
	}

	/** True if the top block of this column is water or ice */
	static boolean surfaceWater(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		pos.set(x, top, z);
		return !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).is(Blocks.ICE);
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
