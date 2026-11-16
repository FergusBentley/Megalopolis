package com.fergusb.plan;

public record Junction(double x, double z, double r, double h, Road road) {
	public boolean contains(int px, int pz) {
		double dx = px - x, dz = pz - z;
		return dx * dx + dz * dz <= r * r;
	}
}
