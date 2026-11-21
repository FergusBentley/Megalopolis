package com.fergusb.world;

import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Hashing;
import com.fergusb.plan.Lot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DoorSteps {
	private static final BlockState BLOCK = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState STAIR = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int MAX_STEPS = 6;

	private DoorSteps() {}

	private record Step(int x, int z, int y, Direction up) {}

	static void place(WorldGenLevel level, BlockPos.MutableBlockPos pos, CityPlanner planner, Surface surf) {
		java.util.Set<Lot> lots = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (int dx = -Surface.B; dx < 16 + Surface.B; dx++) {
			for (int dz = -Surface.B; dz < 16 + Surface.B; dz++) {
				CityPlanner.Cell c = surf.cell(dx, dz);
				if (c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.BUILDING && c.lot().footprint() != null) lots.add(c.lot());
			}
		}
		for (Lot lot : lots) {
			Map<Long, BuildingPlacer.DoorCell> doors = BuildingPlacer.doorPlan(planner, lot);
			if (doors.isEmpty()) continue;
			com.fergusb.plan.Obb fp = lot.footprint();
			for (int x = fp.minX - 1; x <= fp.maxX + 1; x++) for (int z = fp.minZ - 1; z <= fp.maxZ + 1; z++) {
				BuildingPlacer.DoorCell dc = doors.get(Hashing.pack(x, z));
				if (dc == null || !dc.door()) continue;
				for (Step st : flight(planner, x, z, dc)) {
					int sx = st.x() - surf.x0, sz = st.z() - surf.z0;
					if (sx < 0 || sz < 0 || sx > 15 || sz > 15) continue;
					BlockState top = st.up() == null ? BLOCK : STAIR.setValue(StairBlock.FACING, st.up());
					CityPlacer.set(level, pos.set(st.x(), st.y(), st.z()), top);
					CityPlacer.fillDown(level, pos, st.x(), st.y() - 1, st.z(), BLOCK);
					for (int y = st.y() + 1; y <= st.y() + 3; y++) CityPlacer.set(level, pos.set(st.x(), y, st.z()), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static List<Step> flight(CityPlanner planner, int x, int z, BuildingPlacer.DoorCell dc) {
		List<Step> out = new ArrayList<>();
		int ox = dc.outward().getStepX(), oz = dc.outward().getStepZ();
		int fx = x + ox, fz = z + oz;
		int fw = dc.floorWalk();
		if (fw - ground(planner, fx, fz) < 1) return out;
		for (int[] n : new int[][] {{fx + ox, fz + oz}, {fx - oz, fz + ox}, {fx + oz, fz - ox}}) {
			if (building(planner, n[0], n[1])) continue;
			double g = ground(planner, n[0], n[1]);
			if (g >= fw - 0.5 && g <= fw + 0.5) {
				out.add(new Step(fx, fz, fw - 1, null));
				return out;
			}
		}
		int[] side = {-oz, ox};
		boolean flip = Hashing.unit(Hashing.hash(x, z, 97)) < 0.5;
		for (int attempt = 0; attempt < 2; attempt++) {
			int sgn = (attempt == 0) == flip ? 1 : -1;
			int tx = side[0] * sgn, tz = side[1] * sgn;
			List<Step> steps = new ArrayList<>();
			steps.add(new Step(fx, fz, fw - 1, null));
			boolean ok = true;
			for (int k = 1; k <= MAX_STEPS; k++) {
				int sx = fx + tx * k, sz = fz + tz * k;
				int y = fw - k;
				if (y < Math.floor(ground(planner, sx, sz))) break; // reached the ground
				if (building(planner, sx, sz)) {
					ok = false;
					break;
				}
				steps.add(new Step(sx, sz, y, Direction.getApproximateNearest(-tx, 0, -tz)));
			}
			if (ok && steps.size() > 1) return steps;
		}
		out.add(new Step(fx, fz, fw - 1, Direction.getApproximateNearest(-ox, 0, -oz)));
		return out;
	}

	private static boolean building(CityPlanner planner, int x, int z) {
		CityPlanner.Cell c = planner.classify(x, z);
		return c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.BUILDING && c.lot().footprint() != null
				&& com.fergusb.plan.BuildingShape.insideAt(c.lot(), x, z, 0);
	}

	private static double ground(CityPlanner planner, int x, int z) {
		CityPlanner.Cell c = planner.classify(x, z);
		if (c.isRoad()) return planner.roadPoint(c, x, z).height();
		if (c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.BUILDING) return c.lot().baseY();
		double level = planner.grade().grade(x, z);
		if (c.kind() == CityPlanner.Kind.LOT && c.lot().use() == Lot.Use.PARK) {
			ParkPaths.Plan plan = ParkPaths.of(planner, c.lot());
			double ph = plan.height(x, z);
			if (!Double.isNaN(ph)) return ph;
			if (!Double.isNaN(plan.level())) level = plan.level();
		} else if (c.kind() == CityPlanner.Kind.GAP) {
			int best = Integer.MAX_VALUE;
			double ph = Double.NaN;
			for (int ox = -Surface.PARK_SIDE; ox <= Surface.PARK_SIDE; ox++) {
				for (int oz = -Surface.PARK_SIDE; oz <= Surface.PARK_SIDE; oz++) {
					int d = ox * ox + oz * oz;
					if (d >= best) continue;
					CityPlanner.Cell n = planner.classify(x + ox, z + oz);
					if (n.kind() != CityPlanner.Kind.LOT || n.lot().use() != Lot.Use.PARK) continue;
					double h = ParkPaths.of(planner, n.lot()).height(x, z);
					if (Double.isNaN(h)) continue;
					best = d;
					ph = h;
				}
			}
			if (!Double.isNaN(ph)) return ph;
		}
		for (int r = 1; r <= 8; r++) {
			int best = Integer.MAX_VALUE, base = 0;
			for (int ox = -r; ox <= r; ox++) {
				for (int oz = -r; oz <= r; oz++) {
					if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
					CityPlanner.Cell n = planner.classify(x + ox, z + oz);
					if (n.kind() != CityPlanner.Kind.LOT || n.lot().use() != Lot.Use.BUILDING) continue;
					int d = ox * ox + oz * oz;
					if (d < best) {
						best = d;
						base = n.lot().baseY();
					}
				}
			}
			if (best != Integer.MAX_VALUE) {
				double allow = Math.max(0, Math.floor(Math.sqrt(best)) - 1);
				return base + Math.max(-allow, Math.min(allow, level - base));
			}
		}
		return level;
	}
}
