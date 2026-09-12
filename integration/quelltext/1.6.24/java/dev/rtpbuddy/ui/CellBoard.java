package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.stats.CellCoverage;
import dev.rtpbuddy.util.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * The nine-by-nine progress board: which of the server's cells the landings on
 * screen have actually reached.
 *
 * <p>This is the one coverage figure with a real denominator. The density
 * coverage beside it divides by a disc whose size depends on the cell edge you
 * picked, so its percentage means nothing on its own; the grid has eighty-one
 * cells whatever anyone sets, so "22 of 81" is a sentence and there is
 * something left to finish.
 *
 * <p>Drawn as a texture rather than as rectangles. Painted directly the board
 * is around three hundred {@code fill} calls a frame - fifty-nine empty cells
 * needing a ground, a tint and two edges each - and on 1.21.11 every one of
 * those allocates twice. It is the same mistake the gap map made once, at a
 * fifth of the scale, so it gets the same answer: the picture is baked when the
 * numbers change and every frame in between is one quad.
 *
 * <p>What stays in the frame path is what the picture cannot hold: the ring
 * around the cell the player is standing in, which moves, and the numbers,
 * which need the font.
 */
public final class CellBoard {

    private static final int GRID = ServerRegions.COLUMNS;

    private static final int HERE_RING = 0xFF6BE38F;

    /** Below this the numbers would not fit inside a cell. */
    private static final int NUMBER_MIN_CELL = 15;

    private static final ArgbTexture TEXTURE = new ArgbTexture("cell_board");
    private static int bakedSignature = Integer.MIN_VALUE;

    private CellBoard() {
    }

    /** Pixels the board occupies at this column width, drawn or not. */
    public static int height(int width) {
        return cellSize(width) * GRID;
    }

    private static int cellSize(int width) {
        return Math.max(6, Math.min(22, width / GRID));
    }

    /**
     * @return the y below the board, ready for the next row
     */
    public static int render(DrawContext context, CellCoverage coverage,
                             int x, int y, int width, boolean numbers) {
        int cell = cellSize(width);
        int board = cell * GRID;
        int left = x + (width - board) / 2;

        rebakeIfStale(coverage);
        TEXTURE.draw(context, left, y, board, board);

        int here = playerCellNumber();
        if (here > 0) {
            ServerRegions.Cell cell1 = CellCoverage.grid()[here - 1];
            ring(context, left + cell1.column() * cell, y + cell1.row() * cell,
                    cell, HERE_RING);
        }

        if (numbers && cell >= NUMBER_MIN_CELL) {
            TextRenderer font = UiDraw.font();
            for (ServerRegions.Cell entry : CellCoverage.grid()) {
                if (coverage.count(entry.number()) == 0) {
                    continue;
                }
                String text = String.valueOf(entry.number());
                int textX = left + entry.column() * cell + (cell - font.getWidth(text)) / 2;
                int textY = y + entry.row() * cell + (cell - font.fontHeight) / 2;
                // The renderer's own shadow rather than a second draw call: the
                // numbers sit on tinted cells and need one, and drawing each
                // number twice would double the text work every frame.
                context.drawText(font, text, textX, textY, MapPalette.TEXT, true);
            }
        }

        return y + board + 3;
    }

    /**
     * Repaints the image, and only when the numbers behind it have moved.
     *
     * <p>The signature is the board's own contents, so a filter change that
     * happens to leave every cell count alone does not cost an upload either.
     */
    private static void rebakeIfStale(CellCoverage coverage) {
        if (coverage.signature() == bakedSignature && TEXTURE.ready()) {
            return;
        }
        bakedSignature = coverage.signature();
        TEXTURE.update(CellPlate.bake(coverage), CellPlate.SIZE, CellPlate.SIZE);
    }

    /** One-pixel outline inside the cell, so neighbours keep their own edges. */
    private static void ring(DrawContext context, int x, int y, int size, int color) {
        context.fill(x, y, x + size, y + 1, color);
        context.fill(x, y + size - 1, x + size, y + size, color);
        context.fill(x, y + 1, x + 1, y + size - 1, color);
        context.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
    }

    /**
     * The cell the player is standing in, or 0.
     *
     * <p>Goes through the config rather than straight to the grid, so a world
     * with no cell layout - singleplayer, another server - highlights nothing
     * instead of highlighting whatever cell those coordinates would fall in.
     */
    public static int playerCellNumber() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return 0;
        }
        ServerRegions.Cell cell = RTPBuddyClient.config().serverRegionAt(
                dev.rtpbuddy.util.Worlds.serverAddress(client),
                dev.rtpbuddy.util.Worlds.dimensionId(client.world),
                client.player.getX(), client.player.getZ());
        return cell == null ? 0 : cell.number();
    }

    /** "22 of 81 cells · 10 reached once". */
    public static String summary(CellCoverage coverage) {
        return Lang.t("cells.summary", coverage.hitCells(), CellCoverage.totalCells(),
                coverage.onceCells());
    }
}
