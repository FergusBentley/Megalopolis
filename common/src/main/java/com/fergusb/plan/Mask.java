package com.fergusb.plan;

import java.util.BitSet;

/** A set of world columns inside an axis-aligned box */
public final class Mask {
	public final int minX, minZ, width, depth;
	private final BitSet bits;

	Mask(int minX, int minZ, int width, int depth) {
		this.minX = minX;
		this.minZ = minZ;
		this.width = width;
		this.depth = depth;
		this.bits = new BitSet(width * depth);
	}

	void set(int x, int z) {
		bits.set((x - minX) * depth + (z - minZ));
	}

	public boolean get(int x, int z) {
		int lx = x - minX, lz = z - minZ;
		return lx >= 0 && lz >= 0 && lx < width && lz < depth && bits.get(lx * depth + lz);
	}

	public boolean local(int lu, int lv) {
		return lu >= 0 && lv >= 0 && lu < width && lv < depth && bits.get(lu * depth + lv);
	}

	public int size() {
		return bits.cardinality();
	}

	public Obb box() {
		return Obb.ofRect(new Rect(minX, minZ, minX + width - 1, minZ + depth - 1));
	}
}
