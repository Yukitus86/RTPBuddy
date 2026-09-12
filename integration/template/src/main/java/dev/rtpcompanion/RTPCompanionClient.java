package dev.rtpcompanion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's own entrypoint - the half that has nothing to do with RTPBuddy.
 *
 * <p>Note what is missing: no check for whether RTPBuddy is installed, no
 * import from it, nothing that behaves differently when it is absent. Start
 * this mod on its own and {@code /rtpcompanion} says RTPBuddy is not there;
 * start it beside RTPBuddy and the same command shows what came across. The
 * link that makes the difference is {@link RTPBuddyLink}, and the loader is
 * what decides whether it runs.
 */
public class RTPCompanionClient implements ClientModInitializer {

    public static final String MOD_ID = "rtpcompanion";

    static final Logger LOGGER = LoggerFactory.getLogger("RTPCompanion");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("rtpcompanion")
                        .executes(context -> status(context.getSource()))));

        LOGGER.info("[RTPCompanion] ready");
    }

    private static int status(FabricClientCommandSource source) {
        if (!LandingLog.connected()) {
            // Not an error: this mod is meant to work without RTPBuddy. Say
            // which of the two situations this is and stop.
            source.sendFeedback(Text.translatable("rtpcompanion.status.alone"));
            return 0;
        }

        source.sendFeedback(Text.translatable("rtpcompanion.status.connected",
                LandingLog.rtpBuddyVersion()));
        source.sendFeedback(Text.translatable("rtpcompanion.status.seen",
                LandingLog.seenThisGame()));
        if (!LandingLog.lastLanding().isBlank()) {
            source.sendFeedback(Text.translatable("rtpcompanion.status.last",
                    LandingLog.lastLanding()));
        }
        return 1;
    }
}
