package dev.rtpbuddy.config;

public class MapConfig {

    /** POINTS | PATH | GAPS */
    public String markerMode = "POINTS";

    /** DIMENSION | REGION | RECENCY | SESSION */
    public String colorMode = "DIMENSION";

    /** Heatmap cell edge length in blocks. */
    /**
     * Cell edge for the density grid behind the <em>coverage</em> statistic.
     *
     * <p>It used to size the heatmap as well. The heatmap is gone; the statistic
     * is not, and it is the reason this value still exists.
     */
    public int densityCellSize = 512;

    /**
     * Edge length of one mask tile in the gap map, in blocks.
     *
     * <p>The mask is the block of tiles the landings span, filled in, and it is
     * what stops the gap map from naming the unexplored edge of the world as the
     * emptiest place on it. Bigger tiles are coarser and so claim more ground
     * around the outermost landings as explored; smaller ones hug the recording
     * more closely but need more of it before the middle fills in.
     */
    public int gapMaskTile = 16_000;

    /** How many holes the gap map rings and lists. */
    public int gapTopCount = 5;

    /**
     * Ground one counted tile stands for out past the 5k scale, in blocks.
     *
     * <p>The tile used to be sized to stay about the same width on screen at
     * every zoom, which meant zooming out quietly quadrupled the ground behind
     * each square until one of them covered sixty-four thousand blocks a side.
     * A tile asks whether anything ever landed in this square, and that answer
     * is worth less the bigger the square gets, so the size is held here
     * instead and zooming out shrinks the tiles rather than coarsening them.
     *
     * <p>Past a 100k scale bar the held tile falls under three pixels and
     * cannot be drawn at all, so out there the old growing tile takes over
     * whatever this says.
     */
    public int gapRasterTile = 10_000;

    /**
     * Marker radius in pixels. Three was a fat square at every zoom; the plot is
     * about where the landings are, not how much ink each one can claim.
     */
    public int markerRadius = 2;

    /** Round markers. A square marker reads as a chunk, a disc reads as a point. */
    public boolean roundMarkers = true;

    /**
     * Markers on screen past which the plot is drawn the cheap way.
     *
     * <p>Everything the map draws goes through {@code DrawContext.fill}, and on
     * 1.21.11 each of those allocates twice. Two thousand landings framed at
     * once is ten thousand rectangles for the dots alone and twenty thousand
     * for the route on top - which is what makes a full map heavy, not the
     * arithmetic behind it.
     *
     * <p>Past this many markers the canvas thins them to one per marker-sized
     * patch, and past it again squares the shape off. Both only remove work
     * that was landing underneath something already drawn. 0 switches the whole
     * thing off and draws every landing however many there are.
     */
    public int densityBudget = 500;

    public static final int MAX_DENSITY_BUDGET = 20_000;

    public static int clampDensityBudget(int budget) {
        return budget <= 0 ? 0 : Math.min(MAX_DENSITY_BUDGET, budget);
    }

    public boolean showGrid = true;
    public boolean showBorder = true;
    public boolean showSpawnGuard = true;
    public boolean showPlayer = true;

    /**
     * Ripple around the player marker. It is the one moving thing on the plot,
     * which is what makes the marker findable after a teleport has moved it.
     */
    public boolean playerPulse = true;
    public boolean showOrigin = true;

    /**
     * Draws the line into the newest landing: where that teleport started.
     *
     * <p>Watching the map while the loop runs, the one thing the plot could not
     * say was which of the dots you had just come from. Every sample records its
     * own origin, so the answer was already in the file.
     */
    public boolean showLastLeg = true;
    public boolean showSampleNumbers = true;
    public boolean showScaleBar = true;

    /** Colour key in the corner of the plot, saying what the marker colours mean. */
    public boolean showLegend = true;

    /**
     * The nine-by-nine progress board at the top of the metrics column.
     *
     * <p>It answers the one question the density coverage figure cannot: that
     * one is a share of a disc with no natural denominator, this one counts
     * against a grid that really does have eighty-one cells and therefore a
     * finish line.
     */
    public boolean showCellBoard = true;

    /** Number every board cell, not just the ones with landings in them. */
    public boolean cellBoardNumbers = true;

    /**
     * The server-region grid underneath the plot: the nine by nine cells the
     * host splits its overworld into, each tinted by the region that owns it.
     */
    public boolean showServerRegions = true;

    /** Restore the last pan/zoom when the map reopens. */
    public boolean rememberView = true;

    /** SESSION | ALL - which samples the map shows when it opens. */
    public String scope = "SESSION";

    /**
     * BOTH | LEFT_ONLY | RIGHT_ONLY | NONE - which side panels the map opens
     * with. Tab and the toolbar button both cycle it.
     *
     * <p>Stored because the screen is rebuilt from scratch constantly - a
     * teleport replaces the instance outright - so a mode held only in the
     * screen came back as BOTH every time, and setting the map to fill the
     * window had to be redone on every open.
     *
     * <p>A window too narrow to hold the chosen panels falls back to fewer
     * for that draw only; the fallback is never written back here, or one
     * resize would destroy the preference.
     */
    public String panelMode = "BOTH";

    /**
     * Frame cap while the map is open, or 0 to leave the game's own limit alone.
     *
     * <p>A still map redrawn 300 times a second is 300 times the work for the
     * same picture, and the game does not throttle a screen that has a world
     * behind it. Sixty is smooth for panning and costs a fraction of the heat.
     */
    public int mapFpsLimit = 60;

    public double sessionCenterX = 0.0;
    public double sessionCenterZ = 0.0;
    public double sessionZoom = 0.02;

    public double allCenterX = 0.0;
    public double allCenterZ = 0.0;
    public double allZoom = 0.005;

    /**
     * Side panel widths on the map screens, as a fraction of the window width.
     *
     * <p>Stored as a fraction rather than in pixels so a panel keeps its
     * proportions when the window is resized or the GUI scale changes - a pixel
     * width picked at scale 2 would swallow the whole screen at scale 4.
     */
    public double panelLeftFraction = DEFAULT_PANEL_LEFT;
    public double panelRightFraction = DEFAULT_PANEL_RIGHT;

    /**
     * Height of the session picker inside the sidebar, as a fraction of the
     * panel. The sample list gets whatever is left, and the divider between them
     * is draggable.
     */
    public double sidebarFraction = DEFAULT_SIDEBAR;

    public static final double DEFAULT_PANEL_LEFT = 0.24;
    public static final double DEFAULT_PANEL_RIGHT = 0.26;
    public static final double MIN_PANEL_FRACTION = 0.10;
    public static final double MAX_PANEL_FRACTION = 0.45;

    public static final double DEFAULT_SIDEBAR = 0.28;
    public static final double MIN_SIDEBAR_FRACTION = 0.08;
    public static final double MAX_SIDEBAR_FRACTION = 0.72;

    /**
     * Put the map back after a teleport tore it down.
     *
     * <p>A cross-world teleport makes the client swap in its own loading screen,
     * which throws away whatever was open - so every landing closed the map. The
     * screen is restored once the world is back, at the same scope and view.
     */
    public boolean reopenAfterTeleport = true;

    /**
     * Keep the dimension, region and capture-mode filter when the map is closed
     * and opened again. Without it every reopen silently widened the view back
     * to everything, which reads as the map having forgotten what was asked of
     * it.
     */
    public boolean rememberFilter = true;

    /** Persisted filter, written on close and read on open. Null means "all". */
    public String filterDimension = null;

    /** Persisted contents of the search box. Null or blank means "no search". */
    public String filterSearch = null;
    public String filterRegion = null;
    public String filterCaptureMode = null;

    /**
     * Persisted biome filter, by biome family name. Null means "every biome".
     *
     * <p>Stored as the family name rather than as the label shown on the
     * button, because the label is whatever the game calls it in the current
     * language and a filter must survive a language change.
     */
    public String filterBiomeFamily = null;

    /**
     * The sittings picked in the all-sessions list, and whether a picking is in
     * force at all. Both are needed: an empty list with the flag set means "show
     * none", which is not the same as "no session filter".
     *
     * <p>Ids that name a sitting no longer in the store are dropped on load, so
     * deleting a sitting cannot leave the map narrowed to nothing.
     */
    public java.util.List<String> filterSessions = new java.util.ArrayList<>();
    public boolean filterSessionsActive = false;

    /** Hide sittings that never recorded a landing from the session list. */
    public boolean hideEmptySessions = true;

    // ------------------------------------------------------------------- HUD

    /** HUD overlay showing capture state and session counters. */
    public boolean hudEnabled = true;

    /** TOP_LEFT | TOP_CENTER | TOP_RIGHT | BOTTOM_LEFT | BOTTOM_CENTER | BOTTOM_RIGHT */
    public String hudAnchor = "TOP_LEFT";

    /**
     * Offset from the anchor in GUI pixels, always positive and always inwards.
     * Anchoring rather than storing a raw screen position keeps the overlay in
     * its corner when the window is resized or the GUI scale changes.
     */
    public int hudX = 4;
    public int hudY = 4;

    /** Size of the overlay relative to the rest of the HUD, 0.5 to 2.0. */
    public double hudScale = 1.0;

    /** Backing plate behind the text. Opacity is a percentage, 0 to 100. */
    public boolean hudBackground = true;
    public int hudBackgroundOpacity = 60;

    /** Drop shadow on the text, for when the plate is off. */
    public boolean hudTextShadow = false;

    /** Which rows the overlay shows. */
    public boolean hudShowAuto = true;
    public boolean hudShowState = true;
    public boolean hudShowCounters = true;
    public boolean hudShowLast = true;
    /** The row under the last sample: its biome and dimension. */
    public boolean hudShowContext = true;
    public boolean hudShowStatus = true;

    // --------------------------------------------------------------- minimap

    /**
     * The small always-on plot in the corner of the game screen.
     *
     * <p>It answers one question the full map screen also answers, but at the
     * cost of a key press and a paused view: whereabouts in the recorded spread
     * the player is standing right now. Everything else the map screen does -
     * filters, statistics, the gap field, picking a landing - stays there.
     */
    public boolean minimapEnabled = true;

    /** TOP_LEFT | TOP_CENTER | TOP_RIGHT | BOTTOM_LEFT | BOTTOM_CENTER | BOTTOM_RIGHT */
    public String minimapAnchor = "BOTTOM_RIGHT";

    /** Inward offset from the anchor, in GUI pixels. Same contract as the overlay. */
    public int minimapX = 4;
    public int minimapY = 4;

    /** Edge length of the square plate in GUI pixels. */
    public int minimapSize = 104;

    public static final int MIN_MINIMAP_SIZE = 64;
    public static final int MAX_MINIMAP_SIZE = 192;

    /** Opacity of the plate behind the plot, as a percentage. */
    public int minimapOpacity = 78;

    /** SESSION | ALL - which landings the minimap frames. */
    public String minimapScope = "SESSION";

    /** The server-region cells under the plot, tinted by the region that owns them. */
    public boolean minimapShowRegions = true;

    /** Cell numbers written into the region cells, when they are big enough to hold one. */
    public boolean minimapShowCellNumbers = true;

    /** The world axes, x = 0 and z = 0. */
    public boolean minimapShowAxes = true;

    /** The line into the newest landing: where that teleport started. */
    public boolean minimapShowLastLeg = true;

    /** The strip under the plate naming the cell the player is standing in. */
    public boolean minimapShowCaption = true;

    public static int clampMinimapSize(int size) {
        return Math.max(MIN_MINIMAP_SIZE, Math.min(MAX_MINIMAP_SIZE, size));
    }

    public static int clampMinimapOpacity(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
