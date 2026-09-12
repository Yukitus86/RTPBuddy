package dev.rtpbuddy.hud;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.capture.RtpCaptureController;
import dev.rtpbuddy.config.MapConfig;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.ui.SampleCard;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import dev.rtpbuddy.util.Worlds;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact corner overlay: capture state, session counters and the last sample.
 *
 * <p>Where it sits, how big it is and which rows it carries are all set by the
 * player. The game screen is crowded, and an overlay nailed to the top left
 * corner will sooner or later land on top of another mod's.
 */
public class CaptureHud implements HudElement {

    public static final int COLOR_TEXT = 0xFFE6E6E6;
    public static final int COLOR_DIM = 0xFF9A9A9A;
    public static final int COLOR_ARMED = 0xFFFFC85C;
    public static final int COLOR_SETTLING = 0xFF7FD3F5;
    public static final int COLOR_OK = 0xFF7FD37F;

    private static final long STATUS_VISIBLE_MILLIS = 6_000L;

    /** Padding between the plate's edge and the text inside it. */
    public static final int PADDING = 4;

    /**
     * One row of the overlay.
     *
     * <p>A row may carry a second segment in its own colour, which is what lets
     * "#12" stay plain white while the region beside it is painted in that
     * region's colour. Rows without one pass null and behave exactly as before.
     */
    public record Line(String text, int color, String tail, int tailColor) {

        public Line(String text, int color) {
            this(text, color, null, 0);
        }

        /** Gap between the two segments, in font pixels. */
        static final int TAIL_GAP = 5;

        int width(TextRenderer font) {
            int width = font.getWidth(text);
            if (tail != null && !tail.isEmpty()) {
                width += TAIL_GAP + font.getWidth(tail);
            }
            return width;
        }
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        MapConfig map = RTPBuddyClient.config().map;
        if (!map.hudEnabled) {
            return;
        }

        List<Line> lines = buildLines(map);
        if (lines.isEmpty()) {
            return;
        }
        drawAnchored(context, map, lines,
                context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }

    /** Draws the overlay where the config says it belongs on a screen this size. */
    public static void drawAnchored(DrawContext context, MapConfig map, List<Line> lines,
                                    int screenWidth, int screenHeight) {
        HudAnchor anchor = HudAnchor.parse(map.hudAnchor);
        int boxWidth = width(lines, map.hudScale);
        int boxHeight = height(lines, map.hudScale);
        int x = anchor.screenX(screenWidth, boxWidth, map.hudX);
        int y = anchor.screenY(screenHeight, boxHeight, map.hudY);
        draw(context, map, lines, x, y);
    }

    /**
     * Draws the overlay with its top left corner at the given screen position.
     *
     * <p>Scaling goes through the matrix rather than through a bigger font,
     * because the game has exactly one font: the plate is drawn at 1:1 and then
     * multiplied, which keeps padding, row spacing and text in proportion.
     */
    public static void draw(DrawContext context, MapConfig map, List<Line> lines, int x, int y) {
        if (lines.isEmpty()) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        float scale = (float) clampScale(map.hudScale);
        int lineHeight = font.fontHeight + 1;
        int inner = 0;
        for (Line line : lines) {
            inner = Math.max(inner, line.width(font));
        }

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);

        if (map.hudBackground) {
            int alpha = (int) Math.round(clampOpacity(map.hudBackgroundOpacity) * 2.55);
            context.fill(0, 0, inner + PADDING * 2, lines.size() * lineHeight + PADDING * 2,
                    alpha << 24);
        }

        int textY = PADDING;
        for (Line line : lines) {
            context.drawText(font, line.text(), PADDING, textY, line.color(), map.hudTextShadow);
            if (line.tail() != null && !line.tail().isEmpty()) {
                context.drawText(font, line.tail(),
                        PADDING + font.getWidth(line.text()) + Line.TAIL_GAP, textY,
                        line.tailColor(), map.hudTextShadow);
            }
            textY += lineHeight;
        }
        matrices.popMatrix();
    }

    /** Width of the drawn plate in screen pixels, scale included. */
    public static int width(List<Line> lines, double scale) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int inner = 0;
        for (Line line : lines) {
            inner = Math.max(inner, line.width(font));
        }
        return (int) Math.ceil((inner + PADDING * 2) * clampScale(scale));
    }

    /** Height of the drawn plate in screen pixels, scale included. */
    public static int height(List<Line> lines, double scale) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        return (int) Math.ceil((lines.size() * (font.fontHeight + 1) + PADDING * 2)
                * clampScale(scale));
    }

    public static double clampScale(double scale) {
        return Math.max(0.5, Math.min(2.0, scale));
    }

    public static int clampOpacity(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    /** The rows as they stand right now, honouring the per-row switches. */
    public static List<Line> buildLines(MapConfig map) {
        List<Line> lines = new ArrayList<>();
        RtpCaptureController capture = RTPBuddyClient.capture();

        var auto = RTPBuddyClient.autoRtp();
        if (map.hudShowAuto && auto.running()) {
            // Held runs get their own row and their own colour: the countdown is
            // frozen, and a dim "held" reads at a glance as "nothing is being
            // sent" where a stalled number would not.
            if (auto.paused()) {
                lines.add(new Line(Lang.t("hud.auto_held",
                        Math.round(auto.secondsUntilNext()), auto.sentThisRun()), COLOR_DIM));
            } else if (auto.manualStep()) {
                // No countdown exists, so none is shown. A number here would be
                // a promise the run has no intention of keeping.
                lines.add(new Line(Lang.t("hud.auto_manual", auto.sentThisRun()), COLOR_ARMED));
            } else {
                lines.add(new Line(Lang.t("hud.auto",
                        Math.round(auto.secondsUntilNext()), auto.sentThisRun()), COLOR_ARMED));
            }
            // A run that is looking for something says so, and says how many
            // landings it has already turned down. Without it a search order is
            // invisible until the moment it fires.
            if (auto.searching()) {
                lines.add(new Line(Lang.t("hud.searching",
                        dev.rtpbuddy.capture.FindRule.describe(RTPBuddyClient.config().autoRtp),
                        auto.missedThisRun()), COLOR_DIM));
            }
        }

        if (map.hudShowState) {
            String stateText = switch (capture.state()) {
                case IDLE -> Lang.t(capture.paused() ? "hud.paused" : "hud.watching");
                case ARMED -> Lang.t("hud.armed",
                        RTPBuddyClient.config().regionLabel(capture.pendingRegion()),
                        Numbers.fixed(capture.remainingArmTicks() / 20.0, 1));
                case SETTLING -> Lang.t("hud.landing");
            };
            int stateColor = switch (capture.state()) {
                case IDLE -> capture.paused() ? COLOR_DIM : COLOR_TEXT;
                case ARMED -> COLOR_ARMED;
                case SETTLING -> COLOR_SETTLING;
            };
            lines.add(new Line(stateText, stateColor));
        }

        if (map.hudShowCounters) {
            lines.add(new Line(Lang.t("hud.counters", capture.captured(), RTPBuddyClient.store().size())
                    + (capture.timedOut() > 0 ? Lang.t("hud.missed", capture.timedOut()) : "")
                    + (capture.rejected() > 0 ? Lang.t("hud.rejected", capture.rejected()) : ""),
                    COLOR_DIM));
        }

        RtpSample last = capture.lastSample();
        if (map.hudShowLast && last != null) {
            // Three short rows rather than one long one. The number is what the
            // player counts by, the region and biome are what they went looking
            // for, and the coordinates are what they copy - each deserves its
            // own line rather than being run together into a sentence.
            ServerRegions.Cell cell = RTPBuddyClient.config().serverRegion(last);
            String region = cell != null
                    ? Lang.t("hud.region_cell", cell.zone().label(), cell.number())
                    : RTPBuddyClient.config().regionLabel(last.requestedRegion());
            int regionColor = cell != null
                    ? cell.zone().color()
                    : Worlds.dimensionColor(last.dimension());

            lines.add(new Line(Lang.t("map.sample_n",
                    RTPBuddyClient.store().indexInSession(last)), COLOR_OK, region, regionColor));

            if (map.hudShowContext) {
                String biome = SampleCard.prettyBiome(last.biome());
                String dimension = Worlds.dimensionLabel(last.dimension());
                lines.add(new Line(biome == null
                        ? dimension
                        : biome + "  " + Lang.t("word.dot") + "  " + dimension, COLOR_DIM));
            }

            lines.add(new Line(Lang.t("hud.last_pos",
                    Math.round(last.x()), Math.round(last.y()), Math.round(last.z())), COLOR_TEXT,
                    Numbers.compact(last.distanceFromOrigin()), COLOR_DIM));
        }

        String status = capture.statusMessage();
        if (map.hudShowStatus && status != null && !status.isEmpty()
                && capture.statusMessageAge() < STATUS_VISIBLE_MILLIS) {
            lines.add(new Line(status, COLOR_DIM));
        }

        return lines;
    }

    /**
     * Stand-in rows for the placement screen, so the box being dragged is the
     * size the real one will be even when nothing is running.
     */
    public static List<Line> previewLines(MapConfig map) {
        List<Line> lines = new ArrayList<>();
        if (map.hudShowAuto) {
            lines.add(new Line(Lang.t("hud.auto", 12, 34), COLOR_ARMED));
        }
        if (map.hudShowState) {
            lines.add(new Line(Lang.t("hud.watching"), COLOR_TEXT));
        }
        if (map.hudShowCounters) {
            lines.add(new Line(Lang.t("hud.counters", 34, 254), COLOR_DIM));
        }
        if (map.hudShowLast) {
            lines.add(new Line(Lang.t("map.sample_n", 12), COLOR_OK,
                    Lang.t("hud.region_cell", "EU Central", 52), 0xFF3478D4));
            if (map.hudShowContext) {
                lines.add(new Line("Forest  " + Lang.t("word.dot") + "  "
                        + dev.rtpbuddy.util.Worlds.dimensionLabel("minecraft:overworld"), COLOR_DIM));
            }
            lines.add(new Line(Lang.t("hud.last_pos", 102663, 70, 159697), COLOR_TEXT,
                    "189.8k", COLOR_DIM));
        }
        if (map.hudShowStatus) {
            lines.add(new Line(Lang.t("hud.preview_status"), COLOR_DIM));
        }
        if (lines.isEmpty()) {
            lines.add(new Line(Lang.t("hud.preview_empty"), COLOR_DIM));
        }
        return lines;
    }
}
