package dev.rtpbuddy.stats;

import dev.rtpbuddy.util.Numbers;

/**
 * Fixed-bucket histogram with pre-computed labels, ready to be drawn as a bar
 * chart without any further formatting work on the render thread.
 */
public class Histogram {

    private final double min;
    private final double bucketWidth;
    private final int[] counts;
    private final String[] labels;
    private int total;
    private int peak;

    public Histogram(double min, double max, int buckets, java.util.function.DoubleFunction<String> labeller) {
        int safeBuckets = Math.max(1, buckets);
        this.min = min;
        double span = Math.max(1e-9, max - min);
        this.bucketWidth = span / safeBuckets;
        this.counts = new int[safeBuckets];
        this.labels = new String[safeBuckets];
        for (int i = 0; i < safeBuckets; i++) {
            labels[i] = labeller.apply(min + i * bucketWidth);
        }
    }

    /** Histogram over a numeric range with compact numeric labels. */
    public static Histogram linear(double min, double max, int buckets) {
        return new Histogram(min, max, buckets, Numbers::compact);
    }

    /** Sixteen compass sectors, used for the direction rose. */
    public static Histogram compass() {
        String[] names = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        Histogram histogram = new Histogram(0.0, 360.0, 16, value -> "");
        for (int i = 0; i < names.length; i++) {
            histogram.labels[i] = names[i];
        }
        return histogram;
    }

    public void add(double value) {
        int index = (int) Math.floor((value - min) / bucketWidth);
        if (index < 0) {
            index = 0;
        } else if (index >= counts.length) {
            index = counts.length - 1;
        }
        counts[index]++;
        total++;
        peak = Math.max(peak, counts[index]);
    }

    public int buckets() {
        return counts.length;
    }

    public int count(int bucket) {
        return counts[bucket];
    }

    public String label(int bucket) {
        return labels[bucket];
    }

    public double lowerBound(int bucket) {
        return min + bucket * bucketWidth;
    }

    public double upperBound(int bucket) {
        return min + (bucket + 1) * bucketWidth;
    }

    public int total() {
        return total;
    }

    public int peak() {
        return peak;
    }

    /** Bucket height as a 0..1 fraction of the tallest bucket. */
    public double fraction(int bucket) {
        return peak == 0 ? 0.0 : (double) counts[bucket] / peak;
    }

    /** Bucket share as a 0..1 fraction of all samples. */
    public double share(int bucket) {
        return total == 0 ? 0.0 : (double) counts[bucket] / total;
    }

    /**
     * Chi-square statistic against a flat expectation. Meaningful only when every
     * bucket is expected to hold the same number of samples, which is why the
     * radial buckets are built with equal area rather than equal width.
     */
    public double chiSquareAgainstUniform() {
        if (total == 0 || counts.length < 2) {
            return 0.0;
        }
        double expected = (double) total / counts.length;
        double sum = 0.0;
        for (int count : counts) {
            double diff = count - expected;
            sum += diff * diff / expected;
        }
        return sum;
    }

    public int degreesOfFreedom() {
        return Math.max(0, counts.length - 1);
    }
}
