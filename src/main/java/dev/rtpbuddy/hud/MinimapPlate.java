package dev.rtpbuddy.hud;

import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.ui.GapScheme;
import dev.rtpbuddy.ui.MapPalette;
import dev.rtpbuddy.ui.MapViewState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Tile sizes the counted layer may snap to. Held in a field rather than
     * fetched per bake because {@link ServerRegions#tileSteps()} hands out a
     * copy, and the ladder never changes.
     */
    private static final double[] GAP_TILE_STEPS = ServerRegions.tileSteps();

    /**
     * Tiles the picker aims to fit across the plate.
     *
     * <p>What makes the layer readable is not how big one square is but how
     * many different answers are on the plate at once. Sized to the biggest
     * square the plate can comfortably draw, a well-travelled frame comes out
     * as one flat block, because at that size every tile holds four landings or
     * more and they all get the same colour - the picture is technically there
     * and says nothing. Two dozen across splits the same ground finely enough
     * for the bands to separate, which is the pattern that can be read.
     *
     * <p>Aiming at a count rather than at a pixel size also makes the tile a
     * property of the ground rather than of the plate: shrinking the minimap
     * then gives the same map smaller, instead of a coarser and emptier one.
     */
    private static final int GAP_TILES_ACROSS = 22;

    /**
     * Under this a tile cannot be drawn as a square at all, at any count. Three
     * pixels is the same floor the map screen's raster refuses below.
     */
    private static final double GAP_TILE_MIN_PIXELS = 3.0;

    /**
     * The floor instead when the counts are to be written in: the font height
     * plus a pixel of air on each side, so a digit is not sitting on the tile's
     * own edge.
     */
    private static final double GAP_NUMBER_MIN_PIXELS = 13.0;

    /**
     * The share of the plate one tile may cover before the layer is dropped.
     *
     * <p>Three or four squares across is not a raster, it is a stripe pattern
     * with no information in it - and that is what a plate zoomed right in gets
     * with the smallest tile on the ladder. Better nothing than a picture that
     * looks like it says something.
     */
    private static final double GAP_TILE_MAX_SHARE = 0.34;

    /**
     * How much of the map screen's tile colour survives here, out of 255.
     *
     * <p>The map screen paints the counted raster as the picture. On the plate
     * it is one layer under the landings, the last leg and the player, all of
     * which have to stay readable on top of it - and the empty-tile yellow is
     * loud enough at full strength to bury every one of them.
     */
    private static final int GAP_TILE_ALPHA = 150;

    /**
     * Tile edges, once a tile is wide enough for the line to say something.
     *
     * <p>Packed tighter than this the edge is a third of the square and the
     * grid reads as the picture instead of the colours doing - and the colour
     * change is the boundary anyway.
     */
    private static final int GAP_EDGE_MIN_PIXELS = 10;
    private static final int GAP_EDGE_ALPHA = 70;

    /** Guard against a pathological frame asking for a raster of everything. */
    private static final long GAP_TILE_BUDGET = 4_000;

    /** Span framed when there is nothing recorded to frame, and its snap grid. */
    static final double FALLBACK_SPAN = 40_000;
    static final double FALLBACK_SNAP = 5_000;

    private final MapViewState view = new MapViewState();
    private double spanBlocks;

    /**
     * The counted layer's tile size in blocks, or 0 when it was not drawn, and
     * the corner it counted from.
     *
     * <p>Read back by {@link Minimap} so the counts can be written on top in
     * the same squares: text cannot go into a pixel array, so the digits are
     * the one part of this layer drawn live.
     */
    private double gapTile;
    private double gapOriginX;
    private double gapOriginZ;

    /**
     * Landings per counted tile, reused between bakes rather than reallocated.
     *
     * <p>Read by {@link Minimap} when the counts are switched on, which is the
     * only reason it outlives the bake.
     */
    private final Map<Long, Integer> gapCounts = new HashMap<>();

    /** The projection the last bake used, so the live layer plots on the same map. */
    public MapViewState view() {
        return view;
    }

    /** How much ground the plate covers, in blocks across. */
    public double spanBlocks() {
        return spanBlocks;
    }

    /** Edge length of one counted tile in blocks, or 0 when none was drawn. */
    public double gapTile() {
        return gapTile;
    }

    public double gapOriginX() {
        return gapOriginX;
    }

    public double gapOriginZ() {
        return gapOriginZ;
    }

    /** Landings per tile from the last bake, keyed by {@link #tileIndexKey}. */
    public Map<Long, Integer> gapCounts() {
        return gapCounts;
    }

    /**
     * Paints one plate.
     *
     * @param here      the cell the player is standing in, or null off the grid
     * @param playerX   used only to frame the plate when nothing is recorded yet
     * @param gapOriginX the low corner of the world border, which the counted
     *                  tiles count from - the same corner the map screen uses,
     *                  and the reason a tile lands on a region line instead of
     *                  across one
     * @param gapNumbers whether the counts are to be written in, which forces a
     *                  tile big enough to hold a digit
     * @param gapScheme the four colours the counted tiles take
     * @param gapSpan   edge length of the square border, or 0 for no border to
     *                  clip against
     */
    public int[] bake(int size, List<RtpSample> samples, ServerRegions.Cell here,
                      boolean showRegions, boolean showAxes, boolean showLastLeg,
                      boolean showGapTiles, boolean gapNumbers, GapScheme gapScheme,
                      double gapOriginX, double gapOriginZ, double gapSpan,
                      int opacityPercent, double playerX, double playerZ) {
        int[] px = new int[size * size];
        Arrays.fill(px, GROUND);

        view.setBounds(0, 0, size, size);
        frame(samples, here, playerX, playerZ);
        spanBlocks = size / view.zoom();

        if (showRegions) {
            paintRegions(px, size, here);
        }
        // Over the region tints and under everything that moves: the tiles are
        // ground, the landings and the player are what stands on it.
        paintGapTiles(px, size, showGapTiles ? samples : List.of(),
                gapNumbers, gapScheme, gapOriginX, gapOriginZ, gapSpan);
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

    /**
     * The map screen's counted tiles, at plate size.
     *
     * <p>Fixed world squares counting from the border corner, each carrying how
     * many of the framed landings fell inside it, coloured on the same four
     * bands the map screen uses. Every size on the ladder divides a region
     * cell, so a tile never lies across a region line - and the tiles are cut
     * at the border rather than drawn whole and left hanging over it.
     *
     * <p>Called with an empty list when the layer is switched off, which keeps
     * the switch in one place and clears the counts on the way past instead of
     * leaving the last set of them in the map.
     */
    private void paintGapTiles(int[] px, int size, List<RtpSample> samples,
                               boolean numbers, GapScheme scheme, double originX,
                               double originZ, double span) {
        gapCounts.clear();
        gapTile = 0;
        gapOriginX = originX;
        gapOriginZ = originZ;
        if (samples.isEmpty()) {
            return;
        }
        // Asking for numbers raises the floor, and on a wide frame the ladder
        // can top out below it - a whole-world plate at 128 pixels has nothing
        // bigger than a region cell to offer and a cell is twelve pixels there.
        // Falling back to the plain floor keeps the squares; the digits are
        // dropped by the live layer on their own when they do not fit.
        double tile = numbers ? pickGapTile(view.zoom(), size, GAP_NUMBER_MIN_PIXELS) : 0;
        if (tile <= 0) {
            tile = pickGapTile(view.zoom(), size, GAP_TILE_MIN_PIXELS);
        }
        if (tile <= 0) {
            return;
        }

        double left = view.screenToWorldX(0);
        double top = view.screenToWorldZ(0);
        double right = view.screenToWorldX(size);
        double bottom = view.screenToWorldZ(size);
        boolean clip = span > 0 && Double.isFinite(span);
        if (clip) {
            left = Math.max(left, originX);
            top = Math.max(top, originZ);
            right = Math.min(right, originX + span);
            bottom = Math.min(bottom, originZ + span);
            if (right <= left || bottom <= top) {
                return;
            }
        }
        long firstX = (long) Math.floor((left - originX) / tile);
        long lastX = (long) Math.floor((right - originX) / tile);
        long firstZ = (long) Math.floor((top - originZ) / tile);
        long lastZ = (long) Math.floor((bottom - originZ) / tile);
        if ((lastX - firstX + 1) * (lastZ - firstZ + 1) > GAP_TILE_BUDGET) {
            return;
        }

        gapTile = tile;
        for (RtpSample sample : samples) {
            gapCounts.merge(tileKey(sample.x(), sample.z(), tile, originX, originZ),
                    1, Integer::sum);
        }

        boolean edges = tile * view.zoom() >= GAP_EDGE_MIN_PIXELS;
        for (long tz = firstZ; tz <= lastZ; tz++) {
            for (long tx = firstX; tx <= lastX; tx++) {
                double worldLeft = originX + tx * tile;
                double worldTop = originZ + tz * tile;
                double worldRight = worldLeft + tile;
                double worldBottom = worldTop + tile;
                if (clip) {
                    worldLeft = Math.max(worldLeft, originX);
                    worldTop = Math.max(worldTop, originZ);
                    worldRight = Math.min(worldRight, originX + span);
                    worldBottom = Math.min(worldBottom, originZ + span);
                    if (worldRight <= worldLeft || worldBottom <= worldTop) {
                        continue;
                    }
                }
                int x0 = (int) Math.round(view.worldToScreenX(worldLeft));
                int x1 = (int) Math.round(view.worldToScreenX(worldRight));
                int z0 = (int) Math.round(view.worldToScreenY(worldTop));
                int z1 = (int) Math.round(view.worldToScreenY(worldBottom));
                if (x1 <= 0 || z1 <= 0 || x0 >= size || z0 >= size || x1 <= x0 || z1 <= z0) {
                    continue;
                }
                int band = scheme.band(gapCounts.getOrDefault(
                        tileIndexKey(tx, tz), 0));
                rect(px, size, x0, z0, x1, z1, band & 0x00FFFFFF,
                        ((band >>> 24) & 0xFF) * GAP_TILE_ALPHA / 255);
                if (edges) {
                    hLine(px, size, x0, x1, z0, MapPalette.GRID_MAJOR, GAP_EDGE_ALPHA);
                    vLine(px, size, z0, z1, x0, MapPalette.GRID_MAJOR, GAP_EDGE_ALPHA);
                }
            }
        }
    }

    /**
     * The ladder step nearest {@link #GAP_TILES_ACROSS} tiles across the framed
     * ground, raised until the plate can actually draw it.
     *
     * <p>The map screen sizes its tiles off the scale bar and then holds them,
     * because out there the question is how much ground one square stands for.
     * A plate has no scale bar and about a tenth of the room, so it aims at the
     * count instead and lets the pixels fall where they fall - down to the
     * point where a square stops being one, which is the only floor left.
     *
     * <p>The floor is handed in because it is not always the same one: a plate
     * that has to hold a written count needs four times the tile a plain
     * coloured square does, and that is the whole cost of switching the numbers
     * on.
     *
     * @return 0 when nothing fits - the frame is so tight that even the finest
     *         step swallows the plate, or so wide that no step reaches the floor
     */
    private static double pickGapTile(double zoom, int size, double floor) {
        double wanted = size / zoom / GAP_TILES_ACROSS;
        double best = GAP_TILE_STEPS[0];
        for (double step : GAP_TILE_STEPS) {
            if (Math.abs(step - wanted) < Math.abs(best - wanted)) {
                best = step;
            }
        }
        // A tile picked off the ground can still be too few pixels to draw on a
        // small plate, so the wanted size is a starting point and the first
        // step at or above it that the plate can render is what gets used.
        double most = size * GAP_TILE_MAX_SHARE;
        for (double step : GAP_TILE_STEPS) {
            if (step >= best && step * zoom >= floor) {
                return step * zoom <= most ? step : 0;
            }
        }
        return 0;
    }

    /** The tile a world position falls in. */
    private static long tileKey(double worldX, double worldZ, double tile,
                                double originX, double originZ) {
        return tileIndexKey((long) Math.floor((worldX - originX) / tile),
                (long) Math.floor((worldZ - originZ) / tile));
    }

    /** The key a tile index pair takes in {@link #gapCounts()}. */
    public static long tileIndexKey(long tx, long tz) {
        return (tx << 32) ^ (tz & 0xFFFFFFFFL);
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
