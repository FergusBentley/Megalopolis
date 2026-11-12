package com.fergusb.world;

import net.minecraft.resources.Identifier;

import java.util.Set;

/** Vanilla overworld features the city replaces */
public final class VanillaRemovals {
	private static final String[] PREFIXES = {
			"trees_", "flower_", "forest_flowers", "wildflowers_", "patch_", "bamboo", "birch_tall",
			"brown_mushroom_", "red_mushroom_", "pale_garden_", "pale_moss_patch", "dark_forest_vegetation",
			"mushroom_island_vegetation"};
	private static final Set<String> EXACT = Set.of(
			"vines", "forest_rock", "desert_well", "ice_spike", "ice_patch", "lake_lava_surface", "rooted_azalea_tree");
	private static final Set<String> KEEP = Set.of("patch_waterlily");

	private VanillaRemovals() {}

	public static boolean shouldRemove(Identifier id) {
		if (!id.getNamespace().equals("minecraft")) return false;
		String path = id.getPath();
		if (KEEP.contains(path)) return false;
		if (EXACT.contains(path)) return true;
		for (String prefix : PREFIXES) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}
}
