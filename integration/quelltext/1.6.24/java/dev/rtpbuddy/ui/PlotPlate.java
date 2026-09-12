package dev.rtpbuddy.ui;

import dev.rtpbuddy.data.RtpSample;

import java.util.Arrays;
import java.util.List;

/**
 * The landings and the route between them, painted into a grid of ARGB pixels.
 *
 * <p>Why a picture and not rectangles. Everything the canvas draws goes through
 * {@code DrawContext.fill}, and on 1.21.11 each of those allocates a render
 * state <em>and</em> a copy of the current matrix. A round marker is five of
 * them, a hollow one eight, and a route segment is one more plus a matrix push
 * and rotate. Two thousand landings framed at once is some twenty thousand
 * rectangles a frame, which is not arithmetic the machine minds - it is
 * allocation it does.
 *
 * <p>So past a certain density the whole layer is baked once, when something
 * about it actually changes, and every frame in between is a single textured
 * quad. The thinning that had to happen when this was drawn rectangle by
 * rectangle is then not needed at all: the picture holds every landing, round,
 * and the route with its chevrons, because in here they cost pixels rather than
 * draw calls.
 *
 * <p>Free of every Minecraft import, like {@code MinimapPlate} and
 * {@link CellPlate}, so the picture can be rendered to a file and looked at
 * without starting a game - which is the only way to check a baked layer at all,
 * since once it is a texture there is nothing left to inspect but pixels.
 */
public final class PlotPlate {

    /** What the caller has to tell the plate about one frame's geometry. */
    public interface Projection {
        double screenX(double worldX);

        double screenZ(double worldZ);
    }

    private int[] pixels = new int[0];
    private int width;
    private int height;

    public int[] pixels() {
        return pixels;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Paints one layer.
     *
     * @param colors   marker colour per sample, same order and length as samples
     * @param route    segment colours, index i is the leg from i-1 to i, or null
     *                 for no route at all; a zero entry skips that leg
     * @param radius   marker radius in pixels
     * @param round    discs rather than squares
     * @param hollow   markers drawn as outlines, which is what path mode wants
     * @param chevrons draw a direction mark at the middle of a long leg
     */
    public void bake(int width, int height, List<RtpSample> samples, int[] colors,
                     int[] route, Projection view, int radius, boolean round,
                     boolean hollow, boolean chevrons, int holeColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (this.width != width || this.height != height || pixels.length < width * height) {
            pixels = new int[width * height];
        }
        this.width = width;
        this.height = height;
        // Transparent, not black: the grid, the region tints and the guards are
        // painted underneath this and have to show through everywhere the
        // landings are not.
        Arrays.fill(pixels, 0, width * height, 0x00000000);

        if (route != null) {
            for (int i = 1; i < samples.size() && i < route.length; i++) {
                int color = route[i];
                if (color == 0) {
                    continue;
                }
                RtpSample a = samples.get(i - 1);
                RtpSample b = samples.get(i);
                double ax = view.screenX(a.x());
                double ay = view.screenZ(a.z());
                double bx = view.screenX(b.x());
                double by = view.screenZ(b.z());
                line(ax, ay, bx, by, color);
                if (chevrons) {
                    chevron(ax, ay, bx, by, color);
                }
            }
        }

        for (int i = 0; i < samples.size(); i++) {
            int color = colors[i];
            if (color == 0) {
                continue;
            }
            RtpSample sample = samples.get(i);
            double sx = view.screenX(sample.x());
            double sy = view.screenZ(sample.z());
            if (sx < -radius - 2 || sy < -radius - 2
                    || sx > width + radius + 2 || sy > height + radius + 2) {
                continue;
            }
            int cx = (int) Math.round(sx);
            int cy = (int) Math.round(sy);
            if (hollow) {
                disc(cx, cy, radius, round, color);
                disc(cx, cy, radius - 1, round, holeColor);
            } else {
                disc(cx, cy, radius, round, color);
            }
        }
    }

    // ------------------------------------------------------------- primitives

    /** Half-widths per row of a disc, worked out once per radius. */
    private static final int[][] DISC_ROWS = buildDiscRows(10);

    private static int[][] buildDiscRows(int maxRadius) {
        int[][] table = new int[maxRadius + 1][];
        for (int radius = 0; radius <= maxRadius; radius++) {
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

    private void disc(int cx, int cy, int radius, boolean round, int color) {
        if (radius < 0) {
            return;
        }
        int[] rows = round && radius < DISC_ROWS.length ? DISC_ROWS[radius] : null;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = rows == null ? radius : rows[dy + radius];
            int y = cy + dy;
            if (y < 0 || y >= height) {
                continue;
            }
            int from = Math.max(0, cx - half);
            int to = Math.min(width - 1, cx + half);
            int row = y * width;
            for (int x = from; x <= to; x++) {
                pixels[row + x] = blend(pixels[row + x], color);
            }
        }
    }

    /**
     * A one-pixel line, clipped to the plate.
     *
     * <p>Bresenham rather than a rotated rectangle: in here a line is pixels
     * either way, and stepping them is both exact and free of the matrix work
     * the draw-call version needs.
     */
    private void line(double x1, double y1, double x2, double y2, int color) {
        // Cheap reject before any stepping: a route leg is usually far longer
        // than the canvas and most of them miss it entirely.
        if (Math.max(x1, x2) < 0 || Math.min(x1, x2) > width
                || Math.max(y1, y2) < 0 || Math.min(y1, y2) > height) {
            return;
        }
        int ax = (int) Math.round(x1);
        int ay = (int) Math.round(y1);
        int bx = (int) Math.round(x2);
        int by = (int) Math.round(y2);

        int dx = Math.abs(bx - ax);
        int dy = -Math.abs(by - ay);
        int stepX = ax < bx ? 1 : -1;
        int stepY = ay < by ? 1 : -1;
        int error = dx + dy;
        // A leg can run far outside the plate, so the walk is bounded by the
        // steps it could possibly take inside it rather than trusted to end.
        int guard = dx - dy + 4;

        while (guard-- > 0) {
            if (ax >= 0 && ax < width && ay >= 0 && ay < height) {
                int index = ay * width + ax;
                pixels[index] = blend(pixels[index], color);
            }
            if (ax == bx && ay == by) {
                return;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                ax += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                ay += stepY;
            }
        }
    }

    /** The two arms of a direction mark, at the middle of a long enough leg. */
    private void chevron(double ax, double ay, double bx, double by, int color) {
        double dx = bx - ax;
        double dy = by - ay;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < CHEVRON_MIN_SEGMENT) {
            return;
        }
        double midX = ax + dx * 0.5;
        double midY = ay + dy * 0.5;
        if (midX < 0 || midY < 0 || midX > width || midY > height) {
            return;
        }
        double angle = Math.atan2(dy, dx);
        for (double spread : CHEVRON_SPREAD) {
            line(midX, midY,
                    midX + Math.cos(angle + spread) * CHEVRON_SIZE,
                    midY + Math.sin(angle + spread) * CHEVRON_SIZE, color);
        }
    }

    private static final double[] CHEVRON_SPREAD = {2.6, -2.6};
    private static final int CHEVRON_SIZE = 6;
    private static final int CHEVRON_MIN_SEGMENT = 26;

    /** Source over destination, both premultiplied by nothing. */
    private static int blend(int dst, int src) {
        int alpha = src >>> 24;
        if (alpha == 0) {
            return dst;
        }
        if (alpha == 0xFF) {
            return src;
        }
        int dstAlpha = dst >>> 24;
        if (dstAlpha == 0) {
            return src;
        }
        double a = alpha / 255.0;
        int r = (int) Math.round(((src >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
        int g = (int) Math.round(((src >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
        int b = (int) Math.round((src & 0xFF) * a + (dst & 0xFF) * (1 - a));
        int outAlpha = Math.max(alpha, dstAlpha);
        return (outAlpha << 24) | (r << 16) | (g << 8) | b;
    }
}
