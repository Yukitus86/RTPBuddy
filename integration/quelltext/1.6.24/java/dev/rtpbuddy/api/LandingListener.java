package dev.rtpbuddy.api;

/**
 * Told about a landing the moment it is recorded.
 *
 * <p>Called on the client thread, inside the tick that recorded the landing and
 * after it has been added to the store - so {@link RTPBuddyApi#landings()}
 * already contains it. That also means the call is on the game's own thread:
 * anything slow belongs on a thread of the listener's own, or the frame waits
 * for it.
 *
 * <p>A listener that throws is logged and dropped from the notification for
 * that landing; it stays registered. Nothing a partner mod does here can stop a
 * landing from being recorded.
 */
@FunctionalInterface
public interface LandingListener {

    void onLanding(Landing landing);
}
