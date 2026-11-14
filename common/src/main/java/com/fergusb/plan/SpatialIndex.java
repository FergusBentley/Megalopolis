package com.fergusb.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpatialIndex {
	private static final int CELL = 32;
	private static final int[] EMPTY = new int[0];

	private final Map<Long, int[]> cells = new HashMap<>();

	public SpatialIndex(List<int[]> boxes) { // each box: {minX, minZ, maxX, maxZ}
		Map<Long, List<Integer>> tmp = new HashMap<>();
		for (int i = 0; i < boxes.size(); i++) {
			int[] b = boxes.get(i);
			for (int cx = Math.floorDiv(b[0], CELL); cx <= Math.floorDiv(b[2], CELL); cx++) {
				for (int cz = Math.floorDiv(b[1], CELL); cz <= Math.floorDiv(b[3], CELL); cz++) {
					tmp.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(i);
				}
			}
		}
		tmp.forEach((k, v) -> cells.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
	}

	public int[] query(int x, int z) {
		return cells.getOrDefault(key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)), EMPTY);
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}
}
