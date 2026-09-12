package dev.rtpbuddy.hud;

import dev.rtpbuddy.util.Lang;

/**
 * Which corner the capture overlay hangs from.
 *
 * <p>The overlay stores a corner plus an inward offset rather than a raw screen
 * position, so it stays where it was put when the window is resized or the GUI
 * scale changes - a position picked at scale 2 would sit off-screen at scale 4.
 */
public enum HudAnchor {

    TOP_LEFT("hud.anchor.top_left", false, false, false),
    TOP_CENTER("hud.anchor.top_center", false, false, true),
    TOP_RIGHT("hud.anchor.top_right", true, false, false),
    BOTTOM_LEFT("hud.anchor.bottom_left", false, true, false),
    BOTTOM_CENTER("hud.anchor.bottom_center", false, true, true),
    BOTTOM_RIGHT("hud.anchor.bottom_right", true, true, false);

    private final String key;
    private final boolean right;
    private final boolean bottom;
    private final boolean centered;

    HudAnchor(String key, boolean right, boolean bottom, boolean centered) {
        this.key = key;
        this.right = right;
        this.bottom = bottom;
        this.centered = centered;
    }

    public String label() {
        return Lang.t(key);
    }

    public boolean right() {
        return right;
    }

    public boolean bottom() {
        return bottom;
    }

    public boolean centered() {
        return centered;
    }

    public HudAnchor next() {
        HudAnchor[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Screen x of the box's left edge for this anchor. */
    public int screenX(int screenWidth, int boxWidth, int offsetX) {
        if (centered) {
            return (screenWidth - boxWidth) / 2 + offsetX;
        }
        return right ? screenWidth - boxWidth - offsetX : offsetX;
    }

    /** Screen y of the box's top edge for this anchor. */
    public int screenY(int screenHeight, int boxHeight, int offsetY) {
        return bottom ? screenHeight - boxHeight - offsetY : offsetY;
    }

    /** The inward offsets that put a box of this size at this screen position. */
    public int offsetX(int screenWidth, int boxWidth, int screenX) {
        if (centered) {
            return screenX - (screenWidth - boxWidth) / 2;
        }
        return right ? screenWidth - boxWidth - screenX : screenX;
    }

    public int offsetY(int screenHeight, int boxHeight, int screenY) {
        return bottom ? screenHeight - boxHeight - screenY : screenY;
    }

    /**
     * The anchor a box at this position is closest to, so dragging it into a
     * corner leaves it bound to that corner rather than to wherever it started.
     */
    public static HudAnchor nearest(int screenWidth, int screenHeight,
                                    int boxX, int boxY, int boxWidth, int boxHeight) {
        double centerX = boxX + boxWidth / 2.0;
        double centerY = boxY + boxHeight / 2.0;
        boolean bottom = centerY > screenHeight / 2.0;
        // The centre band is deliberately narrow: a box only counts as centred
        // when it really is, not merely because it drifted past a third.
        boolean centered = Math.abs(centerX - screenWidth / 2.0) < screenWidth * 0.08;
        if (centered) {
            return bottom ? BOTTOM_CENTER : TOP_CENTER;
        }
        boolean right = centerX > screenWidth / 2.0;
        if (bottom) {
            return right ? BOTTOM_RIGHT : BOTTOM_LEFT;
        }
        return right ? TOP_RIGHT : TOP_LEFT;
    }

    public static HudAnchor parse(String name) {
        for (HudAnchor anchor : values()) {
            if (anchor.name().equalsIgnoreCase(name)) {
                return anchor;
            }
        }
        return TOP_LEFT;
    }
}
