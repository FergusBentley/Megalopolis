package com.fergusb.plan;

public record BuildingSpec(Purpose purpose, Shape shape, Massing massing, Roof roof,
		WindowStyle windows, PaletteFamily palette, int unitWidth, long seed, String biome, boolean woodAccent) {

	public enum Purpose {
		OFFICE, APARTMENT, MIXED_USE, STORE, TOWNHOUSES, HOUSE;

		/** First floor is a glass storefront with a door on the street side */
		public boolean hasStorefront() {
			return this == STORE || this == MIXED_USE;
		}

		public boolean residential() {
			return this == APARTMENT || this == TOWNHOUSES || this == HOUSE;
		}
	}

	public enum Shape { RECT, L, U, COURTYARD, ROUND, CHAMFERED }

	public enum Massing { STRAIGHT, SETBACKS, PODIUM_TOWER, TAPERED }

	public enum Roof { FLAT, PARAPET, GARDEN, EQUIPMENT, SPIRE, GABLE }

	public enum WindowStyle {
		BANDS,
		RIBBONS,
		PUNCHED,
		SINGLES,
		STRIPS,
		GRID,
		CURTAIN
	}

	public enum PaletteFamily {
		WHITE_MODERN, QUARTZ_GLASS, BLUE_ACCENT,
		DARK_GLASS, SLATE, BLACKSTONE,
		WARM_TERRACOTTA, SANDSTONE, RED_ACCENT,
		COPPER, TEAL_TECH, PRISMARINE,
		PASTEL, PURPUR, TUFF,
		MESA, FROST, VERDANT, MUD, TIMBER,
		DINGY_GRAY, DINGY_BROWN // run-down districts
	}
}
