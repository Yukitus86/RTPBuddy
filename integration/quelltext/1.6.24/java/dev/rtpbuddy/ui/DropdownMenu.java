package dev.rtpbuddy.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A short list that drops out of a toolbar button.
 *
 * <p>The toolbar's other filters cycle, which is right for three or four
 * choices: one press moves one step and a second press undoes it. The biome
 * filter has eighteen families, and cycling eighteen choices is not a filter -
 * getting back to <em>all</em> after picking the wrong one takes seventeen more
 * presses. Anything with that many options needs to be pickable in one click,
 * and to say what it is offering before the choice is made.
 *
 * <p>Deliberately not a vanilla widget: the screen already owns its own input
 * order, and a real widget would have to be added and removed from the child
 * list on every open and close. This is drawn last and hit-tested first, which
 * is all a popup actually needs.
 *
 * <p>Two modes. A plain menu picks one thing and closes, which is what a filter
 * wants. A {@linkplain #openSticky sticky} menu ticks rows on and off and stays
 * open until the click lands outside it, which is what the search order wants -
 * three biomes out of one family is three ticks, not three trips through the
 * same menu.
 */
final class DropdownMenu {

    /**
     * @param key   what the caller gets back; null is a legitimate key and means
     *              "no filter", which is why the click result is wrapped
     * @param count landings behind this choice, or -1 to leave the number off
     * @param checked drawn as picked; several rows may be checked at once, which
     *                is what separates a tick list from a single choice
     */
    record Item(String key, String label, int color, int count, boolean checked) {

        Item(String key, String label, int color, int count) {
            this(key, label, color, count, false);
        }
    }

    /** A click landed on a row. Needed because {@code null} is a valid key. */
    record Pick(String key) {
    }

    private static final int ROW = 13;
    private static final int PAD = 3;
    private static final int SWATCH = 7;
    private static final int MIN_WIDTH = 96;
    /** Left rail on a ticked row, in the width the swatch is indented by. */
    private static final int RAIL = 2;

    private boolean open;
    private boolean sticky;
    private int x;
    private int y;
    private int menuWidth;
    private List<Item> items = List.of();
    private String selected;

    /** First row drawn, for the rare list too long to fit on screen. */
    private int scroll;
    private int visibleRows;

    boolean isOpen() {
        return open;
    }

    /**
     * @param anchorX left edge of the button the menu hangs from
     * @param anchorY the y the menu starts at, normally the button's bottom
     * @param bottomLimit the lowest y the menu may reach; it is moved up to fit
     */
    void open(int anchorX, int anchorY, int bottomLimit, List<Item> entries, String selectedKey) {
        place(anchorX, anchorY, bottomLimit, Integer.MAX_VALUE, entries, selectedKey, false);
    }

    /**
     * A menu that ticks rows instead of picking one, and stays open while it
     * does. The caller reopens it after each pick so the ticks it draws are the
     * ones the config now holds.
     *
     * @param rightLimit the rightmost x the menu may reach; it is moved left to
     *                   fit, because a settings column is narrow and the list of
     *                   biome names is not
     */
    void openSticky(int anchorX, int anchorY, int bottomLimit, int rightLimit,
                    List<Item> entries) {
        place(anchorX, anchorY, bottomLimit, rightLimit, entries, null, true);
    }

    private void place(int anchorX, int anchorY, int bottomLimit, int rightLimit,
                       List<Item> entries, String selectedKey, boolean stickyMode) {
        this.items = new ArrayList<>(entries);
        this.selected = selectedKey;
        this.sticky = stickyMode;
        this.open = !this.items.isEmpty();
        this.scroll = 0;

        TextRenderer font = UiDraw.font();
        int widest = MIN_WIDTH;
        for (Item item : items) {
            int width = SWATCH + 6 + font.getWidth(item.label()) + 10;
            if (item.count() >= 0) {
                width += font.getWidth(String.valueOf(item.count())) + 10;
            }
            widest = Math.max(widest, width);
        }
        this.menuWidth = widest + PAD * 2;
        this.x = anchorX;
        this.y = anchorY;

        // How many rows fit between the anchor and the limit. A menu that opens
        // near the bottom moves up first; only one that cannot fit on the screen
        // at all falls back to scrolling.
        int room = Math.max(ROW + PAD * 2, bottomLimit);
        this.visibleRows = Math.max(1, Math.min(items.size(), (room - PAD * 2) / ROW));

        int height = height();
        if (this.y + height > bottomLimit) {
            this.y = Math.max(0, bottomLimit - height);
        }
        if (rightLimit != Integer.MAX_VALUE && this.x + menuWidth > rightLimit) {
            this.x = Math.max(0, rightLimit - menuWidth);
        }
    }

    void close() {
        open = false;
        sticky = false;
        items = List.of();
    }

    private int height() {
        return visibleRows * ROW + PAD * 2;
    }

    boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= x && mouseX < x + menuWidth
                && mouseY >= y && mouseY < y + height();
    }

    /** Wheel over an open menu that had to be cut short. */
    boolean scrolled(double amount) {
        if (!open || items.size() <= visibleRows) {
            return open;
        }
        int max = items.size() - visibleRows;
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount) * 2));
        return true;
    }

    /**
     * Handles one click anywhere on the screen.
     *
     * <p>A plain menu closes either way: a click on a row picks it, a click
     * beside the menu dismisses it. That is what a popup is for, and it means
     * the caller never has to think about where the click landed. A sticky menu
     * closes only on the click that misses it.
     *
     * @return the picked row, or null when the click was outside the menu
     */
    Pick click(double mouseX, double mouseY) {
        if (!open) {
            return null;
        }
        Pick pick = null;
        if (contains(mouseX, mouseY)) {
            int index = scroll + (int) ((mouseY - y - PAD) / ROW);
            if (index >= 0 && index < items.size()) {
                pick = new Pick(items.get(index).key());
            }
        }
        if (pick == null || !sticky) {
            close();
        }
        return pick;
    }

    void render(DrawContext context, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        int height = height();
        TextRenderer font = UiDraw.font();

        // A shadow rather than a border alone: the menu sits over the map, and
        // an outline on its own reads as part of whatever is behind it.
        context.fill(x + 2, y + 2, x + menuWidth + 2, y + height + 2, 0x66000000);
        Theme.roundOutline(context, x, y, menuWidth, height, 3, Theme.LINE,
                Theme.SURFACE_HEADER);

        for (int row = 0; row < visibleRows; row++) {
            int index = scroll + row;
            if (index >= items.size()) {
                break;
            }
            Item item = items.get(index);
            int rowY = y + PAD + row * ROW;
            boolean hovered = mouseX >= x && mouseX < x + menuWidth
                    && mouseY >= rowY && mouseY < rowY + ROW;
            boolean chosen = item.checked() || java.util.Objects.equals(item.key(), selected);

            if (hovered) {
                context.fill(x + 1, rowY, x + menuWidth - 1, rowY + ROW, Theme.CONTROL_HOVER);
            } else if (chosen) {
                context.fill(x + 1, rowY, x + menuWidth - 1, rowY + ROW, Theme.ACCENT_WASH);
            }

            int swatchY = rowY + (ROW - SWATCH) / 2;
            Theme.roundRect(context, x + PAD + 2, swatchY, SWATCH, SWATCH, 1, item.color());

            int textY = rowY + (ROW - font.fontHeight) / 2 + 1;
            UiDraw.text(context, item.label(), x + PAD + 2 + SWATCH + 5, textY,
                    chosen ? Theme.TEXT : Theme.TEXT_DIM);
            // A rail rather than a tick glyph: several rows can be on at once
            // and the eye has to find them down the left edge, which is also the
            // one mark that needs no font coverage to be sure of.
            if (item.checked()) {
                context.fill(x + 1, rowY + 1, x + 1 + RAIL, rowY + ROW - 1, Theme.ACCENT);
            }
            if (item.count() >= 0) {
                UiDraw.textRight(context, String.valueOf(item.count()),
                        x + menuWidth - PAD - 3, textY, Theme.TEXT_FAINT);
            }
        }

        if (items.size() > visibleRows) {
            Theme.scrollbar(context, x + menuWidth - 3, y + PAD, visibleRows * ROW,
                    items.size() * ROW, scroll * ROW);
        }
    }
}
