package com.fergusb.world;

import com.fergusb.plan.BuildingSpec.PaletteFamily;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Palettes {
	public record Palette(BlockState wall, BlockState trim, BlockState accent, BlockState glass,
						  BlockState roof, BlockState weatheredWall, BlockState weatheredGlass, BlockState storefront) {}

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
		put(PaletteFamily.MUD, Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.MANGROVE_PLANKS,
				Blocks.GLASS, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.STAINED_GLASS.brown());
		// TIMBER's wall is replaced by the biome's wood
		put(PaletteFamily.TIMBER, Blocks.OAK_PLANKS, Blocks.CONCRETE.white(), Blocks.CONCRETE.gray(),
				Blocks.GLASS, Blocks.CONCRETE.gray(), Blocks.OAK_PLANKS, Blocks.GLASS);
		put(PaletteFamily.LOGWORK, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_LOG, Blocks.CONCRETE.black(),
				Blocks.GLASS_PANE, Blocks.DEEPSLATE_TILES, Blocks.STRIPPED_OAK_LOG, Blocks.STAINED_GLASS.gray());

		put(PaletteFamily.CINNABAR, Blocks.POLISHED_CINNABAR, Blocks.CINNABAR_BRICKS, Blocks.CHISELED_CINNABAR,
				Blocks.STAINED_GLASS.red(), Blocks.CINNABAR_BRICKS, Blocks.CINNABAR, Blocks.STAINED_GLASS.brown());
		put(PaletteFamily.SULFUR, Blocks.POLISHED_SULFUR, Blocks.SULFUR_BRICKS, Blocks.CHISELED_SULFUR,
				Blocks.STAINED_GLASS.yellow(), Blocks.SULFUR_BRICKS, Blocks.SULFUR, Blocks.STAINED_GLASS.brown());
		put(PaletteFamily.BASALT, Blocks.POLISHED_BASALT, Blocks.SMOOTH_BASALT, Blocks.QUARTZ_PILLAR,
				Blocks.STAINED_GLASS.gray(), Blocks.SMOOTH_BASALT, Blocks.BASALT, Blocks.STAINED_GLASS.black());
		put(PaletteFamily.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
				Blocks.STAINED_GLASS.orange(), Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.GLASS);
		put(PaletteFamily.RESIN, Blocks.RESIN_BRICKS, Blocks.CHISELED_RESIN_BRICKS, Blocks.DARK_OAK_PLANKS,
				Blocks.STAINED_GLASS.orange(), Blocks.RESIN_BRICKS, Blocks.RESIN_BLOCK, Blocks.STAINED_GLASS.brown());
		put(PaletteFamily.GRANITE, Blocks.POLISHED_GRANITE, Blocks.CONCRETE.white(), Blocks.DYED_TERRACOTTA.red(),
				Blocks.STAINED_GLASS.brown(), Blocks.DYED_TERRACOTTA.brown(), Blocks.GRANITE, Blocks.GLASS);

		put(PaletteFamily.LILAC, Blocks.DYED_TERRACOTTA.purple(), Blocks.CONCRETE.white(), Blocks.DYED_TERRACOTTA.magenta(),
				Blocks.STAINED_GLASS.purple(), Blocks.DYED_TERRACOTTA.gray(), Blocks.DYED_TERRACOTTA.purple(), Blocks.GLASS);
		put(PaletteFamily.OCHRE, Blocks.DYED_TERRACOTTA.yellow(), Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.lime(),
				Blocks.GLASS, Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.STAINED_GLASS.yellow());
		put(PaletteFamily.STEEL_BLUE, Blocks.DYED_TERRACOTTA.blue(), Blocks.DYED_TERRACOTTA.gray(), Blocks.CONCRETE.lightGray(),
				Blocks.STAINED_GLASS.blue(), Blocks.DYED_TERRACOTTA.gray(), Blocks.DYED_TERRACOTTA.blue(), Blocks.STAINED_GLASS.gray());
		// Run-down districts
		put(PaletteFamily.DINGY_GRAY, Blocks.DYED_TERRACOTTA.lightGray(), Blocks.STONE_BRICKS, Blocks.COBBLESTONE,
				Blocks.STAINED_GLASS.gray(), Blocks.COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.STAINED_GLASS.brown());
		put(PaletteFamily.DINGY_BROWN, Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.black(), Blocks.PACKED_MUD,
				Blocks.STAINED_GLASS.brown(), Blocks.PACKED_MUD, Blocks.MOSSY_COBBLESTONE, Blocks.STAINED_GLASS.gray());
		put(PaletteFamily.DINGY_DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.IRON_BARS,
				Blocks.STAINED_GLASS.gray(), Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.STAINED_GLASS.brown());
	}

	// role pools

	private static final List<BlockState> TRIM_POOL = states(
			Blocks.CONCRETE.white(), Blocks.CONCRETE.lightGray(), Blocks.CONCRETE.gray(), Blocks.CONCRETE.black(),
			Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE,
			Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CUT_SANDSTONE, Blocks.CUT_RED_SANDSTONE,
			Blocks.CINNABAR_BRICKS, Blocks.SULFUR_BRICKS, Blocks.RESIN_BRICKS, Blocks.TUFF_BRICKS, Blocks.END_STONE_BRICKS,
			Blocks.PRISMARINE_BRICKS, Blocks.MUD_BRICKS, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT,
			Blocks.CUT_COPPER.waxed().unaffected(), Blocks.CUT_COPPER.waxed().exposed(),
			Blocks.CUT_COPPER.waxed().weathered(), Blocks.CUT_COPPER.waxed().oxidized(),
			Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.lightGray(), Blocks.DYED_TERRACOTTA.gray(),
			Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.black());

	private static final List<BlockState> ACCENT_POOL = states(
			Blocks.CONCRETE.blue(), Blocks.CONCRETE.cyan(), Blocks.CONCRETE.red(), Blocks.CONCRETE.orange(),
			Blocks.CONCRETE.yellow(), Blocks.CONCRETE.lime(), Blocks.CONCRETE.magenta(), Blocks.CONCRETE.purple(),
			Blocks.CONCRETE.lightBlue(), Blocks.CONCRETE.gray(),
			Blocks.CHISELED_TUFF, Blocks.CHISELED_CINNABAR, Blocks.CHISELED_SULFUR, Blocks.CHISELED_RESIN_BRICKS,
			Blocks.CHISELED_RED_SANDSTONE, Blocks.PRISMARINE_BRICKS, Blocks.QUARTZ_PILLAR, Blocks.POLISHED_BASALT,
			Blocks.CUT_COPPER.waxed().unaffected(), Blocks.CUT_COPPER.waxed().exposed(),
			Blocks.CUT_COPPER.waxed().weathered(), Blocks.CUT_COPPER.waxed().oxidized(),
			Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.red(), Blocks.DYED_TERRACOTTA.yellow(),
			Blocks.DYED_TERRACOTTA.lime(), Blocks.DYED_TERRACOTTA.cyan(), Blocks.DYED_TERRACOTTA.blue(),
			Blocks.DYED_TERRACOTTA.magenta(), Blocks.DYED_TERRACOTTA.purple(), Blocks.DYED_TERRACOTTA.pink());

	private static final List<BlockState> ROOF_POOL = states(
			Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CONCRETE.gray(), Blocks.CONCRETE.lightGray(),
			Blocks.CUT_COPPER.waxed().weathered(), Blocks.CUT_COPPER.waxed().oxidized(),
			Blocks.CINNABAR_BRICKS, Blocks.SULFUR_BRICKS, Blocks.SMOOTH_BASALT, Blocks.TUFF_BRICKS, Blocks.RESIN_BRICKS,
			Blocks.SMOOTH_RED_SANDSTONE, Blocks.DYED_TERRACOTTA.gray(), Blocks.DYED_TERRACOTTA.brown());

	private static final List<BlockState> GRATES = states(
			Blocks.COPPER_GRATE.waxed().unaffected(), Blocks.COPPER_GRATE.waxed().exposed(),
			Blocks.COPPER_GRATE.waxed().weathered(), Blocks.COPPER_GRATE.waxed().oxidized());
	private static final List<BlockState> BARS = states(
			Blocks.IRON_BARS, Blocks.COPPER_BARS.waxed().unaffected(), Blocks.COPPER_BARS.waxed().exposed(),
			Blocks.COPPER_BARS.waxed().weathered(), Blocks.COPPER_BARS.waxed().oxidized());
	private static final List<BlockState> GRILLES = states(
			Blocks.OAK_TRAPDOOR, Blocks.ACACIA_TRAPDOOR, Blocks.JUNGLE_TRAPDOOR, Blocks.CHERRY_TRAPDOOR, Blocks.MANGROVE_TRAPDOOR,
			Blocks.BAMBOO_TRAPDOOR, Blocks.POPLAR_TRAPDOOR, Blocks.CRIMSON_TRAPDOOR, Blocks.WARPED_TRAPDOOR, Blocks.IRON_TRAPDOOR,
			Blocks.COPPER_TRAPDOOR.waxed().unaffected(), Blocks.COPPER_TRAPDOOR.waxed().exposed(),
			Blocks.COPPER_TRAPDOOR.waxed().weathered(), Blocks.COPPER_TRAPDOOR.waxed().oxidized());

	private Palettes() {}

	public static Palette mixed(PaletteFamily family, String biome, long seed) {
		Palette p = of(family, biome);
		List<PaletteFamily> all = new ArrayList<>();
		for (PaletteFamily f : PaletteFamily.values()) if (!f.dingy()) all.add(f);
		BlockState trim = p.trim(), accent = p.accent(), glass = p.glass(), roof = p.roof();
		List<BlockState> trims = new ArrayList<>(TRIM_POOL), accents = new ArrayList<>(ACCENT_POOL);
		trims.add(log(biome, false));
		trims.add(log(biome, true));
		accents.add(log(biome, false));
		accents.add(log(biome, true));
		if (unit(seed, 1) < 0.35) trim = unit(seed, 7) < 0.5 ? pick(trims, seed, 2) : of(all.get(index(seed, 2, all.size())), biome).trim();
		if (unit(seed, 3) < 0.35) accent = unit(seed, 8) < 0.5 ? pick(accents, seed, 4) : of(all.get(index(seed, 4, all.size())), biome).accent();
		if (unit(seed, 9) < 0.2) roof = pick(ROOF_POOL, seed, 10);
		double w = unit(seed, 5);
		if (w < 0.35) glass = pane(p.glass(), seed);
		else if (w < 0.40) glass = pick(GRATES, seed, 6);
		else if (w < 0.45) glass = pick(BARS, seed, 6);
		else if (w < 0.50) glass = pick(GRILLES, seed, 6);
		else if (w < 0.65) glass = of(all.get(index(seed, 6, all.size())), biome).glass();
		return new Palette(p.wall(), trim, accent, glass, roof, p.weatheredWall(), p.weatheredGlass(), p.storefront());
	}

	/** Matching pane */
	private static BlockState pane(BlockState glass, long seed) {
		List<Block> glasses = Blocks.STAINED_GLASS.asList(), panes = Blocks.STAINED_GLASS_PANE.asList();
		int i = glasses.indexOf(glass.getBlock());
		if (unit(seed, 11) < 0.5) {
			if (glass.getBlock() == Blocks.GLASS || glass.getBlock() == Blocks.GLASS_PANE) return Blocks.GLASS_PANE.defaultBlockState();
			if (i >= 0) return panes.get(i).defaultBlockState();
		}
		int k = index(seed, 12, panes.size() + 1);
		return k == panes.size() ? Blocks.GLASS_PANE.defaultBlockState() : panes.get(k).defaultBlockState();
	}

	private static BlockState pick(List<BlockState> pool, long seed, int salt) {
		return pool.get(index(seed, salt, pool.size()));
	}

	private static int index(long seed, int salt, int n) {
		return Math.min(n - 1, (int) (unit(seed, salt) * n));
	}

	private static double unit(long seed, int salt) {
		return com.fergusb.plan.Hashing.unit(com.fergusb.plan.Hashing.hash(seed, 71, salt));
	}

	public static Palette of(PaletteFamily family, String biome) {
		Palette p = PALETTES.get(family);
		if (family == PaletteFamily.TIMBER) {
			BlockState wood = wood(biome);
			return new Palette(wood, p.trim(), p.accent(), p.glass(), p.roof(), wood, p.weatheredGlass(), p.storefront());
		}
		if (family == PaletteFamily.LOGWORK) {
			BlockState stripped = log(biome, true), log = log(biome, false);
			return new Palette(stripped, log, p.accent(), p.glass(), p.roof(), stripped, p.weatheredGlass(), p.storefront());
		}
		return p;
	}

	private static void put(PaletteFamily f, Block wall, Block trim, Block accent, Block glass, Block roof,
			Block weatheredWall, Block weatheredGlass) {
		Block storefront = glass == Blocks.GLASS || Blocks.STAINED_GLASS.asList().contains(glass) ? glass : Blocks.GLASS;
		PALETTES.put(f, new Palette(wall.defaultBlockState(), trim.defaultBlockState(), accent.defaultBlockState(),
				glass.defaultBlockState(), roof.defaultBlockState(), weatheredWall.defaultBlockState(),
				weatheredGlass.defaultBlockState(), storefront.defaultBlockState()));
	}

	private static List<BlockState> states(Block... blocks) {
		List<BlockState> out = new ArrayList<>();
		for (Block b : blocks) out.add(b.defaultBlockState());
		return out;
	}

	public static BlockState wood(String biome) {
		Block b = switch (woodKind(biome)) {
			case "spruce" -> Blocks.SPRUCE_PLANKS;
			case "birch" -> Blocks.BIRCH_PLANKS;
			case "jungle" -> Blocks.JUNGLE_PLANKS;
			case "acacia" -> Blocks.ACACIA_PLANKS;
			case "dark_oak" -> Blocks.DARK_OAK_PLANKS;
			case "mangrove" -> Blocks.MANGROVE_PLANKS;
			case "cherry" -> Blocks.CHERRY_PLANKS;
			case "pale_oak" -> Blocks.PALE_OAK_PLANKS;
			case "poplar" -> Blocks.POPLAR_PLANKS;
			case "bamboo" -> Blocks.BAMBOO_PLANKS;
			default -> Blocks.OAK_PLANKS;
		};
		return b.defaultBlockState();
	}

	public static BlockState log(String biome, boolean stripped) {
		Block b = switch (woodKind(biome)) {
			case "spruce" -> stripped ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_LOG;
			case "birch" -> stripped ? Blocks.STRIPPED_BIRCH_LOG : Blocks.BIRCH_LOG;
			case "jungle" -> stripped ? Blocks.STRIPPED_JUNGLE_LOG : Blocks.JUNGLE_LOG;
			case "acacia" -> stripped ? Blocks.STRIPPED_ACACIA_LOG : Blocks.ACACIA_LOG;
			case "dark_oak" -> stripped ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG;
			case "mangrove" -> stripped ? Blocks.STRIPPED_MANGROVE_LOG : Blocks.MANGROVE_LOG;
			case "cherry" -> stripped ? Blocks.STRIPPED_CHERRY_LOG : Blocks.CHERRY_LOG;
			case "pale_oak" -> stripped ? Blocks.STRIPPED_PALE_OAK_LOG : Blocks.PALE_OAK_LOG;
			case "poplar" -> stripped ? Blocks.STRIPPED_POPLAR_LOG : Blocks.POPLAR_LOG;
			case "bamboo" -> stripped ? Blocks.STRIPPED_BAMBOO_BLOCK : Blocks.BAMBOO_BLOCK;
			default -> stripped ? Blocks.STRIPPED_OAK_LOG : Blocks.OAK_LOG;
		};
		return b.defaultBlockState();
	}

	private static final Map<Block, Block> BARK = new java.util.HashMap<>();

	static {
		Block[][] pairs = {
				{Blocks.OAK_LOG, Blocks.OAK_WOOD}, {Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD}, {Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD},
				{Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD}, {Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD}, {Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD},
				{Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD}, {Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD}, {Blocks.PALE_OAK_LOG, Blocks.PALE_OAK_WOOD},
				{Blocks.POPLAR_LOG, Blocks.POPLAR_WOOD},
				{Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_WOOD}, {Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD},
				{Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD}, {Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_WOOD},
				{Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_WOOD}, {Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_WOOD},
				{Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD}, {Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CHERRY_WOOD},
				{Blocks.STRIPPED_PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_WOOD}, {Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_WOOD}};
		for (Block[] p : pairs) BARK.put(p[0], p[1]);
	}

	public static BlockState bark(BlockState st) {
		Block wood = BARK.get(st.getBlock());
		return wood == null ? st : wood.defaultBlockState();
	}

	public static Palette bark(Palette p) {
		return new Palette(bark(p.wall()), bark(p.trim()), bark(p.accent()), p.glass(), bark(p.roof()),
				bark(p.weatheredWall()), p.weatheredGlass(), p.storefront());
	}

	public static BlockState door(String biome, boolean residential, long seed) {
		if (!residential) {
			var c = Blocks.COPPER_DOOR.waxed();
			Block[] stages = {c.unaffected(), c.exposed(), c.weathered(), c.oxidized()};
			return stages[index(seed, 13, stages.length)].defaultBlockState();
		}
		Block b = switch (woodKind(biome)) {
			case "spruce" -> Blocks.SPRUCE_DOOR;
			case "birch" -> Blocks.BIRCH_DOOR;
			case "jungle" -> Blocks.JUNGLE_DOOR;
			case "acacia" -> Blocks.ACACIA_DOOR;
			case "dark_oak" -> Blocks.DARK_OAK_DOOR;
			case "mangrove" -> Blocks.MANGROVE_DOOR;
			case "cherry" -> Blocks.CHERRY_DOOR;
			case "pale_oak" -> Blocks.PALE_OAK_DOOR;
			case "poplar" -> Blocks.POPLAR_DOOR;
			case "bamboo" -> Blocks.BAMBOO_DOOR;
			default -> Blocks.OAK_DOOR;
		};
		// Mostly the biome's wood
		if (unit(seed, 14) >= BIOME_DOOR) b = WOOD_DOORS[index(seed, 15, WOOD_DOORS.length)];
		return b.defaultBlockState();
	}

	private static final double BIOME_DOOR = 0.6;
	private static final Block[] WOOD_DOORS = {
			Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR, Blocks.JUNGLE_DOOR, Blocks.ACACIA_DOOR,
			Blocks.DARK_OAK_DOOR, Blocks.MANGROVE_DOOR, Blocks.CHERRY_DOOR, Blocks.PALE_OAK_DOOR, Blocks.POPLAR_DOOR,
			Blocks.BAMBOO_DOOR, Blocks.CRIMSON_DOOR, Blocks.WARPED_DOOR};

	public static boolean thin(BlockState st) {
		return st.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock
				|| st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock;
	}

	private static String woodKind(String biome) {
		if (biome.contains("taiga") || biome.contains("snowy") || biome.contains("grove")) return "spruce";
		if (biome.contains("birch")) return "birch";
		if (biome.contains("bamboo")) return "bamboo";
		if (biome.contains("jungle")) return "jungle";
		if (biome.contains("savanna")) return "acacia";
		if (biome.contains("dark_forest")) return "dark_oak";
		if (biome.contains("mangrove") || biome.contains("swamp")) return "mangrove";
		if (biome.contains("cherry")) return "cherry";
		if (biome.contains("pale_garden")) return "pale_oak";
		if (biome.contains("dappled")) return "poplar";
		return "oak";
	}
}
