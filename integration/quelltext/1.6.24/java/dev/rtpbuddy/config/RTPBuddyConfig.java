package dev.rtpbuddy.config;

import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.region.ServerRegions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RTPBuddyConfig {

    /**
     * Bumped when a new release ships region presets an existing config has
     * never seen. {@code ConfigManager} merges the missing ones in on load - it
     * never replaces a region the player already has.
     */
    public static final int CURRENT_VERSION = 2;

    public int configVersion = 1;

    public CaptureConfig capture = new CaptureConfig();
    public GuardSettings guards = new GuardSettings();
    public AutoRtpConfig autoRtp = new AutoRtpConfig();
    public MapConfig map = new MapConfig();

    /** Which bundled preset seeded {@link #regions}. Purely informational after first run. */
    public String activePreset = "donutsmp";

    /**
     * Host substring that marks a server as using the DonutSMP region grid.
     * Blank turns the whole server-region layer off and leaves the region of a
     * landing as whatever the command asked for.
     */
    public String serverRegionHost = "donutsmp";

    public List<RegionPreset> regions = new ArrayList<>();

    /** Set once the legacy rtpmapper import has run, so it never runs twice. */
    public boolean legacyImportDone = false;
    public long legacyImportedAt = 0L;
    public int legacyImportedCount = 0;

    /**
     * The colour that stands for a region on the maps and in the statistics.
     *
     * <p>Falls back to a stable hue derived from the id, so regions a server
     * offers that are not in the preset still each get their own colour instead
     * of sharing one default.
     */
    public int regionColor(String id) {
        RegionPreset region = findRegion(id);
        if (region != null) {
            return region.color;
        }
        ServerRegions.Zone zone = ServerRegions.zone(id);
        return zone != null ? zone.color() : dev.rtpbuddy.ui.MapPalette.region(id);
    }

    /** The region's display name, or the raw id when nothing is configured for it. */
    public String regionLabel(String id) {
        RegionPreset region = findRegion(id);
        if (region != null && region.label != null && !region.label.isBlank()) {
            return region.label;
        }
        ServerRegions.Zone zone = ServerRegions.zone(id);
        return zone != null ? zone.label() : id;
    }

    /**
     * The server region a landing fell in, or null when the grid does not apply:
     * another server, the nether or the end, or a position outside the grid.
     *
     * <p>This is the one place RTPBuddy reads a region out of coordinates. It is
     * not a guess at what was asked for - {@code requestedRegion} still holds
     * that - it is where the server actually put the player, which on DonutSMP
     * is the thing the word region means.
     */
    public ServerRegions.Cell serverRegion(RtpSample sample) {
        return sample == null
                ? null
                : serverRegionAt(sample.server(), sample.dimension(), sample.x(), sample.z());
    }

    /**
     * The same lookup for a position that is not a recorded landing - the one the
     * player is standing on. The minimap needs it: it names the cell under the
     * player, and the player is not a sample.
     */
    public ServerRegions.Cell serverRegionAt(String server, String dimension, double x, double z) {
        if (serverRegionHost == null || serverRegionHost.isBlank()) {
            return null;
        }
        if (server == null
                || !server.toLowerCase(Locale.ROOT).contains(serverRegionHost.toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (!"minecraft:overworld".equals(dimension)) {
            return null;
        }
        return ServerRegions.cellAt(x, z);
    }

    /** What "region" means for one sample: the server region if there is one. */
    public String regionKey(RtpSample sample) {
        ServerRegions.Cell cell = serverRegion(sample);
        return cell == null ? sample.requestedRegion() : cell.zone().id();
    }

    public RegionPreset findRegion(String id) {
        for (RegionPreset region : regions) {
            if (region.id.equalsIgnoreCase(id)) {
                return region;
            }
        }
        return null;
    }

    /** First region whose pattern matches the command, or null. */
    public RegionPreset matchCommand(String command) {
        for (RegionPreset region : regions) {
            if (region.matches(command)) {
                return region;
            }
        }
        return null;
    }

    /** Fills in anything a hand-edited or partially-written config left null. */
    public void sanitise() {
        if (capture == null) capture = new CaptureConfig();
        if (guards == null) guards = new GuardSettings();
        if (autoRtp == null) autoRtp = new AutoRtpConfig();
        if (map == null) map = new MapConfig();
        if (regions == null) regions = new ArrayList<>();

        capture.armTimeoutTicks = Math.max(20, capture.armTimeoutTicks);
        capture.settleTicks = Math.max(0, capture.settleTicks);
        capture.saveDebounceMillis = Math.max(0, capture.saveDebounceMillis);
        capture.teleportDistanceThreshold = Math.max(0.0, capture.teleportDistanceThreshold);
        map.densityCellSize = Math.max(16, map.densityCellSize);
        autoRtp.cooldownSeconds = Math.max(AutoRtpConfig.MIN_COOLDOWN_SECONDS, autoRtp.cooldownSeconds);
        autoRtp.jitterSeconds = Math.max(0, autoRtp.jitterSeconds);
        autoRtp.maxPerSession = Math.max(0, autoRtp.maxPerSession);
        autoRtp.stopAfterMinutes = Math.max(0, autoRtp.stopAfterMinutes);
        if (autoRtp.regions == null) autoRtp.regions = new ArrayList<>();
        autoRtp.regions.removeIf(id -> id == null || id.isBlank());
        // A config file written by hand can carry an explicit null here, and a
        // picking of nothing is the same as no picking at all.
        if (map.filterSessions == null) map.filterSessions = new ArrayList<>();
        map.filterSessions.removeIf(id -> id == null || id.isBlank());
        if (map.filterSessions.isEmpty()) map.filterSessionsActive = false;
        if (autoRtp.order == null || autoRtp.order.isBlank()) autoRtp.order = "ROUND_ROBIN";
        if (capture.rtpCommandPattern == null || capture.rtpCommandPattern.isBlank()) {
            capture.rtpCommandPattern = new CaptureConfig().rtpCommandPattern;
        }

        regions.removeIf(r -> r == null || r.id == null);
        regions.forEach(RegionPreset::invalidate);
    }
}
