package dev.rtpbuddy.stats;

import dev.rtpbuddy.data.RtpSample;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Buckets samples into square world cells. Backs the heatmap render mode and the
 * coverage figure ("how much of the reachable area have I actually landed in").
 */
public class DensityGrid {

    /** Packed cell coordinate. */
    public record Cell(int cx, int cz) {
    }

    private final int cellSize;
    private final Map<Cell, Integer> counts = new HashMap<>();
    private int peak;
    private int total;

    public DensityGrid(int cellSize) {
        this.cellSize = Math.max(1, cellSize);
    }

    public static DensityGrid of(List<RtpSample> samples, int cellSize) {
        DensityGrid grid = new DensityGrid(cellSize);
        for (RtpSample sample : samples) {
            grid.add(sample.x(), sample.z());
        }
        return grid;
    }

    public void add(double x, double z) {
        Cell cell = new Cell(Math.floorDiv((int) Math.floor(x), cellSize),
                Math.floorDiv((int) Math.floor(z), cellSize));
        int updated = counts.merge(cell, 1, Integer::sum);
        peak = Math.max(peak, updated);
        total++;
    }

    public int cellSize() {
        return cellSize;
    }

    public int peak() {
        return peak;
    }

    public int total() {
        return total;
    }

    public int occupiedCells() {
        return counts.size();
    }

    public Map<Cell, Integer> cells() {
        return counts;
    }

    public int countAt(int cx, int cz) {
        return counts.getOrDefault(new Cell(cx, cz), 0);
    }

    /** Cell fill as a 0..1 fraction of the busiest cell. */
    public double intensity(int count) {
        return peak == 0 ? 0.0 : (double) count / peak;
    }

    /**
     * Fraction of the cells inside the guard band that hold at least one sample.
     * Returns NaN when the guarded area is not a finite region.
     */
    public double coverage(double borderRadius, double borderGuard, boolean squareBorder) {
        double usable = borderRadius - borderGuard;
        if (!Double.isFinite(usable) || usable <= 0) {
            return Double.NaN;
        }
        double area = squareBorder
                ? (2 * usable) * (2 * usable)
                : Math.PI * usable * usable;
        double totalCells = area / ((double) cellSize * cellSize);
        if (totalCells < 1) {
            return Double.NaN;
        }
        return Math.min(1.0, counts.size() / totalCells);
    }
}
