package com.fergusb.world;

import com.fergusb.plan.BuildingSpec.PaletteFamily;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

public final class Palettes {
	public record Palette(BlockState wall, BlockState trim, BlockState accent, BlockState glass,
			BlockState roof, BlockState weatheredWall, BlockState weatheredGlass) {}

	private static final Map<PaletteFamily, Palette> PALETTES = new EnumMap<>(PaletteFamily.class);

	static {
		put(PaletteFamily.WHITE_MODERN, Blocks.CONCRETE.white(), Blocks.CONCRETE.lightGray(), Blocks.CONCRETE.gray(),
				Blocks.STAINED_GLASS.lightBlue(), Blocks.CONCRETE.lightGray(), Blocks.DYED_TERRACOTTA.white(), Blocks.GLASS);
		put(PaletteFamily.QUARTZ_GLASS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.CONCRETE.cyan(),
				Blocks.STAINED_GLASS.cyan(), Blocks.SMOOTH_QUARTZ, Blocks.CALCITE, Blocks.GLASS);
		put(PaletteFamily.BLUE_ACCENT, Blocks.CONCRETE.white(), Blocks.CONCRETE.blue(), Blocks.CONCRETE.blue(),
				Blocks.STAINED_GLASS.blue(), Blocks.CONCRETE.lightGray(), Blocks.DYED_TERRACOTTA.lightBlue(), Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.DARK_GLASS, Blocks.CONCRETE.black(), Blocks.CONCRETE.gray(), Blocks.CONCRETE.gray(),
				Blocks.STAINED_GLASS.gray(), Blocks.CONCRETE.gray(), Blocks.DYED_TERRACOTTA.black(), Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.SLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.CONCRETE.lightGray(),
				Blocks.STAINED_GLASS.lightGray(), Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CONCRETE.yellow(),
				Blocks.STAINED_GLASS.gray(), Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.WARM_TERRACOTTA, Blocks.TERRACOTTA, Blocks.CONCRETE.white(), Blocks.DYED_TERRACOTTA.orange(),
				Blocks.GLASS, Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.brown(), Blocks.STAINED_GLASS.brown());
		put(PaletteFamily.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CONCRETE.orange(),
				Blocks.STAINED_GLASS.lightBlue(), Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.GLASS);
		put(PaletteFamily.RED_ACCENT, Blocks.CONCRETE.lightGray(), Blocks.CONCRETE.white(), Blocks.CONCRETE.red(),
				Blocks.GLASS, Blocks.CONCRETE.gray(), Blocks.DYED_TERRACOTTA.lightGray(), Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.COPPER, Blocks.CUT_COPPER.waxed().exposed(), Blocks.POLISHED_ANDESITE, Blocks.CUT_COPPER.waxed().unaffected(),
				Blocks.GLASS, Blocks.CUT_COPPER.waxed().weathered(), Blocks.CUT_COPPER.waxed().oxidized(), Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.TEAL_TECH, Blocks.DYED_TERRACOTTA.cyan(), Blocks.CONCRETE.white(), Blocks.CONCRETE.cyan(),
				Blocks.STAINED_GLASS.cyan(), Blocks.CONCRETE.lightGray(), Blocks.DYED_TERRACOTTA.cyan(), Blocks.GLASS);
		put(PaletteFamily.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICKS,
				Blocks.STAINED_GLASS.cyan(), Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.GLASS);
		put(PaletteFamily.PASTEL, Blocks.DYED_TERRACOTTA.pink(), Blocks.CONCRETE.white(), Blocks.CONCRETE.magenta(),
				Blocks.GLASS, Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.pink(), Blocks.GLASS);
		put(PaletteFamily.PURPUR, Blocks.PURPUR_BLOCK, Blocks.END_STONE_BRICKS, Blocks.CONCRETE.purple(),
				Blocks.STAINED_GLASS.magenta(), Blocks.PURPUR_BLOCK, Blocks.END_STONE_BRICKS, Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF,
				Blocks.STAINED_GLASS.lightGray(), Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.GLASS);
		// Biome families
		put(PaletteFamily.MESA, Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.red(),
				Blocks.GLASS, Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.brown(), Blocks.STAINED_GLASS.brown());
		put(PaletteFamily.FROST, Blocks.CALCITE, Blocks.CONCRETE.white(), Blocks.CONCRETE.lightBlue(),
				Blocks.STAINED_GLASS.lightBlue(), Blocks.CONCRETE.white(), Blocks.POLISHED_DIORITE, Blocks.GLASS);
		put(PaletteFamily.VERDANT, Blocks.DYED_TERRACOTTA.green(), Blocks.CONCRETE.white(), Blocks.CONCRETE.lime(),
				Blocks.GLASS, Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS, Blocks.STAINED_GLASS.green());
		put(PaletteFamily.MUD, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MANGROVE_PLANKS,
				Blocks.GLASS, Blocks.PACKED_MUD, Blocks.PACKED_MUD, Blocks.STAINED_GLASS.brown());
		// TIMBER's wall is replaced by the biome's wood
		put(PaletteFamily.TIMBER, Blocks.OAK_PLANKS, Blocks.CONCRETE.white(), Blocks.CONCRETE.gray(),
				Blocks.GLASS, Blocks.CONCRETE.gray(), Blocks.OAK_PLANKS, Blocks.GLASS);
		// Run-down districts
		put(PaletteFamily.DINGY_GRAY, Blocks.DYED_TERRACOTTA.lightGray(), Blocks.STONE_BRICKS, Blocks.COBBLESTONE,
				Blocks.STAINED_GLASS.gray(), Blocks.COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.STAINED_GLASS.brown());
		put(PaletteFamily.DINGY_BROWN, Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.black(), Blocks.PACKED_MUD,
				Blocks.STAINED_GLASS.brown(), Blocks.PACKED_MUD, Blocks.MOSSY_COBBLESTONE, Blocks.STAINED_GLASS.gray());
	}

	private Palettes() {}

	public static Palette of(PaletteFamily family, String biome) {
		Palette p = PALETTES.get(family);
		if (family == PaletteFamily.TIMBER) {
			BlockState wood = wood(biome);
			return new Palette(wood, p.trim(), p.accent(), p.glass(), p.roof(), wood, p.weatheredGlass());
		}
		return p;
	}

	private static void put(PaletteFamily f, Block wall, Block trim, Block accent, Block glass, Block roof,
			Block weatheredWall, Block weatheredGlass) {
		PALETTES.put(f, new Palette(wall.defaultBlockState(), trim.defaultBlockState(), accent.defaultBlockState(),
				glass.defaultBlockState(), roof.defaultBlockState(), weatheredWall.defaultBlockState(),
				weatheredGlass.defaultBlockState()));
	}

	public static BlockState wood(String biome) {
		Block b;
		if (biome.contains("taiga") || biome.contains("snowy") || biome.contains("grove")) b = Blocks.SPRUCE_PLANKS;
		else if (biome.contains("birch")) b = Blocks.BIRCH_PLANKS;
		else if (biome.contains("jungle")) b = Blocks.JUNGLE_PLANKS;
		else if (biome.contains("savanna")) b = Blocks.ACACIA_PLANKS;
		else if (biome.contains("dark_forest")) b = Blocks.DARK_OAK_PLANKS;
		else if (biome.contains("mangrove") || biome.contains("swamp")) b = Blocks.MANGROVE_PLANKS;
		else if (biome.contains("cherry")) b = Blocks.CHERRY_PLANKS;
		else if (biome.contains("pale_garden")) b = Blocks.PALE_OAK_PLANKS;
		else if (biome.contains("dappled")) b = Blocks.POPLAR_PLANKS;
		else if (biome.contains("bamboo")) b = Blocks.BAMBOO_PLANKS;
		else b = Blocks.OAK_PLANKS;
		return b.defaultBlockState();
	}
}
