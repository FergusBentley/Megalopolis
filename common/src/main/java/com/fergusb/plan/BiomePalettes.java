package com.fergusb.plan;

import com.fergusb.plan.BuildingSpec.PaletteFamily;

import java.util.Arrays;

public final class BiomePalettes {
	static final PaletteFamily[] ANY = Arrays.stream(PaletteFamily.values())
			.filter(f -> !f.dingy())
			.toArray(PaletteFamily[]::new);

	private BiomePalettes() {}

	static PaletteFamily[] families(String b) {
		if (b.contains("badlands")) return of(PaletteFamily.MESA, PaletteFamily.WARM_TERRACOTTA, PaletteFamily.RED_SANDSTONE, PaletteFamily.CINNABAR);
		if (b.contains("desert") || b.contains("beach")) return of(PaletteFamily.SANDSTONE, PaletteFamily.WARM_TERRACOTTA, PaletteFamily.WHITE_MODERN, PaletteFamily.SULFUR, PaletteFamily.OCHRE);
		if (b.contains("savanna")) return of(PaletteFamily.WARM_TERRACOTTA, PaletteFamily.RED_ACCENT, PaletteFamily.SANDSTONE, PaletteFamily.TIMBER, PaletteFamily.OCHRE, PaletteFamily.GRANITE);
		if (b.contains("snowy") || b.contains("frozen") || b.contains("ice") || b.contains("grove") || b.contains("peaks"))
			return of(PaletteFamily.FROST, PaletteFamily.WHITE_MODERN, PaletteFamily.QUARTZ_GLASS, PaletteFamily.BLUE_ACCENT, PaletteFamily.STEEL_BLUE, PaletteFamily.LOGWORK);
		if (b.contains("jungle") || b.contains("bamboo")) return of(PaletteFamily.VERDANT, PaletteFamily.TEAL_TECH, PaletteFamily.PRISMARINE, PaletteFamily.TIMBER);
		if (b.contains("swamp") || b.contains("mangrove")) return of(PaletteFamily.MUD, PaletteFamily.TUFF, PaletteFamily.SLATE, PaletteFamily.VERDANT);
		if (b.contains("cherry") || b.contains("meadow") || b.contains("flower")) return of(PaletteFamily.PASTEL, PaletteFamily.WHITE_MODERN, PaletteFamily.LILAC, PaletteFamily.TIMBER);
		if (b.contains("taiga") || b.contains("windswept")) return of(PaletteFamily.SLATE, PaletteFamily.TIMBER, PaletteFamily.TUFF, PaletteFamily.BLACKSTONE, PaletteFamily.FROST, PaletteFamily.COPPER, PaletteFamily.LOGWORK, PaletteFamily.BASALT);
		if (b.contains("dark_forest") || b.contains("pale_garden")) return of(PaletteFamily.DARK_GLASS, PaletteFamily.BLACKSTONE, PaletteFamily.TIMBER, PaletteFamily.SLATE, PaletteFamily.RESIN, PaletteFamily.LOGWORK);
		if (b.contains("mushroom")) return of(PaletteFamily.PURPUR, PaletteFamily.RED_ACCENT, PaletteFamily.PASTEL, PaletteFamily.LILAC);
		if (b.contains("ocean") || b.contains("river") || b.contains("shore")) return of(PaletteFamily.PRISMARINE, PaletteFamily.BLUE_ACCENT, PaletteFamily.WHITE_MODERN, PaletteFamily.TEAL_TECH, PaletteFamily.STEEL_BLUE);
		if (b.contains("forest") || b.contains("birch") || b.contains("dappled")) return of(PaletteFamily.TIMBER, PaletteFamily.WHITE_MODERN, PaletteFamily.COPPER, PaletteFamily.TUFF, PaletteFamily.VERDANT, PaletteFamily.TEAL_TECH, PaletteFamily.LOGWORK);
		return of(PaletteFamily.WHITE_MODERN, PaletteFamily.RED_ACCENT, PaletteFamily.BLUE_ACCENT, PaletteFamily.COPPER, PaletteFamily.QUARTZ_GLASS, PaletteFamily.DARK_GLASS, PaletteFamily.GRANITE, PaletteFamily.OCHRE);
	}

	/** Biomes where wood accents are more common */
	static boolean woody(String b) {
		return b.contains("forest") || b.contains("taiga") || b.contains("jungle") || b.contains("birch")
				|| b.contains("cherry") || b.contains("dappled") || b.contains("grove") || b.contains("swamp");
	}

	private static PaletteFamily[] of(PaletteFamily... f) {
		return f;
	}
}
