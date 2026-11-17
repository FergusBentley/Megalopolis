package com.fergusb.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class ParkSpreader {
	private static final double[] SPREAD_CHANCE = {0.5, 0.3, 0.15};

	private ParkSpreader() {}

	static List<Lot> spread(List<Lot> lots, CityPlanner planner) {
		List<Lot> out = new ArrayList<>(lots);
		int[] generation = new int[out.size()];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int i = 0; i < out.size(); i++) {
			generation[i] = out.get(i).use() == Lot.Use.PARK ? 0 : -1;
			if (generation[i] == 0) queue.add(i);
		}
		while (!queue.isEmpty()) {
			int i = queue.poll();
			int g = generation[i];
			if (g >= SPREAD_CHANCE.length) continue;
			Obb a = out.get(i).bounds();
			for (int k = 0; k < out.size(); k++) {
				Lot other = out.get(k);
				if (generation[k] != -1 || !touches(a, other.bounds())) continue;
				if (other.use() != Lot.Use.BUILDING && other.use() != Lot.Use.PLAZA) continue;
				generation[k] = planner.parkRoll(other.bounds().cx, other.bounds().cz, SPREAD_CHANCE[g]) ? g + 1 : Integer.MAX_VALUE;
				if (generation[k] == g + 1) {
					out.set(k, new Lot(other.bounds(), null, Lot.Use.PARK, other.baseY(), 0,
							other.downtown(), other.condition(), other.seed(), null, other.mask()));
					queue.add(k);
				}
			}
		}
		return out;
	}

	private static boolean touches(Obb a, Obb b) {
		return a.minX <= b.maxX + 2 && a.maxX >= b.minX - 2 && a.minZ <= b.maxZ + 2 && a.maxZ >= b.minZ - 2;
	}
}
