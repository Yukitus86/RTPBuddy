package dev.rtpbuddy.capture;

import dev.rtpbuddy.config.AutoRtpConfig;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.stats.CellCoverage;
import dev.rtpbuddy.util.Biomes;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a landing is the one the run was looking for.
 *
 * <p>Auto-RTP already stops for a list of reasons - damage, the run timer, the
 * session cap, a guard - and every one of them is a reason to give up. This is
 * the other kind: a reason to stop because the loop found what it was sent out
 * for. A run with a search order attached keeps teleporting until a landing
 * matches, then stops on that landing and says which condition fired.
 *
 * <p>Deliberately free of every Minecraft import so the rule can be exercised
 * on its own. It decides nothing about sending; it only reads a landing that
 * has already been recorded and answers yes or no.
 */
public final class FindRule {

    /**
     * @param reasonKey translation key naming the condition that fired
     * @param args      arguments for that key
     */
    public record Match(String reasonKey, Object... args) {
    }

    private FindRule() {
    }

    /** True when the search order has at least one condition switched on. */
    public static boolean armed(AutoRtpConfig auto) {
        if (!auto.findEnabled) {
            return false;
        }
        return auto.findNewCell
                || !parseCells(auto.findCells).isEmpty()
                || !auto.findFamilies.isEmpty()
                || !auto.findBiomes.isEmpty()
                || auto.findMinDistance > 0
                || auto.findMaxDistance > 0
                || auto.findNearRadius > 0;
    }

    /**
     * Tests one landing against the search order.
     *
     * @param coverage the board <em>including</em> this landing, so a cell it
     *                 reached for the first time reads as a count of one
     * @param cellNumber the landing's cell, or 0 when it fell off the grid
     * @return the condition that fired, or null when none did
     */
    public static Match test(AutoRtpConfig auto, RtpSample sample,
                             CellCoverage coverage, int cellNumber) {
        if (!armed(auto)) {
            return null;
        }
        boolean all = auto.findMatchAll;
        Match first = null;

        if (auto.findNewCell) {
            // A count of exactly one means this landing is the only one that
            // cell has ever held, which is the same thing as "never been here".
            boolean hit = cellNumber > 0 && coverage.count(cellNumber) == 1;
            if (!hit && all) {
                return null;
            }
            if (hit && first == null) {
                first = new Match("find.new_cell", cellNumber);
            }
        }

        List<Integer> wanted = parseCells(auto.findCells);
        if (!wanted.isEmpty()) {
            boolean hit = cellNumber > 0 && wanted.contains(cellNumber);
            if (!hit && all) {
                return null;
            }
            if (hit && first == null) {
                first = new Match("find.cell", cellNumber);
            }
        }

        if (!auto.findFamilies.isEmpty()) {
            Biomes.Family family = Biomes.family(sample.biome());
            boolean hit = containsIgnoreCase(auto.findFamilies, family.name());
            if (!hit && all) {
                return null;
            }
            if (hit && first == null) {
                first = new Match("find.family", family.label());
            }
        }

        if (!auto.findBiomes.isEmpty()) {
            boolean hit = sample.biome() != null
                    && containsIgnoreCase(auto.findBiomes, sample.biome());
            if (!hit && all) {
                return null;
            }
            if (hit && first == null) {
                first = new Match("find.biome", Biomes.label(sample.biome()));
            }
        }

        if (auto.findMinDistance > 0 || auto.findMaxDistance > 0) {
            double distance = sample.distanceFromOrigin();
            boolean hit = distance >= auto.findMinDistance
                    && (auto.findMaxDistance <= 0 || distance <= auto.findMaxDistance);
            if (!hit && all) {
                return null;
            }
            if (hit && first == null) {
                first = new Match("find.distance", Math.round(distance));
            }
        }

        if (auto.findNearRadius > 0) {
            double distance = sample.distanceTo(auto.findNearX, auto.findNearZ);
            boolean hit = distance <= auto.findNearRadius;
            if (!hit && all) {
                return null;
            }
            if (hit && first == null) {
                first = new Match("find.near", Math.round(distance),
                        Math.round(auto.findNearX), Math.round(auto.findNearZ));
            }
        }

        return first;
    }

    /**
     * Cell numbers from a typed list: "11, 12 20" is three cells.
     *
     * <p>Anything that is not a number in range is dropped rather than
     * rejected - the field is typed into, and a half-finished entry must not
     * make the whole order stop working.
     */
    public static List<Integer> parseCells(String text) {
        List<Integer> numbers = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            return numbers;
        }
        for (String part : text.split("[^0-9]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                int number = Integer.parseInt(part);
                if (number >= 1 && number <= CellCoverage.totalCells()
                        && !numbers.contains(number)) {
                    numbers.add(number);
                }
            } catch (NumberFormatException ignored) {
                // Not a cell number; skip it.
            }
        }
        return numbers;
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        if (needle == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT)
                    .equals(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** One-line summary of the armed conditions, for the dialog and the HUD. */
    public static String describe(AutoRtpConfig auto) {
        if (!armed(auto)) {
            return "";
        }
        List<String> parts = new java.util.ArrayList<>();
        if (auto.findNewCell) {
            parts.add(dev.rtpbuddy.util.Lang.t("find.short.new_cell"));
        }
        List<Integer> cells = parseCells(auto.findCells);
        if (!cells.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (int number : cells) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append('#').append(number);
            }
            parts.add(text.toString());
        }
        for (String name : auto.findFamilies) {
            try {
                parts.add(Biomes.Family.valueOf(name.toUpperCase(Locale.ROOT)).label());
            } catch (IllegalArgumentException ignored) {
                // A family that no longer exists; leave it out of the summary.
            }
        }
        for (String id : auto.findBiomes) {
            parts.add(Biomes.label(id));
        }
        if (auto.findMinDistance > 0 || auto.findMaxDistance > 0) {
            parts.add(dev.rtpbuddy.util.Lang.t("find.short.distance",
                    dev.rtpbuddy.util.Numbers.compact(auto.findMinDistance),
                    auto.findMaxDistance <= 0
                            ? dev.rtpbuddy.util.Lang.t("filter.infinity")
                            : dev.rtpbuddy.util.Numbers.compact(auto.findMaxDistance)));
        }
        if (auto.findNearRadius > 0) {
            parts.add(dev.rtpbuddy.util.Lang.t("find.short.near",
                    Math.round(auto.findNearX), Math.round(auto.findNearZ),
                    dev.rtpbuddy.util.Numbers.compact(auto.findNearRadius)));
        }
        String joined = String.join(auto.findMatchAll
                ? dev.rtpbuddy.util.Lang.t("find.join_all")
                : dev.rtpbuddy.util.Lang.t("find.join_any"), parts);
        return joined;
    }
}
