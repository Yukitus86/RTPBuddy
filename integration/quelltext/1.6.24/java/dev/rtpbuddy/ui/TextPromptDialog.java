package dev.rtpbuddy.ui;

import dev.rtpbuddy.util.Lang;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
 * A single line of text, asked for on the same card as {@link ConfirmDialog}.
 *
 * <p>Used where a value is a name rather than a choice: naming a sitting cannot
 * be a cycle through fixed options, and the session list has no room to edit a
 * row in place without the name colliding with the count beside it.
 *
 * <p>Submitting an empty line is a deliberate answer, not a cancel - it clears
 * the name and lets whatever the default was show through again. Cancelling is
 * escape or the cancel button, and only that leaves the value untouched.
 */
public class TextPromptDialog extends Screen {

    private static final int CARD_WIDTH = 320;
    private static final int MAX_LENGTH = 32;

    private final Screen parent;
    private final String heading;
    private final String hint;
    private final String initial;
    private final Consumer<String> onAccept;

    private List<String> body = List.of();
    private TextFieldWidget field;

    public TextPromptDialog(Screen parent, String heading, String hint, String initial,
                            Consumer<String> onAccept) {
        super(Text.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.hint = hint;
        this.initial = initial == null ? "" : initial;
        this.onAccept = onAccept;
    }

    @Override
    protected void init() {
        body = UiDraw.wrap(hint, CARD_WIDTH - 32);

        int cardHeight = cardHeight();
        int top = (height - cardHeight) / 2;
        int left = (width - CARD_WIDTH) / 2 + 16;
        int inner = CARD_WIDTH - 32;

        field = new TextFieldWidget(textRenderer, left, fieldY(top), inner, 18,
                Text.literal(heading));
        field.setMaxLength(MAX_LENGTH);
        field.setText(initial);
        // The name is almost always being replaced rather than appended to, so
        // the whole of it starts selected and typing overwrites it.
        field.setSelectionStart(0);
        field.setSelectionEnd(initial.length());
        addDrawableChild(field);
        setInitialFocus(field);

        int buttonY = top + cardHeight - 30;
        int buttonWidth = (inner - 8) / 2;
        addDrawableChild(new RtpButton(left, buttonY, buttonWidth, 20, RtpButton.Style.SURFACE,
                Lang.t("dialog.cancel"), this::close));
        addDrawableChild(new RtpButton(left + buttonWidth + 8, buttonY, buttonWidth, 20,
                RtpButton.Style.PRIMARY, Lang.t("dialog.ok"), this::accept));
    }

    private int fieldY(int top) {
        return top + 14 + UiDraw.lineHeight() + 8 + body.size() * UiDraw.lineHeight() + 4;
    }

    private int cardHeight() {
        return 22 + UiDraw.lineHeight() + 6 + body.size() * UiDraw.lineHeight() + 8 + 18 + 12 + 30;
    }

    private void accept() {
        onAccept.accept(field.getText().trim());
        client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (PanicKey.handle(input)) {
            return true;
        }
        // Enter submits from the field, which is where the cursor already is.
        if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            accept();
            return true;
        }
        return super.keyPressed(input);
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
