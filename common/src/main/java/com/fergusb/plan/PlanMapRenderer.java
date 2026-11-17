package com.fergusb.plan;

import java.awt.image.BufferedImage;

/** Renders a top-down debug map of the plan */
public final class PlanMapRenderer {
    private PlanMapRenderer() {}

    public static BufferedImage render(CityPlanner planner, int minX, int minZ, int sizeBlocks, int blocksPerPixel) {
        int px = Math.max(1, sizeBlocks / blocksPerPixel);
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        GradeField grade = planner.grade();
        for (int iz = 0; iz < px; iz++) {
            for (int ix = 0; ix < px; ix++) {
                int x = minX + ix * blocksPerPixel, z = minZ + iz * blocksPerPixel;
                CityPlanner.Cell cell = planner.classify(x, z);
                int rgb = cell.kind() == CityPlanner.Kind.GAP && grade.water(x, z) ? 0x5b86c2 : color(cell, x, z);
                if (!cell.isRoad()) {
                    int band = (int) Math.floor(grade.grade(x, z) / 8);
                    int bandR = (int) Math.floor(grade.grade(x + blocksPerPixel, z) / 8);
                    int bandD = (int) Math.floor(grade.grade(x, z + blocksPerPixel) / 8);
                    if (band != bandR || band != bandD) rgb = darken(rgb, 0.8);
                }
                img.setRGB(ix, iz, rgb);
            }
        }
        return img;
    }

    private static int color(CityPlanner.Cell c, int x, int z) {
        switch (c.kind()) {
            case ARTERIAL: return 0x262626;
            case STREET: return 0x5c5c5c;
            case GAP: return 0x8fbf72;
            case WILD: return 0x6f8f4f;
            default: break;
        }
        Lot lot = c.lot();
        switch (lot.use()) {
            case PARK: return 0x2e9e3e;
            case GREEN: return 0x9ccc65;
            case PLAZA: return 0xc9c4b0;
            case UNBUILDABLE: return 0x5b86c2;
            default: break;
        }
		if (!BuildingShape.insideAt(lot, x, z, 0)) return 0xd8d2bc;
		// Hue: building purpose. Brightness: height.
		int base = switch (lot.spec().purpose()) {
			case OFFICE -> 0x4f86c6;
			case APARTMENT -> 0xd9894a;
			case MIXED_USE -> 0x9a6fc0;
			case STORE -> 0xe0c341;
			case TOWNHOUSES -> 0xc4524e;
			case HOUSE -> 0xe58fb0;
		};
		double h = Math.min(1, lot.floors() / 30.0);
		double bright = 1.1 - 0.6 * h;
		boolean edge = false;
		for (int dx = -1; dx <= 1 && !edge; dx++) {
			for (int dz = -1; dz <= 1 && !edge; dz++) {
				if (!BuildingShape.insideAt(lot, x + dx, z + dz, 0)) edge = true;
			}
		}
		if (edge) bright *= 0.55; // outline shows where buildings touch
		return rgb(((base >> 16) & 255) * bright, ((base >> 8) & 255) * bright, (base & 255) * bright);
    }

    private static int darken(int rgb, double f) {
        return rgb(((rgb >> 16) & 255) * f, ((rgb >> 8) & 255) * f, (rgb & 255) * f);
    }

    private static int rgb(double r, double g, double b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}