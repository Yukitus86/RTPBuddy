package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import dev.rtpbuddy.util.Worlds;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The card shown for the landing under the cursor.
 *
 * <p>It replaces a stack of thirteen "Label: value" lines. That stack held the
 * right facts but read as a wall: every line the same weight, the coordinates
 * competing with the capture latency, and nothing to tell at a glance which
 * region the landing fell in. Here the sample number and its region own the
 * header, the coordinates get a band of their own, the measurements pair off
 * into two columns, and the recording details drop to a footer - so the eye can
 * go straight to whichever of the three it came for.
 */
public final class SampleCard {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd.MM.  HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final int PADDING = 8;
    private static final int GUTTER = 12;
    private static final int STRIPE = 3;
    private static final int MAX_WIDTH = 300;

    /** Distance the card is held off the cursor so it never sits under it. */
    private static final int CURSOR_GAP = 12;

    private SampleCard() {
    }

    /** One measurement: a dim caption over a bright value. */
    private record Pair(String label, String value) {
    }

    public static void render(DrawContext context, RtpSample sample, RtpSample previous,
                              int number, int previousNumber,
                              int mouseX, int mouseY, int screenWidth, int screenHeight) {
        TextRenderer font = UiDraw.font();
        int lineHeight = font.fontHeight;

        int dimensionColor = Worlds.dimensionColor(sample.dimension());
        ServerRegions.Cell cell = RTPBuddyClient.config().serverRegion(sample);
        int accent = cell != null ? cell.zone().color() : dimensionColor;

        String title = Lang.t("map.sample_n", number);
        String chip = cell != null
                ? cell.zone().label() + "  #" + cell.number()
                : Worlds.dimensionLabel(sample.dimension());

        String context1 = cell != null
                ? Worlds.dimensionLabel(sample.dimension())
                // The region's name, never its id: a landing from /rtp east is
                // stored as "na_east" and reads as machinery on a card.
                : Lang.t("card.requested",
                        RTPBuddyClient.config().regionLabel(sample.requestedRegion()));
        String biome = prettyBiome(sample.biome());
        if (biome != null) {
            context1 = context1 + "  " + Lang.t("word.dot") + "  " + biome;
        }

        List<Pair> pairs = pairs(sample, previous, previousNumber);
        String footer = footer(sample);
        List<String> note = sample.hasNote()
                ? UiDraw.wrap(Lang.t("card.note", sample.note()), MAX_WIDTH - PADDING * 2 - STRIPE)
                : List.of();

        // ------------------------------------------------------------ measure

        int headerWidth = font.getWidth(title) + 10 + font.getWidth(chip) + 10;
        int coordWidth = coordinateWidth(font, sample);
        int contextWidth = font.getWidth(context1);
        int footerWidth = font.getWidth(footer);

        int columnWidth = 0;
        for (Pair pair : pairs) {
            columnWidth = Math.max(columnWidth,
                    Math.max(font.getWidth(pair.label()), font.getWidth(pair.value())));
        }
        int rows = (pairs.size() + 1) / 2;
        int pairsWidth = pairs.size() > 1 ? columnWidth * 2 + GUTTER : columnWidth;

        int inner = Math.max(Math.max(headerWidth, coordWidth),
                Math.max(Math.max(contextWidth, footerWidth), pairsWidth));
        for (String line : note) {
            inner = Math.max(inner, font.getWidth(line));
        }
        int cardWidth = Math.min(MAX_WIDTH, inner + PADDING * 2 + STRIPE);
        inner = cardWidth - PADDING * 2 - STRIPE;
        // Re-split the columns against the width the card actually got, so a
        // clamped card still lines its two columns up on a shared centre.
        columnWidth = pairs.size() > 1 ? (inner - GUTTER) / 2 : inner;

        int cardHeight = PADDING
                + lineHeight + 6                      // header
                + 5 + lineHeight + 4                  // coordinate band
                + lineHeight + 2                      // dimension and biome
                + (rows > 0 ? 5 + rows * (lineHeight * 2 + 3) : 0)
                + (note.isEmpty() ? 0 : 4 + note.size() * (lineHeight + 1))
                + 5 + lineHeight
                + PADDING;

        int x = place(mouseX + CURSOR_GAP, cardWidth, screenWidth, mouseX - CURSOR_GAP - cardWidth);
        int y = Math.max(4, Math.min(screenHeight - cardHeight - 4, mouseY - 8));

        // ---------------------------------------------------------------- draw

        Theme.card(context, x, y, cardWidth, cardHeight);
        // A stripe in the dimension colour down the left edge: which world a
        // landing is in is the one fact worth reading without looking.
        context.fill(x + 1, y + 2, x + 1 + STRIPE, y + cardHeight - 2,
                MapPalette.withAlpha(dimensionColor, 0.85));

        int left = x + STRIPE + PADDING;
        int right = x + cardWidth - PADDING;
        int cursor = y + PADDING;

        context.drawText(UiDraw.font(), title, left, cursor, MapPalette.TEXT, false);
        drawChip(context, chip, right, cursor - 2, accent);
        cursor += lineHeight + 6;

        Theme.rule(context, left, cursor - 3, right - left);
        cursor += 2;
        drawCoordinates(context, sample, left, cursor, right);
        cursor += lineHeight + 4;

        UiDraw.text(context, UiDraw.trim(context1, right - left), left, cursor, MapPalette.TEXT_DIM);
        cursor += lineHeight + 2;

        if (rows > 0) {
            cursor += 5;
            for (int row = 0; row < rows; row++) {
                drawPair(context, pairs.get(row * 2), left, cursor, columnWidth);
                int second = row * 2 + 1;
                if (second < pairs.size()) {
                    drawPair(context, pairs.get(second), left + columnWidth + GUTTER, cursor,
                            columnWidth);
                }
                cursor += lineHeight * 2 + 3;
            }
        }

        if (!note.isEmpty()) {
            cursor += 4;
            for (String line : note) {
                UiDraw.text(context, line, left, cursor, MapPalette.HIGHLIGHT);
                cursor += lineHeight + 1;
            }
        }

        cursor += 5;
        Theme.rule(context, left, cursor - 4, right - left);
        UiDraw.text(context, UiDraw.trim(footer, right - left), left, cursor, Theme.TEXT_FAINT);
    }

    // --------------------------------------------------------------- sections

    /**
     * The coordinates on one line, each axis a dim letter in front of a bright
     * number. Three separate rows of "X: value" was a third of the old wall for
     * the one thing a map tooltip exists to tell you.
     */
    private static void drawCoordinates(DrawContext context, RtpSample sample,
                                        int left, int y, int right) {
        int cursor = left;
        cursor = drawAxis(context, "X", Numbers.fixed(sample.x(), 1), cursor, y);
        cursor = drawAxis(context, "Y", Numbers.fixed(sample.y(), 1), cursor + 8, y);
        drawAxis(context, "Z", Numbers.fixed(sample.z(), 1), cursor + 8, y);
    }

    private static int drawAxis(DrawContext context, String axis, String value, int x, int y) {
        TextRenderer font = UiDraw.font();
        UiDraw.text(context, axis, x, y, Theme.TEXT_FAINT);
        int valueX = x + font.getWidth(axis) + 3;
        UiDraw.text(context, value, valueX, y, MapPalette.TEXT);
        return valueX + font.getWidth(value);
    }

    private static int coordinateWidth(TextRenderer font, RtpSample sample) {
        return font.getWidth("X") + 3 + font.getWidth(Numbers.fixed(sample.x(), 1)) + 8
                + font.getWidth("Y") + 3 + font.getWidth(Numbers.fixed(sample.y(), 1)) + 8
                + font.getWidth("Z") + 3 + font.getWidth(Numbers.fixed(sample.z(), 1));
    }

    private static void drawPair(DrawContext context, Pair pair, int x, int y, int width) {
        UiDraw.text(context, UiDraw.trim(pair.label(), width), x, y, Theme.TEXT_FAINT);
        UiDraw.text(context, UiDraw.trim(pair.value(), width), x, y + UiDraw.font().fontHeight + 1,
                MapPalette.TEXT);
    }

    private static void drawChip(DrawContext context, String label, int rightX, int y, int accent) {
        int width = UiDraw.font().getWidth(label) + 14;
        int x = rightX - width;
        Theme.roundRect(context, x, y, width, 13, Theme.RADIUS_PILL,
                MapPalette.withAlpha(accent, 0.22));
        context.fill(x + 5, y + 5, x + 8, y + 8, accent);
        UiDraw.text(context, label, x + 11, y + 3, MapPalette.TEXT);
    }

    // ------------------------------------------------------------------- data

    private static List<Pair> pairs(RtpSample sample, RtpSample previous, int previousNumber) {
        List<Pair> pairs = new ArrayList<>(6);
        pairs.add(new Pair(Lang.t("card.distance"), Numbers.compact(sample.distanceFromOrigin())));
        if (sample.travelDistance() != null) {
            pairs.add(new Pair(Lang.t("card.jump"), Numbers.compact(sample.travelDistance())));
        }
        if (previous != null) {
            double gap = Math.hypot(sample.x() - previous.x(), sample.z() - previous.z());
            long dt = sample.timestamp() - previous.timestamp();
            pairs.add(new Pair(Lang.t("card.since", previousNumber),
                    Numbers.compact(gap) + "  " + Lang.t("word.dot") + "  " + Numbers.duration(dt)));
        }
        if (sample.latencyMs() != null) {
            pairs.add(new Pair(Lang.t("card.latency"), sample.latencyMs() + " ms"));
        }
        if (sample.surfaceY() != null) {
            pairs.add(new Pair(Lang.t("card.surface"), String.valueOf(sample.surfaceY())));
        }
        return pairs;
    }

    private static String footer(RtpSample sample) {
        String dot = "  " + Lang.t("word.dot") + "  ";
        StringBuilder footer = new StringBuilder(STAMP.format(Instant.ofEpochMilli(sample.timestamp())));
        footer.append(dot).append(sample.captureMode());
        if (sample.server() != null && !sample.server().isBlank()) {
            footer.append(dot).append(sample.server());
        }
        return footer.toString();
    }

    /**
     * "minecraft:snowy_taiga" reads as "Snowy taiga" on a card this small.
     *
     * <p>Shared with the sample list, which shows the same short form in its
     * third column.
     */
    public static String prettyBiome(String biome) {
        if (biome == null || biome.isBlank()) {
            return null;
        }
        String name = biome.substring(biome.indexOf(':') + 1).replace('_', ' ');
        if (name.isEmpty()) {
            return null;
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /** Prefers the side the cursor leaves room for, and never runs off the screen. */
    private static int place(int preferred, int cardWidth, int screenWidth, int fallback) {
        if (preferred + cardWidth + 4 <= screenWidth) {
            return preferred;
        }
        if (fallback >= 4) {
            return fallback;
        }
        return Math.max(4, screenWidth - cardWidth - 4);
    }
}
