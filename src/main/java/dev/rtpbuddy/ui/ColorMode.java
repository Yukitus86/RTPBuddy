package dev.rtpbuddy.ui;

/** What a marker's colour encodes. */
public enum ColorMode {
    DIMENSION("color.dimension"),
    REGION("color.region"),
    RECENCY("color.recency"),
    SESSION("color.session"),
    /**
     * By biome family rather than by biome: the recorded data holds close to
     * fifty biomes, and fifty hues on one plot is noise, not a key.
     */
    BIOME("color.biome");

    private final String key;

    ColorMode(String key) {
        this.key = key;
    }

    /** Translated at call time, so it follows a language change immediately. */
    public String label() {
        return dev.rtpbuddy.util.Lang.t(key);
    }

    public ColorMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static ColorMode parse(String name) {
        for (ColorMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return DIMENSION;
    }
}
