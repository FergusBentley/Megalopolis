package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

final class GridLayout {
	private final CityPlanner planner;
	private final Rect bounds;
	private final Rng rng;

	GridLayout(CityPlanner planner, Rect bounds, Rng rng) {
		this.planner = planner;
		this.bounds = bounds;
		this.rng = rng;
	}

	void plan(List<Road> streets, List<Lot> lots) {
		List<Rect> blocks = new ArrayList<>();
		split(bounds, streets, blocks);
		for (Rect block : blocks) makeLots(block, lots);
	}

	private void split(Rect r, List<Road> streets, List<Rect> blocks) {
		double dt = planner.fields().downtown(r.centerX(), r.centerZ());
		int maxSize = (int) ValueNoise.lerp(dt, 52, 100);
		if (r.width() <= maxSize && r.depth() <= maxSize) {
			blocks.add(r);
			return;
		}
		int w = CityPlanner.STREET_WIDTH;
		boolean cutAcrossX = r.width() >= r.depth(); // street runs along Z, cutting the X extent
		int len = cutAcrossX ? r.width() : r.depth();
		int offset = (int) (len * rng.range(0.38, 0.62));
		if (offset < 20 || len - offset - w < 20) {
			blocks.add(r);
			return;
		}
		Rect a, b, street;
		if (cutAcrossX) {
			int s = r.minX() + offset;
			street = new Rect(s, r.minZ(), s + w - 1, r.maxZ());
			a = new Rect(r.minX(), r.minZ(), s - 1, r.maxZ());
			b = new Rect(s + w, r.minZ(), r.maxX(), r.maxZ());
		} else {
			int s = r.minZ() + offset;
			street = new Rect(r.minX(), s, r.maxX(), s + w - 1);
			a = new Rect(r.minX(), r.minZ(), r.maxX(), s - 1);
			b = new Rect(r.minX(), s + w, r.maxX(), r.maxZ());
		}
		streets.add(Road.ofRect(street));
		split(a, streets, blocks);
		split(b, streets, blocks);
	}

	private void makeLots(Rect block, List<Lot> out) {
		double dt = planner.fields().downtown(block.centerX(), block.centerZ());
		if (rng.chance(0.03 + 0.10 * (1 - dt))) {
			out.add(planner.finishLot(Obb.ofRect(block), rng, true));
			return;
		}
		boolean alongX = block.width() >= block.depth(); // lots line up along the long axis
		int shortLen = alongX ? block.depth() : block.width();
		int shortMin = alongX ? block.minZ() : block.minX();
		int shortMax = alongX ? block.maxZ() : block.maxX();
		List<int[]> rows = new ArrayList<>(); // [lo, hi] on the short axis
		if (shortLen >= 20) {
			int mid = shortMin + shortLen / 2 + rng.nextInt(-2, 2);
			rows.add(new int[] {shortMin, mid - 1});
			rows.add(new int[] {mid, shortMax});
		} else {
			rows.add(new int[] {shortMin, shortMax});
		}
		int longMin = alongX ? block.minX() : block.minZ();
		int longMax = alongX ? block.maxX() : block.maxZ();
		int minW = (int) ValueNoise.lerp(dt, 8, 14);
		int maxW = (int) ValueNoise.lerp(dt, 18, 40);
		for (int[] row : rows) {
			int cursor = longMin;
			while (cursor <= longMax) {
				int remaining = longMax - cursor + 1;
				int w = rng.nextInt(minW, maxW);
				if (remaining - w < minW) w = remaining;
				Rect lot = alongX
						? new Rect(cursor, row[0], cursor + w - 1, row[1])
						: new Rect(row[0], cursor, row[1], cursor + w - 1);
				out.add(planner.finishLot(Obb.ofRect(lot), rng, false));
				cursor += w;
			}
		}
	}
}
