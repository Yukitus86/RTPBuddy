package dev.rtpbuddy.config;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One RTP destination the server offers. {@link #commandPattern} is matched
 * against the outgoing command (without the leading slash) to decide which
 * region a landing was <em>requested</em> for. RTPBuddy never infers the region
 * from the resulting coordinates.
 */
public class RegionPreset {

    public String id = "unknown";
    public String label = "Unknown";

    /** Java regex, case-insensitive, matched against the full command line. */
    public String commandPattern = "^rtp$";

    /**
     * The literal command auto-RTP sends for this region, without the leading
     * slash. Kept separate from {@link #commandPattern} because a regex cannot be
     * run backwards into the one command that a server expects.
     */
    public String command = "rtp";

    /** Expected dimension. Informational only - a mismatch is recorded, not corrected. */
    public String dimension = "minecraft:overworld";

    /** ARGB colour used for this region on the maps. */
    public int color = 0xFF66CC66;

    private transient Pattern compiled;
    private transient boolean compileFailed;

    public RegionPreset() {
    }

    public RegionPreset(String id, String label, String commandPattern, String command,
                        String dimension, int color) {
        this.id = id;
        this.label = label;
        this.commandPattern = commandPattern;
        this.command = command;
        this.dimension = dimension;
        this.color = color;
    }

    public boolean sendable() {
        return command != null && !command.isBlank();
    }

    public boolean matches(String command) {
        Pattern pattern = pattern();
        return pattern != null && pattern.matcher(command).find();
    }

    public Pattern pattern() {
        if (compiled == null && !compileFailed) {
            try {
                compiled = Pattern.compile(commandPattern, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                compileFailed = true;
            }
        }
        return compiled;
    }

    public boolean patternValid() {
        return pattern() != null;
    }

    public void invalidate() {
        compiled = null;
        compileFailed = false;
    }
}
