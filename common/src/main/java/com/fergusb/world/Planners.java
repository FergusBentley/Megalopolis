package com.fergusb.world;

import com.fergusb.plan.CityPlanner;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class Planners {
	private static final Map<ServerLevel, CityPlanner> PLANNERS = Collections.synchronizedMap(new WeakHashMap<>());

	private Planners() {}

	public static CityPlanner get(ServerLevel level) {
		return PLANNERS.computeIfAbsent(level, l -> new CityPlanner(l.getSeed(), new McTerrainSampler(l)));
	}
}
