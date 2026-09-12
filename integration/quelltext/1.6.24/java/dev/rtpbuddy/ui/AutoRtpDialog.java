package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.config.AutoRtpConfig;
import dev.rtpbuddy.config.RegionPreset;
import dev.rtpbuddy.util.Lang;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Confirmation in front of the auto-RTP loop.
 *
 * <p>It exists because this is the one feature that acts without the player, and
 * the consequences land on their account: the dialog states plainly what will be
 * sent, how often, and every condition that will stop it, and nothing is sent
 * until the start button is pressed. Rules on repeating a command differ per
 * server - on DonutSMP it is allowed - so what the dialog does is inform, not
 * decide; pressing start is the player saying they know their server.
 */
public class AutoRtpDialog extends Screen {

    private final Screen parent;
    private final Consumer<Boolean> result;

    private ButtonWidget startButton;

    public AutoRtpDialog(Screen parent, Consumer<Boolean> result) {
        super(Text.literal(Lang.t("screen.auto_dialog")));
        this.parent = parent;
        this.result = result;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = top();

        startButton = ButtonWidget.builder(Text.literal(Lang.t("auto_dialog.start")), button -> confirm())
                .dimensions(centerX - 168, top + 72, 164, 20)
                .build();
        addDrawableChild(startButton);

        addDrawableChild(ButtonWidget.builder(Text.literal(Lang.t("auto_dialog.cancel")), button -> cancel())
                .dimensions(centerX + 4, top + 72, 164, 20)
                .build());

        refreshState();
    }

    /**
     * The one thing that can still hold the button back: a rotation with no
     * command in it would start a loop that sends nothing.
     */
    private void refreshState() {
        startButton.active = RTPBuddyClient.autoRtp().targetRegion() != null;
    }

    private int top() {
        return height / 2 - 51;
    }

    private void confirm() {
        boolean started = RTPBuddyClient.autoRtp().start(true);
        result.accept(started);
        client.setScreen(parent);
    }

    private void cancel() {
        result.accept(false);
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Screen.renderWithTooltip already calls renderBackground before this
        // method, and the blur behind it may only be applied once per frame -
        // calling it again here crashes the game. Dim the world directly instead.
        context.fill(0, 0, width, height, 0xC00B0E12);

        int centerX = width / 2;
        int top = top();
        UiDraw.panel(context, centerX - 180, top - 10, 360, 122);
        context.drawCenteredTextWithShadow(textRenderer, title, centerX, top, MapPalette.HIGHLIGHT);

        AutoRtpConfig auto = RTPBuddyClient.config().autoRtp;
        RegionPreset region = RTPBuddyClient.autoRtp().targetRegion();

        // One statement per line: the German translations are long enough that a
        // combined sentence runs past the panel edge.
        java.util.List<String> lines = new java.util.ArrayList<>(4);
        // Names the whole rotation, not just the next command: starting a run
        // that will walk three zones should say all three before it starts.
        String rotation = RTPBuddyClient.autoRtp().describeRotation();
        lines.add(region == null
                ? Lang.t("auto_dialog.no_command", auto.region)
                : auto.manualStep
                ? Lang.t("auto_dialog.will_send_manual", rotation)
                : Lang.t("auto_dialog.will_send", rotation,
                auto.effectiveCooldownSeconds(), auto.jitterSeconds));
        lines.add(Lang.t("auto_dialog.stops", auto.stopAfterMinutes > 0
                ? Lang.t("auto_dialog.after_minutes", auto.stopAfterMinutes)
                : Lang.t("auto_dialog.after_never")));
        if (auto.maxPerSession > 0) {
            lines.add(Lang.t("auto_dialog.cap", auto.maxPerSession).trim());
        }
        if (auto.stopOnPlayerNearby) {
            lines.add(Lang.t("auto_dialog.on_player", auto.playerNearbyRadius).trim());
        }
        // A search order changes what the run is for, so it belongs on the
        // screen that asks whether to start it, not only in the settings.
        if (dev.rtpbuddy.capture.FindRule.armed(auto)) {
            lines.add(Lang.t("auto_dialog.find",
                    dev.rtpbuddy.capture.FindRule.describe(auto)));
            if (auto.findGiveUpAfter > 0) {
                lines.add(Lang.t("auto_dialog.find_cap", auto.findGiveUpAfter));
            }
        }
        lines.add(Lang.t("auto_dialog.never"));

        int y = top + 18;
        for (String line : lines) {
            UiDraw.text(context, line, centerX - 168, y, MapPalette.TEXT_DIM);
            y += UiDraw.lineHeight();
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        cancel();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (PanicKey.handle(input)) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
