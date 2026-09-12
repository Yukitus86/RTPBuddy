package dev.rtpbuddy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the mod. Kept separate from the client entrypoint so that
 * data/stats classes can be unit-reasoned about without touching Minecraft classes.
 */
public final class RTPBuddy {

    public static final String MOD_ID = "rtpbuddy";
    public static final String MOD_NAME = "RTPBuddy";

    /** Directory name used under {@code .minecraft/config/}. */
    public static final String CONFIG_DIR = "rtpbuddy";

    /** Legacy directory this mod migrates data out of, exactly once. */
    public static final String LEGACY_CONFIG_DIR = "rtpmapper";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private RTPBuddy() {
    }
}
