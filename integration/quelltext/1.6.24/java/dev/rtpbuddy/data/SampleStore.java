package dev.rtpbuddy.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.rtpbuddy.RTPBuddy;
import dev.rtpbuddy.util.FileOps;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Authoritative sample storage: JSON on disk, CSV kept as a mirror.
 *
 * <p>Mutation happens on the client thread; persisting is debounced onto a
 * single background thread, so a burst of teleports costs one write and never
 * stalls a tick. Because those two threads meet over the same lists, every
 * mutator and the write itself take this object's monitor - that is what makes
 * the write's snapshot coherent and {@link #flushBlocking()} genuinely blocking
 * rather than able to slip past a write already in progress.
 */
public class SampleStore {

    public static final int SCHEMA_VERSION = 3;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter EXPORT_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path jsonFile;
    private final Path csvFile;
    private final Path exportDir;

    private final List<RtpSample> samples = new ArrayList<>();
    private final Map<String, SessionRecord> sessions = new LinkedHashMap<>();
    private int nextSample = 1;

    /**
     * Position of each sample inside its own sitting, one-based, by sample
     * number.
     *
     * <p>The stored {@code sample} number is global and never reused - it is the
     * identity every other part of the mod, the CSV and the viewer key on. But a
     * sitting that opens at "#297" reads as a continuation of yesterday rather
     * than as a fresh run, so the screens count from 1 within a session and use
     * the global number only when several sittings are shown at once.
     *
     * <p>Rebuilt lazily whenever {@link #revision} moves, because a delete in
     * the middle of a session shifts everything after it.
     */
    private final Map<Integer, Integer> sessionIndex = new HashMap<>();
    private int indexRevision = -1;
    private int revision;

    private final ScheduledExecutorService writer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "RTPBuddy-IO");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private ScheduledFuture<?> pendingWrite;
    private boolean writeCsvMirror = true;

    public SampleStore(Path configDir, Path exportDir) {
        this.jsonFile = configDir.resolve("rtp_samples.json");
        this.csvFile = configDir.resolve("rtp_samples.csv");
        this.exportDir = exportDir;
    }

    // ---------------------------------------------------------------- loading

    public void load() {
        FileOps.recoverStrayTemp(jsonFile);
        String json = FileOps.readOrNull(jsonFile);
        if (json == null) {
            RTPBuddy.LOGGER.info("[RTPBuddy] no sample file yet, starting empty");
            return;
        }
        try {
            StoreFile parsed = GSON.fromJson(json, StoreFile.class);
            if (parsed == null) {
                return;
            }
            if (parsed.sessions != null) {
                for (SessionRecord session : parsed.sessions) {
                    if (session != null && session.id != null) {
                        sessions.put(session.id, session);
                    }
                }
            }
            if (parsed.samples != null) {
                for (RtpSample sample : parsed.samples) {
                    if (sample != null) {
                        samples.add(sample.normalised());
                    }
                }
            }
            samples.sort(Comparator.comparingInt(RtpSample::sample));
            nextSample = Math.max(parsed.nextSample, highestSampleNumber() + 1);
            RTPBuddy.LOGGER.info("[RTPBuddy] loaded {} samples across {} sessions (schema {})",
                    samples.size(), sessions.size(), parsed.schema);
        } catch (JsonSyntaxException e) {
            RTPBuddy.LOGGER.error("[RTPBuddy] rtp_samples.json is malformed; leaving it untouched and starting empty: {}",
                    e.getMessage());
            samples.clear();
            sessions.clear();
            revision++;
        }
    }

    private int highestSampleNumber() {
        int max = 0;
        for (RtpSample s : samples) {
            max = Math.max(max, s.sample());
        }
        return max;
    }

    // ---------------------------------------------------------------- reading

    public synchronized List<RtpSample> samples() {
        return List.copyOf(samples);
    }

    /** Live view for hot render paths. Callers must not mutate it. */
    public List<RtpSample> samplesView() {
        return samples;
    }

    public synchronized List<RtpSample> samplesOf(String sessionId) {
        List<RtpSample> result = new ArrayList<>();
        for (RtpSample s : samples) {
            if (s.sessionId().equals(sessionId)) {
                result.add(s);
            }
        }
        return result;
    }

    public synchronized List<SessionRecord> sessions() {
        return new ArrayList<>(sessions.values());
    }

    public SessionRecord session(String id) {
        return sessions.get(id);
    }

    public int size() {
        return samples.size();
    }

    /**
     * Bumped by every change to the samples, including ones that leave the count
     * alone - a merge restamps landings onto another sitting without adding or
     * removing any. Anything caching per-session figures has to key on this
     * rather than on {@link #size()}.
     */
    public int revision() {
        return revision;
    }

    public int peekNextSampleNumber() {
        return nextSample;
    }

    // ---------------------------------------------------------------- writing

    public synchronized void putSession(SessionRecord session) {
        sessions.put(session.id, session);
        markDirty();
    }

    /** Drops a session record. Its samples, if any, are left untouched. */
    public synchronized boolean removeSession(String sessionId) {
        boolean removed = sessions.remove(sessionId) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public synchronized boolean hasSamplesFor(String sessionId) {
        for (RtpSample sample : samples) {
            if (sample.sessionId().equals(sessionId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int claimSampleNumber() {
        return nextSample++;
    }

    public synchronized void add(RtpSample sample) {
        samples.add(sample);
        revision++;
        nextSample = Math.max(nextSample, sample.sample() + 1);
        markDirty();
    }

    public synchronized boolean remove(int sampleNumber) {
        boolean removed = samples.removeIf(s -> s.sample() == sampleNumber);
        if (removed) {
            revision++;
            markDirty();
        }
        return removed;
    }

    public synchronized boolean setNote(int sampleNumber, String note) {
        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i).sample() == sampleNumber) {
                samples.set(i, samples.get(i).withNote(note));
                markDirty();
                return true;
            }
        }
        return false;
    }

    /** Bulk insert used by the legacy importer. */
    /**
     * Where this sample sits inside its own sitting, counting from 1, or its
     * global number when the session is unknown.
     */
    public synchronized int indexInSession(RtpSample sample) {
        if (sample == null) {
            return 0;
        }
        if (indexRevision != revision) {
            rebuildSessionIndex();
        }
        Integer index = sessionIndex.get(sample.sample());
        return index != null ? index : sample.sample();
    }

    private void rebuildSessionIndex() {
        sessionIndex.clear();
        Map<String, Integer> counters = new HashMap<>();
        // Recording order is the file order, so one pass in list order is the
        // order the player actually landed in.
        for (RtpSample sample : samples) {
            int next = counters.merge(sample.sessionId(), 1, Integer::sum);
            sessionIndex.put(sample.sample(), next);
        }
        indexRevision = revision;
    }

    public synchronized void addAll(List<RtpSample> incoming, SessionRecord session) {
        if (session != null) {
            sessions.put(session.id, session);
        }
        for (RtpSample s : incoming) {
            samples.add(s);
            nextSample = Math.max(nextSample, s.sample() + 1);
        }
        samples.sort(Comparator.comparingInt(RtpSample::sample));
        revision++;
        markDirty();
    }

    /**
     * Folds several sittings into one.
     *
     * <p>Every landing of {@code sourceIds} is restamped onto {@code targetId},
     * the source records are dropped, and the target's span is widened to cover
     * all of them. Nothing is deleted - the landings all survive, they simply
     * belong to one sitting now.
     *
     * <p>The per-sitting numbering falls out of this on its own:
     * {@link #rebuildSessionIndex()} walks the list in order and the list is
     * kept in global-number order, which is the order the landings were made.
     * So the merged sitting counts 1..n chronologically, whatever order the
     * sittings were picked in.
     *
     * @return how many landings were restamped
     */
    public synchronized int mergeSessions(String targetId, java.util.Collection<String> sourceIds) {
        SessionRecord target = sessions.get(targetId);
        if (target == null || sourceIds == null) {
            return 0;
        }
        java.util.Set<String> from = new java.util.HashSet<>(sourceIds);
        from.remove(targetId);
        from.remove(SessionRecord.LEGACY_ID);
        if (from.isEmpty()) {
            return 0;
        }

        int moved = 0;
        for (int i = 0; i < samples.size(); i++) {
            RtpSample sample = samples.get(i);
            if (from.contains(sample.sessionId())) {
                samples.set(i, sample.withSession(targetId));
                moved++;
            }
        }
        for (String id : from) {
            SessionRecord source = sessions.remove(id);
            if (source != null) {
                target.startedAt = Math.min(target.startedAt, source.startedAt);
                target.endedAt = Math.max(target.endedAt, source.endedAt);
            }
        }
        revision++;
        markDirty();
        RTPBuddy.LOGGER.info("[RTPBuddy] merged {} sessions into {} ({} samples restamped)",
                from.size(), targetId, moved);
        return moved;
    }

    public void setWriteCsvMirror(boolean enabled) {
        this.writeCsvMirror = enabled;
    }

    public void markDirty() {
        dirty.set(true);
    }

    /** Debounced persist. Repeated calls inside the window collapse into one write. */
    public synchronized void scheduleSave(int debounceMillis) {
        if (!dirty.get()) {
            return;
        }
        if (pendingWrite != null && !pendingWrite.isDone()) {
            return;
        }
        pendingWrite = writer.schedule(this::flush, Math.max(0, debounceMillis), TimeUnit.MILLISECONDS);
    }

    /** Queues an immediate write on the IO thread. */
    public void flushNow() {
        writer.execute(this::flush);
    }

    /**
     * Waits for any in-flight background write and then writes anything still
     * pending. Because {@link #flush()} holds this object's monitor, entering it
     * here cannot overtake a write the IO thread has already started - which is
     * what makes this actually blocking rather than merely synchronous-looking.
     */
    public void flushBlocking() {
        flush();
    }

    private synchronized void flush() {
        if (!dirty.getAndSet(false)) {
            return;
        }
        StoreFile out = new StoreFile();
        out.schema = SCHEMA_VERSION;
        out.nextSample = nextSample;
        out.sessions = new ArrayList<>(sessions.values());
        out.samples = new ArrayList<>(samples);
        try {
            FileOps.writeAtomic(jsonFile, GSON.toJson(out));
            if (writeCsvMirror) {
                FileOps.writeAtomic(csvFile, CsvExporter.render(out.samples));
            }
        } catch (IOException e) {
            dirty.set(true);
            RTPBuddy.LOGGER.error("[RTPBuddy] failed to persist samples: {}", e.toString());
        }
    }

    /** Writes a timestamped CSV of the supplied selection. Returns the path or null. */
    public Path export(List<RtpSample> selection) {
        String name = "rtp_data_" + LocalDateTime.now().format(EXPORT_STAMP) + ".csv";
        Path target = exportDir.resolve(name);
        try {
            FileOps.writeAtomic(target, CsvExporter.render(selection));
            RTPBuddy.LOGGER.info("[RTPBuddy] exported {} samples to {}", selection.size(), target);
            return target;
        } catch (IOException e) {
            RTPBuddy.LOGGER.error("[RTPBuddy] export failed: {}", e.toString());
            return null;
        }
    }

    public void shutdown() {
        flush();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                RTPBuddy.LOGGER.warn("[RTPBuddy] sample writer did not finish within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** On-disk shape of rtp_samples.json. */
    static final class StoreFile {
        int schema = SCHEMA_VERSION;
        int nextSample = 1;
        List<SessionRecord> sessions = new ArrayList<>();
        List<RtpSample> samples = new ArrayList<>();
    }
}
