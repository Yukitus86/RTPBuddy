package dev.rtpbuddy.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The one button RTPBuddy draws. Vanilla's {@code ButtonWidget} finalises its
 * rendering in {@code PressableWidget}, so a custom look means going one level
 * down to {@link ClickableWidget} and painting the whole control here.
 *
 * <p>The variants exist because the settings screen has three genuinely
 * different kinds of control - a switch, a value that cycles, and a plain action
 * - and drawing them all as "Label: value" made it impossible to tell at a
 * glance which was which. A switch now carries a coloured bar and an on/off
 * pill; a cycling value carries an accent chip; an action is just a label.
 */
public class RtpButton extends ClickableWidget {

    public enum Style {
        /** Plain action sitting on a card. */
        SURFACE,
        /** The one primary action on a screen, filled with the accent. */
        PRIMARY,
        /** Destructive or stop-everything action. */
        DANGER,
        /** Boolean switch: accent bar on the left, on/off pill on the right. */
        TOGGLE,
        /** Cycles through fixed steps: value shown in a chip on the right. */
        VALUE,
        /** Compact pill for the map's toolbar. */
        CHIP,
        /** Rounded on top only, merges into the card below when selected. */
        TAB
    }

    private final Style style;
    private final Runnable action;

    private String label;
    private String value = "";
    private boolean on;
    private int accent = Theme.ACCENT;

    public RtpButton(int x, int y, int width, int height, Style style, String label, Runnable action) {
        super(x, y, width, height, Text.literal(label));
        this.style = style;
        this.label = label;
        this.action = action;
    }

    public static RtpButton toggle(String label, boolean on, String onOffText, Runnable action) {
        RtpButton button = new RtpButton(0, 0, 100, 20, Style.TOGGLE, label, action);
        button.on = on;
        button.value = onOffText;
        return button;
    }

    public static RtpButton value(String label, String value, Runnable action) {
        RtpButton button = new RtpButton(0, 0, 100, 20, Style.VALUE, label, action);
        button.value = value;
        return button;
    }

    public RtpButton withValue(String newValue) {
        this.value = newValue;
        return this;
    }

    public RtpButton withOn(boolean newOn) {
        this.on = newOn;
        return this;
    }

    public RtpButton withAccent(int color) {
        this.accent = color;
        return this;
    }

    public RtpButton withLabel(String newLabel) {
        this.label = newLabel;
        setMessage(Text.literal(newLabel));
        return this;
    }

    public String label() {
        return label;
    }

    /** Selected state for {@link Style#TAB} and {@link Style#CHIP}. */
    public RtpButton withSelected(boolean selected) {
        this.on = selected;
        return this;
    }

    public boolean selected() {
        return on;
    }

    public void press() {
        if (action != null) {
            action.run();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public void onClick(Click click, boolean doubled) {
        press();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!active || !visible) {
            return false;
        }
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            press();
            return true;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    // -------------------------------------------------------------- rendering

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHovered() && active;

        if (isFocused() && active) {
            Theme.roundRect(context, x - 1, y - 1, w + 2, h + 2,
                    Theme.RADIUS_CONTROL + 1, Theme.ACCENT);
        }

        switch (style) {
            case TAB -> renderTab(context, x, y, w, h, hovered);
            case PRIMARY -> renderFilled(context, x, y, w, h, hovered, Theme.ACCENT, Theme.ACCENT_TEXT);
            case DANGER -> renderFilled(context, x, y, w, h, hovered, Theme.DANGER, Theme.ACCENT_TEXT);
            case CHIP -> renderChip(context, x, y, w, h, hovered);
            case TOGGLE -> renderToggle(context, x, y, w, h, hovered);
            case VALUE -> renderValue(context, x, y, w, h, hovered);
            default -> renderAction(context, x, y, w, h, hovered);
        }
    }

    /** Resting / hover / disabled fill shared by the card-mounted variants. */
    private int surfaceColor(boolean hovered) {
        if (!active) {
            return Theme.CONTROL_DISABLED;
        }
        return hovered ? Theme.CONTROL_HOVER : Theme.CONTROL;
    }

    private int textColor() {
        return active ? Theme.TEXT : Theme.TEXT_DISABLED;
    }

    /** One-pixel lighter line just under the top edge, so the control has a lip. */
    private void topLight(DrawContext context, int x, int y, int w, int background) {
        context.fill(x + Theme.RADIUS_CONTROL, y + 1, x + w - Theme.RADIUS_CONTROL, y + 2,
                Theme.mix(background, 0xFFFFFFFF, 0.07));
    }

    private void renderAction(DrawContext context, int x, int y, int w, int h, boolean hovered) {
        int background = surfaceColor(hovered);
        Theme.roundOutline(context, x, y, w, h, Theme.RADIUS_CONTROL,
                hovered ? Theme.LINE_BRIGHT : Theme.LINE, background);
        topLight(context, x, y, w, background);
        drawCentered(context, label, x, y, w, h, textColor());
    }

    private void renderFilled(DrawContext context, int x, int y, int w, int h, boolean hovered,
                              int fill, int ink) {
        int background = active
                ? (hovered ? Theme.mix(fill, 0xFFFFFFFF, 0.14) : fill)
                : Theme.CONTROL_DISABLED;
        Theme.roundRect(context, x, y, w, h, Theme.RADIUS_CONTROL, background);
        context.fill(x + Theme.RADIUS_CONTROL, y + h - 2, x + w - Theme.RADIUS_CONTROL, y + h - 1,
                Theme.mix(background, 0xFF000000, 0.20));
        drawCentered(context, label, x, y, w, h, active ? ink : Theme.TEXT_DISABLED);
    }

    private void renderChip(DrawContext context, int x, int y, int w, int h, boolean hovered) {
        int background = on
                ? (hovered ? Theme.mix(Theme.ACCENT_DEEP, 0xFFFFFFFF, 0.18) : Theme.ACCENT_DEEP)
                : surfaceColor(hovered);
        Theme.roundOutline(context, x, y, w, h, Theme.RADIUS_CONTROL,
                on ? Theme.ACCENT : (hovered ? Theme.LINE_BRIGHT : Theme.LINE_SOFT), background);
        drawCentered(context, label, x, y, w, h, on ? Theme.TEXT : textColor());
    }

    /**
     * The active tab is drawn one pixel taller than its box so it covers the rule
     * the screen paints under the tab strip - that is what makes it read as part
     * of the card rather than a button floating above it.
     */
    private void renderTab(DrawContext context, int x, int y, int w, int h, boolean hovered) {
        int background = on ? Theme.SURFACE_HEADER : (hovered ? Theme.CONTROL : Theme.LINE_SOFT);
        Theme.roundRectTop(context, x, y, w, h + (on ? 1 : 0), 5, background);
        if (on) {
            Theme.roundRectTop(context, x, y, w, 2, 2, accent);
        }
        drawCentered(context, label, x, y + (on ? 1 : 0), w, h, on ? Theme.TEXT : Theme.TEXT_DIM);
    }

    private void renderToggle(DrawContext context, int x, int y, int w, int h, boolean hovered) {
        int background = surfaceColor(hovered);
        int stateColor = !active ? Theme.TEXT_DISABLED : (on ? Theme.ON : Theme.OFF);
        Theme.roundOutline(context, x, y, w, h, Theme.RADIUS_CONTROL,
                hovered ? Theme.LINE_BRIGHT : Theme.LINE, background);
        topLight(context, x, y, w, background);

        // Left rail: the fastest read of "is this on" in a column of switches.
        Theme.roundRect(context, x + 3, y + 3, 3, h - 6, 1,
                on ? stateColor : Theme.mix(stateColor, background, 0.55));

        int pillWidth = UiDraw.font().getWidth(value) + 10;
        int pillX = x + w - pillWidth - 4;
        Theme.roundRect(context, pillX, y + 4, pillWidth, h - 8, Theme.RADIUS_PILL,
                on ? Theme.mix(stateColor, background, 0.72) : Theme.CONTROL_PRESS);
        UiDraw.text(context, value, pillX + 5, y + (h - 8) / 2 - 1, stateColor);

        int room = pillX - (x + 11);
        UiDraw.text(context, UiDraw.trim(label, room), x + 11, y + (h - 8) / 2 - 1, textColor());
    }

    private void renderValue(DrawContext context, int x, int y, int w, int h, boolean hovered) {
        int background = surfaceColor(hovered);
        Theme.roundOutline(context, x, y, w, h, Theme.RADIUS_CONTROL,
                hovered ? Theme.LINE_BRIGHT : Theme.LINE, background);
        topLight(context, x, y, w, background);

        // The chip is capped at half the control so a long value cannot squeeze
        // the label out entirely; whichever side overflows is trimmed, not hidden.
        int maxChip = Math.max(24, w / 2);
        String shown = UiDraw.trim(value, maxChip - 10);
        int chipWidth = UiDraw.font().getWidth(shown) + 10;
        int chipX = x + w - chipWidth - 4;
        Theme.roundRect(context, chipX, y + 4, chipWidth, h - 8, Theme.RADIUS_PILL,
                active ? Theme.ACCENT_WASH : Theme.CONTROL_PRESS);
        UiDraw.text(context, shown, chipX + 5, y + (h - 8) / 2 - 1,
                active ? Theme.ACCENT : Theme.TEXT_DISABLED);

        int room = chipX - (x + 8) - 4;
        UiDraw.text(context, UiDraw.trim(label, room), x + 8, y + (h - 8) / 2 - 1, textColor());
    }

    private void drawCentered(DrawContext context, String text, int x, int y, int w, int h, int color) {
        String shown = UiDraw.trim(text, w - 8);
        int textX = x + (w - UiDraw.font().getWidth(shown)) / 2;
        UiDraw.text(context, shown, textX, y + (h - 8) / 2 - 1, color);
    }
}
