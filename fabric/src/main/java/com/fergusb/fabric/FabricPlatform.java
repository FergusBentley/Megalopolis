package com.fergusb.fabric;

import com.fergusb.platform.Platform;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import com.fergusb.Megalopolis;
import net.minecraft.core.Holder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
	public void removeOverworldFeatures(Predicate<Identifier> shouldRemove) {
		BiomeModifications.create(Megalopolis.id("remove_vanilla_surface"))
				.add(ModificationPhase.REMOVALS, BiomeSelectors.foundInOverworld(), context -> {
					var settings = context.getGenerationSettings();
					for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
						for (Holder<PlacedFeature> feature : List.copyOf(settings.getFeatures(step))) {
							feature.unwrapKey().ifPresent(key -> {
								if (shouldRemove.test(key.identifier())) settings.removeFeature(step, key);
							});
						}
					}
				});
	}

	@Override
	public void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> registrar) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registrar.accept(dispatcher));
	}
}
