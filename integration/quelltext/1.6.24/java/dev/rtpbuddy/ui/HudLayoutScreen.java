package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.config.MapConfig;
import dev.rtpbuddy.hud.CaptureHud;
import dev.rtpbuddy.hud.HudAnchor;
import dev.rtpbuddy.hud.Minimap;
import dev.rtpbuddy.util.Lang;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Drag the two overlays where they belong.
 *
 * <p>Numbers in a settings list cannot answer the only question that matters -
 * whether an overlay is sitting on top of another mod's, or on top of the other
 * RTPBuddy one - so this screen puts the real boxes on the real screen, over the
 * game's own HUD, and lets them be pushed around. The world keeps rendering
 * behind them and the rest of the HUD is drawn by the game as usual, so the
 * collision being fixed stays visible the whole time.
 *
 * <p>Both boxes are always shown, switched off or not: placing something before
 * turning it on is the ordinary way round, and a box that vanished when its
 * switch was off would be unplaceable exactly when it needed placing.
 */
public class HudLayoutScreen extends Screen {

    /** How close to an edge or the centre line a box snaps, in pixels. */
    private static final int SNAP = 6;

    /** Which of the two overlays is being placed. */
    private enum Target {
        OVERLAY("hud_layout.target.overlay"),
        MINIMAP("hud_layout.target.minimap");

        private final String key;

        Target(String key) {
            this.key = key;
        }

        String label() {
            return Lang.t(key);
        }

        Target next() {
            return this == OVERLAY ? MINIMAP : OVERLAY;
        }
    }

    /** One placed box: where it is now, and where the config keeps it. */
    private final class Box {

        final Target target;
        int x;
        int y;
        int width;
        int height;

        Box(Target target) {
            this.target = target;
        }

        void measure() {
            MapConfig map = map();
            if (target == Target.OVERLAY) {
                width = CaptureHud.width(preview, map.hudScale);
                height = CaptureHud.height(preview, map.hudScale);
            } else {
                width = Minimap.width(map);
                height = Minimap.height(map);
            }
        }

        /** Reads the stored anchor and offsets back into a screen position. */
        void place() {
            measure();
            HudAnchor anchor = anchor();
            x = anchor.screenX(HudLayoutScreen.this.width, width, offsetX());
            y = anchor.screenY(HudLayoutScreen.this.height, height, offsetY());
            clamp();
        }

        void clamp() {
            x = Math.max(0, Math.min(HudLayoutScreen.this.width - width, x));
            y = Math.max(0, Math.min(HudLayoutScreen.this.height - height, y));
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        HudAnchor anchor() {
            MapConfig map = map();
            return HudAnchor.parse(target == Target.OVERLAY ? map.hudAnchor : map.minimapAnchor);
        }

        int offsetX() {
            MapConfig map = map();
            return target == Target.OVERLAY ? map.hudX : map.minimapX;
        }

        int offsetY() {
            MapConfig map = map();
            return target == Target.OVERLAY ? map.hudY : map.minimapY;
        }

        boolean on() {
            MapConfig map = map();
            return target == Target.OVERLAY ? map.hudEnabled : map.minimapEnabled;
        }

        /**
         * Writes the position back as an anchor plus an inward offset, choosing
         * the anchor from where the box actually landed - drag it to the bottom
         * right and it stays bottom right when the window is next resized.
         */
        void store() {
            MapConfig map = map();
            HudAnchor anchor = HudAnchor.nearest(HudLayoutScreen.this.width,
                    HudLayoutScreen.this.height, x, y, width, height);
            if (target == Target.OVERLAY) {
                map.hudAnchor = anchor.name();
                map.hudX = anchor.offsetX(HudLayoutScreen.this.width, width, x);
                map.hudY = anchor.offsetY(HudLayoutScreen.this.height, height, y);
            } else {
                map.minimapAnchor = anchor.name();
                map.minimapX = anchor.offsetX(HudLayoutScreen.this.width, width, x);
                map.minimapY = anchor.offsetY(HudLayoutScreen.this.height, height, y);
            }
            RTPBuddyClient.configManager().save();
        }
    }

    private final Screen parent;

    private List<CaptureHud.Line> preview = List.of();
    private final Box overlay = new Box(Target.OVERLAY);
    private final Box minimap = new Box(Target.MINIMAP);

    private Target selected = Target.OVERLAY;

    private boolean dragging;
    private int grabX;
    private int grabY;

    /** Which guide lines the last move snapped to, for the frame that shows them. */
    private boolean snappedX;
    private boolean snappedY;

    public HudLayoutScreen(Screen parent) {
        super(Text.literal(Lang.t("screen.hud_layout")));
        this.parent = parent;
    }

    private MapConfig map() {
        return RTPBuddyClient.config().map;
    }

    private Box box(Target target) {
        return target == Target.OVERLAY ? overlay : minimap;
    }

    private Box selected() {
        return box(selected);
    }

    @Override
    protected void init() {
        preview = CaptureHud.previewLines(map());
        overlay.place();
        minimap.place();

        int buttonWidth = 92;
        int gap = 4;
        int y = height - 28;
        int x = width / 2 - (buttonWidth * 4 + gap * 3) / 2;
        addDrawableChild(new RtpButton(x, y, buttonWidth, 20, RtpButton.Style.SURFACE,
                Lang.t("hud_layout.editing", selected().target.label()), this::cycleTarget));
        addDrawableChild(new RtpButton(x + buttonWidth + gap, y, buttonWidth, 20,
                RtpButton.Style.SURFACE, sizeLabel(), this::cycleSize));
        addDrawableChild(new RtpButton(x + (buttonWidth + gap) * 2, y, buttonWidth, 20,
                RtpButton.Style.SURFACE, Lang.t("hud_layout.reset"), this::reset));
        addDrawableChild(new RtpButton(x + (buttonWidth + gap) * 3, y, buttonWidth, 20,
                RtpButton.Style.PRIMARY, Lang.t("settings.done"), this::close));
    }

    private void cycleTarget() {
        selected = selected.next();
        clearAndInit();
    }

    private String sizeLabel() {
        MapConfig map = map();
        return selected == Target.OVERLAY
                ? Lang.t("hud_layout.scale", Math.round(CaptureHud.clampScale(map.hudScale) * 100))
                : Lang.t("hud_layout.size", MapConfig.clampMinimapSize(map.minimapSize));
    }

    /**
     * Steps the selected box's size. Both grow around their top left corner, so
     * a bigger box can push itself off the edge; re-placing keeps it reachable.
     */
    private void cycleSize() {
        MapConfig map = map();
        if (selected == Target.OVERLAY) {
            double[] steps = {0.75, 1.0, 1.25, 1.5, 2.0};
            double current = CaptureHud.clampScale(map.hudScale);
            int index = 0;
            for (int i = 0; i < steps.length; i++) {
                if (Math.abs(steps[i] - current) < 0.01) {
                    index = i;
                    break;
                }
            }
            map.hudScale = steps[(index + 1) % steps.length];
            preview = CaptureHud.previewLines(map);
        } else {
            int[] steps = {64, 80, 104, 128, 160, 192};
            int current = MapConfig.clampMinimapSize(map.minimapSize);
            int index = 0;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == current) {
                    index = i;
                    break;
                }
            }
            map.minimapSize = steps[(index + 1) % steps.length];
            Minimap.invalidate();
        }
        selected().place();
        selected().store();
        clearAndInit();
    }

    private void reset() {
        MapConfig map = map();
        if (selected == Target.OVERLAY) {
            map.hudAnchor = HudAnchor.TOP_LEFT.name();
            map.hudX = 4;
            map.hudY = 4;
            map.hudScale = 1.0;
            preview = CaptureHud.previewLines(map);
        } else {
            map.minimapAnchor = HudAnchor.BOTTOM_RIGHT.name();
            map.minimapX = 4;
            map.minimapY = 4;
            map.minimapSize = 104;
            Minimap.invalidate();
        }
        RTPBuddyClient.configManager().save();
        clearAndInit();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        // The minimap is drawn over the text overlay, so it is picked first when
        // the two are stacked - the box on top is the one being pointed at.
        Target hit = minimap.contains(mouseX, mouseY) ? Target.MINIMAP
                : overlay.contains(mouseX, mouseY) ? Target.OVERLAY : null;
        if (hit != null) {
            if (hit != selected) {
                selected = hit;
                clearAndInit();
            }
            Box box = box(hit);
            dragging = true;
            grabX = mouseX - box.x;
            grabY = mouseY - box.y;
            return true;
        }

        // Clicking bare screen jumps the selected box there, so one stranded off
        // an edge can be recovered without hunting for its corner.
        Box box = selected();
        box.x = mouseX - box.width / 2;
        box.y = mouseY - box.height / 2;
        dragging = true;
        grabX = box.width / 2;
        grabY = box.height / 2;
        snap();
        box.store();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging) {
            Box box = selected();
            box.x = (int) click.x() - grabX;
            box.y = (int) click.y() - grabY;
            snap();
            box.store();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            selected().store();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_TAB) {
            cycleTarget();
            return true;
        }
        int step = input.hasShift() ? 10 : 1;
        Box box = selected();
        switch (input.key()) {
            case GLFW.GLFW_KEY_LEFT -> {
                box.x -= step;
                afterNudge();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                box.x += step;
                afterNudge();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                box.y -= step;
                afterNudge();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                box.y += step;
                afterNudge();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(input);
    }

    private void afterNudge() {
        selected().clamp();
        selected().store();
    }

    /** Pulls the selected box onto an edge or the centre line when it comes close. */
    private void snap() {
        Box box = selected();
        snappedX = false;
        snappedY = false;
        int centeredX = (width - box.width) / 2;
        if (Math.abs(box.x - centeredX) <= SNAP) {
            box.x = centeredX;
            snappedX = true;
        } else if (Math.abs(box.x - 4) <= SNAP) {
            box.x = 4;
        } else if (Math.abs(width - (box.x + box.width) - 4) <= SNAP) {
            box.x = width - box.width - 4;
        }
        int centeredY = (height - box.height) / 2;
        if (Math.abs(box.y - centeredY) <= SNAP) {
            box.y = centeredY;
            snappedY = true;
        } else if (Math.abs(box.y - 4) <= SNAP) {
            box.y = 4;
        } else if (Math.abs(height - (box.y + box.height) - 4) <= SNAP) {
            box.y = height - box.height - 4;
        }
        box.clamp();
    }

    // ----------------------------------------------------------------- render

    /**
     * No blur and no dim: the whole point is to see the overlays against the game
     * exactly as they will look in play, including whatever else is on the HUD.
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (snappedX) {
            context.fill(width / 2, 0, width / 2 + 1, height, MapPalette.withAlpha(Theme.ACCENT, 0.5));
        }
        if (snappedY) {
            context.fill(0, height / 2, width, height / 2 + 1, MapPalette.withAlpha(Theme.ACCENT, 0.5));
        }

        CaptureHud.draw(context, map(), preview, overlay.x, overlay.y);
        Minimap.draw(context, map(), minimap.x, minimap.y);

        drawFrame(context, box(selected.next()), mouseX, mouseY, false);
        drawFrame(context, selected(), mouseX, mouseY, true);

        drawReadout(context);

        String hint = Lang.t("hud_layout.hint");
        int hintWidth = textRenderer.getWidth(hint);
        Theme.roundOutline(context, (width - hintWidth) / 2 - 6, 8, hintWidth + 12,
                textRenderer.fontHeight + 6, Theme.RADIUS_PILL, Theme.LINE, Theme.SURFACE);
        UiDraw.text(context, hint, (width - hintWidth) / 2, 11, MapPalette.TEXT_DIM);
    }

    /** The frame around a box: bright and handled when picked, a hairline when not. */
    private void drawFrame(DrawContext context, Box box, int mouseX, int mouseY, boolean picked) {
        boolean hovered = box.contains(mouseX, mouseY) || (picked && dragging);
        int color = picked
                ? (hovered ? Theme.ACCENT : Theme.LINE_BRIGHT)
                : MapPalette.withAlpha(Theme.LINE_BRIGHT, 0.5);
        UiDraw.border(context, box.x - 1, box.y - 1, box.width + 2, box.height + 2, color);
        if (picked) {
            drawHandles(context, box, color);
        }
        drawTag(context, box, picked);
    }

    /** The box's name, so the two are told apart before either is grabbed. */
    private void drawTag(DrawContext context, Box box, boolean picked) {
        String label = box.target.label() + (box.on() ? "" : "  " + Lang.t("word.off"));
        int labelWidth = textRenderer.getWidth(label);
        int tagX = Math.max(2, Math.min(width - labelWidth - 8, box.x));
        // Above the box, unless it is against the top edge - then inside it.
        int tagY = box.y - textRenderer.fontHeight - 4 >= 2
                ? box.y - textRenderer.fontHeight - 4
                : box.y + 2;
        Theme.roundRect(context, tagX - 2, tagY - 1, labelWidth + 6, textRenderer.fontHeight + 3,
                Theme.RADIUS_PILL, picked ? 0xE0101820 : 0xA0101820);
        UiDraw.text(context, label, tagX + 1, tagY,
                picked ? Theme.ACCENT : MapPalette.TEXT_DIM);
    }

    /** Anchor and offsets of the picked box, next to the box itself. */
    private void drawReadout(DrawContext context) {
        Box box = selected();
        String readout = Lang.t("hud_layout.readout",
                box.anchor().label(), box.offsetX(), box.offsetY());
        int readoutWidth = textRenderer.getWidth(readout);
        int readoutX = Math.max(4, Math.min(width - readoutWidth - 10, box.x));
        int below = box.y + box.height + 4;
        // Flip the readout above the box when it would sit under the button row.
        int readoutY = below > height - 40 ? Math.max(4, box.y - 14) : below;
        Theme.roundOutline(context, readoutX - 3, readoutY - 2, readoutWidth + 6,
                textRenderer.fontHeight + 4, Theme.RADIUS_PILL, Theme.LINE, Theme.SURFACE);
        UiDraw.text(context, readout, readoutX, readoutY, MapPalette.TEXT);
    }

    /** Corner ticks, so an empty box still shows something to grab. */
    private void drawHandles(DrawContext context, Box box, int color) {
        int size = 3;
        int x1 = box.x;
        int y1 = box.y;
        int x2 = box.x + box.width;
        int y2 = box.y + box.height;
        context.fill(x1 - 1, y1 - 1, x1 + size, y1 + 1, color);
        context.fill(x1 - 1, y1 - 1, x1 + 1, y1 + size, color);
        context.fill(x2 - size, y1 - 1, x2 + 1, y1 + 1, color);
        context.fill(x2 - 1, y1 - 1, x2 + 1, y1 + size, color);
        context.fill(x1 - 1, y2 - 1, x1 + size, y2 + 1, color);
        context.fill(x1 - 1, y2 - size, x1 + 1, y2 + 1, color);
        context.fill(x2 - size, y2 - 1, x2 + 1, y2 + 1, color);
        context.fill(x2 - 1, y2 - size, x2 + 1, y2 + 1, color);
    }

    @Override
    public void close() {
        overlay.store();
        minimap.store();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
