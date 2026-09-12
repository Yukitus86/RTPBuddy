package dev.rtpbuddy.data;

/**
 * One recorded random-teleport landing (schema 3).
 *
 * <p>Naming note: {@code distanceFromOrigin} means distance from the world
 * origin (0,0) and is the column the v2 CSV contract already promised. The
 * player's position <em>before</em> the teleport is stored separately as the
 * {@code from*} fields.
 *
 * <p>The optional fields are boxed and nullable rather than using a sentinel:
 * NaN is not representable in JSON at all, and 0 or -1 are legitimate values for
 * a coordinate, a distance and a latency. Null means "not recorded", which is
 * exactly what a manual capture or a legacy import produces.
 */
public record RtpSample(
        int sample,
        String sessionId,
        double x,
        double y,
        double z,
        String dimension,
        long timestamp,
        String requestedRegion,
        String category,
        Double fromX,
        Double fromY,
        Double fromZ,
        String fromDimension,
        Double travelDistance,
        Long latencyMs,
        String biome,
        Integer surfaceY,
        String captureMode,
        String server,
        String note
) {

    public static final String CAPTURE_AUTO = "auto";
    public static final String CAPTURE_MANUAL = "manual";
    public static final String CAPTURE_IMPORTED = "imported";
    public static final String REGION_UNKNOWN = "unknown";

    /** Horizontal distance from the world origin. */
    public double distanceFromOrigin() {
        return Math.hypot(x, z);
    }

    public double distanceTo(double otherX, double otherZ) {
        return Math.hypot(x - otherX, z - otherZ);
    }

    /** Compass bearing from the origin in degrees, 0 = north (-Z), clockwise. */
    public double bearingFromOrigin() {
        double deg = Math.toDegrees(Math.atan2(x, -z));
        return deg < 0 ? deg + 360.0 : deg;
    }

    public boolean hasNote() {
        return note != null && !note.isBlank();
    }

    public RtpSample withNote(String newNote) {
        return new RtpSample(sample, sessionId, x, y, z, dimension, timestamp, requestedRegion,
                category, fromX, fromY, fromZ, fromDimension, travelDistance, latencyMs,
                biome, surfaceY, captureMode, server, newNote);
    }

    /** Restamps the landing onto another sitting. Used when sittings are merged. */
    public RtpSample withSession(String newSessionId) {
        return new RtpSample(sample, newSessionId, x, y, z, dimension, timestamp, requestedRegion,
                category, fromX, fromY, fromZ, fromDimension, travelDistance, latencyMs,
                biome, surfaceY, captureMode, server, note);
    }

    public RtpSample withSample(int newSampleNumber) {
        return new RtpSample(newSampleNumber, sessionId, x, y, z, dimension, timestamp, requestedRegion,
                category, fromX, fromY, fromZ, fromDimension, travelDistance, latencyMs,
                biome, surfaceY, captureMode, server, note);
    }

    /** Replaces nulls left by hand-edited or imported JSON. */
    public RtpSample normalised() {
        return new RtpSample(
                sample,
                sessionId == null ? SessionRecord.LEGACY_ID : sessionId,
                x, y, z,
                dimension == null ? "minecraft:overworld" : dimension,
                timestamp,
                requestedRegion == null ? REGION_UNKNOWN : requestedRegion,
                category == null ? "rtp" : category,
                fromX, fromY, fromZ,
                fromDimension,
                travelDistance,
                latencyMs,
                biome,
                surfaceY,
                captureMode == null ? CAPTURE_IMPORTED : captureMode,
                server == null ? "" : server,
                note
        );
    }
}
