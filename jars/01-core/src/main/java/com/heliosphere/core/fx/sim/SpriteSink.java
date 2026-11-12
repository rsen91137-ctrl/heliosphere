package com.heliosphere.core.fx.sim;

/**
 * Receives the sprites of an effect for one frame. Positions are world coordinates. A sprite both absorbs
 * (smoke colour {@code r,g,b} at opacity {@code a}) and emits ({@code er,eg,eb}, added on top), so one
 * quad can be anything from dark smoke to pure light.
 */
public interface SpriteSink {
    /** Camera-facing square of edge {@code size}, rolled by {@code roll} radians around the view axis. */
    void billboard(double x, double y, double z, float size, float roll, int tile,
                   float r, float g, float b, float a, float er, float eg, float eb);

    /** Camera-facing ribbon from A to B of the given width; the tile's u axis runs along the ribbon. */
    void ribbon(double ax, double ay, double az, double bx, double by, double bz, float width, int tile,
                float r, float g, float b, float a, float er, float eg, float eb);

    /** Horizontal square (lying on the ground) of edge {@code size}, rotated by {@code yaw} radians. */
    void ground(double x, double y, double z, float size, float yaw, int tile,
                float r, float g, float b, float a, float er, float eg, float eb);
}
