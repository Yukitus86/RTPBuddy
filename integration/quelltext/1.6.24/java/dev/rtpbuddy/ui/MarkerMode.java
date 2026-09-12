package dev.rtpbuddy.ui;

/** How samples are drawn on the canvas. */
public enum MarkerMode {
    POINTS("marker.points"),
    PATH("marker.path"),
    /**
     * Shades the distance to the nearest landing rather than the landings
     * themselves, so the empty patches are what the eye lands on.
     *
     * <p>Called <em>Advanced</em> on the button, and the enum keeps its old name
     * so a stored {@code map.markerMode} does not have to be migrated. It
     * replaced the heatmap and the cluster view outright: all three answered a
     * question about density, and this is the only one of them that answers it
     * about the ground rather than about the dots.
     */
    GAPS("marker.gaps");

    private final String key;

    MarkerMode(String key) {
        this.key = key;
    }

    /** Translated at call time, so it follows a language change immediately. */
    public String label() {
        return dev.rtpbuddy.util.Lang.t(key);
    }

    public MarkerMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static MarkerMode parse(String name) {
        for (MarkerMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return POINTS;
    }
}
