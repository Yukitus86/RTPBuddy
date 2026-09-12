package dev.rtpbuddy.data;

/**
 * A play session. Mutable because {@code endedAt} and {@code label} are updated
 * in place while the session is live.
 */
public class SessionRecord {

    public static final String LEGACY_ID = "legacy-import";

    public String id;
    public long startedAt;
    public long endedAt;
    public String server = "";
    public String label = "";
    public int colorIndex;

    public SessionRecord() {
    }

    public SessionRecord(String id, long startedAt, String server, int colorIndex) {
        this.id = id;
        this.startedAt = startedAt;
        this.endedAt = startedAt;
        this.server = server == null ? "" : server;
        this.colorIndex = colorIndex;
    }

    public boolean isLegacy() {
        return LEGACY_ID.equals(id);
    }

    public long durationMillis() {
        return Math.max(0L, endedAt - startedAt);
    }

    public String displayName() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        if (isLegacy()) {
            return "Imported (rtpmapper)";
        }
        return server == null || server.isBlank() ? "Singleplayer" : server;
    }
}
