package dev.rtpcompanion;

/**
 * What this mod has seen from RTPBuddy, and the wall between the two mods.
 *
 * <p><b>This class must never name a RTPBuddy type.</b> That is its whole job.
 * Everything else in this mod loads whether or not RTPBuddy is installed, and a
 * class that mentions {@code dev.rtpbuddy.api} can only be loaded when those
 * classes exist. Keeping the mention on one side of this wall - in
 * {@link RTPBuddyLink}, which the loader only ever touches when RTPBuddy asked
 * for it - is what lets this mod run on its own with no check, no reflection
 * and no {@code depends} entry.
 *
 * <p>Written from the client thread by the link and read from the client thread
 * by the command, so the fields are volatile and nothing here blocks.
 */
public final class LandingLog {

    private static volatile boolean connected;
    private static volatile String rtpBuddyVersion = "";
    private static volatile int seenThisGame;
    private static volatile String lastLanding = "";

    private LandingLog() {
    }

    /** Called once when RTPBuddy hands over its api. */
    static void connect(String version) {
        connected = true;
        rtpBuddyVersion = version == null ? "" : version;
    }

    /** Called for every landing RTPBuddy records while this mod is loaded. */
    static void record(String line) {
        seenThisGame++;
        lastLanding = line == null ? "" : line;
    }

    public static boolean connected() {
        return connected;
    }

    public static String rtpBuddyVersion() {
        return rtpBuddyVersion;
    }

    public static int seenThisGame() {
        return seenThisGame;
    }

    public static String lastLanding() {
        return lastLanding;
    }
}
