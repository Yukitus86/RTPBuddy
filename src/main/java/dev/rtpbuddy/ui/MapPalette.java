package dev.rtpbuddy.ui;

/** Shared colours so both map screens read as one design. */
public final class MapPalette {

    public static final int BACKGROUND = 0xFF14171C;
    public static final int PANEL = 0xE0181C22;
    public static final int PANEL_BORDER = 0xFF2C333D;
    public static final int GRID_MINOR = 0xFF20262E;
    public static final int GRID_MAJOR = 0xFF2E3742;
    public static final int AXIS = 0xFF48566A;

    public static final int TEXT = 0xFFE6E9EE;
    public static final int TEXT_DIM = 0xFF8D97A6;
    public static final int TEXT_ACCENT = 0xFF74C0F0;

    /** The draggable divider between a side panel and the canvas. */
    public static final int SPLITTER = 0xFF232A33;
    public static final int SPLITTER_ACTIVE = 0xFF3A4553;

    public static final int SELECTION_FILL = 0x3374C0F0;
    public static final int SELECTION_BORDER = 0xFF74C0F0;
    public static final int HIGHLIGHT = 0xFFFFD166;

    /** The ring drawn round a ranked hole on the gap map. */
    public static final int HOLE_RING = 0xCCFF6B5E;

    public static final int BORDER_GUARD = 0x66FF6B5C;
    public static final int SPAWN_GUARD = 0x66FFB05C;
    public static final int PLAYER = 0xFF6BE38F;
    public static final int ORIGIN = 0xFF7A8699;
    public static final int PATH_LINE = 0x804E9BD1;

    /** The leg that arrives at the picked landing: where the player came from. */
    public static final int PATH_IN = 0xFFE8564A;

    /** The leg that leaves it: where the next RTP took them. */
    public static final int PATH_OUT = 0xFF4FC26B;
    /** Path mode draws the same route as the subject rather than as background. */
    public static final int PATH_BRIGHT = 0xFF7FC7F5;

    /** Distinct hues for per-session colouring, cycled by session index. */
    private static final int[] SESSION_COLORS = {
            0xFF6BC5F0, 0xFFF0A76B, 0xFF8FE36B, 0xFFE36BC5, 0xFFE3D96B,
            0xFF6B7FE3, 0xFFE36B6B, 0xFF6BE3C5, 0xFFC58FE3, 0xFFB0E36B
    };

    /**
     * Fallback hues for regions a server offers that carry no colour of their
     * own. Picked to stay apart from {@link #SESSION_COLORS}, so a map coloured
     * by region never looks like the same map coloured by session.
     */
    private static final int[] REGION_COLORS = {
            0xFF5FD08A, 0xFFE0785C, 0xFFB98CE0, 0xFFE0C55C, 0xFF5CB8E0,
            0xFFE05C9E, 0xFF8CE05C, 0xFF5C6FE0, 0xFFE0975C, 0xFF5CE0D0
    };

    private MapPalette() {
    }

    /**
     * A stable colour for a region id, used when the preset does not name one.
     *
     * <p>Derived from the id rather than from its position in the list, so a
     * region keeps its colour when the list is reordered or another region is
     * added in front of it.
     */
    public static int region(String id) {
        int hash = id == null ? 0 : id.hashCode();
        return REGION_COLORS[Math.floorMod(hash, REGION_COLORS.length)];
    }

    public static int session(int index) {
        if (index < 0) {
            index = 0;
        }
        return SESSION_COLORS[index % SESSION_COLORS.length];
    }

    /** Heatmap ramp from cool to hot for a 0..1 intensity. */
    public static int heat(double intensity) {
        double t = Math.max(0.0, Math.min(1.0, intensity));
        int r;
        int g;
        int b;
        if (t < 0.5) {
            double k = t / 0.5;
            r = (int) (40 + 60 * k);
            g = (int) (90 + 130 * k);
            b = (int) (200 - 40 * k);
        } else {
            double k = (t - 0.5) / 0.5;
            r = (int) (100 + 155 * k);
            g = (int) (220 - 130 * k);
            b = (int) (160 - 140 * k);
        }
        int alpha = (int) (90 + 140 * t);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Gap ramp: near-invisible where landings are dense, amber where none are.
     *
     * <p>The low end is fully transparent on purpose. A gap map is read for its
     * bright patches, and tinting the well-covered ground would hide the grid
     * and the landings underneath it for no gain.
     */
    public static int gap(double emptiness) {
        double t = Math.max(0.0, Math.min(1.0, emptiness));
        int r = (int) (26 + (255 - 26) * Math.pow(t, 1.15));
        int g = (int) (34 + (203 - 34) * Math.pow(t, 1.30));
        int b = (int) (48 + (85 - 48) * t);
        int alpha = (int) (225 * Math.pow(t, 0.9));
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /** The four bands the gap raster uses when the view is too wide to shade. */
    public static int gapBand(int count) {
        if (count <= 0) {
            return 0xE6FFCB55;
        }
        if (count == 1) {
            return 0xCC9C7F3A;
        }
        if (count <= 3) {
            return 0xB341506A;
        }
        return 0x991E2735;
    }

    /** Fades an existing colour toward transparent. */
    public static int withAlpha(int argb, double alphaFactor) {
        int alpha = (int) Math.max(0, Math.min(255, ((argb >>> 24) & 0xFF) * alphaFactor));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Blends toward white for a recency gradient. */
    public static int lighten(int argb, double amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
