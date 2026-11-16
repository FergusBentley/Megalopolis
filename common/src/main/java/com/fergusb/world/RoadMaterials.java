package com.fergusb.world;

import com.fergusb.plan.RoadStyle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

/** Blocks for each road style */
record RoadMaterials(BlockState road, BlockState roadSlab, BlockState sidewalk, BlockState sidewalkSlab,
		BlockState center, BlockState centerSlab, BlockState wall, BlockState retain, BlockState retainSlab) {

	private static final Map<RoadStyle, RoadMaterials> BY_STYLE = new EnumMap<>(RoadStyle.class);

	static {
		put(RoadStyle.ASPHALT, Blocks.CONCRETE.gray(), Blocks.CONCRETE_SLAB.gray(), Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB,
				Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB, Blocks.POLISHED_DEEPSLATE_WALL, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
		put(RoadStyle.DARK_ASPHALT, Blocks.CONCRETE.black(), Blocks.CONCRETE_SLAB.black(), Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB,
				Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB, Blocks.POLISHED_DEEPSLATE_WALL, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
		put(RoadStyle.STONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB,
				Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
		put(RoadStyle.MUD, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_SLAB, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB,
				Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB, Blocks.MUD_BRICK_WALL, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_SLAB);
		put(RoadStyle.DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_SLAB,
				Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB, Blocks.DEEPSLATE_TILE_WALL, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB);
		put(RoadStyle.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB,
				Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB);
		put(RoadStyle.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE,
				Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_SLAB,
				Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
		put(RoadStyle.TUFF, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICK_SLAB, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_SLAB,
				Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_WALL, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICK_SLAB);
	}

	static BlockState fence(String biome) {
		Block b;
		if (biome.contains("taiga") || biome.contains("snowy") || biome.contains("grove")) b = Blocks.SPRUCE_FENCE;
		else if (biome.contains("birch")) b = Blocks.BIRCH_FENCE;
		else if (biome.contains("jungle")) b = Blocks.JUNGLE_FENCE;
		else if (biome.contains("savanna")) b = Blocks.ACACIA_FENCE;
		else if (biome.contains("dark_forest")) b = Blocks.DARK_OAK_FENCE;
		else if (biome.contains("mangrove") || biome.contains("swamp")) b = Blocks.MANGROVE_FENCE;
		else if (biome.contains("cherry")) b = Blocks.CHERRY_FENCE;
		else if (biome.contains("pale_garden")) b = Blocks.PALE_OAK_FENCE;
		else if (biome.contains("dappled")) b = Blocks.POPLAR_FENCE;
		else if (biome.contains("bamboo")) b = Blocks.BAMBOO_FENCE;
		else b = Blocks.OAK_FENCE;
		return b.defaultBlockState();
	}

	static RoadMaterials of(RoadStyle style) {
		return BY_STYLE.get(style);
	}

	private static void put(RoadStyle s, Block road, Block roadSlab, Block sidewalk, Block sidewalkSlab,
			Block center, Block centerSlab, Block wall, Block retain, Block retainSlab) {
		BY_STYLE.put(s, new RoadMaterials(road.defaultBlockState(), roadSlab.defaultBlockState(), sidewalk.defaultBlockState(),
				sidewalkSlab.defaultBlockState(), center.defaultBlockState(), centerSlab.defaultBlockState(),
				wall.defaultBlockState(), retain.defaultBlockState(), retainSlab.defaultBlockState()));
	}
}
