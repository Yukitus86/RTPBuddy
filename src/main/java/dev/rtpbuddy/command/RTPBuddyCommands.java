package dev.rtpbuddy.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.rtpbuddy.RTPBuddyClient;
import dev.rtpbuddy.data.RtpSample;
import dev.rtpbuddy.stats.SampleStats;
import dev.rtpbuddy.ui.AutoRtpDialog;
import dev.rtpbuddy.ui.HudLayoutScreen;
import dev.rtpbuddy.ui.MapScreen;
import dev.rtpbuddy.ui.SettingsScreen;
import dev.rtpbuddy.util.Lang;
import dev.rtpbuddy.util.Numbers;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client-side commands. Registered through Fabric's client command API, so they
 * are resolved locally and no packet ever reaches the server.
 *
 * <p>The literals below are the command grammar and stay English on purpose -
 * only the output is translated.
 */
public final class RTPBuddyCommands {

    private RTPBuddyCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("rtpbuddy")
                        .executes(context -> help(context.getSource()))

                        .then(ClientCommandManager.literal("map")
                                .executes(context -> openScreen(context.getSource(),
                                        () -> new MapScreen(MapScreen.Scope.SESSION))))

                        .then(ClientCommandManager.literal("all")
                                .executes(context -> openScreen(context.getSource(),
                                        () -> new MapScreen(MapScreen.Scope.ALL))))

                        .then(ClientCommandManager.literal("config")
                                .executes(context -> openScreen(context.getSource(), () -> new SettingsScreen(null))))

                        .then(ClientCommandManager.literal("minimap")
                                .executes(context -> minimap(context.getSource(), null))
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> minimap(context.getSource(), true)))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> minimap(context.getSource(), false)))
                                .then(ClientCommandManager.literal("place")
                                        .executes(context -> openScreen(context.getSource(),
                                                () -> new HudLayoutScreen(null)))))

                        .then(ClientCommandManager.literal("stats")
                                .executes(context -> stats(context.getSource(), false))
                                .then(ClientCommandManager.literal("all")
                                        .executes(context -> stats(context.getSource(), true))))

                        .then(ClientCommandManager.literal("export")
                                .executes(context -> export(context.getSource(), false))
                                .then(ClientCommandManager.literal("all")
                                        .executes(context -> export(context.getSource(), true))))

                        .then(ClientCommandManager.literal("capture")
                                .then(ClientCommandManager.literal("pause")
                                        .executes(context -> setPaused(context.getSource(), true)))
                                .then(ClientCommandManager.literal("resume")
                                        .executes(context -> setPaused(context.getSource(), false)))
                                .then(ClientCommandManager.literal("manual")
                                        .executes(context -> manual(context.getSource(), null))
                                        .then(ClientCommandManager.argument("region", StringArgumentType.word())
                                                .executes(context -> manual(context.getSource(),
                                                        StringArgumentType.getString(context, "region"))))))

                        .then(ClientCommandManager.literal("auto")
                                .executes(context -> autoStatus(context.getSource()))
                                .then(ClientCommandManager.literal("status")
                                        .executes(context -> autoStatus(context.getSource())))
                                .then(ClientCommandManager.literal("start")
                                        .executes(context -> autoStart(context.getSource())))
                                .then(ClientCommandManager.literal("stop")
                                        .executes(context -> autoStop(context.getSource())))
                                .then(ClientCommandManager.literal("pause")
                                        .executes(context -> autoHold(context.getSource(), true)))
                                .then(ClientCommandManager.literal("resume")
                                        .executes(context -> autoHold(context.getSource(), false)))
                                .then(ClientCommandManager.literal("step")
                                        .executes(context -> autoStep(context.getSource()))))
                        .then(ClientCommandManager.literal("cells")
                                .executes(context -> cells(context.getSource())))
                        .then(ClientCommandManager.literal("find")
                                .executes(context -> findStatus(context.getSource()))
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> find(context.getSource(), true)))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> find(context.getSource(), false))))
));
    }

    // ------------------------------------------------------------- subcommands

    private static int help(FabricClientCommandSource source) {
        feedback(source, Lang.t("cmd.help"));
        for (int i = 1; i <= 5; i++) {
            feedback(source, Lang.t("cmd.help." + i));
        }
        return 1;
    }

    /**
     * Switches the corner map on or off, or reports where it stands.
     *
     * @param on null to toggle
     */
    private static int minimap(FabricClientCommandSource source, Boolean on) {
        var map = RTPBuddyClient.config().map;
        map.minimapEnabled = on == null ? !map.minimapEnabled : on;
        RTPBuddyClient.configManager().save();
        feedback(source, Lang.t(map.minimapEnabled ? "cmd.minimap.on" : "cmd.minimap.off"));
        return 1;
    }

    /**
     * The cell board as text: how far the grid is filled and what is missing.
     *
     * <p>Reads the cached board rather than recomputing one, so calling this on
     * a keybind macro costs nothing.
     */
    private static int cells(FabricClientCommandSource source) {
        var coverage = dev.rtpbuddy.stats.CellCoverage.global(
                RTPBuddyClient.store().samplesView(), RTPBuddyClient.store().revision());
        feedback(source, Lang.t("cmd.cells.head", coverage.hitCells(),
                dev.rtpbuddy.stats.CellCoverage.totalCells(), coverage.onceCells()));
        if (coverage.counted() == 0) {
            feedback(source, Lang.t("stats.cells_none"));
            return 1;
        }
        for (var zone : coverage.zonesByProgress()) {
            if (coverage.landingsIn(zone) == 0) {
                continue;
            }
            int hit = coverage.hitIn(zone);
            int all = dev.rtpbuddy.stats.CellCoverage.totalIn(zone);
            StringBuilder missing = new StringBuilder();
            for (int number : coverage.missingIn(zone)) {
                if (missing.length() > 0) {
                    missing.append(' ');
                }
                missing.append(number);
            }
            feedback(source, Lang.t("cmd.cells.zone", zone.label(), hit, all,
                    missing.length() == 0 ? Lang.t("cmd.cells.complete") : missing.toString()));
        }
        int here = dev.rtpbuddy.ui.CellBoard.playerCellNumber();
        if (here > 0) {
            feedback(source, Lang.t("cmd.cells.here", here, coverage.count(here)));
        }
        return 1;
    }

    private static int find(FabricClientCommandSource source, boolean on) {
        RTPBuddyClient.config().autoRtp.findEnabled = on;
        RTPBuddyClient.configManager().save();
        return findStatus(source);
    }

    private static int findStatus(FabricClientCommandSource source) {
        var auto = RTPBuddyClient.config().autoRtp;
        if (!dev.rtpbuddy.capture.FindRule.armed(auto)) {
            feedback(source, Lang.t(auto.findEnabled
                    ? "cmd.find.empty" : "cmd.find.off"));
            return 1;
        }
        feedback(source, Lang.t("cmd.find.on", dev.rtpbuddy.capture.FindRule.describe(auto)));
        if (auto.findGiveUpAfter > 0) {
            feedback(source, Lang.t("cmd.find.cap", auto.findGiveUpAfter));
        }
        if (RTPBuddyClient.autoRtp().running()) {
            feedback(source, Lang.t("cmd.find.progress",
                    RTPBuddyClient.autoRtp().missedThisRun()));
        }
        return 1;
    }

    /** Screens must not be opened from inside command execution, so defer a tick. */
    private static int openScreen(FabricClientCommandSource source, Supplier<Screen> factory) {
        MinecraftClient client = source.getClient();
        client.execute(() -> client.setScreen(factory.get()));
        return 1;
    }

    private static int stats(FabricClientCommandSource source, boolean everything) {
        List<RtpSample> samples = everything
                ? RTPBuddyClient.store().samples()
                : currentSessionSamples();
        SampleStats computed = SampleStats.of(samples, RTPBuddyClient.config().guards,
                RTPBuddyClient.config().map.densityCellSize);

        feedback(source, Lang.t(everything ? "cmd.stats.all" : "cmd.stats.session", computed.count));
        if (computed.count == 0) {
            return 1;
        }
        feedback(source, Lang.t("cmd.stats.distance",
                Numbers.compact(computed.distMin),
                Numbers.compact(computed.distMedian),
                Numbers.compact(computed.distMax)));
        feedback(source, Lang.t("cmd.stats.box",
                Numbers.compact(computed.width()), Numbers.compact(computed.depth()),
                Math.round(computed.centroidX), Math.round(computed.centroidZ)));
        feedback(source, Lang.t("cmd.stats.quadrants",
                Numbers.fixed(computed.quadrantShare(0), 1),
                Numbers.fixed(computed.quadrantShare(1), 1),
                Numbers.fixed(computed.quadrantShare(2), 1),
                Numbers.fixed(computed.quadrantShare(3), 1)));
        feedback(source, Lang.t("cmd.stats.uniformity",
                Lang.t(computed.uniformityVerdictKey()),
                Numbers.fixed(computed.radialChiSquare, 1)));
        return 1;
    }

    private static int export(FabricClientCommandSource source, boolean everything) {
        List<RtpSample> samples = everything ? RTPBuddyClient.store().samples() : currentSessionSamples();
        Path target = RTPBuddyClient.store().export(samples);
        if (target == null) {
            source.sendError(Text.literal(Lang.t("cmd.export.failed")));
            return 0;
        }
        feedback(source, Lang.t("cmd.export.ok", samples.size(), target));
        return 1;
    }

    private static int setPaused(FabricClientCommandSource source, boolean paused) {
        RTPBuddyClient.capture().setPaused(paused);
        feedback(source, Lang.t(paused ? "cmd.capture.paused" : "cmd.capture.resumed"));
        return 1;
    }

    private static int manual(FabricClientCommandSource source, String region) {
        RtpSample sample = RTPBuddyClient.capture().captureManual(region);
        if (sample == null) {
            source.sendError(Text.literal(
                    Lang.t("cmd.capture.failed", RTPBuddyClient.capture().statusMessage())));
            return 0;
        }
        feedback(source, Lang.t("cmd.capture.recorded", sample.sample(),
                Math.round(sample.x()), Math.round(sample.y()), Math.round(sample.z())));
        return 1;
    }

    private static int autoStatus(FabricClientCommandSource source) {
        var auto = RTPBuddyClient.autoRtp();
        var config = RTPBuddyClient.config().autoRtp;
        var region = auto.targetRegion();

        if (!auto.running()) {
            feedback(source, Lang.t("cmd.auto.stopped"));
        } else if (auto.manualStep()) {
            feedback(source, Lang.t("cmd.auto.running_manual", auto.sentThisRun(),
                    Numbers.duration(auto.runningForMillis())));
        } else {
            feedback(source, Lang.t("cmd.auto.running", Math.round(auto.secondsUntilNext()),
                    auto.sentThisRun(), Numbers.duration(auto.runningForMillis())));
        }
        feedback(source, region == null
                ? Lang.t("cmd.auto.no_command", config.region)
                : Lang.t("cmd.auto.command", region.command));
        feedback(source, Lang.t("cmd.auto.interval",
                config.effectiveCooldownSeconds(),
                config.jitterSeconds,
                config.stopAfterMinutes > 0
                        ? Lang.t("cmd.auto.interval_stop", config.stopAfterMinutes) : "",
                config.maxPerSession > 0
                        ? Lang.t("cmd.auto.interval_cap", config.maxPerSession) : ""));
        if (!auto.statusMessage().isEmpty()) {
            feedback(source, auto.statusMessage());
        }
        return 1;
    }

    /** Starting from chat still goes through the confirmation dialog. */
    private static int autoStart(FabricClientCommandSource source) {
        if (RTPBuddyClient.autoRtp().running()) {
            feedback(source, Lang.t("cmd.auto.already"));
            return 1;
        }
        MinecraftClient client = source.getClient();
        client.execute(() -> client.setScreen(new AutoRtpDialog(null, started -> {
        })));
        return 1;
    }

    private static int autoStop(FabricClientCommandSource source) {
        RTPBuddyClient.autoRtp().stop("reason.command");
        feedback(source, Lang.t("cmd.auto.stop_ok"));
        return 1;
    }

    /** Holds or continues a run without ending it. Never starts one. */
    private static int autoHold(FabricClientCommandSource source, boolean hold) {
        var auto = RTPBuddyClient.autoRtp();
        if (!auto.running()) {
            feedback(source, Lang.t("chat.auto_pause_idle"));
            return 1;
        }
        if (hold) {
            auto.pause();
            feedback(source, Lang.t("chat.auto_paused", auto.sentThisRun()));
        } else {
            auto.resume();
            feedback(source, Lang.t("chat.auto_resumed", Math.round(auto.secondsUntilNext())));
        }
        return 1;
    }

    /** Sends the next teleport of a manually stepped run. */
    private static int autoStep(FabricClientCommandSource source) {
        var auto = RTPBuddyClient.autoRtp();
        auto.stepNow();
        feedback(source, auto.statusMessage());
        return 1;
    }

    private static List<RtpSample> currentSessionSamples() {
        String sessionId = RTPBuddyClient.sessions().currentId();
        return sessionId == null ? List.of() : RTPBuddyClient.store().samplesOf(sessionId);
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal(message));
    }
}
