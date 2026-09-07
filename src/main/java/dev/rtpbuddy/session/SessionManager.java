package dev.rtpbuddy.session;

import dev.rtpbuddy.RTPBuddy;
import dev.rtpbuddy.data.SampleStore;
import dev.rtpbuddy.data.SessionRecord;

import java.util.UUID;

/**
 * Owns the "current session" concept: a session opens when the player joins a
 * world or server and closes on disconnect. The session id is stamped onto every
 * sample, which is what lets the session map show just this sitting while the
 * all-session map aggregates every one ever recorded.
 */
public class SessionManager {

    private final SampleStore store;
    private SessionRecord current;

    public SessionManager(SampleStore store) {
        this.store = store;
    }

    public SessionRecord current() {
        return current;
    }

    public String currentId() {
        return current == null ? null : current.id;
    }

    public boolean active() {
        return current != null;
    }

    /** Opens a session for the given server address ("" for singleplayer). */
    public SessionRecord begin(String server) {
        if (current != null) {
            end();
        }
        int colorIndex = nextColorIndex();
        current = new SessionRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), server, colorIndex);
        store.putSession(current);
        RTPBuddy.LOGGER.info("[RTPBuddy] session {} started ({})", current.id, current.displayName());
        return current;
    }

    /**
     * Stamps the end time and persists. Safe to call when no session is open.
     *
     * <p>A session that recorded nothing is discarded rather than stored: it puts
     * an empty row in the all-session list and contributes nothing to any map.
     */
    public void end() {
        if (current == null) {
            return;
        }
        if (!store.hasSamplesFor(current.id)) {
            store.removeSession(current.id);
            RTPBuddy.LOGGER.debug("[RTPBuddy] session {} ended with no samples, discarded", current.id);
            current = null;
            store.flushNow();
            return;
        }
        current.endedAt = System.currentTimeMillis();
        store.putSession(current);
        store.flushNow();
        RTPBuddy.LOGGER.info("[RTPBuddy] session {} ended", current.id);
        current = null;
    }

    /**
     * Continues an earlier sitting instead of opening a new one: every landing
     * from here on is stamped with its id, so its numbering carries on where it
     * stopped rather than restarting at 1.
     *
     * <p>The sitting in progress is closed first, which discards it when it
     * recorded nothing - resuming should not leave an empty stub behind. The
     * imported bucket is refused: it is a container for old data, not a sitting
     * anyone was ever in.
     *
     * @return false when the record is unknown, already current, or the import
     */
    public boolean resume(SessionRecord session) {
        if (session == null || session.isLegacy()) {
            return false;
        }
        SessionRecord known = store.session(session.id);
        if (known == null) {
            return false;
        }
        if (current != null && current.id.equals(known.id)) {
            return false;
        }
        end();
        current = known;
        // Reopened, so the duration runs again from now rather than showing the
        // gap since the sitting was last left.
        current.endedAt = System.currentTimeMillis();
        store.putSession(current);
        store.flushNow();
        RTPBuddy.LOGGER.info("[RTPBuddy] session {} resumed ({})", current.id, current.displayName());
        return true;
    }

    /**
     * Points at a record that already exists, closing nothing first.
     *
     * <p>For the one case where the sitting in progress stops existing without
     * ever ending: a merge folded it into another record. Calling {@link #end()}
     * there would stamp and store a record the merge has already removed.
     */
    public void adopt(SessionRecord session) {
        if (session == null) {
            return;
        }
        current = session;
        current.endedAt = System.currentTimeMillis();
        store.putSession(current);
        RTPBuddy.LOGGER.info("[RTPBuddy] session {} adopted after a merge", current.id);
    }

    /** Keeps endedAt fresh so a crash still leaves a sensible duration behind. */
    public void touch() {
        if (current != null) {
            current.endedAt = System.currentTimeMillis();
        }
    }

    public void rename(String label) {
        if (current != null) {
            current.label = label;
            store.putSession(current);
        }
    }

    private int nextColorIndex() {
        int highest = -1;
        for (SessionRecord session : store.sessions()) {
            highest = Math.max(highest, session.colorIndex);
        }
        return highest + 1;
    }
}
