package com.fergusb.world;

import com.fergusb.Megalopolis;
import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Lot;
import com.fergusb.plan.PlanMapRenderer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class MegalopolisCommands {
	private MegalopolisCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("megalopolis")
						.then(Commands.literal("map")
								.executes(ctx -> map(ctx.getSource(), 1024))
								.then(Commands.argument("radius", IntegerArgumentType.integer(64, 8192))
										.executes(ctx -> map(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
						.then(Commands.literal("here")
								.executes(ctx -> here(ctx.getSource())))
						.then(Commands.literal("timing")
								.executes(ctx -> timing(ctx.getSource()))
								.then(Commands.literal("reset").executes(ctx -> {
									com.fergusb.plan.Stats.reset();
									ctx.getSource().sendSuccess(() -> Component.literal("Timings reset."), false);
									return 1;
								})))
						.then(Commands.literal("city")
								.then(Commands.literal("on").executes(ctx -> city(ctx.getSource(), true)))
								.then(Commands.literal("off").executes(ctx -> city(ctx.getSource(), false))))
						.then(Commands.literal("terrain")
								.then(Commands.literal("check").executes(ctx -> terrainCheck(ctx.getSource())))
								.then(Commands.literal("fast").executes(ctx -> terrainMode(ctx.getSource(), true)))
								.then(Commands.literal("vanilla").executes(ctx -> terrainMode(ctx.getSource(), false))))
						.then(Commands.literal("palettes")
								.executes(ctx -> palettes(ctx.getSource(), 768))
								.then(Commands.argument("radius", IntegerArgumentType.integer(64, 4096))
										.executes(ctx -> palettes(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
						.then(Commands.literal("locate")
								.then(Commands.argument("kind", StringArgumentType.word())
										.suggests((ctx, b) -> {
											for (String k : KINDS) b.suggest(k);
											return b.buildFuture();
										})
										.executes(ctx -> locate(ctx.getSource(), StringArgumentType.getString(ctx, "kind"))))));
	}
	private static int timing(CommandSourceStack source) {
		long chunks = com.fergusb.plan.Stats.chunks.get(), placed = com.fergusb.plan.Stats.placed.get();
		double secs = (com.fergusb.plan.Stats.lastChunkNanos.get() - com.fergusb.plan.Stats.firstChunkNanos.get()) / 1e9;
		long plans = com.fergusb.plan.Stats.plans.get(), sums = com.fergusb.plan.Stats.summaries.get();
		long noise = com.fergusb.plan.Stats.noise.get();
		double placeMs = placed == 0 ? 0 : com.fergusb.plan.Stats.placeNanos.get() / 1e6 / placed;
		double maxMs = com.fergusb.plan.Stats.placeMaxNanos.get() / 1e6;
		double planMs = plans == 0 ? 0 : com.fergusb.plan.Stats.planNanos.get() / 1e6 / plans;
		double sumMs = sums == 0 ? 0 : com.fergusb.plan.Stats.summaryNanos.get() / 1e6 / sums;
		double noiseUs = noise == 0 ? 0 : com.fergusb.plan.Stats.noiseNanos.get() / 1e3 / noise;
		double perChunk = placed == 0 ? 0 : (com.fergusb.plan.Stats.planNanos.get() + com.fergusb.plan.Stats.summaryNanos.get()) / 1e6 / placed;
		String text = String.format(
				"City is %s. Chunks generated: %d (%.1f per second over %.0f s).%n"
						+ "Placing the city: %d chunks, %.2f ms each on average (slowest %.1f ms; includes waiting for plans).%n"
						+ "Planning: %d superblocks at %.0f ms each, %d neighbor summaries at %.0f ms each (about %.2f ms per chunk placed, on planner threads).%n"
						+ "Terrain noise: %d samples, %.1f \u00b5s each (%.1f per chunk placed).",
				CityFeature.enabled ? "on" : "off", chunks, secs > 0 ? chunks / secs : 0, secs,
				placed, placeMs, maxMs, plans, planMs, sums, sumMs, perChunk,
				noise, noiseUs, placed == 0 ? 0 : (double) noise / placed);
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int terrainMode(CommandSourceStack source, boolean fast) {
		McTerrainSampler.fast = fast;
		com.fergusb.plan.Stats.reset();
		source.sendSuccess(() -> Component.literal("Terrain sampling for new plans: " + (fast ? "fast" : "vanilla")
				+ ". Timings reset."), false);
		return 1;
	}

	/** Samples random columns around the player and compares */
	private static int terrainCheck(CommandSourceStack source) {
		McTerrainSampler sampler = new McTerrainSampler(source.getLevel());
		MinecraftServer server = source.getServer();
		int px = (int) Math.floor(source.getPosition().x), pz = (int) Math.floor(source.getPosition().z);
		source.sendSuccess(() -> Component.literal("Comparing terrain sampling at 400 columns..."), false);
		CompletableFuture.runAsync(() -> {
			java.util.Random rnd = new java.util.Random(px * 31L + pz);
			long fastNs = 0, vanillaNs = 0;
			int n = 400, diff = 0, waterDiff = 0;
			StringBuilder examples = new StringBuilder();
			for (int k = 0; k < n; k++) {
				int x = px + rnd.nextInt(6001) - 3000, z = pz + rnd.nextInt(6001) - 3000;
				long t0 = System.nanoTime();
				var v = sampler.vanillaSample(x, z);
				long t1 = System.nanoTime();
				var f = sampler.fastSample(x, z);
				long t2 = System.nanoTime();
				vanillaNs += t1 - t0;
				fastNs += t2 - t1;
				if (!v.equals(f)) {
					diff++;
					if (v.water() != f.water()) waterDiff++;
					if (examples.length() < 300) examples.append(String.format(" (%d,%d: %d/%d vs %d/%d)", x, z, v.groundY(), v.surfaceY(), f.groundY(), f.surfaceY()));
				}
			}
			String text = String.format("Vanilla %.0f \u00b5s per column, fast %.0f \u00b5s (%.1fx). Differences: %d of %d columns (%d land/water).%s",
					vanillaNs / 1e3 / n, fastNs / 1e3 / n, (double) vanillaNs / Math.max(1, fastNs), diff, n, waterDiff,
					diff > 0 ? " Ground/surface, vanilla vs fast:" + examples : "");
			server.execute(() -> source.sendSuccess(() -> Component.literal(text), false));
		});
		return 1;
	}

	private static int city(CommandSourceStack source, boolean on) {
		CityFeature.enabled = on;
		com.fergusb.plan.Stats.reset();
		source.sendSuccess(() -> Component.literal(on
				? "City placement on for new chunks. Timings reset."
				: "City placement off for new chunks. Timings reset."), false);
		return 1;
	}

	/** Counts building palettes in the plan around the player */
	private static int palettes(CommandSourceStack source, int radius) {
		CityPlanner planner = Planners.get(source.getLevel());
		MinecraftServer server = source.getServer();
		int px = (int) Math.floor(source.getPosition().x), pz = (int) Math.floor(source.getPosition().z);
		source.sendSuccess(() -> Component.literal("Counting palettes within " + radius + " blocks..."), false);
		CompletableFuture.runAsync(() -> {
			java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
			int total = 0;
			int[] lo = planner.superblockIndex(px - radius, pz - radius), hi = planner.superblockIndex(px + radius, pz + radius);
			for (int i = lo[0]; i <= hi[0]; i++) {
				for (int j = lo[1]; j <= hi[1]; j++) {
					for (Lot l : planner.superblock(i, j).lots) {
						if (l.spec() == null || Math.hypot(l.bounds().cx - px, l.bounds().cz - pz) > radius) continue;
						counts.merge(l.spec().palette().toString().toLowerCase(), 1, Integer::sum);
						total++;
					}
				}
			}
			int n = total;
			StringBuilder sb = new StringBuilder(n + " buildings:");
			counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
					.forEach(e -> sb.append(String.format(" %s %.1f%%,", e.getKey(), 100.0 * e.getValue() / Math.max(1, n))));
			String text = sb.toString().replaceAll(",$", "");
			server.execute(() -> source.sendSuccess(() -> Component.literal(text), false));
		});
		return 1;
	}

	private static final String[] KINDS = {"grid", "diagonal", "organic", "downtown", "outskirts"};

	private static int locate(CommandSourceStack source, String kind) {
		if (!java.util.Arrays.asList(KINDS).contains(kind)) {
			source.sendFailure(Component.literal("Unknown kind '" + kind + "'. Try: " + String.join(", ", KINDS)));
			return 0;
		}
		CityPlanner planner = Planners.get(source.getLevel());
		MinecraftServer server = source.getServer();
		int px = (int) Math.floor(source.getPosition().x), pz = (int) Math.floor(source.getPosition().z);
		int[] home = planner.superblockIndex(px, pz);
		source.sendSuccess(() -> Component.literal("Searching for " + kind + "..."), false);
		CompletableFuture.runAsync(() -> {
			int[] found = null;
			for (int ring = 0; ring <= 48 && found == null; ring++) {
				double best = Double.MAX_VALUE;
				for (int i = home[0] - ring; i <= home[0] + ring; i++) {
					for (int j = home[1] - ring; j <= home[1] + ring; j++) {
						if (Math.max(Math.abs(i - home[0]), Math.abs(j - home[1])) != ring) continue;
						int cx = (planner.arterialX(i) + planner.arterialX(i + 1)) / 2;
						int cz = (planner.arterialZ(j) + planner.arterialZ(j + 1)) / 2;
						if (!matches(planner, kind, i, j, cx, cz)) continue;
						double dist = Math.hypot(cx - px, cz - pz);
						if (dist < best) {
							best = dist;
							found = new int[] {cx, cz, (int) dist};
						}
					}
				}
			}
			int[] result = found;
			server.execute(() -> {
				if (result == null) {
					source.sendFailure(Component.literal("No " + kind + " district found nearby."));
				} else {
					net.minecraft.network.chat.MutableComponent coords = net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets(
									Component.translatable("chat.coordinates", result[0], "~", result[1]))
							.withStyle(st -> st.withColor(net.minecraft.ChatFormatting.GREEN)
									.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(
											"/tp @s " + result[0] + " ~ " + result[1]))
									.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
											Component.translatable("chat.coordinates.tooltip"))));
					Component msg = Component.literal("The nearest " + kind + " district is at ").append(coords)
							.append(" (" + result[2] + " blocks away)");
					source.sendSuccess(() -> msg, false);
				}
			});
		});
		return 1;
	}

	private static boolean matches(CityPlanner planner, String kind, int i, int j, int cx, int cz) {
		return switch (kind) {
			case "downtown" -> planner.fields().downtown(cx, cz) >= 0.8;
			case "outskirts" -> planner.fields().downtown(cx, cz) <= 0.15;
			default -> planner.layoutType(i, j).equals(kind);
		};
	}

	private static int map(CommandSourceStack source, int radius) {
		ServerLevel level = source.getLevel();
		MinecraftServer server = source.getServer();
		CityPlanner planner = Planners.get(level);
		int cx = (int) Math.floor(source.getPosition().x);
		int cz = (int) Math.floor(source.getPosition().z);
		int size = radius * 2;
		int bpp = Math.max(1, size / 2048);
		Path dir = server.getWorldPath(LevelResource.ROOT).resolve("megalopolis");
		source.sendSuccess(() -> Component.literal("Rendering plan map (" + size + " blocks)..."), false);
		CompletableFuture.runAsync(() -> {
			try {
				BufferedImage img = PlanMapRenderer.render(planner, cx - radius, cz - radius, size, bpp);
				Files.createDirectories(dir);
				Path file = dir.resolve("map_" + cx + "_" + cz + "_r" + radius + ".png");
				ImageIO.write(img, "png", file.toFile());
				server.execute(() -> source.sendSuccess(() -> Component.literal("Saved " + file.toAbsolutePath()), false));
			} catch (Exception e) {
				Megalopolis.LOGGER.error("Map render failed", e);
				server.execute(() -> source.sendFailure(Component.literal("Map render failed: " + e.getMessage())));
			}
		});
		return 1;
	}

	private static int here(CommandSourceStack source) {
		CityPlanner planner = Planners.get(source.getLevel());
		int x = (int) Math.floor(source.getPosition().x);
		int z = (int) Math.floor(source.getPosition().z);
		CityPlanner.Cell cell = planner.classify(x, z);
		String where = String.format("%s (downtown=%.2f, condition=%.2f, organic=%.2f)", cell.kind(),
				planner.fields().downtown(x, z), planner.fields().condition(x, z), planner.fields().organic(x, z));
		if (cell.kind() != CityPlanner.Kind.ARTERIAL) {
			int[] sb = planner.superblockIndex(x, z);
			where += " in a " + planner.layoutType(sb[0], sb[1]) + " superblock";
		}
		if (cell.kind() == CityPlanner.Kind.LOT) {
			Lot l = cell.lot();
			where += String.format(" | %s %dx%d, floors=%d, baseY=%d",
					l.use(), l.bounds().widthU(), l.bounds().depthV(), l.floors(), l.baseY())
					+ planner.streetsAround(l);
		}
		String text = where;
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
