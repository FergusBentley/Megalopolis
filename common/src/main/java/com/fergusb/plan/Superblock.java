package com.fergusb.plan;

import java.util.ArrayList;
import java.util.List;

/** The area between four arterial roads */
public final class Superblock {
	public final int i, j;
	public final Rect bounds;
	public final boolean organic;
	public final List<Road> streets;
	public final List<Lot> lots;
	public final List<Junction> junctions;
	public final RoadMap map;
	private final SpatialIndex streetIndex;
	private final SpatialIndex junctionIndex;
	private final SpatialIndex lotIndex;
	private final java.util.BitSet wild;

	public Superblock(int i, int j, Rect bounds, boolean organic, List<Road> streets, List<Lot> lots, List<Junction> junctions,
			java.util.BitSet wild, RoadMap map) {
		this.wild = wild;
		this.i = i;
		this.j = j;
		this.bounds = bounds;
		this.organic = organic;
		this.streets = List.copyOf(streets);
		this.lots = List.copyOf(lots);
		List<int[]> sb = new ArrayList<>();
		for (Road r : streets) sb.add(new int[] {r.minX, r.minZ, r.maxX, r.maxZ});
		this.streetIndex = new SpatialIndex(sb);
		List<int[]> lb = new ArrayList<>();
		for (Lot l : lots) lb.add(new int[] {l.bounds().minX, l.bounds().minZ, l.bounds().maxX, l.bounds().maxZ});
		this.lotIndex = new SpatialIndex(lb);
		this.junctions = List.copyOf(junctions);
		List<int[]> jb = new ArrayList<>();
		for (Junction jn : junctions) {
			jb.add(new int[] {(int) Math.floor(jn.x() - jn.r()), (int) Math.floor(jn.z() - jn.r()),
					(int) Math.ceil(jn.x() + jn.r()), (int) Math.ceil(jn.z() + jn.r())});
		}
		this.junctionIndex = new SpatialIndex(jb);
		this.map = map;
	}

	/** True where the ground is left natural (around tunnels) */
	public boolean wildAt(int x, int z) {
		int lx = x - bounds.minX(), lz = z - bounds.minZ();
		return lx >= 0 && lz >= 0 && lx < bounds.width() && lz < bounds.depth() && wild.get(lx * bounds.depth() + lz);
	}

	public Junction junctionAt(int x, int z) {
		for (int k : junctionIndex.query(x, z)) {
			if (junctions.get(k).contains(x, z)) return junctions.get(k);
		}
		return null;
	}

	public Road streetAt(int x, int z) {
		if (!map.built(x, z)) return null;
		Road first = null, built = null;
		int builtK = Integer.MAX_VALUE;
		for (int k : streetIndex.query(x, z)) {
			Road r = streets.get(k);
			if (!r.contains(x, z)) continue;
			if (r.profile.state(r.arcAt(x, z)) != RoadProfile.State.NONE) {
				if (built == null || r.halfWidth > built.halfWidth || r.halfWidth == built.halfWidth && k < builtK) {
					built = r;
					builtK = k;
				}
				continue;
			}
			if (first == null) first = r;
		}
		return built != null ? built : first;
	}

    /** A built link street crossing the arterial band beside the superblock */
	public Road bandStreetAt(int x, int z) {
		for (int k : streetIndex.query(x, z)) {
			Road r = streets.get(k);
			if (r.external && r.contains(x, z) && r.profile.state(r.arcAt(x, z)) != RoadProfile.State.NONE) return r;
		}
		return null;
	}
	public Lot lotAt(int x, int z) {
		for (int k : lotIndex.query(x, z)) {
			if (lots.get(k).contains(x, z)) return lots.get(k);
		}
		return null;
	}
}
