package dev.rtpbuddy.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** All RTPBuddy key bindings. Every one is rebindable in vanilla Controls. */
public final class RTPBuddyKeys {

    public static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("rtpbuddy", "main"));

    public static KeyBinding openSessionMap;
    public static KeyBinding manualCapture;
    public static KeyBinding toggleAutoRtp;
    public static KeyBinding openSettings;
    public static KeyBinding panic;

    private RTPBuddyKeys() {
    }

    public static void register() {
        openSessionMap = bind("key.rtpbuddy.session_map", GLFW.GLFW_KEY_M);
        manualCapture = bind("key.rtpbuddy.manual_capture", GLFW.GLFW_KEY_K);
        // Start, hold and continue on one key; only End stops. Recording has no
        // key of its own any more - it is a switch you set once a session, not
        // something reached for mid-fight, and it stays on /rtpbuddy capture
        // pause and the master switch in the settings.
        toggleAutoRtp = bind("key.rtpbuddy.toggle_auto_rtp", GLFW.GLFW_KEY_H);
        openSettings = bind("key.rtpbuddy.settings", GLFW.GLFW_KEY_O);
        // Stops everything that acts on its own, instantly and without a prompt.
        panic = bind("key.rtpbuddy.panic", GLFW.GLFW_KEY_END);
    }

    private static KeyBinding bind(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(translationKey, InputUtil.Type.KEYSYM, keyCode, CATEGORY));
    }
}
