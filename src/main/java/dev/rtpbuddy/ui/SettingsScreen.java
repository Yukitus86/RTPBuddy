package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.config.AutoRtpConfig;
import dev.rtpbuddy.config.CaptureConfig;
import dev.rtpbuddy.config.GuardSettings;
import dev.rtpbuddy.config.MapConfig;
import dev.rtpbuddy.config.RegionPreset;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.hud.CaptureHud;
import dev.rtpbuddy.hud.HudAnchor;
import dev.rtpbuddy.util.BiomeCatalog;
import dev.rtpbuddy.util.Biomes;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Five tabs of settings on one scrolling card.
 *
 * <p>The screen owns its own scrolling rather than leaning on a vanilla list
 * widget, because the controls sit in two columns broken up by section headings
 * and no vanilla container does that. Content widgets are therefore registered
 * as selectable children only - they are drawn by hand inside a scissor, which
 * is what stops a long tab from spilling out over the card's edges.
 */
public class SettingsScreen extends Screen {

    private enum Tab {
        CAPTURE("settings.tab.capture"),
        AUTO("settings.tab.auto"),
        GUARDS("settings.tab.guards"),
        MAP("settings.tab.map"),
        HUD("settings.tab.hud");

        final String key;

        Tab(String key) {
            this.key = key;
        }

        String label() {
            return Lang.t(key);
        }
    }

    /** Row geometry. A row is one control plus the gap below it. */
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_STEP = 24;
    private static final int SECTION_STEP = 17;
    private static final int COLUMN_GAP = 8;
    private static final int CARD_PADDING = 12;
    private static final int SCROLLBAR_ROOM = 7;

    /** Title, subtitle and the tab strip above the content. */
    private static final int HEADER_HEIGHT = 70;

    /** The rule, the status line and the Done button below it. */
    private static final int FOOTER_HEIGHT = 38;

    private final Screen parent;
    private Tab tab = Tab.CAPTURE;

    /** Content widgets, drawn and scrolled by this screen rather than by {@link Screen}. */
    private final List<ClickableWidget> content = new ArrayList<>();
    private final List<Integer> contentLayoutY = new ArrayList<>();
    private final List<String> contentTips = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();

    /** A drawn (non-interactive) element of the scrolling area. */
    private record Piece(String text, int x, int layoutY, int width, boolean heading) {
    }

    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;
    private int viewTop;
    private int viewBottom;
    private int columnLeftX;
    private int columnRightX;
    private int columnWidth;

    /**
     * The biome picker hanging off one family row, and the row it hangs off.
     *
     * <p>It lives on the screen rather than in the widget list for the reason
     * the map's menu does - the screen owns its own input order - and it has to
     * survive {@link #clearAndInit()}, because every tick inside it rewrites the
     * config and rebuilds the rows underneath.
     */
    private final DropdownMenu biomeMenu = new DropdownMenu();
    private Biomes.Family menuFamily;
    private final Map<Biomes.Family, RtpButton> familyRows =
            new EnumMap<>(Biomes.Family.class);

    /** Recorded landings per biome and per family, counted when a tab is built. */
    private final Map<String, Integer> biomeCounts = new HashMap<>();
    private final Map<Biomes.Family, Integer> familyCounts =
            new EnumMap<>(Biomes.Family.class);

    /** Menu keys that are not biome ids. No biome id can collide with these. */
    private static final String KEY_WHOLE_FAMILY = "*";
    private static final String KEY_CLEAR = "-";

    private int cursorY;
    private boolean rightColumn;
    private int contentHeight;
    private int scroll;

    private String status = "";

    public SettingsScreen(Screen parent) {
        super(Text.literal(Lang.t("screen.settings")));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ build

    /**
     * The columns are laid out first and the card is then sized to what they
     * came to, so a short tab gets a short card instead of a screen-high box
     * with a footer stranded at the bottom. Only a tab too tall for the screen
     * scrolls.
     */
    @Override
    protected void init() {
        cardWidth = Math.min(404, width - 12);
        cardX = (width - cardWidth) / 2;

        int inner = cardWidth - CARD_PADDING * 2 - SCROLLBAR_ROOM;
        columnWidth = (inner - COLUMN_GAP) / 2;
        columnLeftX = cardX + CARD_PADDING;
        columnRightX = columnLeftX + columnWidth + COLUMN_GAP;

        cursorY = 0;
        rightColumn = false;
        switch (tab) {
            case CAPTURE -> buildCaptureTab();
            case AUTO -> buildAutoRtpTab();
            case GUARDS -> buildGuardsTab();
            case MAP -> buildMapTab();
            case HUD -> buildHudTab();
        }
        endRow();
        contentHeight = cursorY;

        int chrome = HEADER_HEIGHT + FOOTER_HEIGHT;
        cardHeight = Math.min(height - 12, chrome + contentHeight + 6);
        cardHeight = Math.max(chrome + ROW_STEP, cardHeight);
        cardY = (height - cardHeight) / 2;

        viewTop = cardY + HEADER_HEIGHT;
        viewBottom = cardY + cardHeight - FOOTER_HEIGHT;

        buildTabs(cardY + 42);
        buildFooter();

        scroll = clamp(scroll, 0, maxScroll());
        applyScroll();
    }

    private void buildTabs(int y) {
        Tab[] values = Tab.values();
        int gap = 3;
        int usable = cardWidth - CARD_PADDING * 2;
        int tabWidth = (usable - gap * (values.length - 1)) / values.length;
        int x = cardX + CARD_PADDING;
        for (Tab value : values) {
            Tab captured = value;
            RtpButton button = new RtpButton(x, y, tabWidth, 22, RtpButton.Style.TAB,
                    value.label(), () -> {
                biomeMenu.close();
                if (tab != captured) {
                    tab = captured;
                    scroll = 0;
                    clearAndInit();
                }
            });
            button.withSelected(value == tab);
            addDrawableChild(button);
            x += tabWidth + gap;
        }
    }

    private void buildFooter() {
        int doneWidth = Math.min(120, cardWidth / 3);
        int doneX = cardX + cardWidth - CARD_PADDING - doneWidth;
        int doneY = cardY + cardHeight - FOOTER_HEIGHT + 8;
        addDrawableChild(new RtpButton(doneX, doneY, doneWidth, 20, RtpButton.Style.PRIMARY,
                Lang.t("settings.done"), this::close));
    }

    // ------------------------------------------------------------------- tabs

    private void buildCaptureTab() {
        CaptureConfig capture = RTPBuddyClient.config().capture;

        section("settings.section.capture");
        toggle("settings.capture.enabled", () -> capture.enabled, v -> capture.enabled = v);
        toggle("settings.capture.auto", () -> capture.autoCapture, v -> capture.autoCapture = v);
        toggle("settings.capture.dimension",
                () -> capture.captureOnDimensionChange, v -> capture.captureOnDimensionChange = v);
        toggle("settings.capture.reject", () -> capture.rejectShortJumps, v -> capture.rejectShortJumps = v);
        toggle("settings.capture.csv", () -> capture.writeCsvMirror, v -> {
            capture.writeCsvMirror = v;
            RTPBuddyClient.store().setWriteCsvMirror(v);
        });

        section("settings.section.timing");
        cycle("settings.capture.threshold", new double[]{50, 100, 200, 500, 1000, 2000},
                () -> capture.teleportDistanceThreshold,
                v -> capture.teleportDistanceThreshold = v,
                v -> Lang.t("unit.blocks", Math.round(v)));
        cycle("settings.capture.arm_timeout", new double[]{100, 200, 300, 600, 1200},
                () -> capture.armTimeoutTicks,
                v -> capture.armTimeoutTicks = (int) v,
                v -> Lang.t("unit.seconds", Numbers.fixed(v / 20.0, 0)));
        cycle("settings.capture.settle", new double[]{0, 5, 10, 20, 40},
                () -> capture.settleTicks,
                v -> capture.settleTicks = (int) v,
                v -> Lang.t("unit.ticks", Math.round(v)));
        cycle("settings.capture.debounce", new double[]{0, 1000, 2000, 5000, 10000},
                () -> capture.saveDebounceMillis,
                v -> capture.saveDebounceMillis = (int) v,
                v -> Lang.t("unit.millis", Math.round(v)));

        section("settings.section.regions");
        toggle("settings.capture.unknown", () -> capture.captureUnknownRtp,
                v -> capture.captureUnknownRtp = v);
        action("settings.capture.preset", () -> {
            RTPBuddyClient.configManager().applyPreset("donutsmp");
            return Lang.t("settings.capture.preset_done");
        });
        note(regionSummary());
    }

    private void buildAutoRtpTab() {
        var auto = RTPBuddyClient.config().autoRtp;
        var controller = RTPBuddyClient.autoRtp();

        section("settings.section.auto_control");
        boolean running = controller.running();
        actionStyled(running ? "settings.auto.stop" : "settings.auto.start",
                running ? RtpButton.Style.DANGER : RtpButton.Style.PRIMARY, () -> {
                    if (controller.running()) {
                        controller.stop("reason.settings");
                        return Lang.t("map.status.auto_stopped");
                    }
                    client.setScreen(new AutoRtpDialog(this, started -> {
                    }));
                    return "";
                });
        // Only offered while a run exists: pause holds a run, it never arms one,
        // and a dead button on an idle screen would only suggest otherwise.
        if (running) {
            boolean held = controller.paused();
            actionStyled(held ? "settings.auto.resume" : "settings.auto.pause",
                    RtpButton.Style.SURFACE, () -> {
                        boolean nowHeld = controller.togglePaused();
                        return nowHeld
                                ? Lang.t("map.status.auto_paused", controller.sentThisRun())
                                : Lang.t("map.status.auto_resumed",
                                Math.round(controller.secondsUntilNext()));
                    });
            note(held
                    ? Lang.t("settings.auto.pause_note_held", controller.sentThisRun())
                    : Lang.t("settings.auto.pause_note"));
        }
        section("settings.section.auto_regions");
        // One row per destination the server actually takes. DonutSMP wants the
        // zone as an argument, so picking three of them is picking three
        // commands - and the loop then walks them one per teleport, which is
        // the only way to fill a map outside the zone a plain /rtp keeps
        // handing back.
        for (RegionPreset region : RTPBuddyClient.config().regions) {
            if (!region.sendable()) {
                continue;
            }
            String id = region.id;
            toggleLiteral(region.label + "  /" + region.command,
                    Lang.t("settings.auto.region_row.tip", region.command),
                    () -> containsIgnoreCase(auto.regions, id),
                    checked -> {
                        auto.regions.removeIf(entry -> entry.equalsIgnoreCase(id));
                        if (checked) {
                            auto.regions.add(id);
                        }
                    });
        }
        if (RTPBuddyClient.autoRtp().targetRegions().size() > 1) {
            cycle("settings.auto.order", new double[]{0, 1},
                    () -> "RANDOM".equalsIgnoreCase(auto.order) ? 1 : 0,
                    v -> auto.order = v >= 0.5 ? "RANDOM" : "ROUND_ROBIN",
                    v -> Lang.t(v >= 0.5 ? "settings.auto.order.random"
                            : "settings.auto.order.round_robin"));
        }
        String rotation = RTPBuddyClient.autoRtp().describeRotation();
        note(rotation.isEmpty()
                ? Lang.t("settings.auto.rotation_none")
                : Lang.t("settings.auto.rotation", rotation));

        section("settings.section.auto_rate");
        // Typed rather than cycled: a fixed ladder of steps never holds the
        // interval a given server actually wants, and the useful value is
        // whatever that server's own cooldown is.
        numberField("settings.auto.cooldown", auto.cooldownSeconds,
                v -> auto.cooldownSeconds = (int) Math.round(v));
        numberField("settings.auto.jitter", auto.jitterSeconds,
                v -> auto.jitterSeconds = (int) Math.round(v));
        toggle("settings.auto.manual_step", () -> auto.manualStep, v -> auto.manualStep = v);
        note(auto.manualStep
                ? Lang.t("settings.auto.manual_note", AutoRtpConfig.MIN_COOLDOWN_SECONDS)
                : Lang.t("settings.auto.rate_note", AutoRtpConfig.MIN_COOLDOWN_SECONDS));
        cycle("settings.auto.stop_after", new double[]{0, 10, 30, 60, 120},
                () -> auto.stopAfterMinutes,
                v -> auto.stopAfterMinutes = (int) v,
                v -> v <= 0 ? Lang.t("word.no_limit") : Lang.t("unit.minutes", Math.round(v)));
        cycle("settings.auto.max", new double[]{0, 25, 50, 100, 250, 500},
                () -> auto.maxPerSession,
                v -> auto.maxPerSession = (int) v,
                v -> v <= 0 ? Lang.t("word.no_limit") : String.valueOf(Math.round(v)));

        section("settings.section.auto_stops");
        toggle("settings.auto.wait", () -> auto.waitForCapture, v -> auto.waitForCapture = v);
        toggle("settings.auto.on_damage", () -> auto.stopOnDamage, v -> auto.stopOnDamage = v);
        toggle("settings.auto.on_guards", () -> auto.stopOnGuardViolation,
                v -> auto.stopOnGuardViolation = v);
        toggle("settings.auto.on_player", () -> auto.stopOnPlayerNearby,
                v -> auto.stopOnPlayerNearby = v);
        cycle("settings.auto.player_radius",
                new double[]{16, 32, 64, 128, 256},
                () -> auto.playerNearbyRadius,
                v -> auto.playerNearbyRadius = (int) v,
                v -> Lang.t("unit.blocks", Math.round(v)));

        buildFindSection(auto);
    }

    /**
     * The search order: the conditions that end a run because it found
     * something, as opposed to every other stop, which ends it because
     * something went wrong.
     *
     * <p>Collapsed behind its own switch. Six conditions and eighteen biome
     * families is a lot of rows to scroll past for the many runs that are just
     * filling a map, so nothing below the switch is built while it is off.
     */
    private void buildFindSection(dev.rtpbuddy.config.AutoRtpConfig auto) {
        section("settings.section.auto_find");
        toggle("settings.find.enabled", () -> auto.findEnabled, v -> auto.findEnabled = v);
        if (!auto.findEnabled) {
            note(Lang.t("settings.find.off_note"));
            return;
        }

        toggle("settings.find.new_cell", () -> auto.findNewCell, v -> auto.findNewCell = v);
        action("settings.find.cells", () -> {
            client.setScreen(new TextPromptDialog(this, Lang.t("settings.find.cells"),
                    Lang.t("settings.find.cells_hint", dev.rtpbuddy.stats.CellCoverage.totalCells()),
                    auto.findCells, text -> {
                        auto.findCells = text == null ? "" : text.trim();
                        RTPBuddyClient.configManager().save();
                    }));
            return "";
        });
        numberField("settings.find.min_distance", auto.findMinDistance,
                v -> auto.findMinDistance = v);
        numberField("settings.find.max_distance", auto.findMaxDistance,
                v -> auto.findMaxDistance = v);
        numberField("settings.find.near_x", auto.findNearX, v -> auto.findNearX = v);
        numberField("settings.find.near_z", auto.findNearZ, v -> auto.findNearZ = v);
        numberField("settings.find.near_radius", auto.findNearRadius,
                v -> auto.findNearRadius = v);
        action("settings.find.use_position", () -> {
            if (client.player == null) {
                return Lang.t("word.not_in_world");
            }
            auto.findNearX = client.player.getX();
            auto.findNearZ = client.player.getZ();
            if (auto.findNearRadius <= 0) {
                auto.findNearRadius = 500;
            }
            return Lang.t("settings.find.position_set",
                    Math.round(auto.findNearX), Math.round(auto.findNearZ));
        });

        section("settings.section.auto_find_biomes");
        countLandings();
        for (Biomes.Family family : Biomes.Family.values()) {
            familyRow(auto, family);
        }
        note(Lang.t("settings.find.biomes_note"));

        section("settings.section.auto_find_rules");
        cycle("settings.find.match", new double[]{0, 1},
                () -> auto.findMatchAll ? 1 : 0,
                v -> auto.findMatchAll = v >= 0.5,
                v -> Lang.t(v >= 0.5 ? "settings.find.match_all" : "settings.find.match_any"));
        cycle("settings.find.give_up", new double[]{0, 25, 50, 100, 250, 500},
                () -> auto.findGiveUpAfter,
                v -> auto.findGiveUpAfter = (int) v,
                v -> v <= 0 ? Lang.t("word.no_limit") : String.valueOf(Math.round(v)));
        toggle("settings.find.sound", () -> auto.findSound, v -> auto.findSound = v);
        String order = dev.rtpbuddy.capture.FindRule.describe(auto);
        note(order.isEmpty()
                ? Lang.t("settings.find.none_note")
                : Lang.t("settings.find.order_note", order));
    }

    private void buildGuardsTab() {
        GuardSettings guards = RTPBuddyClient.config().guards;

        section("settings.section.border");
        numberField("settings.guards.border_radius", guards.borderRadius, v -> guards.borderRadius = v);
        numberField("settings.guards.nether_border_radius", guards.netherBorderRadius,
                v -> guards.netherBorderRadius = v);
        numberField("settings.guards.end_border_radius", guards.endBorderRadius,
                v -> guards.endBorderRadius = v);
        numberField("settings.guards.border_guard", guards.borderGuard, v -> guards.borderGuard = v);
        toggle("settings.guards.square", () -> guards.squareBorder, v -> guards.squareBorder = v);

        section("settings.section.spawn");
        numberField("settings.guards.spawn_x", guards.spawnX, v -> guards.spawnX = v);
        numberField("settings.guards.spawn_z", guards.spawnZ, v -> guards.spawnZ = v);
        numberField("settings.guards.spawn_guard", guards.spawnGuard, v -> guards.spawnGuard = v);

        section("settings.section.tools");
        action("settings.guards.use_position", () -> {
            if (client.player == null) {
                return Lang.t("word.not_in_world");
            }
            guards.spawnX = client.player.getX();
            guards.spawnZ = client.player.getZ();
            return Lang.t("settings.guards.spawn_set",
                    Math.round(guards.spawnX), Math.round(guards.spawnZ));
        });
    }

    private void buildMapTab() {
        MapConfig map = RTPBuddyClient.config().map;

        section("settings.section.display");
        toggle("settings.map.remember", () -> map.rememberView, v -> map.rememberView = v);
        toggle("settings.map.remember_filter", () -> map.rememberFilter,
                v -> map.rememberFilter = v);
        toggle("settings.map.hide_empty_sessions", () -> map.hideEmptySessions,
                v -> map.hideEmptySessions = v);
        toggle("settings.map.grid", () -> map.showGrid, v -> map.showGrid = v);
        toggle("settings.map.scale_bar", () -> map.showScaleBar, v -> map.showScaleBar = v);
        toggle("settings.map.legend", () -> map.showLegend, v -> map.showLegend = v);
        toggle("settings.map.server_regions", () -> map.showServerRegions,
                v -> map.showServerRegions = v);
        toggle("settings.map.cell_board", () -> map.showCellBoard, v -> map.showCellBoard = v);
        toggle("settings.map.cell_board_numbers", () -> map.cellBoardNumbers,
                v -> map.cellBoardNumbers = v);

        section("settings.section.overlays");
        toggle("settings.map.border", () -> map.showBorder, v -> map.showBorder = v);
        toggle("settings.map.spawn_guard", () -> map.showSpawnGuard, v -> map.showSpawnGuard = v);
        toggle("settings.map.player", () -> map.showPlayer, v -> map.showPlayer = v);
        toggle("settings.map.player_pulse", () -> map.playerPulse, v -> map.playerPulse = v);
        toggle("settings.map.origin", () -> map.showOrigin, v -> map.showOrigin = v);
        toggle("settings.map.last_leg", () -> map.showLastLeg, v -> map.showLastLeg = v);

        section("settings.section.markers");
        toggle("settings.map.numbers", () -> map.showSampleNumbers, v -> map.showSampleNumbers = v);
        toggle("settings.map.round_markers", () -> map.roundMarkers, v -> map.roundMarkers = v);
        cycle("settings.map.marker_size", new double[]{1, 2, 3, 4, 5, 6},
                () -> map.markerRadius,
                v -> map.markerRadius = (int) v,
                v -> Lang.t("unit.pixels", Math.round(v * 2 + 1)));
        cycle("settings.map.density_cell", new double[]{64, 128, 256, 512, 1024, 2048},
                () -> map.densityCellSize,
                v -> map.densityCellSize = (int) v,
                v -> Lang.t("unit.blocks", Math.round(v)));
        cycle("settings.map.gap_mask_tile", new double[]{4_000, 8_000, 16_000, 32_000, 64_000},
                () -> map.gapMaskTile,
                v -> map.gapMaskTile = (int) v,
                v -> Lang.t("unit.blocks", Math.round(v)));
        cycle("settings.map.gap_raster_tile",
                new double[]{2_500, 5_000, 10_000, 25_000, 50_000},
                () -> map.gapRasterTile,
                v -> map.gapRasterTile = (int) v,
                v -> Lang.t("unit.blocks", Math.round(v)));
        cycle("settings.map.gap_top", new double[]{3, 5, 8, 12},
                () -> map.gapTopCount,
                v -> map.gapTopCount = (int) v,
                v -> String.valueOf(Math.round(v)));
        cycle("settings.map.gap_scheme",
                new double[]{GapScheme.CLASSIC.ordinal(), GapScheme.TRAFFIC.ordinal()},
                () -> GapScheme.of(map.gapScheme).ordinal(),
                v -> map.gapScheme = GapScheme.values()[(int) v].name(),
                v -> Lang.t(GapScheme.values()[(int) v].key()));
        note(Lang.t("settings.map.gap_note"));

        section("settings.section.performance");
        cycle("settings.map.density_budget",
                new double[]{0, 250, 500, 1000, 2000, 5000},
                () -> map.densityBudget,
                v -> map.densityBudget = (int) v,
                v -> v <= 0 ? Lang.t("word.off") : String.valueOf(Math.round(v)));
        note(map.densityBudget <= 0
                ? Lang.t("settings.map.density_note_off")
                : Lang.t("settings.map.density_note", MapConfig.clampDensityBudget(map.densityBudget)));
        cycle("settings.map.fps_limit", new double[]{0, 30, 60, 90, 120},
                () -> map.mapFpsLimit,
                v -> map.mapFpsLimit = (int) v,
                v -> v <= 0 ? Lang.t("word.off") : Lang.t("unit.fps", Math.round(v)));

        section("settings.section.layout");
        toggle("settings.map.reopen", () -> map.reopenAfterTeleport,
                v -> map.reopenAfterTeleport = v);
        action("settings.map.panel_reset", () -> {
            map.panelLeftFraction = MapConfig.DEFAULT_PANEL_LEFT;
            map.panelRightFraction = MapConfig.DEFAULT_PANEL_RIGHT;
            return Lang.t("map.status.panels_reset");
        });
        note(Lang.t("settings.map.reopen_note"));
    }

    /**
     * The overlay tab. It exists because the game screen is shared: RTPBuddy has
     * no say over what other mods draw in the top left, so where its own overlay
     * sits, how big it is and which rows it carries all have to be the player's
     * to decide.
     */
    private void buildHudTab() {
        MapConfig map = RTPBuddyClient.config().map;

        section("settings.section.hud_position");
        toggle("settings.hud.enabled", () -> map.hudEnabled, v -> map.hudEnabled = v);
        actionStyled("settings.hud.place", RtpButton.Style.PRIMARY, () -> {
            client.setScreen(new HudLayoutScreen(this));
            return Lang.t("settings.hud.placing");
        });
        cycle("settings.hud.anchor", anchorSteps(),
                () -> HudAnchor.parse(map.hudAnchor).ordinal(),
                v -> map.hudAnchor = HudAnchor.values()[(int) v].name(),
                v -> HudAnchor.values()[(int) v].label());
        numberField("settings.hud.offset_x", map.hudX, v -> map.hudX = (int) Math.round(v));
        numberField("settings.hud.offset_y", map.hudY, v -> map.hudY = (int) Math.round(v));
        note(Lang.t("settings.hud.place_note"));

        section("settings.section.hud_look");
        cycle("settings.hud.scale", new double[]{0.5, 0.75, 1.0, 1.25, 1.5, 2.0},
                () -> CaptureHud.clampScale(map.hudScale),
                v -> map.hudScale = v,
                v -> Math.round(v * 100) + "%");
        toggle("settings.hud.background", () -> map.hudBackground, v -> map.hudBackground = v);
        cycle("settings.hud.opacity", new double[]{0, 20, 40, 60, 80, 100},
                () -> CaptureHud.clampOpacity(map.hudBackgroundOpacity),
                v -> map.hudBackgroundOpacity = (int) v,
                v -> Math.round(v) + "%");
        toggle("settings.hud.shadow", () -> map.hudTextShadow, v -> map.hudTextShadow = v);

        section("settings.section.hud_rows");
        toggle("settings.hud.row_auto", () -> map.hudShowAuto, v -> map.hudShowAuto = v);
        toggle("settings.hud.row_state", () -> map.hudShowState, v -> map.hudShowState = v);
        toggle("settings.hud.row_counters", () -> map.hudShowCounters, v -> map.hudShowCounters = v);
        toggle("settings.hud.row_last", () -> map.hudShowLast, v -> map.hudShowLast = v);
        toggle("settings.hud.row_context", () -> map.hudShowContext,
                v -> map.hudShowContext = v);
        toggle("settings.hud.row_status", () -> map.hudShowStatus, v -> map.hudShowStatus = v);

        section("settings.section.minimap");
        toggle("settings.minimap.enabled", () -> map.minimapEnabled,
                v -> map.minimapEnabled = v);
        cycle("settings.minimap.size", new double[]{64, 80, 104, 128, 160, 192},
                () -> MapConfig.clampMinimapSize(map.minimapSize),
                v -> map.minimapSize = (int) v,
                v -> Lang.t("unit.pixels", Math.round(v)));
        cycle("settings.minimap.opacity", new double[]{30, 50, 65, 78, 90, 100},
                () -> MapConfig.clampMinimapOpacity(map.minimapOpacity),
                v -> map.minimapOpacity = (int) v,
                v -> Math.round(v) + "%");
        cycle("settings.minimap.scope", new double[]{0, 1},
                () -> "ALL".equalsIgnoreCase(map.minimapScope) ? 1 : 0,
                v -> map.minimapScope = v >= 0.5 ? "ALL" : "SESSION",
                v -> Lang.t(v >= 0.5 ? "map.scope.all" : "map.scope.session"));
        note(Lang.t("settings.minimap.note"));

        section("settings.section.minimap_content");
        toggle("settings.minimap.regions", () -> map.minimapShowRegions,
                v -> map.minimapShowRegions = v);
        toggle("settings.minimap.cell_numbers", () -> map.minimapShowCellNumbers,
                v -> map.minimapShowCellNumbers = v);
        toggle("settings.minimap.gap_tiles", () -> map.minimapShowGapTiles,
                v -> map.minimapShowGapTiles = v);
        toggle("settings.minimap.gap_numbers", () -> map.minimapGapNumbers,
                v -> map.minimapGapNumbers = v);
        toggle("settings.minimap.axes", () -> map.minimapShowAxes,
                v -> map.minimapShowAxes = v);
        toggle("settings.minimap.last_leg", () -> map.minimapShowLastLeg,
                v -> map.minimapShowLastLeg = v);
        toggle("settings.minimap.caption", () -> map.minimapShowCaption,
                v -> map.minimapShowCaption = v);

        section("settings.section.tools");
        action("settings.hud.reset", () -> {
            map.hudAnchor = HudAnchor.TOP_LEFT.name();
            map.hudX = 4;
            map.hudY = 4;
            map.hudScale = 1.0;
            map.hudBackground = true;
            map.hudBackgroundOpacity = 60;
            map.hudTextShadow = false;
            return Lang.t("settings.hud.reset_done");
        });
        action("settings.minimap.reset", () -> {
            map.minimapAnchor = HudAnchor.BOTTOM_RIGHT.name();
            map.minimapX = 4;
            map.minimapY = 4;
            map.minimapSize = 104;
            map.minimapOpacity = 78;
            dev.rtpbuddy.hud.Minimap.invalidate();
            return Lang.t("settings.minimap.reset_done");
        });
    }

    /** One step per anchor, so the cycle button walks them in declaration order. */
    private static double[] anchorSteps() {
        HudAnchor[] values = HudAnchor.values();
        double[] steps = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            steps[i] = i;
        }
        return steps;
    }

    // ----------------------------------------------------------- region helper

    private String regionSummary() {
        StringBuilder regions = new StringBuilder(Lang.t("settings.regions"));
        for (RegionPreset region : RTPBuddyClient.config().regions) {
            regions.append(region.id)
                    .append(region.patternValid() ? "" : Lang.t("settings.bad_pattern"))
                    .append("  ");
        }
        return regions.toString();
    }

    private double[] regionSteps() {
        int count = Math.max(1, RTPBuddyClient.config().regions.size());
        double[] steps = new double[count];
        for (int i = 0; i < count; i++) {
            steps[i] = i;
        }
        return steps;
    }

    private int regionIndex(String id) {
        var regions = RTPBuddyClient.config().regions;
        for (int i = 0; i < regions.size(); i++) {
            if (regions.get(i).id.equalsIgnoreCase(id)) {
                return i;
            }
        }
        return 0;
    }

    private String regionIdAt(int index) {
        var regions = RTPBuddyClient.config().regions;
        return regions.isEmpty() ? "overworld" : regions.get(Math.floorMod(index, regions.size())).id;
    }

    private String regionLabelAt(int index) {
        var regions = RTPBuddyClient.config().regions;
        if (regions.isEmpty()) {
            return Lang.t("settings.auto.no_region");
        }
        var region = regions.get(Math.floorMod(index, regions.size()));
        return region.sendable() ? region.label + " - /" + region.command : region.label;
    }

    // --------------------------------------------------------------- controls

    private void toggle(String labelKey, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        RtpButton button = RtpButton.toggle(Lang.t(labelKey), getter.get(),
                Lang.onOff(getter.get()), () -> {
                    setter.accept(!getter.get());
                    RTPBuddyClient.configManager().save();
                    clearAndInit();
                });
        place(button, labelKey);
    }

    /**
     * A toggle whose caption is a value rather than a translation key - one
     * region per row, named by the command it sends.
     */
    private void toggleLiteral(String label, String tooltip, Supplier<Boolean> getter,
                               Consumer<Boolean> setter) {
        RtpButton button = RtpButton.toggle(label, getter.get(), Lang.onOff(getter.get()), () -> {
            setter.accept(!getter.get());
            RTPBuddyClient.configManager().save();
            clearAndInit();
        });
        placeLiteral(button, tooltip);
    }

    /** {@link #place} for a control whose caption is a value, not a lang key. */
    private void placeLiteral(RtpButton button, String tooltip) {
        int[] slot = nextSlot();
        button.setX(slot[0]);
        button.setWidth(columnWidth);
        button.setHeight(ROW_HEIGHT);
        addSelectableChild(button);
        content.add(button);
        contentLayoutY.add(slot[1]);
        contentTips.add(tooltip);
    }

    /**
     * One row per family, opening the family's own biome list.
     *
     * <p>A row used to be a plain switch, which is the right control for
     * eighteen colour bands and the wrong one for the question actually being
     * asked. Nobody sends a run out to find "rare" - they send it out to find
     * the Pale Garden, and the family is only how you get there. So the row
     * still says whether the family is armed, but pressing it opens the biomes
     * behind it, each with the number of landings already recorded in it: a zero
     * is the whole point, because that is a biome the search has never reached.
     */
    private void familyRow(AutoRtpConfig auto, Biomes.Family family) {
        List<String> picked = pickedIn(auto, family);
        boolean whole = containsIgnoreCase(auto.findFamilies, family.name());
        String value = UiDraw.trim(familyValue(auto, family, picked), columnWidth / 2 - 12);
        RtpButton button = RtpButton.toggle(family.label(), whole || !picked.isEmpty(), value,
                () -> openFamilyMenu(family));
        placeLiteral(button, Lang.t("settings.find.family_row.tip", family.label()));
        familyRows.put(family, button);
    }

    /** What the row's pill says: off, the whole family, one biome, or a count. */
    private String familyValue(AutoRtpConfig auto, Biomes.Family family, List<String> picked) {
        boolean whole = containsIgnoreCase(auto.findFamilies, family.name());
        if (whole) {
            return picked.isEmpty()
                    ? Lang.t("settings.find.family_whole")
                    : Lang.t("settings.find.family_whole_plus", picked.size());
        }
        if (picked.isEmpty()) {
            return Lang.t("word.off");
        }
        if (picked.size() == 1) {
            return Biomes.label(picked.get(0));
        }
        return Lang.t("settings.find.family_count", picked.size());
    }

    /** The chosen biomes that belong to this family, in the order they were added. */
    private static List<String> pickedIn(AutoRtpConfig auto, Biomes.Family family) {
        List<String> found = new ArrayList<>();
        for (String id : auto.findBiomes) {
            if (id != null && Biomes.family(id) == family) {
                found.add(id);
            }
        }
        return found;
    }

    /**
     * Landings per biome, for the numbers in the menu. Counted once per rebuild
     * of the tab, never per frame.
     */
    private void countLandings() {
        biomeCounts.clear();
        familyCounts.clear();
        for (RtpSample sample : RTPBuddyClient.store().samples()) {
            String id = sample.biome();
            if (id != null && !id.isBlank()) {
                biomeCounts.merge(id, 1, Integer::sum);
            }
            familyCounts.merge(Biomes.family(id), 1, Integer::sum);
        }
    }

    /**
     * Opens the picker under a family row.
     *
     * <p>The anchor is read off the widget rather than off the layout, because
     * the rows are scrolled: the layout y is where the row would sit at the top
     * of the tab, and the menu has to hang from where it actually is.
     */
    private void openFamilyMenu(Biomes.Family family) {
        RtpButton row = familyRows.get(family);
        if (row == null) {
            return;
        }
        AutoRtpConfig auto = RTPBuddyClient.config().autoRtp;
        menuFamily = family;

        List<DropdownMenu.Item> items = new ArrayList<>();
        items.add(new DropdownMenu.Item(KEY_WHOLE_FAMILY, Lang.t("settings.find.family_whole"),
                family.color(), familyCounts.getOrDefault(family, 0),
                containsIgnoreCase(auto.findFamilies, family.name())));
        for (String id : BiomeCatalog.in(family, biomeCounts.keySet())) {
            items.add(new DropdownMenu.Item(id, Biomes.label(id), family.color(),
                    biomeCounts.getOrDefault(id, 0), containsIgnoreCase(auto.findBiomes, id)));
        }
        if (containsIgnoreCase(auto.findFamilies, family.name()) || !pickedIn(auto, family).isEmpty()) {
            items.add(new DropdownMenu.Item(KEY_CLEAR, Lang.t("settings.find.family_clear"),
                    Theme.TEXT_FAINT, -1, false));
        }

        biomeMenu.openSticky(row.getX(), row.getY() + row.getHeight(), height - 4,
                width - 4, items);
    }

    /**
     * A tick in the picker. The row underneath has to be rebuilt for its pill to
     * change, and the menu then reopened so the ticks it draws are the ones the
     * config now holds.
     */
    private void handleFamilyPick(String key) {
        AutoRtpConfig auto = RTPBuddyClient.config().autoRtp;
        Biomes.Family family = menuFamily;
        if (family == null || key == null) {
            return;
        }
        String name = family.name();
        if (KEY_WHOLE_FAMILY.equals(key)) {
            boolean on = containsIgnoreCase(auto.findFamilies, name);
            auto.findFamilies.removeIf(entry -> entry.equalsIgnoreCase(name));
            if (!on) {
                auto.findFamilies.add(name);
            }
        } else if (KEY_CLEAR.equals(key)) {
            auto.findFamilies.removeIf(entry -> entry.equalsIgnoreCase(name));
            auto.findBiomes.removeIf(id -> id != null && Biomes.family(id) == family);
        } else {
            boolean on = containsIgnoreCase(auto.findBiomes, key);
            auto.findBiomes.removeIf(entry -> entry.equalsIgnoreCase(key));
            if (!on) {
                auto.findBiomes.add(key);
            }
        }
        RTPBuddyClient.configManager().save();
        clearAndInit();
        openFamilyMenu(family);
    }

    private static boolean containsIgnoreCase(java.util.List<String> values, String needle) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    private void cycle(String labelKey, double[] steps, Supplier<Number> getter,
                       java.util.function.DoubleConsumer setter,
                       java.util.function.DoubleFunction<String> format) {
        double current = getter.get().doubleValue();
        RtpButton button = RtpButton.value(Lang.t(labelKey), format.apply(current), () -> {
            int index = -1;
            for (int i = 0; i < steps.length; i++) {
                if (Math.abs(steps[i] - current) < 1e-6) {
                    index = i;
                    break;
                }
            }
            setter.accept(steps[(index + 1) % steps.length]);
            RTPBuddyClient.configManager().save();
            clearAndInit();
        });
        place(button, labelKey);
    }

    private void action(String labelKey, Supplier<String> action) {
        actionStyled(labelKey, RtpButton.Style.SURFACE, action);
    }

    private void actionStyled(String labelKey, RtpButton.Style style, Supplier<String> action) {
        RtpButton button = new RtpButton(0, 0, columnWidth, ROW_HEIGHT, style,
                Lang.t(labelKey), () -> {
            status = action.get();
            RTPBuddyClient.configManager().save();
            clearAndInit();
        });
        place(button, labelKey);
    }

    /**
     * A labelled number box. The label is trimmed to the space left of the field
     * so a long German caption cannot run underneath the input.
     */
    private void numberField(String labelKey, double value, Consumer<Double> setter) {
        String label = Lang.t(labelKey);
        int[] slot = nextSlot();
        int fieldWidth = Math.max(48, columnWidth / 2 - 8);
        int fieldX = slot[0] + columnWidth - fieldWidth;

        TextFieldWidget field = new TextFieldWidget(textRenderer, fieldX, 0, fieldWidth,
                ROW_HEIGHT - 2, Text.literal(label));
        field.setText(Numbers.plain(value));
        field.setTextPredicate(text -> text.isEmpty() || text.matches("-?\\d*\\.?\\d*"));
        field.setChangedListener(text -> {
            try {
                setter.accept(text.isEmpty() || text.equals("-") ? 0.0 : Double.parseDouble(text));
                RTPBuddyClient.configManager().save();
            } catch (NumberFormatException ignored) {
                // Partial input while typing; keep the previous value.
            }
        });
        addSelectableChild(field);
        content.add(field);
        contentLayoutY.add(slot[1] + 1);
        contentTips.add(tip(labelKey));
        pieces.add(new Piece(UiDraw.trim(label, columnWidth - fieldWidth - 6),
                slot[0], slot[1] + 6, columnWidth, false));
    }

    /**
     * Full-width explanatory text under the controls it belongs to. Wrapped
     * rather than trimmed: these lines are the explanation, so cutting one off
     * with an ellipsis defeats the point of having it.
     */
    private void note(String text) {
        endRow();
        int noteWidth = columnWidth * 2 + COLUMN_GAP;
        for (String line : UiDraw.wrap(text, noteWidth)) {
            pieces.add(new Piece(line, columnLeftX, cursorY, noteWidth, false));
            cursorY += UiDraw.lineHeight();
        }
        cursorY += 4;
    }

    private void section(String labelKey) {
        endRow();
        if (cursorY > 0) {
            cursorY += 6;
        }
        pieces.add(new Piece(Lang.t(labelKey), columnLeftX, cursorY,
                columnWidth * 2 + COLUMN_GAP, true));
        cursorY += SECTION_STEP;
    }

    private void place(RtpButton button, String labelKey) {
        int[] slot = nextSlot();
        button.setX(slot[0]);
        button.setWidth(columnWidth);
        button.setHeight(ROW_HEIGHT);
        addSelectableChild(button);
        content.add(button);
        contentLayoutY.add(slot[1]);
        contentTips.add(tip(labelKey));
    }

    /** Two columns, top to bottom. Returns {x, layoutY}. */
    private int[] nextSlot() {
        int x = rightColumn ? columnRightX : columnLeftX;
        int y = cursorY;
        if (rightColumn) {
            cursorY += ROW_STEP;
        }
        rightColumn = !rightColumn;
        return new int[]{x, y};
    }

    /** Closes a half-filled row so the next block starts on the left. */
    private void endRow() {
        if (rightColumn) {
            cursorY += ROW_STEP;
            rightColumn = false;
        }
    }

    /** Explanation for a control, or null when the language file has none. */
    private static String tip(String labelKey) {
        return Lang.tOrNull(labelKey + ".tip");
    }

    @Override
    protected void clearAndInit() {
        content.clear();
        contentLayoutY.clear();
        contentTips.clear();
        pieces.clear();
        familyRows.clear();
        rightColumn = false;
        super.clearAndInit();
    }

    // -------------------------------------------------------------- scrolling

    private int viewHeight() {
        return Math.max(0, viewBottom - viewTop);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewHeight());
    }

    /** Moves the content widgets to their scrolled position and hides the rest. */
    private void applyScroll() {
        for (int i = 0; i < content.size(); i++) {
            ClickableWidget widget = content.get(i);
            int y = viewTop + contentLayoutY.get(i) - scroll;
            widget.setY(y);
            widget.visible = y + widget.getHeight() > viewTop && y < viewBottom;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        // The menu hangs off a row's screen position, so scrolling the rows out
        // from under it would leave it pointing at nothing. It scrolls itself
        // when it is long enough to need it, and swallows the wheel either way.
        if (biomeMenu.isOpen()) {
            biomeMenu.scrolled(verticalAmount);
            return true;
        }
        if (maxScroll() > 0 && mouseX >= cardX && mouseX < cardX + cardWidth
                && mouseY >= viewTop && mouseY < viewBottom) {
            scroll = clamp(scroll - (int) (verticalAmount * 14), 0, maxScroll());
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * A control whose row is only half inside the viewport is still drawn - the
     * scissor clips it - but it must not swallow a click landing outside. Hiding
     * the content for the duration of the dispatch is the least invasive way to
     * let the tabs and the footer have that click instead.
     */
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Before the widgets: the menu covers the rows it hangs over, and a
        // press on the row that opened it has to reach the menu, not reopen it.
        if (biomeMenu.isOpen()) {
            DropdownMenu.Pick pick = biomeMenu.click(click.x(), click.y());
            if (pick != null) {
                handleFamilyPick(pick.key());
            }
            return true;
        }
        if (click.y() >= viewTop && click.y() < viewBottom) {
            return super.mouseClicked(click, doubled);
        }
        for (ClickableWidget widget : content) {
            widget.visible = false;
        }
        boolean handled = super.mouseClicked(click, doubled);
        applyScroll();
        return handled;
    }

    // -------------------------------------------------------------- rendering

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Screen.renderWithTooltip already calls renderBackground before this
        // method, and the blur behind it may only be applied once per frame -
        // calling it again here crashes the game. Dim the world directly instead.
        context.fill(0, 0, width, height, Theme.SCRIM);
        Theme.card(context, cardX, cardY, cardWidth, cardHeight);

        renderHeader(context);
        renderContent(context, mouseX, mouseY, delta);
        renderFooter(context);

        super.render(context, mouseX, mouseY, delta);

        // Last, and instead of the tooltips: the menu is drawn outside the
        // content scissor so it may overhang the card, and a tooltip for the row
        // underneath it would be describing something the player cannot see.
        if (biomeMenu.isOpen()) {
            biomeMenu.render(context, mouseX, mouseY);
        } else {
            renderContentTooltip(context, mouseX, mouseY);
        }
    }

    private void renderHeader(DrawContext context) {
        Theme.roundRectTop(context, cardX + 1, cardY + 1, cardWidth - 2, 40,
                Theme.RADIUS_CARD - 1, Theme.SURFACE_HEADER);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, cardY + 10, Theme.TEXT);
        String subtitle = UiDraw.trim(Lang.t("settings.subtitle"), cardWidth - 24);
        UiDraw.text(context, subtitle, cardX + (cardWidth - textRenderer.getWidth(subtitle)) / 2,
                cardY + 24, Theme.TEXT_FAINT);
        // The rule the active tab sits on; the tab overpaints its own segment.
        Theme.rule(context, cardX + 1, cardY + 64, cardWidth - 2);
    }

    private void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        context.enableScissor(cardX + 1, viewTop, cardX + cardWidth - 1, viewBottom);
        for (Piece piece : pieces) {
            int y = viewTop + piece.layoutY() - scroll;
            if (y + UiDraw.lineHeight() < viewTop || y > viewBottom) {
                continue;
            }
            if (piece.heading()) {
                Theme.sectionLabel(context, piece.text(), piece.x(), y, piece.width());
            } else {
                UiDraw.text(context, UiDraw.trim(piece.text(), piece.width()),
                        piece.x(), y, Theme.TEXT_DIM);
            }
        }
        for (ClickableWidget widget : content) {
            widget.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();

        Theme.scrollbar(context, cardX + cardWidth - CARD_PADDING + 4, viewTop, viewHeight(),
                contentHeight, scroll);
    }

    private void renderFooter(DrawContext context) {
        int footerTop = cardY + cardHeight - FOOTER_HEIGHT;
        Theme.rule(context, cardX + 1, footerTop, cardWidth - 2);
        if (status.isEmpty()) {
            return;
        }
        int room = cardWidth - CARD_PADDING * 2 - Math.min(120, cardWidth / 3) - 10;
        UiDraw.text(context, UiDraw.trim(status, room), cardX + CARD_PADDING,
                footerTop + 15, Theme.ACCENT);
    }

    /**
     * Tooltips for the scrolling controls are drawn here rather than through
     * {@code setTooltip}, because a vanilla tooltip would be painted inside the
     * content scissor and cut off at the card's edge.
     */
    private void renderContentTooltip(DrawContext context, int mouseX, int mouseY) {
        if (mouseY < viewTop || mouseY >= viewBottom) {
            return;
        }
        for (int i = 0; i < content.size(); i++) {
            ClickableWidget widget = content.get(i);
            String text = contentTips.get(i);
            if (text == null || !widget.visible || !widget.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal(headlineFor(widget)));
            for (String wrapped : UiDraw.wrap(text, 190)) {
                lines.add(Text.literal(wrapped));
            }
            context.drawTooltip(textRenderer, lines, mouseX, mouseY);
            return;
        }
    }

    private static String headlineFor(ClickableWidget widget) {
        return widget instanceof RtpButton button ? button.label() : widget.getMessage().getString();
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void close() {
        RTPBuddyClient.configManager().save();
        client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        // Escape before the screen's: it dismisses the menu, and closing the
        // settings out from under an open picker is not what it was pressed for.
        if (biomeMenu.isOpen() && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            biomeMenu.close();
            return true;
        }
        if (PanicKey.handle(input)) {
            clearAndInit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
