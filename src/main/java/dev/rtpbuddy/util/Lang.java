package dev.rtpbuddy.util;

import net.minecraft.client.resource.language.I18n;

/**
 * Every user-visible string in the mod goes through here.
 *
 * <p>The keys live in {@code assets/rtpbuddy/lang/*.json}, so RTPBuddy simply
 * follows the language selected in Minecraft's own options: German when the
 * game is German, English otherwise. {@code en_us.json} is the fallback and
 * therefore has to stay complete - a missing key renders as the key itself.
 *
 * <p>Deliberately returns {@link String} rather than {@code Text}: most of the
 * UI is drawn from plain strings, and mixing the two only invites conversions
 * at every call site.
 */
public final class Lang {

    private static final String PREFIX = "rtpbuddy.";

    private Lang() {
    }

    public static String t(String key, Object... args) {
        return I18n.translate(PREFIX + key, args);
    }

    /**
     * Translation for an optional key, or {@code null} when no language file
     * defines it.
     *
     * <p>{@link I18n#translate} hands back the key itself for a miss, which is
     * fine for a label that must always show something but useless for the
     * settings tooltips: a control without an explanation should have no
     * tooltip at all rather than one reading
     * {@code rtpbuddy.settings.map.grid.tip}.
     */
    public static String tOrNull(String key, Object... args) {
        String full = PREFIX + key;
        String translated = I18n.translate(full, args);
        return translated.equals(full) ? null : translated;
    }

    /** {@code on} / {@code off}, used by the settings toggles. */
    public static String onOff(boolean value) {
        return t(value ? "word.on" : "word.off");
    }
}
