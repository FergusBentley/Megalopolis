package com.fergusb.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.function.Consumer;
import java.util.function.Predicate;

public interface Platform {
	<T> void register(Registry<T> registry, Identifier id, T value);
	void addOverworldFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature);
	void removeOverworldFeatures(Predicate<Identifier> shouldRemove);
	void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> registrar);
}
