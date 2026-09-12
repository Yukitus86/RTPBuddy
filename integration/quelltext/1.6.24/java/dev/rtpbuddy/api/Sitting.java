package dev.rtpbuddy.api;

/**
 * One sitting - the stretch between joining a world and leaving it - as another
 * mod sees it.
 *
 * <p>Landings carry the id of the sitting they fell in, so this is what turns
 * {@link Landing#sittingId()} into something with a name, a server and a clock.
 * A sitting that is still running has its {@code endedAt} moved forward as it
 * goes, so the value is a snapshot and not a promise.
 */
public record Sitting(
        String id,
        long startedAt,
        long endedAt,
        String server,
        String label,
        boolean imported) {

    /** How long the sitting lasted, or has lasted so far. */
    public long durationMillis() {
        return Math.max(0L, endedAt - startedAt);
    }

    /** The name the mod itself shows for this sitting. */
    public String displayName() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        if (imported) {
            return "Imported (rtpmapper)";
        }
        return server == null || server.isBlank() ? "Singleplayer" : server;
    }
}
