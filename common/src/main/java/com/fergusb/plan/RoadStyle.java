package com.fergusb.plan;

/** Road surface materials */
public enum RoadStyle {
	ASPHALT, DARK_ASPHALT, STONE, MUD, DEEPSLATE, SANDSTONE, BLACKSTONE, TUFF;

	/** Street style for a district */
	static RoadStyle forStreets(String biome, double downtown, double condition, double roll) {
		if (condition < 0.35 && roll < 0.6) return STONE;
		if (downtown > 0.6 && roll < 0.6) return ASPHALT;
		if (biome.contains("desert") || biome.contains("badlands") || biome.contains("beach")) return SANDSTONE;
		if (biome.contains("swamp") || biome.contains("mangrove")) return MUD;
		if (biome.contains("taiga") || biome.contains("snowy") || biome.contains("windswept") || biome.contains("grove")) {
			return roll < 0.5 ? DEEPSLATE : STONE;
		}
		if (biome.contains("dark_forest") || biome.contains("pale_garden")) return BLACKSTONE;
		if (biome.contains("jungle") || biome.contains("lush")) return roll < 0.5 ? TUFF : STONE;
		return roll < 0.55 ? ASPHALT : roll < 0.8 ? STONE : TUFF;
	}

	static RoadStyle forArterial(String biome, double roll) {
		if (roll < 0.65) return DARK_ASPHALT;
		return forStreets(biome, 0.5, 1, roll);
	}
}
