package com.fergusb.plan;

public final class Obb {
	private static final double EPS = 1e-6;

	public final double cx, cz, ux, uz, halfU, halfV;
	public final int minX, minZ, maxX, maxZ; // inclusive bounding box

	public Obb(double cx, double cz, double ux, double uz, double halfU, double halfV) {
		double len = Math.sqrt(ux * ux + uz * uz);
		this.cx = cx;
		this.cz = cz;
		this.ux = ux / len;
		this.uz = uz / len;
		this.halfU = halfU;
		this.halfV = halfV;
		double ex = Math.abs(this.ux) * halfU + Math.abs(this.uz) * halfV;
		double ez = Math.abs(this.uz) * halfU + Math.abs(this.ux) * halfV;
		this.minX = (int) Math.floor(cx - ex - EPS);
		this.maxX = (int) Math.ceil(cx + ex + EPS);
		this.minZ = (int) Math.floor(cz - ez - EPS);
		this.maxZ = (int) Math.ceil(cz + ez + EPS);
	}

	public static Obb ofRect(Rect r) {
		return new Obb((r.minX() + r.maxX()) / 2.0, (r.minZ() + r.maxZ()) / 2.0, 1, 0,
				(r.width() - 1) / 2.0, (r.depth() - 1) / 2.0);
	}

	/** Coordinate along u, relative to center */
	public double u(double x, double z) {
		return (x - cx) * ux + (z - cz) * uz;
	}

	/** Coordinate along v, relative to center */
	public double v(double x, double z) {
		return -(x - cx) * uz + (z - cz) * ux;
	}

	public boolean contains(int x, int z) {
		if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
		return Math.abs(u(x, z)) <= halfU + EPS && Math.abs(v(x, z)) <= halfV + EPS;
	}

	public boolean onEdge(int x, int z) {
		if (!contains(x, z)) return false;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && !contains(x + dx, z + dz)) return true;
			}
		}
		return false;
	}

	public boolean onCorner(int x, int z) {
		return onEdge(x, z) && Math.abs(u(x, z)) >= halfU - 1.0 && Math.abs(v(x, z)) >= halfV - 1.0;
	}

	/** Shrinks on all sides, returns null if nothing left */
	public Obb shrink(double s) {
		if (halfU - s < 0 || halfV - s < 0) return null;
		return new Obb(cx, cz, ux, uz, halfU - s, halfV - s);
	}

	/** Size in blocks along u and v */
	public int widthU() {
		return (int) Math.round(2 * halfU) + 1;
	}

	public int depthV() {
		return (int) Math.round(2 * halfV) + 1;
	}

	public double[][] corners() {
		double[][] c = new double[4][];
		int k = 0;
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				c[k++] = point(su * halfU, sv * halfV);
			}
		}
		return c;
	}

	/** World position of local (u, v) */
	public double[] point(double u, double v) {
		return new double[] {cx + u * ux - v * uz, cz + u * uz + v * ux};
	}

	public boolean bboxIntersects(Obb o) {
		return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	public boolean overlaps(Obb o) {
		if (!bboxIntersects(o)) return false;
		double[][] axes = {{ux, uz}, {-uz, ux}, {o.ux, o.uz}, {-o.uz, o.ux}};
		for (double[] a : axes) {
			double c1 = cx * a[0] + cz * a[1], c2 = o.cx * a[0] + o.cz * a[1];
			double r1 = radius(a) + 0.5, r2 = o.radius(a) + 0.5;
			if (Math.abs(c1 - c2) >= r1 + r2) return false;
		}
		return true;
	}

	private double radius(double[] a) {
		return halfU * Math.abs(ux * a[0] + uz * a[1]) + halfV * Math.abs(-uz * a[0] + ux * a[1]);
	}
}
