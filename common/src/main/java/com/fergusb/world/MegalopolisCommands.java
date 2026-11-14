package com.fergusb.world;

import com.fergusb.Megalopolis;
import com.fergusb.plan.CityPlanner;
import com.fergusb.plan.Lot;
import com.fergusb.plan.PlanMapRenderer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
								.executes(ctx -> here(ctx.getSource()))));
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
			where += planner.superblockAt(x, z).organic ? " in an organic superblock" : " in a grid superblock";
		}
		if (cell.kind() == CityPlanner.Kind.LOT) {
			Lot l = cell.lot();
			where += String.format(" | %s %dx%d, floors=%d, baseY=%d",
					l.use(), l.bounds().widthU(), l.bounds().depthV(), l.floors(), l.baseY());
		}
		String text = where;
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
