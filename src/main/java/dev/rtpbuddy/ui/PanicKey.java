package dev.rtpbuddy.ui;

import dev.rtpbuddy.RTPBuddyClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

/**
 * End works as an immediate stop from inside RTPBuddy's own screens too.
 *
 * <p>Vanilla only accumulates key-binding presses while no screen has focus, so
 * the key binding alone would be dead exactly when a map or the settings are
 * open - which is when a running auto-RTP loop is most likely to need stopping.
 *
 * <p>It only claims the key while something is actually running. With nothing to
 * stop, End keeps its ordinary meaning and moves the caret to the end of the line
 * in whichever text field has focus.
 */
public final class PanicKey {

    private PanicKey() {
    }

    /** Returns true when the key was the panic key and has been handled. */
    public static boolean handle(KeyInput input) {
        if (input.key() != GLFW.GLFW_KEY_END || !RTPBuddyClient.anythingRunning()) {
            return false;
        }
        RTPBuddyClient.panicStop("reason.panic_key");
        return true;
    }
}
