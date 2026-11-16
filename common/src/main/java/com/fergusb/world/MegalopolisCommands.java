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
						.then(Commands.literal("locate")
								.then(Commands.argument("kind", StringArgumentType.word())
										.suggests((ctx, b) -> {
											for (String k : KINDS) b.suggest(k);
											return b.buildFuture();
										})
										.executes(ctx -> locate(ctx.getSource(), StringArgumentType.getString(ctx, "kind"))))));
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
