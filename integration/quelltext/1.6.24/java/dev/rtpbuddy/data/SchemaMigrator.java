package dev.rtpbuddy.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.rtpbuddy.RTPBuddy;
import dev.rtpbuddy.util.FileOps;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot import of the predecessor mod's data from
 * {@code config/rtpmapper/rtp_samples.json}.
 *
 * <p>Deliberately parses the raw tree rather than binding to a class: the exact
 * key spelling of the old file is not something this codebase controls, so every
 * field is looked up under a few plausible names and anything missing falls back
 * to a safe default. The source file is only ever read - never rewritten, moved
 * or deleted.
 *
 * <p>Schema-1 rows carry no region. They are imported as {@code unknown} rather
 * than reconstructed from coordinates, because server region boundaries change
 * and a guessed region would be indistinguishable from a recorded one.
 */
public final class SchemaMigrator {

    public record Result(int imported, int skipped, boolean sourcePresent) {

        public static Result absent() {
            return new Result(0, 0, false);
        }
    }

    private SchemaMigrator() {
    }

    public static Result importLegacy(Path legacyDir, SampleStore store, int firstSampleNumber) {
        Path source = legacyDir.resolve("rtp_samples.json");
        String json = FileOps.readOrNull(source);
        if (json == null) {
            return Result.absent();
        }

        JsonArray rows;
        int detectedSchema;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                rows = root.getAsJsonArray();
                detectedSchema = 1;
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                rows = firstArray(obj, "samples", "entries", "data", "records");
                detectedSchema = intOf(obj, 1, "schema", "schemaVersion", "version");
            } else {
                RTPBuddy.LOGGER.warn("[RTPBuddy] legacy sample file has an unexpected shape, skipping import");
                return new Result(0, 0, true);
            }
        } catch (JsonSyntaxException e) {
            RTPBuddy.LOGGER.warn("[RTPBuddy] legacy sample file is not valid JSON, skipping import: {}", e.getMessage());
            return new Result(0, 0, true);
        }

        if (rows == null) {
            RTPBuddy.LOGGER.warn("[RTPBuddy] legacy sample file contains no sample array, skipping import");
            return new Result(0, 0, true);
        }

        List<RtpSample> imported = new ArrayList<>();
        int skipped = 0;
        int number = firstSampleNumber;

        for (JsonElement element : rows) {
            if (!element.isJsonObject()) {
                skipped++;
                continue;
            }
            JsonObject row = element.getAsJsonObject();
            Double x = doubleOf(row, "x");
            Double y = doubleOf(row, "y");
            Double z = doubleOf(row, "z");
            if (x == null || z == null) {
                skipped++;
                continue;
            }

            String region = stringOf(row, "requestedRegion", "requested_region", "region");
            if (detectedSchema < 2 || region == null || region.isBlank()) {
                region = RtpSample.REGION_UNKNOWN;
            }

            long timestamp = longOf(row, 0L, "timestamp", "time", "epochMillis");
            String dimension = stringOf(row, "dimension", "dim", "world");
            String category = stringOf(row, "category", "type");

            imported.add(new RtpSample(
                    number++,
                    SessionRecord.LEGACY_ID,
                    x,
                    y == null ? 0.0 : y,
                    z,
                    dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension,
                    timestamp,
                    region,
                    category == null || category.isBlank() ? "rtp" : category,
                    null, null, null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    RtpSample.CAPTURE_IMPORTED,
                    "",
                    null
            ));
        }

        if (imported.isEmpty()) {
            RTPBuddy.LOGGER.info("[RTPBuddy] legacy file present but yielded no usable samples ({} skipped)", skipped);
            return new Result(0, skipped, true);
        }

        SessionRecord legacySession = new SessionRecord(SessionRecord.LEGACY_ID, oldest(imported), "", 0);
        legacySession.endedAt = newest(imported);
        legacySession.label = "Imported (rtpmapper schema " + detectedSchema + ")";

        store.addAll(imported, legacySession);
        store.flushNow();

        RTPBuddy.LOGGER.info("[RTPBuddy] imported {} legacy samples from {} (schema {}, {} skipped)",
                imported.size(), source, detectedSchema, skipped);
        return new Result(imported.size(), skipped, true);
    }

    private static long oldest(List<RtpSample> samples) {
        long min = Long.MAX_VALUE;
        for (RtpSample s : samples) {
            if (s.timestamp() > 0) {
                min = Math.min(min, s.timestamp());
            }
        }
        return min == Long.MAX_VALUE ? System.currentTimeMillis() : min;
    }

    private static long newest(List<RtpSample> samples) {
        long max = 0L;
        for (RtpSample s : samples) {
            max = Math.max(max, s.timestamp());
        }
        return max == 0L ? System.currentTimeMillis() : max;
    }

    // ------------------------------------------------------- lenient accessors

    private static JsonArray firstArray(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement element = obj.get(key);
            if (element != null && element.isJsonArray()) {
                return element.getAsJsonArray();
            }
        }
        return null;
    }

    private static String stringOf(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement element = obj.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsString();
            }
        }
        return null;
    }

    private static Double doubleOf(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement element = obj.get(key);
            if (element != null && element.isJsonPrimitive()) {
                try {
                    return element.getAsDouble();
                } catch (NumberFormatException ignored) {
                    // fall through to the next candidate key
                }
            }
        }
        return null;
    }

    private static long longOf(JsonObject obj, long fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = obj.get(key);
            if (element != null && element.isJsonPrimitive()) {
                try {
                    return element.getAsLong();
                } catch (NumberFormatException ignored) {
                    // fall through to the next candidate key
                }
            }
        }
        return fallback;
    }

    private static int intOf(JsonObject obj, int fallback, String... keys) {
        return (int) longOf(obj, fallback, keys);
    }
}
