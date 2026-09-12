package dev.rtpbuddy.stats;

import dev.rtpbuddy.data.RtpSample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How far every point on the canvas is from the nearest recorded landing.
 *
 * <p>The heatmap answers "where did I land"; this answers the opposite, and the
 * two are not the same picture. Absence has no marker to draw, so a dark patch
 * on a heatmap means three different things at once - never landed there, the
 * filter hid it, or nothing on the server ever sends you there. Measuring the
 * distance to the nearest landing turns the absence itself into a value that
 * can be shaded, ranked and read off the screen.
 *
 * <p>The field is computed in screen space, which keeps it the same cost at any
 * zoom, and by a two-pass chamfer transform rather than by asking every cell for
 * its nearest sample: the brute-force version is cells times samples, some
 * thirty million multiplications for a full canvas and a thousand landings.
 *
 * <h2>Where the measurement is allowed to mean anything</h2>
 *
 * <p>Distance alone would always name the edge of the canvas as the emptiest
 * place, because nothing has ever been recorded past it. The field therefore
 * carries a mask: the world tiles that hold a landing, grown by one tile in
 * every direction. Inside it, "no landing here" is a fact about the server;
 * outside it, it is only a fact about how far the recording has got. Holes are
 * ranked inside the mask only.
 *
 * <p>Nothing here knows which region a landing was asked for. It does not have
 * to: a spot reachable through two regions is only rare if it is rare in both,
 * and the distance to the nearest landing of <em>any</em> region on show is
 * exactly that. Narrowing to one region is the filter's job.
 *
 * <h2>Why the whole picture is built here</h2>
 *
 * <p>Including the banding into rectangles, which is drawing work and looks out
 * of place in a stats class. It is here because it is the only step that touches
 * every cell again, and doing it beside the transform keeps the per-cell work in
 * one place where it can be kept to array reads and integer arithmetic. The
 * first version asked a palette function for a colour per cell; three calls to
 * {@code Math.pow} per cell, ninety thousand cells on a large canvas and a
 * rebuild on every frame of a zoom is how a map screen heats up a CPU.
 */
public final class GapField {

    /** A round patch of world with no recorded landing in it. */
    public record Hole(double x, double z, double radius) {
    }

    /**
     * Smallest cell, in canvas pixels.
     *
     * <p>The field is a smooth measurement, not a picture with edges in it, so a
     * finer cell buys almost no visible detail while every step finer costs the
     * square of it in build time. Four measured two thirds slower than six and
     * looked the same once the ramp was smooth.
     */
    private static final int MIN_CELL = 6;

    /**
     * Cell budget for one build.
     *
     * <p>The cost of everything here is linear in cells, and the canvas can be
     * anything from a corner of a scaled-up GUI to most of a 1440p screen - a
     * spread of about eight times in area. A fixed cell size means the work
     * follows the window size, so the mode runs fine while it is being written
     * and melts on the machine it ships to. Holding the cell count instead makes
     * a big canvas draw a slightly coarser field for the same cost.
     */
    private static final int MAX_CELLS = 40_000;

    /**
     * Ceiling on the mask's own tile grid.
     *
     * <p>The tile is a setting and the samples can span half a world, so the
     * product of the two is what has to be held rather than either on its own.
     * Past this the tile is doubled until the grid fits.
     */
    private static final int MAX_MASK_TILES = 1_000_000;

    /**
     * Cells of working grid kept beyond each edge of the canvas.
     *
     * <p>Without it a landing one pixel off the left edge is not seeded, the
     * cells beside it measure their distance to something far inland, and the
     * map grows a bright false hole along its own border - which the ranking
     * would then name as the emptiest place on the server. The margin lets
     * landings just out of frame pull the field down the way they should.
     */
    private static final int MARGIN = 24;

    /** Chamfer weights: 3 for a step, 4 for a diagonal, so one cell is 3 units. */
    private static final int STEP = 3;
    private static final int DIAGONAL = 4;
    private static final float UNREACHED = 1e9f;

    /** A hole must be this fraction of the deepest one before it is worth naming. */
    private static final double HOLE_FLOOR = 0.15;

    /** Two holes closer than this share of their radii are treated as one. */
    private static final double HOLE_SEPARATION = 0.75;

    /** Ground outside the mask is shaded at this share, not dropped. */
    private static final double OUTSIDE_DIM = 0.3;

    /** Where the ramp reaches its top end, as a share of the deepest hole. */
    private static final double RAMP_TOP = 0.72;

    /** The working grid, canvas plus margin on every side. */
    private float[] dist = new float[0];
    private int columns;
    private int rows;

    /** The canvas grid, which is what the drawing and the ranking see. */
    private boolean[] inside = new boolean[0];
    private int gridWidth;
    private int gridHeight;
    private int cell = MIN_CELL;

    /** The shaded picture, one ARGB value per canvas cell, row-major. */
    private int[] pixels = new int[0];

    private double worldLeft;
    private double worldTop;
    private double blocksPerPixel;

    /**
     * The mask, kept in world units after the build.
     *
     * <p>The field itself is screen space and is thrown away with the view, but
     * the mask is a statement about the world and must survive as one. Asking it
     * through a screen pixel was what made the outer tiles flicker on a pan: a
     * tile hanging off the edge was probed at whatever part of it was still
     * visible, so the same square answered differently from one frame to the
     * next.
     */
    private int maskTile;
    private int maskX0;
    private int maskZ0;
    private int maskWide;
    private int maskHigh;
    private double[] rowMinX = new double[0];
    private double[] rowMaxX = new double[0];
    private double[] columnMinZ = new double[0];
    private double[] columnMaxZ = new double[0];

    private double deepestBlocks;
    private List<Hole> holes = List.of();
    private boolean ready;
    private long lastBuildNanos;

    public boolean ready() {
        return ready;
    }

    /** Canvas pixels per field cell for the last build. */
    public int cellPixels() {
        return cell;
    }

    public int gridWidth() {
        return gridWidth;
    }

    public int gridHeight() {
        return gridHeight;
    }

    /** How long the last build took, for deciding whether it can run per frame. */
    public long lastBuildNanos() {
        return lastBuildNanos;
    }

    /** The deepest hole found inside the mask, in blocks. Zero means none. */
    public double deepestBlocks() {
        return deepestBlocks;
    }

    public List<Hole> holes() {
        return holes;
    }

    /** One ARGB value per canvas cell, row-major, {@code gridWidth * gridHeight} long. */
    public int[] pixels() {
        return pixels;
    }

    /** Distance from this canvas cell to the nearest landing, in blocks. */
    public double blocksAt(int cellX, int cellZ) {
        if (!ready || cellX < 0 || cellZ < 0 || cellX >= gridWidth || cellZ >= gridHeight) {
            return 0;
        }
        return toBlocks(dist[index(cellX, cellZ)]);
    }

    /** Whether this canvas cell sits in territory the recording actually covers. */
    public boolean insideAt(int cellX, int cellZ) {
        if (!ready || cellX < 0 || cellZ < 0 || cellX >= gridWidth || cellZ >= gridHeight) {
            return false;
        }
        return inside[cellZ * gridWidth + cellX];
    }

    /**
     * Reading at a point on the canvas, for the cursor line.
     *
     * @param canvasX pixels from the left edge of the plot
     * @param canvasY pixels from the top edge of the plot
     * @return distance to the nearest landing in blocks, or NaN outside the mask
     */
    public double blocksAtCanvas(double canvasX, double canvasY) {
        int cellX = (int) Math.floor(canvasX / cell);
        int cellZ = (int) Math.floor(canvasY / cell);
        if (!insideAt(cellX, cellZ)) {
            return Double.NaN;
        }
        return blocksAt(cellX, cellZ);
    }

    /**
     * Rebuilds the field for one view of one sample set.
     *
     * @param worldLeft      world X at the left edge of the plot
     * @param worldTop       world Z at the top edge of the plot
     * @param blocksPerPixel world units one canvas pixel covers
     * @param widthPixels    plot width
     * @param heightPixels   plot height
     * @param maskTile       edge length of a mask tile, in blocks
     * @param topCount       how many holes to rank
     * @param palette        colours for the banded picture, dark end first
     */
    public void build(List<RtpSample> samples, double worldLeft, double worldTop,
                      double blocksPerPixel, int widthPixels, int heightPixels,
                      int maskTile, int topCount, int[] palette) {
        long started = System.nanoTime();
        ready = false;
        holes = List.of();
        deepestBlocks = 0;
        maskWide = 0;
        maskHigh = 0;
        this.worldLeft = worldLeft;
        this.worldTop = worldTop;
        this.blocksPerPixel = blocksPerPixel;

        if (samples == null || samples.isEmpty() || widthPixels <= 0 || heightPixels <= 0
                || !(blocksPerPixel > 0) || !Double.isFinite(worldLeft) || !Double.isFinite(worldTop)) {
            lastBuildNanos = System.nanoTime() - started;
            return;
        }

        chooseCell(widthPixels, heightPixels);
        columns = gridWidth + 2 * MARGIN;
        rows = gridHeight + 2 * MARGIN;

        if (dist.length != columns * rows) {
            dist = new float[columns * rows];
        }
        if (inside.length != gridWidth * gridHeight) {
            inside = new boolean[gridWidth * gridHeight];
            pixels = new int[gridWidth * gridHeight];
        }
        Arrays.fill(pixels, 0);
        Arrays.fill(dist, UNREACHED);
        Arrays.fill(inside, false);

        seed(samples);
        transform();
        markInside(samples, Math.max(256, maskTile));
        rank(Math.max(0, topCount));
        paint(palette);
        ready = true;
        lastBuildNanos = System.nanoTime() - started;
    }

    /** The finest cell that keeps the grid inside the budget. */
    private void chooseCell(int widthPixels, int heightPixels) {
        cell = MIN_CELL;
        while (true) {
            gridWidth = Math.max(1, (widthPixels + cell - 1) / cell);
            gridHeight = Math.max(1, (heightPixels + cell - 1) / cell);
            if (gridWidth * gridHeight <= MAX_CELLS || cell >= 32) {
                return;
            }
            cell += 2;
        }
    }

    private void seed(List<RtpSample> samples) {
        for (RtpSample sample : samples) {
            int cellX = (int) Math.floor((sample.x() - worldLeft) / blocksPerPixel / cell);
            int cellZ = (int) Math.floor((sample.z() - worldTop) / blocksPerPixel / cell);
            if (cellX >= -MARGIN && cellZ >= -MARGIN
                    && cellX < gridWidth + MARGIN && cellZ < gridHeight + MARGIN) {
                dist[index(cellX, cellZ)] = 0;
            }
        }
    }

    /**
     * Two sweeps, one down-right and one up-left. After both, every cell holds
     * the length of the shortest chain of steps back to a seeded cell, which for
     * chamfer weights of 3 and 4 is within a few percent of the true distance.
     */
    private void transform() {
        for (int z = 0; z < rows; z++) {
            for (int x = 0; x < columns; x++) {
                int i = z * columns + x;
                float best = dist[i];
                if (x > 0) {
                    best = Math.min(best, dist[i - 1] + STEP);
                }
                if (z > 0) {
                    best = Math.min(best, dist[i - columns] + STEP);
                    if (x > 0) {
                        best = Math.min(best, dist[i - columns - 1] + DIAGONAL);
                    }
                    if (x < columns - 1) {
                        best = Math.min(best, dist[i - columns + 1] + DIAGONAL);
                    }
                }
                dist[i] = best;
            }
        }
        for (int z = rows - 1; z >= 0; z--) {
            for (int x = columns - 1; x >= 0; x--) {
                int i = z * columns + x;
                float best = dist[i];
                if (x < columns - 1) {
                    best = Math.min(best, dist[i + 1] + STEP);
                }
                if (z < rows - 1) {
                    best = Math.min(best, dist[i + columns] + STEP);
                    if (x < columns - 1) {
                        best = Math.min(best, dist[i + columns + 1] + DIAGONAL);
                    }
                    if (x > 0) {
                        best = Math.min(best, dist[i + columns - 1] + DIAGONAL);
                    }
                }
                dist[i] = best;
            }
        }
    }

    /**
     * The study area: the tiles the landings actually span, filled in.
     *
     * <p>The first version grew every occupied tile by a ring of one, which is
     * the cheap way to close the holes between landings - and on a real map it
     * wrapped the whole recording in an apron of bright, never-visited ground
     * several tiles wide. Those cells are honestly empty and completely useless:
     * they are empty because nobody has looked there yet, not because the server
     * avoids them.
     *
     * <p>So the mask is the recording's own footprint, filled rather than grown.
     * The tile is only a strip width: the landings are cut into strips that many
     * blocks tall and that many wide, and a point is inside when it lies between
     * the leftmost and rightmost landing of its row strip <em>and</em> between
     * the topmost and bottommost landing of its column strip. Holes in the middle
     * stay inside, which is the whole point, while the outer edge lands on the
     * outermost <em>landing</em> rather than on the tile edge past it - measuring
     * the strips against the real coordinates is what stops a column of never
     * visited ground surviving along the left and bottom.
     */
    private void markInside(List<RtpSample> samples, int tile) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (RtpSample sample : samples) {
            minX = Math.min(minX, sample.x());
            maxX = Math.max(maxX, sample.x());
            minZ = Math.min(minZ, sample.z());
            maxZ = Math.max(maxZ, sample.z());
        }
        while ((maxX - minX) / tile * ((maxZ - minZ) / tile) > MAX_MASK_TILES && tile < 1 << 24) {
            tile *= 2;
        }

        maskTile = tile;
        maskX0 = tileOf(minX, tile);
        maskZ0 = tileOf(minZ, tile);
        maskWide = tileOf(maxX, tile) - maskX0 + 1;
        maskHigh = tileOf(maxZ, tile) - maskZ0 + 1;
        if (maskWide <= 0 || maskHigh <= 0) {
            maskWide = 0;
            maskHigh = 0;
            return;
        }

        // Per strip, the real span of the landings in it. A strip with none
        // keeps its empty range, so nothing in it can ever pass the test.
        if (rowMinX.length != maskHigh) {
            rowMinX = new double[maskHigh];
            rowMaxX = new double[maskHigh];
        }
        if (columnMinZ.length != maskWide) {
            columnMinZ = new double[maskWide];
            columnMaxZ = new double[maskWide];
        }
        Arrays.fill(rowMinX, Double.MAX_VALUE);
        Arrays.fill(rowMaxX, -Double.MAX_VALUE);
        Arrays.fill(columnMinZ, Double.MAX_VALUE);
        Arrays.fill(columnMaxZ, -Double.MAX_VALUE);

        for (RtpSample sample : samples) {
            int tz = tileOf(sample.z(), tile) - maskZ0;
            int tx = tileOf(sample.x(), tile) - maskX0;
            rowMinX[tz] = Math.min(rowMinX[tz], sample.x());
            rowMaxX[tz] = Math.max(rowMaxX[tz], sample.x());
            columnMinZ[tx] = Math.min(columnMinZ[tx], sample.z());
            columnMaxZ[tx] = Math.max(columnMaxZ[tx], sample.z());
        }

        double half = cell / 2.0;
        for (int z = 0; z < gridHeight; z++) {
            double worldZ = worldTop + (z * cell + half) * blocksPerPixel;
            for (int x = 0; x < gridWidth; x++) {
                double worldX = worldLeft + (x * cell + half) * blocksPerPixel;
                boolean covered = maskCovers(worldX, worldZ);
                inside[z * gridWidth + x] = covered;
                if (covered) {
                    deepestBlocks = Math.max(deepestBlocks, toBlocks(dist[index(x, z)]));
                }
            }
        }
    }

    /**
     * Whether a world point lies in the study area.
     *
     * <p>The only honest way to ask, and the one the drawing has to use for
     * anything bigger than a field cell: the answer depends on the point, never
     * on where the view happens to sit.
     */
    /**
     * The ground the mask spans, as {@code {minX, minZ, maxX, maxZ}}, or null
     * while nothing is built.
     *
     * <p>Everything outside it is ground no landing has ever been near, which
     * is what lets the counted raster stop iterating there instead of walking
     * empty tiles it would only skip.
     */
    public double[] maskBounds() {
        if (!ready || maskWide <= 0 || maskHigh <= 0) {
            return null;
        }
        return new double[]{
                (double) maskX0 * maskTile,
                (double) maskZ0 * maskTile,
                (double) (maskX0 + maskWide) * maskTile,
                (double) (maskZ0 + maskHigh) * maskTile
        };
    }

    public boolean insideWorld(double worldX, double worldZ) {
        return ready && maskCovers(worldX, worldZ);
    }

    private boolean maskCovers(double worldX, double worldZ) {
        if (maskWide <= 0 || maskHigh <= 0) {
            return false;
        }
        int tz = tileOf(worldZ, maskTile) - maskZ0;
        int tx = tileOf(worldX, maskTile) - maskX0;
        if (tz < 0 || tz >= maskHigh || tx < 0 || tx >= maskWide) {
            return false;
        }
        return worldX >= rowMinX[tz] && worldX <= rowMaxX[tz]
                && worldZ >= columnMinZ[tx] && worldZ <= columnMaxZ[tx];
    }

    /**
     * The deepest points of the field, taken in order and skipping any that fall
     * inside one already picked - two peaks a few cells apart are one hole with
     * a flat bottom, not two places worth walking to.
     */
    private void rank(int topCount) {
        if (topCount <= 0 || deepestBlocks <= 0) {
            return;
        }
        double floor = deepestBlocks * HOLE_FLOOR;
        double half = cell / 2.0;
        List<Hole> peaks = new ArrayList<>();

        for (int z = 0; z < gridHeight; z++) {
            for (int x = 0; x < gridWidth; x++) {
                if (!inside[z * gridWidth + x]) {
                    continue;
                }
                int i = index(x, z);
                float value = dist[i];
                if (toBlocks(value) < floor) {
                    continue;
                }
                if (higherInside(value, x - 1, z) || higherInside(value, x + 1, z)
                        || higherInside(value, x, z - 1) || higherInside(value, x, z + 1)) {
                    continue;
                }
                peaks.add(new Hole(worldLeft + (x * cell + half) * blocksPerPixel,
                        worldTop + (z * cell + half) * blocksPerPixel,
                        reportedRadius(value)));
            }
        }
        peaks.sort((a, b) -> Double.compare(b.radius(), a.radius()));

        List<Hole> picked = new ArrayList<>(topCount);
        for (Hole peak : peaks) {
            if (picked.size() >= topCount) {
                break;
            }
            boolean merged = false;
            for (Hole kept : picked) {
                double reach = (kept.radius() + peak.radius()) * HOLE_SEPARATION;
                if (Math.hypot(kept.x() - peak.x(), kept.z() - peak.z()) < reach) {
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                picked.add(peak);
            }
        }
        holes = List.copyOf(picked);
    }

    /**
     * Colours every cell from a lookup table.
     *
     * <p>Banded rather than continuous, and read out of a table rather than
     * computed: the first version called a palette function per cell, which cost
     * three {@code Math.pow} calls on a grid of tens of thousands rebuilt
     * whenever the view moved. With the picture going to a texture there is no
     * longer any reason to keep the band count low, so the table is fine enough
     * that the steps do not read as steps.
     */
    private void paint(int[] palette) {
        if (palette == null || palette.length < 2 || deepestBlocks <= 0) {
            return;
        }
        int bands = palette.length - 1;
        double span = Math.max(1.0, deepestBlocks * RAMP_TOP);
        double scale = bands * cell * blocksPerPixel / (STEP * span);

        for (int z = 0; z < gridHeight; z++) {
            int rowBase = z * gridWidth;
            int distBase = (z + MARGIN) * columns + MARGIN;
            for (int x = 0; x < gridWidth; x++) {
                double value = dist[distBase + x] * scale;
                if (!inside[rowBase + x]) {
                    value *= OUTSIDE_DIM;
                }
                int band = (int) (value + 0.5);
                pixels[rowBase + x] = palette[band < 0 ? 0 : Math.min(band, bands)];
            }
        }
    }

    /** Whether a neighbouring cell is both inside the mask and deeper than this one. */
    private boolean higherInside(float value, int cellX, int cellZ) {
        if (cellX < 0 || cellZ < 0 || cellX >= gridWidth || cellZ >= gridHeight) {
            return false;
        }
        return inside[cellZ * gridWidth + cellX] && dist[index(cellX, cellZ)] > value;
    }

    /** Canvas cell to working-grid index. The margin makes every neighbour real. */
    private int index(int cellX, int cellZ) {
        return (cellZ + MARGIN) * columns + (cellX + MARGIN);
    }

    /**
     * The radius a hole is willing to claim.
     *
     * <p>Half a cell short of what the field measured. Two things push the raw
     * reading high: chamfer weights overestimate a diagonal by a few percent,
     * and a landing seeds the cell it falls in rather than its own exact spot,
     * so the nearest landing can sit most of a cell nearer than the cell centre
     * suggests. Rounding down means the number under-promises - the patch is at
     * least this empty - which is the honest direction for a figure someone is
     * about to fly across a world on.
     */
    private double reportedRadius(float chamfer) {
        return Math.max(0, toBlocks(chamfer) - cell * blocksPerPixel * 0.5);
    }

    private double toBlocks(float chamfer) {
        if (chamfer >= UNREACHED) {
            return 0;
        }
        return chamfer / STEP * cell * blocksPerPixel;
    }

    private static int tileOf(double world, int tile) {
        return Math.floorDiv((int) Math.floor(world), tile);
    }

}
