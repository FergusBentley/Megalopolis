package com.fergusb.world;

import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.RoadProfile;
import com.fergusb.plan.RoadStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;

import java.util.ArrayList;
import java.util.List;

/** Places road columns from the planned height profiles */
final class RoadPlacer {
	private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();
	private static final BlockState RETAIN = Blocks.STONE_BRICKS.defaultBlockState();
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private final WorldGenLevel level;
	private final Surface surf;
	private final int x0, z0;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final List<BlockPos> links = new ArrayList<>(); // walls and fences to connect

	RoadPlacer(WorldGenLevel level, Surface surf) {
		this.level = level;
		this.surf = surf;
		this.x0 = surf.x0;
		this.z0 = surf.z0;
	}

	void placeColumn(int dx, int dz, boolean wet) {
		int x = x0 + dx, z = z0 + dz;
		CityPlanner.RoadPoint rp = surf.road(dx, dz);
		RoadMaterials m = RoadMaterials.of(rp.style());
		boolean edge = false, open = false, wetSide = false;
		for (int[] d : DIRS) {
			int nx = dx + d[0], nz = dz + d[1];
			if (!surf.builtRoad(nx, nz)) edge = true;
			if (surf.openWater(nx, nz)) {
				wetSide = true;
				if (!surf.builtRoad(nx, nz)) open = true;
			}
		}
		BlockState full = edge ? m.sidewalk() : rp.centerLine() ? m.center() : m.road();
		BlockState slab = edge ? m.sidewalkSlab() : rp.centerLine() ? m.centerSlab() : m.roadSlab();

		double h = rp.height();
		int k = (int) Math.floor(h);
		boolean half = h - k > 0.25;
		int top = k - 1;
		boolean deep = waterDepth(x, z) >= 3;
		boolean lowGround = rp.state() == RoadProfile.State.BRIDGE && !wet && !deep && clearBelow(x, z, top - 1) >= 3;
		CityPlacer.set(level, pos.set(x, top, z), full);
		if (half) CityPlacer.set(level, pos.set(x, k, z), slab);

		RoadProfile.Bridge bridge = rp.bridge();
		if (rp.state() == RoadProfile.State.ELEVATED) {
			elevated(dx, dz, rp, k, half, edge, m);
			return;
		}
		boolean islet = rp.state() == RoadProfile.State.BRIDGE && bridge != null && !wet && !deep
				&& (lowGround || surf.planner().grade().smallIslandAt(x, z));
		boolean onDeck = (rp.state() == RoadProfile.State.BRIDGE && bridge != null && (wet || islet)) || deep && rp.state() == RoadProfile.State.BRIDGE;
		if (onDeck) {
			int t = bridge != null ? bridge.pillar() : rp.arterial() ? 2 : 1, spacing = t <= 1 ? 6 : t == 2 ? 8 : t == 3 ? 10 : 16;
			if (t > 0 && !islet && pillarAt(dx, dz, rp, t, spacing, t >= 4)) {
				CityPlacer.fillDown(level, pos, x, top - 1, z, Bridges.PILLAR);
			}
		} else {
			CityPlacer.fillDown(level, pos, x, top - 1, z, edge ? m.retain() : FOUNDATION);
			if (wetSide) faceWater(x, z, top - 1, m.retain());
		}

		int clearFrom = half ? k + 1 : k;
		if (rp.state() == RoadProfile.State.TUNNEL) {
			tunnel(dx, dz, k, half, m);
			return;
		}
		boolean rail = open || islet || onDeck && !besideBuilding(dx, dz);
		if (edge && rail && !surf.facesDoor(dx, dz)) {
			RoadProfile.Railing railing = bridge != null ? bridge.railing()
					: rp.straight() ? rp.railing() : RoadProfile.Railing.CRENEL;
			clearFrom = railing(x, z, k, half, railing, rp.biome(), m);
		}
		CityPlacer.clearUp(level, pos, x, clearFrom, z);
	}

	private void elevated(int dx, int dz, CityPlanner.RoadPoint rp, int k, boolean half, boolean edge, RoadMaterials m) {
		int x = x0 + dx, z = z0 + dz;
		int top = k - 1;
		int ground = surf.groundY(dx, dz);
		CityPlacer.placePaved(level, pos, Math.min(surf.height(dx, dz), top - 1), x, z);
		for (int y = ground; y < top; y++) CityPlacer.set(level, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
		BlockState full = edge ? m.sidewalk() : m.road(), slab = edge ? m.sidewalkSlab() : m.roadSlab();
		CityPlacer.set(level, pos.set(x, top, z), full);
		if (half) CityPlacer.set(level, pos.set(x, k, z), slab);
		int t = rp.arterial() ? 2 : 1, spacing = t == 1 ? 6 : 8;
		if (pillarAt(dx, dz, rp, t, spacing, false)) {
			CityPlacer.fillDown(level, pos, x, top - 1, z, Bridges.PILLAR);
		}
		int clearFrom = half ? k + 1 : k;
		boolean drop = false;
		for (int[] d : DIRS) {
			int nx = dx + d[0], nz = dz + d[1];
			if (!surf.builtRoad(nx, nz) && !surf.building(nx, nz) && surf.height(nx, nz) <= rp.height() - 2) drop = true;
		}
		if (edge && drop && !surf.facesDoor(dx, dz)) {
			RoadProfile.Railing railing = rp.straight() ? rp.railing() : RoadProfile.Railing.CRENEL;
			clearFrom = railing(x, z, k, half, railing, rp.biome(), m);
		}
		CityPlacer.clearUp(level, pos, x, clearFrom, z);
	}

	private boolean pillarAt(int dx, int dz, CityPlanner.RoadPoint rp, int t, int spacing, boolean centered) {
		int x = x0 + dx, z = z0 + dz;
		double lateral, along, half;
		CityPlanner planner = surf.planner();
		CityPlanner.Cell cell = surf.cell(dx, dz);
		if (rp.arterial()) {
			int ox = planner.arterialOffsetX(x), oz = planner.arterialOffsetZ(z);
			if (ox != Integer.MAX_VALUE && oz != Integer.MAX_VALUE) return false;
			boolean alongZ = ox != Integer.MAX_VALUE;
			lateral = alongZ ? ox : oz;
			along = alongZ ? z : x;
			half = rp.profile().isMinor(rp.s()) ? com.fergusb.plan.RoadProfile.MINOR_HALF_WIDTH : CityPlanner.ARTERIAL_HALF_WIDTH;
		} else {
			com.fergusb.plan.Road road = cell.road();
			if (road == null) return Math.floorMod(x, spacing) < t && Math.floorMod(z, spacing) < t;
			double[] n = road.nearest(x, z);
			lateral = (x - n[0]) * -n[3] + (z - n[1]) * n[2];
			along = road.arcAt(x, z);
			half = road.halfWidth;
		}
		if (Math.floorMod((int) Math.floor(along), spacing) >= t) return false;
		double a = Math.abs(lateral);
		return centered ? a <= t / 2.0 - 0.5 : a >= half - t + 0.5 && a <= half + 0.5;
	}

	private int clearBelow(int x, int z, int y) {
		int n = 0;
		for (; n < 8; n++) {
			BlockState b = level.getBlockState(pos.set(x, y - n, z));
			if (!b.isAir() && b.getFluidState().isEmpty()) break;
		}
		return n;
	}

	private boolean besideBuilding(int dx, int dz) {
		for (int[] d : DIRS) if (surf.building(dx + d[0], dz + d[1])) return true;
		return false;
	}

	private void faceWater(int x, int z, int top, BlockState block) {
		for (int y = level.getSeaLevel() - 6; y <= top; y++) {
			BlockState b = level.getBlockState(pos.set(x, y, z));
			if (b.isAir() || !b.getFluidState().isEmpty() || b.is(block.getBlock())) continue;
			if (b.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || b.is(net.minecraft.tags.BlockTags.DIRT)
					|| b.is(net.minecraft.tags.BlockTags.SAND) || b.is(Blocks.GRAVEL) || b.is(Blocks.CLAY) || b.is(Blocks.SANDSTONE)) {
				CityPlacer.set(level, pos.set(x, y, z), block);
			}
		}
	}

	private int waterDepth(int x, int z) {
		int sea = level.getSeaLevel(), n = 0;
		for (int y = sea - 1; y > sea - 12; y--) {
			BlockState b = level.getBlockState(pos.set(x, y, z));
			if (b.getFluidState().isEmpty() && !b.is(net.minecraft.tags.BlockTags.ICE)) break;
			n++;
		}
		return n;
	}

	private static final int TUNNEL_HEIGHT = 5;
	private static final BlockState TUNNEL_LIGHT = Blocks.SEA_LANTERN.defaultBlockState();

	private void tunnel(int dx, int dz, int k, boolean half, RoadMaterials m) {
		int x = x0 + dx, z = z0 + dz;
		int walk = half ? k + 1 : k; // first air block above the road surface
		for (int y = walk; y < k + TUNNEL_HEIGHT; y++) CityPlacer.set(level, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
		int ceiling = k + TUNNEL_HEIGHT;
		boolean light = Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0;
		CityPlacer.set(level, pos.set(x, ceiling, z), light ? TUNNEL_LIGHT : m.retain());
		boolean portal = false;
		for (int[] d : DIRS) if (surf.openRoad(dx + d[0], dz + d[1])) portal = true;
		if (portal) {
			int top = surf.groundY(dx, dz) - 2;
			for (int y = ceiling + 1; y <= top; y++) CityPlacer.set(level, pos.set(x, y, z), m.retain());
		}
	}

	void lineTunnel(int dx, int dz) {
		if (surf.builtRoad(dx, dz)) return;
		int x = x0 + dx, z = z0 + dz;
		for (int[] d : DIRS) {
			int nx = dx + d[0], nz = dz + d[1];
			if (!surf.tunnel(nx, nz)) continue;
			CityPlanner.RoadPoint rp = surf.road(nx, nz);
			int k = (int) Math.floor(rp.height());
			BlockState lining = RoadMaterials.of(rp.style()).retain();
			for (int y = k - 1; y <= k + TUNNEL_HEIGHT; y++) CityPlacer.set(level, pos.set(x, y, z), lining);
			return;
		}
	}

	private int railing(int x, int z, int k, boolean half, RoadProfile.Railing railing, String biome, RoadMaterials m) {
		if (half && (railing == RoadProfile.Railing.WALL || railing == RoadProfile.Railing.FENCE)) {
			railing = RoadProfile.Railing.BLOCKS;
		}
		boolean even = ((x + z) & 1) == 0;
		switch (railing) {
			case WALL, FENCE -> {
				CityPlacer.set(level, pos.set(x, k, z), railing == RoadProfile.Railing.WALL ? m.wall() : RoadMaterials.fence(biome));
				links.add(new BlockPos(x, k, z));
				return k + 1;
			}
			case BLOCKS -> {
				if (half) {
					CityPlacer.set(level, pos.set(x, k, z), m.retain());
					return k + 1;
				}
				CityPlacer.set(level, pos.set(x, k, z), m.retainSlab());
				return k + 1;
			}
			default -> {
				if (half) {
					CityPlacer.set(level, pos.set(x, k, z), m.retain());
					if (!even) {
						CityPlacer.set(level, pos.set(x, k + 1, z), m.retainSlab());
						return k + 2;
					}
					return k + 1;
				}
				CityPlacer.set(level, pos.set(x, k, z), even ? m.retainSlab() : m.retain());
				return k + 1;
			}
		}
	}

	void finish() {
		for (BlockPos p : links) connect(p, true);
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				retain(dx, dz);
				seawall(dx, dz);
			}
		}
	}

	private void seawall(int dx, int dz) {
		if (surf.builtRoad(dx, dz) || surf.building(dx, dz) || surf.natural(dx, dz) || surf.openWater(dx, dz) || !surf.shore(dx, dz)) return;
		int x = x0 + dx, z = z0 + dz;
		RoadStyle style = surf.style(dx, dz);
		BlockState block = style != null ? RoadMaterials.of(style).retain() : RETAIN;
		int sea = level.getSeaLevel();
		int top = surf.groundY(dx, dz) - 2; // keep the surface block
		for (int y = sea - 6; y <= top; y++) {
			BlockState b = level.getBlockState(pos.set(x, y, z));
			if (b.isAir() || !b.getFluidState().isEmpty() || b.is(Blocks.SEA_LANTERN)) continue;
			level.setBlock(pos, block, Block.UPDATE_CLIENTS);
		}
	}

	private static boolean linkable(BlockState a, BlockState b) {
		if (a.getBlock() instanceof WallBlock) return b.getBlock() instanceof WallBlock;
		if (a.getBlock() instanceof FenceBlock) return b.getBlock() instanceof FenceBlock;
		return false;
	}

	private void connect(BlockPos p, boolean updateNeighbors) {
		BlockState state = level.getBlockState(p);
		boolean wall = state.getBlock() instanceof WallBlock;
		if (!wall && !(state.getBlock() instanceof FenceBlock)) return;
		boolean[] linked = new boolean[4];
		Direction[] dirs = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
		for (int i = 0; i < 4; i++) {
			BlockPos n = p.relative(dirs[i]);
			if (linkable(state, level.getBlockState(n))) {
				linked[i] = true;
				boolean outside = n.getX() < x0 || n.getX() > x0 + 15 || n.getZ() < z0 || n.getZ() > z0 + 15;
				if (updateNeighbors && outside) connect(n, false); // let an earlier chunk's railing link back
			}
		}
		for (int i = 0; i < 4; i++) {
			if (wall) {
				state = state.setValue(WallBlock.PROPERTY_BY_DIRECTION.get(dirs[i]), linked[i] ? WallSide.LOW : WallSide.NONE);
			} else {
				state = state.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(dirs[i]), linked[i]);
			}
		}
		if (wall) {
			boolean straight = (linked[0] && linked[1] && !linked[2] && !linked[3]) || (linked[2] && linked[3] && !linked[0] && !linked[1]);
			state = state.setValue(WallBlock.UP, !straight);
		}
		level.setBlock(p, state, Block.UPDATE_CLIENTS);
	}

	private void retain(int dx, int dz) {
		if (surf.builtRoad(dx, dz) || surf.building(dx, dz) || surf.openWater(dx, dz)) return;
		int x = x0 + dx, z = z0 + dz;
		int topSolid = surf.natural(dx, dz)
				? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z) - 2
				: surf.groundY(dx, dz) - 1;
		int lowest = Integer.MAX_VALUE;
		RoadStyle style = surf.style(dx, dz);
		for (int[] d : DIRS) {
			int nx = dx + d[0], nz = dz + d[1];
			if (surf.building(nx, nz) || surf.openWater(nx, nz) || surf.natural(nx, nz)) continue;
			if (surf.natural(dx, dz) && !surf.openRoad(nx, nz)) continue;
			int walk = (int) Math.floor(surf.height(nx, nz));
			if (walk < lowest) {
				lowest = walk;
				CityPlanner.RoadPoint rp = surf.road(nx, nz);
				if (rp != null) style = rp.style();
			}
		}
		if (lowest == Integer.MAX_VALUE || topSolid - 1 < lowest) return;
		BlockState block = style != null ? RoadMaterials.of(style).retain() : RETAIN;
		for (int y = lowest; y <= topSolid - 1; y++) CityPlacer.set(level, pos.set(x, y, z), block);
	}
}
