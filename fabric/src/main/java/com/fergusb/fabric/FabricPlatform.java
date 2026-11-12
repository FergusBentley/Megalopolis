package com.fergusb.fabric;

import com.fergusb.platform.Platform;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.function.Consumer;

public final class FabricPlatform implements Platform {
	@Override
	public <T> void register(Registry<T> registry, Identifier id, T value) {
		Registry.register(registry, id, value);
	}

	@Override
	public void addOverworldFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature) {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), step, feature);
	}

	@Override
	public void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> registrar) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registrar.accept(dispatcher));
	}
}
