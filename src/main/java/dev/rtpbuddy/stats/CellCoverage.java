package dev.rtpbuddy.stats;

import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;

import java.util.List;
import java.util.function.Function;

/**
 * How much of the server's cell grid a set of landings has actually reached.
 *
 * <p>The grid is finite - {@value #CELLS} cells - so unlike the density
 * coverage figure this one has a real denominator and a real finish line: 22 of
 * 81 is a sentence, 0.impossible% of the disc is not.
 *
 * <p>Built in one pass and then only read. Two callers need it and both would
 * otherwise walk the whole dataset on a hot path, so both go through a cached
 * snapshot: {@link #global(List, int)} keys on the store's revision, and the
 * per-filter figure rides along inside {@link SampleStats}, which is already
 * memoised per filter change.
 */
public final class CellCoverage {

    public static final int CELLS = ServerRegions.COLUMNS * ServerRegions.COLUMNS;

    /**
     * The grid itself, held once.
     *
     * <p>{@code ServerRegions.cells()} hands back a clone, which is right for
     * a caller that might keep it and wrong for anything that asks per frame.
     * Everything in this class and in the board that draws it reads this copy.
     */
    private static final ServerRegions.Cell[] CELLS_BY_NUMBER = ServerRegions.cells();

    /** Cells per zone, counted once from the layout rather than hard-coded. */
    private static final int[] ZONE_TOTALS = new int[ServerRegions.Zone.values().length];

    static {
        for (ServerRegions.Cell cell : CELLS_BY_NUMBER) {
            ZONE_TOTALS[cell.zone().ordinal()]++;
        }
    }

    /** The grid, in server-number order. Callers must not mutate it. */
    public static ServerRegions.Cell[] grid() {
        return CELLS_BY_NUMBER;
    }

    /**
     * How a landing is mapped onto a cell. The client points this at the config
     * so a singleplayer landing, or one on a server that has no grid, counts as
     * outside rather than being dropped onto whatever cell its coordinates
     * happen to fall in. The default keeps the class usable on its own.
     */
    private static Function<RtpSample, ServerRegions.Cell> resolver =
            sample -> "minecraft:overworld".equals(sample.dimension())
                    ? ServerRegions.cellAt(sample.x(), sample.z())
                    : null;

    public static void setResolver(Function<RtpSample, ServerRegions.Cell> lookup) {
        resolver = lookup == null
                ? sample -> ServerRegions.cellAt(sample.x(), sample.z())
                : lookup;
    }

    /** Landings per cell, indexed by cell number minus one. */
    private final int[] counts = new int[CELLS];
    private final int[] zoneHit = new int[ServerRegions.Zone.values().length];
    private final int[] zoneLandings = new int[ServerRegions.Zone.values().length];

    private final int hitCells;
    private final int onceCells;
    private final int counted;
    private final int outside;
    private final int busiestNumber;

    /**
     * Identity of the board's contents, so a drawer can tell whether the
     * picture it baked is still the right one.
     *
     * <p>Computed here, once, rather than asked for per frame: the whole reason
     * this class exists as a snapshot is that nothing about it should be
     * recomputed while a screen is open.
     */
    private final int signature;

    /**
     * The missing cell numbers of each zone, already joined.
     *
     * <p>Built here rather than on demand: the metrics column asks for this
     * line once per zone per frame, and building it there meant a cloned grid,
     * a list and a boxed Integer per missing cell, sixty times a second.
     */
    private final String[] missingText = new String[ServerRegions.Zone.values().length];

    private static CellCoverage globalCache;
    private static int globalRevision = Integer.MIN_VALUE;

    /**
     * The board over every stored landing, recomputed only when the store
     * changes.
     *
     * <p>Called once per landing at most, never per frame. The pass itself is
     * one integer increment per sample, but the rule that keeps this cheap is
     * the revision check, not the pass.
     */
    public static CellCoverage global(List<RtpSample> samples, int revision) {
        CellCoverage cached = globalCache;
        if (cached != null && globalRevision == revision) {
            return cached;
        }
        cached = of(samples);
        globalCache = cached;
        globalRevision = revision;
        return cached;
    }

    /** Drops the cached board. Only needed when the resolver itself changes. */
    public static void invalidate() {
        globalCache = null;
        globalRevision = Integer.MIN_VALUE;
    }

    public static CellCoverage of(List<RtpSample> samples) {
        return new CellCoverage(samples);
    }

    private CellCoverage(List<RtpSample> samples) {
        int outsideGrid = 0;
        // Indexed rather than for-each: this runs over the store's live view.
        for (int i = 0; i < samples.size(); i++) {
            ServerRegions.Cell cell = resolver.apply(samples.get(i));
            if (cell == null) {
                outsideGrid++;
                continue;
            }
            counts[cell.number() - 1]++;
            zoneLandings[cell.zone().ordinal()]++;
        }

        int hit = 0;
        int once = 0;
        int total = 0;
        int busiest = 0;
        int busiestCount = 0;
        for (ServerRegions.Cell cell : CELLS_BY_NUMBER) {
            int count = counts[cell.number() - 1];
            total += count;
            if (count == 0) {
                continue;
            }
            hit++;
            if (count == 1) {
                once++;
            }
            zoneHit[cell.zone().ordinal()]++;
            if (count > busiestCount) {
                busiestCount = count;
                busiest = cell.number();
            }
        }

        this.hitCells = hit;
        this.onceCells = once;
        this.counted = total;
        this.outside = outsideGrid;
        this.busiestNumber = busiest;

        int stamp = 17;
        for (int count : counts) {
            stamp = stamp * 31 + count;
        }
        this.signature = stamp;

        StringBuilder text = new StringBuilder();
        for (ServerRegions.Zone zone : ServerRegions.Zone.values()) {
            text.setLength(0);
            for (ServerRegions.Cell cell : CELLS_BY_NUMBER) {
                if (cell.zone() == zone && counts[cell.number() - 1] == 0) {
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    text.append(cell.number());
                }
            }
            missingText[zone.ordinal()] = text.toString();
        }
    }

    /** The zone's missing cell numbers as one string, "" when it is complete. */
    public String missingText(ServerRegions.Zone zone) {
        return missingText[zone.ordinal()];
    }

    /** Changes whenever any cell's count does. */
    public int signature() {
        return signature;
    }

    /** @param number the 1-based cell number */
    public int count(int number) {
        return number < 1 || number > CELLS ? 0 : counts[number - 1];
    }

    public boolean hit(int number) {
        return count(number) > 0;
    }

    public int hitCells() {
        return hitCells;
    }

    public static int totalCells() {
        return CELLS;
    }

    /** Cells reached exactly once - the thin ice in an otherwise filled zone. */
    public int onceCells() {
        return onceCells;
    }

    /** Landings that fell on the grid at all. */
    public int counted() {
        return counted;
    }

    /** Landings with no cell: another server, another dimension, off the grid. */
    public int outside() {
        return outside;
    }

    public int busiestNumber() {
        return busiestNumber;
    }

    public int busiestCount() {
        return count(busiestNumber);
    }

    public int hitIn(ServerRegions.Zone zone) {
        return zoneHit[zone.ordinal()];
    }

    public int landingsIn(ServerRegions.Zone zone) {
        return zoneLandings[zone.ordinal()];
    }

    public static int totalIn(ServerRegions.Zone zone) {
        return ZONE_TOTALS[zone.ordinal()];
    }

    public double fraction() {
        return hitCells / (double) CELLS;
    }

    /**
     * The cell numbers of a zone that hold no landing, in ascending order.
     *
     * <p>Allocates, so it belongs to the chat command and not to a screen. On a
     * render path use {@link #missingText(ServerRegions.Zone)}, which was built
     * with the board.
     */
    public java.util.List<Integer> missingIn(ServerRegions.Zone zone) {
        java.util.List<Integer> missing = new java.util.ArrayList<>();
        for (ServerRegions.Cell cell : CELLS_BY_NUMBER) {
            if (cell.zone() == zone && counts[cell.number() - 1] == 0) {
                missing.add(cell.number());
            }
        }
        return missing;
    }

    /**
     * Zones ordered for the breakdown: the ones being filled first, untouched
     * ones last. A zone with no landings at all is not a gap in the map, it is
     * a zone that was never asked for, and putting it under the others says so.
     */
    public java.util.List<ServerRegions.Zone> zonesByProgress() {
        java.util.List<ServerRegions.Zone> zones =
                new java.util.ArrayList<>(java.util.List.of(ServerRegions.Zone.values()));
        zones.sort((a, b) -> {
            boolean aTouched = zoneLandings[a.ordinal()] > 0;
            boolean bTouched = zoneLandings[b.ordinal()] > 0;
            if (aTouched != bTouched) {
                return aTouched ? -1 : 1;
            }
            return Integer.compare(zoneHit[b.ordinal()], zoneHit[a.ordinal()]);
        });
        return zones;
    }
}
