package dev.rtpbuddy.capture;

import dev.rtpbuddy.RTPBuddy;
import dev.rtpbuddy.config.RTPBuddyConfig;
import dev.rtpbuddy.config.RegionPreset;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.data.SampleStore;
import dev.rtpbuddy.session.SessionManager;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Worlds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns "the player ran an RTP command and then moved a long way" into a stored
 * sample.
 *
 * <pre>
 * IDLE ──(outgoing command matches a region pattern)──► ARMED
 * ARMED ──(dimension change or long jump)────────────► SETTLING
 * ARMED ──(timeout)──────────────────────────────────► IDLE
 * SETTLING ──(player stationary for settleTicks)─────► sample stored, IDLE
 * </pre>
 *
 * <p>This is passive throughout: it reads the command the player themselves
 * typed and the client's own position. Nothing is sent, delayed or rewritten.
 */
public class RtpCaptureController {

    public enum State {
        IDLE,
        ARMED,
        SETTLING
    }

    private final Supplier<RTPBuddyConfig> config;

    /** Cached compile of {@code capture.rtpCommandPattern}. */
    private Pattern compiledRtpPattern;
    private String compiledRtpSource;

    private final SampleStore store;
    private final SessionManager sessions;

    private State state = State.IDLE;
    private boolean paused;

    // --- armed context -------------------------------------------------------
    private double fromX = Double.NaN;
    private double fromY = Double.NaN;
    private double fromZ = Double.NaN;
    private String fromDimension;
    private long armedAtMillis;
    private int armedTicks;
    private String pendingRegion = RtpSample.REGION_UNKNOWN;
    private String pendingCommand = "";

    // --- settling context ----------------------------------------------------
    private int settleTicks;
    private double settleLastX;
    private double settleLastZ;

    // --- session counters for the HUD ---------------------------------------
    private int captured;
    private int timedOut;
    private int rejected;
    private RtpSample lastSample;

    /**
     * Called once for every landing that is actually recorded, on the client
     * thread, after the sample is in the store.
     *
     * <p>One hook rather than several: the cell board and the auto-RTP search
     * order both need to see a landing exactly once and both would otherwise
     * have to poll for it, which is the sort of thing that ends up running per
     * frame.
     */
    private java.util.function.Consumer<RtpSample> onCaptured;
    private String statusMessage = "";
    private long statusMessageAt;

    public RtpCaptureController(Supplier<RTPBuddyConfig> config, SampleStore store, SessionManager sessions) {
        this.config = config;
        this.store = store;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------ state

    public State state() {
        return state;
    }

    public boolean paused() {
        return paused;
    }

    public int captured() {
        return captured;
    }

    public int timedOut() {
        return timedOut;
    }

    public int rejected() {
        return rejected;
    }

    public RtpSample lastSample() {
        return lastSample;
    }

    public void setOnCaptured(java.util.function.Consumer<RtpSample> listener) {
        this.onCaptured = listener;
    }

    public String pendingRegion() {
        return pendingRegion;
    }

    /** Ticks remaining before an armed capture gives up. */
    public int remainingArmTicks() {
        return Math.max(0, config.get().capture.armTimeoutTicks - armedTicks);
    }

    public String statusMessage() {
        return statusMessage;
    }

    public long statusMessageAge() {
        return System.currentTimeMillis() - statusMessageAt;
    }

    public boolean togglePaused() {
        paused = !paused;
        if (paused) {
            reset();
        }
        setStatus(Lang.t(paused ? "capture.paused" : "capture.resumed"));
        return paused;
    }

    public void setPaused(boolean value) {
        if (paused != value) {
            togglePaused();
        }
    }

    public void reset() {
        state = State.IDLE;
        armedTicks = 0;
        settleTicks = 0;
        pendingRegion = RtpSample.REGION_UNKNOWN;
        pendingCommand = "";
        fromX = Double.NaN;
        fromY = Double.NaN;
        fromZ = Double.NaN;
        fromDimension = null;
    }

    /** Clears per-session counters. Called when a new session opens. */
    public void resetSessionCounters() {
        captured = 0;
        timedOut = 0;
        rejected = 0;
        lastSample = null;
        reset();
    }

    /**
     * Starts the per-sitting counters from a sitting that already holds
     * landings, which is what resuming one means: the HUD has to agree with the
     * map about how many that sitting holds instead of counting from zero.
     *
     * <p>Only the captured count carries over. Missed and rejected attempts
     * belong to the stretch of play they happened in and were never stored.
     */
    public void adoptSessionCounters(int alreadyCaptured) {
        resetSessionCounters();
        captured = Math.max(0, alreadyCaptured);
    }

    // --------------------------------------------------------------- commands

    /**
     * Called for every command the player sends, without the leading slash.
     * Arms a capture when the command matches a configured region pattern.
     */
    public void onCommandSent(String command) {
        RTPBuddyConfig cfg = config.get();
        if (!cfg.capture.enabled || !cfg.capture.autoCapture || paused) {
            return;
        }
        if (command == null || command.isBlank()) {
            return;
        }
        String normalised = command.trim();
        RegionPreset region = cfg.matchCommand(normalised);
        String regionId;
        String regionLabel;
        if (region != null) {
            regionId = region.id;
            regionLabel = region.label;
        } else {
            // An RTP the region list does not know about is still an RTP. The
            // argument is kept exactly as typed rather than mapped onto the
            // nearest configured region, because what the server was asked for
            // is a fact and what it means is not ours to decide.
            regionId = unknownRtpRegion(cfg, normalised);
            if (regionId == null) {
                return;
            }
            regionLabel = cfg.regionLabel(regionId);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        fromX = player.getX();
        fromY = player.getY();
        fromZ = player.getZ();
        fromDimension = Worlds.dimensionId(client.world);
        armedAtMillis = System.currentTimeMillis();
        armedTicks = 0;
        pendingRegion = regionId;
        pendingCommand = normalised;
        state = State.ARMED;
        setStatus(Lang.t("capture.armed", regionLabel));
    }

    /**
     * The region id to record for an RTP command no configured region matched,
     * or null when the command is not an RTP at all or the catch-all is off.
     */
    private String unknownRtpRegion(RTPBuddyConfig cfg, String command) {
        if (!cfg.capture.captureUnknownRtp) {
            return null;
        }
        Matcher matcher = rtpPattern(cfg).matcher(command);
        if (!matcher.matches()) {
            return null;
        }
        String argument = matcher.groupCount() >= 2 ? matcher.group(2) : null;
        if (argument == null || argument.isBlank()) {
            return cfg.capture.manualFallbackRegion;
        }
        return argument.trim().toLowerCase(Locale.ROOT).replaceAll("\s+", " ");
    }

    /** Compiled once per pattern string; a broken pattern matches nothing. */
    private Pattern rtpPattern(RTPBuddyConfig cfg) {
        String source = cfg.capture.rtpCommandPattern;
        if (compiledRtpPattern == null || !source.equals(compiledRtpSource)) {
            compiledRtpSource = source;
            try {
                compiledRtpPattern = Pattern.compile(source, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                RTPBuddy.LOGGER.warn("[RTPBuddy] capture.rtpCommandPattern is not a valid regex: {}",
                        e.getMessage());
                compiledRtpPattern = Pattern.compile("(?!)");
            }
        }
        return compiledRtpPattern;
    }

    // ------------------------------------------------------------------- tick

    public void tick(MinecraftClient client) {
        RTPBuddyConfig cfg = config.get();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            if (state != State.IDLE) {
                reset();
            }
            return;
        }

        switch (state) {
            case IDLE -> {
            }
            case ARMED -> tickArmed(cfg, player, world);
            case SETTLING -> tickSettling(cfg, client, player, world);
        }
    }

    private void tickArmed(RTPBuddyConfig cfg, ClientPlayerEntity player, ClientWorld world) {
        armedTicks++;

        boolean dimensionChanged = cfg.capture.captureOnDimensionChange
                && !Worlds.dimensionId(world).equals(fromDimension);
        double jump = Math.hypot(player.getX() - fromX, player.getZ() - fromZ);
        boolean jumped = jump >= cfg.capture.teleportDistanceThreshold;

        if (dimensionChanged || jumped) {
            state = State.SETTLING;
            settleTicks = 0;
            settleLastX = player.getX();
            settleLastZ = player.getZ();
            return;
        }

        if (armedTicks >= cfg.capture.armTimeoutTicks) {
            timedOut++;
            setStatus(Lang.t("capture.no_teleport", pendingCommand));
            reset();
        }
    }

    private void tickSettling(RTPBuddyConfig cfg, MinecraftClient client,
                              ClientPlayerEntity player, ClientWorld world) {
        double drift = Math.hypot(player.getX() - settleLastX, player.getZ() - settleLastZ);
        settleLastX = player.getX();
        settleLastZ = player.getZ();

        // Any real movement restarts the settle window, so a sample is only taken
        // once the server has stopped correcting the position.
        if (drift > 0.05) {
            settleTicks = 0;
        } else {
            settleTicks++;
        }

        if (settleTicks >= cfg.capture.settleTicks) {
            capture(client, player, world, pendingRegion, RtpSample.CAPTURE_AUTO,
                    System.currentTimeMillis() - armedAtMillis);
            reset();
        }
    }

    // ---------------------------------------------------------------- capture

    /** Records the current position immediately, bypassing the state machine. */
    public RtpSample captureManual(String region) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            setStatus(Lang.t("capture.not_in_world"));
            return null;
        }
        String effectiveRegion = region == null || region.isBlank()
                ? config.get().capture.manualFallbackRegion
                : region;
        return capture(client, player, world, effectiveRegion, RtpSample.CAPTURE_MANUAL, -1L);
    }

    private RtpSample capture(MinecraftClient client, ClientPlayerEntity player, ClientWorld world,
                              String region, String captureMode, long latencyMs) {
        RTPBuddyConfig cfg = config.get();

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        boolean auto = RtpSample.CAPTURE_AUTO.equals(captureMode) && !Double.isNaN(fromX);
        double travel = auto ? Math.hypot(x - fromX, z - fromZ) : Double.NaN;
        // Anything the capture did not actually observe is stored as null, not as
        // a sentinel: see the note on RtpSample.
        if (auto && cfg.capture.rejectShortJumps && travel < cfg.capture.teleportDistanceThreshold) {
            // The dimension-change path can land here with a small horizontal
            // delta; that is a real teleport, so only reject same-dimension hops.
            if (Worlds.dimensionId(world).equals(fromDimension)) {
                rejected++;
                setStatus(Lang.t("capture.rejected", Math.round(travel)));
                return null;
            }
        }

        if (!sessions.active()) {
            sessions.begin(Worlds.serverAddress(client));
        }

        BlockPos pos = player.getBlockPos();
        RtpSample sample = new RtpSample(
                store.claimSampleNumber(),
                sessions.currentId(),
                x, y, z,
                Worlds.dimensionId(world),
                System.currentTimeMillis(),
                region,
                cfg.capture.defaultCategory,
                auto ? fromX : null,
                auto ? fromY : null,
                auto ? fromZ : null,
                auto ? fromDimension : null,
                auto ? travel : null,
                latencyMs >= 0 ? latencyMs : null,
                Worlds.biomeId(world, pos),
                surfaceY(world, pos),
                captureMode,
                Worlds.serverAddress(client),
                null
        );

        store.add(sample);
        store.scheduleSave(cfg.capture.saveDebounceMillis);
        sessions.touch();

        captured++;
        lastSample = sample;

        String guardWarning = guardWarning(cfg, sample.dimension(), x, z);
        // The overlay counts within the sitting, so this line has to as well:
        // "recorded #3" beside a row reading "#3" is one landing, "#299" beside
        // it looks like two.
        int shown = store.indexInSession(sample);
        setStatus(guardWarning == null
                ? Lang.t("capture.recorded", shown)
                : Lang.t("capture.recorded_warn", shown, guardWarning));
        RTPBuddy.LOGGER.debug("[RTPBuddy] captured sample #{} at {}/{}/{} in {}",
                sample.sample(), x, y, z, sample.dimension());
        if (onCaptured != null) {
            // Last, and guarded: a listener that throws must not cost the
            // landing that was just recorded.
            try {
                onCaptured.accept(sample);
            } catch (RuntimeException e) {
                RTPBuddy.LOGGER.warn("[RTPBuddy] capture listener failed", e);
            }
        }
        return sample;
    }

    private static Integer surfaceY(ClientWorld world, BlockPos pos) {
        int y = Worlds.surfaceY(world, pos.getX(), pos.getZ());
        return y == Integer.MIN_VALUE ? null : y;
    }

    /** Advisory guard check. Mapping is never blocked - this only surfaces text. */
    private String guardWarning(RTPBuddyConfig cfg, String dimension, double x, double z) {
        if (!cfg.guards.insideBorderGuard(dimension, x, z)) {
            return Lang.t("warn.border_guard");
        }
        if (!cfg.guards.outsideSpawnGuard(x, z)) {
            return Lang.t("warn.spawn_guard");
        }
        return null;
    }

    private void setStatus(String message) {
        this.statusMessage = message;
        this.statusMessageAt = System.currentTimeMillis();
    }
}
