package dev.rtpbuddy.hud;

import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.ui.MapPalette;
import dev.rtpbuddy.ui.MapViewState;

import java.util.Arrays;
import java.util.List;

/**
 * The picture on the minimap, painted into a grid of ARGB pixels.
 *
 * <p>Held apart from {@link Minimap} because nothing here touches Minecraft:
 * the plate is a function of the landings, the region grid and a few switches,
 * which means it can be rendered and looked at outside the game. The half that
 * needs a running client - where the plate sits, the player marker on top of
 * it, the caption under it - stays in {@code Minimap}.
 *
 * <p>Everything is composited against an <em>opaque</em> ground and only faded
 * to the configured opacity at the very end. Blending translucent layers onto a
 * translucent ground makes every tinted cell denser than the empty ground beside
 * it, which reads as a stain rather than as a tint.
 */
public final class MinimapPlate {

    /** Corner radius of the plate, in pixels. */
    static final int RADIUS = 5;

    /** Ground under everything. The plate is dark so the world reads through it. */
    private static final int GROUND = 0xFF0D1117;
    private static final int EDGE_INNER = 0xFF3B4653;
    private static final int EDGE_OUTER = 0xFF07090C;

    /** Landings, and the newest one. */
    private static final int DOT = 0xFFB6C6D9;
    private static final int DOT_NEW = 0xFFFFD166;

    /** Region cell tints: the cell the player stands in is the loud one. */
    private static final int CELL_TINT = 22;
    private static final int CELL_TINT_HERE = 58;
    private static final int CELL_EDGE = 64;
    private static final int CELL_EDGE_HERE = 190;

    private static final int AXIS_ALPHA = 96;

    /** Span framed when there is nothing recorded to frame, and its snap grid. */
    static final double FALLBACK_SPAN = 40_000;
    static final double FALLBACK_SNAP = 5_000;

    private final MapViewState view = new MapViewState();
    private double spanBlocks;

    /** The projection the last bake used, so the live layer plots on the same map. */
    public MapViewState view() {
        return view;
    }

    /** How much ground the plate covers, in blocks across. */
    public double spanBlocks() {
        return spanBlocks;
    }

    /**
     * Paints one plate.
     *
     * @param here      the cell the player is standing in, or null off the grid
     * @param playerX   used only to frame the plate when nothing is recorded yet
     */
    public int[] bake(int size, List<RtpSample> samples, ServerRegions.Cell here,
                      boolean showRegions, boolean showAxes, boolean showLastLeg,
                      int opacityPercent, double playerX, double playerZ) {
        int[] px = new int[size * size];
        Arrays.fill(px, GROUND);

        view.setBounds(0, 0, size, size);
        frame(samples, here, playerX, playerZ);
        spanBlocks = size / view.zoom();

        if (showRegions) {
            paintRegions(px, size, here);
        }
        if (showAxes) {
            paintAxes(px, size);
        }
        if (showLastLeg) {
            paintLastLeg(px, size, samples);
        }
        paintSamples(px, size, samples);

        fade(px, size, opacityPercent);
        paintEdge(px, size);
        return px;
    }

    /**
     * Frames the landings, or - with none to frame - the cell the player is in.
     *
     * <p>The fallback matters more than it looks: on the first sitting after an
     * install there is nothing recorded at all, and a plate framed on an empty
     * set would be blank. Framed on the cell it still answers the question the
     * minimap is there for.
     */
    private void frame(List<RtpSample> samples, ServerRegions.Cell here,
                       double playerX, double playerZ) {
        if (!samples.isEmpty()) {
            view.fit(samples);
            return;
        }
        if (here != null) {
            view.fitSquare(here.minX() + ServerRegions.CELL_SIZE / 2.0,
                    here.minZ() + ServerRegions.CELL_SIZE / 2.0,
                    ServerRegions.CELL_SIZE / 2.0);
            return;
        }
        view.fitSquare(Math.floor(playerX / FALLBACK_SNAP) * FALLBACK_SNAP,
                Math.floor(playerZ / FALLBACK_SNAP) * FALLBACK_SNAP,
                FALLBACK_SPAN / 2);
    }

    private void paintRegions(int[] px, int size, ServerRegions.Cell here) {
        for (ServerRegions.Cell cell : ServerRegions.cells()) {
            int x0 = (int) Math.round(view.worldToScreenX(cell.minX()));
            int x1 = (int) Math.round(view.worldToScreenX(cell.maxX()));
            int z0 = (int) Math.round(view.worldToScreenY(cell.minZ()));
            int z1 = (int) Math.round(view.worldToScreenY(cell.maxZ()));
            if (x1 <= 0 || z1 <= 0 || x0 >= size || z0 >= size) {
                continue;
            }
            boolean mine = here != null && here.number() == cell.number();
            int color = cell.zone().color();
            rect(px, size, x0, z0, x1, z1, color, mine ? CELL_TINT_HERE : CELL_TINT);
            int edge = mine ? CELL_EDGE_HERE : CELL_EDGE;
            hLine(px, size, x0, x1, z0, color, edge);
            hLine(px, size, x0, x1, z1 - 1, color, edge);
            vLine(px, size, z0, z1, x0, color, edge);
            vLine(px, size, z0, z1, x1 - 1, color, edge);
        }
    }

    private void paintAxes(int[] px, int size) {
        vLine(px, size, 0, size, (int) Math.round(view.worldToScreenX(0)),
                MapPalette.AXIS, AXIS_ALPHA);
        hLine(px, size, 0, size, (int) Math.round(view.worldToScreenY(0)),
                MapPalette.AXIS, AXIS_ALPHA);
    }

    /**
     * The line into the newest landing: where that teleport started. The full
     * map draws the same leg, and it is the one piece of history worth the ink
     * at this size - it says which way the last jump went.
     */
    private void paintLastLeg(int[] px, int size, List<RtpSample> samples) {
        RtpSample newest = newest(samples);
        if (newest == null || newest.fromX() == null || newest.fromZ() == null) {
            return;
        }
        if (newest.fromDimension() != null && !newest.fromDimension().equals(newest.dimension())) {
            return;
        }
        line(px, size,
                (int) Math.round(view.worldToScreenX(newest.fromX())),
                (int) Math.round(view.worldToScreenY(newest.fromZ())),
                (int) Math.round(view.worldToScreenX(newest.x())),
                (int) Math.round(view.worldToScreenY(newest.z())),
                MapPalette.PATH_IN, 210);
    }

    private void paintSamples(int[] px, int size, List<RtpSample> samples) {
        for (RtpSample sample : samples) {
            plot(px, size,
                    (int) Math.round(view.worldToScreenX(sample.x())),
                    (int) Math.round(view.worldToScreenY(sample.z())),
                    DOT, 205);
        }
        RtpSample newest = newest(samples);
        if (newest == null) {
            return;
        }
        int nx = (int) Math.round(view.worldToScreenX(newest.x()));
        int nz = (int) Math.round(view.worldToScreenY(newest.z()));
        plot(px, size, nx, nz, DOT_NEW, 255);
        plot(px, size, nx - 1, nz, DOT_NEW, 190);
        plot(px, size, nx + 1, nz, DOT_NEW, 190);
        plot(px, size, nx, nz - 1, DOT_NEW, 190);
        plot(px, size, nx, nz + 1, DOT_NEW, 190);
    }

    static RtpSample newest(List<RtpSample> samples) {
        RtpSample newest = null;
        for (RtpSample sample : samples) {
            if (newest == null || sample.timestamp() > newest.timestamp()) {
                newest = sample;
            }
        }
        return newest;
    }

    /** Fades the finished picture to the configured opacity, corners included. */
    private static void fade(int[] px, int size, int percent) {
        int alpha = (int) Math.round(Math.max(0, Math.min(100, percent)) * 2.55);
        for (int y = 0; y < size; y++) {
            int inset = cornerInset(size, y);
            int row = y * size;
            for (int x = 0; x < size; x++) {
                if (x < inset || x >= size - inset) {
                    px[row + x] = 0;
                    continue;
                }
                px[row + x] = (alpha << 24) | (px[row + x] & 0x00FFFFFF);
            }
        }
    }

    /**
     * The rim: a bright hairline just inside a dark one, which is what lifts the
     * plate off whatever happens to be behind it. Painted after the fade and at
     * a fixed alpha, so a plate turned down to a whisper still has an edge.
     */
    private static void paintEdge(int[] px, int size) {
        for (int y = 0; y < size; y++) {
            int inset = cornerInset(size, y);
            int prev = cornerInset(size, Math.max(0, y - 1));
            int next = cornerInset(size, Math.min(size - 1, y + 1));
            rim(px, size, inset, y);
            // On the corner arc the inset jumps by more than one pixel per row,
            // and a rim drawn one pixel per row would break into dashes. The
            // columns the jump skipped are filled from the neighbours' insets.
            for (int i = Math.min(prev, next); i < Math.max(prev, next); i++) {
                rim(px, size, i, y);
            }
        }
        // The top and bottom rows are a rim of their own: the loop above only
        // walks the left and right edges.
        int inset = cornerInset(size, 0);
        for (int x = inset; x < size - inset; x++) {
            edgePixel(px, size, x, 0, EDGE_INNER, 235);
            edgePixel(px, size, x, size - 1, EDGE_INNER, 235);
        }
    }

    private static void rim(int[] px, int size, int inset, int y) {
        edgePixel(px, size, inset, y, EDGE_INNER, 235);
        edgePixel(px, size, size - 1 - inset, y, EDGE_INNER, 235);
        if (inset > 0) {
            edgePixel(px, size, inset - 1, y, EDGE_OUTER, 150);
            edgePixel(px, size, size - inset, y, EDGE_OUTER, 150);
        }
    }

    /**
     * Writes a rim pixel at a fixed alpha, leaving the knocked-out corners empty.
     * The rim is painted after the fade, so it sets its own alpha rather than
     * inheriting the plate's.
     */
    private static void edgePixel(int[] px, int size, int x, int y, int rgb, int alpha) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return;
        }
        int index = y * size + x;
        if ((px[index] >>> 24) == 0) {
            return;
        }
        px[index] = (alpha << 24) | (mix(px[index], rgb, 200) & 0x00FFFFFF);
    }

    /** Horizontal distance from the plate's edge to the rounded corner arc. */
    private static int cornerInset(int size, int y) {
        int r = Math.min(RADIUS, size / 2);
        int row = y < r ? y : (y >= size - r ? size - 1 - y : -1);
        if (row < 0) {
            return 0;
        }
        double dy = r - row - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0.0, r * (double) r - dy * dy)));
    }

    // ------------------------------------------------------------- primitives

    /** Blends a pixel into the opaque composite, keeping its alpha channel. */
    private static void plot(int[] px, int size, int x, int y, int rgb, int alpha) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return;
        }
        px[y * size + x] = mix(px[y * size + x], rgb, alpha);
    }

    private static void rect(int[] px, int size, int x0, int y0, int x1, int y1,
                             int rgb, int alpha) {
        for (int y = Math.max(0, y0); y < Math.min(size, y1); y++) {
            int row = y * size;
            for (int x = Math.max(0, x0); x < Math.min(size, x1); x++) {
                px[row + x] = mix(px[row + x], rgb, alpha);
            }
        }
    }

    private static void hLine(int[] px, int size, int x0, int x1, int y, int rgb, int alpha) {
        if (y < 0 || y >= size) {
            return;
        }
        for (int x = Math.max(0, x0); x < Math.min(size, x1); x++) {
            px[y * size + x] = mix(px[y * size + x], rgb, alpha);
        }
    }

    private static void vLine(int[] px, int size, int y0, int y1, int x, int rgb, int alpha) {
        if (x < 0 || x >= size) {
            return;
        }
        for (int y = Math.max(0, y0); y < Math.min(size, y1); y++) {
            px[y * size + x] = mix(px[y * size + x], rgb, alpha);
        }
    }

    /** Plain Bresenham: the leg is a handful of pixels and needs no more. */
    private static void line(int[] px, int size, int x0, int y0, int x1, int y1,
                             int rgb, int alpha) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        // Both endpoints can sit far outside the plate, so the walk is bounded
        // rather than trusted to arrive.
        int guard = 8 * size + 16;
        while (guard-- > 0) {
            plot(px, size, x0, y0, rgb, alpha);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Blends {@code rgb} at {@code alpha} over a destination, keeping its alpha. */
    private static int mix(int dst, int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (dst & 0xFF000000)
                | (channel(dst, 16, rgb, a) << 16)
                | (channel(dst, 8, rgb, a) << 8)
                | channel(dst, 0, rgb, a);
    }

    private static int channel(int dst, int shift, int rgb, int alpha) {
        int d = (dst >>> shift) & 0xFF;
        int s = (rgb >>> shift) & 0xFF;
        return d + (s - d) * alpha / 255;
    }
}
