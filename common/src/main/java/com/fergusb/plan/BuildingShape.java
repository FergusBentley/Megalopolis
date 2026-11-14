package com.fergusb.plan;

public final class BuildingShape {
	private BuildingShape() {}

	/** Local cell of a world column */
	public static int[] local(Obb fp, int x, int z) {
		if (!fp.contains(x, z)) return null;
		int lu = (int) Math.round(fp.u(x, z) + fp.halfU);
		int lv = (int) Math.round(fp.v(x, z) + fp.halfV);
		return new int[] {Math.max(0, Math.min(fp.widthU() - 1, lu)), Math.max(0, Math.min(fp.depthV() - 1, lv))};
	}

	public static boolean insideAt(Lot lot, int x, int z, int floor) {
		int[] c = local(lot.footprint(), x, z);
		return c != null && inside(lot, c[0], c[1], floor);
	}

	public static boolean inside(Lot lot, int lu, int lv, int floor) {
		BuildingSpec s = lot.spec();
		int w = lot.footprint().widthU(), d = lot.footprint().depthV();
		int inset = inset(s, w, d, floor, lot.floors());
		int u = lu - inset, v = lv - inset, iw = w - 2 * inset, id = d - 2 * inset;
		if (u < 0 || v < 0 || u >= iw || v >= id) return false;
		return insideFootprint(s, u, v, iw, id);
	}

	/** Townhouse unit index of a local cell */
	public static int unit(BuildingSpec s, int lu, int w) {
		if (s.unitWidth() <= 0) return 0;
		int units = Math.max(1, w / s.unitWidth());
		return Math.min(lu / s.unitWidth(), units - 1);
	}

	/** First and last local u of a townhouse unit */
	public static int[] unitSpan(BuildingSpec s, int unit, int w) {
		int units = Math.max(1, w / s.unitWidth());
		int start = unit * s.unitWidth();
		int end = unit == units - 1 ? w - 1 : start + s.unitWidth() - 1;
		return new int[] {start, end};
	}

	static int inset(BuildingSpec s, int w, int d, int floor, int floors) {
		int minDim = Math.min(w, d);
		int maxInset = Math.max(0, (minDim - 7) / 2); // never thinner than 7 blocks
		int inset = switch (s.massing()) {
			case STRAIGHT -> 0;
			case SETBACKS -> {
				int tier = Math.max(4, floors / 4);
				yield (floor / tier) * 2;
			}
			case PODIUM_TOWER -> {
				int podium = Math.min(4, Math.max(2, floors / 5));
				yield floor < podium ? 0 : Math.max(2, (int) (minDim * 0.22));
			}
			case TAPERED -> (int) Math.round((double) floor / Math.max(1, floors) * minDim * 0.25);
		};
		return Math.min(inset, maxInset);
	}

	private static boolean insideFootprint(BuildingSpec s, int u, int v, int w, int d) {
		long h = s.seed();
		switch (s.shape()) {
			case RECT:
				return true;
			case L: {
				int nu = (int) (w * (0.45 + 0.15 * Hashing.unit(Hashing.hash(h, 11))));
				int nv = (int) (d * (0.45 + 0.15 * Hashing.unit(Hashing.hash(h, 12))));
				int corner = (int) (Hashing.unit(Hashing.hash(h, 13)) * 4);
				boolean hu = (corner & 1) == 0 ? u >= nu : u < w - nu;
				boolean hv = (corner & 2) == 0 ? v >= nv : v < d - nv;
				return !(hu && hv);
			}
			case U: {
				int slotLo = w / 3, slotHi = w - w / 3;
				int depth = (int) (d * 0.5);
				boolean back = Hashing.unit(Hashing.hash(h, 14)) < 0.5;
				boolean inSlot = u >= slotLo && u < slotHi && (back ? v >= d - depth : v < depth);
				return !inSlot;
			}
			case COURTYARD: {
				int ring = Math.max(4, Math.min(w, d) / 3);
				return u < ring || v < ring || u >= w - ring || v >= d - ring;
			}
			case ROUND: {
				double a = (u + 0.5 - w / 2.0) / (w / 2.0), b = (v + 0.5 - d / 2.0) / (d / 2.0);
				return a * a + b * b <= 1.0;
			}
			case CHAMFERED: {
				int c = Math.max(2, Math.min(w, d) / 4);
				return u + v >= c && (w - 1 - u) + v >= c && u + (d - 1 - v) >= c && (w - 1 - u) + (d - 1 - v) >= c;
			}
			default:
				return true;
		}
	}
}
