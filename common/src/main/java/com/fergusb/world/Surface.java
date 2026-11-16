package com.fergusb.world;

import com.fergusb.plan.BuildingShape;
import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.RoadProfile;
import com.fergusb.plan.RoadStyle;

/** The finished ground for a chunk */
final class Surface {
	static final int B = 9; // cells and water are known this far outside the chunk
	private static final int BANK = 8; // ground within this distance of a road banks toward it
	private static final int LEVEL = 2; // ground this close to a road is level with it
	private static final int BUILDING_REACH = 3;

	private final CityPlanner planner;
	private final CityPlanner.Cell[][] cells; // [dx + B][dz + B]
	private final CityPlanner.RoadPoint[][] road; // built road points [dx + B][dz + B]
	private final boolean[][] building; // building footprint [dx + B][dz + B]
	private final boolean[][] wide; // sea-level water [dx + B][dz + B]
	private final boolean[][] filled = new boolean[18][18];
	private final boolean[][] natural = new boolean[18][18];
	private static final int CITY_REACH = 6;
	private final double[][] height = new double[18][18];
	private final RoadStyle[][] style = new RoadStyle[18][18];
	final int x0, z0;

	Surface(CityPlanner planner, boolean[][] wide, int x0, int z0) {
		this.planner = planner;
		this.wide = wide;
		this.x0 = x0;
		this.z0 = z0;
		int n = 16 + 2 * B;
		cells = new CityPlanner.Cell[n][n];
		road = new CityPlanner.RoadPoint[n][n];
		building = new boolean[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = x0 + i - B, z = z0 + j - B;
				CityPlanner.Cell c = planner.classify(x, z);
				cells[i][j] = c;
				if (c.isRoad()) {
					CityPlanner.RoadPoint rp = planner.roadPoint(c, x, z);
					if (rp.state() != RoadProfile.State.NONE) road[i][j] = rp;
				} else if (c.kind() == CityPlanner.Kind.LOT && c.lot().footprint() != null) {
					building[i][j] = BuildingShape.insideAt(c.lot(), x, z, 0);
				}
			}
		}
		int sea = planner.terrain().seaLevel();
		for (int dx = -1; dx <= 16; dx++) {
			for (int dz = -1; dz <= 16; dz++) {
				int x = x0 + dx, z = z0 + dz;
				CityPlanner.RoadPoint own = road[dx + B][dz + B];
				if (own != null && follows(own)) {
					height[dx + 1][dz + 1] = own.height();
					style[dx + 1][dz + 1] = own.style();
					continue;
				}
				double grade = planner.grade().grade(x, z);
				if (own != null || cells[dx + B][dz + B].kind() == CityPlanner.Kind.WILD) {
					height[dx + 1][dz + 1] = Math.round(grade * 2) / 2.0;
					style[dx + 1][dz + 1] = own != null ? own.style() : null;
					continue;
				}
				CityPlanner.RoadPoint near = null;
				int best = Integer.MAX_VALUE;
				boolean nearBuilding = false;
				for (int ox = -BANK; ox <= BANK; ox++) {
					for (int oz = -BANK; oz <= BANK; oz++) {
						int d = ox * ox + oz * oz, i = dx + ox + B, j = dz + oz + B;
						CityPlanner.RoadPoint rp = road[i][j];
						if (rp != null && follows(rp) && d <= BANK * BANK + 1 && d < best) {
							best = d;
							near = rp;
						}
						if (d <= BUILDING_REACH * BUILDING_REACH + 1 && building[i][j]) nearBuilding = true;
					}
				}
				boolean wet = wide[dx + B][dz + B];
				double h = grade;
				if (near != null) {
					// Level beside the road, then a bank of at most one block per block
					double dist = Math.sqrt(best);
					double allow = Math.max(0, Math.floor(dist) - LEVEL);
					h = near.height() + Math.max(-allow, Math.min(allow, grade - near.height()));
				}
				boolean fill = wet && nearBuilding;
				if (fill) h = Math.max(h, sea);
				filled[dx + 1][dz + 1] = fill;
				// Open ground away from the streets and buildings is left alone
				boolean nearCity = nearBuilding || (near != null && best <= CITY_REACH * CITY_REACH + 1);
				natural[dx + 1][dz + 1] = !nearCity && openGround(cells[dx + B][dz + B]);
				height[dx + 1][dz + 1] = Math.round(h * 2) / 2.0;
				style[dx + 1][dz + 1] = near != null ? near.style() : null;
			}
		}
	}

	/** Roads the ground follows */
	private static boolean follows(CityPlanner.RoadPoint rp) {
		return rp.state() == RoadProfile.State.GROUND || rp.state() == RoadProfile.State.BRIDGE;
	}

	CityPlanner.Cell cell(int dx, int dz) {
		return cells[dx + B][dz + B];
	}

	CityPlanner.RoadPoint road(int dx, int dz) {
		return road[dx + B][dz + B];
	}

	boolean builtRoad(int dx, int dz) {
		return road[dx + B][dz + B] != null;
	}

	/** A built road at ground level */
	boolean openRoad(int dx, int dz) {
		CityPlanner.RoadPoint rp = road[dx + B][dz + B];
		return rp != null && follows(rp);
	}

	boolean elevated(int dx, int dz) {
		CityPlanner.RoadPoint rp = road[dx + B][dz + B];
		return rp != null && rp.state() == RoadProfile.State.ELEVATED;
	}

	boolean tunnel(int dx, int dz) {
		CityPlanner.RoadPoint rp = road[dx + B][dz + B];
		return rp != null && rp.state() == RoadProfile.State.TUNNEL;
	}

	/** Natural ground left untouched */
	boolean wild(int dx, int dz) {
		return cells[dx + B][dz + B].kind() == CityPlanner.Kind.WILD;
	}

	boolean natural(int dx, int dz) {
		return wild(dx, dz) || natural[dx + 1][dz + 1];
	}

	/** Ground with nothing planned */
	private static boolean openGround(CityPlanner.Cell c) {
		return switch (c.kind()) {
			case GAP, ARTERIAL, STREET -> true; // roads here are the unbuilt ones
			case LOT -> c.lot().use() == com.fergusb.plan.Lot.Use.GREEN || c.lot().use() == com.fergusb.plan.Lot.Use.UNBUILDABLE;
			default -> false;
		};
	}

	boolean building(int dx, int dz) {
		return building[dx + B][dz + B];
	}

	double height(int dx, int dz) {
		return height[dx + 1][dz + 1];
	}

	/** First air block above open ground */
	int groundY(int dx, int dz) {
		return (int) Math.ceil(height[dx + 1][dz + 1]);
	}

	RoadStyle style(int dx, int dz) {
		return style[dx + 1][dz + 1];
	}

	/** Sea-level water that is filled in */
	boolean filled(int dx, int dz) {
		return filled[dx + 1][dz + 1];
	}

	boolean openWater(int dx, int dz) {
		return wide[dx + B][dz + B] && !filled[dx + 1][dz + 1] && !builtRoad(dx, dz);
	}

	boolean shore(int dx, int dz) {
		return openWater(dx + 1, dz) || openWater(dx - 1, dz) || openWater(dx, dz + 1) || openWater(dx, dz - 1);
	}

	double heightAt(int x, int z) {
		return height(x - x0, z - z0);
	}
}
