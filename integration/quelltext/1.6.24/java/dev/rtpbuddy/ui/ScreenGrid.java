package dev.rtpbuddy.ui;

import java.util.Arrays;

/**
 * A one-frame record of which patches of the canvas are already spoken for,
 * and how many markers wanted each one.
 *
 * <p>Two jobs on the map, both the same shape of problem. Sample numbers may
 * only be written once per label-sized patch, or a cluster stacks two hundred of
 * them into a grey smear. Markers past a certain density may only be drawn once
 * per marker-sized patch, because past that point the extra ones land underneath
 * a marker that is already there and cost a rectangle to draw nothing.
 *
 * <p>The counts are what keeps the cheap drawing honest: a marker standing in
 * for twenty others sits inside a blob and can be a square, while one alone in
 * its patch is a dot somebody is actually looking at and keeps its round shape.
 *
 * <p>Deliberately reused arrays rather than a {@code Set}. The obvious version
 * keys a {@code HashSet<Long>} on the patch, which boxes a Long per marker per
 * frame - two thousand allocations a frame to answer a question about a grid
 * that could be an array index. Clearing is two {@code Arrays.fill} calls and
 * the arrays only grow when the canvas does.
 */
final class ScreenGrid {

    private short[] counts = new short[0];
    private boolean[] used = new boolean[0];
    private int columns;
    private int rows;
    private int step = 1;
    private int originX;
    private int originY;

    /**
     * Readies the grid for one frame.
     *
     * @param step patch size in pixels; anything below 1 is treated as 1
     */
    void reset(int x, int y, int width, int height, int step) {
        this.step = Math.max(1, step);
        this.originX = x;
        this.originY = y;
        // Two spare rows and columns: a marker may sit slightly outside the
        // canvas and still be drawn, and it must not fold onto a patch inside.
        this.columns = Math.max(1, width / this.step + 3);
        this.rows = Math.max(1, height / this.step + 3);
        int needed = columns * rows;
        if (counts.length < needed) {
            counts = new short[needed];
            used = new boolean[needed];
        } else {
            Arrays.fill(counts, 0, needed, (short) 0);
            Arrays.fill(used, 0, needed, false);
        }
    }

    /** Clears only the draw claims, so a counted frame can be replayed. */
    void clearClaims() {
        Arrays.fill(used, 0, Math.min(used.length, columns * rows), false);
    }

    /** Records that a marker wants this patch, without claiming it for drawing. */
    void tally(double x, double y) {
        int index = indexOf(x, y);
        if (index >= 0 && counts[index] < Short.MAX_VALUE) {
            counts[index]++;
        }
    }

    /**
     * Claims the patch holding a point for drawing.
     *
     * @return true when the patch was free, false when something already owns it
     */
    boolean claim(double x, double y) {
        int index = indexOf(x, y);
        if (index < 0) {
            // Off the grid entirely: never merged with anything, always drawn.
            return true;
        }
        if (used[index]) {
            return false;
        }
        used[index] = true;
        return true;
    }

    /** How many markers wanted this patch during the counting pass. */
    int count(double x, double y) {
        int index = indexOf(x, y);
        return index < 0 ? 1 : counts[index];
    }

    private int indexOf(double x, double y) {
        int column = Math.floorDiv((int) Math.floor(x) - originX, step) + 1;
        int row = Math.floorDiv((int) Math.floor(y) - originY, step) + 1;
        if (column < 0 || row < 0 || column >= columns || row >= rows) {
            return -1;
        }
        return row * columns + column;
    }
}
