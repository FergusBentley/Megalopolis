package com.fergusb.world;

import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Hashing;
import com.fergusb.plan.Lot;
import com.fergusb.plan.Rect;
import com.fergusb.plan.Road;
import com.fergusb.plan.TerrainSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Writes one chunk's slice of the plan */
public final class CityPlacer {
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	private static final int GRID = 8; // road height sample spacing
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
		RoadHeights heights = new RoadHeights(planner.terrain(), x0, z0);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = x0 + dx, z = z0 + dz;
				CityPlanner.Cell cell = planner.classify(x, z);
				switch (cell.kind()) {
					case ARTERIAL -> placeArterial(level, pos, cell, heights, x, z, dx, dz);
					case STREET -> placeStreet(level, pos, cell.road(), heights, x, z, dx, dz);
					case LOT -> placeLotColumn(level, pos, cell.lot(), x, z);
					default -> { }
				}
			}
		}
	}

	// roads

	private static void placeArterial(WorldGenLevel level, BlockPos.MutableBlockPos pos, CityPlanner.Cell cell,
			RoadHeights h, int x, int z, int dx, int dz) {
		if (h.water(dx, dz)) return; // TODO: bridges
		int y = h.y(dx, dz) - 1;
		boolean onX = cell.arterialDx() != Integer.MAX_VALUE;
		boolean onZ = cell.arterialDz() != Integer.MAX_VALUE;
		BlockState top;
		if (onX && onZ) {
			top = ARTERIAL_ASPHALT; // intersection
		} else {
			int off = onX ? cell.arterialDx() : cell.arterialDz();
			int along = onX ? z : x;
			if (Math.abs(off) >= CityPlanner.ARTERIAL_HALF_WIDTH - 1) top = SIDEWALK;
			else if (off == 0 && Math.floorMod(along, 8) < 4) top = LANE_LINE;
			else top = ARTERIAL_ASPHALT;
		}
		placeRoadColumn(level, pos, x, y, z, top);
	}

	private static void placeStreet(WorldGenLevel level, BlockPos.MutableBlockPos pos, Road road, RoadHeights h,
			int x, int z, int dx, int dz) {
		if (h.water(dx, dz)) return; // TODO: bridges
		int y = h.y(dx, dz) - 1;
		Rect r = road.rect();
		boolean edge = road.runsAlongZ() ? (x == r.minX() || x == r.maxX()) : (z == r.minZ() || z == r.maxZ());
		placeRoadColumn(level, pos, x, y, z, edge ? SIDEWALK : ASPHALT);
	}

	private static void placeRoadColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState top) {
		set(level, pos.set(x, y, z), top);
		fillDown(level, pos, x, y - 1, z, FOUNDATION);
		clearUp(level, pos, x, y + 1, z);
	}

	// lots

	private static void placeLotColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, Lot lot, int x, int z) {
		int base = lot.baseY();
		switch (lot.use()) {
			case UNBUILDABLE -> { }
			case PARK -> {
				set(level, pos.set(x, base - 1, z), GRASS);
				fillDown(level, pos, x, base - 2, z, DIRT);
				clearUp(level, pos, x, base, z);
			}
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

	private static void placeBuildingColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, Lot lot, int x, int z) {
		Rect fp = lot.footprint();
		int base = lot.baseY();
		int top = lot.topY();
		double cond = lot.condition();
		BlockState wall = pick(cond > 0.66 ? WALLS_GOOD : cond > 0.33 ? WALLS_MID : WALLS_BAD, lot.seed(), 1);
		BlockState glass = pick(cond > 0.5 ? GLASS_GOOD : GLASS_BAD, lot.seed(), 2);
		boolean edge = fp.onEdge(x, z);
		boolean corner = fp.onCorner(x, z);
		boolean lightColumn = Math.floorMod(x - fp.minX(), 6) == 3 && Math.floorMod(z - fp.minZ(), 6) == 3;
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

	private static final class RoadHeights {
		private final int[][] y = new int[3][3];
		private final boolean[][] water = new boolean[3][3];

		RoadHeights(TerrainSampler terrain, int x0, int z0) {
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					TerrainSampler.Sample s = terrain.sample(x0 + i * GRID, z0 + j * GRID);
					y[i][j] = s.groundY();
					water[i][j] = s.water();
				}
			}
		}

		int y(int dx, int dz) {
			int i = dx / GRID, j = dz / GRID;
			double fx = (dx % GRID) / (double) GRID, fz = (dz % GRID) / (double) GRID;
			double a = y[i][j] + (y[i + 1][j] - y[i][j]) * fx;
			double b = y[i][j + 1] + (y[i + 1][j + 1] - y[i][j + 1]) * fx;
			return (int) Math.round(a + (b - a) * fz);
		}

		boolean water(int dx, int dz) {
			int i = dx / GRID, j = dz / GRID;
			return water[i][j] || water[i + 1][j] || water[i][j + 1] || water[i + 1][j + 1];
		}
	}
}
