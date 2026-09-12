package dev.rtpbuddy.stats;

import dev.rtpbuddy.config.GuardSettings;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.util.Numbers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the map screens display about a set of samples. Computed once per
 * filter change and then only read, so the render loop never does arithmetic
 * over the whole dataset.
 */
public final class SampleStats {

    /** Above this many samples the O(n^2) nearest/farthest pair scan is skipped. */
    private static final int PAIRWISE_LIMIT = 3_000;

    public final int count;

    public final long firstTimestamp;
    public final long lastTimestamp;
    public final long spanMillis;
    public final double samplesPerMinute;
    public final double meanLatencyMillis;
    public final int latencySamples;

    // Distance from the world origin.
    public final double distMin;
    public final double distMax;
    public final double distMean;
    public final double distMedian;
    public final double distP90;
    public final double distStdDev;

    // Jump distance between consecutive samples in recording order.
    public final double jumpMin;
    public final double jumpMax;
    public final double jumpMean;
    public final int jumpSamples;

    // Extent.
    public final double minX;
    public final double maxX;
    public final double minZ;
    public final double maxZ;
    public final double centroidX;
    public final double centroidZ;

    // Quadrants, in NE / NW / SE / SW order (north = -Z).
    public final int[] quadrants;

    public final Histogram radial;
    public final Histogram angular;
    public final Histogram yLevels;

    public final Map<String, Integer> byDimension;
    public final Map<String, Integer> byRegion;
    public final Map<String, Integer> byServer;
    public final Map<String, Integer> byCaptureMode;

    /**
     * Landings per biome id and per biome family, both ordered by count.
     *
     * <p>The biome has been on every sample since schema 3 and was never shown
     * anywhere. Ordered rather than insertion-ordered like the others because
     * the recorded data holds close to fifty biomes: the interesting rows are
     * the busiest few and the single-landing tail, and neither is findable in
     * the order the samples happened to arrive.
     */
    public final Map<String, Integer> byBiome;
    public final Map<String, Integer> byBiomeFamily;

    /** How much of the server's cell grid this set of landings has reached. */
    public final CellCoverage cells;

    public final DensityGrid density;
    public final double coverage;

    public final boolean pairwiseComputed;
    public final double nearestPairDistance;
    public final int nearestPairA;
    public final int nearestPairB;
    public final double farthestPairDistance;
    public final int farthestPairA;
    public final int farthestPairB;

    /** Chi-square against an even spread over the sampled disc. Lower is more uniform. */
    public final double radialChiSquare;
    public final double angularChiSquare;

    public static SampleStats empty() {
        return new SampleStats(List.of(), new GuardSettings(), 512);
    }

    /**
     * How a sample's region is decided. The client points this at the config on
     * start-up, so a landing on a server with a region grid counts under the
     * region it actually fell in rather than the one the command asked for. The
     * default keeps the class usable on its own.
     */
    private static java.util.function.Function<RtpSample, String> regionKey = RtpSample::requestedRegion;

    public static void setRegionKey(java.util.function.Function<RtpSample, String> resolver) {
        regionKey = resolver == null ? RtpSample::requestedRegion : resolver;
    }

    public static SampleStats of(List<RtpSample> samples, GuardSettings guards, int cellSize) {
        return new SampleStats(samples, guards, cellSize);
    }

    private SampleStats(List<RtpSample> input, GuardSettings guards, int cellSize) {
        List<RtpSample> samples = new ArrayList<>(input);
        samples.sort(Comparator.comparingInt(RtpSample::sample));

        this.count = samples.size();
        this.cells = CellCoverage.of(samples);
        this.density = DensityGrid.of(samples, cellSize);
        this.coverage = density.coverage(guards.borderRadius, guards.borderGuard, guards.squareBorder);

        if (samples.isEmpty()) {
            firstTimestamp = 0;
            lastTimestamp = 0;
            spanMillis = 0;
            samplesPerMinute = 0;
            meanLatencyMillis = Double.NaN;
            latencySamples = 0;
            distMin = distMax = distMean = distMedian = distP90 = distStdDev = Double.NaN;
            jumpMin = jumpMax = jumpMean = Double.NaN;
            jumpSamples = 0;
            minX = maxX = minZ = maxZ = centroidX = centroidZ = Double.NaN;
            quadrants = new int[4];
            radial = Histogram.linear(0, 1, 1);
            angular = Histogram.compass();
            yLevels = Histogram.linear(0, 1, 1);
            byDimension = Map.of();
            byRegion = Map.of();
            byServer = Map.of();
            byCaptureMode = Map.of();
            byBiome = Map.of();
            byBiomeFamily = Map.of();
            pairwiseComputed = false;
            nearestPairDistance = farthestPairDistance = Double.NaN;
            nearestPairA = nearestPairB = farthestPairA = farthestPairB = -1;
            radialChiSquare = angularChiSquare = Double.NaN;
            return;
        }

        // --- time ------------------------------------------------------------
        long first = Long.MAX_VALUE;
        long last = 0L;
        long latencyTotal = 0L;
        int latencyCount = 0;
        for (RtpSample s : samples) {
            if (s.timestamp() > 0) {
                first = Math.min(first, s.timestamp());
                last = Math.max(last, s.timestamp());
            }
            if (s.latencyMs() != null) {
                latencyTotal += s.latencyMs();
                latencyCount++;
            }
        }
        firstTimestamp = first == Long.MAX_VALUE ? 0L : first;
        lastTimestamp = last;
        spanMillis = Math.max(0L, lastTimestamp - firstTimestamp);
        samplesPerMinute = spanMillis > 0 ? count / (spanMillis / 60_000.0) : 0.0;
        latencySamples = latencyCount;
        meanLatencyMillis = latencyCount > 0 ? (double) latencyTotal / latencyCount : Double.NaN;

        // --- distance from origin -------------------------------------------
        double[] distances = new double[count];
        double distSum = 0.0;
        double loX = Double.MAX_VALUE;
        double hiX = -Double.MAX_VALUE;
        double loZ = Double.MAX_VALUE;
        double hiZ = -Double.MAX_VALUE;
        double sumX = 0.0;
        double sumZ = 0.0;
        int[] quads = new int[4];

        for (int i = 0; i < count; i++) {
            RtpSample s = samples.get(i);
            double d = s.distanceFromOrigin();
            distances[i] = d;
            distSum += d;
            loX = Math.min(loX, s.x());
            hiX = Math.max(hiX, s.x());
            loZ = Math.min(loZ, s.z());
            hiZ = Math.max(hiZ, s.z());
            sumX += s.x();
            sumZ += s.z();
            quads[quadrantOf(s.x(), s.z())]++;
        }

        double[] sorted = distances.clone();
        java.util.Arrays.sort(sorted);
        distMin = sorted[0];
        distMax = sorted[count - 1];
        distMean = distSum / count;
        distMedian = percentile(sorted, 0.50);
        distP90 = percentile(sorted, 0.90);

        double varianceSum = 0.0;
        for (double d : distances) {
            double diff = d - distMean;
            varianceSum += diff * diff;
        }
        distStdDev = Math.sqrt(varianceSum / count);

        minX = loX;
        maxX = hiX;
        minZ = loZ;
        maxZ = hiZ;
        centroidX = sumX / count;
        centroidZ = sumZ / count;
        quadrants = quads;

        // --- consecutive jumps ------------------------------------------------
        double jLo = Double.MAX_VALUE;
        double jHi = 0.0;
        double jSum = 0.0;
        int jCount = 0;
        for (int i = 1; i < count; i++) {
            RtpSample a = samples.get(i - 1);
            RtpSample b = samples.get(i);
            if (!a.dimension().equals(b.dimension())) {
                continue;
            }
            double jump = Math.hypot(b.x() - a.x(), b.z() - a.z());
            jLo = Math.min(jLo, jump);
            jHi = Math.max(jHi, jump);
            jSum += jump;
            jCount++;
        }
        jumpSamples = jCount;
        jumpMin = jCount > 0 ? jLo : Double.NaN;
        jumpMax = jCount > 0 ? jHi : Double.NaN;
        jumpMean = jCount > 0 ? jSum / jCount : Double.NaN;

        // --- histograms -------------------------------------------------------
        // Radial buckets are built over r^2 so every annulus covers the same area.
        // That is what makes the chi-square below a fair uniformity test: a truly
        // uniform RTP fills equal-area rings equally, not equal-width ones.
        double maxRadiusSquared = Math.max(1.0, distMax * distMax);
        radial = new Histogram(0.0, maxRadiusSquared, 12,
                value -> Numbers.compact(Math.sqrt(Math.max(0.0, value))));
        angular = Histogram.compass();

        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (RtpSample s : samples) {
            minY = Math.min(minY, s.y());
            maxY = Math.max(maxY, s.y());
        }
        yLevels = new Histogram(minY, Math.max(minY + 1, maxY), 12, v -> Numbers.fixed(v, 0));

        Map<String, Integer> dims = new LinkedHashMap<>();
        Map<String, Integer> regions = new LinkedHashMap<>();
        Map<String, Integer> servers = new LinkedHashMap<>();
        Map<String, Integer> modes = new TreeMap<>();
        Map<String, Integer> biomes = new LinkedHashMap<>();
        Map<String, Integer> families = new LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            RtpSample s = samples.get(i);
            radial.add(distances[i] * distances[i]);
            angular.add(s.bearingFromOrigin());
            yLevels.add(s.y());
            dims.merge(s.dimension(), 1, Integer::sum);
            regions.merge(regionKey.apply(s), 1, Integer::sum);
            servers.merge(s.server() == null || s.server().isBlank() ? "singleplayer" : s.server(), 1, Integer::sum);
            modes.merge(s.captureMode(), 1, Integer::sum);
            if (s.biome() != null && !s.biome().isBlank()) {
                biomes.merge(s.biome(), 1, Integer::sum);
                families.merge(dev.rtpbuddy.util.Biomes.family(s.biome()).name(), 1, Integer::sum);
            }
        }

        byDimension = dims;
        byRegion = regions;
        byServer = servers;
        byCaptureMode = modes;
        byBiome = sortedByCount(biomes);
        byBiomeFamily = sortedByCount(families);

        radialChiSquare = radial.chiSquareAgainstUniform();
        angularChiSquare = angular.chiSquareAgainstUniform();

        // --- pairwise extremes -------------------------------------------------
        if (count >= 2 && count <= PAIRWISE_LIMIT) {
            double near = Double.MAX_VALUE;
            double far = -1.0;
            int nA = -1;
            int nB = -1;
            int fA = -1;
            int fB = -1;
            for (int i = 0; i < count; i++) {
                RtpSample a = samples.get(i);
                for (int j = i + 1; j < count; j++) {
                    RtpSample b = samples.get(j);
                    if (!a.dimension().equals(b.dimension())) {
                        continue;
                    }
                    double d = Math.hypot(a.x() - b.x(), a.z() - b.z());
                    if (d < near) {
                        near = d;
                        nA = a.sample();
                        nB = b.sample();
                    }
                    if (d > far) {
                        far = d;
                        fA = a.sample();
                        fB = b.sample();
                    }
                }
            }
            pairwiseComputed = nA >= 0;
            nearestPairDistance = pairwiseComputed ? near : Double.NaN;
            farthestPairDistance = pairwiseComputed ? far : Double.NaN;
            nearestPairA = nA;
            nearestPairB = nB;
            farthestPairA = fA;
            farthestPairB = fB;
        } else {
            pairwiseComputed = false;
            nearestPairDistance = Double.NaN;
            farthestPairDistance = Double.NaN;
            nearestPairA = nearestPairB = farthestPairA = farthestPairB = -1;
        }
    }

    public double width() {
        return maxX - minX;
    }

    public double depth() {
        return maxZ - minZ;
    }

    /** Bounding-box area in blocks squared. */
    public double boundingArea() {
        return width() * depth();
    }

    /** Quadrant share as a percentage, index order NE / NW / SE / SW. */
    public double quadrantShare(int index) {
        return count == 0 ? 0.0 : 100.0 * quadrants[index] / count;
    }

    /**
     * Plain-language reading of the radial chi-square. Twelve equal-area buckets
     * give 11 degrees of freedom, whose 95% critical value is about 19.7.
     */
    public String uniformityVerdict() {
        if (count < 24 || Double.isNaN(radialChiSquare)) {
            return "too few samples to judge";
        }
        if (radialChiSquare < 11.0) {
            return "consistent with uniform";
        }
        if (radialChiSquare < 19.7) {
            return "slightly uneven";
        }
        if (radialChiSquare < 40.0) {
            return "clearly biased by distance";
        }
        return "strongly biased by distance";
    }

    /**
     * The same verdict as a translation key. Kept separate from
     * {@link #uniformityVerdict()} so this class stays free of Minecraft
     * imports and can still be exercised by the headless test harness.
     */
    public String uniformityVerdictKey() {
        if (count < 24 || Double.isNaN(radialChiSquare)) {
            return "verdict.too_few";
        }
        if (radialChiSquare < 11.0) {
            return "verdict.uniform";
        }
        if (radialChiSquare < 19.7) {
            return "verdict.slight";
        }
        if (radialChiSquare < 40.0) {
            return "verdict.clear";
        }
        return "verdict.strong";
    }

    /** Same map, walked busiest first. Ties keep the order they arrived in. */
    private static Map<String, Integer> sortedByCount(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    private static int quadrantOf(double x, double z) {
        boolean north = z < 0;
        boolean east = x >= 0;
        if (north) {
            return east ? 0 : 1;
        }
        return east ? 2 : 3;
    }

    private static double percentile(double[] sorted, double fraction) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        double position = fraction * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        double weight = position - lower;
        return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    }
}
