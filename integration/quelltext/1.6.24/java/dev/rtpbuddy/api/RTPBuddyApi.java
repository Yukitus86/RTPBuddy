package dev.rtpbuddy.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything RTPBuddy offers another mod on the same client.
 *
 * <p>Get it by implementing {@link RTPBuddyPlugin} and declaring it under the
 * {@code rtpbuddy} entrypoint - see that interface for why that, and not a
 * static lookup, is the way in. {@link #getOrNull()} exists for code that
 * already runs for other reasons and only wants to know whether RTPBuddy is
 * there.
 *
 * <p><b>What this deliberately does not offer.</b> There is no way to send a
 * command, move the player, or start the auto-RTP loop. The loop is off until
 * the player starts it by hand and that is the whole point of it; a second mod
 * being able to start it would make that promise untrue in a way nobody could
 * see from RTPBuddy's own screens. Stopping is the one direction that is
 * allowed, because stopping is always safe.
 *
 * <p><b>Threading.</b> Every method is called on the client thread. The lists
 * handed back are snapshots and safe to hold, but they are snapshots: a landing
 * recorded after the call is not in them.
 *
 * <p><b>Stability.</b> {@link #API_VERSION} is bumped only when something here
 * changes in a way that breaks a mod compiled against the version before.
 * Adding a method or a record accessor is not that. The mod's own version moves
 * on its own schedule and says nothing about this.
 */
public interface RTPBuddyApi {

    /** The entrypoint key a partner mod declares its {@link RTPBuddyPlugin} under. */
    String ENTRYPOINT = "rtpbuddy";

    /**
     * The contract version this partner mod was compiled against, baked into
     * the partner's own class file. Compare it with {@link #apiVersion()}, which
     * is what the installed RTPBuddy actually speaks.
     */
    int API_VERSION = 1;

    /** What the installed RTPBuddy speaks. See {@link #API_VERSION}. */
    int apiVersion();

    /** The installed mod's version, e.g. {@code "1.6.24"}. */
    String modVersion();

    /**
     * {@code .minecraft/config/rtpbuddy}, where the recordings and the config
     * live. Read it if you must; writing into it means two writers on files
     * RTPBuddy saves on a debounce, and the later save wins.
     */
    Path dataDirectory();

    /** Every recorded landing, oldest first. A snapshot; never null. */
    List<Landing> landings();

    /** The landings of one sitting, oldest first. Empty for an unknown id. */
    List<Landing> landingsOf(String sittingId);

    /** The landings of the sitting that is running, or empty if none is. */
    List<Landing> landingsOfCurrentSitting();

    /** The most recently recorded landing, or null if nothing was ever recorded. */
    Landing lastLanding();

    /** How many landings are on record, without building the list. */
    int landingCount();

    /** Every sitting on record, oldest first. A snapshot; never null. */
    List<Sitting> sittings();

    /** The sitting that is running, or null between worlds. */
    Sitting currentSitting();

    /**
     * Adds a listener for landings as they are recorded. Registering the same
     * listener twice makes it fire twice; the api does not deduplicate.
     */
    void addLandingListener(LandingListener listener);

    /** Removes a listener. True if it was registered. */
    boolean removeLandingListener(LandingListener listener);

    /** True while the auto-RTP loop is running, held or not. */
    boolean autoRtpRunning();

    /**
     * Stops everything that acts on its own, exactly as the panic key does, and
     * tells the player what was stopped and why.
     *
     * @param reason a short line for the log and the chat message, in the
     *               partner mod's own words - it is shown to the player
     */
    void stopEverything(String reason);

    /**
     * The number of the server's region cell a point falls in, or 0 where the
     * grid does not reach - which is anywhere outside the configured server,
     * and everywhere in singleplayer.
     */
    int cellNumberAt(double x, double z);

    /**
     * The api if RTPBuddy is loaded and has finished starting, otherwise null.
     *
     * <p>Only safe from a class that is loaded whether or not RTPBuddy is
     * installed - which is the case this exists for, and also the case where
     * merely naming this type is the thing that would fail. Reach it by
     * reflection there, or take the {@link RTPBuddyPlugin} route instead and
     * skip the question entirely.
     */
    static RTPBuddyApi getOrNull() {
        return Holder.instance;
    }

    /** Internal. Set by RTPBuddy during its own init; do not call. */
    final class Holder {

        private static volatile RTPBuddyApi instance;

        private Holder() {
        }

        public static void set(RTPBuddyApi api) {
            instance = api;
        }
    }
}
