package com.fergusb.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

public record CappedCarver(WorldCarver carver, int maxY) implements WorldCarver {
	public static final MapCodec<CappedCarver> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			WorldCarver.DIRECT_CODEC.fieldOf("carver").forGetter(CappedCarver::carver),
			Codec.INT.fieldOf("max_y").forGetter(CappedCarver::maxY)
	).apply(i, CappedCarver::new));

	@Override
	public int getRange() {
		return carver.getRange();
	}

	@Override
	public boolean isStartChunk(RandomSource random) {
		return carver.isStartChunk(random);
	}

	@Override
	public boolean carve(WorldGenerationContext context, RandomSource random, ChunkPos chunk, ChunkPos start, CarverOutput output) {
		int ceiling = maxY;
		return carver.carve(context, random, chunk, start, new CarverOutput() {
			@Override
			public void carve(int x, int y, int z) {
				if (y <= ceiling) output.carve(x, y, z);
			}

			@Override
			public int minY() {
				return output.minY();
			}

			@Override
			public int maxY() {
				return Math.min(output.maxY(), ceiling);
			}
		});
	}

	@Override
	public MapCodec<CappedCarver> codec() {
		return CODEC;
	}
}
