package com.fergusb;

import com.fergusb.platform.Platform;
import com.fergusb.world.CityFeature;
import com.fergusb.world.MegalopolisCommands;
import com.fergusb.world.VanillaRemovals;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Megalopolis {
	public static final String MOD_ID = "megalopolis";
	public static final Logger LOGGER = LoggerFactory.getLogger("Megalopolis");

	public static final ResourceKey<PlacedFeature> CITY_PLACED =
			ResourceKey.create(Registries.PLACED_FEATURE, id("city"));

	private static Platform platform;

	private Megalopolis() {}

	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;

		platform.register(BuiltInRegistries.FEATURE_TYPE, id("city"), CityFeature.CODEC);

		platform.addOverworldFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, CITY_PLACED);

		platform.removeOverworldFeatures(VanillaRemovals::shouldRemove);
		platform.register(BuiltInRegistries.CARVER_TYPE, id("capped"), com.fergusb.world.CappedCarver.CODEC);
		platform.replaceLandCarvers(java.util.stream.Stream.of("cave_deep", "cave_extra_underground_deep", "canyon_deep")
				.map(n -> ResourceKey.create(Registries.CARVER, id(n))).toList());
		var dripstoneCaves = ResourceKey.create(Registries.BIOME, net.minecraft.resources.Identifier.withDefaultNamespace("dripstone_caves"));
		platform.addBiomeFeature(dripstoneCaves, GenerationStep.Decoration.UNDERGROUND_DECORATION,
				ResourceKey.create(Registries.PLACED_FEATURE, id("dripstone_cluster_deep")));
		platform.addBiomeFeature(dripstoneCaves, GenerationStep.Decoration.UNDERGROUND_DECORATION,
				ResourceKey.create(Registries.PLACED_FEATURE, id("large_dripstone_deep")));
		platform.addOverworldFeature(GenerationStep.Decoration.LAKES,
				ResourceKey.create(Registries.PLACED_FEATURE, id("lake_lava_underground_deep")));

		platform.registerCommands(MegalopolisCommands::register);
		LOGGER.info("Megalopolis loaded");
	}

	public static Platform platform() {
		return platform;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
