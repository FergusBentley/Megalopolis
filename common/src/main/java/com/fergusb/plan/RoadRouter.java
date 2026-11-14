package com.fergusb.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

final class RoadRouter {
	private static final int CELL = 4;
	private static final double SLOPE_COST = 30;
	private static final double STEEP = 0.35;
	private static final int MAX_EXPANSIONS = 40_000;

	private final GradeField grade;
	private final Rect bounds;
	private final int nx, nz;
	private final double[] heights;
	private final byte[] water; // 0 unknown, 1 dry, 2 water

	RoadRouter(GradeField grade, Rect bounds) {
		this.grade = grade;
		this.bounds = bounds;
		this.nx = bounds.width() / CELL + 1;
		this.nz = bounds.depth() / CELL + 1;
		this.heights = new double[nx * nz];
		Arrays.fill(heights, Double.NaN);
		this.water = new byte[nx * nz];
	}

	double[][] route(double fx, double fz, double tx, double tz) {
		int start = cellOf(fx, fz), goal = cellOf(tx, tz);
		double dist = Math.hypot(tx - fx, tz - fz);
		int margin = (int) Math.max(12, dist * 0.5 / CELL);
		int wx0 = Math.max(0, Math.min(start % nx, goal % nx) - margin);
		int wx1 = Math.min(nx - 1, Math.max(start % nx, goal % nx) + margin);
		int wz0 = Math.max(0, Math.min(start / nx, goal / nx) - margin);
		int wz1 = Math.min(nz - 1, Math.max(start / nx, goal / nx) + margin);

		double[] g = new double[nx * nz];
		Arrays.fill(g, Double.MAX_VALUE);
		int[] from = new int[nx * nz];
		Arrays.fill(from, -1);
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		g[start] = 0;
		open.add(new double[] {heuristic(start, goal), start});
		int expansions = 0;
		boolean found = false;
		while (!open.isEmpty() && expansions++ < MAX_EXPANSIONS) {
			double[] top = open.poll();
			int c = (int) top[1];
			if (c == goal) {
				found = true;
				break;
			}
			if (top[0] - heuristic(c, goal) > g[c] + 1e-9) continue; // stale entry
			int cx = c % nx, cz = c / nx;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) continue;
					int x = cx + dx, z = cz + dz;
					if (x < wx0 || x > wx1 || z < wz0 || z > wz1) continue;
					int n = z * nx + x;
					double ng = g[c] + stepCost(c, n, (dx != 0 && dz != 0) ? Math.sqrt(2) * CELL : CELL);
					if (ng < g[n]) {
						g[n] = ng;
						from[n] = c;
						open.add(new double[] {ng + heuristic(n, goal), n});
					}
				}
			}
		}

		List<double[]> pts = new ArrayList<>();
		if (found) {
			for (int c = goal; c != -1; c = from[c]) pts.add(0, center(c));
			pts.set(0, new double[] {fx, fz});
			pts.set(pts.size() - 1, new double[] {tx, tz});
		} else {
			pts.add(new double[] {fx, fz});
			pts.add(new double[] {tx, tz});
		}
		pts = simplify(pts, 1.5);
		pts = chaikin(chaikin(pts));
		double[] xs = new double[pts.size()], zs = new double[pts.size()];
		for (int i = 0; i < pts.size(); i++) {
			xs[i] = pts.get(i)[0];
			zs[i] = pts.get(i)[1];
		}
		return new double[][] {xs, zs};
	}

	private double stepCost(int a, int b, double len) {
		double s = Math.abs(height(a) - height(b)) / len;
		double cost = len * (1 + SLOPE_COST * s * s);
		if (s > STEEP) cost += len * 50;
		if (isWater(b)) cost += len * 200;
		return cost;
	}

	private double heuristic(int a, int b) {
		return Math.hypot((a % nx) - (b % nx), (a / nx) - (b / nx)) * CELL;
	}

	private double height(int c) {
		if (Double.isNaN(heights[c])) {
			double[] p = center(c);
			heights[c] = grade.grade(p[0], p[1]);
		}
		return heights[c];
	}

	private boolean isWater(int c) {
		if (water[c] == 0) {
			double[] p = center(c);
			water[c] = (byte) (grade.water(p[0], p[1]) ? 2 : 1);
		}
		return water[c] == 2;
	}

	private int cellOf(double x, double z) {
		int cx = (int) Math.round((x - bounds.minX()) / CELL);
		int cz = (int) Math.round((z - bounds.minZ()) / CELL);
		cx = Math.max(0, Math.min(nx - 1, cx));
		cz = Math.max(0, Math.min(nz - 1, cz));
		return cz * nx + cx;
	}

	private double[] center(int c) {
		return new double[] {bounds.minX() + (c % nx) * CELL, bounds.minZ() + (c / nx) * CELL};
	}

	// smoothing

	static List<double[]> simplify(List<double[]> pts, double eps) {
		if (pts.size() < 3) return pts;
		int idx = -1;
		double max = 0;
		double[] a = pts.get(0), b = pts.get(pts.size() - 1);
		for (int i = 1; i < pts.size() - 1; i++) {
			double d = segDist(pts.get(i), a, b);
			if (d > max) {
				max = d;
				idx = i;
			}
		}
		if (max <= eps) {
			List<double[]> r = new ArrayList<>();
			r.add(a);
			r.add(b);
			return r;
		}
		List<double[]> left = simplify(pts.subList(0, idx + 1), eps);
		List<double[]> right = simplify(pts.subList(idx, pts.size()), eps);
		List<double[]> r = new ArrayList<>(left.subList(0, left.size() - 1));
		r.addAll(right);
		return r;
	}

	private static double segDist(double[] p, double[] a, double[] b) {
		double dx = b[0] - a[0], dz = b[1] - a[1];
		double len2 = dx * dx + dz * dz;
		double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / len2));
		return Math.hypot(a[0] + t * dx - p[0], a[1] + t * dz - p[1]);
	}

	static List<double[]> chaikin(List<double[]> pts) {
		if (pts.size() < 3) return pts;
		List<double[]> r = new ArrayList<>();
		r.add(pts.get(0));
		for (int i = 0; i + 1 < pts.size(); i++) {
			double[] a = pts.get(i), b = pts.get(i + 1);
			if (i > 0) r.add(new double[] {0.75 * a[0] + 0.25 * b[0], 0.75 * a[1] + 0.25 * b[1]});
			if (i + 2 < pts.size()) r.add(new double[] {0.25 * a[0] + 0.75 * b[0], 0.25 * a[1] + 0.75 * b[1]});
		}
		r.add(pts.get(pts.size() - 1));
		return r;
	}
}
