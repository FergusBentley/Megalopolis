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

	public Mask opened() {
		Mask out = new Mask(minX, minZ, width, depth);
		for (int x = minX; x < minX + width; x++) {
			for (int z = minZ; z < minZ + depth; z++) {
				if (!get(x, z)) continue;
				boolean keep = false;
				for (int a = -1; a <= 0 && !keep; a++) {
					for (int b = -1; b <= 0 && !keep; b++) {
						keep = get(x + a, z + b) && get(x + a + 1, z + b) && get(x + a, z + b + 1) && get(x + a + 1, z + b + 1);
					}
				}
				if (keep) out.set(x, z);
			}
		}
		return out.size() >= 4 ? out : this;
	}

	public Obb box() {
		return Obb.ofRect(new Rect(minX, minZ, minX + width - 1, minZ + depth - 1));
	}
}
