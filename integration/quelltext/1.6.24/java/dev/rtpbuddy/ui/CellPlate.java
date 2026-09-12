package dev.rtpbuddy.ui;

import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.stats.CellCoverage;

import java.util.Arrays;

/**
 * Paints the cell board into a grid of ARGB pixels.
 *
 * <p>Split out of {@link CellBoard} for the same reason {@code MinimapPlate} is
 * split out of the minimap: free of every Minecraft import, the picture can be
 * rendered to a file and looked at without starting a game. That is the only
 * way to check a baked image at all - once it is a texture there is nothing
 * left to inspect but the finished pixels.
 */
public final class CellPlate {

    private static final int GRID = ServerRegions.COLUMNS;

    /** Pixels per cell in the baked image, independent of the drawn size. */
    public static final int CELL = 22;
    public static final int SIZE = CELL * GRID;

    static final int EMPTY = 0xFF161A20;
    static final int EMPTY_EDGE = 0xFF20262E;
    static final int ONCE_RING = 0xFFFFD166;

    private CellPlate() {
    }

    /**
     * @param coverage the board to draw
     * @return {@link #SIZE} by {@link #SIZE} pixels, row-major and opaque
     */
    public static int[] bake(CellCoverage coverage) {
        int[] pixels = new int[SIZE * SIZE];
        Arrays.fill(pixels, EMPTY);
        int busiest = Math.max(1, coverage.busiestCount());

        for (ServerRegions.Cell entry : CellCoverage.grid()) {
            int x0 = entry.column() * CELL;
            int y0 = entry.row() * CELL;
            int count = coverage.count(entry.number());
            int zone = entry.zone().color();

            int fill;
            if (count == 0) {
                // The zone tint still shows through, so the board reads as the
                // server's own layout even where nothing has been recorded.
                fill = blend(EMPTY, zone, 0.09);
            } else {
                // Square-rooted, not linear: one cell can hold a third of every
                // landing ever recorded, and on a linear ramp that cell would
                // be the only one with any colour in it.
                double weight = Math.sqrt(count / (double) busiest);
                fill = blend(EMPTY, zone, 0.25 + 0.70 * weight);
            }
            box(pixels, x0, y0, fill);

            if (count == 0) {
                line(pixels, x0, y0, CELL, true, EMPTY_EDGE);
                line(pixels, x0, y0, CELL, false, EMPTY_EDGE);
            } else if (count == 1) {
                // The thin ice: one landing is all that stands between this
                // cell and being empty again if that landing is ever deleted.
                outline(pixels, x0, y0, ONCE_RING);
            }
        }
        return pixels;
    }

    // ------------------------------------------------------- image primitives

    private static void box(int[] pixels, int x0, int y0, int color) {
        for (int y = y0; y < y0 + CELL; y++) {
            int row = y * SIZE;
            for (int x = x0; x < x0 + CELL; x++) {
                pixels[row + x] = color;
            }
        }
    }

    private static void line(int[] pixels, int x0, int y0, int length,
                             boolean horizontal, int color) {
        for (int i = 0; i < length; i++) {
            int x = horizontal ? x0 + i : x0;
            int y = horizontal ? y0 : y0 + i;
            pixels[y * SIZE + x] = color;
        }
    }

    private static void outline(int[] pixels, int x0, int y0, int color) {
        line(pixels, x0, y0, CELL, true, color);
        line(pixels, x0, y0 + CELL - 1, CELL, true, color);
        line(pixels, x0, y0, CELL, false, color);
        line(pixels, x0 + CELL - 1, y0, CELL, false, color);
    }

    /**
     * Composites onto an opaque ground rather than drawing a translucent layer.
     *
     * <p>The image is uploaded as it will be shown, so a tint has to be mixed
     * into the ground here; leaving it translucent would let whatever sits
     * behind the panel show through the cells and not through the gaps between
     * them, which is what made the first gap field look blotchy.
     */
    private static int blend(int ground, int tint, double alpha) {
        double a = Math.max(0.0, Math.min(1.0, alpha));
        int r = (int) Math.round(((ground >> 16) & 0xFF) * (1 - a) + ((tint >> 16) & 0xFF) * a);
        int g = (int) Math.round(((ground >> 8) & 0xFF) * (1 - a) + ((tint >> 8) & 0xFF) * a);
        int b = (int) Math.round((ground & 0xFF) * (1 - a) + (tint & 0xFF) * a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
