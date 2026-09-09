package dev.rtpbuddy.capture;

import dev.rtpbuddy.RTPBuddy;
import dev.rtpbuddy.config.AutoRtpConfig;
import dev.rtpbuddy.config.RTPBuddyConfig;
import dev.rtpbuddy.config.RegionPreset;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Worlds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Sends the configured RTP command on a timer so a map can be filled in without
 * typing the same command hundreds of times.
 *
 * <p>This is the one part of RTPBuddy that acts on its own, so it is built like
 * a deliberate, revocable action rather than like the rest of the mod:
 *
 * <ul>
 *   <li>{@link #running} is runtime-only and <b>never persisted</b>. It starts
 *       false on every launch and is cleared on disconnect, world join and death.</li>
 *   <li>It only ever sends the exact command the player configured, at an
 *       interval the player chose, with jitter so it is not machine-exact. It
 *       does not shorten or bypass any server cooldown; if the server rejects a
 *       teleport, the next attempt simply waits out the same interval again.</li>
 *   <li>It stops itself on damage, on the session cap, on the run timer and -
 *       optionally - on a guard violation or on another player coming close
 *       once the run has teleported away from where it started.</li>
 *   <li>It can be <b>held</b> instead of stopped: {@link #pause()} freezes both
 *       clocks and sends nothing, and {@link #resume()} continues with the same
 *       wait, counters and rotation position. That is for stopping to look at
 *       something without losing a run.</li>
 * </ul>
 *
 * <p>Server rules on repeating a command differ - DonutSMP permits it - so the
 * decision is the player's, and the start dialog states exactly what will be
 * sent and how often before anything is.
 */
public class AutoRtpController {

    private final Supplier<RTPBuddyConfig> config;
    private final RtpCaptureController capture;
    private final Random random = new Random();

    /** Runtime-only. There is no code path that writes this to disk. */
    private boolean running;

    /**
     * Held, not stopped: the run keeps its counters, its rotation position and
     * the wait it had left, and simply sends nothing until it is resumed. This
     * is for looking at whatever the last teleport landed next to without
     * losing the run. Runtime-only, like {@link #running}.
     */
    private boolean paused;

    private long pausedAtMillis;

    private long startedAtMillis;
    private long nextSendAtMillis;
    private int sentThisRun;
    private float lastHealth = Float.NaN;
    private String statusMessage = "";

    /** Position in the region rotation. Runtime-only, like {@link #running}. */
    private int cursor;

    /**
     * Where the run was started, and whether it has been teleported away from
     * there yet. Only the nearby-player stop reads these; see
     * {@link #leftTheStartingSpot}. Runtime-only, like {@link #running}.
     */
    private double startX;
    private double startZ;
    private String startDimension = "";
    private boolean nearbyArmed;

    /** Landings this run has seen that did not match the search order. */
    private int missedThisRun;

    /** What the search order found, cleared when the next run starts. */
    private String foundMessage = "";

    public AutoRtpController(Supplier<RTPBuddyConfig> config, RtpCaptureController capture) {
        this.config = config;
        this.capture = capture;
    }

    public boolean running() {
        return running;
    }

    public boolean paused() {
        return running && paused;
    }

    /** True when the run waits for a key press rather than for the clock. */
    public boolean manualStep() {
        return config.get().autoRtp.manualStep;
    }

    /**
     * Sends the next teleport of a manually stepped run.
     *
     * <p>Refuses rather than queues. A press that cannot be honoured - nothing
     * running, a capture still settling, the floor not yet elapsed - says so and
     * changes nothing, because a press that silently fires later is worse than
     * one that does nothing: the whole point of stepping by hand is knowing when
     * the command went out.
     *
     * @return true when a command was sent
     */
    public boolean stepNow() {
        if (!running) {
            statusMessage = Lang.t("auto.step_idle");
            return false;
        }
        if (!manualStep()) {
            statusMessage = Lang.t("auto.step_timed");
            return false;
        }
        if (paused) {
            statusMessage = Lang.t("auto.step_held");
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            stop("reason.left_world");
            return false;
        }
        AutoRtpConfig auto = config.get().autoRtp;
        if (auto.waitForCapture && capture.state() != RtpCaptureController.State.IDLE) {
            statusMessage = Lang.t("auto.step_capturing");
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < nextSendAtMillis) {
            statusMessage = Lang.t("auto.step_too_soon",
                    Math.max(1, Math.round((nextSendAtMillis - now) / 1000.0)));
            return false;
        }
        RegionPreset region = nextRegion();
        if (region == null) {
            stop("reason.region_no_command");
            return false;
        }
        send(client, region);
        // The floor only, never the configured wait. Manual means the player
        // picks the moment; it does not mean a held key may turn into a command
        // every tick.
        nextSendAtMillis = System.currentTimeMillis()
                + AutoRtpConfig.MIN_COOLDOWN_SECONDS * 1000L;
        return true;
    }

    /**
     * Holds or continues a running loop. Does nothing when nothing is running -
     * pause is not a way to arm the loop.
     *
     * @return true if the loop is paused afterwards
     */
    public boolean togglePaused() {
        if (!running) {
            statusMessage = Lang.t("auto.pause_idle");
            return false;
        }
        if (paused) {
            resume();
        } else {
            pause();
        }
        return paused;
    }

    public void pause() {
        if (!running || paused) {
            return;
        }
        paused = true;
        pausedAtMillis = System.currentTimeMillis();
        statusMessage = Lang.t("auto.paused", sentThisRun);
        RTPBuddy.LOGGER.info("[RTPBuddy] auto-RTP paused after {} teleports", sentThisRun);
    }

    public void resume() {
        if (!running || !paused) {
            return;
        }
        // The pause is meant to cost nothing, so the time spent in it is added
        // back to both clocks: the wait that was left is still the wait that is
        // left, and the run timer does not burn while the loop is holding.
        long held = System.currentTimeMillis() - pausedAtMillis;
        nextSendAtMillis += held;
        startedAtMillis += held;
        paused = false;
        // Damage taken while paused is the player's own doing, not the loop's.
        MinecraftClient client = MinecraftClient.getInstance();
        lastHealth = client.player != null ? client.player.getHealth() : Float.NaN;
        statusMessage = Lang.t("auto.resumed", Math.round(secondsUntilNext()));
        RTPBuddy.LOGGER.info("[RTPBuddy] auto-RTP resumed");
    }

    public int sentThisRun() {
        return sentThisRun;
    }

    /** Landings judged against the search order and rejected, this run. */
    public int missedThisRun() {
        return missedThisRun;
    }

    /** True when this run is looking for something rather than just filling. */
    public boolean searching() {
        return FindRule.armed(config.get().autoRtp);
    }

    /** What the search order last found, or "" if it has not fired. */
    public String foundMessage() {
        return foundMessage;
    }

    /**
     * Judges a freshly recorded landing against the search order.
     *
     * <p>Runs once per landing, wired to the capture controller rather than
     * polled: asking "has something been captured yet" on the tick path would
     * mean walking the store for an answer that arrives on its own.
     *
     * <p>Does nothing at all unless a run is going and a condition is armed, so
     * capture outside a run costs one boolean.
     */
    public void onLanding(dev.rtpbuddy.data.RtpSample sample,
                          dev.rtpbuddy.stats.CellCoverage coverage, int cellNumber) {
        if (!running || paused) {
            return;
        }
        AutoRtpConfig auto = config.get().autoRtp;
        if (!FindRule.armed(auto)) {
            return;
        }
        FindRule.Match match = FindRule.test(auto, sample, coverage, cellNumber);
        if (match != null) {
            foundMessage = Lang.t(match.reasonKey(), match.args());
            stop("reason.found", foundMessage);
            announceFind(sample);
            return;
        }
        missedThisRun++;
        if (auto.findGiveUpAfter > 0 && missedThisRun >= auto.findGiveUpAfter) {
            stop("reason.find_exhausted", missedThisRun);
        }
    }

    /**
     * Says what was found, in chat and - if asked for - with a short note.
     *
     * <p>The whole point of a search order is not having to watch the screen,
     * so the ending has to be noticeable without one.
     */
    private void announceFind(dev.rtpbuddy.data.RtpSample sample) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        if (config.get().autoRtp.findSound) {
            client.player.playSound(
                    net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.7f, 1.4f);
        }
        client.player.sendMessage(net.minecraft.text.Text.literal(
                Lang.t("chat.found", foundMessage, Math.round(sample.x()), Math.round(sample.z()))),
                false);
    }

    /**
     * True once this run has actually been teleported somewhere else.
     *
     * <p>The nearby-player stop waits for this, and the reason is the state it
     * leaves behind: it fires because a landing put someone next to you, and
     * the thing you want next is another teleport, out. Live from the first
     * tick, the run you start to get away would die on the very player you are
     * getting away from, and that spot would have no exit at all.
     *
     * <p>So it arms only after the run has moved you - a jump past the capture
     * threshold, or a change of dimension - and stays armed for the rest of the
     * run. Whoever is standing next to you when you press start is your
     * business; whoever is standing next to you where the loop dropped you is
     * the loop's.
     */
    private boolean leftTheStartingSpot(MinecraftClient client, ClientPlayerEntity player) {
        if (!Worlds.dimensionId(client.world).equals(startDimension)) {
            return true;
        }
        double dx = player.getX() - startX;
        double dz = player.getZ() - startZ;
        double threshold = config.get().capture.teleportDistanceThreshold;
        return dx * dx + dz * dz >= threshold * threshold;
    }

    /**
     * Distance to the closest other player inside {@code radius}, or -1 when
     * there is none - which is also what a radius of 0 always answers.
     *
     * <p>Reads {@code world.getPlayers()}, so it sees exactly what the client
     * has been told about and nothing else: no player outside the server's
     * tracking range, and no player the server is hiding. Spectators are
     * skipped because a spectator cannot do anything to you.
     *
     * <p>Cheap enough to run every tick - the client player list is short and
     * this is squared distances - and it has to be, because the answer is only
     * useful while it is still current.
     */
    private double nearestOtherPlayer(MinecraftClient client, double radius) {
        if (radius <= 0 || client.world == null || client.player == null) {
            return -1;
        }
        double limit = radius * radius;
        double best = -1;
        for (net.minecraft.entity.player.PlayerEntity other : client.world.getPlayers()) {
            if (other == client.player || other.isSpectator() || other.isRemoved()) {
                continue;
            }
            double squared = other.squaredDistanceTo(client.player);
            if (squared <= limit && (best < 0 || squared < best)) {
                best = squared;
            }
        }
        return best < 0 ? -1 : Math.sqrt(best);
    }

    /**
     * Says out loud that the run ended because someone is close.
     *
     * <p>Unconditional, unlike the search order's sound: the reason for this
     * stop is that you may be about to be attacked, and a warning that only
     * reaches a screen you were not watching is not a warning.
     */
    private void announcePlayerNearby(MinecraftClient client, double distance) {
        if (client.player == null) {
            return;
        }
        client.player.playSound(
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.9f, 0.7f);
        client.player.sendMessage(net.minecraft.text.Text.literal(
                Lang.t("chat.player_nearby", Math.round(distance))), false);
    }

    public long runningForMillis() {
        if (!running) {
            return 0L;
        }
        return (paused ? pausedAtMillis : System.currentTimeMillis()) - startedAtMillis;
    }

    public String statusMessage() {
        return statusMessage;
    }

    /** Seconds until the next command, or -1 when not waiting on the clock. */
    public double secondsUntilNext() {
        if (!running) {
            return -1;
        }
        // Frozen while paused: the countdown shown must match what actually
        // happens on resume, and nothing counts down while the loop holds.
        long now = paused ? pausedAtMillis : System.currentTimeMillis();
        return Math.max(0, (nextSendAtMillis - now) / 1000.0);
    }

    /**
     * The regions auto-RTP will rotate through, in the order they were chosen.
     *
     * <p>Falls back to the single {@code autoRtp.region} when no list is set, so
     * a config written before multi-region selection keeps behaving as it did.
     * Ids that name nothing, or a region with no command to send, are dropped
     * here rather than stopping the loop later.
     */
    public List<RegionPreset> targetRegions() {
        AutoRtpConfig auto = config.get().autoRtp;
        List<RegionPreset> found = new ArrayList<>();
        for (String id : auto.regions) {
            RegionPreset region = config.get().findRegion(id);
            if (region != null && region.sendable() && !found.contains(region)) {
                found.add(region);
            }
        }
        if (found.isEmpty()) {
            RegionPreset single = config.get().findRegion(auto.region);
            if (single != null && single.sendable()) {
                found.add(single);
            }
        }
        return found;
    }

    /** The region the next teleport would request, or null if none is sendable. */
    public RegionPreset targetRegion() {
        List<RegionPreset> regions = targetRegions();
        if (regions.isEmpty()) {
            return null;
        }
        if (regions.size() == 1) {
            return regions.get(0);
        }
        return regions.get(Math.floorMod(cursor, regions.size()));
    }

    /**
     * Advances the rotation and hands back the region to send now.
     *
     * <p>Round robin is the default because that is what filling a map wants:
     * every chosen zone gets the same number of samples instead of the run
     * happening to favour one. Random stays available for a run that should not
     * be predictable.
     */
    private RegionPreset nextRegion() {
        List<RegionPreset> regions = targetRegions();
        if (regions.isEmpty()) {
            return null;
        }
        if (regions.size() == 1) {
            return regions.get(0);
        }
        if ("RANDOM".equalsIgnoreCase(config.get().autoRtp.order)) {
            return regions.get(random.nextInt(regions.size()));
        }
        RegionPreset region = regions.get(Math.floorMod(cursor, regions.size()));
        cursor = Math.floorMod(cursor + 1, regions.size());
        return region;
    }

    /**
     * Starts the loop. {@code acknowledged} records that the caller obtained the
     * player's explicit confirmation first.
     */
    public boolean start(boolean acknowledged) {
        if (!acknowledged) {
            statusMessage = Lang.t("auto.need_ack");
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            statusMessage = Lang.t("word.not_in_world");
            return false;
        }
        RegionPreset region = targetRegion();
        if (region == null) {
            statusMessage = Lang.t("auto.no_command", config.get().autoRtp.region);
            return false;
        }

        running = true;
        paused = false;
        startedAtMillis = System.currentTimeMillis();
        sentThisRun = 0;
        missedThisRun = 0;
        foundMessage = "";
        cursor = 0;
        lastHealth = client.player.getHealth();
        startX = client.player.getX();
        startZ = client.player.getZ();
        startDimension = Worlds.dimensionId(client.world);
        nearbyArmed = false;
        // A manual run is ready at once: making the first press wait out a
        // jitter nobody asked for would just look broken.
        if (config.get().autoRtp.manualStep) {
            nextSendAtMillis = System.currentTimeMillis();
        } else {
            scheduleNext(0);
        }
        String plan = describeRotation();
        statusMessage = Lang.t("auto.running", plan);
        RTPBuddy.LOGGER.info("[RTPBuddy] auto-RTP started ({})", plan);
        return true;
    }

    /**
     * @param reasonKey a translation key such as {@code reason.run_timer}. The
     *                  key itself is what reaches the log, so log lines stay
     *                  stable and English whatever the game language is.
     */
    public void stop(String reasonKey, Object... args) {
        if (!running) {
            return;
        }
        running = false;
        paused = false;
        statusMessage = Lang.t("auto.stopped", Lang.t(reasonKey, args));
        RTPBuddy.LOGGER.info("[RTPBuddy] auto-RTP stopped ({}) after {} teleports", reasonKey, sentThisRun);
    }

    /** Called on disconnect and world join. The loop never survives either. */
    public void onWorldTransition(String reasonKey) {
        stop(reasonKey);
        lastHealth = Float.NaN;
    }

    public void tick(MinecraftClient client) {
        if (!running) {
            return;
        }

        ClientPlayerEntity player = client.player;
        if (player == null) {
            stop("reason.left_world");
            return;
        }

        // A held loop sends nothing and judges nothing: taking damage or
        // stepping outside a guard while paused is the player acting, and
        // ending the run for it would defeat the point of holding it.
        if (paused) {
            lastHealth = player.getHealth();
            return;
        }

        AutoRtpConfig auto = config.get().autoRtp;

        if (auto.stopOnDamage && !Float.isNaN(lastHealth) && player.getHealth() < lastHealth - 0.01f) {
            stop("reason.damage");
            return;
        }
        lastHealth = player.getHealth();

        if (auto.stopOnPlayerNearby) {
            if (!nearbyArmed) {
                nearbyArmed = leftTheStartingSpot(client, player);
            }
            if (nearbyArmed) {
                double distance = nearestOtherPlayer(client, auto.playerNearbyRadius);
                if (distance >= 0) {
                    stop("reason.player_nearby", Math.round(distance));
                    announcePlayerNearby(client, distance);
                    return;
                }
            }
        }

        if (auto.maxPerSession > 0 && sentThisRun >= auto.maxPerSession) {
            stop("reason.session_limit", auto.maxPerSession);
            return;
        }
        if (auto.stopAfterMinutes > 0 && runningForMillis() > auto.stopAfterMinutes * 60_000L) {
            stop("reason.run_timer");
            return;
        }
        if (auto.stopOnGuardViolation
                && !config.get().guards.isSafe(player.getX(), player.getZ())) {
            stop("reason.outside_guards");
            return;
        }

        // Everything above still applies to a manual run - the caps, the run
        // timer, the damage and guard stops are about the run, not about its
        // rhythm. Only the sending is the player's to trigger.
        if (auto.manualStep) {
            return;
        }

        // While a capture is in flight the cooldown clock is held back, so the
        // interval is measured between landings rather than between attempts.
        if (auto.waitForCapture && capture.state() != RtpCaptureController.State.IDLE) {
            scheduleNext(auto.effectiveCooldownSeconds());
            return;
        }

        if (System.currentTimeMillis() < nextSendAtMillis) {
            return;
        }

        RegionPreset region = nextRegion();
        if (region == null) {
            stop("reason.region_no_command");
            return;
        }
        send(client, region);
        scheduleNext(auto.effectiveCooldownSeconds());
    }

    private void send(MinecraftClient client, RegionPreset region) {
        if (client.getNetworkHandler() == null) {
            stop("reason.no_connection");
            return;
        }
        // Goes through the same path a typed command takes, so the capture
        // controller sees it and arms exactly as it would for the player.
        client.getNetworkHandler().sendChatCommand(region.command);
        sentThisRun++;
        statusMessage = Lang.t("auto.sent", region.command, sentThisRun);
    }

    /** "rtp asia -> rtp eu central -> rtp east", or just the one command. */
    public String describeRotation() {
        List<RegionPreset> regions = targetRegions();
        if (regions.isEmpty()) {
            return "";
        }
        if (regions.size() == 1) {
            return regions.get(0).command;
        }
        StringBuilder text = new StringBuilder();
        for (RegionPreset region : regions) {
            if (text.length() > 0) {
                text.append(" → ");
            }
            text.append(region.command);
        }
        return text.toString();
    }

    private void scheduleNext(int cooldownSeconds) {
        AutoRtpConfig auto = config.get().autoRtp;
        int jitter = auto.jitterSeconds > 0 ? random.nextInt(auto.jitterSeconds + 1) : 0;
        nextSendAtMillis = System.currentTimeMillis() + (cooldownSeconds + jitter) * 1000L;
    }
}
