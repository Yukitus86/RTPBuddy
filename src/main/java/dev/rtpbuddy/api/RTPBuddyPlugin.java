package dev.rtpbuddy.api;

/**
 * What another mod implements to be handed the {@link RTPBuddyApi}.
 *
 * <p>Declare the implementing class under the {@code rtpbuddy} entrypoint in
 * the partner mod's {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "client": [ "dev.example.ExampleClient" ],
 *   "rtpbuddy": [ "dev.example.ExampleRtpBuddyLink" ]
 * }
 * }</pre>
 *
 * <p>This, rather than a static lookup, is the way in - because of what happens
 * when RTPBuddy is <em>not</em> installed. The loader only loads an entrypoint
 * class when something asks for that entrypoint, and the only mod that ever
 * asks for {@code rtpbuddy} is RTPBuddy. With RTPBuddy absent the class is
 * never loaded, so the API types it mentions are never looked up, so there is
 * no {@code NoClassDefFoundError} to guard against. The partner mod needs no
 * {@code depends} on RTPBuddy and keeps working on its own.
 *
 * <p>The one rule that follows from that: keep every mention of
 * {@code dev.rtpbuddy.api} inside the classes reached from this entrypoint. A
 * field of type {@link Landing} on the partner's own initializer would undo the
 * whole arrangement.
 */
public interface RTPBuddyPlugin {

    /**
     * Called once, during RTPBuddy's client init, after its store and
     * controllers are up and before the first landing can be recorded.
     *
     * <p>Register listeners here. The api handed in stays valid for the rest of
     * the game's life, so it can be held onto.
     *
     * <p>Throwing from here is logged against the partner mod by name and does
     * not stop RTPBuddy or any other plugin from starting.
     */
    void onRTPBuddyReady(RTPBuddyApi api);
}
