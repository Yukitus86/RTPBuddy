package dev.rtpbuddy.data;

import dev.rtpbuddy.util.Numbers;

import java.util.List;

/**
 * CSV rendering.
 *
 * <p>The first eight columns are byte-for-byte the v2 contract documented in the
 * project README, so anything already parsing the old exports keeps working.
 * Schema-3 fields are appended after them.
 */
public final class CsvExporter {

    public static final String LEGACY_HEADER =
            "sample,x,y,z,distance_from_origin,dimension,timestamp,requested_region";

    private static final String EXTRA_HEADER =
            ",session_id,category,from_x,from_y,from_z,from_dimension,travel_distance,latency_ms,biome,surface_y,capture_mode,server,note";

    public static final String HEADER = LEGACY_HEADER + EXTRA_HEADER;

    private CsvExporter() {
    }

    public static String render(List<RtpSample> samples) {
        StringBuilder sb = new StringBuilder(64 + samples.size() * 128);
        sb.append(HEADER).append('\n');
        for (RtpSample s : samples) {
            appendRow(sb, s);
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, RtpSample s) {
        sb.append(s.sample()).append(',')
                .append(Numbers.plain(s.x())).append(',')
                .append(Numbers.plain(s.y())).append(',')
                .append(Numbers.plain(s.z())).append(',')
                .append(Numbers.plain(s.distanceFromOrigin())).append(',')
                .append(escape(s.dimension())).append(',')
                .append(s.timestamp()).append(',')
                .append(escape(s.requestedRegion())).append(',')
                .append(escape(s.sessionId())).append(',')
                .append(escape(s.category())).append(',')
                .append(Numbers.plain(s.fromX())).append(',')
                .append(Numbers.plain(s.fromY())).append(',')
                .append(Numbers.plain(s.fromZ())).append(',')
                .append(escape(s.fromDimension())).append(',')
                .append(Numbers.plain(s.travelDistance())).append(',')
                .append(s.latencyMs() == null ? "" : String.valueOf(s.latencyMs())).append(',')
                .append(escape(s.biome())).append(',')
                .append(s.surfaceY() == null ? "" : String.valueOf(s.surfaceY())).append(',')
                .append(escape(s.captureMode())).append(',')
                .append(escape(s.server())).append(',')
                .append(escape(s.note()))
                .append('\n');
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
