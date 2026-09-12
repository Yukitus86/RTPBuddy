package dev.rtpbuddy.ui;

import net.minecraft.client.MinecraftClient;

/**
 * Caps the frame rate while one of our screens is open.
 *
 * <p>Minecraft only throttles a screen when there is no world behind it: the
 * title screen and the pause menu run at ten frames a second, but a screen
 * opened in-game renders as fast as the video setting allows. The map is a
 * static picture between inputs, so redrawing it two or three hundred times a
 * second buys nothing and heats the CPU - which is exactly what shows up when
 * auto-RTP is running and the map is left open to watch it.
 *
 * <p>{@code InactivityFpsLimiter} owns the number the render loop reads, so the
 * cap goes there and comes back off on close. The restore reads the option
 * rather than a remembered value, so changing the FPS slider while the map is
 * open still wins.
 */
public final class FrameLimiter {

    private boolean active;

    /** Applies the cap. A limit of zero or less leaves the game's own setting alone. */
    public void apply(int limit) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (limit <= 0 || client == null) {
            return;
        }
        int configured = client.options.getMaxFps().getValue();
        if (limit >= configured) {
            // Already slower than we would ask for; nothing to do, and nothing
            // to undo later either.
            return;
        }
        client.getInactivityFpsLimiter().setMaxFps(limit);
        active = true;
    }

    /** Hands the limit back to the video setting. Safe to call when nothing was capped. */
    public void release() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active || client == null) {
            return;
        }
        active = false;
        client.getInactivityFpsLimiter().setMaxFps(client.options.getMaxFps().getValue());
    }
}
