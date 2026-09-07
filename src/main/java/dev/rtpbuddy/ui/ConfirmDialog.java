package dev.rtpbuddy.ui;

import dev.rtpbuddy.util.Lang;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * A yes/no in front of anything that throws recorded data away.
 *
 * <p>Vanilla has a confirm screen, but it is styled for the pause menu and
 * looks like a different program next to the map. This one is the same card,
 * type and buttons as the rest of RTPBuddy, and it states the count it is about
 * to delete rather than a generic warning.
 */
public class ConfirmDialog extends Screen {

    private static final int CARD_WIDTH = 320;

    private final Screen parent;
    private final String heading;
    private final String detail;
    private final String confirmLabel;
    private final Runnable onConfirm;

    private List<String> body = List.of();

    public ConfirmDialog(Screen parent, String heading, String detail, String confirmLabel,
                         Runnable onConfirm) {
        super(Text.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.detail = detail;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        body = UiDraw.wrap(detail, CARD_WIDTH - 32);

        int cardHeight = cardHeight();
        int top = (height - cardHeight) / 2;
        int buttonY = top + cardHeight - 30;
        int buttonWidth = (CARD_WIDTH - 32 - 8) / 2;
        int left = (width - CARD_WIDTH) / 2 + 16;

        addDrawableChild(new RtpButton(left, buttonY, buttonWidth, 20, RtpButton.Style.SURFACE,
                Lang.t("dialog.cancel"), this::close));
        addDrawableChild(new RtpButton(left + buttonWidth + 8, buttonY, buttonWidth, 20,
                RtpButton.Style.DANGER, confirmLabel, () -> {
            onConfirm.run();
            close();
        }));
    }

    private int cardHeight() {
        return 22 + UiDraw.lineHeight() + 6 + body.size() * UiDraw.lineHeight() + 12 + 30;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // renderBackground has already run for this frame and its blur may only
        // be applied once, so the world is dimmed by hand here.
        context.fill(0, 0, width, height, Theme.SCRIM);

        int cardHeight = cardHeight();
        int x = (width - CARD_WIDTH) / 2;
        int y = (height - cardHeight) / 2;
        Theme.card(context, x, y, CARD_WIDTH, cardHeight);

        int cursor = y + 14;
        UiDraw.text(context, heading, x + 16, cursor, MapPalette.HIGHLIGHT);
        cursor += UiDraw.lineHeight() + 4;
        Theme.rule(context, x + 16, cursor - 2, CARD_WIDTH - 32);
        cursor += 4;
        for (String line : body) {
            UiDraw.text(context, line, x + 16, cursor, MapPalette.TEXT_DIM);
            cursor += UiDraw.lineHeight();
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
