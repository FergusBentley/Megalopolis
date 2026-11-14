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
            default: break;
        }
        Lot lot = c.lot();
        switch (lot.use()) {
            case PARK: return 0x4caf50;
            case PLAZA: return 0xc9c4b0;
            case UNBUILDABLE: return 0x5b86c2;
            default: break;
        }
        Obb fp = lot.footprint();
        if (!fp.contains(x, z)) return 0xd8d2bc; // setback / alley
        // Hue: condition. Brightness: height.
        double h = Math.min(1, lot.floors() / 30.0);
        double cond = lot.condition();
        int r = (int) ValueNoise.lerp(cond, 190, 70);
        int g = (int) ValueNoise.lerp(cond, 110, 170);
        int b = (int) ValueNoise.lerp(cond, 80, 200);
        double bright = 1.15 - 0.75 * h;
        if (fp.onEdge(x, z)) bright *= 0.6; // outline shows where buildings touch
        return rgb(r * bright, g * bright, b * bright);
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