package dev.rtpbuddy.integration;

import dev.rtpbuddy.RTPBuddy;
import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.api.Landing;
import dev.rtpbuddy.api.LandingListener;
import dev.rtpbuddy.api.RTPBuddyApi;
import dev.rtpbuddy.api.RTPBuddyPlugin;
import dev.rtpbuddy.api.Sitting;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.data.SessionRecord;
import dev.rtpbuddy.region.ServerRegions;
import dev.rtpbuddy.util.Worlds;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The live side of {@link RTPBuddyApi}: reads the running store and controllers,
 * and hands the result out in the api's own shapes.
 *
 * <p>Kept out of {@code dev.rtpbuddy.api} on purpose. That package is the
 * contract and holds nothing that reaches into the mod or into Minecraft, so it
 * stays readable - and compilable - on its own. This class is the only place
 * the two sides meet.
 */
public final class RTPBuddyApiImpl implements RTPBuddyApi {

    /** How much of a partner mod's stop reason reaches the chat line. */
    private static final int REASON_LIMIT = 80;

    private static RTPBuddyApiImpl instance;

    private final List<LandingListener> listeners = new CopyOnWriteArrayList<>();

    private RTPBuddyApiImpl() {
    }

    /**
     * Publishes the api and hands it to every mod that declared a
     * {@link RTPBuddyPlugin}.
     *
     * <p>Each plugin is loaded and called on its own, named by the mod it came
     * from, so one partner mod that throws on the way up cannot take RTPBuddy or
     * the other partners down with it. That is the whole reason this does not
     * simply use {@code getEntrypoints}: a failure there is one exception for
     * the whole list, with nothing in it to say whose fault it was.
     */
    public static void start() {
        if (instance != null) {
            return;
        }
        instance = new RTPBuddyApiImpl();
        RTPBuddyApi.Holder.set(instance);

        List<EntrypointContainer<RTPBuddyPlugin>> found;
        try {
            found = FabricLoader.getInstance()
                    .getEntrypointContainers(RTPBuddyApi.ENTRYPOINT, RTPBuddyPlugin.class);
        } catch (Throwable t) {
            RTPBuddy.LOGGER.error("[RTPBuddy] could not read '{}' entrypoints: {}",
                    RTPBuddyApi.ENTRYPOINT, t.toString());
            return;
        }

        int started = 0;
        for (EntrypointContainer<RTPBuddyPlugin> container : found) {
            String from = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().onRTPBuddyReady(instance);
                started++;
                RTPBuddy.LOGGER.info("[RTPBuddy] connected mod '{}'", from);
            } catch (Throwable t) {
                RTPBuddy.LOGGER.error("[RTPBuddy] mod '{}' failed to connect: {}", from, t.toString());
            }
        }
        if (started > 0) {
            RTPBuddy.LOGGER.info("[RTPBuddy] api v{} open to {} mod(s)", API_VERSION, started);
        }
    }

    /**
     * Tells the listeners about a landing. Called once per recorded landing,
     * after the store already holds it, so a listener that asks for
     * {@link #landings()} gets one that includes it.
     */
    public static void fireLanding(RtpSample sample) {
        if (instance == null || instance.listeners.isEmpty()) {
            return;
        }
        Landing landing = toLanding(sample);
        for (LandingListener listener : instance.listeners) {
            try {
                listener.onLanding(landing);
            } catch (Throwable t) {
                RTPBuddy.LOGGER.error("[RTPBuddy] a landing listener threw: {}", t.toString());
            }
        }
    }

    // ----------------------------------------------------------------- shapes

    private static Landing toLanding(RtpSample s) {
        return new Landing(
                s.sample(),
                s.sessionId(),
                s.x(), s.y(), s.z(),
                s.dimension(),
                s.timestamp(),
                s.requestedRegion(),
                s.travelDistance(),
                s.latencyMs(),
                s.biome(),
                s.surfaceY(),
                RtpSample.CAPTURE_MANUAL.equals(s.captureMode()),
                s.server(),
                s.note());
    }

    private static Sitting toSitting(SessionRecord r) {
        return new Sitting(r.id, r.startedAt, r.endedAt, r.server, r.label, r.isLegacy());
    }

    private static List<Landing> toLandings(List<RtpSample> samples) {
        List<Landing> out = new ArrayList<>(samples.size());
        for (RtpSample s : samples) {
            out.add(toLanding(s));
        }
        return List.copyOf(out);
    }

    // -------------------------------------------------------------- the api

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(RTPBuddy.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public Path dataDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(RTPBuddy.CONFIG_DIR);
    }

    @Override
    public List<Landing> landings() {
        return toLandings(RTPBuddyClient.store().samples());
    }

    @Override
    public List<Landing> landingsOf(String sittingId) {
        if (sittingId == null) {
            return List.of();
        }
        return toLandings(RTPBuddyClient.store().samplesOf(sittingId));
    }

    @Override
    public List<Landing> landingsOfCurrentSitting() {
        String id = RTPBuddyClient.sessions().currentId();
        return id == null ? List.of() : landingsOf(id);
    }

    @Override
    public Landing lastLanding() {
        List<RtpSample> live = RTPBuddyClient.store().samplesView();
        // Read the size once: the list is the store's own, and a landing
        // recorded between the check and the read would make an index taken
        // from the first read point past the end of the second.
        int size = live.size();
        return size == 0 ? null : toLanding(live.get(size - 1));
    }

    @Override
    public int landingCount() {
        return RTPBuddyClient.store().size();
    }

    @Override
    public List<Sitting> sittings() {
        List<SessionRecord> records = RTPBuddyClient.store().sessions();
        List<Sitting> out = new ArrayList<>(records.size());
        for (SessionRecord r : records) {
            out.add(toSitting(r));
        }
        return List.copyOf(out);
    }

    @Override
    public Sitting currentSitting() {
        SessionRecord current = RTPBuddyClient.sessions().current();
        return current == null ? null : toSitting(current);
    }

    @Override
    public void addLandingListener(LandingListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public boolean removeLandingListener(LandingListener listener) {
        return listener != null && listeners.remove(listener);
    }

    @Override
    public boolean autoRtpRunning() {
        return RTPBuddyClient.anythingRunning();
    }

    @Override
    public void stopEverything(String reason) {
        String said = tidy(reason);
        if (said.isEmpty()) {
            RTPBuddyClient.panicStop("reason.api");
        } else {
            RTPBuddyClient.panicStop("reason.api_with", said);
        }
    }

    /**
     * The partner mod's reason, made fit to put in front of the player.
     *
     * <p>It is shown in chat, so it cannot be used as a translation key the way
     * the mod's own reasons are - it would render as the key itself. It is also
     * a string from somewhere else: the section sign is dropped so a reason
     * cannot colour or obfuscate the rest of the line, newlines with it, and the
     * whole thing is cut to a length that stays one message.
     */
    private static String tidy(String reason) {
        if (reason == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(reason.length(), REASON_LIMIT));
        for (int i = 0; i < reason.length() && out.length() < REASON_LIMIT; i++) {
            char c = reason.charAt(i);
            if (c == '\u00a7' || c == '\n' || c == '\r') {
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    @Override
    public int cellNumberAt(double x, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return 0;
        }
        ServerRegions.Cell cell = RTPBuddyClient.config().serverRegionAt(
                Worlds.serverAddress(client), Worlds.dimensionId(client.world), x, z);
        return cell == null ? 0 : cell.number();
    }
}
