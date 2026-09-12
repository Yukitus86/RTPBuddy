package dev.rtpbuddy.config;

public class CaptureConfig {

    /** Master switch for recording. Turning this off leaves existing data untouched. */
    public boolean enabled = true;

    /** Watch outgoing commands and record the landing automatically. */
    public boolean autoCapture = true;

    /** Horizontal jump (blocks) that counts as a teleport rather than normal travel. */
    public double teleportDistanceThreshold = 200.0;

    /** How long an armed capture waits for the teleport before giving up (ticks). */
    public int armTimeoutTicks = 300;

    /** Ticks the player must stay put after landing before the sample is taken. */
    public int settleTicks = 10;

    /** Treat a dimension change while armed as a successful teleport. */
    public boolean captureOnDimensionChange = true;

    /** Coalesce disk writes for this long (ms) so a burst of RTPs is one write. */
    public int saveDebounceMillis = 2000;

    /** Keep rtp_samples.csv in sync with the authoritative JSON. */
    public boolean writeCsvMirror = true;

    /** Reject a landing whose horizontal jump is below the threshold. */
    public boolean rejectShortJumps = true;

    /** Default category stamped on new samples. */
    public String defaultCategory = "rtp";

    /** Region recorded for manual captures when no region is supplied. */
    public String manualFallbackRegion = "unknown";

    /**
     * Record an RTP command even when no configured region matches it.
     *
     * <p>Without this, a hand-typed {@code /rtp <something>} that is not in the
     * region list is ignored in silence - the landing simply never appears on
     * the map. With it, the command still arms a capture and the argument is
     * kept verbatim as the requested region, so an unknown destination is
     * recorded as itself rather than being dropped or guessed at.
     */
    public boolean captureUnknownRtp = true;

    /**
     * What counts as an RTP command for {@link #captureUnknownRtp}. The first
     * group is the command, the rest is kept as the requested region.
     */
    public String rtpCommandPattern = "^(rtp|wild|wilderness)(?: +(.*))?$";
}
