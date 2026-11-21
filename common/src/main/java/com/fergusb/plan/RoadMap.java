package com.fergusb.plan;

/** Height map of a superblock's streets */
public final class RoadMap {
	private static final short EMPTY = Short.MIN_VALUE;
	private static final RoadProfile.State[] STATES = RoadProfile.State.values();

	private final int x0, z0, w, d;
	private final short[] h2; // height in half blocks
	private final byte[] state;

	private RoadMap(Rect bounds) {
		x0 = bounds.minX();
		z0 = bounds.minZ();
		w = bounds.width();
		d = bounds.depth();
		h2 = new short[w * d];
		state = new byte[w * d];
		java.util.Arrays.fill(h2, EMPTY);
	}

	/** True if the column has a built street */
	public boolean built(int x, int z) {
		int i = index(x, z);
		return i >= 0 && h2[i] != EMPTY && STATES[state[i]] != RoadProfile.State.NONE;
	}

	/** True if the column is inside the map and has a street on it */
	public boolean covers(int x, int z) {
		int i = index(x, z);
		return i >= 0 && h2[i] != EMPTY;
	}

	public double height(int x, int z) {
		int i = index(x, z);
		return i < 0 || h2[i] == EMPTY ? 0 : h2[i] / 2.0;
	}

	public RoadProfile.State state(int x, int z) {
		int i = index(x, z);
		return i < 0 || h2[i] == EMPTY ? RoadProfile.State.NONE : STATES[state[i]];
	}

	private int index(int x, int z) {
		int lx = x - x0, lz = z - z0;
		return lx < 0 || lz < 0 || lx >= w || lz >= d ? -1 : lx * d + lz;
	}

	static RoadMap build(Rect bounds, java.util.List<Road> streets, java.util.List<Junction> junctions) {
		RoadMap m = new RoadMap(bounds);
		int n = m.w * m.d;
		double[] h = new double[n];
		boolean[] pinned = new boolean[n];
		java.util.List<Road> jogs = new java.util.ArrayList<>();
		for (Road r : streets) if (r.jog && r.profile != null) jogs.add(r);
		for (Junction j : junctions) {
			for (int x = (int) Math.floor(j.x() - j.r()); x <= j.x() + j.r(); x++) {
				for (int z = (int) Math.floor(j.z() - j.r()); z <= j.z() + j.r(); z++) {
					int i = m.index(x, z);
					if (i < 0 || !j.contains(x, z) || m.h2[i] != EMPTY) continue;
					boolean onJog = false;
					for (Road r : jogs) {
						if (r.contains(x, z) && r.profile.state(r.arcAt(x, z)) != RoadProfile.State.NONE) onJog = true;
					}
					if (onJog) continue;
					m.h2[i] = (short) Math.round(j.h() * 2);
					m.state[i] = (byte) RoadProfile.State.GROUND.ordinal();
					h[i] = j.h();
					pinned[i] = true;
				}
			}
		}
		java.util.List<Road> order = new java.util.ArrayList<>(streets);
		order.sort((a, b) -> Double.compare(b.halfWidth, a.halfWidth));
		for (Road road : order) {
			for (int x = Math.max(m.x0, road.minX); x <= Math.min(bounds.maxX(), road.maxX); x++) {
				for (int z = Math.max(m.z0, road.minZ); z <= Math.min(bounds.maxZ(), road.maxZ); z++) {
					int i = m.index(x, z);
					if (m.h2[i] != EMPTY && STATES[m.state[i]] != RoadProfile.State.NONE || !road.contains(x, z)) continue;
					double s = road.arcAt(x, z);
					RoadProfile.State st = road.profile.state(s);
					if (m.h2[i] != EMPTY && st == RoadProfile.State.NONE) continue;
					h[i] = road.profile.height(s);
					m.h2[i] = (short) Math.round(h[i] * 2);
					m.state[i] = (byte) st.ordinal();
					boolean edge = x - m.x0 < 2 || z - m.z0 < 2 || bounds.maxX() - x < 2 || bounds.maxZ() - z < 2;
					pinned[i] = st != RoadProfile.State.GROUND && st != RoadProfile.State.ELEVATED || edge || road.jog;
				}
			}
		}
		// Smooth street columns with their neighbors
		double[] next = new double[n];
		for (int pass = 0; pass < 3; pass++) {
			for (int i = 0; i < n; i++) {
				next[i] = h[i];
				if (m.h2[i] == EMPTY || pinned[i]) continue;
				int x = i / m.d, z = i % m.d;
				double sum = 0;
				int cnt = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int a = x + dx, b = z + dz;
						if (a < 0 || b < 0 || a >= m.w || b >= m.d) continue;
						int j = a * m.d + b;
						if (m.h2[j] == EMPTY || STATES[m.state[j]] == RoadProfile.State.NONE) continue;
						sum += h[j];
						cnt++;
					}
				}
				if (cnt > 0) next[i] = sum / cnt;
			}
			System.arraycopy(next, 0, h, 0, n);
		}
		for (int i = 0; i < n; i++) {
			if (m.h2[i] != EMPTY) m.h2[i] = (short) Math.round(h[i] * 2);
		}
		return m;
	}
}
