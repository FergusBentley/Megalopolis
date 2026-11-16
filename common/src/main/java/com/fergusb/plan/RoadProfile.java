package com.fergusb.plan;

import java.util.List;
import java.util.function.DoubleFunction;

/** Planned heights along a road */
public final class RoadProfile {
	public enum State { GROUND, BRIDGE, TUNNEL, ELEVATED, NONE }
	public enum Railing { BLOCKS, CRENEL, FENCE, WALL }

	public record Bridge(double deck, Railing railing, int pillar) {}
	public record Flat(double s0, double s1, double h) {}

	public static final double STREET_SLOPE = 0.34; // steepest allowed
	public static final int MAX_SPAN = 64, MAX_SPAN_ARTERIAL = 128; // longest bridge allowed
	private static final int MIN_ISLAND_PIECE = 48; // minimum size island to be built on
	private static final double MAX_CUT = 4;
	private static final double TUNNEL_CUT = 12;
	private static final double TUNNEL_DEPTH = 14;
	private static final int TUNNEL_MIN = 16;
	private static final double STREET_CUT = 2;
	private static final double ELEVATED_HEIGHT = 7;
	private static final int ELEVATED_MIN = 6;
	private static final int MIN_LANDING = 8; // minimum size island for bridge to land at

	private final double[] h;
	private final State[] state;
	private final Bridge[] bridge;
	public final RoadStyle style;
	public final Railing railing;
	public final String biome;

	private RoadProfile(double[] h, State[] state, Bridge[] bridge, RoadStyle style, Railing railing, String biome) {
		this.h = h;
		this.state = state;
		this.bridge = bridge;
		this.style = style;
		this.railing = railing;
		this.biome = biome;
	}

	public double height(double s) {
		return h[index(s)];
	}

	public State state(double s) {
		return state[index(s)];
	}

	/** The bridge at s */
	public Bridge bridge(double s) {
		return bridge[index(s)];
	}

	public double length() {
		return h.length - 1;
	}

	/** Marks samples a to b as not built */
	void clear(int a, int b) {
		for (int k = Math.max(0, a); k <= Math.min(h.length - 1, b); k++) {
			state[k] = State.NONE;
			bridge[k] = null;
		}
	}

	State stateAt(int k) {
		return state[k];
	}

	int samples() {
		return h.length;
	}

	private int index(double s) {
		return Math.max(0, Math.min(h.length - 1, (int) Math.round(s)));
	}

	static RoadProfile build(CityPlanner planner, DoubleFunction<double[]> pointAt, double length,
			List<Flat> flats, double maxSlope, RoadStyle style, boolean arterial, boolean straight, long seed) {
		GradeField grade = planner.grade();
		TerrainSampler terrain = planner.terrain();
		int sea = terrain.seaLevel();
		int n = Math.max(1, (int) Math.ceil(length)) + 1;
		double[] raw = new double[n];
		double[] ground = new double[n];
		boolean[] water = new boolean[n];
		int[] floor = new int[n];
		final int step = 4;
		for (int i = 0; i < n; i++) {
			double[] p = pointAt.apply(Math.min(i, length));
			raw[i] = grade.grade(p[0], p[1]);
			ground[i] = raw[i];
			if (i % step == 0 || i == n - 1) {
				int x = (int) Math.round(p[0] / 4) * 4, z = (int) Math.round(p[1] / 4) * 4;
				TerrainSampler.Sample s = terrain.sample(x, z);
				water[i] = s.water() || grade.smallIslandAt(x, z);
				floor[i] = s.groundY();
			}
		}
		for (int i = 0; i < n; i++) {
			if (i % step == 0 || i == n - 1) continue;
			int a = (i / step) * step, b = Math.min(n - 1, a + step);
			water[i] = water[a] && water[b];
			floor[i] = Math.min(floor[a], floor[b]);
		}

		for (int a = 0; a < n; ) {
			if (water[a]) {
				a++;
				continue;
			}
			int b = a;
			while (b + 1 < n && !water[b + 1]) b++;
			if (a > 0 && b < n - 1 && b - a + 1 < MIN_LANDING) for (int k = a; k <= b; k++) water[k] = true;
			a = b + 1;
		}
		// A road end that joins another road counts as land
		Double startJoin = null, endJoin = null;
		for (Flat f : flats) {
			if (f.s0() <= 0.5) startJoin = f.h();
			if (f.s1() >= n - 1.5) endJoin = f.h();
		}

		// Near water, roads sit at least 2 blocks above sea level
		for (int i = 0; i < n; i++) {
			if (water[i]) {
				raw[i] = Math.max(raw[i], sea + 2);
				continue;
			}
			for (int k = Math.max(0, i - 8); k <= Math.min(n - 1, i + 8); k++) {
				if (water[k]) {
					raw[i] = Math.max(raw[i], sea + 2);
					break;
				}
			}
		}

		// Decide water crossing
		State[] state = new State[n];
		Bridge[] bridges = new Bridge[n];
		boolean[] pinned = new boolean[n];
		for (int i = 0; i < n; i++) state[i] = State.GROUND;
		int maxSpan = arterial ? MAX_SPAN_ARTERIAL : MAX_SPAN;
		Rng rr = new Rng(Hashing.hash(seed, 77));
		Railing railing;
		if (arterial) railing = rr.chance(0.65) ? Railing.WALL : Railing.CRENEL;
		else {
			double r = rr.nextDouble();
			railing = r < 0.45 ? Railing.FENCE : r < 0.75 ? Railing.WALL : Railing.CRENEL;
		}
		double[] mid = pointAt.apply(length / 2);
		String biome = terrain.biome((int) Math.floor(mid[0]), (int) Math.floor(mid[1]));
		int i = 0;
		while (i < n) {
			if (!water[i]) {
				i++;
				continue;
			}
			int a = i;
			while (i < n && water[i]) i++;
			int b = i - 1;
			boolean landBefore = a > 0 || startJoin != null, landAfter = b < n - 1 || endJoin != null;
			if (landBefore && landAfter && b - a + 1 <= maxSpan) {
				double before = a > 0 ? raw[a - 1] : startJoin, after = b < n - 1 ? raw[b + 1] : endJoin;
				double deck = Math.ceil(Math.max(sea + 2, Math.max(before, after)));
				Bridge bridge = designBridge(pointAt, a, b, deck, floor, sea, arterial, straight, railing);
				for (int k = a; k <= b; k++) {
					state[k] = State.BRIDGE;
					bridges[k] = bridge;
					raw[k] = deck;
					pinned[k] = true;
				}
			} else {
				for (int k = a; k <= b; k++) state[k] = State.NONE;
			}
		}
		for (boolean changed = true; changed; ) {
			changed = false;
			i = 0;
			while (i < n) {
				if (state[i] == State.NONE) {
					i++;
					continue;
				}
				int a = i;
				while (i < n && state[i] != State.NONE) i++;
				int b = i - 1;
				boolean cutOff = a > 0 && b < n - 1;
				if (cutOff && b - a + 1 < MIN_ISLAND_PIECE) {
					for (int k = a; k <= b; k++) {
						state[k] = State.NONE;
						bridges[k] = null;
					}
					changed = true;
				}
			}
			i = 0;
			while (i < n) {
				if (state[i] != State.BRIDGE) {
					i++;
					continue;
				}
				int a = i;
				while (i < n && state[i] == State.BRIDGE) i++;
				int b = i - 1;
				boolean deadEnd = (a > 0 && state[a - 1] == State.NONE) || (b < n - 1 && state[b + 1] == State.NONE);
				if (deadEnd) {
					for (int k = a; k <= b; k++) {
						state[k] = State.NONE;
						bridges[k] = null;
					}
					changed = true;
				}
			}
		}

		double[] env = raw.clone();
		if (!arterial) {
			for (int k = 0; k < n; k++) {
				for (int j = Math.max(0, k - 2); j <= Math.min(n - 1, k + 2); j++) env[k] = Math.max(env[k], raw[j]);
			}
		}
		int window = arterial ? 3 : 6;
		double[] smooth = new double[n];
		for (int k = 0; k < n; k++) {
			if (pinned[k]) {
				smooth[k] = raw[k];
				continue;
			}
			double sum = 0;
			int cnt = 0;
			for (int j = Math.max(0, k - window); j <= Math.min(n - 1, k + window); j++) {
				sum += env[j];
				cnt++;
			}
			smooth[k] = sum / cnt;
		}
		double[] hgt = smooth.clone();
		for (Flat f : flats) {
			int k0 = Math.max(0, (int) Math.round(f.s0())), k1 = Math.min(n - 1, (int) Math.round(f.s1()));
			for (int k = k0; k <= k1; k++) {
				if (state[k] == State.BRIDGE) continue; // decks stay level
				hgt[k] = f.h();
				pinned[k] = true;
			}
		}
		clampSlope(hgt, pinned, maxSlope);
		if (arterial) {
			for (int k = 0; k < n; k++) {
				if (!pinned[k]) hgt[k] = Math.max(smooth[k] - MAX_CUT, Math.min(smooth[k] + MAX_CUT, hgt[k]));
			}
			clampSlope(hgt, pinned, STREET_SLOPE);
		}
		if (!arterial) {
			for (int k = 0; k < n; k++) if (!pinned[k]) hgt[k] = Math.max(hgt[k], ground[k] - STREET_CUT);
			clampSlope(hgt, pinned, maxSlope);
		}
		for (int k = 0; k < n; k++) hgt[k] = Math.round(hgt[k] * 2) / 2.0;
		clampSlope(hgt, pinned, 0.5);
		for (int k = 0; k < n; k++) hgt[k] = Math.round(hgt[k] * 2) / 2.0;
		flattenBlips(hgt, pinned, arterial ? 16 : 10);
		boolean[] deck = longRuns(n, k -> state[k] == State.GROUND && hgt[k] - ground[k] >= ELEVATED_HEIGHT, ELEVATED_MIN);
		for (int k = 0; k < n; k++) if (deck[k]) state[k] = State.ELEVATED;
		return new RoadProfile(hgt, state, bridges, style, railing, biome);
	}

	private static void flattenBlips(double[] h, boolean[] pinned, int maxLen) {
		for (int pass = 0; pass < 8; pass++) {
			boolean changed = false;
			int a = 0;
			while (a < h.length) {
				int b = a;
				while (b + 1 < h.length && h[b + 1] == h[a]) b++;
				if (a > 0 && b < h.length - 1 && b - a + 1 < maxLen) {
					double before = h[a - 1], after = h[b + 1];
					boolean bump = before < h[a] && after < h[a];
					boolean dip = before > h[a] && after > h[a];
					boolean free = true;
					for (int k = a; k <= b; k++) if (pinned[k]) free = false;
					if ((bump || dip) && free) {
						double level = bump ? Math.max(before, after) : Math.min(before, after);
						for (int k = a; k <= b; k++) h[k] = level;
						changed = true;
					}
				}
				a = b + 1;
			}
			if (!changed) return;
		}
	}

	private static Bridge designBridge(DoubleFunction<double[]> pointAt, int a, int b, double deck,
			int[] floor, int sea, boolean arterial, boolean straight, Railing roadRailing) {
		int span = b - a + 1;
		int lowest = sea;
		for (int k = a; k <= b; k++) lowest = Math.min(lowest, floor[k]);
		int depth = sea - lowest;
		boolean axis = straight || axisAligned(pointAt, a - 1, b + 1);
		boolean half = deck != Math.floor(deck);
		Railing railing = half ? Railing.BLOCKS : axis ? roadRailing : Railing.CRENEL;
		int pillar = 1 + (arterial ? 1 : 0) + (span > 40 || depth > 10 ? 1 : 0);
		return new Bridge(deck, railing, Math.min(3, pillar));
	}

	/** True if the center line between samples a and b runs along the X or Z axis */
	private static boolean axisAligned(DoubleFunction<double[]> pointAt, int a, int b) {
		double[] p0 = pointAt.apply(Math.max(0, a));
		boolean alongX = true, alongZ = true;
		for (int k = a; k <= b; k += 2) {
			double[] p = pointAt.apply(Math.max(0, k));
			if (Math.abs(p[1] - p0[1]) > 0.6) alongX = false;
			if (Math.abs(p[0] - p0[0]) > 0.6) alongZ = false;
		}
		return alongX || alongZ;
	}

	private static boolean[] longRuns(int n, java.util.function.IntPredicate test, int min) {
		boolean[] out = new boolean[n];
		int k = 0;
		while (k < n) {
			if (!test.test(k)) {
				k++;
				continue;
			}
			int a = k;
			while (k < n && test.test(k)) k++;
			if (k - a >= min) for (int j = a; j < k; j++) out[j] = true;
		}
		return out;
	}

	private static void clampSlope(double[] h, boolean[] pinned, double m) {
		int n = h.length;
		double[] lim = new double[n]; // limit for the step from k - 1 to k
		java.util.Arrays.fill(lim, m);
		int prev = -1;
		for (int k = 0; k < n; k++) {
			if (!pinned[k]) continue;
			if (prev >= 0 && k - prev > 1) {
				double need = Math.abs(h[k] - h[prev]) / (k - prev);
				if (need > m) for (int j = prev + 1; j <= k; j++) lim[j] = need;
			}
			prev = k;
		}
		for (int k = 1; k < n; k++) {
			if (!pinned[k]) h[k] = Math.max(h[k - 1] - lim[k], Math.min(h[k - 1] + lim[k], h[k]));
		}
		for (int k = n - 2; k >= 0; k--) {
			if (!pinned[k]) h[k] = Math.max(h[k + 1] - lim[k + 1], Math.min(h[k + 1] + lim[k + 1], h[k]));
		}
	}

	static double junctionHeight(CityPlanner planner, double x, double z) {
		GradeField grade = planner.grade();
		double hgt = grade.grade(x, z);
		if (grade.water(x, z)) hgt = Math.max(hgt, planner.terrain().seaLevel() + 2);
		return Math.round(hgt * 2) / 2.0;
	}

	/** Flats holding both ends of a road at its junction heights */
	static List<Flat> endFlats(double length, Double start, Double end, double reach) {
		double r = Math.min(reach, length / 3);
		List<Flat> out = new java.util.ArrayList<>();
		if (start != null) out.add(new Flat(0, r, start));
		if (end != null) out.add(new Flat(length - r, length, end));
		return out;
	}
}
