package dev.rtpbuddy.ui;

import dev.rtpbuddy.data.RtpSample;

import java.util.List;

/**
 * Pan and zoom for a top-down XZ view. Zoom is expressed in screen pixels per
 * world block, so the projection is a single multiply in each direction.
 *
 * <p>Screen Y grows downwards and world Z grows south, so the two map directly:
 * north ends up at the top, matching every other Minecraft map.
 */
public class MapViewState {

    /**
     * Lowest zoom, in screen pixels per block.
     *
     * <p>This used to be 0.0005, which frames some 1.3 million blocks across a
     * normal canvas - plenty for a 450 000 block world, and nowhere near enough
     * to pull back far enough to see a vanilla 30 000 000 block border. The
     * floor now clears the vanilla 60 million block border across a canvas of a
     * hundred-odd scaled pixels, which is what a large GUI scale leaves, so the
     * whole configured world can always be brought into view.
     */
    public static final double MIN_ZOOM = 0.000001;
    public static final double MAX_ZOOM = 8.0;

    private double centerX;
    private double centerZ;
    private double zoom = 0.02;

    private int boundsX;
    private int boundsY;
    private int boundsWidth;
    private int boundsHeight;

    public void setBounds(int x, int y, int width, int height) {
        this.boundsX = x;
        this.boundsY = y;
        this.boundsWidth = Math.max(1, width);
        this.boundsHeight = Math.max(1, height);
    }

    public int boundsX() {
        return boundsX;
    }

    public int boundsY() {
        return boundsY;
    }

    public int boundsWidth() {
        return boundsWidth;
    }

    public int boundsHeight() {
        return boundsHeight;
    }

    public int right() {
        return boundsX + boundsWidth;
    }

    public int bottom() {
        return boundsY + boundsHeight;
    }

    public boolean contains(double screenX, double screenY) {
        return screenX >= boundsX && screenX < right() && screenY >= boundsY && screenY < bottom();
    }

    public double centerX() {
        return centerX;
    }

    public double centerZ() {
        return centerZ;
    }

    public double zoom() {
        return zoom;
    }

    public void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
    }

    public void setZoom(double value) {
        this.zoom = clampZoom(value);
    }

    public void restore(double x, double z, double zoomValue) {
        setCenter(x, z);
        setZoom(zoomValue);
    }

    // ----------------------------------------------------------- projection

    public double worldToScreenX(double worldX) {
        return boundsX + boundsWidth / 2.0 + (worldX - centerX) * zoom;
    }

    public double worldToScreenY(double worldZ) {
        return boundsY + boundsHeight / 2.0 + (worldZ - centerZ) * zoom;
    }

    public double screenToWorldX(double screenX) {
        return centerX + (screenX - (boundsX + boundsWidth / 2.0)) / zoom;
    }

    public double screenToWorldZ(double screenY) {
        return centerZ + (screenY - (boundsY + boundsHeight / 2.0)) / zoom;
    }

    /** World-space bounds currently visible, as {minX, minZ, maxX, maxZ}. */
    public double[] visibleWorldBounds() {
        return new double[]{
                screenToWorldX(boundsX),
                screenToWorldZ(boundsY),
                screenToWorldX(right()),
                screenToWorldZ(bottom())
        };
    }

    // ------------------------------------------------------------ movement

    /** Drags the map by a screen-space delta. */
    public void panByPixels(double dx, double dy) {
        centerX -= dx / zoom;
        centerZ -= dy / zoom;
    }

    /** Zooms around a fixed screen point so the world position under it stays put. */
    public void zoomAt(double screenX, double screenY, double steps) {
        double anchorWorldX = screenToWorldX(screenX);
        double anchorWorldZ = screenToWorldZ(screenY);
        setZoom(zoom * Math.pow(1.2, steps));
        centerX = anchorWorldX - (screenX - (boundsX + boundsWidth / 2.0)) / zoom;
        centerZ = anchorWorldZ - (screenY - (boundsY + boundsHeight / 2.0)) / zoom;
    }

    public void reset() {
        centerX = 0;
        centerZ = 0;
        zoom = 0.02;
    }

    /** Frames a square world region of the given half-width around its centre. */
    public void fitSquare(double centreX, double centreZ, double halfWidth) {
        if (halfWidth <= 0) {
            return;
        }
        centerX = centreX;
        centerZ = centreZ;
        double span = halfWidth * 2;
        setZoom(Math.min(boundsWidth * 0.92 / span, boundsHeight * 0.92 / span));
    }

    /** Frames every sample with a small margin. No-op on an empty list. */
    public void fit(List<RtpSample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (RtpSample sample : samples) {
            minX = Math.min(minX, sample.x());
            maxX = Math.max(maxX, sample.x());
            minZ = Math.min(minZ, sample.z());
            maxZ = Math.max(maxZ, sample.z());
        }
        centerX = (minX + maxX) / 2.0;
        centerZ = (minZ + maxZ) / 2.0;

        double spanX = Math.max(16.0, maxX - minX);
        double spanZ = Math.max(16.0, maxZ - minZ);
        double margin = 0.85;
        setZoom(Math.min(boundsWidth * margin / spanX, boundsHeight * margin / spanZ));
    }

    private static double clampZoom(double value) {
        if (Double.isNaN(value)) {
            return 0.02;
        }
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }
}
