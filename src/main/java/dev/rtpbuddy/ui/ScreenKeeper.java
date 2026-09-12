package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;

/**
 * Keeps the map on screen across a teleport.
 *
 * <p>Two mechanisms, in this order:
 *
 * <ol>
 *   <li>{@link #shouldRefuse} is asked from a mixin on the client's own
 *       {@code setScreen}, and declines the swap while the map is up and the
 *       incoming screen is one of the client's transient loading screens. This
 *       is what makes a teleport leave the map exactly where it was - no blink,
 *       no rebuild, and every filter, scroll position and selection intact,
 *       because it is the same screen object throughout.</li>
 *   <li>{@link #tick} is the fallback. If a screen does get through - a path
 *       that does not go via {@code setScreen}, or a version where the loading
 *       screens are different classes - the same map instance is put back as
 *       soon as the world is there and nothing else has claimed the screen.</li>
 * </ol>
 *
 * <p>Both are deliberately narrow. Only a map the player already had open is
 * ever restored, only transient screens are ever refused, escape always means
 * closed, and anything the player or the server opens on purpose - a disconnect
 * screen above all - is left alone.
 */
public final class ScreenKeeper {

    /** How long the fallback keeps trying after the map went missing. */
    private static final long WINDOW_MILLIS = 20_000L;

    /**
     * Restores allowed inside one window. A client that insists on clearing the
     * screen every tick would otherwise be fought forever, at 20 blinks a
     * second; giving up after this many leaves the player in control.
     */
    private static final int MAX_RESTORES = 40;

    /** The live map, kept so the restore is the same object and not a copy. */
    private static MapScreen kept;

    /** Set while the player's own close is in flight, so escape is honoured. */
    private static boolean closing;

    private static long missingSince;
    private static int restores;

    private ScreenKeeper() {
    }

    /** The player pressed escape: this closure is final. */
    static void onMapClosed() {
        closing = true;
        kept = null;
        missingSince = 0;
        restores = 0;
    }

    /** Drops any pending restore. Used when leaving the server. */
    public static void cancel() {
        kept = null;
        closing = false;
        missingSince = 0;
        restores = 0;
    }

    /**
     * Whether the client's attempt to replace the current screen should be
     * declined. Called from the mixin on every single {@code setScreen}, so it
     * answers false as early as it can.
     */
    public static boolean shouldRefuse(MinecraftClient client, Screen incoming) {
        if (closing || client == null) {
            return false;
        }
        if (!(client.currentScreen instanceof MapScreen)) {
            return false;
        }
        if (!transitional(incoming)) {
            // A screen somebody opened on purpose - settings, a dialog, a
            // disconnect notice - always wins over the map.
            return false;
        }
        return RTPBuddyClient.config().map.reopenAfterTeleport;
    }

    public static void tick(MinecraftClient client) {
        Screen screen = client.currentScreen;

        if (screen instanceof MapScreen map) {
            kept = map;
            closing = false;
            missingSince = 0;
            restores = 0;
            return;
        }

        if (kept == null) {
            closing = false;
            return;
        }
        if (!RTPBuddyClient.config().map.reopenAfterTeleport || !transitional(screen)) {
            cancel();
            return;
        }
        long now = System.currentTimeMillis();
        if (missingSince == 0) {
            missingSince = now;
        }
        if (now - missingSince > WINDOW_MILLIS || restores >= MAX_RESTORES) {
            cancel();
            return;
        }
        if (client.world == null || client.player == null) {
            // The world is still coming in. The map draws the player, so it
            // waits for one rather than rendering half a screen.
            return;
        }
        restores++;
        client.setScreen(kept);
    }

    /**
     * The client's own between-worlds screens: shown while a teleport or a
     * reconfiguration is in flight, and cleared again by the client itself.
     * Nothing the player opened is in this set.
     *
     * <p>{@link LevelLoadingScreen} is the one that matters most and the one
     * that was missing. On 1.21.11 it is what took over from the old terrain
     * screen: {@code ClientPlayNetworkHandler.startWorldLoading} puts it up on
     * every dimension change and every respawn - a shard switch on a server
     * included - through {@code setScreenAndRender}, which goes via
     * {@code setScreen} and so past the mixin. Left out of this set, both halves
     * of the keeper failed at once: the refusal let it replace the map, and the
     * fallback took it for a screen opened on purpose and gave up.
     *
     * <p>Declining it is safe because it only watches. The load progress belongs
     * to the network handler, the screen's tick reads it for the bar and closes
     * the screen when it is done, and its close sends nothing to the server - so
     * the world goes on loading behind the map exactly as it would behind the
     * loading screen.
     */
    private static boolean transitional(Screen screen) {
        return screen == null
                || screen instanceof MessageScreen
                || screen instanceof ProgressScreen
                || screen instanceof ReconfiguringScreen
                || screen instanceof LevelLoadingScreen;
    }
}
