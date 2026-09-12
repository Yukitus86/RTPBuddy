package dev.rtpbuddy.api;

/**
 * One recorded landing, as another mod sees it.
 *
 * <p>Deliberately not the mod's own {@code RtpSample}. That record is the
 * storage schema: it grows a field whenever something new is worth writing to
 * disk, and it carries the shapes that only the writer cares about. A partner
 * mod compiled against it would have to be rebuilt for changes that have
 * nothing to do with it. This is the narrower thing - what a landing
 * <em>is</em>, not how it is stored - and it only ever gains accessors.
 *
 * <p>The optional values are boxed and may be null, which means "not recorded"
 * rather than zero: a manual capture has no latency and no origin to measure
 * from, and zero is a legitimate reading for both.
 */
public record Landing(
        int number,
        String sittingId,
        double x,
        double y,
        double z,
        String dimension,
        long timestamp,
        String requestedRegion,
        Double travelDistance,
        Long latencyMillis,
        String biome,
        Integer surfaceY,
        boolean manual,
        String server,
        String note) {

    /** Horizontal distance from the world origin. */
    public double distanceFromOrigin() {
        return Math.hypot(x, z);
    }

    /** Horizontal distance to any other point. */
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

    /** True while the player was on a server rather than in a local world. */
    public boolean onServer() {
        return server != null && !server.isBlank();
    }
}
