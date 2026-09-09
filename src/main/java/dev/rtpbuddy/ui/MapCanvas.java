package dev.rtpbuddy.ui;

import dev.rtpbuddy.config.GuardSettings;
import dev.rtpbuddy.config.MapConfig;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.stats.GapField;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import dev.rtpbuddy.util.Worlds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The shared top-down plot used by both map screens: grid, guard overlays,
 * markers, path, the gap field, hit-testing and the scale bar.
 *
 * <p>Input handling lives in the screens; this class only projects and draws.
 */
public class MapCanvas {

    /** Fallback marker radius, used when the config holds nothing sensible. */
    private static final int MARKER_RADIUS = 2;

    /** Radii the config is allowed to ask for. */
    private static final int MIN_MARKER_RADIUS = 1;
    private static final int MAX_MARKER_RADIUS = 8;

    private static final int HIT_RADIUS = 6;

    /**
     * Above this many markers <em>on screen</em>, numbers are dropped.
     *
     * <p>Counted against what the canvas is actually about to draw, not against
     * the whole filtered set. Counting the set meant that passing the budget
     * once turned the numbers off everywhere and for good, however far you
     * zoomed in - two hundred landings spread over half a world have no crowding
     * problem at all. Zooming into a busy patch now brings the numbers back.</p>
     */
    private static final int LABEL_BUDGET = 250;

    /**
     * How many of the visible markers may share a label slot before the numbers
     * are dropped altogether. A fifth colliding already reads as a smear.
     */
    private static final int LABEL_CROWD_PERCENT = 20;

    /**
     * The scale bar reading at which numbers may start appearing, in blocks.
     * Above it the plot is a survey of where landings fell, not a list of which
     * ones they were, and the numbers only get in the way.
     */
    private static final double LABEL_MAX_SCALE_BLOCKS = 5_000;

    /**
     * The scale bar reading above which the gap map switches from shading to
     * counted tiles, in blocks.
     *
     * <p>Deliberately the same 5k landmark the sample numbers use, so the map
     * has one rule to remember rather than two: at 5k and closer you get the
     * detail, further out you get the survey. Shading is the better picture up
     * close, where a hole is a shape; further out a hole is a statistic, and
     * four counted bands say more than a smooth wash of amber that never
     * quite resolves into an edge.
     */
    private static final double GAP_RASTER_SCALE_BLOCKS = 5_000;

    /** Steps in the colour key's ramp strip. */
    private static final int GAP_KEY_STEPS = 16;

    /** Ring detail for a ranked hole. The ring marks a spot, it is not a shape. */
    private static final int HOLE_RING_SEGMENTS = 56;

    /**
     * Tile sizes the gap raster may snap to, in blocks.
     *
     * <p>Every one of them divides a server region cell, which is what keeps a
     * tile from straddling a region line. A cell is 50 000 blocks measured from
     * the border corner, so 8 000 - the old default - cut every cell into six
     * and a quarter and the leftover quarter hung over the line into the next
     * region. A size larger than a cell is no good either: two cells to a tile
     * puts the line between them straight through its middle. So the ladder
     * stops at one whole cell.
     */
    private static final double[] GAP_TILE_STEPS = {
            500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000
    };

    /**
     * The largest piece of ground one raster tile may stand for while the
     * scale bar reads {@link #GAP_TILE_HOLD_SCALE_BLOCKS} or less, in blocks.
     *
     * <p>Sizing every tile to about the same width on screen keeps the picture
     * looking the same at every zoom, and that is the problem: the square stays
     * put while the ground under it quadruples. A tile is a question - has
     * anything ever landed in this square - and the answer is worth much less
     * when the square grows to sixty-four thousand blocks a side. Held here
     * instead, zooming out shrinks the tiles rather than coarsening them.
     *
     * <p>This is the fallback for a config that names no size of its own;
     * {@link MapConfig#gapRasterTile} is what normally decides.
     */
    private static final double GAP_TILE_HOLD = 10_000;

    /**
     * The scale bar reading past which the tile is free to grow again.
     *
     * <p>Beyond this the held tile falls under three pixels and stops being
     * drawable at all, so out here a coarser square really is the only picture
     * left. In practice the whole world frames at about a third of this, so
     * the growth is the far end of the zoom rather than the normal case.
     */
    private static final double GAP_TILE_HOLD_SCALE_BLOCKS = 100_000;

    /**
     * Shading bands.
     *
     * <p>The picture goes to a texture, so the band count costs nothing at draw
     * time and only sets how smooth the ramp looks. Thirty-two is past the point
     * where the steps are visible on a screen.
     */
    private static final int GAP_BANDS = 32;

    /**
     * The ramp, resolved once.
     *
     * <p>The field is banded, so only a fixed set of colours can come out of it.
     * Asking the palette for one per cell meant three calls to {@code Math.pow}
     * per cell on a grid rebuilt whenever the view moved, which is half the
     * reason the first version of this mode heated up a CPU on a zoom.
     */
    private static final int[] GAP_PALETTE = gapPalette();

    /**
     * A rebuild this slow is not run on every frame of a zoom.
     *
     * <p>Small canvases stay exact, because there the rebuild is well under a
     * millisecond and there is nothing to gain by holding it back. Only when the
     * measured build crosses this does the shading start lagging the view by up
     * to one interval, which is a fair trade against the alternative of a map
     * that drops frames while it is being aimed.
     */
    private static final long GAP_SLOW_BUILD_NANOS = 2_500_000L;

    /** How long a stale field may be drawn while the view keeps changing. */
    private static final long GAP_STALE_MILLIS = 80;

    private static int[] gapPalette() {
        int[] palette = new int[GAP_BANDS + 1];
        for (int i = 0; i <= GAP_BANDS; i++) {
            palette[i] = MapPalette.gap(i / (double) GAP_BANDS);
        }
        return palette;
    }
    private static final int PATH_SEGMENT_BUDGET = 2_000;

    /** Direction chevrons in path mode: size, and the segment length that earns one. */
    private static final int ARROW_SIZE = 6;
    private static final int ARROW_MIN_SEGMENT = 26;

    /** The two arms of a chevron. Held once: it was allocated per segment. */
    private static final double[] ARROW_SPREAD = {2.6, -2.6};

    /**
     * Segments past which the route stops drawing chevrons.
     *
     * <p>Each chevron is two more rotated rectangles, so on a full route they
     * are two thirds of what the line costs - and on two thousand overlapping
     * segments they are not read as direction anyway, they are read as noise.
     * The line itself still fades from old to new, which is the same
     * information at none of the price.
     */
    private static final int ARROW_BUDGET = 400;

    private final MapViewState view = new MapViewState();

    private MarkerMode markerMode = MarkerMode.POINTS;
    private ColorMode colorMode = ColorMode.DIMENSION;

    private Function<RtpSample, Integer> sessionColorLookup = sample -> MapPalette.session(0);
    private Function<RtpSample, Integer> regionColorLookup =
            sample -> MapPalette.region(sample.requestedRegion());

    /** The 81 cells are fixed, so the array is built once rather than per frame. */
    private static final ServerRegions.Cell[] REGION_CELLS = ServerRegions.cells();

    /** Set by the screen: true only when the samples on show use the grid. */
    private boolean showServerRegions;

    /**
     * Marker shape for this frame, taken from the config in {@link #render}.
     *
     * <p>A field rather than a parameter because the markers are drawn from more
     * than one method and the shapes must not disagree inside one picture.
     */
    private boolean roundMarkers = true;

    /** Which dimensions' borders to draw. Set by the screen from the filter. */
    private List<String> borderDimensions = List.of(Worlds.OVERWORLD);

    /**
     * The gap field, kept between frames.
     *
     * <p>It carries its own shading as runs of equal colour: painting a full
     * canvas cell by cell is some thirty thousand calls to {@code fill} per
     * frame, which is the mistake this mode very nearly shipped with.
     * The field only changes when the view or the sample set does, and the
     * frames in between merely replay what it already holds.
     */
    private final GapField gapField = new GapField();

    /** The shading, held on the GPU so a frame costs one quad instead of thousands. */
    private final ArgbTexture gapTexture = new ArgbTexture("gap_field");

    /** Landing counts per raster tile, rebuilt with the field rather than per frame. */
    private final java.util.Map<Long, Integer> gapCounts = new java.util.HashMap<>();
    private double gapCountTile;

    private long gapSignature;
    private long gapBuiltAtMillis;
    private boolean gapDirty = true;

    /** One row of the colour key: what a colour on the plot stands for. */
    public record LegendEntry(int color, String label) {
    }

    private List<LegendEntry> legend = List.of();

    /**
     * Sets the colour key. Built by the screen when the filter or the colour mode
     * changes, not here: it needs the whole sample set and the config, and
     * rebuilding it per frame would walk every sample sixty times a second.
     */
    public void setLegend(List<LegendEntry> entries) {
        this.legend = entries;
    }

    public MapViewState view() {
        return view;
    }

    public MarkerMode markerMode() {
        return markerMode;
    }

    public void setMarkerMode(MarkerMode mode) {
        this.markerMode = mode;
    }

    public ColorMode colorMode() {
        return colorMode;
    }

    public void setColorMode(ColorMode mode) {
        this.colorMode = mode;
    }

    public void setSessionColorLookup(Function<RtpSample, Integer> lookup) {
        this.sessionColorLookup = lookup;
    }

    public void setRegionColorLookup(Function<RtpSample, Integer> lookup) {
        this.regionColorLookup = lookup;
    }

    public void setShowServerRegions(boolean show) {
        this.showServerRegions = show;
    }

    public void setBorderDimensions(List<String> dimensions) {
        this.borderDimensions = dimensions == null || dimensions.isEmpty()
                ? List.of(Worlds.OVERWORLD)
                : dimensions;
    }

    /**
     * Forces the gap field to be rebuilt on the next frame.
     *
     * <p>Called by the screen when the filter changes. The canvas notices a pan
     * or a zoom on its own, but it cannot see a filter that swaps the sample set
     * for another one of the same size.
     */
    public void invalidateGaps() {
        this.gapDirty = true;
    }

    /** The ranked holes from the last gap build, for the metrics column. */
    public java.util.List<GapField.Hole> gapHoles() {
        return markerMode == MarkerMode.GAPS ? gapField.holes() : java.util.List.of();
    }

    // ------------------------------------------------------------- rendering

    public void render(DrawContext context, List<RtpSample> samples, MapConfig config,
                       GuardSettings guards, int selectedSample, int hoveredIndex) {
        context.fill(view.boundsX(), view.boundsY(), view.right(), view.bottom(), MapPalette.BACKGROUND);
        context.enableScissor(view.boundsX(), view.boundsY(), view.right(), view.bottom());
        roundMarkers = config.roundMarkers;
        playerPulse = config.playerPulse;
        plotTextured = decidePlotTexture(samples, config);

        if (config.showGrid) {
            drawGrid(context);
        }
        if (config.showServerRegions) {
            drawServerRegions(context);
        }
        if (config.showBorder) {
            drawBorderGuard(context, guards);
        }
        if (config.showSpawnGuard) {
            drawSpawnGuard(context, guards);
        }

        switch (markerMode) {
            // The landings stay on the plot in gap mode: the shading says where
            // nothing was recorded, and that reads as a claim only when what
            // was recorded is visible next to it.
            case GAPS -> {
                drawGaps(context, samples, config, guards);
                drawMarkers(context, samples, config, selectedSample, hoveredIndex, true);
                drawGapHoles(context);
            }
            // Path is about the order the landings came in, so the line is the
            // subject: it carries an arrowhead per segment and the samples shrink
            // to hollow markers behind it. Points is about where they are, and
            // draws nothing but the dots - a line there made the two modes the
            // same picture and buried the points under it.
            case PATH -> {
                drawPath(context, samples, selectedSample);
                drawMarkers(context, samples, config, selectedSample, hoveredIndex, true);
            }
            // Points mode draws no route, but the two legs that touch a picked
            // landing are not the route - they are the answer to "where did I
            // come from and where did I go", which is worth the same two lines
            // here as it is on the path.
            case POINTS -> {
                drawSelectedLegs(context, samples, selectedSample);
                drawMarkers(context, samples, config, selectedSample, hoveredIndex, false);
            }
        }

        if (config.showLastLeg) {
            drawLastLeg(context, samples);
        }
        if (config.showOrigin) {
            drawCross(context, 0, 0, 5, MapPalette.ORIGIN);
        }
        if (config.showPlayer) {
            drawPlayer(context);
        }

        context.disableScissor();

        if (config.showScaleBar) {
            drawScaleBar(context);
        }
        if (config.showLegend) {
            if (markerMode == MarkerMode.GAPS) {
                drawGapKey(context);
            } else {
                drawLegend(context);
            }
        }
    }

    /**
     * The colour key, in the top right of the plot. Without it the colour mode
     * button changes the picture and says nothing about what the new colours
     * mean - which is most of the point of colouring by region.
     */
    private void drawLegend(DrawContext context) {
        if (legend.isEmpty()) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int lineHeight = font.fontHeight + 3;
        String title = colorMode.label();

        int textWidth = font.getWidth(title);
        for (LegendEntry entry : legend) {
            textWidth = Math.max(textWidth, font.getWidth(entry.label()) + 12);
        }
        int width = Math.min(textWidth + 14, view.boundsWidth() - 16);
        int height = lineHeight * (legend.size() + 1) + 8;
        if (width < 40 || height > view.boundsHeight() - 16) {
            return;
        }

        // Below the top edge labels, which the grid has already drawn there.
        int x = view.right() - width - 6;
        int y = view.boundsY() + 14;
        Theme.roundRect(context, x, y, width, height, Theme.RADIUS_CONTROL, 0xE0171B21);
        Theme.roundOutline(context, x, y, width, height, Theme.RADIUS_CONTROL, 0x60000000, 0x00000000);

        context.drawText(font, UiDraw.trim(title, width - 10), x + 6, y + 5,
                MapPalette.TEXT_DIM, false);
        int rowY = y + 5 + lineHeight;
        for (LegendEntry entry : legend) {
            Theme.roundRect(context, x + 6, rowY + 1, 7, 7, 2, entry.color());
            context.drawText(font, UiDraw.trim(entry.label(), width - 22), x + 17, rowY,
                    MapPalette.TEXT, false);
            rowY += lineHeight;
        }
    }

    // ------------------------------------------------------------------ grid

    private void drawGrid(DrawContext context) {
        double step = chooseGridStep();
        double[] bounds = view.visibleWorldBounds();

        double startX = Math.floor(bounds[0] / step) * step;
        for (double wx = startX; wx <= bounds[2]; wx += step) {
            int sx = (int) Math.round(view.worldToScreenX(wx));
            boolean axis = Math.abs(wx) < step / 2;
            boolean major = Math.abs(Math.IEEEremainder(wx, step * 5)) < step / 2;
            context.fill(sx, view.boundsY(), sx + 1, view.bottom(),
                    axis ? MapPalette.AXIS : (major ? MapPalette.GRID_MAJOR : MapPalette.GRID_MINOR));
        }

        double startZ = Math.floor(bounds[1] / step) * step;
        for (double wz = startZ; wz <= bounds[3]; wz += step) {
            int sy = (int) Math.round(view.worldToScreenY(wz));
            boolean axis = Math.abs(wz) < step / 2;
            boolean major = Math.abs(Math.IEEEremainder(wz, step * 5)) < step / 2;
            context.fill(view.boundsX(), sy, view.right(), sy + 1,
                    axis ? MapPalette.AXIS : (major ? MapPalette.GRID_MAJOR : MapPalette.GRID_MINOR));
        }

        drawGridLabels(context, step, bounds);
    }

    /**
     * One number per grid line.
     *
     * <p>This used to label only every fifth line, which is a spacing of some
     * 450 virtual pixels - wider than the canvas at any normal GUI scale. The
     * result was that exactly one label ever fitted on each edge, and since the
     * view starts centred on the origin that label read "0" whatever the zoom
     * was doing.
     */
    private void drawGridLabels(DrawContext context, double step, double[] bounds) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        double labelStep = step;

        double startX = Math.floor(bounds[0] / labelStep) * labelStep;
        for (double wx = startX; wx <= bounds[2]; wx += labelStep) {
            int sx = (int) Math.round(view.worldToScreenX(wx));
            if (sx < view.boundsX() + 2 || sx > view.right() - 24) {
                continue;
            }
            context.drawText(font, Numbers.compact(wx), sx + 2, view.boundsY() + 2, MapPalette.TEXT_DIM, false);
        }

        double startZ = Math.floor(bounds[1] / labelStep) * labelStep;
        for (double wz = startZ; wz <= bounds[3]; wz += labelStep) {
            int sy = (int) Math.round(view.worldToScreenY(wz));
            if (sy < view.boundsY() + 12 || sy > view.bottom() - 10) {
                continue;
            }
            context.drawText(font, Numbers.compact(wz), view.boundsX() + 2, sy + 2, MapPalette.TEXT_DIM, false);
        }
    }

    /**
     * The host's server-region grid: one tinted cell per 50 000 blocks, in the
     * colour of the region that owns it, with the server number written in.
     *
     * <p>Drawn under the markers, so a landing's dot sits on the region it
     * belongs to and the two colours can be read against each other. Suppressed
     * below a few pixels per cell, where the tints would blur into one wash.
     */
    private void drawServerRegions(DrawContext context) {
        if (!showServerRegions) {
            return;
        }
        double cellPixels = ServerRegions.CELL_SIZE * view.zoom();
        if (cellPixels < 5) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        boolean labels = cellPixels >= 54;

        for (ServerRegions.Cell cell : REGION_CELLS) {
            double left = view.worldToScreenX(cell.minX());
            double top = view.worldToScreenY(cell.minZ());
            double right = left + cellPixels;
            double bottom = top + cellPixels;
            if (right < view.boundsX() || left > view.right()
                    || bottom < view.boundsY() || top > view.bottom()) {
                continue;
            }
            int x1 = (int) Math.max(view.boundsX(), Math.floor(left));
            int y1 = (int) Math.max(view.boundsY(), Math.floor(top));
            int x2 = (int) Math.min(view.right(), Math.ceil(right));
            int y2 = (int) Math.min(view.bottom(), Math.ceil(bottom));
            if (x2 <= x1 || y2 <= y1) {
                continue;
            }
            int color = cell.zone().color();
            context.fill(x1, y1, x2, y2, MapPalette.withAlpha(color, 0.10));
            // Only the cell's own edges, not the clamped ones, so a cell running
            // off the canvas does not draw a border along the canvas edge.
            if (left >= view.boundsX()) {
                context.fill(x1, y1, x1 + 1, y2, MapPalette.withAlpha(color, 0.45));
            }
            if (top >= view.boundsY()) {
                context.fill(x1, y1, x2, y1 + 1, MapPalette.withAlpha(color, 0.45));
            }

            if (labels) {
                String number = "#" + cell.number();
                context.drawText(font, number, x1 + 4, y1 + 3,
                        MapPalette.withAlpha(color, 0.95), false);
                if (cellPixels >= 96) {
                    context.drawText(font, cell.zone().label(), x1 + 4, y1 + 3 + font.fontHeight + 1,
                            MapPalette.withAlpha(color, 0.65), false);
                }
            }
        }
    }

    /** Picks a 1/2/5 x 10^n step whose on-screen spacing lands between 60 and 160 px. */
    private double chooseGridStep() {
        double target = 90.0 / view.zoom();
        double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(1e-6, target))));
        double normalised = target / magnitude;
        double factor;
        if (normalised <= 1.5) {
            factor = 1;
        } else if (normalised <= 3.5) {
            factor = 2;
        } else if (normalised <= 7.5) {
            factor = 5;
        } else {
            factor = 10;
        }
        return Math.max(1.0, factor * magnitude);
    }

    // -------------------------------------------------------------- overlays

    /**
     * One border per dimension on show, each in that dimension's own colour.
     *
     * <p>Servers rarely give the three dimensions the same border - DonutSMP's
     * overworld reaches 225 000 blocks, its nether nowhere near that - so a
     * single square drawn over all of them is wrong for two of the three. Each
     * gets its own rectangle, labelled, and dimensions that happen to share a
     * radius are drawn once.
     */
    private void drawBorderGuard(DrawContext context, GuardSettings guards) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        java.util.Set<Double> drawn = new java.util.HashSet<>();

        for (String dimension : borderDimensions) {
            double limit = guards.radiusFor(dimension) - guards.borderGuard;
            if (limit <= 0 || !Double.isFinite(limit) || !drawn.add(limit)) {
                continue;
            }
            int color = MapPalette.withAlpha(Worlds.dimensionColor(dimension), 0.65);
            if (guards.squareBorder) {
                drawWorldRect(context, guards.borderCenterX - limit, guards.borderCenterZ - limit,
                        guards.borderCenterX + limit, guards.borderCenterZ + limit, color);
            } else {
                drawWorldCircle(context, guards.borderCenterX, guards.borderCenterZ, limit, color);
            }

            // Names the line, so three nested squares are not a guessing game.
            int labelX = (int) Math.round(view.worldToScreenX(guards.borderCenterX - limit)) + 3;
            int labelY = (int) Math.round(view.worldToScreenY(guards.borderCenterZ - limit)) + 3;
            if (view.contains(labelX, labelY) && borderDimensions.size() > 1) {
                context.drawText(font, Worlds.dimensionLabel(dimension), labelX, labelY, color, false);
            }
        }
    }

    private void drawSpawnGuard(DrawContext context, GuardSettings guards) {
        if (guards.spawnGuard <= 0) {
            return;
        }
        drawWorldCircle(context, guards.spawnX, guards.spawnZ, guards.spawnGuard, MapPalette.SPAWN_GUARD);
    }

    /**
     * Where the player is standing, built to be found rather than merely drawn.
     *
     * <p>A seven-pixel dot with a thin tail is fine on an empty plot and
     * invisible on a full one - over a mat of route lines and a few hundred
     * landings it is one more small mark among many, and after a teleport it has
     * moved somewhere unknown, which is exactly when it needs to be found
     * fastest. So it is built out of things nothing else on the map does: a dark
     * collar that separates it from whatever it lands on, a ring rather than a
     * blob, a wide heading wedge, and a slow pulse that no static marker has.
     */
    private void drawPlayer(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        double px = client.player.getX();
        double pz = client.player.getZ();
        int sx = (int) Math.round(view.worldToScreenX(px));
        int sy = (int) Math.round(view.worldToScreenY(pz));

        if (playerPulse) {
            // One slow ripple. Motion is the only channel the rest of the plot
            // does not use, so it carries the eye without adding more ink.
            double phase = (System.currentTimeMillis() % PULSE_MILLIS) / (double) PULSE_MILLIS;
            double radius = PULSE_MIN + phase * (PULSE_MAX - PULSE_MIN);
            ringOutline(context, sx, sy, radius, radius - 2.0,
                    MapPalette.withAlpha(MapPalette.PLAYER, 0.55 * (1.0 - phase)));
        }

        // Heading first, so the dot covers where the stub starts.
        // Yaw 0 faces +Z (south), which is down on screen.
        //
        // A short thin stub and nothing more: the dot is what has to be found,
        // and an arrowhead on the end only competes with it while saying the
        // same thing. Drawn pixel by pixel rather than as a rotated band -
        // seven pixels cost nothing, and a rotated rectangle frays at the tip
        // because its end cap lands between screen pixels.
        double yaw = Math.toRadians(client.player.getYaw());
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        headingStub(context, sx, sy, dx, dz, MapPalette.PLAYER, PLAYER_COLLAR);

        dot(context, sx, sy, 6, true, PLAYER_COLLAR);
        dot(context, sx, sy, 5, true, MapPalette.PLAYER);
        dot(context, sx, sy, 2, true, PLAYER_CORE);
    }

    /** Length of the heading ray from the marker centre, in pixels. */
    private static final int HEADING_LENGTH = 12;

    /**
     * The heading stub: a single-pixel ray from the marker's centre outward,
     * with a dark pixel around it so it stays readable over a pale biome or a
     * route line. The disc drawn afterwards covers the inner two thirds, so
     * what shows is a short stub emerging from the ring.
     *
     * <p>One pixel per step along the dominant axis - a plain Bresenham ray.
     * Walking the line at fixed distances and rounding instead put two pixels
     * on some rows and none on others, which is what made the stub read as bent
     * and off-centre at every angle that was not square.
     */
    private static void headingStub(DrawContext context, int sx, int sy, double dx, double dz,
                                    int core, int halo) {
        int[] xs = new int[HEADING_LENGTH + 1];
        int[] ys = new int[HEADING_LENGTH + 1];
        int count;
        if (Math.abs(dx) >= Math.abs(dz)) {
            int step = dx >= 0 ? 1 : -1;
            double slope = dx == 0 ? 0 : dz / dx;
            count = (int) Math.round(Math.abs(dx) * HEADING_LENGTH) + 1;
            for (int i = 0; i < count; i++) {
                int offset = step * i;
                xs[i] = sx + offset;
                ys[i] = sy + (int) Math.round(offset * slope);
            }
        } else {
            int step = dz >= 0 ? 1 : -1;
            double slope = dz == 0 ? 0 : dx / dz;
            count = (int) Math.round(Math.abs(dz) * HEADING_LENGTH) + 1;
            for (int i = 0; i < count; i++) {
                int offset = step * i;
                ys[i] = sy + offset;
                xs[i] = sx + (int) Math.round(offset * slope);
            }
        }
        for (int i = 0; i < count; i++) {
            context.fill(xs[i] - 1, ys[i] - 1, xs[i] + 2, ys[i] + 2, halo);
        }
        for (int i = 0; i < count; i++) {
            context.fill(xs[i], ys[i], xs[i] + 1, ys[i] + 1, core);
        }
    }

    /**
     * A circular outline. {@code ring} above cannot do this: it paints the hole
     * over the disc, which needs an opaque colour to hide it, and a translucent
     * halo has none.
     */
    private static void ringOutline(DrawContext context, int cx, int cy,
                                    double outer, double inner, int color) {
        int limit = (int) Math.ceil(outer);
        for (int dy = -limit; dy <= limit; dy++) {
            double outerSpan = outer * outer - dy * dy;
            if (outerSpan <= 0) {
                continue;
            }
            int right = (int) Math.floor(Math.sqrt(outerSpan));
            double innerSpan = inner * inner - dy * dy;
            if (innerSpan <= 0) {
                context.fill(cx - right, cy + dy, cx + right + 1, cy + dy + 1, color);
                continue;
            }
            int hole = (int) Math.floor(Math.sqrt(innerSpan));
            context.fill(cx - right, cy + dy, cx - hole, cy + dy + 1, color);
            context.fill(cx + hole + 1, cy + dy, cx + right + 1, cy + dy + 1, color);
        }
    }

    // --------------------------------------------------------------- markers

    /**
     * Every landing on the plot, drawn once.
     *
     * <p>Two things here exist only because of how many landings that can be.
     * Past {@code map.densityBudget} markers on screen the plot is thinned to
     * one marker per marker-sized patch: the ones dropped were landing
     * underneath a marker already drawn, so the picture is the same and the
     * work is not. Past the budget again, after thinning, the shape drops from
     * a disc to a square - five rectangles to one - because at that density
     * nobody is reading the corners of a five-pixel dot.
     *
     * <p>Neither happens below the budget, so an ordinary sitting draws exactly
     * what it drew before. The picked and hovered landings are never thinned or
     * squared: they are the two the eye is actually on.
     */
    private void drawMarkers(DrawContext context, List<RtpSample> samples, MapConfig config,
                             int selectedSample, int hoveredIndex, boolean hollow) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        boolean labels = config.showSampleNumbers && labelsFit(samples);
        int base = markerRadius(config);

        if (plotTextured) {
            ensurePlotTexture(samples, config, selectedSample, hollow);
            plotTexture.draw(context, view.boundsX(), view.boundsY(),
                    view.boundsWidth(), view.boundsHeight());
            drawEmphasised(context, samples, config, selectedSample, hoveredIndex, labels, font);
            return;
        }

        int budget = MapConfig.clampDensityBudget(config.densityBudget);
        int patch = 2 * base + 1;
        int onScreen = 0;
        int kept = 0;
        if (budget > 0) {
            // One pass to find out what this frame is up against: how many
            // markers are on screen at all, and how many of them survive the
            // thinning. Both answers are needed before the first rectangle is
            // drawn, and neither can be guessed from the file size - two
            // thousand landings zoomed in on one of them is not a dense plot.
            markerSlots.reset(view.boundsX(), view.boundsY(),
                    view.boundsWidth(), view.boundsHeight(), patch);
            for (int i = 0; i < samples.size(); i++) {
                RtpSample sample = samples.get(i);
                double sxd = view.worldToScreenX(sample.x());
                double syd = view.worldToScreenY(sample.z());
                if (sxd < view.boundsX() - 16 || sxd > view.right() + 16
                        || syd < view.boundsY() - 16 || syd > view.bottom() + 16) {
                    continue;
                }
                onScreen++;
                markerSlots.tally(sxd, syd);
                if (markerSlots.claim(sxd, syd)) {
                    kept++;
                }
            }
        }
        boolean thin = budget > 0 && onScreen > budget;
        // Thinning cannot go below one marker per patch. When even that leaves
        // the plot over budget, the shape is what is left to give.
        boolean cheap = thin && kept > budget;

        if (thin) {
            // Only the claims are cleared, not the counts: the draw pass walks
            // the same samples in the same order, so the same marker wins each
            // patch, and it still needs to know how crowded that patch was.
            markerSlots.clearClaims();
        }
        if (labels) {
            labelSlots.reset(view.boundsX(), view.boundsY(),
                    view.boundsWidth(), view.boundsHeight(), LABEL_SLOT_W);
        }

        for (int i = 0; i < samples.size(); i++) {
            RtpSample sample = samples.get(i);
            double sxd = view.worldToScreenX(sample.x());
            double syd = view.worldToScreenY(sample.z());
            if (sxd < view.boundsX() - 16 || sxd > view.right() + 16
                    || syd < view.boundsY() - 16 || syd > view.bottom() + 16) {
                continue;
            }

            boolean selected = sample.sample() == selectedSample;
            boolean hovered = i == hoveredIndex;
            boolean emphasised = selected || hovered;

            if (thin && !emphasised && !markerSlots.claim(sxd, syd)) {
                continue;
            }

            int sx = (int) Math.round(sxd);
            int sy = (int) Math.round(syd);
            int color = colorOf(sample, i, samples.size());
            // Square only where squaring cannot be seen. A marker standing in
            // for a dozen others is one dot inside a blob; a marker alone in
            // its patch is a dot somebody is looking at, and keeps its shape.
            boolean round = roundMarkers
                    && (emphasised || !cheap || markerSlots.count(sxd, syd) <= 1);
            int radius = emphasised
                    ? base + 2
                    : (hollow ? Math.max(MIN_MARKER_RADIUS, base) : base);

            if (emphasised) {
                dot(context, sx, sy, radius + 1, roundMarkers,
                        selected ? MapPalette.HIGHLIGHT : MapPalette.SELECTION_BORDER);
            }
            // A hollow marker is two draws, which is the wrong trade on a plot
            // already dense enough to be squaring the shape off.
            if (hollow && !emphasised && round) {
                ring(context, sx, sy, radius, round, color, MapPalette.BACKGROUND);
            } else {
                dot(context, sx, sy, radius, round, color);
            }

            // One number per label-sized patch of screen. Without this a tight
            // cluster stacks two hundred numbers into a grey smear that hides
            // the very markers it is labelling.
            if (labels && labelSlots.claim(sxd, syd)) {
                context.drawText(font, String.valueOf(numberLookup.applyAsInt(sample)),
                        sx + radius + 3, sy - 4, MapPalette.TEXT_DIM, false);
            }
        }
    }

    /**
     * The picked and hovered landings, and the sample numbers.
     *
     * <p>Everything the baked picture cannot hold. The two emphasised markers
     * change with a click and with the mouse, so baking them would rebuild the
     * layer on every movement; the numbers need the font, which the plate has
     * no access to by design.
     */
    private void drawEmphasised(DrawContext context, List<RtpSample> samples, MapConfig config,
                                int selectedSample, int hoveredIndex, boolean labels,
                                TextRenderer font) {
        int base = markerRadius(config);
        if (labels) {
            labelSlots.reset(view.boundsX(), view.boundsY(),
                    view.boundsWidth(), view.boundsHeight(), LABEL_SLOT_W);
        }
        for (int i = 0; i < samples.size(); i++) {
            RtpSample sample = samples.get(i);
            boolean selected = sample.sample() == selectedSample;
            boolean hovered = i == hoveredIndex;
            if (!selected && !hovered && !labels) {
                continue;
            }
            double sxd = view.worldToScreenX(sample.x());
            double syd = view.worldToScreenY(sample.z());
            if (sxd < view.boundsX() - 16 || sxd > view.right() + 16
                    || syd < view.boundsY() - 16 || syd > view.bottom() + 16) {
                continue;
            }
            int sx = (int) Math.round(sxd);
            int sy = (int) Math.round(syd);
            int radius = base + 2;
            if (selected || hovered) {
                dot(context, sx, sy, radius + 1, roundMarkers,
                        selected ? MapPalette.HIGHLIGHT : MapPalette.SELECTION_BORDER);
                dot(context, sx, sy, radius, roundMarkers,
                        colorOf(sample, i, samples.size()));
            }
            if (labels && labelSlots.claim(sxd, syd)) {
                context.drawText(font, String.valueOf(numberLookup.applyAsInt(sample)),
                        sx + radius + 3, sy - 4, MapPalette.TEXT_DIM, false);
            }
        }
    }

    /**
     * Whether this frame is heavy enough to be worth a picture.
     *
     * <p>Same budget as the thinning it replaces, and the same reason for
     * counting rather than guessing: two thousand landings zoomed in on one of
     * them is not a dense plot and would only pay for a texture it does not
     * need.
     */
    private boolean decidePlotTexture(List<RtpSample> samples, MapConfig config) {
        int budget = MapConfig.clampDensityBudget(config.densityBudget);
        if (budget <= 0 || samples.size() <= budget) {
            return false;
        }
        if ((long) view.boundsWidth() * view.boundsHeight() > PLOT_MAX_PIXELS) {
            return false;
        }
        int onScreen = 0;
        for (int i = 0; i < samples.size(); i++) {
            RtpSample sample = samples.get(i);
            double sxd = view.worldToScreenX(sample.x());
            double syd = view.worldToScreenY(sample.z());
            if (sxd >= view.boundsX() - 16 && sxd <= view.right() + 16
                    && syd >= view.boundsY() - 16 && syd <= view.bottom() + 16) {
                onScreen++;
                if (onScreen > budget) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Repaints the layer, and only when something it depends on has moved. */
    private void ensurePlotTexture(List<RtpSample> samples, MapConfig config,
                                   int selectedSample, boolean hollow) {
        long signature = 1125899906842597L;
        signature = 31 * signature + Double.doubleToLongBits(view.zoom());
        signature = 31 * signature + Double.doubleToLongBits(view.screenToWorldX(view.boundsX()));
        signature = 31 * signature + Double.doubleToLongBits(view.screenToWorldZ(view.boundsY()));
        signature = 31 * signature + view.boundsWidth();
        signature = 31 * signature + view.boundsHeight();
        // The screen hands over a fresh list whenever the filter or the scope
        // changes, so its identity is the cheapest true test for "other data".
        signature = 31 * signature + System.identityHashCode(samples);
        signature = 31 * signature + samples.size();
        signature = 31 * signature + markerMode.ordinal();
        signature = 31 * signature + colorMode.ordinal();
        signature = 31 * signature + markerRadius(config);
        signature = 31 * signature + (roundMarkers ? 1 : 0);
        signature = 31 * signature + (hollow ? 2 : 0);
        // Not the hover: that changes with the mouse and is drawn on top. The
        // selection is baked out of the route, so it belongs here.
        signature = 31 * signature + selectedSample;
        if (signature == plotSignature && plotTexture.ready()) {
            return;
        }
        // A view that is still being dragged is allowed to outrun a rebuild
        // that has already proved expensive: the signature is deliberately left
        // stale, so the next frame past the interval picks the work up again
        // and the picture lands on the view the player actually stopped at.
        long now = System.currentTimeMillis();
        if (plotTexture.ready() && plotBuildNanos > PLOT_SLOW_BUILD_NANOS
                && now - plotBuiltAtMillis < PLOT_STALE_MILLIS) {
            return;
        }
        plotSignature = signature;
        plotBuiltAtMillis = now;
        long startedAt = System.nanoTime();

        int count = samples.size();
        if (plotColors.length < count) {
            plotColors = new int[count];
            plotRoute = new int[count];
        }
        for (int i = 0; i < count; i++) {
            plotColors[i] = colorOf(samples.get(i), i, count);
        }

        boolean route = markerMode == MarkerMode.PATH;
        if (route) {
            int segments = Math.min(count - 1, PATH_SEGMENT_BUDGET);
            java.util.Arrays.fill(plotRoute, 0, count, 0);
            for (int i = 1; i <= segments; i++) {
                RtpSample a = samples.get(i - 1);
                RtpSample b = samples.get(i);
                if (!a.dimension().equals(b.dimension())
                        || a.sample() == selectedSample || b.sample() == selectedSample) {
                    continue;
                }
                double age = count <= 1 ? 1.0 : (double) i / count;
                plotRoute[i] = MapPalette.withAlpha(MapPalette.PATH_LINE, 0.35 + 0.45 * age);
            }
        }

        int originX = view.boundsX();
        int originY = view.boundsY();
        plot.bake(view.boundsWidth(), view.boundsHeight(), samples, plotColors,
                route ? plotRoute : null,
                new PlotPlate.Projection() {
                    @Override
                    public double screenX(double worldX) {
                        return view.worldToScreenX(worldX) - originX;
                    }

                    @Override
                    public double screenZ(double worldZ) {
                        return view.worldToScreenY(worldZ) - originY;
                    }
                },
                hollow ? Math.max(MIN_MARKER_RADIUS, markerRadius(config)) : markerRadius(config),
                roundMarkers, hollow, route && count - 1 <= ARROW_BUDGET,
                MapPalette.BACKGROUND);
        plotTexture.update(plot.pixels(), plot.width(), plot.height());
        plotBuildNanos = System.nanoTime() - startedAt;
    }

    /** The route leg into or out of a landing, or -1 when there is none. */
    private int legIndex(List<RtpSample> samples, int selectedSample, boolean incoming) {
        if (selectedSample < 0) {
            return -1;
        }
        for (int i = 1; i < samples.size(); i++) {
            RtpSample a = samples.get(i - 1);
            RtpSample b = samples.get(i);
            if (!a.dimension().equals(b.dimension())) {
                continue;
            }
            if (incoming && b.sample() == selectedSample) {
                return i;
            }
            if (!incoming && a.sample() == selectedSample) {
                return i;
            }
        }
        return -1;
    }

    /** Patches already carrying a sample number, one frame at a time. */
    private final ScreenGrid labelSlots = new ScreenGrid();

    /** Patches already carrying a marker, used only on a dense plot. */
    private final ScreenGrid markerSlots = new ScreenGrid();

    // ------------------------------------------------------ the baked plot

    /**
     * The landings and the route as one picture, for when there are too many of
     * them to draw one rectangle at a time.
     *
     * <p>Decided per frame in {@link #render}: below the drawing budget nothing
     * here runs and the plot is drawn exactly as it always was. Above it the
     * layer is baked when something about it changes and every frame in between
     * is one quad - which is not only faster than the rectangles but a better
     * picture than the thinning that used to stand in for them, because in a
     * texture every landing can be round and the whole route can be there.
     */
    private final PlotPlate plot = new PlotPlate();
    private final ArgbTexture plotTexture = new ArgbTexture("plot");
    private long plotSignature = Long.MIN_VALUE;
    private boolean plotTextured;
    private int[] plotColors = new int[0];
    private int[] plotRoute = new int[0];
    private long plotBuildNanos;
    private long plotBuiltAtMillis;

    /**
     * Canvas area past which the picture stops being the cheaper answer.
     *
     * <p>The bake itself is under a millisecond on a normal window, but it also
     * has to be handed to the GPU a pixel at a time, and that cost grows with
     * the canvas rather than with the landings. On a very large window the
     * rectangles are the better trade again, so the plot falls back to drawing
     * and thinning them.
     */
    private static final int PLOT_MAX_PIXELS = 2_200_000;

    /** A build slower than this may be outrun by a view that is still moving. */
    private static final long PLOT_SLOW_BUILD_NANOS = 2_500_000L;

    /** How long a stale picture may be drawn while the view keeps changing. */
    private static final long PLOT_STALE_MILLIS = 80;

    /** Used by the crowding test that decides whether numbers are drawn at all. */
    private final ScreenGrid crowdSlots = new ScreenGrid();

    /** Width in pixels of the patch one number is allowed to own. */
    private static final int LABEL_SLOT_W = 22;

    /**
     * Whether the numbers are worth drawing at this zoom.
     *
     * <p>Two questions, answered in one pass over the visible markers: are there
     * few enough of them, and do they sit far enough apart for a number to land
     * beside one. The second is what "zoomed out" really means here - it is not
     * the zoom level that makes a number unreadable but the markers packing
     * closer together than a label is wide, and how far out that happens depends
     * entirely on how the landings are spread. Measuring it means the numbers
     * come back the moment there is room for them, on any map.
     *
     * <p>Markers sharing a label slot are counted rather than the labels merely
     * being dropped, because dropping most of them leaves an arbitrary scatter
     * of numbers that reads worse than none at all.
     */
    private boolean labelsFit(List<RtpSample> samples) {
        // Zoomed out past the 5k bar the answer is no, however the markers
        // happen to be spread: a handful of numbers scattered over half a world
        // is noise on a picture that is about coverage at that range.
        if (scaleBarSpan() > LABEL_MAX_SCALE_BLOCKS) {
            return false;
        }
        // Its own grid, not the one the drawing uses: this runs before that one
        // is set up, and the two must not share a frame's claims.
        crowdSlots.reset(view.boundsX(), view.boundsY(),
                view.boundsWidth(), view.boundsHeight(), LABEL_SLOT_W);
        int onScreen = 0;
        int crowded = 0;
        for (int i = 0; i < samples.size(); i++) {
            RtpSample sample = samples.get(i);
            double sx = view.worldToScreenX(sample.x());
            double sy = view.worldToScreenY(sample.z());
            if (sx < view.boundsX() - 16 || sx > view.right() + 16
                    || sy < view.boundsY() - 16 || sy > view.bottom() + 16) {
                continue;
            }
            onScreen++;
            if (!crowdSlots.claim(sx, sy)) {
                crowded++;
            }
            // Nothing below can change the answer once the plot is over budget,
            // and on a full map that saves most of this pass.
            if (onScreen > LABEL_BUDGET) {
                return false;
            }
        }
        if (onScreen == 0) {
            return false;
        }
        return crowded * 100 <= onScreen * LABEL_CROWD_PERCENT;
    }

    /**
     * The route between landings, in recording order, with a direction arrow on
     * every segment long enough to carry one.
     *
     * <p>One pixel, always. Thickness is in GUI units, so a 2 became four to six
     * screen pixels at the scales this is actually played at, and a few dozen
     * crossing landings turned the plot into a solid mat.
     */
    private void drawPath(DrawContext context, List<RtpSample> samples, int selectedSample) {
        int segments = Math.min(samples.size() - 1, PATH_SEGMENT_BUDGET);
        int thickness = 1;
        boolean chevrons = segments <= ARROW_BUDGET;
        if (plotTextured) {
            // The route is in the picture. Only the two legs that touch the
            // picked landing are drawn here, because those are wanted on top of
            // everything and change with a click rather than with the view.
            drawPathHighlight(context, samples, legIndex(samples, selectedSample, true),
                    MapPalette.PATH_IN);
            drawPathHighlight(context, samples, legIndex(samples, selectedSample, false),
                    MapPalette.PATH_OUT);
            return;
        }
        // The two segments that touch the picked landing are drawn last, on top
        // of everything else - inside a dense plot they would otherwise be
        // buried under later lines, which is exactly when they are wanted.
        int incoming = -1;
        int outgoing = -1;
        for (int i = 1; i <= segments; i++) {
            RtpSample a = samples.get(i - 1);
            RtpSample b = samples.get(i);
            if (!a.dimension().equals(b.dimension())) {
                continue;
            }
            if (selectedSample >= 0) {
                if (b.sample() == selectedSample) {
                    incoming = i;
                    continue;
                }
                if (a.sample() == selectedSample) {
                    outgoing = i;
                    continue;
                }
            }
            double ax = view.worldToScreenX(a.x());
            double ay = view.worldToScreenY(a.z());
            double bx = view.worldToScreenX(b.x());
            double by = view.worldToScreenY(b.z());
            // Older segments fade out so recording order reads at a glance.
            double age = samples.size() <= 1 ? 1.0 : (double) i / samples.size();
            int color = MapPalette.withAlpha(MapPalette.PATH_LINE, 0.35 + 0.45 * age);
            drawLine(context, ax, ay, bx, by, color, thickness);
            if (chevrons) {
                drawArrowHead(context, ax, ay, bx, by, color);
            }
        }
        // Where the player came from, and where they went next. Two colours say
        // it at a glance; the arrowheads still carry the direction.
        drawPathHighlight(context, samples, incoming, MapPalette.PATH_IN);
        drawPathHighlight(context, samples, outgoing, MapPalette.PATH_OUT);
    }

    /**
     * The leg into the picked landing and the leg out of it, and nothing else.
     *
     * <p>A leg that crosses dimensions is left out for the same reason the route
     * skips it: the two ends are not points on one plane, so a straight line
     * between them would be measuring nothing.
     */
    /**
     * The leg into the newest landing on the plot: where that teleport started.
     *
     * <p>Drawn from the sample's own recorded origin rather than from the sample
     * before it. The two are usually the same point and sometimes are not - you
     * walk, you fall, you take a portal - and only the recorded origin is a fact
     * about that teleport. It is the same red as the incoming leg of a picked
     * landing, because it is the same thing, and it is always on so that a map
     * left open while the loop runs answers "where did that one come from"
     * without a click.
     */
    private void drawLastLeg(DrawContext context, List<RtpSample> samples) {
        RtpSample newest = null;
        for (RtpSample sample : samples) {
            if (newest == null || sample.timestamp() > newest.timestamp()) {
                newest = sample;
            }
        }
        if (newest == null || newest.fromX() == null || newest.fromZ() == null) {
            return;
        }
        // A straight line between two different worlds measures nothing.
        if (newest.fromDimension() != null && !newest.fromDimension().equals(newest.dimension())) {
            return;
        }
        double ax = view.worldToScreenX(newest.fromX());
        double ay = view.worldToScreenY(newest.fromZ());
        double bx = view.worldToScreenX(newest.x());
        double by = view.worldToScreenY(newest.z());
        drawLine(context, ax, ay, bx, by, MapPalette.PATH_IN, 2);
        drawArrowHead(context, ax, ay, bx, by, MapPalette.PATH_IN);
        if (view.contains(ax, ay)) {
            ring(context, (int) Math.round(ax), (int) Math.round(ay), 3, roundMarkers,
                    MapPalette.PATH_IN, MapPalette.BACKGROUND);
        }
    }

    private void drawSelectedLegs(DrawContext context, List<RtpSample> samples, int selectedSample) {
        if (selectedSample < 0 || samples.size() < 2) {
            return;
        }
        int index = -1;
        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i).sample() == selectedSample) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        if (index > 0 && samples.get(index - 1).dimension().equals(samples.get(index).dimension())) {
            drawPathHighlight(context, samples, index, MapPalette.PATH_IN);
        }
        if (index + 1 < samples.size()
                && samples.get(index).dimension().equals(samples.get(index + 1).dimension())) {
            drawPathHighlight(context, samples, index + 1, MapPalette.PATH_OUT);
        }
    }

    /** One emphasised segment of the route, drawn thicker and fully opaque. */
    private void drawPathHighlight(DrawContext context, List<RtpSample> samples, int index, int color) {
        if (index < 1 || index >= samples.size()) {
            return;
        }
        RtpSample a = samples.get(index - 1);
        RtpSample b = samples.get(index);
        double ax = view.worldToScreenX(a.x());
        double ay = view.worldToScreenY(a.z());
        double bx = view.worldToScreenX(b.x());
        double by = view.worldToScreenY(b.z());
        drawLine(context, ax, ay, bx, by, color, 2);
        drawArrowHead(context, ax, ay, bx, by, color);
    }

    /**
     * A chevron at the midpoint of a segment, pointing the way the player went.
     * Skipped on segments too short to hold one, where it would just be a blob.
     */
    private void drawArrowHead(DrawContext context, double ax, double ay, double bx, double by, int color) {
        double dx = bx - ax;
        double dy = by - ay;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < ARROW_MIN_SEGMENT) {
            return;
        }
        double midX = ax + dx * 0.5;
        double midY = ay + dy * 0.5;
        if (!view.contains(midX, midY)) {
            return;
        }
        double angle = Math.atan2(dy, dx);
        for (double spread : ARROW_SPREAD) {
            drawLine(context, midX, midY,
                    midX + Math.cos(angle + spread) * ARROW_SIZE,
                    midY + Math.sin(angle + spread) * ARROW_SIZE, color, 1);
        }
    }

    // ------------------------------------------------------------------ gaps

    /**
     * The gap map: distance to the nearest landing, shaded or counted.
     *
     * <p>Two pictures of one measurement. Close in, every cell of the canvas is
     * shaded by how far it sits from the nearest recorded landing, which draws
     * the shape of a hole and its edges. Far out, the same ground is cut into
     * fixed world tiles carrying their own landing count, because a shape a few
     * pixels across is not a shape and a number is.
     */
    private void drawGaps(DrawContext context, List<RtpSample> samples, MapConfig config,
                          GuardSettings guards) {
        // Before ensureGapField: the counts are keyed on this corner.
        double radius = guards.radiusFor(borderDimensions.get(0));
        gapOriginX = guards.borderCenterX - radius;
        gapOriginZ = guards.borderCenterZ - radius;
        ensureGapField(samples, config);
        if (rasterGaps()) {
            drawGapRaster(context, guards, radius);
        } else {
            gapTexture.draw(context, view.boundsX(), view.boundsY(),
                    gapField.gridWidth() * gapField.cellPixels(),
                    gapField.gridHeight() * gapField.cellPixels());
        }
    }

    /**
     * The corner the counted raster counts from: the low corner of the world
     * border, not the world origin.
     *
     * <p>Tiles used to sit on multiples of 0,0. DonutSMP's border reaches
     * 225 000 blocks, and its region cells are 50 000 measured from that edge,
     * so the cell lines fall on -225 000, -175 000, -125 000 and so on - odd
     * multiples of 25 000, which a grid counting from zero only meets by
     * accident. Counting from the corner instead, any tile that divides a cell
     * lands on every cell line and on the border itself.
     *
     * <p>Set once per frame in {@link #drawGaps}, because the counts are keyed
     * on it and have to be rebuilt when it moves.
     */
    private double gapOriginX;
    private double gapOriginZ;

    private boolean rasterGaps() {
        return scaleBarSpan() > GAP_RASTER_SCALE_BLOCKS;
    }

    private void ensureGapField(List<RtpSample> samples, MapConfig config) {
        long signature = gapSignature(samples, config);
        if (!gapDirty && signature == gapSignature) {
            return;
        }
        // A filter change is answered at once; a view that is still moving is
        // allowed to outrun a rebuild that has already proved expensive. The
        // signature is deliberately left stale here, so the next frame past the
        // interval picks the work up again and the field lands on the view the
        // player actually stopped at.
        long now = System.currentTimeMillis();
        if (!gapDirty && gapField.ready()
                && gapField.lastBuildNanos() > GAP_SLOW_BUILD_NANOS
                && now - gapBuiltAtMillis < GAP_STALE_MILLIS) {
            return;
        }
        gapSignature = signature;
        gapDirty = false;
        gapBuiltAtMillis = now;

        gapField.build(samples,
                view.screenToWorldX(view.boundsX()),
                view.screenToWorldZ(view.boundsY()),
                1.0 / view.zoom(),
                view.boundsWidth(), view.boundsHeight(),
                config.gapMaskTile, config.gapTopCount,
                rasterGaps() ? null : GAP_PALETTE);

        if (rasterGaps()) {
            countRasterTiles(samples, config);
        } else {
            gapTexture.update(gapField.pixels(), gapField.gridWidth(), gapField.gridHeight());
        }
    }

    /** Everything the field depends on, in one number. */
    private long gapSignature(List<RtpSample> samples, MapConfig config) {
        long hash = 1125899906842597L;
        hash = 31 * hash + Double.doubleToLongBits(view.zoom());
        hash = 31 * hash + Double.doubleToLongBits(view.screenToWorldX(view.boundsX()));
        hash = 31 * hash + Double.doubleToLongBits(view.screenToWorldZ(view.boundsY()));
        hash = 31 * hash + view.boundsWidth();
        hash = 31 * hash + view.boundsHeight();
        hash = 31 * hash + samples.size();
        hash = 31 * hash + config.gapMaskTile;
        hash = 31 * hash + config.gapTopCount;
        hash = 31 * hash + config.gapRasterTile;
        // The counts are keyed on the corner, so moving the border rebuilds them.
        hash = 31 * hash + Double.doubleToLongBits(gapOriginX);
        hash = 31 * hash + Double.doubleToLongBits(gapOriginZ);
        return hash;
    }

    /** Landing counts per tile, held between frames alongside the field. */
    private void countRasterTiles(List<RtpSample> samples, MapConfig config) {
        gapCounts.clear();
        gapCountTile = gapRasterTile(config);
        for (RtpSample sample : samples) {
            gapCounts.merge(tileKey(sample.x(), sample.z(), gapCountTile), 1, Integer::sum);
        }
    }

    /**
     * The counted version: one fixed world tile per cell, in four bands.
     *
     * <p>Four bands and not a ramp, because the question out here is "has this
     * square ever come up" and a ramp answers it with a shade the eye has to
     * compare against a key. Tiles snap to a fixed multiple of the world origin,
     * so the same square keeps its identity between sittings and can be ticked
     * off a list.
     */
    private void drawGapRaster(DrawContext context, GuardSettings guards, double radius) {
        double tile = gapCountTile;
        double tilePixels = tile * view.zoom();
        if (tile <= 0 || tilePixels < 3) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        // A held tile is a good deal smaller than the old growing one - at a
        // 50k scale bar it is fourteen pixels, not fifty-eight - and dropping
        // the counts there would take away the one thing the counted raster is
        // for. Font height plus a pixel of air is the real limit; a count too
        // wide for its own tile is skipped below, one at a time.
        boolean labels = tilePixels >= font.fontHeight + 4;

        double[] bounds = view.visibleWorldBounds();
        // Held tiles are small, so a wide view can span far more of them than
        // the budget allows - almost all of it ground the mask never covered
        // and the loop would skip anyway. Trimming to the mask first means the
        // budget is spent on tiles that can actually be drawn, instead of the
        // empty half of the screen blanking the whole raster.
        double[] mask = gapField.maskBounds();
        if (mask != null) {
            // One tile of slack: the mask is not raster-aligned, and a tile
            // straddling its edge can still hold landings.
            bounds = new double[]{
                    Math.max(bounds[0], mask[0] - tile),
                    Math.max(bounds[1], mask[1] - tile),
                    Math.min(bounds[2], mask[2] + tile),
                    Math.min(bounds[3], mask[3] + tile)
            };
            if (bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) {
                return;
            }
        }
        long firstX = (long) Math.floor((bounds[0] - gapOriginX) / tile);
        long lastX = (long) Math.floor((bounds[2] - gapOriginX) / tile);
        long firstZ = (long) Math.floor((bounds[1] - gapOriginZ) / tile);
        long lastZ = (long) Math.floor((bounds[3] - gapOriginZ) / tile);
        // A square border is a hard edge in world units, so the tiles that meet
        // it are cut there rather than drawn whole and left hanging over. With a
        // size off GAP_TILE_STEPS and the corner anchor there is nothing to cut -
        // this is what keeps an odd radius or a moved centre honest.
        boolean clip = guards.squareBorder && radius > 0 && Double.isFinite(radius);
        double clipMinX = guards.borderCenterX - radius;
        double clipMinZ = guards.borderCenterZ - radius;
        double clipMaxX = guards.borderCenterX + radius;
        double clipMaxZ = guards.borderCenterZ + radius;
        if ((lastX - firstX + 1) * (lastZ - firstZ + 1) > 20_000) {
            return;
        }

        for (long tz = firstZ; tz <= lastZ; tz++) {
            for (long tx = firstX; tx <= lastX; tx++) {
                double worldLeft = gapOriginX + tx * tile;
                double worldTop = gapOriginZ + tz * tile;
                double worldRight = worldLeft + tile;
                double worldBottom = worldTop + tile;
                if (clip) {
                    worldLeft = Math.max(worldLeft, clipMinX);
                    worldTop = Math.max(worldTop, clipMinZ);
                    worldRight = Math.min(worldRight, clipMaxX);
                    worldBottom = Math.min(worldBottom, clipMaxZ);
                    if (worldRight <= worldLeft || worldBottom <= worldTop) {
                        continue;
                    }
                }
                double left = view.worldToScreenX(worldLeft);
                double top = view.worldToScreenY(worldTop);
                int x1 = (int) Math.max(view.boundsX(), Math.floor(left));
                int y1 = (int) Math.max(view.boundsY(), Math.floor(top));
                int x2 = (int) Math.min(view.right(),
                        Math.ceil(view.worldToScreenX(worldRight)));
                int y2 = (int) Math.min(view.bottom(),
                        Math.ceil(view.worldToScreenY(worldBottom)));
                if (x2 <= x1 || y2 <= y1) {
                    continue;
                }
                int count = gapCounts.getOrDefault(tileIndexKey(tx, tz), 0);
                // Judged on the tile's own middle in world units, never on the
                // part of it that happens to be on screen: probing the clipped
                // centre made the outer row and column blink in and out as the
                // map was dragged, because the probe walked across the tile
                // while the tile stood still.
                //
                // A tile holding a landing is drawn whatever the mask says. The
                // mask is not tile-aligned, so a tile whose middle falls just
                // outside it can still hold landings in a corner - and a marker
                // sitting on bare ground with no tile under it reads as a bug,
                // which is exactly what it looked like.
                if (count == 0 && !gapField.insideWorld(gapOriginX + (tx + 0.5) * tile,
                        gapOriginZ + (tz + 0.5) * tile)) {
                    continue;
                }
                context.fill(x1, y1, x2, y2, MapPalette.gapBand(count));
                // Tile edges only when a tile is big enough for the edge to say
                // something. Packed tighter they are two more rectangles each
                // for a line the eye reads off the colour change anyway.
                if (labels && left >= view.boundsX()) {
                    context.fill(x1, y1, x1 + 1, y2, MapPalette.GRID_MAJOR);
                }
                if (labels && top >= view.boundsY()) {
                    context.fill(x1, y1, x2, y1 + 1, MapPalette.GRID_MAJOR);
                }
                if (labels) {
                    String text = String.valueOf(count);
                    int width = font.getWidth(text);
                    if (width + 2 > tilePixels) {
                        continue;
                    }
                    context.drawText(font, text, (x1 + x2 - width) / 2,
                            (y1 + y2 - font.fontHeight) / 2,
                            count == 0 ? 0xFF4A3708 : MapPalette.TEXT_DIM, false);
                }
            }
        }
    }

    /**
     * Picks a tile that lands near half the scale bar, so a few dozen fit
     * across - then holds it at {@link #GAP_TILE_HOLD} until the scale bar
     * passes {@link #GAP_TILE_HOLD_SCALE_BLOCKS}.
     */
    private double gapRasterTile(MapConfig config) {
        double span = scaleBarSpan();
        double best = snapToStep(span / 2.0);
        if (span > GAP_TILE_HOLD_SCALE_BLOCKS) {
            return best;
        }
        // Snapped, not taken as typed: a config written before the ladder
        // changed still says 8 000, and an unsnapped size is exactly the thing
        // that puts a tile across a region line.
        double hold = snapToStep(config.gapRasterTile > 0
                ? config.gapRasterTile : GAP_TILE_HOLD);
        // Never *bigger* than the automatic size: close in, a held 8k tile
        // would be a third of the screen and the picture would stop being a
        // raster. The hold only ever stops the tile growing.
        return Math.min(best, hold);
    }

    /** The step nearest {@code wanted}. Every step fits the region grid. */
    private static double snapToStep(double wanted) {
        double best = GAP_TILE_STEPS[0];
        for (double step : GAP_TILE_STEPS) {
            if (Math.abs(step - wanted) < Math.abs(best - wanted)) {
                best = step;
            }
        }
        return best;
    }

    private long tileKey(double worldX, double worldZ, double tile) {
        return tileIndexKey((long) Math.floor((worldX - gapOriginX) / tile),
                (long) Math.floor((worldZ - gapOriginZ) / tile));
    }

    private static long tileIndexKey(long tx, long tz) {
        return (tx << 32) ^ (tz & 0xFFFFFFFFL);
    }

    /**
     * Rings the ranked holes and numbers them, matching the list in the metrics
     * column. The ring is the hole's own radius, so its size is the measurement
     * rather than a marker size someone picked.
     */
    private void drawGapHoles(DrawContext context) {
        java.util.List<GapField.Hole> holes = gapField.holes();
        if (holes.isEmpty()) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int number = 1;
        for (GapField.Hole hole : holes) {
            double sx = view.worldToScreenX(hole.x());
            double sy = view.worldToScreenY(hole.z());
            double radius = Math.max(6.0, hole.radius() * view.zoom());
            drawWorldCircle(context, hole.x(), hole.z(), hole.radius(), MapPalette.HOLE_RING,
                    HOLE_RING_SEGMENTS);
            drawCross(context, hole.x(), hole.z(), 3, MapPalette.HOLE_RING);
            int labelX = (int) Math.round(sx + radius) + 3;
            int labelY = (int) Math.round(sy) - 4;
            if (view.contains(labelX, labelY)) {
                context.drawText(font, String.valueOf(number), labelX, labelY,
                        MapPalette.HIGHLIGHT, false);
            }
            number++;
        }
    }

    /**
     * The gap map's own colour key: a strip of the ramp with both ends named.
     *
     * <p>The normal key lists categories, and here the colour is a measurement,
     * so a list of rows would have nothing to put in them.
     */
    private void drawGapKey(DrawContext context) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        String title = Lang.t("map.gaps.key");
        String low = Lang.t("map.gaps.key_low");
        String high = Lang.t("map.gaps.key_high");

        int width = Math.max(font.getWidth(title), font.getWidth(low) + font.getWidth(high) + 10) + 14;
        width = Math.min(width, view.boundsWidth() - 16);
        int height = font.fontHeight * 2 + 18;
        if (width < 60 || height > view.boundsHeight() - 16) {
            return;
        }
        int x = view.right() - width - 6;
        int y = view.boundsY() + 14;
        Theme.roundRect(context, x, y, width, height, Theme.RADIUS_CONTROL, 0xE0171B21);
        Theme.roundOutline(context, x, y, width, height, Theme.RADIUS_CONTROL, 0x60000000, 0x00000000);

        context.drawText(font, UiDraw.trim(title, width - 10), x + 6, y + 5, MapPalette.TEXT_DIM, false);

        // In steps, not per pixel. A rectangle is an allocation apiece on this
        // version, and a hundred and fifty of them for a key nobody is reading
        // closely is the same mistake the shading itself was just cured of.
        int stripY = y + 6 + font.fontHeight + 2;
        int stripLeft = x + 6;
        int stripRight = x + width - 6;
        int steps = Math.min(GAP_KEY_STEPS, Math.max(1, stripRight - stripLeft));
        for (int step = 0; step < steps; step++) {
            int from = stripLeft + (stripRight - stripLeft) * step / steps;
            int to = stripLeft + (stripRight - stripLeft) * (step + 1) / steps;
            if (to <= from) {
                continue;
            }
            int shade = GAP_PALETTE[Math.min(GAP_BANDS, step * GAP_BANDS / Math.max(1, steps - 1))];
            context.fill(from, stripY, to, stripY + 6, 0xFF000000 | (shade & 0x00FFFFFF));
        }

        int labelY = stripY + 8;
        context.drawText(font, low, stripLeft, labelY, MapPalette.TEXT_DIM, false);
        context.drawText(font, high, stripRight - font.getWidth(high), labelY, MapPalette.TEXT_DIM, false);
    }

    private int colorOf(RtpSample sample, int index, int total) {
        return switch (colorMode) {
            case DIMENSION -> Worlds.dimensionColor(sample.dimension());
            case REGION -> regionColorLookup.apply(sample);
            case RECENCY -> {
                double age = total <= 1 ? 1.0 : (double) index / (total - 1);
                yield MapPalette.lighten(0xFF3F6FA8, age * 0.7);
            }
            case SESSION -> sessionColorLookup.apply(sample);
            // Family lookups are memoised, so this stays a map hit per marker
            // rather than string work on the render path.
            case BIOME -> dev.rtpbuddy.util.Biomes.color(sample.biome());
        };
    }

    // -------------------------------------------------------- marker shapes

    /** The radius the config asks for, kept inside what the span table covers. */
    private static int markerRadius(MapConfig config) {
        int radius = config == null ? MARKER_RADIUS : config.markerRadius;
        return Math.max(MIN_MARKER_RADIUS, Math.min(MAX_MARKER_RADIUS, radius));
    }

    /**
     * Half-widths of every row of a disc, one entry per radius.
     *
     * <p>Filling a circle pixel by pixel is what turned a busy map into a
     * slideshow once before, so a disc is drawn as one {@code fill} per row -
     * five rectangles for the default marker instead of twenty-five draws - and
     * the row widths are worked out once at class load rather than per marker.
     *
     * <p>The radius is padded by a third of a pixel before the test. Without it
     * the smallest discs come out as diamonds, because at radius two the corner
     * pixel misses the circle by a hair.
     */
    /**
     * What number to draw beside a marker. The screen decides: inside one
     * sitting the samples count from 1, across sittings they keep their global
     * number.
     */
    private java.util.function.ToIntFunction<RtpSample> numberLookup = RtpSample::sample;

    /** Dark collar drawn under the player marker, for contrast on any ground. */
    private static final int PLAYER_COLLAR = 0xE00A0D11;

    /** The hole in the middle, which turns the dot into a ring. */
    private static final int PLAYER_CORE = 0xFF12301C;

    /** One full ripple of the player pulse, in milliseconds. */
    private static final long PULSE_MILLIS = 1_500L;
    private static final double PULSE_MIN = 7.0;
    private static final double PULSE_MAX = 22.0;

    /** Whether the player marker ripples. Set from the config on every render. */
    private boolean playerPulse = true;

    private static final int[][] DISC_ROWS = buildDiscRows();

    private static int[][] buildDiscRows() {
        int[][] table = new int[MAX_MARKER_RADIUS + 3][];
        for (int radius = 0; radius < table.length; radius++) {
            int[] rows = new int[radius * 2 + 1];
            double limit = (radius + 0.35) * (radius + 0.35);
            for (int dy = -radius; dy <= radius; dy++) {
                int half = 0;
                while ((half + 1) * (half + 1) + dy * dy <= limit) {
                    half++;
                }
                rows[dy + radius] = half;
            }
            table[radius] = rows;
        }
        return table;
    }

    /** A filled marker: a disc when round is asked for, a square otherwise. */
    public void setNumberLookup(java.util.function.ToIntFunction<RtpSample> lookup) {
        this.numberLookup = lookup == null ? RtpSample::sample : lookup;
    }

    private static void dot(DrawContext context, int cx, int cy, int radius, boolean round, int color) {
        if (radius < 0) {
            return;
        }
        if (!round || radius >= DISC_ROWS.length) {
            context.fill(cx - radius, cy - radius, cx + radius + 1, cy + radius + 1, color);
            return;
        }
        int[] rows = DISC_ROWS[radius];
        for (int dy = -radius; dy <= radius; dy++) {
            int half = rows[dy + radius];
            context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** An outlined marker: the same shape, punched out with the canvas colour. */
    private static void ring(DrawContext context, int cx, int cy, int radius, boolean round,
                             int color, int hole) {
        dot(context, cx, cy, radius, round, color);
        dot(context, cx, cy, radius - 1, round, hole);
    }

    // ----------------------------------------------------------- hit-testing

    /** Index of the sample under the cursor, or -1. Nearest wins on overlap. */
    public int hitTest(List<RtpSample> samples, double mouseX, double mouseY) {
        if (!view.contains(mouseX, mouseY)) {
            return -1;
        }
        int best = -1;
        double bestDistance = HIT_RADIUS * HIT_RADIUS;
        for (int i = 0; i < samples.size(); i++) {
            RtpSample sample = samples.get(i);
            double dx = view.worldToScreenX(sample.x()) - mouseX;
            double dy = view.worldToScreenY(sample.z()) - mouseY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= bestDistance) {
                bestDistance = distanceSquared;
                best = i;
            }
        }
        return best;
    }

    /** Samples whose world position falls inside a screen-space rectangle. */
    public java.util.List<RtpSample> withinScreenRect(List<RtpSample> samples,
                                                      double x1, double y1, double x2, double y2) {
        double left = Math.min(x1, x2);
        double right = Math.max(x1, x2);
        double top = Math.min(y1, y2);
        double bottom = Math.max(y1, y2);
        java.util.List<RtpSample> result = new java.util.ArrayList<>();
        for (RtpSample sample : samples) {
            double sx = view.worldToScreenX(sample.x());
            double sy = view.worldToScreenY(sample.z());
            if (sx >= left && sx <= right && sy >= top && sy <= bottom) {
                result.add(sample);
            }
        }
        return result;
    }

    // ------------------------------------------------------------- primitives

    private boolean offscreen(double sx, double sy) {
        return sx < view.boundsX() - 32 || sx > view.right() + 32
                || sy < view.boundsY() - 32 || sy > view.bottom() + 32;
    }

    /**
     * A diagonal, drawn as one rotated quad.
     *
     * <p>{@link DrawContext} only fills axis-aligned rectangles, so this used to
     * plot the line pixel by pixel - one {@code fill} per pixel. Every
     * {@code fill} allocates a matrix and a render-state object, so the path
     * overlay alone cost a quarter of a million allocations per frame once a few
     * hundred landings had been recorded. That is what heated the CPU while the
     * map was open and auto-RTP kept adding samples. Rotating the matrix instead
     * costs exactly one quad per line whatever its length.
     *
     * <p>The segment is clipped to the canvas first: unclipped world coordinates
     * run to millions of pixels at low zoom, and a quad that large loses
     * precision in the float matrix.
     */
    private void drawLine(DrawContext context, double x1, double y1, double x2, double y2, int color) {
        drawLine(context, x1, y1, x2, y2, color, 1);
    }

    private void drawLine(DrawContext context, double x1, double y1, double x2, double y2,
                          int color, int thickness) {
        if (!clipToView(x1, y1, x2, y2)) {
            return;
        }
        double dx = clipX2 - clipX1;
        double dy = clipY2 - clipY1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) {
            int px = (int) Math.round(clipX1);
            int py = (int) Math.round(clipY1);
            context.fill(px, py, px + 1, py + 1, color);
            return;
        }

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) clipX1, (float) clipY1);
        matrices.rotate((float) Math.atan2(dy, dx));
        // Half the width to each side, measured *after* the rotation. Shifting
        // before it moved the band straight up the screen instead of across the
        // line, so a thick line leaned further off centre the closer it came to
        // vertical - which is what made the player's heading arrow look bent.
        int half = thickness / 2;
        context.fill(0, -half, (int) Math.round(length), thickness - half, color);
        matrices.popMatrix();
    }

    /**
     * The clipped segment from the last {@link #clipToView} that returned true.
     *
     * <p>Fields rather than a returned array. The route asks this question once
     * per segment, up to two thousand times a frame, and the obvious version
     * allocated three {@code double[4]} per call - the two Liang-Barsky tables
     * and the result - which is six thousand arrays a frame to answer a
     * question about four numbers.
     */
    private double clipX1;
    private double clipY1;
    private double clipX2;
    private double clipY2;

    /**
     * Liang-Barsky against the canvas, written out rather than looped.
     *
     * @return true when any of the segment is inside; the visible part is then
     *         in {@link #clipX1} through {@link #clipY2}
     */
    private boolean clipToView(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        clipEnter = 0.0;
        clipExit = 1.0;
        double enter;
        double exit;

        // left, right, top, bottom - the same four tests the table held.
        if (!clipEdge(-dx, x1 - view.boundsX())) {
            return false;
        }
        enter = clipEnter;
        exit = clipExit;
        if (!clipEdge(dx, view.right() - x1)) {
            return false;
        }
        if (!clipEdge(-dy, y1 - view.boundsY())) {
            return false;
        }
        if (!clipEdge(dy, view.bottom() - y1)) {
            return false;
        }
        enter = clipEnter;
        exit = clipExit;
        if (enter > exit) {
            return false;
        }
        clipX1 = x1 + enter * dx;
        clipY1 = y1 + enter * dy;
        clipX2 = x1 + exit * dx;
        clipY2 = y1 + exit * dy;
        return true;
    }

    private double clipEnter;
    private double clipExit;

    /** One edge of the clip. Carries the running interval in two fields. */
    private boolean clipEdge(double p, double q) {
        if (p == 0.0) {
            return q >= 0.0;
        }
        double t = q / p;
        if (p < 0.0) {
            if (t > clipEnter) {
                clipEnter = t;
            }
        } else if (t < clipExit) {
            clipExit = t;
        }
        return clipEnter <= clipExit;
    }

    private void drawWorldRect(DrawContext context, double minX, double minZ,
                               double maxX, double maxZ, int color) {
        int x1 = (int) Math.round(view.worldToScreenX(minX));
        int y1 = (int) Math.round(view.worldToScreenY(minZ));
        int x2 = (int) Math.round(view.worldToScreenX(maxX));
        int y2 = (int) Math.round(view.worldToScreenY(maxZ));
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2, x2, y2 + 1, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2, y1, x2 + 1, y2, color);
    }

    /**
     * A circle in world space, approximated by straight segments.
     *
     * <p>The ring is skipped outright when it cannot cross the canvas. The
     * border guard is a 225 000 block square on DonutSMP; zoomed in anywhere
     * near the middle of it, every one of its segments lies off-screen, and
     * walking them was pure cost for nothing drawn.
     */
    private void drawWorldCircle(DrawContext context, double centerX, double centerZ,
                                 double radius, int color) {
        drawWorldCircle(context, centerX, centerZ, radius, color, 180);
    }

    /**
     * @param maxSegments how finely the ring may be cut. Every segment is a draw
     *                    call that allocates, so a ring nobody is measuring off
     *                    the screen has no business costing a hundred and eighty.
     */
    private void drawWorldCircle(DrawContext context, double centerX, double centerZ,
                                 double radius, int color, int maxSegments) {
        double pixelRadius = radius * view.zoom();
        if (pixelRadius < 1.5 || !ringCrossesView(centerX, centerZ, radius)) {
            return;
        }
        int segments = (int) Math.max(24, Math.min(maxSegments, pixelRadius));
        double previousX = 0;
        double previousY = 0;
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            double wx = centerX + Math.cos(angle) * radius;
            double wz = centerZ + Math.sin(angle) * radius;
            double sx = view.worldToScreenX(wx);
            double sy = view.worldToScreenY(wz);
            if (i > 0) {
                drawLine(context, previousX, previousY, sx, sy, color);
            }
            previousX = sx;
            previousY = sy;
        }
    }

    /**
     * True when the ring of the given radius passes through the visible world
     * rectangle - that is, when the rectangle has points both inside and outside
     * the circle. A rectangle wholly inside or wholly outside shows nothing.
     */
    private boolean ringCrossesView(double centerX, double centerZ, double radius) {
        double[] bounds = view.visibleWorldBounds();
        double nearX = Math.max(bounds[0], Math.min(centerX, bounds[2]));
        double nearZ = Math.max(bounds[1], Math.min(centerZ, bounds[3]));
        double nearest = Math.hypot(centerX - nearX, centerZ - nearZ);

        double farX = Math.abs(centerX - bounds[0]) > Math.abs(centerX - bounds[2]) ? bounds[0] : bounds[2];
        double farZ = Math.abs(centerZ - bounds[1]) > Math.abs(centerZ - bounds[3]) ? bounds[1] : bounds[3];
        double farthest = Math.hypot(centerX - farX, centerZ - farZ);

        return radius >= nearest && radius <= farthest;
    }

    private void drawCross(DrawContext context, double worldX, double worldZ, int size, int color) {
        int sx = (int) Math.round(view.worldToScreenX(worldX));
        int sy = (int) Math.round(view.worldToScreenY(worldZ));
        context.fill(sx - size, sy, sx + size + 1, sy + 1, color);
        context.fill(sx, sy - size, sx + 1, sy + size + 1, color);
    }

    /**
     * Blocks the scale bar in the corner currently spans: the nearest 1/2/5 step
     * to about ninety pixels.
     *
     * <p>Pulled out of the drawing because the sample numbers are gated against
     * it. Stating that rule as a zoom figure would make it invisible; stated as
     * the bar, it is something the player reads straight off the screen -
     * numbers appear once the bar says 5k blocks or less.
     */
    private double scaleBarSpan() {
        double worldSpan = 90.0 / view.zoom();
        double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(1e-6, worldSpan))));
        double normalised = worldSpan / magnitude;
        double factor = normalised <= 1.5 ? 1 : normalised <= 3.5 ? 2 : normalised <= 7.5 ? 5 : 10;
        return factor * magnitude;
    }

    private void drawScaleBar(DrawContext context) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        double niceSpan = scaleBarSpan();
        int pixels = (int) Math.round(niceSpan * view.zoom());
        int maxPixels = view.boundsWidth() - 20;
        if (pixels > maxPixels || maxPixels < 20) {
            return;
        }

        int x = view.boundsX() + 8;
        int y = view.bottom() - 14;
        context.fill(x, y, x + pixels, y + 2, MapPalette.TEXT_DIM);
        context.fill(x, y - 3, x + 1, y + 2, MapPalette.TEXT_DIM);
        context.fill(x + pixels - 1, y - 3, x + pixels, y + 2, MapPalette.TEXT_DIM);
        context.drawText(font, dev.rtpbuddy.util.Lang.t("unit.blocks", Numbers.compact(niceSpan)),
                x, y - 13, MapPalette.TEXT_DIM, false);
    }

    /** Live world coordinates under the cursor, drawn in the corner. */
    public void drawCursorReadout(DrawContext context, double mouseX, double mouseY) {
        if (!view.contains(mouseX, mouseY)) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        String text = "X " + Math.round(view.screenToWorldX(mouseX))
                + "   Z " + Math.round(view.screenToWorldZ(mouseY))
                + "   " + Numbers.fixed(view.zoom() * 1000, 2) + " px/kb";
        // In gap mode the reading under the cursor is the point of the picture,
        // so it goes where the coordinates already are rather than into a
        // tooltip the player has to hover something to get.
        if (markerMode == MarkerMode.GAPS && gapField.ready()) {
            double blocks = gapField.blocksAtCanvas(mouseX - view.boundsX(), mouseY - view.boundsY());
            text += "   " + (Double.isNaN(blocks)
                    ? Lang.t("map.gaps.outside")
                    : Lang.t("map.gaps.cursor", Numbers.compact(blocks)));
        }
        int width = font.getWidth(text);
        int x = view.right() - width - 10;
        int y = view.bottom() - 14;
        context.fill(x - 3, y - 2, x + width + 3, y + font.fontHeight, MapPalette.PANEL);
        context.drawText(font, text, x, y, MapPalette.TEXT_DIM, false);
    }
}
