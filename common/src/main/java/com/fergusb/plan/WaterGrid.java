package com.fergusb.plan;

final class WaterGrid {
	private final int gx0, gz0, gw, gd;
	private final boolean[] wet;

	WaterGrid(TerrainSampler terrain, GradeField grade, int minX, int minZ, int maxX, int maxZ) {
		gx0 = Math.round((minX - 4) / 4f);
		gz0 = Math.round((minZ - 4) / 4f);
		int gx1 = Math.round((maxX + 4) / 4f), gz1 = Math.round((maxZ + 4) / 4f);
		gw = gx1 - gx0 + 1;
		gd = gz1 - gz0 + 1;
		wet = new boolean[gw * gd];
		for (int i = 0; i < gw; i++) {
			for (int j = 0; j < gd; j++) wet[i * gd + j] = terrain.sample((gx0 + i) * 4, (gz0 + j) * 4).water() || grade.smallIslandAt((gx0 + i) * 4, (gz0 + j) * 4);
		}
	}

	boolean at(int x, int z) {
		int i = Math.round(x / 4f) - gx0, j = Math.round(z / 4f) - gz0;
		return i >= 0 && j >= 0 && i < gw && j < gd && wet[i * gd + j];
	}
}
