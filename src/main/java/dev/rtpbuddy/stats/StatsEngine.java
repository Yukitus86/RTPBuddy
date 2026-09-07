package dev.rtpbuddy.stats;

import dev.rtpbuddy.config.GuardSettings;
import dev.rtpbuddy.data.RtpSample;

import java.util.List;
import java.util.function.Supplier;

/**
 * Memoises the last {@link SampleStats} computation. The map screens recompute
 * only when their cache key changes - a filter edit, a new sample, a different
 * session selection - so panning and zooming stay allocation-free.
 */
public class StatsEngine {

    private String cacheKey;
    private SampleStats cached = SampleStats.empty();

    public SampleStats get(String key, Supplier<List<RtpSample>> samples,
                           GuardSettings guards, int cellSize) {
        if (key.equals(cacheKey)) {
            return cached;
        }
        cached = SampleStats.of(samples.get(), guards, cellSize);
        cacheKey = key;
        return cached;
    }

    public SampleStats last() {
        return cached;
    }

    public void invalidate() {
        cacheKey = null;
    }
}
