package com.fergusb.world;

import com.fergusb.plan.TerrainSampler;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/** Samples terrain from noise generator without generating chunks */
public final class McTerrainSampler implements TerrainSampler {
	private final ChunkGenerator generator;
	private final RandomState randomState;
	private final LevelHeightAccessor heightAccessor;
	private final ThreadLocal<BiomeResolver> biomes;

	public McTerrainSampler(ServerLevel level) {
		this.generator = level.getChunkSource().getGenerator();
		this.randomState = level.getChunkSource().randomState();
		this.heightAccessor = level;
		this.biomes = ThreadLocal.withInitial(() -> generator.getBiomeSource().createUncachedResolver(randomState));
	}

	@Override
	public Sample sample(int x, int z) {
		int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
		int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
		return new Sample(ground, Math.max(ground, surface));
	}

	@Override
	public String biome(int x, int z) {
		int y = sample(x, z).surfaceY();
		return biomes.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
				.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
	}
}
