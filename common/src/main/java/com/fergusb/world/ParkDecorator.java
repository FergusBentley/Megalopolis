package com.fergusb.world;

import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Hashing;
import com.fergusb.plan.Lot;
import com.fergusb.plan.Obb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

final class ParkDecorator {
	private static final int CANOPY = 4;

	private ParkDecorator() {}

	static void decorate(WorldGenLevel level, ChunkGenerator generator, RandomSource random, CityPlanner planner,
			Surface surf, boolean[][] water) {
		int x0 = surf.x0, z0 = surf.z0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean[][] planted = new boolean[16][16];
		int[][] wildTop = new int[16][16];
		String chunkBiome = planner.terrain().biome(x0 + 8, z0 + 8);
		java.util.Map<Lot, String> lotBiomes = new java.util.IdentityHashMap<>(); // one lookup per lot per chunk

		// ground

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx, z = z0 + dz;
				CityPlanner.Cell cell = surf.cell(dx, dz);
				Kind kind = kind(cell, surf, dx, dz);
				if (kind == Kind.NONE || wet(level, pos, surf, water, dx, dz)) continue;
				int y = surf.groundY(dx, dz); // first block above the ground
				String biome = cell.lot() != null ? lotBiomes.computeIfAbsent(cell.lot(), l -> biome(planner, l)) : chunkBiome;
				long h = Hashing.hash(planner.seed(), x, z, 91);
				double r = Hashing.unit(h);
				if (kind == Kind.WILD) {
					int top = naturalTop(level, pos, x, z, y);
					if (top == 0) continue;
					BlockState under = level.getBlockState(pos.set(x, top - 1, z));
					boolean soil = under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.PODZOL)
							|| isSandy(biome) && under.is(net.minecraft.tags.BlockTags.SAND);
					if (!soil) continue;
					wildTop[dx][dz] = top;
					BlockState plant = cover(biome, r / 0.8, h);
					if (plant != null) CityPlacer.set(level, pos.set(x, top, z), plant);
					continue;
				}

				ParkPaths.Plan paths = kind == Kind.PARK ? ParkPaths.of(planner, cell.lot())
						: surf.parkSide(dx, dz) ? nearPark(planner, surf, dx, dz) : null;
				if (paths != null && paths.path(x, z)) {
					CityPlacer.set(level, pos.set(x, y - 1, z), Blocks.DIRT_PATH.defaultBlockState());
					planted[dx][dz] = true; // keep trees off paths
					continue;
				}
				if (paths != null && paths.lamp(x, z)) {
					BlockState soil = soil(biome);
					if (soil != null) CityPlacer.set(level, pos.set(x, y - 1, z), soil);
					CityPlacer.set(level, pos.set(x, y, z), Blocks.OAK_FENCE.defaultBlockState());
					CityPlacer.set(level, pos.set(x, y + 1, z), Blocks.LANTERN.defaultBlockState());
					planted[dx][dz] = true;
					continue;
				}
				BlockState soil = soil(biome);
				if (soil != null) CityPlacer.set(level, pos.set(x, y - 1, z), soil);

				double density = kind == Kind.OPEN ? 0.4 : 1.0;
				BlockState cover = cover(biome, r / density, h);
				if (cover != null) CityPlacer.set(level, pos.set(x, y, z), cover);
			}
		}

		// trees

		Registry<Feature> features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
		for (int cu = CANOPY; cu <= 15 - CANOPY; cu += 4) {
			for (int cv = CANOPY; cv <= 15 - CANOPY; cv += 4) {
				long h = Hashing.hash(planner.seed(), x0 + cu, z0 + cv, 92);
				int dx = Math.min(15 - CANOPY, cu + (int) (Hashing.unit(h) * 4));
				int dz = Math.min(15 - CANOPY, cv + (int) (Hashing.unit(Hashing.hash(h, 1)) * 4));
				CityPlanner.Cell cell = surf.cell(dx, dz);
				Kind kind = kind(cell, surf, dx, dz);
				double chance = kind == Kind.PARK ? 0.7 : kind == Kind.GREEN ? 0.3 : kind == Kind.WILD ? 0.3 : 0;
				if (Hashing.unit(Hashing.hash(h, 2)) >= chance || planted[dx][dz]) continue;
				if (!clearAround(level, pos, surf, water, dx, dz, kind)) continue;
				if (kind == Kind.WILD && !wildGround(wildTop, dx, dz)) continue;
				int x = x0 + dx, z = z0 + dz;
				String biome = cell.lot() != null ? lotBiomes.computeIfAbsent(cell.lot(), l -> biome(planner, l)) : chunkBiome;
				String tree = tree(biome, Hashing.unit(Hashing.hash(h, 3)));
				if (tree == null) {
					if (isSandy(biome)) cactus(level, pos, x, kind == Kind.WILD ? wildTop[dx][dz] : surf.groundY(dx, dz), z, h);
					continue;
				}
				int y = kind == Kind.WILD ? wildTop[dx][dz] : surf.groundY(dx, dz);
				CityPlacer.set(level, pos.set(x, y, z), Blocks.AIR.defaultBlockState()); // clear the cover
				features.getOptional(Identifier.withDefaultNamespace(tree))
						.ifPresent(f -> f.place(level, generator, random, new BlockPos(x, y, z)));
			}
		}
	}

	private enum Kind { NONE, PARK, GREEN, OPEN, WILD }

	private static ParkPaths.Plan nearPark(CityPlanner planner, Surface surf, int dx, int dz) {
		for (int r = 1; r <= 6; r++) {
			for (int ox = -r; ox <= r; ox++) {
				for (int oz = -r; oz <= r; oz++) {
					if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
					CityPlanner.Cell c = surf.cell(dx + ox, dz + oz);
					if (c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.PARK) return ParkPaths.of(planner, c.lot());
				}
			}
		}
		return null;
	}

	private static int naturalTop(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int near) {
		int hi = Math.min(level.getMaxY(), near + 32), lo = Math.max(level.getMinY() + 1, near - 32);
		for (int y = hi; y >= lo; y--) {
			BlockState b = level.getBlockState(pos.set(x, y, z));
			if (b.isAir()) continue;
			if (!b.getFluidState().isEmpty() || b.is(net.minecraft.tags.BlockTags.ICE)) return 0;
			return y + 1;
		}
		return 0;
	}

	private static boolean wildGround(int[][] top, int dx, int dz) {
		int y = top[dx][dz];
		if (y == 0) return false;
		for (int ox = -2; ox <= 2; ox++) {
			for (int oz = -2; oz <= 2; oz++) {
				int t = top[dx + ox][dz + oz];
				if (t == 0 || Math.abs(t - y) > 1) return false;
			}
		}
		return true;
	}

	private static boolean wet(WorldGenLevel level, BlockPos.MutableBlockPos pos, Surface surf, boolean[][] water, int dx, int dz) {
		return (water[dx + 1][dz + 1] && !surf.filled(dx, dz)) || CityPlacer.surfaceWater(level, pos, surf.x0 + dx, surf.z0 + dz);
	}

	private static Kind kind(CityPlanner.Cell cell, Surface surf, int dx, int dz) {
		if (surf.natural(dx, dz) && !surf.builtRoad(dx, dz)) return Kind.WILD;
		if (surf.parkSide(dx, dz)) return Kind.GREEN;
		if (cell.kind() == CityPlanner.Kind.LOT) {
			if (cell.lot().use() == Lot.Use.PARK) return Kind.PARK;
			return Kind.NONE;
		}
		if (cell.kind() == CityPlanner.Kind.GAP) {
			return Kind.NONE; // open ground is paved
		}
		return Kind.NONE;
	}

	private static boolean clearAround(WorldGenLevel level, BlockPos.MutableBlockPos pos, Surface surf,
			boolean[][] water, int dx, int dz, Kind kind) {
		for (int ox = -CANOPY + 1; ox <= CANOPY - 1; ox++) {
			for (int oz = -CANOPY + 1; oz <= CANOPY - 1; oz++) {
				int ax = dx + ox, az = dz + oz;
				if (ax < 0 || az < 0 || ax > 15 || az > 15) return false;
				if (kind(surf.cell(ax, az), surf, ax, az) != kind) return false;
				if (wet(level, pos, surf, water, ax, az)) return false;
			}
		}
		return true;
	}

	// biome choices

	private static String biome(CityPlanner planner, Lot lot) {
		if (lot == null) return "plains";
		return planner.terrain().biome((int) Math.round(lot.bounds().cx), (int) Math.round(lot.bounds().cz));
	}

	private static boolean isSandy(String b) {
		return b.contains("desert") || b.contains("beach") || b.contains("badlands");
	}

	/** Ground block (if not grass) */
	private static BlockState soil(String b) {
		if (b.contains("badlands")) return Blocks.RED_SAND.defaultBlockState();
		if (b.contains("desert") || b.contains("beach")) return Blocks.SAND.defaultBlockState();
		if (b.contains("old_growth") && b.contains("taiga")) return Blocks.PODZOL.defaultBlockState();
		return null;
	}

	/** Plant cover */
	private static BlockState cover(String b, double r, long h) {
		if (isSandy(b)) {
			return r < 0.04 ? Blocks.DEAD_BUSH.defaultBlockState() : r < 0.08 ? Blocks.SHORT_DRY_GRASS.defaultBlockState() : null;
		}
		if (r < 0.28) return b.contains("jungle") || b.contains("taiga") ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
		if (r < 0.36) return flower(b, Hashing.unit(Hashing.hash(h, 5)));
		if (r < 0.38) return Blocks.BUSH.defaultBlockState();
		if (r < 0.385 && b.contains("taiga")) return Blocks.SWEET_BERRY_BUSH.defaultBlockState();
		if (r < 0.387) return Blocks.PUMPKIN.defaultBlockState();
		if (r < 0.389 && b.contains("jungle")) return Blocks.MELON.defaultBlockState();
		if (r < 0.40 && (b.contains("forest") || b.contains("birch"))) return Blocks.LEAF_LITTER.defaultBlockState();
		return null;
	}

	private static BlockState flower(String b, double r) {
		Block[] options;
		if (b.contains("cherry")) options = new Block[] {Blocks.PINK_PETALS, Blocks.PINK_PETALS, Blocks.ALLIUM};
		else if (b.contains("swamp") || b.contains("mangrove")) options = new Block[] {Blocks.BLUE_ORCHID};
		else if (b.contains("flower") || b.contains("meadow")) options = new Block[] {Blocks.ALLIUM, Blocks.AZURE_BLUET,
				Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.OXEYE_DAISY, Blocks.WILDFLOWERS, Blocks.POPPY};
		else if (b.contains("birch")) options = new Block[] {Blocks.WILDFLOWERS, Blocks.LILY_OF_THE_VALLEY, Blocks.DANDELION};
		else if (b.contains("taiga") || b.contains("snowy") || b.contains("grove")) options = new Block[] {Blocks.FERN, Blocks.POPPY};
		else options = new Block[] {Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER};
		return options[(int) (r * options.length)].defaultBlockState();
	}

	private static String tree(String b, double r) {
		String[] options;
		if (isSandy(b)) return null;
		if (b.contains("dappled")) options = new String[] {"yellow_poplar", "orange_poplar", "red_poplar"};
		else if (b.contains("cherry")) options = new String[] {"cherry", "cherry", "cherry_bees_005"};
		else if (b.contains("pale_garden")) options = new String[] {"pale_oak"};
		else if (b.contains("dark_forest")) options = new String[] {"dark_oak", "oak"};
		else if (b.contains("birch")) options = new String[] {"birch", "birch", "birch_bees_005"};
		else if (b.contains("taiga") || b.contains("snowy") || b.contains("grove")) options = new String[] {"spruce", "pine"};
		else if (b.contains("jungle") || b.contains("bamboo")) options = new String[] {"jungle_tree_no_vine", "jungle_bush"};
		else if (b.contains("savanna")) options = new String[] {"acacia"};
		else if (b.contains("swamp") || b.contains("mangrove")) options = new String[] {"swamp_oak", "mangrove"};
		else if (b.contains("forest")) options = new String[] {"oak", "birch", "fancy_oak", "oak_bees_005", "birch_bees_005"};
		else options = new String[] {"oak", "oak", "fancy_oak", "oak_bees_005"};
		return options[(int) (r * options.length)];
	}

	private static void cactus(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, long h) {
		int height = 1 + (int) (Hashing.unit(Hashing.hash(h, 7)) * 3);
		for (int k = 0; k < height; k++) CityPlacer.set(level, pos.set(x, y + k, z), Blocks.CACTUS.defaultBlockState());
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) CityPlacer.set(level, pos.set(x + d[0], y, z + d[1]), Blocks.AIR.defaultBlockState());
	}
}
