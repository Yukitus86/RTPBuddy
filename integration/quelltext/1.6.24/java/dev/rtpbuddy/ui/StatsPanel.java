package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.stats.SampleStats;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import dev.rtpbuddy.util.Worlds;
import net.minecraft.client.gui.DrawContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The scrolling metrics column. Everything is precomputed in
 * {@link SampleStats}; this class only lays it out.
 */
public class StatsPanel {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** How many single biomes the breakdown lists before it stops. */
    private static final int BIOME_ROWS = 8;

    /**
     * The band of the column that is actually on screen this frame.
     *
     * <p>The column is laid out as one long strip and scrolled with a scissor,
     * so without this every row is drawn whether or not it can be seen - and
     * the strip runs to well over a hundred rows once the biome breakdowns and
     * the cell board are on it. Each row is a handful of rectangles and two
     * pieces of text, and on 1.21.11 every rectangle allocates twice, so the
     * rows below the fold were costing as much as the ones being read.
     *
     * <p>The cursor still advances through them: the skipped rows have to keep
     * their height or the scrollbar would not know how far the column runs.
     */
    private int clipTop;
    private int clipBottom;

    private boolean onScreen(int y, int height) {
        return y + height >= clipTop && y <= clipBottom;
    }

    private int clippedRow(DrawContext context, String label, String value,
                           int x, int y, int width) {
        if (!onScreen(y, UiDraw.lineHeight())) {
            return y + UiDraw.lineHeight() + 2;
        }
        return UiDraw.row(context, label, value, x, y, width);
    }

    private int clippedBar(DrawContext context, String label, double fraction, String value,
                           int x, int y, int width, int color) {
        if (!onScreen(y, UiDraw.lineHeight() + 6)) {
            return y + UiDraw.font().fontHeight + 7;
        }
        return UiDraw.bar(context, label, fraction, value, x, y, width, color);
    }

    private int clippedHeading(DrawContext context, String label, int x, int y, int width) {
        if (!onScreen(y, UiDraw.lineHeight() + 3)) {
            return y + UiDraw.lineHeight() + 3;
        }
        return UiDraw.heading(context, label, x, y, width);
    }

    private int clippedHistogram(DrawContext context, dev.rtpbuddy.stats.Histogram histogram,
                                 int x, int y, int width, int height, int color) {
        if (!onScreen(y, height + 2)) {
            return histogram.buckets() == 0 ? y : y + height + 2;
        }
        return UiDraw.histogram(context, histogram, x, y, width, height, color);
    }

    private int clippedRose(DrawContext context, dev.rtpbuddy.stats.Histogram compass,
                            int x, int y, int size, int color) {
        if (!onScreen(y, size)) {
            return y + size + 4;
        }
        return UiDraw.rose(context, compass, x, y, size, color);
    }

    private void clippedText(DrawContext context, String value, int x, int y, int color) {
        if (onScreen(y, UiDraw.lineHeight())) {
            UiDraw.text(context, value, x, y, color);
        }
    }

    private int contentHeight;

    public int contentHeight() {
        return contentHeight;
    }

    /**
     * @param scroll pixels scrolled down
     * @return the drawn content height, for scroll clamping
     */
    public int render(DrawContext context, int x, int y, int width, int height,
                      SampleStats stats, int scroll, String subtitle) {
        return render(context, x, y, width, height, stats, scroll, subtitle, java.util.List.of());
    }

    /**
     * @param holes the gap map's ranked holes, empty in every other marker mode
     */
    public int render(DrawContext context, int x, int y, int width, int height,
                      SampleStats stats, int scroll, String subtitle,
                      java.util.List<dev.rtpbuddy.stats.GapField.Hole> holes) {
        UiDraw.panel(context, x, y, width, height);
        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        clipTop = y;
        clipBottom = y + height;

        int innerX = x + 8;
        int innerWidth = width - 16;
        int cursor = y + 8 - scroll;

        cursor = clippedHeading(context, Lang.t("stats.overview"), innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.samples"), String.valueOf(stats.count),
                innerX, cursor, innerWidth);
        if (subtitle != null && !subtitle.isBlank()) {
            cursor = clippedRow(context, Lang.t("stats.scope"), UiDraw.trim(subtitle, innerWidth / 2),
                    innerX, cursor, innerWidth);
        }

        // Above the metrics rather than below them: in gap mode this list is
        // what the player opened the map for, and it is a short list.
        if (!holes.isEmpty()) {
            cursor += 4;
            cursor = clippedHeading(context, Lang.t("map.gaps.title"), innerX, cursor, innerWidth);
            int number = 1;
            for (dev.rtpbuddy.stats.GapField.Hole hole : holes) {
                cursor = clippedRow(context, "#" + number,
                        Numbers.compact(hole.x()) + " / " + Numbers.compact(hole.z()),
                        innerX, cursor, innerWidth);
                clippedText(context, Lang.t("map.gaps.radius", Numbers.compact(hole.radius())),
                        innerX + 6, cursor, MapPalette.TEXT_DIM);
                cursor += UiDraw.lineHeight();
                number++;
            }
            cursor += 4;
        }

        if (stats.count == 0) {
            clippedText(context, Lang.t("stats.empty"), innerX, cursor + 4, MapPalette.TEXT_DIM);
            context.disableScissor();
            contentHeight = cursor + 20 - (y - scroll);
            return contentHeight;
        }

        cursor = clippedRow(context, Lang.t("stats.first"),
                TIME.format(Instant.ofEpochMilli(stats.firstTimestamp)), innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.last"),
                TIME.format(Instant.ofEpochMilli(stats.lastTimestamp)), innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.span"), Numbers.duration(stats.spanMillis),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.rate"),
                Lang.t("stats.rate_value", Numbers.fixed(stats.samplesPerMinute, 2)),
                innerX, cursor, innerWidth);
        if (stats.latencySamples > 0) {
            cursor = clippedRow(context, Lang.t("stats.mean_latency"),
                    Lang.t("stats.latency_value", Math.round(stats.meanLatencyMillis), stats.latencySamples),
                    innerX, cursor, innerWidth);
        }
        cursor += 4;

        cursor = renderCells(context, stats, innerX, cursor, innerWidth);

        cursor = clippedHeading(context, Lang.t("stats.distance"), innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.min"), Numbers.compact(stats.distMin),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.median"), Numbers.compact(stats.distMedian),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.mean"), Numbers.compact(stats.distMean),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.p90"), Numbers.compact(stats.distP90),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.max"), Numbers.compact(stats.distMax),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.stddev"), Numbers.compact(stats.distStdDev),
                innerX, cursor, innerWidth);
        cursor += 2;
        cursor = clippedHistogram(context, stats.radial, innerX, cursor, innerWidth, 34, 0xFF4E9BD1);
        clippedText(context, UiDraw.trim(Lang.t("stats.rings"), innerWidth),
                innerX, cursor, MapPalette.TEXT_DIM);
        cursor += UiDraw.lineHeight() + 4;

        if (stats.jumpSamples > 0) {
            cursor = clippedHeading(context, Lang.t("stats.jump"), innerX, cursor, innerWidth);
            cursor = clippedRow(context, Lang.t("stats.min"), Numbers.compact(stats.jumpMin),
                    innerX, cursor, innerWidth);
            cursor = clippedRow(context, Lang.t("stats.mean"), Numbers.compact(stats.jumpMean),
                    innerX, cursor, innerWidth);
            cursor = clippedRow(context, Lang.t("stats.max"), Numbers.compact(stats.jumpMax),
                    innerX, cursor, innerWidth);
            cursor += 4;
        }

        cursor = clippedHeading(context, Lang.t("stats.spread"), innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.x_range"),
                Lang.t("stats.range_value", Math.round(stats.minX), Math.round(stats.maxX)),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.z_range"),
                Lang.t("stats.range_value", Math.round(stats.minZ), Math.round(stats.maxZ)),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.box"),
                Lang.t("stats.box_value", Numbers.compact(stats.width()), Numbers.compact(stats.depth())),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.centroid"),
                Lang.t("stats.centroid_value", Math.round(stats.centroidX), Math.round(stats.centroidZ)),
                innerX, cursor, innerWidth);
        if (!Double.isNaN(stats.coverage)) {
            cursor = clippedRow(context, Lang.t("stats.coverage"),
                    Lang.t("stats.coverage_value", Numbers.fixed(stats.coverage * 100, 3),
                            stats.density.occupiedCells()),
                    innerX, cursor, innerWidth);
        }
        cursor += 4;

        cursor = clippedHeading(context, Lang.t("stats.quadrants"), innerX, cursor, innerWidth);
        String[] quadrantKeys = {"stats.ne", "stats.nw", "stats.se", "stats.sw"};
        for (int i = 0; i < 4; i++) {
            cursor = clippedBar(context, Lang.t(quadrantKeys[i]), stats.quadrants[i] / (double) stats.count,
                    Lang.t("stats.share", stats.quadrants[i], Numbers.fixed(stats.quadrantShare(i), 1)),
                    innerX, cursor, innerWidth, 0xFF6BC5F0);
        }
        cursor += 4;

        cursor = clippedHeading(context, Lang.t("stats.direction"), innerX, cursor, innerWidth);
        cursor = clippedRose(context, stats.angular, innerX, cursor, Math.min(innerWidth, 92), 0xFF8FE36B);
        cursor += 4;

        cursor = clippedHeading(context, Lang.t("stats.y_levels"), innerX, cursor, innerWidth);
        cursor = clippedHistogram(context, stats.yLevels, innerX, cursor, innerWidth, 28, 0xFFF0A76B);
        cursor += 4;

        cursor = section(context, Lang.t("stats.dimensions"), stats.byDimension, stats.count,
                innerX, cursor, innerWidth, Worlds::dimensionLabel, Worlds::dimensionColor);
        // Regions carry the same colour here as their dots on the map, which is
        // what makes the breakdown readable as a legend for the plot.
        cursor = section(context, Lang.t("stats.regions"), stats.byRegion, stats.count,
                innerX, cursor, innerWidth,
                id -> RTPBuddyClient.config().regionLabel(id),
                id -> RTPBuddyClient.config().regionColor(id));
        // Families above single biomes: the families are the legend the map is
        // actually coloured by, and the biome list under them is the detail.
        cursor = section(context, Lang.t("stats.biome_families"), stats.byBiomeFamily, stats.count,
                innerX, cursor, innerWidth,
                name -> dev.rtpbuddy.util.Biomes.Family.valueOf(name).label(),
                name -> dev.rtpbuddy.util.Biomes.Family.valueOf(name).color());
        cursor = cappedSection(context, Lang.t("stats.biomes"), stats.byBiome, stats.count,
                innerX, cursor, innerWidth, BIOME_ROWS,
                dev.rtpbuddy.util.Biomes::label, dev.rtpbuddy.util.Biomes::color);
        cursor = section(context, Lang.t("stats.capture_mode"), stats.byCaptureMode, stats.count,
                innerX, cursor, innerWidth, id -> id, id -> 0xFF6BC5F0);
        if (stats.byServer.size() > 1) {
            cursor = section(context, Lang.t("stats.servers"), stats.byServer, stats.count,
                    innerX, cursor, innerWidth, id -> id, id -> 0xFF6BC5F0);
        }

        cursor = clippedHeading(context, Lang.t("stats.uniformity"), innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.radial_chi"),
                Lang.t("stats.chi_value", Numbers.fixed(stats.radialChiSquare, 1), 11),
                innerX, cursor, innerWidth);
        cursor = clippedRow(context, Lang.t("stats.angular_chi"),
                Lang.t("stats.chi_value", Numbers.fixed(stats.angularChiSquare, 1), 15),
                innerX, cursor, innerWidth);
        clippedText(context, UiDraw.trim(Lang.t(stats.uniformityVerdictKey()), innerWidth),
                innerX, cursor, MapPalette.TEXT_ACCENT);
        cursor += UiDraw.lineHeight() + 4;

        if (stats.pairwiseComputed) {
            cursor = clippedHeading(context, Lang.t("stats.extremes"), innerX, cursor, innerWidth);
            cursor = clippedRow(context, Lang.t("stats.closest"),
                    Lang.t("stats.pair_value", stats.nearestPairA, stats.nearestPairB,
                            Numbers.compact(stats.nearestPairDistance)),
                    innerX, cursor, innerWidth);
            cursor = clippedRow(context, Lang.t("stats.furthest"),
                    Lang.t("stats.pair_value", stats.farthestPairA, stats.farthestPairB,
                            Numbers.compact(stats.farthestPairDistance)),
                    innerX, cursor, innerWidth);
        } else if (stats.count >= 2) {
            cursor = clippedHeading(context, Lang.t("stats.extremes"), innerX, cursor, innerWidth);
            clippedText(context, UiDraw.trim(Lang.t("stats.pairs_skipped"), innerWidth),
                    innerX, cursor, MapPalette.TEXT_DIM);
            cursor += UiDraw.lineHeight();
        }

        context.disableScissor();
        contentHeight = cursor + 8 - (y - scroll);
        return contentHeight;
    }

    /**
     * The cell board and the per-zone progress under it.
     *
     * <p>Zones with no landings at all sit at the bottom and are drawn dim: a
     * zone that was never requested is not a gap in the map, and reading it as
     * one would put fifty-odd cells on the to-do list that nothing has ever
     * tried to reach.
     */
    private int renderCells(DrawContext context, SampleStats stats,
                            int x, int y, int width) {
        if (!RTPBuddyClient.config().map.showCellBoard) {
            return y;
        }
        dev.rtpbuddy.stats.CellCoverage cells = stats.cells;
        int cursor = clippedHeading(context, Lang.t("stats.cells"), x, y, width);
        // The board is one quad and up to eighty numbers; scrolled past, it is
        // eighty pieces of text nobody can see.
        int boardSize = CellBoard.height(width);
        if (onScreen(cursor, boardSize)) {
            cursor = CellBoard.render(context, cells, x, cursor, width,
                    RTPBuddyClient.config().map.cellBoardNumbers);
        } else {
            cursor += boardSize + 3;
        }
        clippedText(context, UiDraw.trim(CellBoard.summary(cells), width),
                x, cursor, MapPalette.TEXT_DIM);
        cursor += UiDraw.lineHeight() + 2;

        if (cells.counted() == 0) {
            clippedText(context, UiDraw.trim(Lang.t("stats.cells_none"), width),
                    x, cursor, MapPalette.TEXT_DIM);
            return cursor + UiDraw.lineHeight() + 4;
        }

        for (dev.rtpbuddy.region.ServerRegions.Zone zone : cells.zonesByProgress()) {
            int hit = cells.hitIn(zone);
            int all = dev.rtpbuddy.stats.CellCoverage.totalIn(zone);
            boolean touched = cells.landingsIn(zone) > 0;
            cursor = clippedBar(context, UiDraw.trim(zone.label(), width - 44),
                    touched ? hit / (double) all : 0.0,
                    Lang.t("stats.cells_of", hit, all),
                    x, cursor, width,
                    touched ? zone.color() : MapPalette.withAlpha(zone.color(), 0.35));
            if (touched && hit < all) {
                // Already joined when the board was built - see missingText.
                clippedText(context, UiDraw.trim(
                                Lang.t("stats.cells_missing", cells.missingText(zone)), width),
                        x + 6, cursor, MapPalette.TEXT_DIM);
                cursor += UiDraw.lineHeight();
            }
        }
        return cursor + 4;
    }

    /**
     * A breakdown with a floor under it: the recorded data holds close to fifty
     * biomes, and a column that lists every one of them is a column nobody
     * scrolls to the bottom of.
     */
    private int cappedSection(DrawContext context, String title, Map<String, Integer> counts,
                              int total, int x, int y, int width, int rows,
                              Function<String, String> labelOf, ToIntFunction<String> colorOf) {
        if (counts.isEmpty()) {
            return y;
        }
        int cursor = clippedHeading(context, title, x, y, width);
        int drawn = 0;
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (drawn == rows) {
                break;
            }
            cursor = clippedBar(context, UiDraw.trim(labelOf.apply(entry.getKey()), width - 40),
                    entry.getValue() / (double) total,
                    Lang.t("stats.share", entry.getValue(),
                            Numbers.fixed(100.0 * entry.getValue() / total, 1)),
                    x, cursor, width, colorOf.applyAsInt(entry.getKey()));
            shown += entry.getValue();
            drawn++;
        }
        if (counts.size() > rows) {
            clippedText(context, UiDraw.trim(Lang.t("stats.more_rows",
                    counts.size() - rows, total - shown), width), x + 6, cursor,
                    MapPalette.TEXT_DIM);
            cursor += UiDraw.lineHeight();
        }
        return cursor + 4;
    }

    private int section(DrawContext context, String title, Map<String, Integer> counts, int total,
                        int x, int y, int width,
                        Function<String, String> labelOf, ToIntFunction<String> colorOf) {
        if (counts.isEmpty()) {
            return y;
        }
        int cursor = clippedHeading(context, title, x, y, width);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String label = labelOf.apply(entry.getKey());
            int color = colorOf.applyAsInt(entry.getKey());
            cursor = clippedBar(context, UiDraw.trim(label, width - 40), entry.getValue() / (double) total,
                    Lang.t("stats.share", entry.getValue(), Numbers.fixed(100.0 * entry.getValue() / total, 1)),
                    x, cursor, width, color);
        }
        return cursor + 4;
    }
}
