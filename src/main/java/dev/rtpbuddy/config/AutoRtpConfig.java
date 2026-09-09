package dev.rtpbuddy.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings for the optional auto-RTP loop.
 *
 * <p>Nothing here starts the loop. Whether it is running is a runtime-only flag
 * in {@code AutoRtpController} that is never written to disk, so the loop always
 * comes back stopped after a restart.
 */
public class AutoRtpConfig {

    /** Seconds to wait between teleports. Floored at {@link #MIN_COOLDOWN_SECONDS}. */
    public int cooldownSeconds = 60;

    /** Random extra delay, 0..jitter seconds, so the interval is not machine-exact. */
    public int jitterSeconds = 5;

    /** Stop after this many teleports in one session. 0 means no limit. */
    public int maxPerSession = 0;

    /** Stop after this many minutes of running. 0 means no limit. */
    public int stopAfterMinutes = 30;

    /** Which configured region to request. Used when {@link #regions} is empty. */
    public String region = "overworld";

    /**
     * The regions auto-RTP rotates through, by region id.
     *
     * <p>DonutSMP takes the zone as an argument - {@code /rtp asia},
     * {@code /rtp eu central} - so sending a different region is a different
     * command, not a different mode. One entry behaves exactly like the old
     * single region; several make each teleport go to the next one, which is
     * the only way to fill a map outside the zone a plain {@code /rtp} keeps
     * landing in.
     */
    public List<String> regions = new ArrayList<>();

    /** ROUND_ROBIN walks the list in order, RANDOM picks one each time. */
    public String order = "ROUND_ROBIN";

    /** Do not send the next command until the previous landing has been recorded. */
    public boolean waitForCapture = true;

    /** Stop as soon as the player takes damage. */
    public boolean stopOnDamage = true;

    /** Stop when a landing falls outside the border or spawn guard band. */
    public boolean stopOnGuardViolation = false;

    /**
     * Stop as soon as another player is within {@link #playerNearbyRadius}.
     *
     * <p>This reads the client's own entity list, which holds only the players
     * the server chose to send. Anyone outside the server's player tracking
     * range, and anyone the server is deliberately hiding, is invisible to it.
     * So it is a reason to stop, never a promise that nobody is there - and it
     * is written that way in the tooltip too, because a safety switch that is
     * trusted further than it reaches is worse than no switch.
     */
    public boolean stopOnPlayerNearby = false;

    /**
     * How close another player has to be to count, in blocks, measured in
     * three dimensions.
     *
     * <p>Past the server's tracking range a larger number buys nothing: the
     * client is never told about those players in the first place.
     */
    public int playerNearbyRadius = 64;

    /**
     * Every teleport waits for a key press instead of the clock.
     *
     * <p>The run is still a run: the rotation, the counters, the caps, the run
     * timer and every stop condition behave exactly as they do on the timer.
     * Only the moment is handed back to the player. It exists because filling a
     * map and watching where you land are two different jobs, and the second one
     * wants to happen at your pace, not at a rate you had to guess in advance.
     */
    public boolean manualStep = false;

    // ------------------------------------------------------- the search order

    /**
     * Whether the run is looking for something.
     *
     * <p>Every other stop in this class is a reason to give up. These are the
     * opposite: the loop keeps going until a landing matches, then stops on it.
     * Nothing here makes the loop send more, faster or differently - a search
     * order can only end a run earlier than it would have ended anyway.
     */
    public boolean findEnabled = false;

    /** Stop on the first landing in a cell no stored landing has ever reached. */
    public boolean findNewCell = false;

    /** Cell numbers to stop in, typed freely: "11, 12 20". */
    public String findCells = "";

    /** Biome families to stop in, by {@code Biomes.Family} name. */
    public List<String> findFamilies = new ArrayList<>();

    /**
     * Exact biome ids to stop in, such as {@code minecraft:cherry_grove}.
     *
     * <p>Config-only on purpose: the settings screen offers the families, which
     * is the choice worth making with a mouse. This is here for the rarer case
     * of wanting one specific biome and nothing near it.
     */
    public List<String> findBiomes = new ArrayList<>();

    /** Stop only at or beyond this distance from the origin. 0 disables. */
    public double findMinDistance = 0;

    /** Stop only at or within this distance from the origin. 0 disables. */
    public double findMaxDistance = 0;

    /** Stop within {@link #findNearRadius} of this point. Radius 0 disables. */
    public double findNearX = 0;
    public double findNearZ = 0;
    public double findNearRadius = 0;

    /** false: any condition ends the run. true: they all have to hold at once. */
    public boolean findMatchAll = false;

    /**
     * Give up after this many landings without a match. 0 means never.
     *
     * <p>A search order with no cap and a rare target is a loop that runs until
     * something else stops it, which is exactly the sort of thing that should
     * not be the default.
     */
    public int findGiveUpAfter = 0;

    /** Play a short sound when the search order fires. */
    public boolean findSound = true;

    public static final int MIN_COOLDOWN_SECONDS = 2;

    public int effectiveCooldownSeconds() {
        return Math.max(MIN_COOLDOWN_SECONDS, cooldownSeconds);
    }
}
