package com.fergusb.world;

import com.fergusb.plan.BuildingShape;
import com.fergusb.plan.BuildingSpec;
import com.fergusb.plan.BuildingSpec.Purpose;
import com.fergusb.plan.BuildingSpec.Roof;
import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Hashing;
import com.fergusb.plan.Lot;
import com.fergusb.plan.Obb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

final class BuildingPlacer {
	private static final int FH = CityPlanner.FLOOR_HEIGHT;
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();
	private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();
	private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
	private static final BlockState HEDGE = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState VENT = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState VENT_TOP = Blocks.IRON_TRAPDOOR.defaultBlockState();
	private static final BlockState ANTENNA = Blocks.END_ROD.defaultBlockState();
	private static final BlockState[] HOUSE_ROOFS = {
			Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CONCRETE.gray().defaultBlockState(),
			Blocks.DYED_TERRACOTTA.brown().defaultBlockState()};
	private static final BuildingSpec.PaletteFamily[] FAMILIES = BuildingSpec.PaletteFamily.values();

	private BuildingPlacer() {}

	/** Places the column at (x, z) */
	static void place(WorldGenLevel level, BlockPos.MutableBlockPos pos, CityPlanner planner, Surface surf, Lot lot, int x, int z) {
		Obb fp = lot.footprint();
		BuildingSpec s = lot.spec();
		int[] cell = BuildingShape.local(fp, x, z);
		int lu = cell[0], lv = cell[1];
		int w = fp.widthU(), d = fp.depthV();
		int base = lot.baseY();
		boolean townhouses = s.purpose() == Purpose.TOWNHOUSES;
		boolean homey = townhouses || s.purpose() == Purpose.HOUSE;

		// Townhouses get their own palette
		int unit = townhouses ? BuildingShape.unit(lot, lu) : 0;
		int floors = lot.floors() + (townhouses ? extraFloors(s, unit) : 0);
		boolean partition = townhouses && lu > 0 && BuildingShape.unit(lot, lu - 1) != unit;
		int bottom = base;
		int top = base + floors * FH;
		boolean roofStep = false;
		if (townhouses) {
			base = unitBase(planner, lot, s, unit, w);
			bottom = base;
			top = base + floors * FH;
			if (partition) {
				int prevBase = unitBase(planner, lot, s, unit - 1, w);
				int prevTop = prevBase + (lot.floors() + extraFloors(s, unit - 1)) * FH;
				bottom = Math.min(base, prevBase);
				roofStep = prevTop != top;
				top = Math.max(top, prevTop);
				floors = (top - base + FH - 1) / FH;
			}
		}
		BuildingSpec.PaletteFamily family = townhouses
				? FAMILIES[(int) (Hashing.unit(Hashing.hash(s.seed(), 31, unit)) * FAMILIES.length)]
				: s.palette();
		Palettes.Palette pal = Palettes.mixed(family, s.biome(), Hashing.hash(s.seed(), 33, unit));
		BlockState wood = Palettes.wood(s.biome());
		if (s.woodAccent()) pal = new Palettes.Palette(pal.wall(), wood, wood, pal.glass(), pal.roof(), pal.weatheredWall(), pal.weatheredGlass(), pal.storefront());
		boolean woodTrim = s.woodAccent() || homey && Hashing.unit(Hashing.hash(s.seed(), 34)) < 0.5;
		BlockState trim = woodTrim ? wood : pal.trim();
		BlockState roofBlock = homey ? HOUSE_ROOFS[(int) (Hashing.unit(Hashing.hash(s.seed(), 32, unit)) * HOUSE_ROOFS.length)] : pal.roof();
		double cond = lot.condition();
		double decay = Math.max(0, 0.4 - cond) / 0.4;
		double weathering = Math.max(0, 0.3 - cond) / 0.3;

		Outside out = lookOutside(planner, surf, lot, x, z);

		// On slopes the outer walls go down to the ground
		BlockState below = out.groundWall() ? weathered(pal.wall(), pal.weatheredWall(), 0.5, weathering) : FOUNDATION;
		set(level, pos.set(x, base - 1, z), out.groundWall() ? below : FLOOR);
		int fillFrom = base - 2;
		if (out.groundWall()) {
			int lowest = base - 1;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!BuildingShape.insideAt(lot, x + dx, z + dz, 0)) {
						lowest = Math.min(lowest, (int) Math.floor(surf.heightAt(x + dx, z + dz)) - 1);
					}
				}
			}
			for (int y = base - 2; y >= lowest; y--) set(level, pos.set(x, y, z), below);
			fillFrom = lowest - 1;
		}
		fillDown(level, pos, x, fillFrom, z, below);

		int lightU = Math.floorMod(lu, 6), lightV = Math.floorMod(lv, 6);
		java.util.Map<Long, Outside> besideOut = null;
		java.util.Map<Long, DoorCell> doors = doorPlan(planner, lot);
		for (int y = bottom; y < base; y++) set(level, pos.set(x, y, z), weathered(pal.wall(), pal.weatheredWall(), 0.5, weathering));
		for (int y = base; y < top; y++) {
			int local = y - base, f = local / FH, ly = local % FH;
			long h = Hashing.hash(s.seed(), x, y, z);
			double r = Hashing.unit(h);
			BlockState state;
			if (!BuildingShape.insideAt(lot, x, z, f)) {
				state = (ly == 0 && f > 0 && BuildingShape.insideAt(lot, x, z, f - 1)) ? roofBlock : AIR;
			} else {
				Face face = face(lot, fp, x, z, lu, lv, f);
				if (face != null) {
					boolean straight = !face.corner() && straightWall(lot, x, z, f, face.alongX());
					int floorWalk = y - ly + 1;
					DoorCell door = doors.get(Hashing.pack(x, z));
					boolean doorHere = door != null && door.floorWalk() == floorWalk && (ly == 1 || ly == 2);
					boolean front = true;
					if (!partition && shopfront(s, face, out, y, ly)) {
						int[] back = indexStep(fp, face, x, z, lu, lv);
						if (besideOut == null) besideOut = new java.util.HashMap<>(4);
						front = false;
						for (int k : new int[] {1, -1}) {
							int nx = x + back[0] * k, nz = z + back[1] * k;
							DoorCell nd = doors.get(Hashing.pack(nx, nz));
							BlockState nb = besideWall(planner, surf, lot, fp, s, pal, trim, nx, nz, f, y, ly, unit, w, besideOut);
							if (nd != null && nd.floorWalk() == floorWalk || nb != null && nb.getBlock() == pal.storefront().getBlock()) front = true;
						}
					}
					if (doorHere) {
						state = doorBlock(s, ly, door);
					} else if (partition && !face.corner()) {
						state = trim;
					} else {
						state = wallBlock(s, face, f, ly, out, y, pal, trim, r, decay, weathering, front);
					}
					if (state.isAir() && doorNear(doors, x, z, floorWalk)) state = pal.glass();
					if (Palettes.thin(state) && (!straight || doorNear(doors, x, z, floorWalk))) {
						state = state.getBlock() == pal.glass().getBlock() ? pal.storefront() : trim;
					}
					if (!straight) state = Palettes.bark(state);
					state = orient(state, face.alongX(), ly == 0 && !face.corner());
				} else if (partition) {
					state = weathered(pal.wall(), pal.weatheredWall(), r, weathering);
				} else if (ly == 0) {
					boolean light = f > 0 && lightU == 3 && lightV == 3 && r >= 0.7 * decay;
					state = light ? LIGHT : FLOOR;
				} else if (ly == FH - 1 && r < 0.08 * decay) {
					state = COBWEB;
				} else {
					state = AIR;
				}
			}
			set(level, pos.set(x, y, z), state);
		}
		int roofTop = placeRoof(level, pos, lot, s, x, z, lu, lv, w, d, floors, top, unit, roofStep, Palettes.bark(pal), Palettes.bark(trim), Palettes.bark(roofBlock));
		clearUp(level, pos, x, roofTop + 1, z);
	}

	private static int unitBase(CityPlanner planner, Lot lot, BuildingSpec s, int unit, int w) {
		Obb fp = lot.footprint();
		int base = lot.baseY();
		int[] span = BuildingShape.unitSpan(lot, unit);
		double u = (span[0] + span[1]) / 2 - fp.halfU; // door column
		int reach = lot.bounds().widthU() - fp.widthU() + 3;
		int best = Integer.MIN_VALUE;
		for (int side : new int[] {-1, 1}) {
			double v = side * (fp.halfV - 0.25);
			int x = (int) Math.round(fp.cx + u * fp.ux - v * fp.uz), z = (int) Math.round(fp.cz + u * fp.uz + v * fp.ux);
			int[] dir = {(int) Math.round(-fp.uz * side), (int) Math.round(fp.ux * side)};
			if (dir[0] == 0 && dir[1] == 0) continue;
			int sy = streetOutside(planner, x, z, dir, reach);
			if (sy == Integer.MIN_VALUE) continue;
			if (best == Integer.MIN_VALUE || Math.abs(sy - 1 - base) < Math.abs(best - 1 - base)) best = sy;
		}
		if (best == Integer.MIN_VALUE) return base;
		return Math.max(base - 8, Math.min(base + 8, best - 1));
	}

	private static int extraFloors(BuildingSpec s, int unit) {
		return Hashing.unit(Hashing.hash(s.seed(), 30, unit)) < 0.4 ? 1 : 0;
	}

	// facades

	private record Face(boolean corner, int index, int length, boolean alongX) {}

	/** Non-null if (x, z) is on the outer wall of floor f */
	private static Face face(Lot lot, Obb fp, int x, int z, int lu, int lv, int f) {
		boolean outX = false, outZ = false;
		int[] first = null;
		for (int[] dir : DIRS) {
			if (!BuildingShape.insideAt(lot, x + dir[0], z + dir[1], f)) {
				if (dir[0] != 0) outX = true;
				else outZ = true;
				if (first == null) first = dir;
			}
		}
		if (first == null) {
			return null;
		}
		if (outX && outZ) {
			int sx = 0, sz = 0;
			for (int[] dir : DIRS) {
				if (BuildingShape.insideAt(lot, x + dir[0], z + dir[1], f)) continue;
				if (dir[0] != 0) sx = dir[0];
				else sz = dir[1];
			}
			boolean alongZWall = BuildingShape.insideAt(lot, x - sx, z, f) && !BuildingShape.insideAt(lot, x - sx, z + sz, f);
			boolean alongXWall = BuildingShape.insideAt(lot, x, z - sz, f) && !BuildingShape.insideAt(lot, x + sx, z - sz, f);
			if (!(alongZWall && alongXWall)) {
				outX = false;
			}
		}
		// Which building axis the face is perpendicular to decides how windows are counted along it
		double du = first[0] * fp.ux + first[1] * fp.uz;
		double dv = -first[0] * fp.uz + first[1] * fp.ux;
		boolean faceAlongV = Math.abs(du) > Math.abs(dv);
		return new Face(outX && outZ, faceAlongV ? lv : lu, faceAlongV ? fp.depthV() : fp.widthU(), first[0] == 0);
	}

	private static BlockState wallBlock(BuildingSpec s, Face face, int f, int ly, Outside out, int y,
			Palettes.Palette pal, BlockState trim, double r, double decay, double weathering, boolean front) {
		BlockState wall = weathered(pal.wall(), pal.weatheredWall(), r, weathering);
		BlockState glass = r < 0.35 * decay ? AIR : weathered(pal.glass(), pal.weatheredGlass(), r, weathering);
		if (face.corner()) return trim;
		boolean groundWall = out.groundWall();
		// Walls that touch or nearly touch a neighbor stay solid up to the neighbor's height
		if (groundWall && y <= out.neighborTop()) return ly == 0 ? trim : wall;
		// Solid walls below ground
		if (groundWall && y < out.groundTop()) return ly == 0 ? trim : wall;
		// The floor level with the street outside gets the doors and shopfronts
		int floorWalk = y - ly + 1;
		boolean streetFloor = out.street() && Math.abs(floorWalk - out.streetY()) <= 1;
		if (streetFloor && groundWall) {
			if (front && s.purpose().hasStorefront()) return ly == 0 ? trim : weathered(pal.storefront(), pal.weatheredGlass(), r, weathering);
		}
		int i = face.index();
		return switch (s.windows()) {
			case BANDS -> ly == 0 ? trim : glass;
			case RIBBONS -> ly == 0 ? trim : (ly == 1 || ly == 2) ? glass : wall;
			case PUNCHED -> ly == 0 ? trim : (i % 3 != 0 && ly <= 2) ? glass : wall;
			case SINGLES -> ly == 0 ? trim : (i % 2 == 1 && ly <= 2) ? glass : wall;
			case STRIPS -> i % 3 == 0 ? pal.accent() : glass;
			case GRID -> (ly == 0 || i % 2 == 0) ? pal.accent() : glass;
			case CURTAIN -> glass;
		};
	}

	private static boolean shopfront(BuildingSpec s, Face face, Outside out, int y, int ly) {
		if (face.corner() || !out.groundWall() || !s.purpose().hasStorefront()) return false;
		if (y <= out.neighborTop() || y < out.groundTop()) return false;
		return out.street() && Math.abs(y - ly + 1 - out.streetY()) <= 1;
	}

	private static BlockState besideWall(CityPlanner planner, Surface surf, Lot lot, Obb fp, BuildingSpec s,
			Palettes.Palette pal, BlockState trim, int nx, int nz, int f, int y, int ly, int unit, int w,
			java.util.Map<Long, Outside> memo) {
		if (!BuildingShape.insideAt(lot, nx, nz, f)) return null;
		int[] c = BuildingShape.local(fp, nx, nz);
		int nlu = c[0], nlv = c[1];
		if (s.purpose() == Purpose.TOWNHOUSES) {
			if (BuildingShape.unit(lot, nlu) != unit) return null;
			if (nlu > 0 && BuildingShape.unit(lot, nlu - 1) != unit) return null;
		}
		Face nf = face(lot, fp, nx, nz, nlu, nlv, f);
		if (nf == null || nf.corner()) return null;
		Outside o = memo.computeIfAbsent(Hashing.pack(nx, nz), k -> lookOutside(planner, surf, lot, nx, nz));
		return wallBlock(s, nf, f, ly, o, y, pal, trim, 1.0, 0, 0, true);
	}

	static BlockState orient(BlockState st, boolean alongX, boolean horizontal) {
		if (st.hasProperty(BlockStateProperties.AXIS)) {
			return st.setValue(BlockStateProperties.AXIS, horizontal ? (alongX ? Direction.Axis.X : Direction.Axis.Z) : Direction.Axis.Y);
		}
		if (st.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock) {
			return st.setValue(BlockStateProperties.EAST, alongX).setValue(BlockStateProperties.WEST, alongX)
					.setValue(BlockStateProperties.NORTH, !alongX).setValue(BlockStateProperties.SOUTH, !alongX);
		}
		if (st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
			return st.setValue(BlockStateProperties.OPEN, true)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, alongX ? Direction.NORTH : Direction.EAST);
		}
		return st;
	}

	private static boolean straightWall(Lot lot, int x, int z, int f, boolean alongX) {
		int ax = alongX ? 1 : 0, az = alongX ? 0 : 1; // along
		int ox = alongX ? 0 : 1, oz = alongX ? 1 : 0; // across
		int side = !BuildingShape.insideAt(lot, x + ox, z + oz, f) ? 1 : -1; // outside
		for (int k : new int[] {-1, 1}) {
			int nx = x + ax * k, nz = z + az * k;
			if (!BuildingShape.insideAt(lot, nx, nz, f) || BuildingShape.insideAt(lot, nx + ox * side, nz + oz * side, f)) return false;
		}
		return true;
	}

	private static int[] indexStep(Obb fp, Face face, int x, int z, int lu, int lv) {
		boolean alongU = face.length() == fp.widthU() && face.index() == lu;
		double dx = alongU ? -fp.ux : fp.uz, dz = alongU ? -fp.uz : -fp.ux;
		return new int[] {(int) Math.round(dx), (int) Math.round(dz)};
	}

	// doors

	record DoorCell(int floorWalk, Direction outward, int px, int pz) {}

	private static final java.util.Map<Lot, java.util.Map<Long, DoorCell>> DOOR_PLANS =
			java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	static java.util.Map<Long, DoorCell> doorPlan(CityPlanner planner, Lot lot) {
		java.util.Map<Long, DoorCell> plan = DOOR_PLANS.get(lot);
		if (plan == null) {
			plan = planDoors(planner, lot);
			DOOR_PLANS.put(lot, plan);
		}
		return plan;
	}

	private static java.util.Map<Long, DoorCell> planDoors(CityPlanner planner, Lot lot) {
		java.util.Map<Long, DoorCell> plan = new java.util.HashMap<>();
		Obb fp = lot.footprint();
		BuildingSpec s = lot.spec();
		if (fp == null || s == null) return plan;
		int w = fp.widthU();
		boolean townhouses = s.purpose() == Purpose.TOWNHOUSES;
		boolean pairs = !townhouses && s.purpose() != Purpose.HOUSE;
		int reach = lot.bounds().widthU() - fp.widthU() + 3;
		record Cand(int x, int z, int fw, int dir, int index, double score, boolean straight) {}
		java.util.Map<Integer, java.util.List<Cand>> groups = new java.util.TreeMap<>();
		for (int x = fp.minX - 1; x <= fp.maxX + 1; x++) {
			for (int z = fp.minZ - 1; z <= fp.maxZ + 1; z++) {
				if (!BuildingShape.insideAt(lot, x, z, 0)) continue;
				int open = openSide(lot, x, z, 0);
				if (open < 0) continue;
				int[] dir = DIRS[open];
				double h = streetHeight(planner, lot, x, z, dir, reach);
				if (Double.isNaN(h)) continue;
				boolean adjacent = planner.classify(x + dir[0], z + dir[1]).isRoad();
				int[] c = BuildingShape.local(fp, x, z);
				int lu = c[0], lv = c[1];
				int unit = townhouses ? BuildingShape.unit(lot, lu) : 0;
				int base = lot.baseY(), floors = lot.floors();
				if (townhouses) {
					if (lu > 0 && BuildingShape.unit(lot, lu - 1) != unit) continue;
					if (lu < w - 1 && BuildingShape.unit(lot, lu + 1) != unit) continue;
					base = unitBase(planner, lot, s, unit, w);
					floors += extraFloors(s, unit);
				}
				int k = (int) Math.round((h - base - 1) / FH);
				int fw = base + k * FH + 1;
				if (k < 0 || k >= floors || Math.abs(h - fw) > 0.5 || adjacent && h > fw) continue;
				if (k > 0 && (!BuildingShape.insideAt(lot, x, z, k) || openSide(lot, x, z, k) != open)) continue;
				Face face = face(lot, fp, x, z, lu, lv, k);
				if (face == null || face.corner()) continue;
				int i = face.index();
				if (i < 1 || i > face.length() - 2) continue;
				boolean front = face.length() == w && i == lu;
				double target;
				if (townhouses && front) {
					int[] span = BuildingShape.unitSpan(lot, unit);
					target = (span[0] + span[1]) / 2;
				} else {
					target = face.length() / 2;
				}
				if (!straightWall(lot, x, z, k, face.alongX())) continue;
				boolean straight = true;
				groups.computeIfAbsent(open * 1000 + unit, g -> new java.util.ArrayList<>())
						.add(new Cand(x, z, fw, open, i, Math.abs(i - target), straight));
			}
		}
		for (java.util.List<Cand> group : groups.values()) {
			Cand best = null;
			for (Cand cd : group) {
				if (best == null || cd.score() < best.score()
						|| cd.score() == best.score() && (cd.x() < best.x() || cd.x() == best.x() && cd.z() < best.z())) best = cd;
			}
			Direction outward = Direction.getApproximateNearest(DIRS[best.dir()][0], 0, DIRS[best.dir()][1]);
			Cand partner = null;
			if (pairs && best.straight()) {
				for (Cand cd : group) {
					if (cd.fw() != best.fw() || !cd.straight() || Math.abs(cd.x() - best.x()) + Math.abs(cd.z() - best.z()) != 1) continue;
					if (partner == null || cd.index() < partner.index()) partner = cd;
				}
			}
			if (partner == null) {
				plan.put(Hashing.pack(best.x(), best.z()), new DoorCell(best.fw(), outward, 0, 0));
			} else {
				int px = partner.x() - best.x(), pz = partner.z() - best.z();
				plan.put(Hashing.pack(best.x(), best.z()), new DoorCell(best.fw(), outward, px, pz));
				plan.put(Hashing.pack(partner.x(), partner.z()), new DoorCell(best.fw(), outward, -px, -pz));
			}
		}
		return plan;
	}

	private static int openSide(Lot lot, int x, int z, int f) {
		int open = -1;
		for (int d = 0; d < DIRS.length; d++) {
			if (BuildingShape.insideAt(lot, x + DIRS[d][0], z + DIRS[d][1], f)) continue;
			if (open >= 0) return -1;
			open = d;
		}
		return open;
	}

	private static double streetHeight(CityPlanner planner, Lot lot, int x, int z, int[] dir, int reach) {
		for (int k = 1; k <= Math.max(2, reach); k++) {
			int nx = x + dir[0] * k, nz = z + dir[1] * k;
			CityPlanner.Cell c = planner.classify(nx, nz);
			if (c.isRoad()) {
				CityPlanner.RoadPoint rp = planner.roadPoint(c, nx, nz);
				return rp.state() == com.fergusb.plan.RoadProfile.State.GROUND || rp.state() == com.fergusb.plan.RoadProfile.State.ELEVATED
						? rp.height() : Double.NaN;
			}
			if (c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.BUILDING && c.lot().footprint() != null
					&& BuildingShape.insideAt(c.lot(), nx, nz, 0)) return Double.NaN;
		}
		return Double.NaN;
	}

	private static boolean doorNear(java.util.Map<Long, DoorCell> doors, int x, int z, int floorWalk) {
		if (doors.isEmpty()) return false;
		DoorCell d = doors.get(Hashing.pack(x, z));
		if (d != null && d.floorWalk() == floorWalk) return true;
		for (int[] dir : DIRS) {
			d = doors.get(Hashing.pack(x + dir[0], z + dir[1]));
			if (d != null && d.floorWalk() == floorWalk) return true;
		}
		return false;
	}

	private static BlockState doorBlock(BuildingSpec s, int ly, DoorCell dc) {
		BlockState st = Palettes.door(s.biome(), s.purpose().residential(), s.seed());
		Direction facing = dc.outward();
		var hinge = net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT;
		if (dc.px() != 0 || dc.pz() != 0) {
			Direction partner = Direction.getApproximateNearest(dc.px(), 0, dc.pz());
			hinge = partner == facing.getCounterClockWise()
					? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT
					: net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT;
		}
		return st.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, ly == 1
						? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
						: net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
				.setValue(BlockStateProperties.DOOR_HINGE, hinge)
				.setValue(BlockStateProperties.OPEN, false);
	}

	private static BlockState weathered(BlockState normal, BlockState worn, double r, double weathering) {
		return r < weathering * 0.6 ? worn : normal;
	}

	// surroundings

	private record Outside(boolean groundWall, boolean street, int neighborTop, int streetY, int groundTop, Direction outward) {}

	private static Outside lookOutside(CityPlanner planner, Surface surf, Lot lot, int x, int z) {
		boolean wall = false, street = false;
		int neighborTop = Integer.MIN_VALUE, streetY = Integer.MIN_VALUE, groundTop = Integer.MIN_VALUE;
		int reach = lot.bounds().widthU() - lot.footprint().widthU() + 3; // setback plus a little
		int[] streetDir = null;
		for (int[] dir : DIRS) {
			if (BuildingShape.insideAt(lot, x + dir[0], z + dir[1], 0)) continue;
			wall = true;
			groundTop = Math.max(groundTop, (int) Math.ceil(surf.heightAt(x + dir[0], z + dir[1])));
			for (int k = 1; k <= Math.max(2, reach); k++) {
				CityPlanner.Cell c = planner.classify(x + dir[0] * k, z + dir[1] * k);
				if (c.isRoad()) {
					CityPlanner.RoadPoint rp = planner.roadPoint(c, x + dir[0] * k, z + dir[1] * k);
					if ((rp.state() == com.fergusb.plan.RoadProfile.State.GROUND || rp.state() == com.fergusb.plan.RoadProfile.State.ELEVATED)) {
						street = true;
						int sy = (int) Math.ceil(rp.height());
						if (sy > streetY) {
							streetY = sy;
							streetDir = dir;
						}
					}
					break;
				}
				if (k <= 2 && c.kind() == CityPlanner.Kind.LOT && c.lot() != lot && c.lot().use() == Lot.Use.BUILDING
						&& BuildingShape.insideAt(c.lot(), x + dir[0] * k, z + dir[1] * k, 0)) {
					neighborTop = Math.max(neighborTop, c.lot().topY());
				}
			}
		}
		Direction outward = streetDir == null ? null : Direction.getApproximateNearest(streetDir[0], 0, streetDir[1]);
		return new Outside(wall, street, neighborTop, streetY, groundTop, outward);
	}

	/** Walking height of the ground-level street within reach of (x, z) in direction dir */
	private static int streetOutside(CityPlanner planner, int x, int z, int[] dir, int reach) {
		for (int k = 1; k <= Math.max(2, reach); k++) {
			CityPlanner.Cell c = planner.classify(x + dir[0] * k, z + dir[1] * k);
			if (!c.isRoad()) continue;
			CityPlanner.RoadPoint rp = planner.roadPoint(c, x + dir[0] * k, z + dir[1] * k);
			return (rp.state() == com.fergusb.plan.RoadProfile.State.GROUND || rp.state() == com.fergusb.plan.RoadProfile.State.ELEVATED) ? (int) Math.ceil(rp.height()) : Integer.MIN_VALUE;
		}
		return Integer.MIN_VALUE;
	}

	// roofs

	/** Places the roof above the top floor and returns the highest Y used */
	private static int placeRoof(WorldGenLevel level, BlockPos.MutableBlockPos pos, Lot lot, BuildingSpec s,
								 int x, int z, int lu, int lv, int w, int d, int floors, int top, int unit,
								 boolean roofStep, Palettes.Palette pal, BlockState trim, BlockState roofBlock) {
		int f = floors - 1;
		if (!BuildingShape.insideAt(lot, x, z, f)) return top - 1;
		boolean edge = roofStep || face(lot, lot.footprint(), x, z, lu, lv, f) != null;
		long h = Hashing.hash(s.seed(), x, top, z);
		double r = Hashing.unit(h);

		if (s.roof() == Roof.GABLE) return gable(level, pos, lot, s, x, z, lu, lv, w, d, top, unit, pal, roofBlock);

		boolean homey = s.purpose() == Purpose.TOWNHOUSES || s.purpose() == Purpose.HOUSE;
		set(level, pos.set(x, top, z), s.roof() == Roof.GARDEN && !edge ? MOSS : edge && homey ? trim : roofBlock);
		if (s.roof() == Roof.FLAT) return top;
		if (edge) {
			set(level, pos.set(x, top + 1, z), trim); // parapet
			return top + 1;
		}
		switch (s.roof()) {
			case GARDEN -> {
				if (r < 0.12) {
					set(level, pos.set(x, top + 1, z), HEDGE);
					return top + 1;
				}
			}
			case EQUIPMENT -> {
				boolean box = Math.floorMod(lu, 5) >= 1 && Math.floorMod(lu, 5) <= 2 && Math.floorMod(lv, 5) >= 1
						&& Math.floorMod(lv, 5) <= 2 && Hashing.unit(Hashing.hash(s.seed(), lu / 5, lv / 5)) < 0.4;
				if (box) {
					int layers = Hashing.unit(Hashing.hash(s.seed(), lu / 5, lv / 5, 2)) < 0.5 ? 1 : 2;
					for (int k = 1; k <= layers; k++) set(level, pos.set(x, top + k, z), VENT);
					set(level, pos.set(x, top + layers + 1, z), VENT_TOP);
					return top + layers + 1;
				}
			}
			case SPIRE -> {
				int cu = Math.abs(lu - w / 2), cv = Math.abs(lv - d / 2);
				if (cu == 0 && cv == 0) {
					int height = 8 + floors / 3;
					set(level, pos.set(x, top + 1, z), pal.accent());
					set(level, pos.set(x, top + 2, z), pal.accent());
					for (int k = 3; k <= height; k++) set(level, pos.set(x, top + k, z), ANTENNA);
					return top + height;
				}
				if (cu <= 1 && cv <= 1) {
					set(level, pos.set(x, top + 1, z), pal.accent());
					set(level, pos.set(x, top + 2, z), pal.accent());
					return top + 2;
				}
			}
			default -> { }
		}
		return top;
	}

	private static int gable(WorldGenLevel level, BlockPos.MutableBlockPos pos, Lot lot, BuildingSpec s, int x, int z,
							 int lu, int lv, int w, int d, int top, int unit, Palettes.Palette pal, BlockState roofBlock) {
		int dist;
		boolean gableEnd;
		if (s.purpose() == Purpose.TOWNHOUSES) {
			int[] span = BuildingShape.unitSpan(lot, unit);
			dist = Math.min(lu - span[0], span[1] - lu);
			gableEnd = lv == 0 || lv == d - 1;
		} else if (w >= d) {
			dist = Math.min(lv, d - 1 - lv);
			gableEnd = lu == 0 || lu == w - 1;
		} else {
			dist = Math.min(lu, w - 1 - lu);
			gableEnd = lv == 0 || lv == d - 1;
		}
		for (int k = 0; k <= dist; k++) {
			BlockState state;
			if (k == dist) state = roofBlock;
			else if (gableEnd) state = pal.wall();
			else state = k == 0 ? FLOOR : AIR;
			set(level, pos.set(x, top + k, z), state);
		}
		return top + dist;
	}

	// helpers

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.isOutsideBuildHeight(pos.getY())) return;
		level.setBlock(pos, state, FLAGS);
	}

	private static void fillDown(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z, BlockState state) {
		for (int y = fromY, n = 0; n < 48; y--, n++) {
			pos.set(x, y, z);
			if (level.isOutsideBuildHeight(y)) return;
			if (!CityPlacer.fillable(level.getBlockState(pos))) return;
			level.setBlock(pos, state, FLAGS);
		}
	}

	private static void clearUp(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z) {
		CityPlacer.clearUp(level, pos, x, fromY, z);
	}
}
