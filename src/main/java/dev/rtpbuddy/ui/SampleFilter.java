package dev.rtpbuddy.ui;

import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.util.Lang;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The filter shared by both map screens. Also produces the cache key the stats
 * engine uses, so stats recompute exactly when the visible set changes.
 */
public class SampleFilter {

    /** null means "every dimension". */
    public String dimension;
    public String region;
    public String server;
    public String captureMode;

    /**
     * Biome family to narrow to, by {@code Biomes.Family} name, or null for all.
     *
     * <p>A family rather than a single biome, because the button that drives it
     * is a cycle: close to fifty biomes are recorded and stepping through them
     * one press at a time is not a filter anyone would use. The exact biome is
     * reachable through the search box, which matches both the id and the name
     * the game shows.
     *
     * <p>Held as the family name and never as the shown label - the label comes
     * from a translation and would stop matching on a language change.
     */
    public String biomeFamily;

    public double minDistance;
    public double maxDistance = Double.MAX_VALUE;

    public long fromTimestamp;
    public long toTimestamp = Long.MAX_VALUE;

    /**
     * Session visibility, used by the all-session screen. Only consulted when
     * {@link #sessionFilterActive} is set, so an empty set can mean "show none"
     * rather than being indistinguishable from "no session filter at all".
     */
    public final Set<String> visibleSessions = new HashSet<>();
    public boolean sessionFilterActive;

    /** Free-text match against sample number, region, biome, dimension and note. */
    public String search = "";

    /**
     * How the screen numbers a landing right now, or null for the global number
     * only. Searching has to find what is on the row: with the map narrowed to
     * one sitting the list reads "#3", and typing 3 must reach it even though
     * that landing is #297 in the file. Both numbers match, so neither way of
     * remembering a sample comes up empty.
     */
    public java.util.function.ToIntFunction<RtpSample> displayNumber;

    public boolean matches(RtpSample sample) {
        if (dimension != null && !dimension.equals(sample.dimension())) {
            return false;
        }
        if (region != null && !region.equalsIgnoreCase(sample.requestedRegion())) {
            return false;
        }
        if (captureMode != null && !captureMode.equalsIgnoreCase(sample.captureMode())) {
            return false;
        }
        if (biomeFamily != null
                && !biomeFamily.equalsIgnoreCase(
                        dev.rtpbuddy.util.Biomes.family(sample.biome()).name())) {
            return false;
        }
        if (server != null) {
            String actual = sample.server() == null || sample.server().isBlank() ? "singleplayer" : sample.server();
            if (!server.equals(actual)) {
                return false;
            }
        }
        if (sessionFilterActive && !visibleSessions.contains(sample.sessionId())) {
            return false;
        }
        double distance = sample.distanceFromOrigin();
        if (distance < minDistance || distance > maxDistance) {
            return false;
        }
        if (sample.timestamp() < fromTimestamp || sample.timestamp() > toTimestamp) {
            return false;
        }
        return matchesSearch(sample);
    }

    private boolean matchesSearch(RtpSample sample) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        if (String.valueOf(sample.sample()).contains(needle)) {
            return true;
        }
        if (displayNumber != null
                && String.valueOf(displayNumber.applyAsInt(sample)).contains(needle)) {
            return true;
        }
        if (contains(sample.requestedRegion(), needle)
                || contains(sample.dimension(), needle)
                || contains(sample.biome(), needle)
                // Also the name the game shows, so typing "Wald" finds what is
                // stored as minecraft:forest.
                || contains(dev.rtpbuddy.util.Biomes.label(sample.biome()), needle)
                || contains(sample.note(), needle)
                || contains(sample.server(), needle)) {
            return true;
        }
        return false;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    public List<RtpSample> apply(List<RtpSample> samples) {
        List<RtpSample> result = new ArrayList<>(Math.min(samples.size(), 256));
        for (RtpSample sample : samples) {
            if (matches(sample)) {
                result.add(sample);
            }
        }
        return result;
    }

    public boolean isDefault() {
        return dimension == null && region == null && server == null && captureMode == null
                && biomeFamily == null
                && minDistance == 0 && maxDistance == Double.MAX_VALUE
                && fromTimestamp == 0 && toTimestamp == Long.MAX_VALUE
                && !sessionFilterActive && (search == null || search.isBlank());
    }

    public void clear() {
        dimension = null;
        region = null;
        server = null;
        captureMode = null;
        biomeFamily = null;
        minDistance = 0;
        maxDistance = Double.MAX_VALUE;
        fromTimestamp = 0;
        toTimestamp = Long.MAX_VALUE;
        visibleSessions.clear();
        sessionFilterActive = false;
        search = "";
    }

    /** Short human summary for the filter bar. */
    public String describe() {
        if (isDefault()) {
            return Lang.t("filter.none");
        }
        List<String> parts = new ArrayList<>();
        if (dimension != null) {
            parts.add(dev.rtpbuddy.util.Worlds.dimensionLabel(dimension));
        }
        if (region != null) {
            parts.add(Lang.t("filter.region",
                    dev.rtpbuddy.RTPBuddyClient.config().regionLabel(region)));
        }
        if (server != null) {
            parts.add(server);
        }
        if (captureMode != null) {
            parts.add(captureMode);
        }
        if (biomeFamily != null) {
            parts.add(dev.rtpbuddy.util.Biomes.Family.valueOf(biomeFamily).label());
        }
        if (minDistance > 0 || maxDistance < Double.MAX_VALUE) {
            parts.add(Lang.t("filter.distance", dev.rtpbuddy.util.Numbers.compact(minDistance),
                    maxDistance == Double.MAX_VALUE
                            ? Lang.t("filter.infinity")
                            : dev.rtpbuddy.util.Numbers.compact(maxDistance)));
        }
        if (fromTimestamp > 0 || toTimestamp < Long.MAX_VALUE) {
            parts.add(Lang.t("filter.time_range"));
        }
        if (sessionFilterActive) {
            parts.add(Lang.t("filter.sessions", visibleSessions.size()));
        }
        if (search != null && !search.isBlank()) {
            parts.add("\"" + search + "\"");
        }
        return String.join(", ", parts);
    }

    /** Identity of the current filter, used to invalidate memoised stats. */
    public String cacheKey() {
        return dimension + "|" + region + "|" + server + "|" + captureMode + "|" + biomeFamily + "|"
                + minDistance + "|" + maxDistance + "|" + fromTimestamp + "|" + toTimestamp + "|"
                + sessionFilterActive + ":" + visibleSessions.size() + ":" + visibleSessions.hashCode()
                + "|" + search;
    }
}
