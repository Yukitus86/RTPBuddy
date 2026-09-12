package dev.rtpbuddy.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * The visual language shared by every RTPBuddy screen: colours, corner radii and
 * the handful of primitives Minecraft's {@link DrawContext} does not provide.
 *
 * <p>Vanilla only draws axis-aligned rectangles, so rounded corners are built
 * here out of one filled row per pixel of the corner arc. That costs a couple of
 * dozen extra {@code fill} calls per widget, which is nothing next to a chart or
 * the map canvas, and it is what lets the whole UI stop looking like a stack of
 * grey boxes.
 */
public final class Theme {

    private Theme() {
    }

    // ------------------------------------------------------------------ colour

    /** Full-screen dim behind a dialog. */
    public static final int SCRIM = 0xD00A0D12;

    /** The card a screen's content sits on. */
    public static final int SURFACE = 0xF2161B22;

    /** Header and footer strips inside a card, one step lighter than the card. */
    public static final int SURFACE_HEADER = 0xFF1B212A;

    /** Resting fill of a control. */
    public static final int CONTROL = 0xFF1F262F;
    public static final int CONTROL_HOVER = 0xFF2A333F;
    public static final int CONTROL_PRESS = 0xFF161C24;
    public static final int CONTROL_DISABLED = 0xFF191E25;

    public static final int LINE = 0xFF2C333D;
    public static final int LINE_SOFT = 0xFF232A33;
    public static final int LINE_BRIGHT = 0xFF3D4855;

    public static final int TEXT = 0xFFE6E9EE;
    public static final int TEXT_DIM = 0xFF8D97A6;
    public static final int TEXT_FAINT = 0xFF5F6977;
    public static final int TEXT_DISABLED = 0xFF4E5764;

    /** One accent, used sparingly: focus, the active tab, the primary action. */
    public static final int ACCENT = 0xFF57B6E8;
    public static final int ACCENT_DEEP = 0xFF2B6E93;
    public static final int ACCENT_WASH = 0x3357B6E8;
    public static final int ACCENT_TEXT = 0xFF0C1620;

    /** Semantic colours, kept apart from the accent so state never reads as chrome. */
    public static final int ON = 0xFF5FD08A;
    public static final int OFF = 0xFF667384;
    public static final int WARN = 0xFFE8B457;
    public static final int DANGER = 0xFFE86A5C;

    /** Corner radii. Cards are softer than the controls sitting on them. */
    public static final int RADIUS_CARD = 6;
    public static final int RADIUS_CONTROL = 4;
    public static final int RADIUS_PILL = 3;

    // -------------------------------------------------------------- primitives

    /**
     * Filled rectangle with rounded corners.
     *
     * <p>Each corner row is inset by the horizontal distance from the arc, taken
     * at the middle of the row so the curve does not look a pixel too square.
     */
    public static void roundRect(DrawContext context, int x, int y, int width, int height,
                                 int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            context.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            context.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /** Rounded on the top two corners only - the shape a tab needs. */
    public static void roundRectTop(DrawContext context, int x, int y, int width, int height,
                                    int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height)));
        context.fill(x, y + r, x + width, y + height, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            context.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
        }
    }

    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        return radius - (int) Math.round(Math.sqrt(Math.max(0.0, radius * (double) radius - dy * dy)));
    }

    /** A one-pixel rounded outline with an optional fill inside it. */
    public static void roundOutline(DrawContext context, int x, int y, int width, int height,
                                    int radius, int borderColor, int fillColor) {
        roundRect(context, x, y, width, height, radius, borderColor);
        if (fillColor != 0) {
            roundRect(context, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fillColor);
        }
    }

    /**
     * The card every screen is built on: soft shadow, rounded body, hairline edge.
     * The shadow is two translucent rings rather than a blur, which is enough to
     * lift the card off the world behind it.
     */
    public static void card(DrawContext context, int x, int y, int width, int height) {
        roundRect(context, x - 2, y - 1, width + 4, height + 4, RADIUS_CARD + 2, 0x30000000);
        roundRect(context, x - 1, y, width + 2, height + 2, RADIUS_CARD + 1, 0x40000000);
        roundOutline(context, x, y, width, height, RADIUS_CARD, LINE, SURFACE);
    }

    /** Hairline rule used to separate a header or footer from the content. */
    public static void rule(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, LINE_SOFT);
    }

    /**
     * Uppercase section label with a pixel of tracking between characters.
     *
     * <p>Minecraft ships one font, so hierarchy has to come from case, colour and
     * letter spacing instead of from a second typeface.
     */
    public static int sectionLabel(DrawContext context, String label, int x, int y, int width) {
        int cursor = x;
        String upper = label.toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            String ch = String.valueOf(upper.charAt(i));
            UiDraw.text(context, ch, cursor, y, ACCENT);
            cursor += UiDraw.font().getWidth(ch) + 1;
        }
        int lineY = y + 4;
        int lineX = cursor + 5;
        if (lineX < x + width) {
            context.fill(lineX, lineY, x + width, lineY + 1, LINE_SOFT);
        }
        return y + UiDraw.lineHeight() + 3;
    }

    /** Small rounded label chip, used for counts and states in the bars. */
    public static int chip(DrawContext context, String label, int x, int y, int background, int textColor) {
        int textWidth = UiDraw.font().getWidth(label);
        int chipWidth = textWidth + 10;
        roundRect(context, x, y, chipWidth, 12, RADIUS_PILL, background);
        UiDraw.text(context, label, x + 5, y + 2, textColor);
        return x + chipWidth;
    }

    /** Vertical scrollbar for a scissored viewport. Draws nothing when everything fits. */
    public static void scrollbar(DrawContext context, int x, int y, int height,
                                 int contentHeight, int scroll) {
        if (contentHeight <= height) {
            return;
        }
        roundRect(context, x, y, 3, height, 1, LINE_SOFT);
        int thumbHeight = Math.max(16, height * height / contentHeight);
        int travel = height - thumbHeight;
        int maxScroll = contentHeight - height;
        int thumbY = y + (maxScroll <= 0 ? 0 : travel * scroll / maxScroll);
        roundRect(context, x, thumbY, 3, thumbHeight, 1, LINE_BRIGHT);
    }

    /** Mixes two ARGB colours; {@code amount} 0 keeps {@code from}. */
    public static int mix(int from, int to, double amount) {
        double t = Math.max(0.0, Math.min(1.0, amount));
        int a = channel(from, 24, to, t);
        int r = channel(from, 16, to, t);
        int g = channel(from, 8, to, t);
        int b = channel(from, 0, to, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int shift, int to, double t) {
        int a = (from >>> shift) & 0xFF;
        int b = (to >>> shift) & 0xFF;
        return (int) Math.round(a + (b - a) * t);
    }
}
