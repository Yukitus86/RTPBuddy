package dev.rtpbuddy.ui;

/**
 * The four colours a counted gap tile can take, and the two ways of assigning
 * them.
 *
 * <p>The bands themselves never change - none, one, a few, many - because the
 * question a tile answers is "has anything ever landed in this square", and
 * that has a floor and no ceiling. Only what those four bands look like is a
 * matter of taste, and taste is what a setting is for.
 *
 * <p>Free of every Minecraft import on purpose: it is read by
 * {@link dev.rtpbuddy.hud.MinimapPlate}, which renders outside the game, and a
 * label would drag {@code Lang} and half of {@code net.minecraft} in with it.
 * The enum therefore carries the lang <em>key</em> and lets the settings screen
 * resolve it.
 */
public enum GapScheme {

    /**
     * The original: amber for untouched ground, cooling to near-black as the
     * count rises.
     *
     * <p>It is built around one bright colour and three quiet ones, so the eye
     * goes straight to the holes and the landings drawn on top stay readable.
     */
    CLASSIC(0xE6FFCB55, 0xCC9C7F3A, 0xB341506A, 0x991E2735),

    /**
     * Green where nothing has ever landed, then yellow, orange and red as the
     * count rises.
     *
     * <p>Read the other way round from a heat map on purpose: here green is the
     * square still worth going to, and red is ground already covered. The four
     * colours all carry weight, which makes the busy half of a frame as legible
     * as the empty half - and costs a little of the contrast the landings on top
     * had against {@link #CLASSIC}.
     *
     * <p>Muted rather than saturated. Four signal colours at full strength turn
     * the whole plate into a poster and leave nothing quiet enough to read the
     * landings, the route and the player against; pulled toward sage, gold,
     * terracotta and brick they still separate into four steps while the
     * picture stays something the eye can rest on during play.
     */
    TRAFFIC(0xD8548057, 0xCEC4AE5A, 0xC8BE7645, 0xC0A04A42);

    /**
     * Ink for the count written into a tile: near-black on a light band,
     * near-white on a dark one.
     *
     * <p>One dim grey for every band was fine while three of the four were dark
     * and the fourth was never written on. It stopped being fine the moment a
     * scheme gave all four real weight - a grey digit on gold and the same grey
     * on brick cannot both be read, and neither could.
     *
     * <p>Worked out from the band rather than listed per scheme, because what
     * the digit actually sits on is not the colour named above: the band is
     * translucent and the map's own dark ground shows through it, which drags
     * every one of them down. So the blend is what gets measured, and the
     * threshold sits above the midpoint - on a colour that is neither clearly
     * light nor clearly dark, white beats black.
     */
    public int bandInk(int count) {
        int argb = band(count);
        int alpha = (argb >>> 24) & 0xFF;
        double r = over((argb >> 16) & 0xFF, GROUND_R, alpha);
        double g = over((argb >> 8) & 0xFF, GROUND_G, alpha);
        double b = over(argb & 0xFF, GROUND_B, alpha);
        double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luma >= INK_SWITCH ? INK_DARK : INK_LIGHT;
    }

    /** {@link MapPalette#BACKGROUND}, split into channels. */
    private static final int GROUND_R = 0x14;
    private static final int GROUND_G = 0x17;
    private static final int GROUND_B = 0x1C;

    /** Luma at which the ink flips. Above the midpoint - see {@link #bandInk}. */
    private static final double INK_SWITCH = 128;

    private static final int INK_DARK = 0xFF12161A;
    private static final int INK_LIGHT = 0xFFF2F4F0;

    private static double over(int source, int ground, int alpha) {
        return (source * alpha + ground * (255 - alpha)) / 255.0;
    }

    private final int[] bands;

    GapScheme(int none, int one, int few, int many) {
        this.bands = new int[]{none, one, few, many};
    }

    /** The colour for a tile holding this many landings. */
    public int band(int count) {
        if (count <= 0) {
            return bands[0];
        }
        if (count == 1) {
            return bands[1];
        }
        return count <= 3 ? bands[2] : bands[3];
    }

    /** The lang key for this scheme's name. */
    public String key() {
        return "settings.map.gap_scheme." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The scheme a config string names, falling back to {@link #TRAFFIC}. */
    public static GapScheme of(String id) {
        if (id != null) {
            for (GapScheme scheme : values()) {
                if (scheme.name().equalsIgnoreCase(id)) {
                    return scheme;
                }
            }
        }
        return TRAFFIC;
    }
}
