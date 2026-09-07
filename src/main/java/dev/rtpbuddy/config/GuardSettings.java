package dev.rtpbuddy.config;

import dev.rtpbuddy.util.Worlds;

/**
 * Border and spawn guards. These are advisory for mapping (they raise a HUD
 * warning) and enforced for the auto-RTP loop, which can be told to stop when
 * run outside the safe band.
 */
public class GuardSettings {

    /**
     * Overworld border radius in blocks, measured from
     * {@link #borderCenterX}/{@link #borderCenterZ}. Also the fallback for any
     * dimension that has no radius of its own.
     */
    public double borderRadius = 30_000_000.0;

    /**
     * Nether border radius. Servers rarely give the three dimensions the same
     * border - DonutSMP does not - and one radius drawn over all of them puts
     * the line in the wrong place in two of the three. 0 means "same as
     * {@link #borderRadius}".
     */
    public double netherBorderRadius = 0.0;

    /** End border radius. 0 means "same as {@link #borderRadius}". */
    public double endBorderRadius = 0.0;

    public double borderCenterX = 0.0;
    public double borderCenterZ = 0.0;

    /** Stay at least this far inside the border. */
    public double borderGuard = 1_000.0;

    public double spawnX = 0.0;
    public double spawnZ = 0.0;

    /** Stay at least this far away from spawn. */
    public double spawnGuard = 500.0;

    /** Treat the border as a square (vanilla style) rather than a circle. */
    public boolean squareBorder = true;

    /** The border radius that applies in a dimension, falling back to the overworld's. */
    public double radiusFor(String dimension) {
        double radius = switch (dimension == null ? "" : dimension) {
            case Worlds.NETHER -> netherBorderRadius;
            case Worlds.END -> endBorderRadius;
            default -> borderRadius;
        };
        return radius > 0 ? radius : borderRadius;
    }

    public boolean insideBorderGuard(String dimension, double x, double z) {
        return insideBorderGuard(radiusFor(dimension), x, z);
    }

    /** Overworld check, kept for callers that have no dimension to hand. */
    public boolean insideBorderGuard(double x, double z) {
        return insideBorderGuard(borderRadius, x, z);
    }

    private boolean insideBorderGuard(double borderRadius, double x, double z) {
        double limit = borderRadius - borderGuard;
        if (limit <= 0) {
            return false;
        }
        double dx = Math.abs(x - borderCenterX);
        double dz = Math.abs(z - borderCenterZ);
        return squareBorder
                ? dx <= limit && dz <= limit
                : Math.hypot(dx, dz) <= limit;
    }

    public boolean outsideSpawnGuard(double x, double z) {
        return Math.hypot(x - spawnX, z - spawnZ) >= spawnGuard;
    }

    public boolean isSafe(String dimension, double x, double z) {
        return insideBorderGuard(dimension, x, z) && outsideSpawnGuard(x, z);
    }

    public boolean isSafe(double x, double z) {
        return insideBorderGuard(x, z) && outsideSpawnGuard(x, z);
    }
}
