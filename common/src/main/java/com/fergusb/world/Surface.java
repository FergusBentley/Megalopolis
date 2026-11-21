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
	private final boolean[][] parkSide = new boolean[18][18];
	private static final int CITY_REACH = 6;
	static final int PARK_SIDE = 6;
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
				int bBest = Integer.MAX_VALUE, bBase = Integer.MIN_VALUE; // nearest building within reach
				for (int ox = -BANK; ox <= BANK; ox++) {
					for (int oz = -BANK; oz <= BANK; oz++) {
						int d = ox * ox + oz * oz, i = dx + ox + B, j = dz + oz + B;
						CityPlanner.RoadPoint rp = road[i][j];
						if (rp != null && rp.state() == RoadProfile.State.GROUND && d <= BANK * BANK + 1 && d < best) {
							best = d;
							near = rp;
						}
						if (d <= BUILDING_REACH * BUILDING_REACH + 1 && building[i][j]) nearBuilding = true;
						if (building[i][j] && d <= BANK * BANK + 1 && d < bBest) {
							bBest = d;
							bBase = cells[i][j].lot().baseY();
						}
					}
				}
				boolean wet = wide[dx + B][dz + B];
				CityPlanner.Cell here = cells[dx + B][dz + B];
				if (here.kind() == CityPlanner.Kind.LOT && here.lot().footprint() != null) grade = here.lot().baseY();
				else if (bBase != Integer.MIN_VALUE && here.kind() == CityPlanner.Kind.LOT && !openGround(here)
						&& here.lot().use() != com.fergusb.plan.Lot.Use.PARK) grade = bBase;
				boolean isPark = here.kind() == CityPlanner.Kind.LOT && here.lot().use() == com.fergusb.plan.Lot.Use.PARK;
				boolean besidePark = false;
				com.fergusb.plan.Lot parkLot = isPark ? here.lot() : null;
				if (!isPark && openGround(here) && here.kind() != CityPlanner.Kind.STREET && here.kind() != CityPlanner.Kind.ARTERIAL) {
					int pBest = Integer.MAX_VALUE, pAny = Integer.MAX_VALUE;
					com.fergusb.plan.Lot anyLot = null;
					for (int ox = -PARK_SIDE; ox <= PARK_SIDE; ox++) {
						for (int oz = -PARK_SIDE; oz <= PARK_SIDE; oz++) {
							CityPlanner.Cell c = cells[dx + ox + B][dz + oz + B];
							if (c.kind() != CityPlanner.Kind.LOT || c.lot().use() != com.fergusb.plan.Lot.Use.PARK) continue;
							int d = ox * ox + oz * oz;
							if (d < pAny) {
								pAny = d;
								anyLot = c.lot();
							}
							if (d < pBest && !Double.isNaN(ParkPaths.of(planner, c.lot()).height(x, z))) {
								pBest = d;
								parkLot = c.lot();
							}
						}
					}
					if (parkLot == null) parkLot = anyLot;
					besidePark = parkLot != null;
				}
				parkSide[dx + 1][dz + 1] = besidePark;
				if (isPark || besidePark) {
					double level = ParkPaths.of(planner, parkLot).level();
					if (!Double.isNaN(level)) {
						grade = level;
					} else {
						double sum = 0;
						for (int ox = -6; ox <= 6; ox += 3) {
							for (int oz = -6; oz <= 6; oz += 3) sum += planner.grade().grade(x + ox, z + oz);
						}
						grade = sum / 25;
					}
				}
				double h = grade;
				if (near != null) {
					double dist = Math.sqrt(best);
					double allow = Math.max(0, Math.floor(dist) - LEVEL);
					h = near.height() + Math.max(-allow, Math.min(allow, grade - near.height()));
				}
				if (isPark || besidePark) {
					int lBest = Integer.MAX_VALUE, lBase = 0;
					for (int ox = -BANK; ox <= BANK; ox++) {
						for (int oz = -BANK; oz <= BANK; oz++) {
							int d = ox * ox + oz * oz;
							if (d >= lBest || d > BANK * BANK + 1) continue;
							CityPlanner.Cell c = cells[dx + ox + B][dz + oz + B];
							if (c.kind() != CityPlanner.Kind.LOT || c.lot().use() != com.fergusb.plan.Lot.Use.BUILDING) continue;
							lBest = d;
							lBase = c.lot().baseY();
						}
					}
					if (lBest != Integer.MAX_VALUE) {
						double allow = Math.max(0, Math.floor(Math.sqrt(lBest)) - 1);
						h = lBase + Math.max(-allow, Math.min(allow, h - lBase));
					}
				}
				if (parkLot != null) {
					double ph = ParkPaths.of(planner, parkLot).height(x, z);
					if (!Double.isNaN(ph)) h = ph;
				}
				boolean between = false;
				for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
					boolean a = false, b = false, g = false;
					for (int k = 1; k <= 2; k++) {
						CityPlanner.RoadPoint ra = road[dx + d[0] * k + B][dz + d[1] * k + B], rb = road[dx - d[0] * k + B][dz - d[1] * k + B];
						a |= ra != null;
						b |= rb != null;
						g |= ground(ra) || ground(rb);
					}
					between |= a && b && g;
				}
				between &= !building[dx + B][dz + B];
				boolean fill = wet && (nearBuilding || between || pocket(dx + B, dz + B));
				if (fill) h = Math.max(h, sea);
				filled[dx + 1][dz + 1] = fill;
				// Open ground away from the streets and buildings is left alone
				boolean nearCity = nearBuilding || between || (near != null && best <= CITY_REACH * CITY_REACH + 1);
				CityPlanner.Cell cell = cells[dx + B][dz + B];
				boolean park = cell.kind() == CityPlanner.Kind.LOT && cell.lot().use() == com.fergusb.plan.Lot.Use.PARK;
				boolean steep = false;
				int doorFloor = doorFront(dx, dz);
				if (doorFloor != Integer.MIN_VALUE && h > doorFloor) {
					h = doorFloor;
					nearCity = true;
				}
				natural[dx + 1][dz + 1] = !nearCity && !besidePark && (openGround(cell) || steep);
				height[dx + 1][dz + 1] = Math.round(h * 2) / 2.0;
				style[dx + 1][dz + 1] = near != null ? near.style() : null;
			}
		}
	}

	private static final int[][] DIRS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private int doorFront(int dx, int dz) {
		return doorAhead(dx, dz, 1);
	}

	static final int DOOR_CLEAR = 8;

	boolean facesDoor(int dx, int dz) {
		return doorAhead(dx, dz, DOOR_CLEAR) != Integer.MIN_VALUE;
	}

	private int doorAhead(int dx, int dz, int reach) {
		for (int[] d : DIRS4) {
			for (int k = 1; k <= reach; k++) {
				int i = dx - d[0] * k + B, j = dz - d[1] * k + B;
				if (i < 0 || j < 0 || i >= building.length || j >= building.length) break;
				if (road[i][j] != null) break;
				if (!building[i][j]) continue;
				BuildingPlacer.DoorCell dc = BuildingPlacer.doorPlan(planner, cells[i][j].lot())
						.get(com.fergusb.plan.Hashing.pack(x0 + i - B, z0 + j - B));
				if (dc != null && dc.door() && dc.outward().getStepX() == d[0] && dc.outward().getStepZ() == d[1]) return dc.floorWalk();
				break;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static final int POCKET = 3, POCKET_MAX = 6;

	private boolean pocket(int i, int j) {
		int open = 0;
		for (int a = i - POCKET; a <= i + POCKET; a++) {
			for (int b = j - POCKET; b <= j + POCKET; b++) {
				if (!wide[a][b]) continue;
				if (road[a][b] != null || fillsBase(a, b)) continue;
				if (++open > POCKET_MAX) return false;
			}
		}
		return true;
	}

	private boolean fillsBase(int i, int j) {
		if (building[i][j]) return false;
		for (int ox = -BUILDING_REACH; ox <= BUILDING_REACH; ox++) {
			for (int oz = -BUILDING_REACH; oz <= BUILDING_REACH; oz++) {
				if (ox * ox + oz * oz <= BUILDING_REACH * BUILDING_REACH + 1 && building[i + ox][j + oz]) return true;
			}
		}
		for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
			boolean a = false, b = false, g = false;
			for (int k = 1; k <= 2; k++) {
				CityPlanner.RoadPoint ra = road[i + d[0] * k][j + d[1] * k], rb = road[i - d[0] * k][j - d[1] * k];
				a |= ra != null;
				b |= rb != null;
				g |= ground(ra) || ground(rb);
			}
			if (a && b && g) return true;
		}
		return false;
	}

	private static boolean ground(CityPlanner.RoadPoint rp) {
		return rp != null && (rp.state() == RoadProfile.State.GROUND || rp.state() == RoadProfile.State.BRIDGE && rp.bridge() == null);
	}

	/** Roads the ground follows */
	private static boolean follows(CityPlanner.RoadPoint rp) {
		return rp.state() == RoadProfile.State.GROUND || rp.state() == RoadProfile.State.BRIDGE;
	}

	CityPlanner planner() {
		return planner;
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

	boolean parkSide(int dx, int dz) {
		return parkSide[dx + 1][dz + 1];
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
		if (!wide[dx + B][dz + B] || filled[dx + 1][dz + 1]) return false;
		CityPlanner.RoadPoint rp = road[dx + B][dz + B];
		return rp == null || rp.state() == RoadProfile.State.BRIDGE;
	}

	boolean shore(int dx, int dz) {
		return openWater(dx + 1, dz) || openWater(dx - 1, dz) || openWater(dx, dz + 1) || openWater(dx, dz - 1);
	}

	double heightAt(int x, int z) {
		return height(Math.max(-1, Math.min(16, x - x0)), Math.max(-1, Math.min(16, z - z0)));
	}
}
