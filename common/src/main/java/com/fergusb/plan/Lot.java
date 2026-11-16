package com.fergusb.plan;

public record Lot(Obb bounds, Obb footprint, Use use, int baseY, int floors,
				  double downtown, double condition, long seed, BuildingSpec spec, Mask mask, int lift) {

	public Lot(Obb bounds, Obb footprint, Use use, int baseY, int floors,
			   double downtown, double condition, long seed, BuildingSpec spec, Mask mask) {
		this(bounds, footprint, use, baseY, floors, downtown, condition, seed, spec, mask, 0);
	}

	/** True if the column belongs to this lot */
	public boolean contains(int x, int z) {
		return mask != null ? mask.get(x, z) : bounds.contains(x, z);
	}

	public enum Use { BUILDING, PLAZA, PARK, GREEN, UNBUILDABLE }

	public int topY() {
		return baseY + floors * CityPlanner.FLOOR_HEIGHT;
	}
}

