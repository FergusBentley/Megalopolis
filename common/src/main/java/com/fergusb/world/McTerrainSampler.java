package com.fergusb.world;

import com.fergusb.Megalopolis;
import com.fergusb.plan.Hashing;
import com.fergusb.plan.PointCache;
import com.fergusb.plan.Stats;
import com.fergusb.plan.TerrainSampler;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/** Samples terrain from noise generator without generating chunks */
public final class McTerrainSampler implements TerrainSampler {
	public static volatile boolean fast = true;
	private final ChunkGenerator generator;
	private final RandomState randomState;
	private final LevelHeightAccessor heightAccessor;
	private final int seaLevel;
	private final ThreadLocal<BiomeResolver> biomes;
	private final PointCache cache = new PointCache(20);
	private final NoiseGeneratorSettings settings;
	private final Aquifer.FluidPicker fluids;
	private final int minY, maxY;
	private static final int WINDOW = 24;
	private static final int CELL_HEIGHT = 8;

	public McTerrainSampler(ServerLevel level) {
		this.generator = level.getChunkSource().getGenerator();
		this.randomState = level.getChunkSource().randomState();
		this.heightAccessor = level;
		this.seaLevel = generator.getSeaLevel();
		this.biomes = ThreadLocal.withInitial(() -> generator.getBiomeSource().createUncachedResolver(randomState));
		NoiseGeneratorSettings s = null;
		Aquifer.FluidPicker f = null;
		int lo = level.getMinY(), hi = level.getMaxY();
		if (generator instanceof NoiseBasedChunkGenerator noise) {
			try {
				s = noise.generatorSettings().value();
				java.lang.reflect.Field field = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
				field.setAccessible(true);
				f = (Aquifer.FluidPicker) ((java.util.function.Supplier<?>) field.get(noise)).get();
				var ns = s.noiseSettings().clampToHeightAccessor(level);
				lo = ns.minY();
				hi = ns.minY() + ns.height() - 1;
			} catch (Throwable t) {
				Megalopolis.LOGGER.warn("Fast terrain sampling unavailable, using getBaseHeight", t);
				s = null;
				f = null;
			}
		}
		this.settings = s;
		this.fluids = f;
		this.minY = lo;
		this.maxY = hi;
	}

	@Override
	public String biome(int x, int z) {
		int y = sample(x, z).surfaceY();
		return biomes.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
				.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
	}
	@Override
	public Sample sample(int x, int z) {
		long key = Hashing.pack(x, z);
		long hit = cache.get(key);
		if (hit != PointCache.MISSING) return new Sample((int) (hit >> 32), (int) hit);
		long t0 = System.nanoTime();
		Sample s = fast && settings != null ? fastSample(x, z) : vanillaSample(x, z);
		Stats.noise.incrementAndGet();
		Stats.noiseNanos.addAndGet(System.nanoTime() - t0);
		cache.put(key, ((long) s.groundY() << 32) | (s.surfaceY() & 0xFFFFFFFFL));
		return s;
	}

	public Sample vanillaSample(int x, int z) {
		int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
		int ground = surface > seaLevel + 1
				? surface
				: generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
		return new Sample(ground, Math.max(ground, surface));
	}

	public Sample fastSample(int x, int z) {
		if (settings == null) return vanillaSample(x, z);
		int expect = expectedSurface(x, z);
		int lo = Math.min(expect, seaLevel) - WINDOW, hi = Math.max(expect, seaLevel) + WINDOW;
		Sample s = scan(x, z, lo, hi);
		return s != null ? s : scan(x, z, minY, maxY);
	}

	private int expectedSurface(int x, int z) {
		try {
			float e = randomState.sampleBlockValueUncached(settings.noiseRouter().chunkSurfaceLevel(), x, seaLevel, z);
			if (!Float.isNaN(e) && e > minY && e < maxY) return Math.round(e);
		} catch (Throwable ignored) {
		}
		return seaLevel;
	}

	private Sample scan(int x, int z, int lo, int hi) {
		lo = Math.max(minY, minY + Math.floorDiv(lo - minY, CELL_HEIGHT) * CELL_HEIGHT);
		hi = Math.min(maxY, minY + (Math.floorDiv(hi - minY, CELL_HEIGHT) + 1) * CELL_HEIGHT - 1);
		boolean whole = lo == minY && hi == maxY;
		DensityVolume volume = new DensityVolume(1, hi - lo + 1, 1, x, lo, z);
		NoiseChunk chunk = new NoiseChunk(randomState, null, settings, fluids, Blender.empty(), volume);
		try (ScopedDensityBuffer buffer = chunk.cachingSamplers().get(settings.noiseRouter().finalDensity()).sampleVolume(chunk.volume())) {
			Aquifer aquifer = chunk.aquifer();
			BlockState fallback = settings.defaultBlock();
			var notAir = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
			var solid = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
			int surface = Integer.MIN_VALUE;
			for (int iy = volume.sizeY() - 1; iy >= 0; iy--) {
				float d = buffer.get(volume.indexUnchecked(0, iy, 0));
				int y = volume.blockY(iy);
				BlockState st = aquifer.computeSubstance(x, y, z, d);
				if (st == null) st = fallback;
				if (surface == Integer.MIN_VALUE) {
					if (!notAir.test(st)) continue;
					if (iy == volume.sizeY() - 1 && !whole) return null;
					surface = y + 1;
					if (surface > seaLevel + 1) return new Sample(surface, surface);
				}
				if (solid.test(st)) return new Sample(y + 1, Math.max(y + 1, surface));
			}
			if (whole) return new Sample(minY, surface == Integer.MIN_VALUE ? minY : surface);
			return null;
		} finally {
			chunk.close();
		}
	}
}
