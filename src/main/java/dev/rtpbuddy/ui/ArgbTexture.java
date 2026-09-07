package dev.rtpbuddy.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * A grid of ARGB pixels, uploaded once per rebuild and drawn as a single quad.
 *
 * <p>The obvious way to paint a field is a rectangle per cell, and it is what
 * the gap map did first. On 1.21.11 that is the wrong shape of call: every
 * {@code DrawContext.fill} allocates a {@code ColoredQuadGuiElementRenderState}
 * <em>and</em> a copy of the current matrix, then queues both for sorting. Some
 * three thousand rectangles a frame is six thousand allocations a frame, which
 * is what heated up a CPU and dropped the frame rate on a map that had barely
 * anything on it.
 *
 * <p>A texture moves that cost to where it belongs. The picture is written into
 * an image when it is rebuilt - which is at most once per view change, not once
 * per frame - and every frame in between is one textured quad.
 *
 * <p>Two things use it: the gap shading on the map screen, and the minimap in
 * the corner of the game screen. Each owns its own instance and its own
 * identifier, which is why the name is a constructor argument.
 */
public final class ArgbTexture implements AutoCloseable {

    private final Identifier id;
    private final String label;

    private NativeImageBackedTexture texture;
    private int width;
    private int height;
    private boolean filled;

    public ArgbTexture(String name) {
        this.id = Identifier.of("rtpbuddy", name);
        this.label = "RTPBuddy " + name;
    }

    public boolean ready() {
        return filled && texture != null && texture.getImage() != null;
    }

    /**
     * Uploads one grid of ARGB pixels.
     *
     * @param pixels row-major, {@code width * height} entries
     */
    public void update(int[] pixels, int width, int height) {
        filled = false;
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            return;
        }
        // A resource reload closes the textures it owns, so a texture handed out
        // earlier can come back empty. Rebuilding on a null image costs one
        // allocation in a case that happens twice a session.
        if (texture == null || texture.getImage() == null
                || this.width != width || this.height != height) {
            allocate(width, height);
        }
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                image.setColorArgb(x, y, pixels[row + x]);
            }
        }
        texture.upload();
        filled = true;
    }

    /** Stretches the grid over a rectangle. */
    public void draw(DrawContext context, int x, int y, int drawWidth, int drawHeight) {
        if (!ready() || drawWidth <= 0 || drawHeight <= 0) {
            return;
        }
        context.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0f, 0.0f,
                drawWidth, drawHeight, width, height, width, height, 0xFFFFFFFF);
    }

    private void allocate(int width, int height) {
        close();
        this.width = width;
        this.height = height;
        texture = new NativeImageBackedTexture(() -> label, width, height, false);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        filled = false;
    }
}
