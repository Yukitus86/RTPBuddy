package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.config.GuardSettings;
import dev.rtpbuddy.config.MapConfig;
import dev.rtpbuddy.config.RTPBuddyConfig;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.data.SessionRecord;
import dev.rtpbuddy.stats.SampleStats;
import dev.rtpbuddy.stats.StatsEngine;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import dev.rtpbuddy.util.Worlds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The map. One screen for every view of the recorded landings: layout, input,
 * the stats column, the sample list, the detail card and the filter bar.
 *
 * <p>What it plots is a matter of scope, switched with the one button in the
 * top right rather than by opening a second screen. The two used to be separate
 * screens that differed in a single method and one sidebar panel, which meant
 * two keys to remember, two stored views to keep straight, and no way to
 * compare a sitting against the whole history without leaving and coming back.
 */
public class MapScreen extends Screen {

    /** Which landings the map is plotting. */
    public enum Scope {

        /** This sitting only, in recording order. */
        SESSION,

        /** Every sitting ever recorded, with the session picker in the sidebar. */
        ALL;

        Scope next() {
            return this == SESSION ? ALL : SESSION;
        }

        static Scope parse(String name) {
            for (Scope scope : values()) {
                if (scope.name().equalsIgnoreCase(name)) {
                    return scope;
                }
            }
            return SESSION;
        }
    }

    /**
     * The toolbar lives at the top, directly under the title row: the buttons are
     * what the player reaches for, and putting them where the eye already is
     * leaves the bottom edge free for the one line of text that explains what is
     * currently on screen.
     */
    private static final int TITLE_ROW = 24;
    private static final int TOOLBAR_ROW = 22;
    private static final int TOP_BAR = TITLE_ROW + TOOLBAR_ROW;
    private static final int BOTTOM_BAR = 20;
    private static final int GAP = 4;

    /** Width of the draggable divider between a side panel and the canvas. */
    private static final int SPLITTER = 6;

    /** Room kept clear on the right of a scrolling list for its scrollbar. */
    private static final int SCROLLBAR_ROOM = 7;

    /**
     * The map is the point of the screen, so it gets a floor of its own. Showing
     * both panels demands considerably more than the bare minimum, because at a
     * large GUI scale two panels plus a legible map simply do not fit.
     */
    private static final int MIN_CANVAS = 180;
    private static final int MIN_CANVAS_BOTH_PANELS = 240;

    /** A panel narrower than this cannot show a label and a value side by side. */
    private static final int MIN_PANEL = 110;

    /** Floors for the two halves of the sidebar, either side of its divider. */
    private static final int MIN_SIDEBAR_TOP = 74;

    /** Height the session card adds for its "new sitting" button. */
    private static final int SESSION_CARD_BUTTON = 17;
    private static final int MIN_SAMPLE_LIST = 96;

    /** Clearance kept between the sidebar's two halves for the divider. */
    private static final int SIDEBAR_GUTTER = 9;

    /** Rows the colour key will show before it collapses into a "more" row. */
    private static final int LEGEND_ROWS = 6;

    /** Which side panels are shown. Tab cycles through them. */
    private enum PanelMode {
        BOTH, LEFT_ONLY, RIGHT_ONLY, NONE
    }

    /** Which divider the mouse is currently dragging, if any. */
    private enum Splitter {
        NONE,
        /** Vertical, between the stats column and the canvas. */
        LEFT,
        /** Vertical, between the canvas and the sidebar. */
        RIGHT,
        /** Horizontal, inside the sidebar, between the session picker and the sample list. */
        SIDEBAR
    }

    private PanelMode panelMode = PanelMode.BOTH;
    private int leftPanel;
    private int rightPanel;
    private Splitter dragging = Splitter.NONE;

    /** Sub-pixel remainder of the divider drag, so slow drags still move. */
    private double dragAccumulator;

    private static final DateTimeFormatter LEGEND_DAY =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final MapCanvas canvas = new MapCanvas();
    private final SampleFilter filter = new SampleFilter();
    private final StatsPanel statsPanel = new StatsPanel();
    private final StatsEngine statsEngine = new StatsEngine();

    private List<RtpSample> visible = List.of();
    private SampleStats stats = SampleStats.empty();
    private int lastStoreSize = -1;

    private int selectedSample = -1;
    private int hoveredIndex = -1;

    private int statsScroll;
    private int listScroll;

    /**
     * What the sample list actually drew last frame. Scrolling and hit-testing
     * both used to re-derive this from a guess - the old scroll ceiling subtracted
     * a flat 60 pixels of chrome - and with the session picker open that guess was
     * two hundred pixels short, so the last rows could never be reached.
     */
    private int listViewTop;
    private int listViewHeight;
    private int listContentHeight;

    /** Left edge of the sample list's scrollbar strip, from the last draw. */
    private int listScrollbarX = Integer.MAX_VALUE;

    /** True while the sample list's scrollbar is being dragged. */
    private boolean draggingList;

    /** Screen y of the sidebar's horizontal divider, or -1 while it is not shown. */
    private int sidebarDividerY = -1;

    /** {x, y, width, height} of the session card's button, from the last draw. */
    private int[] newSessionButton;

    private boolean panning;
    private boolean selecting;
    private double selectStartX;
    private double selectStartY;
    private double selectCurrentX;
    private double selectCurrentY;
    private List<RtpSample> rectSelection = List.of();

    /**
     * The last batch deleted from the map, kept for one undo.
     *
     * <p>Static because the screen is thrown away and rebuilt constantly - every
     * toolbar press rebuilds it, and a teleport replaces the instance outright -
     * and an undo that only survives until the next button press is no safety
     * net at all.
     */
    private static List<RtpSample> lastDeleted = List.of();

    private TextFieldWidget searchField;
    private RtpButton autoButton;
    private RtpButton scopeButton;

    /**
     * The biome filter's list, and where it hangs from.
     *
     * <p>The other filters on the bar cycle, which suits three or four choices.
     * Eighteen biome families do not cycle: picking the wrong one leaves
     * seventeen presses between you and <em>all biomes</em> again.
     */
    private final DropdownMenu biomeMenu = new DropdownMenu();
    private int biomeButtonX;
    private int biomeButtonBottom;
    private boolean biomeAnchorFound;
    private String statusLine = "";
    private long statusLineAt;

    private Scope scope;
    private final SessionListPanel sessionList =
            new SessionListPanel(filter, this::onSessionPickChanged, this::onSessionsChanged,
                    this::askConfirm, this::askText, this::setStatus);
    private final FrameLimiter frameLimiter = new FrameLimiter();

    /**
     * The stored pan and zoom are read once per scope, not on every rebuild.
     * Rebuilding is how a toolbar button picks up its new caption, and doing it
     * used to snap the map back to wherever it was last saved.
     */
    private boolean viewRestored;

    /**
     * Whether a session narrowing is in force, kept apart from
     * {@link SampleFilter#sessionFilterActive} because that one is only allowed
     * to bite in the aggregate scope. Filtering "this session" by a set that
     * does not contain it empties the map for no reason the player can see, so
     * the narrowing is parked while the scope shows a single sitting.
     */
    private boolean sessionNarrowing;

    /** Any change made in the session list: the panel owns the picking. */
    private void onSessionPickChanged() {
        sessionNarrowing = filter.sessionFilterActive;
        persistSessionPick();
        refresh();
    }

    /**
     * After the session list changed what a sitting is - resuming one, or
     * folding several into one. The picked set moved with it, so it is written
     * back here too.
     */
    private void onSessionsChanged() {
        sessionNarrowing = filter.sessionFilterActive;
        persistSessionPick();
        sessionList.reload();
        listScroll = 0;
        selectedSample = -1;
        rebuild();
    }

    /** The panel's way of asking before something irreversible. */
    private void askConfirm(String heading, String detail, String confirmLabel, Runnable action) {
        client.setScreen(new ConfirmDialog(this, heading, detail, confirmLabel, action));
    }

    /** The panel's way of asking for a line of text. */
    private void askText(String heading, String hint, String initial,
                         java.util.function.Consumer<String> onAccept) {
        client.setScreen(new TextPromptDialog(this, heading, hint, initial, onAccept));
    }

    /**
     * Only the aggregate scope narrows by session. Called wherever the scope or
     * the picking changes, so the two can never disagree.
     */
    private void syncSessionScope() {
        filter.sessionFilterActive = sessionNarrowing && scope == Scope.ALL;
    }

    /** Which landings this screen is plotting. Read by {@link ScreenKeeper}. */
    Scope scope() {
        return scope;
    }

    public MapScreen() {
        this(Scope.parse(RTPBuddyClient.config().map.scope));
    }

    public MapScreen(Scope scope) {
        super(Text.literal(Lang.t("screen.map")));
        this.scope = scope;
    }

    // ------------------------------------------------------------- lifecycle

    /** All samples the current scope may show, before filtering. */
    private List<RtpSample> sourceSamples() {
        if (scope == Scope.ALL) {
            return RTPBuddyClient.store().samplesView();
        }
        String sessionId = RTPBuddyClient.sessions().currentId();
        return sessionId == null ? List.of() : RTPBuddyClient.store().samplesOf(sessionId);
    }

    /** Identity of the current scope, mixed into the stats cache key. */
    private String scopeKey() {
        if (scope == Scope.ALL) {
            return "all:" + RTPBuddyClient.store().size();
        }
        String sessionId = RTPBuddyClient.sessions().currentId();
        return "session:" + (sessionId == null ? "none" : sessionId);
    }

    /** Text shown in the stats panel describing what is being summarised. */
    private String scopeDescription() {
        if (scope == Scope.ALL) {
            return sessionList.describe();
        }
        SessionRecord session = RTPBuddyClient.sessions().current();
        return session == null ? Lang.t("session.none") : session.displayName();
    }

    /**
     * Height reserved above the sample list for the scope card or the session
     * picker.
     *
     * <p>In session scope the card holds four fixed rows, so it takes what it
     * needs. In all-sessions scope the picker is a list of its own and the split
     * is the player's to make, which is what the divider under it is for.
     */
    private int sidebarExtraHeight() {
        if (rightPanel == 0) {
            return 0;
        }
        if (scope != Scope.ALL) {
            return MIN_SIDEBAR_TOP + SESSION_CARD_BUTTON;
        }
        int panel = panelHeight();
        int ceiling = panel - MIN_SAMPLE_LIST;
        if (ceiling <= MIN_SIDEBAR_TOP) {
            return Math.max(0, ceiling);
        }
        int desired = (int) Math.round(panel * clampSidebarFraction(mapConfig().sidebarFraction));
        return clamp(desired, MIN_SIDEBAR_TOP, ceiling);
    }

    private static double clampSidebarFraction(double fraction) {
        return Math.max(MapConfig.MIN_SIDEBAR_FRACTION,
                Math.min(MapConfig.MAX_SIDEBAR_FRACTION, fraction));
    }

    /** True while the sidebar's horizontal divider is on screen and draggable. */
    private boolean sidebarSplitterShown() {
        return rightPanel > 0 && scope == Scope.ALL && sidebarDividerY >= 0;
    }

    private void renderSidebarExtra(DrawContext context, int x, int y, int panelWidth, int extraHeight,
                                    int mouseX, int mouseY) {
        if (scope == Scope.ALL) {
            // Keep the divider's own strip clear, or the picker's last row and the
            // sample list's heading are drawn over each other.
            sessionList.render(context, x, y, panelWidth, extraHeight - SIDEBAR_GUTTER, mouseX, mouseY);
        } else {
            renderSessionCard(context, x, y, panelWidth, mouseX, mouseY);
        }
    }

    /**
     * The picker keeps the row rectangles it last drew, so the click has to be
     * inside the sidebar before they are consulted - otherwise hiding the panel
     * would leave a strip of the map answering to rows that are no longer there.
     */
    private boolean clickSidebarExtra(double mouseX, double mouseY, int button) {
        int extraHeight = sidebarExtraHeight();
        if (extraHeight == 0 || mouseX < rightPanelX()
                || mouseY < TOP_BAR || mouseY >= TOP_BAR + extraHeight) {
            return false;
        }
        if (scope != Scope.ALL) {
            if (newSessionButton != null
                    && mouseX >= newSessionButton[0]
                    && mouseX < newSessionButton[0] + newSessionButton[2]
                    && mouseY >= newSessionButton[1]
                    && mouseY < newSessionButton[1] + newSessionButton[3]) {
                startNewSession();
                return true;
            }
            return false;
        }
        return sessionList.click(mouseX, mouseY, button);
    }

    /** Live counters for the sitting in progress, shown in place of the picker. */
    private void renderSessionCard(DrawContext context, int x, int y, int cardWidth,
                                   int mouseX, int mouseY) {
        var capture = RTPBuddyClient.capture();
        SessionRecord session = RTPBuddyClient.sessions().current();
        String dash = Lang.t("word.dash");

        int cursor = UiDraw.heading(context, Lang.t("session.heading"), x, y, cardWidth);
        cursor = UiDraw.row(context, Lang.t("session.server"),
                UiDraw.trim(session == null ? dash : session.displayName(), cardWidth / 2),
                x, cursor, cardWidth);
        cursor = UiDraw.row(context, Lang.t("session.duration"),
                session == null ? dash : Numbers.duration(session.durationMillis()), x, cursor, cardWidth);
        cursor = UiDraw.row(context, Lang.t("session.captured"),
                String.valueOf(capture.captured()), x, cursor, cardWidth);
        cursor = UiDraw.row(context, Lang.t("session.missed"),
                Lang.t("session.missed_value", capture.timedOut(), capture.rejected()),
                x, cursor, cardWidth);

        // Closing one sitting and opening the next without relogging: the sample
        // numbers start at 1 again, and the counters above reset with them.
        int buttonY = cursor + 2;
        boolean hovered = mouseX >= x && mouseX < x + cardWidth
                && mouseY >= buttonY && mouseY < buttonY + 13;
        Theme.roundRect(context, x, buttonY, cardWidth, 13, 3,
                hovered ? Theme.mix(MapPalette.PANEL_BORDER, MapPalette.HIGHLIGHT, 0.45)
                        : MapPalette.PANEL_BORDER);
        String label = UiDraw.trim(Lang.t("map.button.new_session"), cardWidth - 4);
        UiDraw.text(context, label, x + (cardWidth - UiDraw.font().getWidth(label)) / 2, buttonY + 3,
                hovered ? MapPalette.HIGHLIGHT : MapPalette.TEXT_DIM);
        newSessionButton = new int[]{x, buttonY, cardWidth, 13};
    }

    /**
     * Ends the sitting in progress and opens a fresh one.
     *
     * <p>Nothing is deleted: the landings recorded so far keep the session they
     * were taken in, and only what counts as "now" moves on. A sitting that has
     * not recorded anything is left alone - closing and reopening it would
     * produce exactly the same empty session under a new id.
     */
    private void startNewSession() {
        var sessions = RTPBuddyClient.sessions();
        String currentId = sessions.currentId();
        if (currentId == null) {
            setStatus(Lang.t("word.not_in_world"));
            return;
        }
        if (!RTPBuddyClient.store().hasSamplesFor(currentId)) {
            setStatus(Lang.t("map.status.new_session_empty"));
            return;
        }
        RTPBuddyClient.capture().resetSessionCounters();
        sessions.begin(dev.rtpbuddy.util.Worlds.serverAddress(client));
        sessionList.reload();
        listScroll = 0;
        selectedSample = -1;
        rectSelection = List.of();
        setStatus(Lang.t("map.status.new_session"));
        rebuild();
    }

    // ----------------------------------------------------------------- scope

    /**
     * Swaps the scope, keeping each one its own pan and zoom: the session view
     * is usually a close-up of a handful of landings and the aggregate view is
     * the whole world, and sharing one camera between them made every switch
     * start with a hunt for the points.
     */
    private void cycleScope() {
        persistView();
        scope = scope.next();
        syncSessionScope();
        mapConfig().scope = scope.name();
        RTPBuddyClient.configManager().save();
        sessionList.resetScroll();
        listScroll = 0;
        viewRestored = false;
        if (scope == Scope.ALL) {
            sessionList.reload();
        }
        setStatus(Lang.t("map.status.scope", scopeLabel()));
        rebuild();
    }

    /** Caption of the scope button. Reads as the chosen set, not as a verb. */
    private String scopeLabel() {
        if (scope == Scope.SESSION) {
            return Lang.t("map.scope.session");
        }
        return sessionList.narrowed()
                ? Lang.t("map.scope.picked", sessionList.selectedCount())
                : Lang.t("map.scope.all");
    }

    /**
     * The number this sample is shown under.
     *
     * <p>Inside one sitting the landings count from 1, because a session that
     * opens at "#297" reads as yesterday's run continuing. Across sittings the
     * global number is used instead: it is the only numbering that stays unique
     * once several sessions are on screen together, and it is what the CSV, the
     * viewer and the delete dialog mean by a sample number.
     *
     * <p>Which of the two applies follows what is actually on the plot, not the
     * scope: narrowing the aggregate view down to one sitting shows one sitting,
     * so it counts from 1 there too. Deciding this by scope alone was what made
     * a single picked session open at some number in the middle.
     */
    private int displayNumber(RtpSample sample) {
        return showingOneSession()
                ? RTPBuddyClient.store().indexInSession(sample)
                : sample.sample();
    }

    /**
     * True while everything the map may show belongs to a single sitting.
     *
     * <p>Only an explicit picking counts. Deriving it from the samples that
     * survive the filter would let an unrelated filter - a dimension, a search -
     * silently renumber the list as it is typed.
     */
    private boolean showingOneSession() {
        if (scope == Scope.SESSION) {
            return true;
        }
        return filter.sessionFilterActive && filter.visibleSessions.size() == 1;
    }

    private MapConfig mapConfig() {
        return RTPBuddyClient.config().map;
    }

    @Override
    protected void init() {
        canvas.setMarkerMode(MarkerMode.parse(mapConfig().markerMode));
        canvas.setColorMode(ColorMode.parse(mapConfig().colorMode));
        canvas.setRegionColorLookup(sample -> {
            RTPBuddyConfig config = RTPBuddyClient.config();
            return config.regionColor(config.regionKey(sample));
        });
        canvas.setSessionColorLookup(sample -> {
            var session = RTPBuddyClient.store().session(sample.sessionId());
            return MapPalette.session(session == null ? 0 : session.colorIndex);
        });

        canvas.setNumberLookup(this::displayNumber);
        filter.displayNumber = this::displayNumber;

        sessionList.reload();
        frameLimiter.apply(mapConfig().mapFpsLimit);

        computeLayout();
        if (!viewRestored) {
            restoreView();
            restoreFilter();
            viewRestored = true;
        }
        buildWidgets();
        refresh();
    }

    /**
     * Panel widths come from the stored fractions, are clamped so the map always
     * keeps a usable minimum, and are dropped entirely when even that does not
     * fit - which is exactly what happens at a large GUI scale, where the whole
     * screen is only a few hundred virtual pixels wide.
     */
    private void computeLayout() {
        MapConfig config = mapConfig();
        int desiredLeft = panelPixels(config.panelLeftFraction);
        int desiredRight = panelPixels(config.panelRightFraction);

        PanelMode effective = panelMode;
        if (effective == PanelMode.BOTH
                && width - desiredLeft - desiredRight - GAP * 3 - SPLITTER * 2 < MIN_CANVAS_BOTH_PANELS) {
            effective = PanelMode.RIGHT_ONLY;
        }
        if ((effective == PanelMode.RIGHT_ONLY
                && width - desiredRight - GAP * 2 - SPLITTER < MIN_CANVAS)
                || (effective == PanelMode.LEFT_ONLY
                && width - desiredLeft - GAP * 2 - SPLITTER < MIN_CANVAS)) {
            effective = PanelMode.NONE;
        }

        leftPanel = switch (effective) {
            case BOTH, LEFT_ONLY -> desiredLeft;
            default -> 0;
        };
        rightPanel = switch (effective) {
            case BOTH, RIGHT_ONLY -> desiredRight;
            default -> 0;
        };

        int canvasX = GAP + (leftPanel > 0 ? leftPanel + SPLITTER : 0);
        int canvasWidth = width - canvasX - (rightPanel > 0 ? rightPanel + SPLITTER : 0) - GAP;
        canvas.view().setBounds(canvasX, TOP_BAR, Math.max(MIN_CANVAS, canvasWidth),
                height - TOP_BAR - BOTTOM_BAR);
    }

    private int panelPixels(double fraction) {
        double clamped = Math.max(MapConfig.MIN_PANEL_FRACTION,
                Math.min(MapConfig.MAX_PANEL_FRACTION, fraction));
        return clamp((int) Math.round(width * clamped), MIN_PANEL, Math.max(MIN_PANEL, width / 2));
    }

    /** X of the right panel. Only meaningful while that panel is shown. */
    private int rightPanelX() {
        return width - rightPanel - GAP;
    }

    private int panelHeight() {
        return height - TOP_BAR - BOTTOM_BAR;
    }

    /** X of the left divider strip. Only meaningful while the left panel is shown. */
    private int leftSplitterX() {
        return GAP + leftPanel;
    }

    /** X of the right divider strip. Only meaningful while the right panel is shown. */
    private int rightSplitterX() {
        return rightPanelX() - SPLITTER;
    }

    private boolean overSplitter(double mouseX, double mouseY, Splitter which) {
        if (mouseY < TOP_BAR || mouseY >= height - BOTTOM_BAR) {
            return false;
        }
        // The sidebar divider is horizontal and has its own hit test.
        int x = switch (which) {
            case LEFT -> leftPanel > 0 ? leftSplitterX() : Integer.MIN_VALUE;
            case RIGHT -> rightPanel > 0 ? rightSplitterX() : Integer.MIN_VALUE;
            case NONE, SIDEBAR -> Integer.MIN_VALUE;
        };
        // One pixel of slack either side: a 6px strip is a small target at GUI
        // scale 1, and overshooting it by a pixel should still grab the divider.
        return x != Integer.MIN_VALUE && mouseX >= x - 1 && mouseX < x + SPLITTER + 1;
    }

    private void cyclePanels() {
        panelMode = switch (panelMode) {
            case BOTH -> PanelMode.LEFT_ONLY;
            case LEFT_ONLY -> PanelMode.RIGHT_ONLY;
            case RIGHT_ONLY -> PanelMode.NONE;
            case NONE -> PanelMode.BOTH;
        };
        rebuild();
    }

    private void buildWidgets() {
        // The scope button sits in the title row rather than the toolbar: it
        // decides what the whole screen is about, and the toolbar below it is a
        // row of things that adjust how that is drawn.
        String scopeCaption = scopeLabel();
        int scopeWidth = Math.min(width / 3, textRenderer.getWidth(scopeCaption) + 18);
        int scopeX = width - GAP - scopeWidth;
        scopeButton = new RtpButton(scopeX, 4, scopeWidth, 16, RtpButton.Style.PRIMARY,
                scopeCaption, this::cycleScope);
        scopeButton.setTooltip(Tooltip.of(Text.literal(Lang.t("map.tip.scope"))));
        addDrawableChild(scopeButton);

        int searchWidth = Math.min(150, Math.max(70, (width - scopeWidth) / 4));
        int searchX = scopeX - GAP - searchWidth;
        searchField = new TextFieldWidget(textRenderer, searchX, 4, searchWidth, 16,
                Text.literal(Lang.t("map.search_label")));
        searchField.setPlaceholder(Text.literal(Lang.t("map.search")));
        searchField.setText(filter.search == null ? "" : filter.search);
        searchField.setChangedListener(value -> {
            filter.search = value;
            refresh();
        });
        addDrawableChild(searchField);

        buildToolbar();
    }

    /** One toolbar entry: a terse caption, the tooltip that explains it, and state. */
    private record BarButton(String label, String tooltip, boolean active, Runnable action) {
        BarButton(String label, String tooltip, Runnable action) {
            this(label, tooltip, false, action);
        }
    }

    /**
     * Captions are kept short and every button is sized to its own text, because
     * at a large GUI scale the whole bar has only a few hundred pixels to work
     * with and fixed widths clip the labels.
     */
    private void buildToolbar() {
        List<BarButton> entries = new ArrayList<>(List.of(
                new BarButton(canvas.markerMode().label(), Lang.t("map.tip.marker"), true,
                        () -> {
                            canvas.setMarkerMode(canvas.markerMode().next());
                            mapConfig().markerMode = canvas.markerMode().name();
                            RTPBuddyClient.configManager().save();
                            rebuild();
                        }),
                new BarButton(canvas.colorMode().label(), Lang.t("map.tip.color"), true,
                        () -> {
                            canvas.setColorMode(canvas.colorMode().next());
                            mapConfig().colorMode = canvas.colorMode().name();
                            RTPBuddyClient.configManager().save();
                            rebuild();
                        }),
                new BarButton(dimensionCycleLabel(), Lang.t("map.tip.dimension"),
                        filter.dimension != null,
                        () -> {
                            cycleDimension();
                            rebuild();
                        }),
                new BarButton(biomeCycleLabel(), Lang.t("map.tip.biome"),
                        filter.biomeFamily != null, this::openBiomeMenu),
                new BarButton(Lang.t("map.button.fit"), Lang.t("map.tip.fit"),
                        () -> fit(shiftHeld())),
                new BarButton(Lang.t("map.button.reset"), Lang.t("map.tip.reset"),
                        () -> canvas.view().reset()),
                new BarButton(Lang.t("map.button.export"), Lang.t("map.tip.export"),
                        this::exportVisible),
                new BarButton(Lang.t("map.button.clear"), Lang.t("map.tip.clear"),
                        () -> {
                            filter.clear();
                            // clear() drops the picking too, so the held copy
                            // has to go with it or the next scope switch would
                            // bring a narrowing back that the player just cleared.
                            sessionNarrowing = false;
                            persistSessionPick();
                            searchField.setText("");
                            rebuild();
                        }),
                new BarButton(deleteLabel(), Lang.t("map.tip.delete"),
                        !rectSelection.isEmpty(),
                        this::requestDelete),
                new BarButton(Lang.t("map.button.undo"), Lang.t("map.tip.undo"),
                        !lastDeleted.isEmpty(),
                        this::undoDelete),
                new BarButton(panelLabel(), Lang.t("map.tip.panels"),
                        panelMode != PanelMode.NONE,
                        this::cyclePanels),
                new BarButton(Lang.t("map.button.settings"), Lang.t("map.tip.settings"),
                        () -> client.setScreen(new SettingsScreen(this))),
                new BarButton(autoLabel(), Lang.t("map.tip.auto"),
                        RTPBuddyClient.autoRtp().running(),
                        () -> {
                            var auto = RTPBuddyClient.autoRtp();
                            if (auto.running()) {
                                auto.stop("reason.map_screen");
                                setStatus(Lang.t("map.status.auto_stopped"));
                                rebuild();
                            } else {
                                client.setScreen(new AutoRtpDialog(this, started -> {
                                }));
                            }
                        })
        ));

        int[] widths = new int[entries.size()];
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            // Size the countdown button for its widest state so it does not
            // twitch as the number shrinks.
            String label = i == entries.size() - 1
                    ? Lang.t("map.button.auto_width")
                    : entries.get(i).label();
            widths[i] = textRenderer.getWidth(label) + 10;
            total += widths[i];
        }
        total += GAP * (entries.size() - 1);

        // If even the short captions do not fit, scale every button down evenly
        // rather than letting the last ones fall off the screen.
        int available = width - GAP * 2;
        double scale = total > available ? (double) available / total : 1.0;

        int x = GAP;
        int y = TITLE_ROW + 2;
        autoButton = null;
        biomeAnchorFound = false;
        biomeButtonX = GAP;
        biomeButtonBottom = y + 18;
        for (int i = 0; i < entries.size(); i++) {
            BarButton entry = entries.get(i);
            int buttonWidth = Math.max(12, (int) (widths[i] * scale));
            RtpButton button = new RtpButton(x, y, buttonWidth, 18, RtpButton.Style.CHIP,
                    entry.label(), entry.action());
            button.withSelected(entry.active());
            button.setTooltip(Tooltip.of(Text.literal(entry.tooltip())));
            addDrawableChild(button);
            if (i == entries.size() - 1) {
                autoButton = button;
            }
            // The menu hangs off this button, and only the layout knows where it
            // ended up: every caption is measured, so the x is not fixed.
            if (!biomeAnchorFound && entry.label().equals(biomeCycleLabel())) {
                biomeAnchorFound = true;
                biomeButtonX = x;
                biomeButtonBottom = y + 18;
            }
            x += buttonWidth + (int) (GAP * scale);
        }
    }

    /**
     * Frames the samples, or - with shift held - the whole configured world
     * border, which is the only way to see how much of the map the landings have
     * actually covered.
     */
    private void fit(boolean toBorder) {
        if (!toBorder) {
            canvas.view().fit(visible);
            return;
        }
        GuardSettings guards = RTPBuddyClient.config().guards;
        // The widest border on show, so the fit frames every square it drew
        // rather than only the overworld's.
        double radius = guards.borderRadius;
        for (String dimension : borderDimensions()) {
            radius = Math.max(radius, guards.radiusFor(dimension));
        }
        canvas.view().fitSquare(guards.borderCenterX, guards.borderCenterZ, radius);
        setStatus(Lang.t("map.status.fit_border", Numbers.compact(radius * 2)));
    }

    private boolean shiftHeld() {
        return client != null
                && (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT));
    }

    /** Doubles as the countdown readout while the loop is running. */
    private String autoLabel() {
        var auto = RTPBuddyClient.autoRtp();
        if (!auto.running()) {
            return Lang.t("map.button.auto_off");
        }
        // A held loop must not read like a running one: the countdown is frozen,
        // so showing a number that never moves would look like a stuck client.
        if (auto.paused()) {
            return Lang.t("map.button.auto_held");
        }
        if (auto.manualStep()) {
            return Lang.t("map.button.auto_manual");
        }
        return Lang.t("map.button.auto_on", Math.round(auto.secondsUntilNext()));
    }

    private String panelLabel() {
        return Lang.t(switch (panelMode) {
            case BOTH -> "map.button.panels_both";
            case LEFT_ONLY -> "map.button.panels_left";
            case RIGHT_ONLY -> "map.button.panels_right";
            case NONE -> "map.button.panels_none";
        });
    }

    /** Rebuilds widgets so button captions pick up new state. */
    private void rebuild() {
        clearAndInit();
    }

    @Override
    public void close() {
        // An escape is the player saying they are done, so no reopen is queued.
        ScreenKeeper.onMapClosed();
        persistView();
        frameLimiter.release();
        super.close();
    }

    @Override
    public void removed() {
        // Also covers being replaced rather than closed - the settings screen
        // and the auto-RTP dialog both take over from here.
        frameLimiter.release();
        // Landing somewhere new tears this screen down without ever calling
        // close(), so the pan and zoom have to be written here too or every
        // teleport would throw the view away.
        persistView();
        super.removed();
    }

    private void restoreView() {
        MapConfig config = mapConfig();
        if (!config.rememberView) {
            canvas.view().fit(sourceSamples());
            return;
        }
        double[] saved = savedView();
        if (saved[2] <= 0) {
            canvas.view().fit(sourceSamples());
        } else {
            canvas.view().restore(saved[0], saved[1], saved[2]);
        }
    }

    /**
     * Writes back what the player set up, so the next open is the same map.
     *
     * <p>The filter is stored alongside the view because a reopen that quietly
     * widened "Overworld only" back to everything reads as the map having
     * forgotten the question, not as a fresh start.
     */
    private void persistView() {
        MapConfig config = mapConfig();
        boolean dirty = false;
        if (config.rememberView) {
            storeView(canvas.view().centerX(), canvas.view().centerZ(), canvas.view().zoom());
            dirty = true;
        }
        if (config.rememberFilter) {
            config.filterDimension = filter.dimension;
            config.filterRegion = filter.region;
            config.filterCaptureMode = filter.captureMode;
            config.filterBiomeFamily = filter.biomeFamily;
            storeSessionPick(config);
            dirty = true;
        }
        if (dirty) {
            RTPBuddyClient.configManager().save();
        }
    }

    /**
     * Writes the picked sittings straight away rather than waiting for the map
     * to close. Landing somewhere new can tear this screen down through paths
     * that never reach {@code close()}, and a picking that survives only a tidy
     * exit is one the player cannot rely on.
     */
    private void persistSessionPick() {
        MapConfig config = mapConfig();
        if (!config.rememberFilter) {
            return;
        }
        storeSessionPick(config);
        RTPBuddyClient.configManager().save();
    }

    /**
     * A stored family name that no longer names a family is dropped rather than
     * restored: it would narrow the map to nothing with no visible cause and no
     * button press that could widen it again.
     */
    private static String validFamily(String name) {
        if (name == null) {
            return null;
        }
        try {
            return dev.rtpbuddy.util.Biomes.Family.valueOf(name).name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void storeSessionPick(MapConfig config) {
        config.filterSessionsActive = sessionNarrowing;
        config.filterSessions = new java.util.ArrayList<>(filter.visibleSessions);
    }

    /** The stored dimension, region and mode filter, if that is switched on. */
    private void restoreFilter() {
        MapConfig config = mapConfig();
        if (!config.rememberFilter) {
            return;
        }
        filter.dimension = blankToNull(config.filterDimension);
        filter.region = blankToNull(config.filterRegion);
        filter.captureMode = blankToNull(config.filterCaptureMode);
        filter.biomeFamily = validFamily(blankToNull(config.filterBiomeFamily));
        restoreSessionPick(config);
    }

    /**
     * Puts the picked sittings back. Ids naming a sitting the store no longer
     * holds are dropped, and a picking left with nothing in it is abandoned
     * rather than restored - deleting the last picked sitting must not reopen
     * the map onto an empty plot with no visible cause.
     */
    private void restoreSessionPick(MapConfig config) {
        filter.visibleSessions.clear();
        sessionNarrowing = false;
        if (!config.filterSessionsActive || config.filterSessions == null) {
            syncSessionScope();
            return;
        }
        for (String id : config.filterSessions) {
            if (id != null && RTPBuddyClient.store().session(id) != null) {
                filter.visibleSessions.add(id);
            }
        }
        sessionNarrowing = !filter.visibleSessions.isEmpty();
        syncSessionScope();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** {centerX, centerZ, zoom} previously stored for the current scope. */
    private double[] savedView() {
        MapConfig map = mapConfig();
        return scope == Scope.ALL
                ? new double[]{map.allCenterX, map.allCenterZ, map.allZoom}
                : new double[]{map.sessionCenterX, map.sessionCenterZ, map.sessionZoom};
    }

    private void storeView(double centerX, double centerZ, double zoom) {
        MapConfig map = mapConfig();
        if (scope == Scope.ALL) {
            map.allCenterX = centerX;
            map.allCenterZ = centerZ;
            map.allZoom = zoom;
        } else {
            map.sessionCenterX = centerX;
            map.sessionCenterZ = centerZ;
            map.sessionZoom = zoom;
        }
    }

    // ---------------------------------------------------------------- filter

    private void refresh() {
        visible = filter.apply(sourceSamples());
        RTPBuddyConfig config = RTPBuddyClient.config();
        String key = scopeKey() + "#" + filter.cacheKey() + "#" + visible.size()
                + "#" + config.map.densityCellSize;
        stats = statsEngine.get(key, () -> visible, config.guards, config.map.densityCellSize);
        // A filter change can swap the sample set for another of the same size,
        // which the canvas has no way of noticing on its own.
        canvas.invalidateGaps();
        canvas.setLegend(buildLegend());
        canvas.setShowServerRegions(mapConfig().showServerRegions && usesServerRegions());
        canvas.setBorderDimensions(borderDimensions());
        listScroll = Math.min(listScroll, Math.max(0, visible.size() * UiDraw.lineHeight()));
    }

    /**
     * Which dimensions' borders belong on the plot: the filtered one if the
     * filter names one, otherwise every dimension the visible samples are in.
     */
    private List<String> borderDimensions() {
        if (filter.dimension != null) {
            return List.of(filter.dimension);
        }
        java.util.LinkedHashSet<String> dimensions = new java.util.LinkedHashSet<>();
        for (RtpSample sample : visible) {
            dimensions.add(sample.dimension());
        }
        return dimensions.isEmpty() ? List.of(Worlds.OVERWORLD) : List.copyOf(dimensions);
    }

    /**
     * True when the samples on screen come from a host that splits its world
     * into server regions. The grid is drawn only then: painting DonutSMP's
     * layout over a single-player world would be inventing a fact.
     */
    private boolean usesServerRegions() {
        RTPBuddyConfig config = RTPBuddyClient.config();
        for (RtpSample sample : visible) {
            if (config.serverRegion(sample) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The colour key for whatever the markers currently encode, built from the
     * samples on screen so it never lists a colour that is not there.
     */
    private List<MapCanvas.LegendEntry> buildLegend() {
        // Heatmap and cluster colour by density, not by category, so the colour
        // mode's key would be describing colours that are not on screen.
        // The gap map draws its own key, because its colour is a measurement
        // rather than a set of categories to list.
        if (!mapConfig().showLegend || visible.isEmpty()
                || canvas.markerMode() == MarkerMode.GAPS) {
            return List.of();
        }
        RTPBuddyConfig config = RTPBuddyClient.config();
        return switch (canvas.colorMode()) {
            case DIMENSION -> legendOf(RtpSample::dimension,
                    Worlds::dimensionLabel, Worlds::dimensionColor);
            case REGION -> legendOf(config::regionKey,
                    config::regionLabel, config::regionColor);
            case SESSION -> legendOf(RtpSample::sessionId,
                    id -> {
                        var session = RTPBuddyClient.store().session(id);
                        // Every local sitting is called "Singleplayer", so the
                        // name on its own names nothing.
                        return session == null ? id : session.displayName() + "  "
                                + LEGEND_DAY.format(Instant.ofEpochMilli(session.startedAt));
                    },
                    id -> {
                        var session = RTPBuddyClient.store().session(id);
                        return MapPalette.session(session == null ? 0 : session.colorIndex);
                    });
            case BIOME -> legendOf(sample -> dev.rtpbuddy.util.Biomes.family(sample.biome()).name(),
                    name -> dev.rtpbuddy.util.Biomes.Family.valueOf(name).label(),
                    name -> dev.rtpbuddy.util.Biomes.Family.valueOf(name).color());
            // Recency is a gradient, not a set of categories: two ends say it.
            case RECENCY -> List.of(
                    new MapCanvas.LegendEntry(MapPalette.lighten(0xFF3F6FA8, 0.0),
                            Lang.t("map.legend.oldest")),
                    new MapCanvas.LegendEntry(MapPalette.lighten(0xFF3F6FA8, 0.7),
                            Lang.t("map.legend.newest")));
        };
    }

    /** At most {@value #LEGEND_ROWS} rows, in the order the samples introduce them. */
    private List<MapCanvas.LegendEntry> legendOf(java.util.function.Function<RtpSample, String> keyOf,
                                                 java.util.function.Function<String, String> labelOf,
                                                 java.util.function.ToIntFunction<String> colorOf) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (RtpSample sample : visible) {
            keys.add(keyOf.apply(sample));
            if (keys.size() > LEGEND_ROWS) {
                break;
            }
        }
        List<MapCanvas.LegendEntry> entries = new ArrayList<>();
        for (String key : keys) {
            if (entries.size() == LEGEND_ROWS) {
                entries.add(new MapCanvas.LegendEntry(Theme.TEXT_FAINT, Lang.t("map.legend.more")));
                break;
            }
            entries.add(new MapCanvas.LegendEntry(colorOf.applyAsInt(key), labelOf.apply(key)));
        }
        return entries;
    }

    /**
     * Steps the dimension filter through the dimensions the scope actually
     * holds.
     *
     * <p>Built from the scope's whole sample set, not from the filtered one.
     * Reading it off the filtered set meant that once a dimension was picked it
     * was the only one left in the list, so the cycle ran all -> overworld ->
     * all and the nether could never be reached however much nether data was
     * recorded.
     */
    private void cycleDimension() {
        List<String> options = new ArrayList<>();
        options.add(null);
        java.util.LinkedHashSet<String> present = new java.util.LinkedHashSet<>();
        for (RtpSample sample : sourceSamples()) {
            present.add(sample.dimension());
        }
        options.addAll(present);
        if (options.size() == 1) {
            options.add(Worlds.OVERWORLD);
        }
        int index = options.indexOf(filter.dimension);
        filter.dimension = options.get((index + 1) % options.size());
        refresh();
    }

    private String dimensionCycleLabel() {
        return filter.dimension == null
                ? Lang.t("map.dimension_all")
                : Worlds.dimensionLabel(filter.dimension);
    }

    /**
     * Steps the biome filter through the families the scope actually holds.
     *
     * <p>Read off the scope's whole sample set for the same reason the
     * dimension cycle is: taking the list from the filtered set would leave
     * exactly one family in it the moment one was chosen, and the cycle would
     * never reach any other.
     *
     * <p>Families, not biomes. Close to fifty biomes are recorded, and a button
     * that needs forty presses to reach the one you want is not a filter. The
     * single biome is what the search box is for.
     */
    /**
     * Builds the biome list from the families the scope actually holds.
     *
     * <p>Read off the scope's whole sample set rather than the filtered one: the
     * filtered set holds exactly one family the moment one is chosen, and a menu
     * built from that could never offer another.
     *
     * <p>Families, not biomes. Close to fifty biomes are recorded; the single
     * biome is what the search box is for, and it matches both the id and the
     * name the game shows.
     */
    private void openBiomeMenu() {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        List<RtpSample> source = sourceSamples();
        for (RtpSample sample : source) {
            counts.merge(dev.rtpbuddy.util.Biomes.family(sample.biome()).name(), 1, Integer::sum);
        }
        List<String> ordered = new ArrayList<>(counts.keySet());
        // Enum order, so the list reads the same whatever order the landings
        // happened to arrive in.
        ordered.sort(java.util.Comparator.comparingInt(
                name -> dev.rtpbuddy.util.Biomes.Family.valueOf(name).ordinal()));

        List<DropdownMenu.Item> items = new ArrayList<>();
        items.add(new DropdownMenu.Item(null, Lang.t("map.biome_all"),
                Theme.TEXT_FAINT, source.size()));
        for (String name : ordered) {
            dev.rtpbuddy.util.Biomes.Family family = dev.rtpbuddy.util.Biomes.Family.valueOf(name);
            items.add(new DropdownMenu.Item(name, family.label(), family.color(),
                    counts.get(name)));
        }
        biomeMenu.open(biomeButtonX, biomeButtonBottom, height - BOTTOM_BAR, items,
                filter.biomeFamily);
    }

    private String biomeCycleLabel() {
        return filter.biomeFamily == null
                ? Lang.t("map.biome_all")
                : dev.rtpbuddy.util.Biomes.Family.valueOf(filter.biomeFamily).label();
    }

    private void exportVisible() {
        Path target = RTPBuddyClient.store().export(visible);
        setStatus(target == null
                ? Lang.t("map.status.export_failed")
                : Lang.t("map.status.exported", visible.size(), target.getFileName()));
    }

    private void setStatus(String message) {
        statusLine = message;
        statusLineAt = System.currentTimeMillis();
    }

    // -------------------------------------------------------------- rendering

    /**
     * The map covers the whole screen opaquely, so the vanilla blur and darkening
     * would be computed and then immediately painted over. Skipping them saves a
     * full-screen blur every frame.
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF0E1014);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Pick up samples recorded while the screen is open.
        if (RTPBuddyClient.store().size() != lastStoreSize) {
            lastStoreSize = RTPBuddyClient.store().size();
            refresh();
        }
        // The auto-RTP caption counts down. Retitling the one button is enough;
        // rebuilding the bar every second would steal focus from the search box.
        if (autoButton != null) {
            autoButton.withLabel(autoLabel()).withSelected(RTPBuddyClient.autoRtp().running());
        }
        // The caption counts the picked sessions, so ticking a row in the
        // sidebar has to show up here without a rebuild.
        if (scopeButton != null) {
            scopeButton.withLabel(scopeLabel());
        }

        hoveredIndex = canvas.hitTest(visible, mouseX, mouseY);
        canvas.render(context, visible, mapConfig(), RTPBuddyClient.config().guards,
                selectedSample, hoveredIndex);

        if (selecting) {
            drawSelectionRect(context);
        }
        canvas.drawCursorReadout(context, mouseX, mouseY);

        if (leftPanel > 0) {
            statsPanel.render(context, GAP, TOP_BAR, leftPanel, panelHeight(),
                    stats, statsScroll, scopeDescription(), canvas.gapHoles());
        }
        if (rightPanel > 0) {
            renderRightPanel(context, mouseX, mouseY);
        }
        renderSplitters(context, mouseX, mouseY);
        renderTopBar(context);
        renderBottomBar(context);

        super.render(context, mouseX, mouseY, delta);

        if (hoveredIndex >= 0) {
            RtpSample hovered = visible.get(hoveredIndex);
            RtpSample previous = hoveredIndex > 0 ? visible.get(hoveredIndex - 1) : null;
            SampleCard.render(context, hovered, previous,
                    displayNumber(hovered), previous == null ? 0 : displayNumber(previous),
                    mouseX, mouseY, width, height);
        } else if (dragging == Splitter.NONE
                && (overSplitter(mouseX, mouseY, Splitter.LEFT)
                || overSplitter(mouseX, mouseY, Splitter.RIGHT))) {
            context.drawTooltip(textRenderer, List.of(Text.literal(Lang.t("map.tip.splitter"))),
                    mouseX, mouseY);
        } else if (dragging == Splitter.NONE && overSidebarSplitter(mouseX, mouseY)) {
            context.drawTooltip(textRenderer, List.of(Text.literal(Lang.t("map.tip.sidebar"))),
                    mouseX, mouseY);
        }

        renderBiomeMenu(context, mouseX, mouseY);
    }

    /**
     * The open filter list, over everything else the screen has drawn.
     *
     * <p>Last, because it is a popup: the toolbar button it hangs from, the
     * canvas and the panels all sit underneath it.
     */
    private void renderBiomeMenu(DrawContext context, int mouseX, int mouseY) {
        biomeMenu.render(context, mouseX, mouseY);
    }

    /**
     * The dividers are drawn last of the panel furniture so the grip is visible
     * on top of both neighbours, and they light up on hover: without a cursor
     * change there is nothing else to tell the player they can be dragged.
     */
    private void renderSplitters(DrawContext context, int mouseX, int mouseY) {
        if (leftPanel > 0) {
            drawSplitter(context, leftSplitterX(),
                    dragging == Splitter.LEFT || overSplitter(mouseX, mouseY, Splitter.LEFT));
        }
        if (rightPanel > 0) {
            drawSplitter(context, rightSplitterX(),
                    dragging == Splitter.RIGHT || overSplitter(mouseX, mouseY, Splitter.RIGHT));
        }
    }

    /**
     * The grab handle between the session picker and the sample list. Drawn as a
     * short bar rather than a full-width rule so it reads as something to take
     * hold of instead of as a section break.
     */
    private void drawSidebarSplitter(DrawContext context, int x, int barWidth, int mouseX, int mouseY) {
        boolean active = dragging == Splitter.SIDEBAR || overSidebarSplitter(mouseX, mouseY);
        int y = sidebarDividerY;
        context.fill(x, y + 1, x + barWidth, y + 2, active ? Theme.LINE_BRIGHT : Theme.LINE_SOFT);

        int gripWidth = 22;
        int gripX = x + (barWidth - gripWidth) / 2;
        Theme.roundRect(context, gripX, y, gripWidth, 4, 2,
                active ? Theme.ACCENT : Theme.LINE);
    }

    private boolean overSidebarSplitter(double mouseX, double mouseY) {
        if (!sidebarSplitterShown()) {
            return false;
        }
        return mouseX >= rightPanelX() && mouseX < rightPanelX() + rightPanel
                && mouseY >= sidebarDividerY - 3 && mouseY < sidebarDividerY + 6;
    }

    private void drawSplitter(DrawContext context, int x, boolean active) {
        int top = TOP_BAR;
        int bottom = height - BOTTOM_BAR;
        context.fill(x, top, x + SPLITTER, bottom,
                active ? MapPalette.SPLITTER_ACTIVE : MapPalette.SPLITTER);

        // Three dots in the middle, the usual shorthand for "this one moves".
        int gripColor = active ? Theme.ACCENT : Theme.TEXT_FAINT;
        int centerY = (top + bottom) / 2;
        for (int i = -1; i <= 1; i++) {
            Theme.roundRect(context, x + 2, centerY + i * 5 - 1, SPLITTER - 4, 2, 1, gripColor);
        }
    }

    /**
     * Two rows: the screen's name and search on top, the toolbar underneath. A
     * hairline between them keeps the buttons from reading as part of the title.
     */
    private void renderTopBar(DrawContext context) {
        context.fill(0, 0, width, TOP_BAR, Theme.SURFACE_HEADER);
        context.fill(0, TITLE_ROW - 1, width, TITLE_ROW, Theme.LINE_SOFT);
        context.fill(0, TOP_BAR - 1, width, TOP_BAR, Theme.LINE);

        Theme.roundRect(context, GAP, 7, 3, 10, 1, Theme.ACCENT);
        UiDraw.text(context, title.getString(), GAP + 9, 8, Theme.TEXT);

        int chipX = GAP + 9 + textRenderer.getWidth(title.getString()) + 8;
        String counts = Lang.t("map.counts", visible.size(), sourceSamples().size());
        if (searchField != null
                && chipX + textRenderer.getWidth(counts) + 12 < searchField.getX() - 6) {
            Theme.chip(context, counts, chipX, 6, Theme.CONTROL, Theme.TEXT_DIM);
        }
    }

    /**
     * One line of text along the bottom: whatever just happened, or else what the
     * current filter is showing. The keyboard hint fills the right-hand end when
     * there is room for it.
     */
    private void renderBottomBar(DrawContext context) {
        int y = height - BOTTOM_BAR;
        context.fill(0, y, width, height, Theme.SURFACE_HEADER);
        context.fill(0, y, width, y + 1, Theme.LINE);

        boolean fresh = System.currentTimeMillis() - statusLineAt < 8_000 && !statusLine.isEmpty();
        String message = fresh
                ? statusLine
                : Lang.t("map.filter", filter.describe())
                + (rectSelection.isEmpty() ? "" : Lang.t("map.selection", rectSelection.size()));

        String hint = Lang.t("map.hint.keys");
        int hintWidth = textRenderer.getWidth(hint);
        int room = width - GAP * 2 - 4;
        if (hintWidth + textRenderer.getWidth(message) + 24 < width) {
            UiDraw.textRight(context, hint, width - GAP - 2, y + 6, Theme.TEXT_FAINT);
            room -= hintWidth + 16;
        }
        UiDraw.text(context, UiDraw.trim(message, room), GAP + 2, y + 6,
                fresh ? Theme.ACCENT : Theme.TEXT_DIM);
    }

    private void renderRightPanel(DrawContext context, int mouseX, int mouseY) {
        int x = rightPanelX();
        int y = TOP_BAR;
        int panelHeight = panelHeight();
        UiDraw.panel(context, x, y, rightPanel, panelHeight);

        int extraHeight = sidebarExtraHeight();
        if (extraHeight > 0) {
            renderSidebarExtra(context, x + 6, y + 6, rightPanel - 12, extraHeight, mouseX, mouseY);
        }

        int detailHeight = selectedSample >= 0 ? 92 : 0;
        int listY = y + extraHeight + 6;
        int listHeight = panelHeight - extraHeight - detailHeight - 12;

        // The divider sits in the gutter the picker leaves free above the list.
        sidebarDividerY = scope == Scope.ALL && listHeight > 24 ? listY - SIDEBAR_GUTTER + 2 : -1;
        if (sidebarSplitterShown()) {
            drawSidebarSplitter(context, x + 6, rightPanel - 12, mouseX, mouseY);
        }

        if (listHeight > 24) {
            renderSampleList(context, x + 6, listY, rightPanel - 12, listHeight, mouseX, mouseY);
        } else {
            listViewHeight = 0;
        }
        if (detailHeight > 0) {
            renderDetail(context, x + 6, y + panelHeight - detailHeight - 4, rightPanel - 12, detailHeight);
        }
    }

    private void renderSampleList(DrawContext context, int x, int y, int listWidth, int listHeight,
                                  int mouseX, int mouseY) {
        UiDraw.heading(context, Lang.t("map.samples"), x, y, listWidth);
        int top = y + UiDraw.lineHeight() + 2;
        int rowHeight = UiDraw.lineHeight();
        int bottom = y + listHeight;

        // Remember the viewport for the scroll ceiling and the click hit-test.
        listViewTop = top;
        listViewHeight = Math.max(0, bottom - top);
        listContentHeight = visible.size() * rowHeight;
        listScroll = clamp(listScroll, 0, maxListScroll());

        context.enableScissor(x, top, x + listWidth, bottom);

        int firstRow = Math.max(0, listScroll / rowHeight);
        int rows = listHeight / rowHeight + 2;

        for (int i = firstRow; i < Math.min(visible.size(), firstRow + rows); i++) {
            RtpSample sample = visible.get(i);
            int rowY = top + i * rowHeight - listScroll;
            boolean selected = sample.sample() == selectedSample;
            boolean hovered = mouseX >= x && mouseX < x + listWidth - SCROLLBAR_ROOM
                    && mouseY >= rowY && mouseY < rowY + rowHeight;

            if (selected) {
                context.fill(x, rowY, x + listWidth, rowY + rowHeight, 0x3374C0F0);
            } else if (hovered) {
                context.fill(x, rowY, x + listWidth, rowY + rowHeight, 0x22FFFFFF);
            }

            context.fill(x + 1, rowY + 2, x + 4, rowY + rowHeight - 2, colorFor(sample));
            String number = "#" + displayNumber(sample);
            UiDraw.text(context, number, x + 7, rowY + 1,
                    selected ? MapPalette.HIGHLIGHT : MapPalette.TEXT);

            String coordinates = Math.round(sample.x()) + " / " + Math.round(sample.z());
            int coordinatesX = x + listWidth - SCROLLBAR_ROOM - textRenderer.getWidth(coordinates);
            UiDraw.text(context, coordinates, coordinatesX, rowY + 1, MapPalette.TEXT_DIM);

            // The biome fills whatever is left between the number and the
            // coordinates, and simply drops out when the panel is too narrow to
            // hold it - a truncated word is worth less here than the coordinates
            // it would otherwise crowd.
            String biome = SampleCard.prettyBiome(sample.biome());
            if (biome != null) {
                int biomeX = x + 7 + textRenderer.getWidth(number) + 6;
                int room = coordinatesX - biomeX - 6;
                if (room >= 20) {
                    UiDraw.text(context, UiDraw.trim(biome, room), biomeX, rowY + 1,
                            Theme.TEXT_FAINT);
                }
            }
        }

        context.disableScissor();
        listScrollbarX = x + listWidth - SCROLLBAR_ROOM;
        Theme.scrollbar(context, x + listWidth - 4, top, listViewHeight, listContentHeight, listScroll);
    }

    /** True while the pointer is over the sample list's scrollbar strip. */
    private boolean overListScrollbar(double mouseX, double mouseY) {
        return listViewHeight > 0 && listContentHeight > listViewHeight
                && mouseX >= listScrollbarX && mouseX < listScrollbarX + SCROLLBAR_ROOM
                && mouseY >= listViewTop && mouseY < listViewTop + listViewHeight;
    }

    /**
     * Jumps the list so the thumb sits under the pointer.
     *
     * <p>The thumb is sized the same way {@link Theme#scrollbar} draws it, so
     * grabbing it does not make it move out from under the cursor.
     */
    private void scrollListTo(double mouseY) {
        int maxScroll = maxListScroll();
        if (maxScroll <= 0) {
            return;
        }
        int thumbHeight = Math.max(16, listViewHeight * listViewHeight / listContentHeight);
        double travel = listViewHeight - thumbHeight;
        if (travel <= 0) {
            listScroll = 0;
            return;
        }
        double position = (mouseY - listViewTop - thumbHeight / 2.0) / travel;
        listScroll = clamp((int) Math.round(position * maxScroll), 0, maxScroll);
    }

    /** How far the sample list can scroll, from what it actually drew. */
    private int maxListScroll() {
        return Math.max(0, listContentHeight - listViewHeight);
    }

    private int colorFor(RtpSample sample) {
        return switch (canvas.colorMode()) {
            case REGION -> {
                RTPBuddyConfig config = RTPBuddyClient.config();
                yield config.regionColor(config.regionKey(sample));
            }
            case SESSION -> {
                var session = RTPBuddyClient.store().session(sample.sessionId());
                yield MapPalette.session(session == null ? 0 : session.colorIndex);
            }
            case BIOME -> dev.rtpbuddy.util.Biomes.color(sample.biome());
            default -> Worlds.dimensionColor(sample.dimension());
        };
    }

    private void renderDetail(DrawContext context, int x, int y, int detailWidth, int detailHeight) {
        RtpSample sample = findSelected();
        if (sample == null) {
            return;
        }
        UiDraw.panel(context, x, y, detailWidth, detailHeight);
        int cursor = UiDraw.heading(context, Lang.t("map.sample_n", displayNumber(sample)),
                x + 5, y + 4, detailWidth - 10);
        int inner = detailWidth - 10;
        cursor = UiDraw.row(context, Lang.t("map.detail.x"), Numbers.fixed(sample.x(), 3),
                x + 5, cursor, inner);
        cursor = UiDraw.row(context, Lang.t("map.detail.y"), Numbers.fixed(sample.y(), 3),
                x + 5, cursor, inner);
        cursor = UiDraw.row(context, Lang.t("map.detail.z"), Numbers.fixed(sample.z(), 3),
                x + 5, cursor, inner);
        cursor = UiDraw.row(context, Lang.t("map.detail.distance"),
                Numbers.compact(sample.distanceFromOrigin()), x + 5, cursor, inner);
        UiDraw.text(context, UiDraw.trim(Lang.t("map.detail.hint"), inner),
                x + 5, cursor + 1, MapPalette.TEXT_DIM);
    }

    private RtpSample findSelected() {
        for (RtpSample sample : visible) {
            if (sample.sample() == selectedSample) {
                return sample;
            }
        }
        return null;
    }

    private void drawSelectionRect(DrawContext context) {
        int x1 = (int) Math.min(selectStartX, selectCurrentX);
        int y1 = (int) Math.min(selectStartY, selectCurrentY);
        int x2 = (int) Math.max(selectStartX, selectCurrentX);
        int y2 = (int) Math.max(selectStartY, selectCurrentY);
        context.fill(x1, y1, x2, y2, MapPalette.SELECTION_FILL);
        UiDraw.border(context, x1, y1, x2 - x1, y2 - y1, MapPalette.SELECTION_BORDER);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Before super, and before everything else on the screen: an open popup
        // owns the next click wherever it lands. Without this a second press on
        // the button that opened it would close and reopen it in one go.
        if (biomeMenu.isOpen()) {
            DropdownMenu.Pick pick = biomeMenu.click(click.x(), click.y());
            if (pick != null) {
                filter.biomeFamily = pick.key();
                refresh();
                rebuild();
            }
            return true;
        }
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        double mouseX = click.x();
        double mouseY = click.y();

        // Dividers are tested before anything else in the body of the screen:
        // they overlap nothing, but they sit right against the canvas edge and a
        // click there should never be read as the start of a pan.
        if (overSplitter(mouseX, mouseY, Splitter.LEFT)) {
            return grabSplitter(Splitter.LEFT, doubled);
        }
        if (overSplitter(mouseX, mouseY, Splitter.RIGHT)) {
            return grabSplitter(Splitter.RIGHT, doubled);
        }
        if (overSidebarSplitter(mouseX, mouseY)) {
            return grabSplitter(Splitter.SIDEBAR, doubled);
        }

        if (clickSidebarExtra(mouseX, mouseY, click.button())) {
            return true;
        }

        if (canvas.view().contains(mouseX, mouseY)) {
            if (doubled) {
                canvas.view().setCenter(canvas.view().screenToWorldX(mouseX),
                        canvas.view().screenToWorldZ(mouseY));
                return true;
            }
            if ((click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                selecting = true;
                selectStartX = mouseX;
                selectStartY = mouseY;
                selectCurrentX = mouseX;
                selectCurrentY = mouseY;
                return true;
            }
            int hit = canvas.hitTest(visible, mouseX, mouseY);
            if (hit >= 0) {
                selectedSample = visible.get(hit).sample();
                return true;
            }
            panning = true;
            return true;
        }

        int listX = rightPanelX() + 6;
        // The scrollbar is asked first. It sits inside the list's own bounds, so
        // without this every attempt to drag it just picked the row behind it.
        if (rightPanel > 0 && overListScrollbar(mouseX, mouseY)) {
            draggingList = true;
            scrollListTo(mouseY);
            return true;
        }
        if (rightPanel > 0 && listViewHeight > 0
                && mouseX >= listX && mouseX < listX + rightPanel - 12 - SCROLLBAR_ROOM
                && mouseY >= listViewTop && mouseY < listViewTop + listViewHeight) {
            int rowHeight = UiDraw.lineHeight();
            int index = (int) ((mouseY - listViewTop + listScroll) / rowHeight);
            if (index >= 0 && index < visible.size()) {
                selectedSample = visible.get(index).sample();
                return true;
            }
        }
        return false;
    }

    /** A double-click on a divider restores that panel's default width. */
    private boolean grabSplitter(Splitter which, boolean doubled) {
        if (doubled) {
            MapConfig config = mapConfig();
            if (which == Splitter.LEFT) {
                config.panelLeftFraction = MapConfig.DEFAULT_PANEL_LEFT;
            } else if (which == Splitter.SIDEBAR) {
                config.sidebarFraction = MapConfig.DEFAULT_SIDEBAR;
            } else {
                config.panelRightFraction = MapConfig.DEFAULT_PANEL_RIGHT;
            }
            RTPBuddyClient.configManager().save();
            setStatus(Lang.t("map.status.panels_reset"));
            computeLayout();
            return true;
        }
        dragging = which;
        dragAccumulator = 0;
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingList) {
            scrollListTo(click.y());
            return true;
        }
        if (dragging == Splitter.SIDEBAR) {
            resizeSidebar(deltaY);
            return true;
        }
        if (dragging != Splitter.NONE) {
            resizePanel(dragging, deltaX);
            return true;
        }
        if (panning) {
            canvas.view().panByPixels(deltaX, deltaY);
            return true;
        }
        if (selecting) {
            selectCurrentX = click.x();
            selectCurrentY = click.y();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    /**
     * Grows or shrinks one panel by the mouse movement since the last event.
     *
     * <p>Deliberately driven by the delta rather than by the cursor's absolute
     * position: the delta is the one figure that means the same thing whatever
     * the GUI scale is doing, and it is what the canvas panning already uses.
     * The accumulator keeps the sub-pixel remainder, so a slow drag does not
     * lose movement to rounding.
     *
     * <p>The upper bound comes from what the map and the opposite panel still
     * need, so a drag can never squeeze the canvas below its minimum or push the
     * other panel off the screen.
     */
    /**
     * Moves the sidebar's divider. Same delta-driven accumulator as the vertical
     * dividers, for the same reason: a delta means the same thing at every GUI
     * scale, and the remainder has to survive between events or a slow drag
     * rounds away to nothing.
     */
    private void resizeSidebar(double deltaY) {
        int panel = panelHeight();
        int ceiling = panel - MIN_SAMPLE_LIST;
        if (ceiling <= MIN_SIDEBAR_TOP) {
            return;
        }
        dragAccumulator += deltaY;
        int whole = (int) dragAccumulator;
        if (whole == 0) {
            return;
        }
        dragAccumulator -= whole;

        int pixels = clamp(sidebarExtraHeight() + whole, MIN_SIDEBAR_TOP, ceiling);
        mapConfig().sidebarFraction = clampSidebarFraction(pixels / (double) panel);
    }

    private void resizePanel(Splitter which, double deltaX) {
        MapConfig config = mapConfig();
        int other = which == Splitter.LEFT ? rightPanel : leftPanel;
        int reserved = GAP * 2 + SPLITTER + (other > 0 ? other + SPLITTER : 0);
        int maxPanel = width - reserved - (other > 0 ? MIN_CANVAS_BOTH_PANELS : MIN_CANVAS);
        maxPanel = Math.min(maxPanel, (int) Math.round(width * MapConfig.MAX_PANEL_FRACTION));
        if (maxPanel < MIN_PANEL) {
            return;
        }

        // Dragging the left divider right widens the left panel; the right
        // divider is mirrored.
        dragAccumulator += which == Splitter.LEFT ? deltaX : -deltaX;
        int whole = (int) dragAccumulator;
        if (whole == 0) {
            return;
        }
        dragAccumulator -= whole;

        int current = which == Splitter.LEFT ? leftPanel : rightPanel;
        int pixels = clamp(current + whole, MIN_PANEL, maxPanel);
        double fraction = Math.max(MapConfig.MIN_PANEL_FRACTION,
                Math.min(MapConfig.MAX_PANEL_FRACTION, pixels / (double) width));

        if (which == Splitter.LEFT) {
            config.panelLeftFraction = fraction;
        } else {
            config.panelRightFraction = fraction;
        }
        computeLayout();
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingList) {
            draggingList = false;
            return true;
        }
        if (dragging != Splitter.NONE) {
            dragging = Splitter.NONE;
            // One write per drag rather than one per frame.
            RTPBuddyClient.configManager().save();
            return true;
        }
        if (selecting) {
            selecting = false;
            rectSelection = canvas.withinScreenRect(visible, selectStartX, selectStartY,
                    selectCurrentX, selectCurrentY);
            if (!rectSelection.isEmpty()) {
                SampleStats selectionStats = SampleStats.of(rectSelection,
                        RTPBuddyClient.config().guards, RTPBuddyClient.config().map.densityCellSize);
                setStatus(Lang.t("map.status.selection", rectSelection.size(),
                        Numbers.compact(selectionStats.distMean),
                        Numbers.compact(selectionStats.width()),
                        Numbers.compact(selectionStats.depth())));
            }
            // The delete button carries the count of what it would remove, so
            // the toolbar has to be rebuilt for a selection to show up on it.
            rebuild();
            return true;
        }
        panning = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // An open menu takes the wheel: it hangs off a toolbar button, so
        // zooming the map underneath it would only move the map out from under
        // a list the player is still reading.
        if (biomeMenu.isOpen()) {
            biomeMenu.scrolled(verticalAmount);
            return true;
        }
        if (canvas.view().contains(mouseX, mouseY)) {
            canvas.view().zoomAt(mouseX, mouseY, verticalAmount);
            return true;
        }
        if (leftPanel > 0 && mouseX < leftSplitterX() + SPLITTER) {
            int maxScroll = Math.max(0, statsPanel.contentHeight() - panelHeight() + 16);
            statsScroll = clamp(statsScroll - (int) (verticalAmount * 18), 0, maxScroll);
            return true;
        }
        if (rightPanel == 0) {
            return false;
        }
        int extraHeight = sidebarExtraHeight();
        if (scope == Scope.ALL && mouseX >= rightPanelX()
                && mouseY >= TOP_BAR && mouseY < TOP_BAR + extraHeight) {
            sessionList.scrollBy(verticalAmount, extraHeight);
            return true;
        }
        int rowHeight = UiDraw.lineHeight();
        listScroll = clamp(listScroll - (int) (verticalAmount * rowHeight * 3), 0, maxListScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // A popup takes Escape before the screen does, or dismissing the list
        // would also throw away the map.
        if (biomeMenu.isOpen() && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            biomeMenu.close();
            return true;
        }
        if (PanicKey.handle(input)) {
            setStatus(Lang.t("map.status.stopped_all"));
            rebuild();
            return true;
        }
        switch (input.key()) {
            case GLFW.GLFW_KEY_TAB -> {
                cyclePanels();
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                canvas.view().reset();
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                fit(shiftHeld());
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> {
                stepSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> {
                stepSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_C -> {
                if (copySelectedCoordinates()) {
                    return true;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (deleteSelected()) {
                    return true;
                }
            }
            // Both letters, because GLFW reports the physical key using the US
            // layout: on the QWERTZ keyboard this mod is written for, the key
            // printed Z sits where a US keyboard has Y, so Strg+Z arrives here
            // as GLFW_KEY_Y and the shortcut silently did nothing.
            case GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_Y -> {
                if (input.hasCtrlOrCmd()) {
                    return undoDelete();
                }
            }
            default -> {
            }
        }
        return super.keyPressed(input);
    }

    private void stepSelection(int direction) {
        if (visible.isEmpty()) {
            return;
        }
        int index = 0;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).sample() == selectedSample) {
                index = i;
                break;
            }
        }
        index = clamp(index + direction, 0, visible.size() - 1);
        RtpSample sample = visible.get(index);
        selectedSample = sample.sample();
        canvas.view().setCenter(sample.x(), sample.z());
    }

    private boolean copySelectedCoordinates() {
        RtpSample sample = findSelected();
        if (sample == null || client == null) {
            return false;
        }
        client.keyboard.setClipboard(Numbers.fixed(sample.x(), 2) + " "
                + Numbers.fixed(sample.y(), 2) + " " + Numbers.fixed(sample.z(), 2));
        setStatus(Lang.t("map.status.copied", displayNumber(sample)));
        return true;
    }

    /** What the delete button would act on: the box selection, else the pick. */
    private List<RtpSample> deleteTargets() {
        if (!rectSelection.isEmpty()) {
            return rectSelection;
        }
        RtpSample selected = findSelected();
        return selected == null ? List.of() : List.of(selected);
    }

    private String deleteLabel() {
        int count = deleteTargets().size();
        return count > 1
                ? Lang.t("map.button.delete_n", count)
                : Lang.t("map.button.delete");
    }

    /**
     * Deletes what is picked, asking first when it is more than one landing.
     *
     * <p>A single sample goes straight away - it is one click to undo and the
     * confirmation would outnumber the work. A box selection can hold a hundred,
     * so that one states its count and waits.
     */
    private void requestDelete() {
        List<RtpSample> targets = deleteTargets();
        if (targets.isEmpty()) {
            setStatus(Lang.t("map.status.delete_nothing"));
            return;
        }
        if (targets.size() == 1) {
            deleteSamples(targets);
            return;
        }
        List<RtpSample> batch = List.copyOf(targets);
        client.setScreen(new ConfirmDialog(this,
                Lang.t("dialog.delete.title", batch.size()),
                Lang.t("dialog.delete.body", batch.size(), displayNumber(batch.get(0)),
                        displayNumber(batch.get(batch.size() - 1))),
                Lang.t("dialog.delete.confirm"),
                () -> deleteSamples(batch)));
    }

    private void deleteSamples(List<RtpSample> targets) {
        if (targets.isEmpty()) {
            return;
        }
        for (RtpSample sample : targets) {
            RTPBuddyClient.store().remove(sample.sample());
        }
        RTPBuddyClient.store().flushNow();
        lastDeleted = List.copyOf(targets);
        rectSelection = List.of();
        selectedSample = -1;
        refresh();
        setStatus(targets.size() == 1
                ? Lang.t("map.status.removed", displayNumber(targets.get(0)))
                : Lang.t("map.status.removed_n", targets.size()));
        rebuild();
    }

    /** Puts the last deleted batch back, sample numbers and all. */
    private boolean undoDelete() {
        if (lastDeleted.isEmpty()) {
            setStatus(Lang.t("map.status.undo_empty"));
            return true;
        }
        RTPBuddyClient.store().addAll(lastDeleted, null);
        RTPBuddyClient.store().flushNow();
        setStatus(Lang.t("map.status.undone", lastDeleted.size()));
        lastDeleted = List.of();
        refresh();
        rebuild();
        return true;
    }

    private boolean deleteSelected() {
        List<RtpSample> targets = deleteTargets();
        if (targets.isEmpty()) {
            return false;
        }
        requestDelete();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
