package com.fergusb.plan;

/** Lets the planner look at terrain without generating chunks */
public interface TerrainSampler {
	/** groundY = first air/water block above solid ground. surfaceY = first air block */
	record Sample(int groundY, int surfaceY) {
		public boolean water() {
			return surfaceY > groundY;
		}
	}

	Sample sample(int x, int z);

	default String biome(int x, int z) {
		return "plains";
	}
}
