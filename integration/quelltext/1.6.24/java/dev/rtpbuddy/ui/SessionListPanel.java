package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.data.SessionRecord;
import dev.rtpbuddy.stats.SampleStats;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import net.minecraft.client.gui.DrawContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The session picker shown in the map sidebar while the scope is
 * {@link MapScreen.Scope#ALL}: every sitting ever recorded, with its colour, its
 * sample count and a tick that hides it from the plot.
 *
 * <p>Right-clicking two rows pins them against each other, which is the one
 * question the aggregate view cannot answer on its own: whether two sittings
 * landed the same way.
 */
public class SessionListPanel {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    /** Stable ids; the captions next to them are translated. */
    private static final String[] QUICK_IDS = {"all", "none", "today", "7d", "30d"};

    private final SampleFilter filter;
    private final Runnable onFilterChanged;
    private final Runnable onSessionsChanged;
    private final Confirm confirm;
    private final Prompt prompt;
    private final Consumer<String> status;

    /** Asks the player before something irreversible, then runs it. */
    public interface Confirm {
        void ask(String heading, String detail, String confirmLabel, Runnable action);
    }

    /** Asks the player for a line of text, then hands it over. */
    public interface Prompt {
        void ask(String heading, String hint, String initial, Consumer<String> onAccept);
    }

    private List<SessionRecord> sessions = List.of();
    private final Map<String, Integer> sampleCounts = new HashMap<>();
    private int countedRevision = -1;
    private int scroll;

    /** Sittings with no landing at all, which the list can hide and drop. */
    private int emptyCount;

    private String compareA;
    private String compareB;
    private SampleStats compareStatsA = SampleStats.empty();
    private SampleStats compareStatsB = SampleStats.empty();

    /** Screen-space rows, rebuilt every frame for hit-testing. */
    private final List<RowHit> rowHits = new ArrayList<>();
    private final List<RowHit> buttonHits = new ArrayList<>();

    public SessionListPanel(SampleFilter filter, Runnable onFilterChanged,
                            Runnable onSessionsChanged, Confirm confirm, Prompt prompt,
                            Consumer<String> status) {
        this.filter = filter;
        this.onFilterChanged = onFilterChanged;
        this.onSessionsChanged = onSessionsChanged;
        this.confirm = confirm;
        this.prompt = prompt;
        this.status = status;
    }

    public void reload() {
        recountSamples();
        List<SessionRecord> all = new ArrayList<>(RTPBuddyClient.store().sessions());
        all.sort(Comparator.comparingLong((SessionRecord session) -> session.startedAt).reversed());

        // Every join opens a sitting, so a session that recorded nothing is the
        // normal residue of logging in and doing something else. Counting them
        // here rather than filtering blindly keeps the offer to delete honest
        // about how many there are, and never counts the one running now.
        String currentId = RTPBuddyClient.sessions().currentId();
        List<SessionRecord> kept = new ArrayList<>(all.size());
        int empty = 0;
        boolean hide = RTPBuddyClient.config().map.hideEmptySessions;
        for (SessionRecord session : all) {
            boolean isEmpty = count(session) == 0 && !session.id.equals(currentId);
            if (isEmpty) {
                empty++;
                if (hide) {
                    continue;
                }
            }
            kept.add(session);
        }
        emptyCount = empty;
        sessions = kept;
    }

    /**
     * Drops every sitting that never recorded a landing, except the one running
     * now. Nothing is lost: a session record with no samples is a timestamp and
     * a colour, and the samples of any session are never touched here.
     *
     * @return how many were removed
     */
    public int purgeEmptySessions() {
        String currentId = RTPBuddyClient.sessions().currentId();
        int removed = 0;
        for (SessionRecord session : RTPBuddyClient.store().sessions()) {
            if (session.id.equals(currentId) || RTPBuddyClient.store().hasSamplesFor(session.id)) {
                continue;
            }
            if (RTPBuddyClient.store().removeSession(session.id)) {
                filter.visibleSessions.remove(session.id);
                removed++;
            }
        }
        if (removed > 0) {
            RTPBuddyClient.store().scheduleSave(0);
            reload();
        }
        return removed;
    }

    public int sessionCount() {
        return sessions.size();
    }

    public String describe() {
        int shown = filter.sessionFilterActive ? filter.visibleSessions.size() : sessions.size();
        return Lang.t("all.scope", shown, sessions.size());
    }

    /** True while the player has narrowed the aggregate view to a chosen set. */
    public boolean narrowed() {
        return filter.sessionFilterActive;
    }

    public int selectedCount() {
        return filter.visibleSessions.size();
    }

    /**
     * Per-session sample counts, recomputed only when the store changes. Doing
     * this per row per frame would be O(sessions x samples) every frame.
     *
     * <p>Keyed on the store's revision rather than its size: merging sittings
     * moves every count around without changing how many landings there are, so
     * a size check would have kept serving the counts from before the merge.
     */
    private void recountSamples() {
        int revision = RTPBuddyClient.store().revision();
        if (revision == countedRevision) {
            return;
        }
        countedRevision = revision;
        sampleCounts.clear();
        for (RtpSample sample : RTPBuddyClient.store().samplesView()) {
            sampleCounts.merge(sample.sessionId(), 1, Integer::sum);
        }
    }

    // ------------------------------------------------------------- rendering

    public void render(DrawContext context, int x, int y, int panelWidth, int panelHeight,
                       int mouseX, int mouseY) {
        rowHits.clear();
        buttonHits.clear();
        recountSamples();

        int cursor = UiDraw.heading(context, Lang.t("all.sessions"), x, y, panelWidth);
        cursor = renderQuickButtons(context, x, cursor, panelWidth);
        cursor = renderPurgeButton(context, x, cursor, panelWidth, mouseX, mouseY);
        cursor = renderResumeButton(context, x, cursor, panelWidth, mouseX, mouseY);
        cursor = renderMergeButton(context, x, cursor, panelWidth, mouseX, mouseY);
        cursor = renderRenameButton(context, x, cursor, panelWidth, mouseX, mouseY);

        int listTop = cursor;
        int listBottom = y + panelHeight;
        int rowHeight = rowHeight();

        context.enableScissor(x, listTop, x + panelWidth, listBottom);
        for (int i = 0; i < sessions.size(); i++) {
            SessionRecord session = sessions.get(i);
            int rowY = listTop + i * rowHeight - scroll;
            if (rowY + rowHeight < listTop || rowY > listBottom) {
                continue;
            }
            boolean shown = !filter.sessionFilterActive || filter.visibleSessions.contains(session.id);
            boolean hovered = mouseX >= x && mouseX < x + panelWidth
                    && mouseY >= rowY && mouseY < rowY + rowHeight;
            boolean compared = session.id.equals(compareA) || session.id.equals(compareB);

            if (compared) {
                context.fill(x, rowY, x + panelWidth, rowY + rowHeight, 0x33FFD166);
            } else if (hovered) {
                context.fill(x, rowY, x + panelWidth, rowY + rowHeight, 0x22FFFFFF);
            }

            // Colour swatch doubles as the visibility checkbox.
            int swatch = MapPalette.session(session.colorIndex);
            Theme.roundRect(context, x + 1, rowY + 3, 8, 8, 2, shown ? swatch : 0xFF3A424E);
            if (!shown) {
                UiDraw.border(context, x + 1, rowY + 3, 8, 8, swatch);
            }

            // The count and date are measured before the name is trimmed: a
            // fixed allowance let "Singleplayer" run straight into the number
            // once the panel was dragged narrow.
            String trailing = Lang.t("all.row", count(session), DAY.format(Instant.ofEpochMilli(session.startedAt)));
            int nameRoom = panelWidth - UiDraw.font().getWidth(trailing) - 22;
            UiDraw.text(context, UiDraw.trim(session.displayName(), Math.max(16, nameRoom)), x + 13, rowY + 2,
                    shown ? MapPalette.TEXT : MapPalette.TEXT_DIM);
            UiDraw.textRight(context, trailing, x + panelWidth - 2, rowY + 2, MapPalette.TEXT_DIM);

            rowHits.add(new RowHit(x, rowY, panelWidth, rowHeight, session.id));
        }
        context.disableScissor();

        if (compareA != null && compareB != null) {
            renderComparison(context, x, listBottom - 58, panelWidth);
        }
    }

    /**
     * The five range shortcuts, on one row when they fit and on two when they
     * do not. Squeezing all five across a narrow panel left them reading
     * "Kei..." and "Heu...", which is no shortcut at all.
     */
    private int renderQuickButtons(DrawContext context, int x, int y, int panelWidth) {
        int widest = 0;
        for (String id : QUICK_IDS) {
            widest = Math.max(widest, UiDraw.font().getWidth(Lang.t("all.quick." + id)));
        }
        int perRow = (panelWidth + 2) / (widest + 10) >= QUICK_IDS.length ? QUICK_IDS.length : 3;
        int rows = (QUICK_IDS.length + perRow - 1) / perRow;
        int buttonWidth = (panelWidth - (perRow - 1) * 2) / perRow;

        for (int i = 0; i < QUICK_IDS.length; i++) {
            int column = i % perRow;
            int row = i / perRow;
            int bx = x + column * (buttonWidth + 2);
            int by = y + row * 15;
            Theme.roundRect(context, bx, by, buttonWidth, 13, 3, MapPalette.PANEL_BORDER);
            String label = UiDraw.trim(Lang.t("all.quick." + QUICK_IDS[i]), buttonWidth - 4);
            int labelWidth = UiDraw.font().getWidth(label);
            UiDraw.text(context, label, bx + (buttonWidth - labelWidth) / 2, by + 3, MapPalette.TEXT);
            buttonHits.add(new RowHit(bx, by, buttonWidth, 13, QUICK_IDS[i]));
        }
        return y + rows * 15 + 2;
    }

    /**
     * The offer to drop the sittings that recorded nothing. Hidden entirely when
     * there are none, so the panel does not carry a permanently dead button.
     */
    private int renderPurgeButton(DrawContext context, int x, int y, int panelWidth,
                                  int mouseX, int mouseY) {
        if (emptyCount <= 0) {
            return y;
        }
        boolean hovered = mouseX >= x && mouseX < x + panelWidth && mouseY >= y && mouseY < y + 13;
        Theme.roundRect(context, x, y, panelWidth, 13, 3,
                hovered ? Theme.mix(MapPalette.PANEL_BORDER, Theme.DANGER, 0.55) : MapPalette.PANEL_BORDER);
        String label = UiDraw.trim(Lang.t("all.purge_empty", emptyCount), panelWidth - 4);
        int labelWidth = UiDraw.font().getWidth(label);
        UiDraw.text(context, label, x + (panelWidth - labelWidth) / 2, y + 3,
                hovered ? Theme.DANGER : MapPalette.TEXT_DIM);
        buttonHits.add(new RowHit(x, y, panelWidth, 13, "purge_empty"));
        return y + 15;
    }

    /**
     * The sitting a resume would continue: exactly one row picked, and not the
     * one already running. Narrowing to one row is the interaction the list
     * already has - "Keine", then click a row - so the button appears the moment
     * a single sitting is on screen and says which one.
     */
    private SessionRecord resumeCandidate() {
        if (!filter.sessionFilterActive || filter.visibleSessions.size() != 1) {
            return null;
        }
        String id = filter.visibleSessions.iterator().next();
        if (id.equals(RTPBuddyClient.sessions().currentId())) {
            return null;
        }
        SessionRecord session = RTPBuddyClient.store().session(id);
        return session == null || session.isLegacy() ? null : session;
    }

    /**
     * Continues the picked sitting rather than opening a new one. Only offered
     * while a sitting is actually pickable, so the panel carries no dead button.
     */
    private int renderResumeButton(DrawContext context, int x, int y, int panelWidth,
                                   int mouseX, int mouseY) {
        SessionRecord candidate = resumeCandidate();
        if (candidate == null) {
            return y;
        }
        boolean hovered = mouseX >= x && mouseX < x + panelWidth && mouseY >= y && mouseY < y + 13;
        Theme.roundRect(context, x, y, panelWidth, 13, 3,
                hovered ? Theme.mix(MapPalette.PANEL_BORDER, Theme.ACCENT, 0.55) : MapPalette.PANEL_BORDER);
        String label = UiDraw.trim(Lang.t("all.resume",
                DAY.format(Instant.ofEpochMilli(candidate.startedAt))), panelWidth - 4);
        int labelWidth = UiDraw.font().getWidth(label);
        UiDraw.text(context, label, x + (panelWidth - labelWidth) / 2, y + 3,
                hovered ? MapPalette.TEXT_ACCENT : MapPalette.TEXT_DIM);
        buttonHits.add(new RowHit(x, y, panelWidth, 13, "resume_session"));
        return y + 15;
    }

    /**
     * Hands the current sitting over to the picked one. Refused across servers:
     * a sitting is one stretch of play on one server, and mixing two of them
     * into a single set of numbers would make the sitting a lie.
     */
    private void resumePicked() {
        SessionRecord candidate = resumeCandidate();
        if (candidate == null) {
            status.accept(Lang.t("all.resume_pick_one"));
            return;
        }
        if (!RTPBuddyClient.sessions().active()) {
            status.accept(Lang.t("word.not_in_world"));
            return;
        }
        String here = dev.rtpbuddy.util.Worlds.serverAddress(
                net.minecraft.client.MinecraftClient.getInstance());
        if (!sameServer(here, candidate.server)) {
            status.accept(Lang.t("all.resume_other_server", candidate.displayName()));
            return;
        }
        if (!RTPBuddyClient.sessions().resume(candidate)) {
            status.accept(Lang.t("all.resume_failed"));
            return;
        }
        RTPBuddyClient.capture().adoptSessionCounters(count(candidate));
        reload();
        // The number the next landing will carry, not the count it had:
        // continuing a sitting is a promise about what comes next.
        status.accept(Lang.t("all.resumed", candidate.displayName(), count(candidate) + 1));
        onSessionsChanged.run();
    }

    private static boolean sameServer(String a, String b) {
        return normaliseServer(a).equalsIgnoreCase(normaliseServer(b));
    }

    private static String normaliseServer(String server) {
        return server == null ? "" : server.trim();
    }

    /**
     * The picked sittings, oldest first, or null when a merge is not on offer.
     *
     * <p>Two or more, and none of them the imported bucket - that one is a
     * container for another mod's data, not a stretch of play. Whether they
     * belong to the same server is checked on the click instead, so the reason
     * for a refusal can be said out loud rather than the button just missing.
     */
    private List<SessionRecord> mergeCandidates() {
        if (!filter.sessionFilterActive || filter.visibleSessions.size() < 2) {
            return null;
        }
        List<SessionRecord> picked = new ArrayList<>(filter.visibleSessions.size());
        for (String id : filter.visibleSessions) {
            SessionRecord session = RTPBuddyClient.store().session(id);
            if (session == null || session.isLegacy()) {
                return null;
            }
            picked.add(session);
        }
        picked.sort(Comparator.comparingLong(session -> session.startedAt));
        return picked;
    }

    /** Folds the picked sittings into one. Irreversible, so it asks first. */
    private int renderMergeButton(DrawContext context, int x, int y, int panelWidth,
                                  int mouseX, int mouseY) {
        List<SessionRecord> picked = mergeCandidates();
        if (picked == null) {
            return y;
        }
        boolean hovered = mouseX >= x && mouseX < x + panelWidth && mouseY >= y && mouseY < y + 13;
        Theme.roundRect(context, x, y, panelWidth, 13, 3,
                hovered ? Theme.mix(MapPalette.PANEL_BORDER, Theme.ACCENT, 0.55) : MapPalette.PANEL_BORDER);
        String label = UiDraw.trim(Lang.t("all.merge", picked.size()), panelWidth - 4);
        int labelWidth = UiDraw.font().getWidth(label);
        UiDraw.text(context, label, x + (panelWidth - labelWidth) / 2, y + 3,
                hovered ? MapPalette.TEXT_ACCENT : MapPalette.TEXT_DIM);
        buttonHits.add(new RowHit(x, y, panelWidth, 13, "merge_sessions"));
        return y + 15;
    }

    private void mergePicked() {
        List<SessionRecord> picked = mergeCandidates();
        if (picked == null) {
            status.accept(Lang.t("all.merge_pick_two"));
            return;
        }
        String server = picked.get(0).server;
        for (SessionRecord session : picked) {
            if (!sameServer(server, session.server)) {
                status.accept(Lang.t("all.merge_other_server"));
                return;
            }
        }
        // Oldest wins: the merged sitting is the run that started first, and it
        // keeps that record's colour, label and start time.
        SessionRecord target = picked.get(0);
        int landings = 0;
        for (SessionRecord session : picked) {
            landings += count(session);
        }
        confirm.ask(Lang.t("all.merge_confirm"),
                Lang.t("all.merge_confirm.body", picked.size(), landings,
                        DAY.format(Instant.ofEpochMilli(target.startedAt))),
                Lang.t("all.merge_confirm.ok"),
                () -> applyMerge(target, picked));
    }

    private void applyMerge(SessionRecord target, List<SessionRecord> picked) {
        List<String> ids = new ArrayList<>(picked.size());
        for (SessionRecord session : picked) {
            ids.add(session.id);
        }
        String currentId = RTPBuddyClient.sessions().currentId();
        boolean currentFolded = currentId != null && ids.contains(currentId)
                && !currentId.equals(target.id);

        int moved = RTPBuddyClient.store().mergeSessions(target.id, ids);
        if (moved == 0) {
            status.accept(Lang.t("all.merge_failed"));
            return;
        }
        RTPBuddyClient.store().scheduleSave(0);
        recountSamples();

        // The sitting in progress can have just stopped existing as its own
        // record. Point at what it became rather than at a removed id, or the
        // next landing would be stamped with a session nothing knows about.
        if (currentFolded) {
            RTPBuddyClient.sessions().adopt(target);
            RTPBuddyClient.capture().adoptSessionCounters(count(target));
        }

        // Leave the map on what the merge produced, not on ids that are gone.
        filter.visibleSessions.clear();
        filter.visibleSessions.add(target.id);
        reload();
        status.accept(Lang.t("all.merged", picked.size(), count(target)));
        onSessionsChanged.run();
    }

    /**
     * The sitting a rename would touch: exactly one picked, and not the imported
     * bucket. The one running now is fair game - naming the sitting you are in
     * is the most likely moment to want to.
     */
    private SessionRecord renameCandidate() {
        if (!filter.sessionFilterActive || filter.visibleSessions.size() != 1) {
            return null;
        }
        SessionRecord session = RTPBuddyClient.store().session(filter.visibleSessions.iterator().next());
        return session == null || session.isLegacy() ? null : session;
    }

    /**
     * Gives the picked sitting a name of its own. Every row otherwise reads as
     * the server it was played on, which is the same words eight times over.
     */
    private int renderRenameButton(DrawContext context, int x, int y, int panelWidth,
                                   int mouseX, int mouseY) {
        if (renameCandidate() == null) {
            return y;
        }
        boolean hovered = mouseX >= x && mouseX < x + panelWidth && mouseY >= y && mouseY < y + 13;
        Theme.roundRect(context, x, y, panelWidth, 13, 3,
                hovered ? Theme.mix(MapPalette.PANEL_BORDER, Theme.ACCENT, 0.55) : MapPalette.PANEL_BORDER);
        String label = UiDraw.trim(Lang.t("all.rename"), panelWidth - 4);
        int labelWidth = UiDraw.font().getWidth(label);
        UiDraw.text(context, label, x + (panelWidth - labelWidth) / 2, y + 3,
                hovered ? MapPalette.TEXT_ACCENT : MapPalette.TEXT_DIM);
        buttonHits.add(new RowHit(x, y, panelWidth, 13, "rename_session"));
        return y + 15;
    }

    private void renamePicked() {
        SessionRecord session = renameCandidate();
        if (session == null) {
            status.accept(Lang.t("all.rename_pick_one"));
            return;
        }
        String fallback = session.server == null || session.server.isBlank()
                ? Lang.t("word.singleplayer")
                : session.server;
        prompt.ask(Lang.t("all.rename_prompt"),
                Lang.t("all.rename_hint", fallback),
                session.label == null ? "" : session.label,
                name -> applyRename(session, name, fallback));
    }

    private void applyRename(SessionRecord session, String name, String fallback) {
        // The store hands out the live record, so the label is set on the same
        // object the session manager is holding - a sitting being renamed while
        // it runs needs no second path.
        session.label = name == null ? "" : name.trim();
        RTPBuddyClient.store().putSession(session);
        RTPBuddyClient.store().scheduleSave(0);
        reload();
        status.accept(session.label.isEmpty()
                ? Lang.t("all.rename_cleared", fallback)
                : Lang.t("all.renamed", session.label));
        onSessionsChanged.run();
    }

    private void renderComparison(DrawContext context, int x, int y, int panelWidth) {
        SampleStats left = compareStatsA;
        SampleStats right = compareStatsB;
        Theme.roundRect(context, x, y - 2, panelWidth, 58, 4, MapPalette.PANEL);
        UiDraw.border(context, x, y - 2, panelWidth, 58, MapPalette.PANEL_BORDER);

        int half = panelWidth / 2;
        UiDraw.text(context, Lang.t("all.compare"), x + 3, y + 1, MapPalette.TEXT_ACCENT);
        int cursor = y + UiDraw.lineHeight() + 1;
        cursor = compareRow(context, Lang.t("all.compare.n"),
                String.valueOf(left.count), String.valueOf(right.count), x + 3, cursor, half);
        cursor = compareRow(context, Lang.t("all.compare.mean"),
                Numbers.compact(left.distMean), Numbers.compact(right.distMean), x + 3, cursor, half);
        compareRow(context, Lang.t("all.compare.chi"), Numbers.fixed(left.radialChiSquare, 1),
                Numbers.fixed(right.radialChiSquare, 1), x + 3, cursor, half);
    }

    private int compareRow(DrawContext context, String label, String a, String b, int x, int y, int half) {
        UiDraw.text(context, label, x, y, MapPalette.TEXT_DIM);
        UiDraw.textRight(context, a, x + half - 6, y, MapPalette.TEXT);
        UiDraw.textRight(context, b, x + half * 2 - 12, y, MapPalette.TEXT);
        return y + UiDraw.lineHeight();
    }

    private SampleStats statsFor(String sessionId) {
        return SampleStats.of(RTPBuddyClient.store().samplesOf(sessionId),
                RTPBuddyClient.config().guards, RTPBuddyClient.config().map.densityCellSize);
    }

    private int count(SessionRecord session) {
        return sampleCounts.getOrDefault(session.id, 0);
    }

    // ----------------------------------------------------------------- input

    public boolean click(double mouseX, double mouseY, int button) {
        for (RowHit hit : buttonHits) {
            if (hit.contains(mouseX, mouseY)) {
                if ("purge_empty".equals(hit.id())) {
                    int removed = purgeEmptySessions();
                    status.accept(removed > 0
                            ? Lang.t("all.purged", removed)
                            : Lang.t("all.purge_none"));
                    onFilterChanged.run();
                } else if ("resume_session".equals(hit.id())) {
                    resumePicked();
                } else if ("merge_sessions".equals(hit.id())) {
                    mergePicked();
                } else if ("rename_session".equals(hit.id())) {
                    renamePicked();
                } else {
                    applyQuickButton(hit.id());
                }
                return true;
            }
        }
        for (RowHit hit : rowHits) {
            if (hit.contains(mouseX, mouseY)) {
                if (button == 1) {
                    toggleComparison(hit.id());
                } else {
                    toggleSession(hit.id());
                }
                onFilterChanged.run();
                return true;
            }
        }
        return false;
    }

    public void scrollBy(double verticalAmount, int panelHeight) {
        int rowHeight = rowHeight();
        int maxScroll = Math.max(0, sessions.size() * rowHeight - panelHeight + 40);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * rowHeight * 2)));
    }

    public void resetScroll() {
        scroll = 0;
    }

    private static int rowHeight() {
        return UiDraw.lineHeight() + 3;
    }

    private void applyQuickButton(String id) {
        long now = System.currentTimeMillis();
        switch (id) {
            case "all" -> {
                filter.sessionFilterActive = false;
                filter.visibleSessions.clear();
                filter.fromTimestamp = 0;
                filter.toTimestamp = Long.MAX_VALUE;
            }
            case "none" -> {
                filter.sessionFilterActive = true;
                filter.visibleSessions.clear();
            }
            case "today" -> setSince(now - DAY_MILLIS);
            case "7d" -> setSince(now - 7 * DAY_MILLIS);
            case "30d" -> setSince(now - 30 * DAY_MILLIS);
            default -> {
            }
        }
        onFilterChanged.run();
    }

    private void setSince(long from) {
        filter.fromTimestamp = from;
        filter.toTimestamp = Long.MAX_VALUE;
    }

    /**
     * First click switches from "all sessions" to an explicit set containing
     * everything, so unticking one row hides exactly that row.
     */
    private void toggleSession(String sessionId) {
        if (!filter.sessionFilterActive) {
            filter.sessionFilterActive = true;
            filter.visibleSessions.clear();
            for (SessionRecord session : sessions) {
                filter.visibleSessions.add(session.id);
            }
        }
        if (!filter.visibleSessions.remove(sessionId)) {
            filter.visibleSessions.add(sessionId);
        }
        if (filter.visibleSessions.size() == sessions.size()) {
            filter.sessionFilterActive = false;
            filter.visibleSessions.clear();
        }
    }

    private void toggleComparison(String sessionId) {
        if (sessionId.equals(compareA)) {
            compareA = null;
        } else if (sessionId.equals(compareB)) {
            compareB = null;
        } else if (compareA == null) {
            compareA = sessionId;
        } else {
            compareB = sessionId;
        }
        // Comparison stats are computed here, not per frame.
        compareStatsA = compareA == null ? SampleStats.empty() : statsFor(compareA);
        compareStatsB = compareB == null ? SampleStats.empty() : statsFor(compareB);
        status.accept(Lang.t(compareA == null && compareB == null
                ? "all.status.compare_cleared"
                : "all.status.comparing"));
    }

    private record RowHit(int x, int y, int width, int height, String id) {

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
