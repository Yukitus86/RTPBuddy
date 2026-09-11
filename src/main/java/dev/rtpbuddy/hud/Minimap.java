package dev.rtpbuddy.hud;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.config.GuardSettings;
import dev.rtpbuddy.config.MapConfig;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.ui.ArgbTexture;
import dev.rtpbuddy.ui.GapScheme;
import dev.rtpbuddy.ui.MapPalette;
import dev.rtpbuddy.ui.MapViewState;
import dev.rtpbuddy.ui.Theme;
import dev.rtpbuddy.ui.UiDraw;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import dev.rtpbuddy.util.Worlds;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * The always-on plot in the corner of the game screen.
 *
 * <p>It shows one thing the full map screen also shows, but without costing a
 * key press and a paused view: whereabouts in the recorded spread the player is
 * standing, and which server cell that is. Everything else - filters,
 * statistics, the gap field, picking a landing - stays on the map screen. The
 * frame is the map screen's "fit" rectangle over the same landings, held still,
 * with the player moving about inside it.
 *
 * <p>Almost the whole picture is baked into a texture by {@link MinimapPlate}
 * and redrawn as a single quad. A minimap is on screen for the entire session,
 * so a rectangle per landing would be a thousand {@code fill} calls in every
 * frame of every fight - the mistake the gap map very nearly shipped with, made
 * permanent. What stays live is only what actually moves or is made of text:
 * the player marker, the numbers written into the cells or the counted tiles,
 * and the caption.
 */
public class Minimap implements HudElement {

    /** Player marker. */
    private static final int PLAYER_COLLAR = 0xE6060A0E;
    private static final int PLAYER_CORE = 0xFFEFFFF4;
    private static final int HEADING_LENGTH = 7;

    /** Cells narrower than this on the plate get no number written in them. */
    private static final int CELL_NUMBER_MIN_PIXELS = 26;

    /** Height of the caption strip under the plate, and the gap above it. */
    private static final int CAPTION_GAP = 2;
    private static final int CAPTION_HEIGHT = 13;

    // ------------------------------------------------------------------ state

    /**
     * One plate, one texture. The overlay is a singleton on the game screen, and
     * the placement screen draws the very same thing rather than a copy of it.
     */
    private static final ArgbTexture TEXTURE = new ArgbTexture("minimap");
    private static final MinimapPlate PLATE = new MinimapPlate();

    /** The 81 cells never change, so the array is built once rather than per frame. */
    private static final ServerRegions.Cell[] CELLS = ServerRegions.cells();

    private static long signature = Long.MIN_VALUE;
    private static int bakedSize;

    /** How long a cell reached for the first time stays marked, in millis. */
    private static final long FLASH_MILLIS = 8_000L;

    /** Twelve pulses over the eight seconds - visible without being a strobe. */
    private static final double FLASH_HZ = 1.5;

    private static int flashCellNumber;
    private static long flashUntilMillis;

    // ----------------------------------------------------------------- layout

    /** Width of the whole overlay, plate and caption alike. */
    public static int width(MapConfig map) {
        return MapConfig.clampMinimapSize(map.minimapSize);
    }

    /** Height of the whole overlay, caption included when it is switched on. */
    public static int height(MapConfig map) {
        int size = MapConfig.clampMinimapSize(map.minimapSize);
        return map.minimapShowCaption ? size + CAPTION_GAP + CAPTION_HEIGHT : size;
    }

    // ------------------------------------------------------------------ entry

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        MapConfig map = RTPBuddyClient.config().map;
        if (!map.minimapEnabled) {
            return;
        }
        drawAnchored(context, map,
                context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }

    /** Draws the overlay where the config says it belongs on a screen this size. */
    public static void drawAnchored(DrawContext context, MapConfig map,
                                    int screenWidth, int screenHeight) {
        HudAnchor anchor = HudAnchor.parse(map.minimapAnchor);
        int boxWidth = width(map);
        int boxHeight = height(map);
        draw(context, map,
                anchor.screenX(screenWidth, boxWidth, map.minimapX),
                anchor.screenY(screenHeight, boxHeight, map.minimapY));
    }

    /**
     * Draws the overlay with its top left corner at the given screen position.
     *
     * <p>The cell under the player is worked out once and handed down. It is not
     * a free lookup - it lowercases the server address to compare it - and this
     * method runs on every frame the game draws.
     */
    public static void draw(DrawContext context, MapConfig map, int x, int y) {
        int size = MapConfig.clampMinimapSize(map.minimapSize);
        ServerRegions.Cell here = playerCell();

        rebuildIfStale(map, size, here);
        TEXTURE.draw(context, x, y, size, size);

        // Everything past here moves between frames, which is exactly why it is
        // not in the texture. Scissored, so a cell number half off the plate is
        // cut at the edge rather than spilling onto the game's own HUD.
        context.enableScissor(x + 1, y + 1, x + size - 1, y + size - 1);
        boolean counts = map.minimapShowGapTiles && map.minimapGapNumbers
                && drawGapCounts(context, map, x, y, size);
        // Both sets of numbers want the middle of the same square, and two
        // numbers in one square is neither of them. The count wins where they
        // meet - the caption still names the cell the player is standing in,
        // which is the part of the cell number worth having.
        if (!counts && map.minimapShowRegions && map.minimapShowCellNumbers) {
            drawCellNumbers(context, x, y, size, here);
        }
        drawFlash(context, x, y, size);
        drawPlayer(context, x, y, size);
        context.disableScissor();

        drawNorth(context, x, y, size);

        if (map.minimapShowCaption) {
            drawCaption(context, x, y + size + CAPTION_GAP, size, here);
        }
    }

    // ------------------------------------------------------------------- bake

    /** The landings the plate frames: this sitting, or everything recorded. */
    private static List<RtpSample> samples(MapConfig map) {
        if ("ALL".equalsIgnoreCase(map.minimapScope)) {
            return RTPBuddyClient.store().samplesView();
        }
        String sessionId = RTPBuddyClient.sessions().currentId();
        return sessionId == null ? List.of() : RTPBuddyClient.store().samplesOf(sessionId);
    }

    /**
     * Rebuilds the texture when what it shows has changed.
     *
     * <p>Two things this signature is careful about, and both of them are how a
     * minimap quietly costs a session its frame rate.
     *
     * <p>It never asks for the sample list. {@code samplesOf} walks every
     * recorded landing and builds a new list each time it is called; on a frame
     * path that is a two-thousand element scan and an allocation per frame, for
     * an answer that changes once per teleport. The list is fetched only once a
     * rebuild has already been decided on.
     *
     * <p>And it takes the player's position as a coarse grid square rather than
     * as a position. The position moves every tick and would rebuild the plate
     * as fast as the player walks; the square is thousands of blocks across,
     * which is the rate the picture actually changes at.
     */
    private static void rebuildIfStale(MapConfig map, int size, ServerRegions.Cell here) {
        long stamp = 17;
        stamp = stamp * 31 + size;
        stamp = stamp * 31 + MapConfig.clampMinimapOpacity(map.minimapOpacity);
        stamp = stamp * 31 + RTPBuddyClient.store().revision();
        stamp = stamp * 31 + (here == null ? 0 : here.number());
        stamp = stamp * 31 + java.util.Objects.hashCode(RTPBuddyClient.sessions().currentId());
        stamp = stamp * 31 + java.util.Objects.hashCode(map.minimapScope);
        stamp = stamp * 31 + GapScheme.of(map.gapScheme).ordinal();
        stamp = stamp * 31 + flags(map);
        stamp = stamp * 31 + fallbackKey();
        // Walking into the nether moves the border, and the counted tiles are
        // keyed on its corner.
        stamp = stamp * 31 + Double.hashCode(borderRadius(RTPBuddyClient.config().guards));

        if (stamp == signature && bakedSize == size && TEXTURE.ready()) {
            return;
        }
        signature = stamp;
        bakedSize = size;

        MinecraftClient client = MinecraftClient.getInstance();
        double px = client.player == null ? 0 : client.player.getX();
        double pz = client.player == null ? 0 : client.player.getZ();
        GuardSettings guards = RTPBuddyClient.config().guards;
        double radius = borderRadius(guards);
        TEXTURE.update(PLATE.bake(size, samples(map), here,
                map.minimapShowRegions, map.minimapShowAxes, map.minimapShowLastLeg,
                map.minimapShowGapTiles, map.minimapGapNumbers,
                GapScheme.of(map.gapScheme),
                guards.borderCenterX - radius, guards.borderCenterZ - radius,
                guards.squareBorder ? radius * 2 : 0,
                MapConfig.clampMinimapOpacity(map.minimapOpacity), px, pz), size, size);
    }

    /**
     * The border radius that applies where the player is standing.
     *
     * <p>The counted tiles count from the low corner of this border, exactly as
     * the map screen's raster does, so the two pictures sit on one grid. The
     * dimensions rarely share a radius - DonutSMP gives all three a different
     * one - and a corner taken from the wrong dimension would put every tile
     * line in the wrong place.
     */
    private static double borderRadius(GuardSettings guards) {
        MinecraftClient client = MinecraftClient.getInstance();
        return guards.radiusFor(client.world == null
                ? null : Worlds.dimensionId(client.world));
    }

    private static int flags(MapConfig map) {
        return (map.minimapShowRegions ? 1 : 0)
                | (map.minimapShowAxes ? 2 : 0)
                | (map.minimapShowLastLeg ? 4 : 0)
                | (map.minimapShowGapTiles ? 8 : 0)
                | (map.minimapGapNumbers ? 16 : 0);
    }

    /**
     * The grid square the player is standing on, on the same coarse grid the
     * fallback frame snaps to.
     *
     * <p>It only really matters with nothing recorded and no server cell, where
     * it is the whole frame - but it is folded in unconditionally rather than
     * only in that case, because working out whether it applies would mean
     * fetching the sample list, which is precisely what this signature exists to
     * avoid. The price of the simpler rule is one extra rebuild per five
     * thousand blocks walked.
     */
    private static int fallbackKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 1;
        }
        int gx = (int) Math.floor(client.player.getX() / MinimapPlate.FALLBACK_SNAP);
        int gz = (int) Math.floor(client.player.getZ() / MinimapPlate.FALLBACK_SNAP);
        return gx * 8191 + gz;
    }

    // ------------------------------------------------------------ live layer

    /**
     * Server numbers written into the cells, so the plate says which sector is
     * which rather than only that they differ. Skipped when the cells are too
     * small to hold a number without covering the landings inside them.
     */
    private static void drawCellNumbers(DrawContext context, int x, int y, int size,
                                        ServerRegions.Cell here) {
        MapViewState view = PLATE.view();
        if (ServerRegions.CELL_SIZE * view.zoom() < CELL_NUMBER_MIN_PIXELS) {
            return;
        }
        TextRenderer font = UiDraw.font();
        for (ServerRegions.Cell cell : CELLS) {
            double cx = view.worldToScreenX(cell.minX() + ServerRegions.CELL_SIZE / 2.0);
            double cz = view.worldToScreenY(cell.minZ() + ServerRegions.CELL_SIZE / 2.0);
            if (cx < -8 || cz < -8 || cx > size + 8 || cz > size + 8) {
                continue;
            }
            String label = String.valueOf(cell.number());
            boolean mine = here != null && here.number() == cell.number();
            context.drawText(font, label,
                    x + (int) Math.round(cx) - font.getWidth(label) / 2,
                    y + (int) Math.round(cz) - font.fontHeight / 2,
                    mine ? cell.zone().color() : MapPalette.withAlpha(cell.zone().color(), 0.45),
                    false);
        }
    }

    /**
     * The landing count written into each counted tile.
     *
     * <p>The tiles are baked into the plate; only the digits are live, because
     * text cannot be written into a pixel array. Switched on they cost squares -
     * see {@link MapConfig#minimapGapNumbers} - so this method only ever runs
     * when the plate has already been baked with tiles big enough to hold them,
     * and draws nothing otherwise.
     *
     * <p>The ink comes off the band under it, the same rule the map screen uses:
     * one grey cannot be read on four colours.
     *
     * @return whether anything was written, which is what makes the cell numbers
     *         stand aside
     */
    private static boolean drawGapCounts(DrawContext context, MapConfig map,
                                         int x, int y, int size) {
        double tile = PLATE.gapTile();
        if (tile <= 0) {
            return false;
        }
        MapViewState view = PLATE.view();
        double tilePixels = tile * view.zoom();
        TextRenderer font = UiDraw.font();
        GapScheme scheme = GapScheme.of(map.gapScheme);
        double originX = PLATE.gapOriginX();
        double originZ = PLATE.gapOriginZ();
        long firstX = (long) Math.floor((view.screenToWorldX(0) - originX) / tile);
        long lastX = (long) Math.floor((view.screenToWorldX(size) - originX) / tile);
        long firstZ = (long) Math.floor((view.screenToWorldZ(0) - originZ) / tile);
        long lastZ = (long) Math.floor((view.screenToWorldZ(size) - originZ) / tile);

        int most = 0;
        for (long tz = firstZ; tz <= lastZ; tz++) {
            for (long tx = firstX; tx <= lastX; tx++) {
                Integer held = PLATE.gapCounts().get(MinimapPlate.tileIndexKey(tx, tz));
                if (held != null && held > most) {
                    most = held;
                }
            }
        }
        float scale = numberScale(font.getWidth(String.valueOf(most)),
                font.fontHeight, tilePixels);
        if (scale <= 0) {
            return false;
        }

        Matrix3x2fStack matrices = context.getMatrices();
        for (long tz = firstZ; tz <= lastZ; tz++) {
            for (long tx = firstX; tx <= lastX; tx++) {
                Integer held = PLATE.gapCounts().get(MinimapPlate.tileIndexKey(tx, tz));
                int count = held == null ? 0 : held;
                String text = String.valueOf(count);
                // The tile's own middle in world units, never the part of it
                // that happens to be on the plate: probing the clipped centre
                // makes the outer row walk about as the frame moves.
                double cx = view.worldToScreenX(originX + (tx + 0.5) * tile);
                double cz = view.worldToScreenY(originZ + (tz + 0.5) * tile);
                // Translated to the centre and scaled about it, rather than
                // drawn at a scaled coordinate: the digits then sit on the exact
                // middle of their tile instead of drifting by up to a pixel
                // each, which across a plateful of them reads as a crooked grid.
                matrices.pushMatrix();
                matrices.translate((float) (x + cx), (float) (y + cz));
                matrices.scale(scale, scale);
                context.drawText(font, text, -font.getWidth(text) / 2,
                        -font.fontHeight / 2, scheme.bandInk(count), true);
                matrices.popMatrix();
            }
        }
        return true;
    }

    /**
     * How far the digits have to be shrunk to fit the tile, or zero if no size
     * worth reading does.
     *
     * <p>One size for every count on the plate, taken from the widest of them.
     * Fitting each count to its own tile is what used to leave holes: a
     * two-figure number is twice the width of a one-figure number, so on a plate
     * whose tiles sat between the two, every tile that had been landed in ten
     * times or more came out blank - which reads as no landings rather than as
     * many, the exact opposite of what is there.
     */
    private static float numberScale(int widest, int fontHeight, double tilePixels) {
        double room = tilePixels - MinimapPlate.GAP_NUMBER_PADDING * 2;
        if (room <= 0 || widest <= 0) {
            return 0;
        }
        double fit = Math.min(room / widest, room / fontHeight);
        if (fit < MinimapPlate.GAP_NUMBER_MIN_SCALE) {
            return 0;
        }
        return (float) Math.min(fit, MinimapPlate.GAP_NUMBER_SCALE);
    }

    /**
     * Marks a cell no landing had ever reached before.
     *
     * <p>Called from the landing hook, not polled: the minimap has no way of
     * knowing a cell is new without the board, and asking the board on the
     * frame path is the sort of thing that costs a whole dataset walk per
     * frame.
     */
    public static void flashCell(int cellNumber) {
        flashCellNumber = cellNumber;
        flashUntilMillis = System.currentTimeMillis() + FLASH_MILLIS;
    }

    /**
     * The outline around a freshly reached cell, for a few seconds.
     *
     * <p>Four rectangles and only while a flash is live, so the common case -
     * no flash - is one comparison. Not baked into the plate because it is
     * time-dependent: baking it would mean rebaking the whole texture every
     * frame it pulses.
     */
    private static void drawFlash(DrawContext context, int x, int y, int size) {
        long now = System.currentTimeMillis();
        if (flashCellNumber <= 0 || now >= flashUntilMillis) {
            return;
        }
        ServerRegions.Cell cell = null;
        for (ServerRegions.Cell candidate : CELLS) {
            if (candidate.number() == flashCellNumber) {
                cell = candidate;
                break;
            }
        }
        if (cell == null) {
            flashCellNumber = 0;
            return;
        }
        MapViewState view = PLATE.view();
        int left = x + (int) Math.round(view.worldToScreenX(cell.minX()));
        int top = y + (int) Math.round(view.worldToScreenY(cell.minZ()));
        int right = x + (int) Math.round(view.worldToScreenX(cell.maxX()));
        int bottom = y + (int) Math.round(view.worldToScreenY(cell.maxZ()));
        if (right <= left || bottom <= top) {
            return;
        }
        // Fades out over its life and pulses on the way, so it reads as an
        // event rather than as a permanent part of the plate.
        double life = (flashUntilMillis - now) / (double) FLASH_MILLIS;
        double pulse = 0.55 + 0.45 * Math.sin((flashUntilMillis - now) / 1000.0
                * FLASH_HZ * Math.PI * 2);
        int color = MapPalette.withAlpha(MapPalette.HIGHLIGHT, Math.max(0.0, life * pulse));
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top + 1, left + 1, bottom - 1, color);
        context.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    /**
     * The player, and the only part of the plate that moves every frame.
     *
     * <p>Standing outside the framed rectangle is a real case - the frame is the
     * spread of the landings, and a walk can leave it - so the marker is pinned
     * to the rim in the warning colour rather than drawn where it cannot be seen.
     */
    private static void drawPlayer(DrawContext context, int x, int y, int size) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        MapViewState view = PLATE.view();
        double sx = view.worldToScreenX(client.player.getX());
        double sz = view.worldToScreenY(client.player.getZ());
        boolean outside = sx < 3 || sz < 3 || sx > size - 3 || sz > size - 3;

        int px = x + (int) Math.round(Math.max(4, Math.min(size - 5, sx)));
        int py = y + (int) Math.round(Math.max(4, Math.min(size - 5, sz)));

        // Yaw 0 faces +Z (south), which is down on screen.
        double yaw = Math.toRadians(client.player.getYaw());
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);

        if (!outside) {
            headingStub(context, px, py, dx, dz);
        }
        disc(context, px, py, 4, PLAYER_COLLAR);
        disc(context, px, py, 3, outside ? Theme.WARN : MapPalette.PLAYER);
        context.fill(px, py, px + 1, py + 1, PLAYER_CORE);
    }

    /** North, so the plate can be read without first working out which way is up. */
    private static void drawNorth(DrawContext context, int x, int y, int size) {
        TextRenderer font = UiDraw.font();
        String north = Lang.t("minimap.north");
        context.drawText(font, north, x + size / 2 - font.getWidth(north) / 2, y + 2,
                MapPalette.TEXT_DIM, false);
    }

    /**
     * The strip under the plate: which cell the player is standing in, and how
     * much ground the plate covers.
     *
     * <p>The cell is the answer to the question the minimap exists for. Off the
     * region grid - another server, the nether, past the edge of the grid - it
     * says the dimension instead rather than inventing a cell.
     */
    private static void drawCaption(DrawContext context, int x, int y, int size,
                                    ServerRegions.Cell here) {
        Theme.roundOutline(context, x, y, size, CAPTION_HEIGHT, Theme.RADIUS_PILL,
                0xB0000000, 0xC00E1218);

        TextRenderer font = UiDraw.font();
        MinecraftClient client = MinecraftClient.getInstance();

        String label;
        int color;
        if (here != null) {
            label = Lang.t("hud.region_cell", here.zone().label(), here.number());
            color = here.zone().color();
        } else {
            label = Worlds.dimensionLabel(client.world == null
                    ? null : Worlds.dimensionId(client.world));
            color = MapPalette.TEXT_DIM;
        }

        int textY = y + (CAPTION_HEIGHT - font.fontHeight) / 2 + 1;
        context.fill(x + 4, textY + 2, x + 7, textY + 5, color);

        String span = Numbers.compact(PLATE.spanBlocks());
        int spanWidth = font.getWidth(span);
        int room = size - 14 - spanWidth - 4;
        context.drawText(font, UiDraw.trim(label, Math.max(8, room)), x + 10, textY,
                MapPalette.TEXT, false);
        context.drawText(font, span, x + size - 4 - spanWidth, textY,
                MapPalette.TEXT_DIM, false);
    }

    /** The cell the player is standing in right now, or null off the grid. */
    private static ServerRegions.Cell playerCell() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return null;
        }
        return RTPBuddyClient.config().serverRegionAt(Worlds.serverAddress(client),
                Worlds.dimensionId(client.world), client.player.getX(), client.player.getZ());
    }

    // ------------------------------------------------------------- primitives

    /** A filled disc, one fill per row. Radii here are single digits. */
    private static void disc(DrawContext context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0.0,
                    radius * (double) radius - dy * (double) dy)));
            if (half <= 0) {
                continue;
            }
            context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /**
     * A short ray out of the marker in the direction the player is facing, with a
     * dark pixel around it so it stays readable over a tinted cell. One pixel per
     * step along the dominant axis, the same walk the full map uses - stepping at
     * fixed distances instead puts two pixels on some rows and none on others,
     * which reads as a bent stub at every angle that is not square.
     */
    private static void headingStub(DrawContext context, int cx, int cy, double dx, double dz) {
        int[] xs = new int[HEADING_LENGTH + 1];
        int[] ys = new int[HEADING_LENGTH + 1];
        for (int i = 0; i <= HEADING_LENGTH; i++) {
            if (Math.abs(dx) >= Math.abs(dz)) {
                int offset = (int) Math.round(Math.signum(dx) * i);
                xs[i] = cx + offset;
                ys[i] = cy + (int) Math.round(offset * (dx == 0 ? 0 : dz / dx));
            } else {
                int offset = (int) Math.round(Math.signum(dz) * i);
                ys[i] = cy + offset;
                xs[i] = cx + (int) Math.round(offset * (dz == 0 ? 0 : dx / dz));
            }
        }
        for (int i = 0; i <= HEADING_LENGTH; i++) {
            context.fill(xs[i] - 1, ys[i] - 1, xs[i] + 2, ys[i] + 2, PLAYER_COLLAR);
        }
        for (int i = 0; i <= HEADING_LENGTH; i++) {
            context.fill(xs[i], ys[i], xs[i] + 1, ys[i] + 1, MapPalette.PLAYER);
        }
    }

    /** Forces the next frame to rebuild the plate: the settings changed under it. */
    public static void invalidate() {
        signature = Long.MIN_VALUE;
    }
}
