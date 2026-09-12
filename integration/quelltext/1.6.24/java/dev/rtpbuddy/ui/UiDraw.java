package dev.rtpbuddy.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small drawing helpers shared by the panels. */
public final class UiDraw {

    private UiDraw() {
    }

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static int lineHeight() {
        return font().fontHeight + 2;
    }

    /** The rounded card every panel and detail box is drawn on. */
    public static void panel(DrawContext context, int x, int y, int width, int height) {
        Theme.roundOutline(context, x, y, width, height, Theme.RADIUS_CARD,
                Theme.LINE, MapPalette.PANEL);
    }

    public static void border(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    public static void text(DrawContext context, String value, int x, int y, int color) {
        context.drawText(font(), value, x, y, color, false);
    }

    public static void textRight(DrawContext context, String value, int rightX, int y, int color) {
        context.drawText(font(), value, rightX - font().getWidth(value), y, color, false);
    }

    /** Section heading with a hairline underneath. */
    public static int heading(DrawContext context, String label, int x, int y, int width) {
        return Theme.sectionLabel(context, label, x, y, width) - 1;
    }

    /** Label on the left, value right-aligned. Returns the next y. */
    public static int row(DrawContext context, String label, String value, int x, int y, int width) {
        text(context, label, x, y, MapPalette.TEXT_DIM);
        textRight(context, value, x + width, y, MapPalette.TEXT);
        return y + lineHeight();
    }

    /** Horizontal proportion bar used for quadrant and category shares. */
    public static int bar(DrawContext context, String label, double fraction, String value,
                          int x, int y, int width, int color) {
        text(context, label, x, y, MapPalette.TEXT_DIM);
        textRight(context, value, x + width, y, MapPalette.TEXT);
        int barY = y + font().fontHeight + 1;
        context.fill(x, barY, x + width, barY + 3, MapPalette.PANEL_BORDER);
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * width);
        context.fill(x, barY, x + filled, barY + 3, color);
        return barY + 6;
    }

    /** Vertical bar chart. Returns the next y. */
    public static int histogram(DrawContext context, dev.rtpbuddy.stats.Histogram histogram,
                                int x, int y, int width, int height, int color) {
        if (histogram.buckets() == 0) {
            return y;
        }
        int buckets = histogram.buckets();
        int gap = buckets > 20 ? 0 : 1;
        int barWidth = Math.max(1, (width - gap * (buckets - 1)) / buckets);
        context.fill(x, y + height, x + width, y + height + 1, MapPalette.PANEL_BORDER);

        for (int i = 0; i < buckets; i++) {
            int barHeight = (int) Math.round(histogram.fraction(i) * height);
            int barX = x + i * (barWidth + gap);
            if (barHeight > 0) {
                context.fill(barX, y + height - barHeight, barX + barWidth, y + height, color);
            }
        }
        return y + height + 2;
    }

    /**
     * Compass rose for the 16-sector angular histogram: each sector is drawn as a
     * spoke whose length is its share of the busiest sector.
     */
    public static int rose(DrawContext context, dev.rtpbuddy.stats.Histogram compass,
                           int x, int y, int size, int color) {
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = size / 2 - 2;

        context.fill(centerX - radius, centerY, centerX + radius, centerY + 1, MapPalette.PANEL_BORDER);
        context.fill(centerX, centerY - radius, centerX + 1, centerY + radius, MapPalette.PANEL_BORDER);

        for (int i = 0; i < compass.buckets(); i++) {
            double fraction = compass.fraction(i);
            if (fraction <= 0) {
                continue;
            }
            // Bucket 0 is north; bearings run clockwise.
            double angle = Math.toRadians(i * 360.0 / compass.buckets());
            double length = radius * fraction;
            int endX = (int) Math.round(centerX + Math.sin(angle) * length);
            int endY = (int) Math.round(centerY - Math.cos(angle) * length);
            line(context, centerX, centerY, endX, endY, color);
        }

        text(context, "N", centerX - 2, y - 1, MapPalette.TEXT_DIM);
        return y + size + 2;
    }

    /**
     * A straight line, drawn as one rotated quad.
     *
     * <p>Not plotted pixel by pixel: every {@code fill} allocates a matrix and a
     * render-state object, so a per-pixel line costs one allocation per pixel.
     * The rose has sixteen short spokes and would survive it, but there is no
     * reason to keep a second copy of the version that does not.
     */
    public static void line(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        org.joml.Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x1, y1 - 0.5f);
        matrices.rotate((float) Math.atan2(dy, dx));
        context.fill(0, 0, (int) Math.round(length), 1, color);
        matrices.popMatrix();
    }

    /**
     * Greedy word wrap to a pixel width. Used for the settings tooltips, which
     * are built as plain strings and so cannot go through the vanilla text
     * wrapper without a round trip through {@code Text}.
     */
    public static java.util.List<String> wrap(String value, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font().getWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Trims a string to fit a pixel width, appending an ellipsis. */
    public static String trim(String value, int maxWidth) {
        TextRenderer font = font();
        if (font.getWidth(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int budget = maxWidth - font.getWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (font.getWidth(sb.toString() + c) > budget) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }
}
