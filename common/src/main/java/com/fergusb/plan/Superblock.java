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
    private final SpatialIndex streetIndex;
    private final SpatialIndex lotIndex;

    public Superblock(int i, int j, Rect bounds, boolean organic, List<Road> streets, List<Lot> lots) {
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
    }

    public Road streetAt(int x, int z) {
        for (int k : streetIndex.query(x, z)) {
            if (streets.get(k).contains(x, z)) return streets.get(k);
        }
        return null;
    }

    public Lot lotAt(int x, int z) {
        for (int k : lotIndex.query(x, z)) {
            if (lots.get(k).bounds().contains(x, z)) return lots.get(k);
        }
        return null;
    }
}
