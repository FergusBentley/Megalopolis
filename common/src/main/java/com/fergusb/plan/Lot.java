package com.fergusb.plan;

public record Lot(Obb bounds, Obb footprint, Use use, int baseY, int floors,
		double downtown, double condition, long seed) {

	public enum Use { BUILDING, PLAZA, PARK, UNBUILDABLE }

	public int topY() {
		return baseY + floors * CityPlanner.FLOOR_HEIGHT;
	}
}
