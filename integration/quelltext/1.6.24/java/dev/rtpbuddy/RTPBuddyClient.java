package dev.rtpbuddy;

import dev.rtpbuddy.capture.AutoRtpController;
import dev.rtpbuddy.capture.RtpCaptureController;
import dev.rtpbuddy.config.ConfigManager;
import dev.rtpbuddy.config.RTPBuddyConfig;
import dev.rtpbuddy.data.SampleStore;
import dev.rtpbuddy.data.SchemaMigrator;
import dev.rtpbuddy.hud.CaptureHud;
import dev.rtpbuddy.input.RTPBuddyKeys;
import dev.rtpbuddy.session.SessionManager;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Worlds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RTPBuddyClient implements ClientModInitializer {

    private static ConfigManager configManager;
    private static SampleStore store;
    private static SessionManager sessions;
    private static RtpCaptureController capture;
    private static AutoRtpController autoRtp;

    private String lastDimension;
    private int saveTickCounter;

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(RTPBuddy.CONFIG_DIR);
        Path legacyDir = FabricLoader.getInstance().getConfigDir().resolve(RTPBuddy.LEGACY_CONFIG_DIR);
        Path exportDir = FabricLoader.getInstance().getGameDir().resolve(RTPBuddy.CONFIG_DIR).resolve("exports");

        try {
            Files.createDirectories(configDir);
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            RTPBuddy.LOGGER.error("[RTPBuddy] could not create data directories: {}", e.toString());
        }

        configManager = new ConfigManager(configDir);
        configManager.load();

        // Statistics count a landing under the region it fell in, which on a
        // server with a region grid is not the region the command named.
        dev.rtpbuddy.stats.SampleStats.setRegionKey(sample -> config().regionKey(sample));
        // Same rule for the cell board: a landing counts towards the grid only
        // where the grid exists, so singleplayer and other servers stay off it
        // rather than being dropped onto whatever cell their coordinates hit.
        dev.rtpbuddy.stats.CellCoverage.setResolver(sample -> config().serverRegion(sample));

        store = new SampleStore(configDir, exportDir);
        store.setWriteCsvMirror(config().capture.writeCsvMirror);
        store.load();

        sessions = new SessionManager(store);
        capture = new RtpCaptureController(RTPBuddyClient::config, store, sessions);
        autoRtp = new AutoRtpController(RTPBuddyClient::config, capture);
        capture.setOnCaptured(RTPBuddyClient::onLanding);


        runLegacyImportOnce(legacyDir);

        // After the store and the controllers, before the first landing can be
        // recorded: a partner mod registering a listener here never misses one.
        dev.rtpbuddy.integration.RTPBuddyApiImpl.start();

        RTPBuddyKeys.register();
        dev.rtpbuddy.command.RTPBuddyCommands.register();
        registerEvents();

        HudElementRegistry.addLast(Identifier.of(RTPBuddy.MOD_ID, "capture_hud"), new CaptureHud());
        HudElementRegistry.addLast(Identifier.of(RTPBuddy.MOD_ID, "minimap"),
                new dev.rtpbuddy.hud.Minimap());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sessions.end();
            store.shutdown();
        }, "RTPBuddy-Shutdown"));

        RTPBuddy.LOGGER.info("[RTPBuddy] ready: {} samples, {} sessions",
                store.size(), store.sessions().size());
    }

    private void registerEvents() {
        // Passive observation of the player's own outgoing commands. The event is
        // fired after the command has already been sent; nothing is intercepted.
        ClientSendMessageEvents.COMMAND.register(command -> capture.onCommandSent(command));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            autoRtp.onWorldTransition("reason.world_join");
            capture.resetSessionCounters();
            sessions.begin(Worlds.serverAddress(client));
            lastDimension = client.world == null ? null : Worlds.dimensionId(client.world);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Never resume automation across a reconnect.
            autoRtp.onWorldTransition("reason.disconnect");
            // A restore queued by a teleport must not survive the server itself.
            dev.rtpbuddy.ui.ScreenKeeper.cancel();
            capture.reset();
            sessions.end();
            store.flushNow();
            lastDimension = null;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        handleKeys(client);
        // Runs before the player check: the map is restored once the world is
        // back, and the world coming back is exactly what this is waiting for.
        dev.rtpbuddy.ui.ScreenKeeper.tick(client);

        if (client.player == null || client.world == null) {
            return;
        }

        lastDimension = Worlds.dimensionId(client.world);

        capture.tick(client);
        autoRtp.tick(client);

        // Cheap heartbeat: keeps the session end time fresh and lets the store
        // decide whether its debounce window has elapsed.
        if (++saveTickCounter >= 20) {
            saveTickCounter = 0;
            sessions.touch();
            store.scheduleSave(config().capture.saveDebounceMillis);
        }
    }

    private void handleKeys(MinecraftClient client) {
        while (RTPBuddyKeys.panic.wasPressed()) {
            panicStop("reason.panic_key");
        }
        if (client.player == null) {
            return;
        }
        // One map key. It reopens at whatever scope was last used, which is why
        // the second key that forced "all sessions" is gone: the scope is saved
        // with the rest of the view, so the two keys opened the same screen.
        // "All sessions" is still reachable in the map itself and via
        // /rtpbuddy all.
        while (RTPBuddyKeys.openSessionMap.wasPressed()) {
            client.setScreen(new dev.rtpbuddy.ui.MapScreen());
        }
        while (RTPBuddyKeys.openSettings.wasPressed()) {
            client.setScreen(new dev.rtpbuddy.ui.SettingsScreen(null));
        }
        while (RTPBuddyKeys.manualCapture.wasPressed()) {
            capture.captureManual(null);
        }
        // One key for the whole run: it opens the start dialog when nothing is
        // running, and from then on it holds and continues. Stopping is End's
        // job alone, which keeps the one irreversible action on a key of its own
        // rather than one press away from a hold.
        while (RTPBuddyKeys.toggleAutoRtp.wasPressed()) {
            if (!autoRtp.running()) {
                client.setScreen(new dev.rtpbuddy.ui.AutoRtpDialog(null, started -> {
                }));
            } else if (autoRtp.manualStep()) {
                // In a manual run there is no countdown to hold, so the same key
                // does the only thing left to do: send the next one.
                //
                // A press that worked says nothing: the command itself is already
                // in the chat, the server answers it, and the HUD counts it. Only
                // a press that did nothing has something to report, because that
                // is the one case the screen would otherwise leave unexplained.
                if (!autoRtp.stepNow()) {
                    tell(client, autoRtp.statusMessage());
                }
            } else {
                boolean held = autoRtp.togglePaused();
                tell(client, held
                        ? Lang.t("chat.auto_paused", autoRtp.sentThisRun())
                        : Lang.t("chat.auto_resumed", Math.round(autoRtp.secondsUntilNext())));
            }
        }
    }

    /** True while anything that acts on its own is live. */
    /**
     * Everything that has to happen once per recorded landing, in one place.
     *
     * <p>Both jobs here would otherwise be polls. The board has to notice the
     * first landing in a cell, and the search order has to judge the landing
     * that just arrived; asking either question on the frame or tick path means
     * walking the store for an answer that is handed to us here for free.
     */
    private static void onLanding(dev.rtpbuddy.data.RtpSample sample) {
        // Reads the board that already includes this landing, so a cell it
        // reached for the first time stands at exactly one.
        dev.rtpbuddy.stats.CellCoverage coverage =
                dev.rtpbuddy.stats.CellCoverage.global(store.samplesView(), store.revision());
        dev.rtpbuddy.region.ServerRegions.Cell cell = config().serverRegion(sample);
        int number = cell == null ? 0 : cell.number();

        if (number > 0 && coverage.count(number) == 1) {
            dev.rtpbuddy.hud.Minimap.flashCell(number);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal(Lang.t("chat.new_cell", number,
                        coverage.hitCells(),
                        dev.rtpbuddy.stats.CellCoverage.totalCells())), false);
            }
        }

        autoRtp.onLanding(sample, coverage, number);

        // Last: a partner mod sees the landing only once the mod's own work on
        // it is done, so what it reads back is the finished state and not a
        // half-updated one.
        dev.rtpbuddy.integration.RTPBuddyApiImpl.fireLanding(sample);
    }

    public static boolean anythingRunning() {
        return autoRtp.running();
    }

    /**
     * Immediate stop for everything that can act on its own, which today means
     * the auto-RTP loop.
     *
     * <p>Safe to call at any time and from any screen. It reports exactly what was
     * running rather than a fixed message, so pressing it when nothing is active
     * says so instead of implying something was stopped.
     */
    public static void panicStop(String reasonKey, Object... reasonArgs) {
        java.util.List<String> stopped = new java.util.ArrayList<>();

        if (autoRtp.running()) {
            stopped.add(Lang.t("chat.panic.auto", autoRtp.sentThisRun()));
            autoRtp.stop(reasonKey, reasonArgs);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        tell(client, stopped.isEmpty()
                ? Lang.t("chat.panic_idle")
                : Lang.t("chat.panic_stopped", String.join(", ", stopped)));
        RTPBuddy.LOGGER.info("[RTPBuddy] panic stop ({}): {}", reasonKey,
                stopped.isEmpty() ? "nothing running" : String.join(", ", stopped));
    }

    private static void tell(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    /**
     * Imports the predecessor mod's data exactly once. The marker lives in
     * config.json so a failed or empty import is not retried on every launch.
     */
    private void runLegacyImportOnce(Path legacyDir) {
        RTPBuddyConfig cfg = config();
        if (cfg.legacyImportDone) {
            return;
        }
        SchemaMigrator.Result result =
                SchemaMigrator.importLegacy(legacyDir, store, store.peekNextSampleNumber());
        if (!result.sourcePresent()) {
            // Nothing to import now, but a user may drop an old file in later.
            return;
        }
        cfg.legacyImportDone = true;
        cfg.legacyImportedAt = System.currentTimeMillis();
        cfg.legacyImportedCount = result.imported();
        configManager.save();
    }

    public static ConfigManager configManager() {
        return configManager;
    }

    public static RTPBuddyConfig config() {
        return configManager.get();
    }

    public static SampleStore store() {
        return store;
    }

    public static SessionManager sessions() {
        return sessions;
    }

    public static RtpCaptureController capture() {
        return capture;
    }

    public static AutoRtpController autoRtp() {
        return autoRtp;
    }

}
