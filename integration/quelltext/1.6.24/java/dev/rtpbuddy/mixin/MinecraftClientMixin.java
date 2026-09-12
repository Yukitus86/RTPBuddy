package dev.rtpbuddy.mixin;

import dev.rtpbuddy.ui.ScreenKeeper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the RTP map on screen through a teleport.
 *
 * <p>A teleport that crosses worlds makes the client put up its own loading
 * screen and then clear it again, and whatever the player had open is thrown
 * away in the process - so with the auto loop running the map blinked shut on
 * every single landing. Reopening it afterwards was worse than the problem: the
 * map still vanished for a moment, and it came back as a new screen that had
 * forgotten which dimension was filtered.
 *
 * <p>So the swap is declined instead. Only the client's transient loading
 * screens are refused, only while the map is the screen that is up, and only
 * while the player has not closed it - everything else, a disconnect screen
 * above all, comes through untouched. See {@link ScreenKeeper} for the exact
 * rule and for the fallback that puts the map back if one slips past.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void rtpbuddy$keepMapOpen(Screen screen, CallbackInfo ci) {
        if (ScreenKeeper.shouldRefuse((MinecraftClient) (Object) this, screen)) {
            ci.cancel();
        }
    }
}
