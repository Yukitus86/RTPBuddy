package dev.rtpbuddy.util;

import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the biome id already stored on every landing into something the map can
 * colour, filter and name.
 *
 * <p>Two problems, two answers. Names come from Minecraft's own translation
 * keys, so a biome is called whatever the game calls it in the language the
 * player picked and no biome name is ever shipped in this mod's lang files.
 * Colours come from a small family table, because the recorded data holds close
 * to fifty distinct biomes and fifty hues on one map is confetti - families are
 * what the eye can actually separate.
 *
 * <p>Both lookups are memoised. Colouring runs once per marker per frame, and
 * the string work behind it must not.
 */
public final class Biomes {

    /** A colour band on the map, and a row in the breakdown. */
    public enum Family {
        PLAINS("biome_family.plains", 0xFF8DC46A),
        FOREST("biome_family.forest", 0xFF3F8F4A),
        TAIGA("biome_family.taiga", 0xFF2F7A6B),
        JUNGLE("biome_family.jungle", 0xFF4FB828),
        SAVANNA("biome_family.savanna", 0xFFC8A64B),
        DESERT("biome_family.desert", 0xFFE3D27A),
        BADLANDS("biome_family.badlands", 0xFFC7703C),
        SWAMP("biome_family.swamp", 0xFF5E7042),
        SNOWY("biome_family.snowy", 0xFFCFE4F2),
        MOUNTAIN("biome_family.mountain", 0xFF9AA6B4),
        BEACH("biome_family.beach", 0xFFE8D9A0),
        OCEAN("biome_family.ocean", 0xFF3C6FC4),
        RIVER("biome_family.river", 0xFF4FA3D1),
        CAVE("biome_family.cave", 0xFF7B5FA8),
        RARE("biome_family.rare", 0xFFE86FB0),
        NETHER("biome_family.nether", 0xFFD1503C),
        END("biome_family.end", 0xFFB07FD3),
        OTHER("biome_family.other", 0xFF8D97A6);

        private final String key;
        private final int color;

        Family(String key, int color) {
            this.key = key;
            this.color = color;
        }

        /** Translated at call time, so it follows a language change at once. */
        public String label() {
            return Lang.t(key);
        }

        public int color() {
            return color;
        }
    }

    /**
     * Family per biome path, resolved by substring rather than by an exhaustive
     * list: a table of every vanilla biome would be out of date one version
     * later, and the paths are descriptive enough to sort themselves. Order
     * matters - the first match wins, so the narrow rules sit above the broad
     * ones ({@code snowy_taiga} is snow before it is taiga).
     */
    private static final String[][] RULES = {
            {"mushroom", "RARE"},
            {"cherry", "RARE"},
            {"deep_dark", "CAVE"},
            {"lush_caves", "CAVE"},
            {"dripstone", "CAVE"},
            {"pale_garden", "RARE"},
            {"snowy", "SNOWY"},
            {"frozen", "SNOWY"},
            {"ice_spikes", "SNOWY"},
            // Above "grove", which this contains: a mangrove swamp is a swamp,
            // and the substring alone would paint it snow-white.
            {"mangrove", "SWAMP"},
            {"grove", "SNOWY"},
            {"jagged_peaks", "SNOWY"},
            {"frozen_peaks", "SNOWY"},
            {"snowy_slopes", "SNOWY"},
            {"badlands", "BADLANDS"},
            {"nether", "NETHER"},
            {"crimson", "NETHER"},
            {"warped", "NETHER"},
            {"soul_sand", "NETHER"},
            {"basalt", "NETHER"},
            // Anchored, not bare "end": that substring turns up inside plenty
            // of words a modded biome might use.
            {"the_end", "END"},
            {"end_", "END"},
            {"small_end", "END"},
            {"the_void", "OTHER"},
            {"ocean", "OCEAN"},
            {"river", "RIVER"},
            {"beach", "BEACH"},
            {"stony_shore", "BEACH"},
            {"swamp", "SWAMP"},
            {"jungle", "JUNGLE"},
            {"savanna", "SAVANNA"},
            {"desert", "DESERT"},
            {"taiga", "TAIGA"},
            {"windswept_forest", "MOUNTAIN"},
            {"windswept_hills", "MOUNTAIN"},
            {"windswept_gravelly", "MOUNTAIN"},
            {"stony_peaks", "MOUNTAIN"},
            {"meadow", "PLAINS"},
            {"windswept_savanna", "SAVANNA"},
            {"forest", "FOREST"},
            {"plains", "PLAINS"},
    };

    private static final Map<String, Family> FAMILIES = new HashMap<>();
    private static final Map<String, String> LABELS = new HashMap<>();

    private Biomes() {
    }

    /** The family a biome id belongs to. Null and unknown ids land in OTHER. */
    public static Family family(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return Family.OTHER;
        }
        Family cached = FAMILIES.get(biomeId);
        if (cached != null) {
            return cached;
        }
        Family found = classify(biomeId);
        FAMILIES.put(biomeId, found);
        return found;
    }

    public static int color(String biomeId) {
        return family(biomeId).color();
    }

    private static Family classify(String biomeId) {
        String path = biomeId.toLowerCase(Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        for (String[] rule : RULES) {
            if (path.contains(rule[0])) {
                return Family.valueOf(rule[1]);
            }
        }
        return Family.OTHER;
    }

    /**
     * The biome's name in the player's language, via Minecraft's own key.
     *
     * <p>Falls back to the bare path when the key resolves to itself, which is
     * what a modded biome with no translation does.
     */
    public static String label(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return Lang.t("word.unknown");
        }
        String cached = LABELS.get(biomeId);
        if (cached != null) {
            return cached;
        }
        String namespace = "minecraft";
        String path = biomeId;
        int colon = biomeId.indexOf(':');
        if (colon >= 0) {
            namespace = biomeId.substring(0, colon);
            path = biomeId.substring(colon + 1);
        }
        String key = "biome." + namespace + "." + path;
        String name;
        try {
            name = Text.translatable(key).getString();
        } catch (RuntimeException e) {
            name = key;
        }
        if (name.equals(key)) {
            name = prettify(path);
        }
        LABELS.put(biomeId, name);
        return name;
    }

    /** "old_growth_birch_forest" -> "Old Growth Birch Forest". */
    private static String prettify(String path) {
        StringBuilder text = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_') {
                text.append(' ');
                upper = true;
            } else {
                text.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return text.toString();
    }

    /** Called on a language change: the cached names are language-dependent. */
    public static void clearLabelCache() {
        LABELS.clear();
    }
}
