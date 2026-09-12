package dev.rtpcompanion;

import dev.rtpbuddy.api.Landing;
import dev.rtpbuddy.api.RTPBuddyApi;
import dev.rtpbuddy.api.RTPBuddyPlugin;
import dev.rtpbuddy.api.Sitting;

import java.util.Locale;

/**
 * The only class in this mod that knows RTPBuddy exists.
 *
 * <p>It is declared under the {@code rtpbuddy} entrypoint in
 * {@code fabric.mod.json}. RTPBuddy is the only mod that ever asks the loader
 * for that entrypoint, so with RTPBuddy absent this class is never loaded and
 * the {@code dev.rtpbuddy.api} imports above are never looked up. That is why
 * this mod needs no {@code depends} on RTPBuddy, no {@code isModLoaded} check
 * and no reflection - and why everything RTPBuddy-shaped has to stay on this
 * side of {@link LandingLog}.
 */
public class RTPBuddyLink implements RTPBuddyPlugin {

    @Override
    public void onRTPBuddyReady(RTPBuddyApi api) {
        LandingLog.connect(api.modVersion());

        if (api.apiVersion() != RTPBuddyApi.API_VERSION) {
            // Worth saying out loud rather than failing later on a method that
            // is not there: the number only moves when something that was
            // compiled against the old contract stops working.
            RTPCompanionClient.LOGGER.warn(
                    "[RTPCompanion] built against RTPBuddy api v{}, running against v{}",
                    RTPBuddyApi.API_VERSION, api.apiVersion());
        }

        Sitting sitting = api.currentSitting();
        RTPCompanionClient.LOGGER.info(
                "[RTPCompanion] connected to RTPBuddy {} - {} landings on record, sitting {}",
                api.modVersion(), api.landingCount(),
                sitting == null ? "none yet" : sitting.displayName());

        api.addLandingListener(this::onLanding);
    }

    /**
     * Runs on the client thread inside the tick that recorded the landing, so it
     * stays short. Anything that reads a file or talks to the network belongs on
     * a thread of this mod's own.
     */
    private void onLanding(Landing landing) {
        LandingLog.record(String.format(Locale.ROOT, "#%d  %.0f / %.0f / %.0f  %s  %s",
                landing.number(), landing.x(), landing.y(), landing.z(),
                shortDimension(landing.dimension()), landing.requestedRegion()));
    }

    private static String shortDimension(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        return colon < 0 ? String.valueOf(id) : id.substring(colon + 1);
    }
}
