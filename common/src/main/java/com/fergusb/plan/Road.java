package com.fergusb.plan;

public final class Road {
	public enum Kind { ARTERIAL, STREET }

	private final double[] xs, zs;
	public final double halfWidth;
	public final Kind kind;
	public final int minX, minZ, maxX, maxZ;

	public Road(double[] xs, double[] zs, double halfWidth, Kind kind) {
		this.xs = xs;
		this.zs = zs;
		this.halfWidth = halfWidth;
		this.kind = kind;
		double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
		for (int i = 0; i < xs.length; i++) {
			x0 = Math.min(x0, xs[i]);
			x1 = Math.max(x1, xs[i]);
			z0 = Math.min(z0, zs[i]);
			z1 = Math.max(z1, zs[i]);
		}
		int pad = (int) Math.ceil(halfWidth) + 1;
		this.minX = (int) Math.floor(x0) - pad;
		this.maxX = (int) Math.ceil(x1) + pad;
		this.minZ = (int) Math.floor(z0) - pad;
		this.maxZ = (int) Math.ceil(z1) + pad;
	}

	public static Road ofRect(Rect r) {
		boolean alongZ = r.width() <= r.depth();
		if (alongZ) {
			double x = (r.minX() + r.maxX()) / 2.0;
			return new Road(new double[] {x, x}, new double[] {r.minZ(), r.maxZ()}, (r.width() - 1) / 2.0, Kind.STREET);
		}
		double z = (r.minZ() + r.maxZ()) / 2.0;
		return new Road(new double[] {r.minX(), r.maxX()}, new double[] {z, z}, (r.depth() - 1) / 2.0, Kind.STREET);
	}

	public boolean contains(int x, int z) {
		return containsWithin(x, z, halfWidth + 0.25);
	}

	public boolean containsWithin(double x, double z, double dist) {
		if (x < minX - dist || x > maxX + dist || z < minZ - dist || z > maxZ + dist) return false;
		return distanceSq(x, z) <= dist * dist;
	}

	public double distanceSq(double x, double z) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 1 < xs.length; i++) {
			double ax = xs[i], az = zs[i], bx = xs[i + 1], bz = zs[i + 1];
			double dx = bx - ax, dz = bz - az;
			double len2 = dx * dx + dz * dz;
			double t = len2 == 0 ? 0 : ((x - ax) * dx + (z - az) * dz) / len2;
			t = Math.max(0, Math.min(1, t));
			double px = ax + t * dx - x, pz = az + t * dz - z;
			best = Math.min(best, px * px + pz * pz);
		}
		return best;
	}

	public double[] nearest(double x, double z) {
		double[] best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i + 1 < xs.length; i++) {
			double ax = xs[i], az = zs[i], bx = xs[i + 1], bz = zs[i + 1];
			double dx = bx - ax, dz = bz - az;
			double len2 = dx * dx + dz * dz;
			if (len2 == 0) continue;
			double t = Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len2));
			double px = ax + t * dx, pz = az + t * dz;
			double d = Math.hypot(px - x, pz - z);
			if (d < bestD) {
				double len = Math.sqrt(len2);
				bestD = d;
				best = new double[] {px, pz, dx / len, dz / len, d};
			}
		}
		return best;
	}

	public int points() {
		return xs.length;
	}

	public double x(int i) {
		return xs[i];
	}

	public double z(int i) {
		return zs[i];
	}
}
