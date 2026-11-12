package com.fergusb.plan;

/** Axis-aligned rectangle in block coordinates, inclusive */
public record Rect(int minX, int minZ, int maxX, int maxZ) {
	public int width() {
		return maxX - minX + 1;
	}

	public int depth() {
		return maxZ - minZ + 1;
	}

	public boolean contains(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	public boolean intersects(Rect o) {
		return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	public Rect shrink(int n) {
		Rect r = new Rect(minX + n, minZ + n, maxX - n, maxZ - n);
		return (r.minX > r.maxX || r.minZ > r.maxZ) ? null : r;
	}

	public boolean onEdge(int x, int z) {
		return x == minX || x == maxX || z == minZ || z == maxZ;
	}

	public boolean onCorner(int x, int z) {
		return (x == minX || x == maxX) && (z == minZ || z == maxZ);
	}

	public int centerX() {
		return (minX + maxX) / 2;
	}

	public int centerZ() {
		return (minZ + maxZ) / 2;
	}
}
