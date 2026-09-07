package dev.rtpbuddy.util;

import java.math.BigDecimal;
import java.util.Locale;

public final class Numbers {

    private Numbers() {
    }

    /** Empty string for an unrecorded value. */
    public static String plain(Double value) {
        return value == null ? "" : plain(value.doubleValue());
    }

    /** Full round-trip precision, never scientific notation (CSV-safe). */
    public static String plain(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Fixed-decimal rendering for UI labels. */
    public static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** Compact distances: 1234 -> "1.2k", 1234567 -> "1.23M". */
    public static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000) {
            return fixed(value / 1_000_000, 2) + "M";
        }
        if (abs >= 1_000) {
            return fixed(value / 1_000, 1) + "k";
        }
        // Grid and axis labels read badly as "0.0"; only show a decimal when
        // there actually is a fractional part.
        boolean whole = value == Math.rint(value);
        return fixed(value, abs < 10 && !whole ? 1 : 0);
    }

    public static String duration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", h, m, s);
        }
        if (m > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", m, s);
        }
        return s + "s";
    }
}
