package com.fergusb.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

public record CityFeature() implements Feature {
	public static final MapCodec<CityFeature> CODEC = MapCodec.unit(CityFeature::new);

	@Override
	public MapCodec<CityFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		ServerLevel server = level.getLevel();
		if (server.dimension() != Level.OVERWORLD) return false;
		CityPlacer.placeChunk(level, Planners.get(server), origin.getX() >> 4, origin.getZ() >> 4);
		return true;
	}
}
