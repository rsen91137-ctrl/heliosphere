package com.heliosphere.sphereworld.terrain;

/** One sampled terrain column. Heights are in blocks relative to the planet datum. */
public final class Column {
    public double height;
    public Mat top = Mat.REGOLITH;
    public int topDepth = 3;
    public Mat sub = Mat.ANORTHOSITE;
    public int subDepth = 20;
    public Mat rock = Mat.ANORTHOSITE;
    /** Optional buried layer (e.g. ground ice): material, depth below surface, thickness. */
    public Mat layer = null;
    public int layerDepth;
    public int layerThickness;
    /** Region tags used by the ore placer and the map baker. */
    public float kreep, highTi, iron, ice;
    /** Optional liquid filling air up to this height (blocks relative to datum), e.g. Titan's seas, Io's lava lakes. */
    public Mat liquid = null;
    public double liquidLevel;
    /** Surface colour hint for orbital maps (0xRRGGBB). */
    public int color;

    public void reset() {
        height = 0; top = Mat.REGOLITH; topDepth = 3; sub = Mat.ANORTHOSITE; subDepth = 20; rock = Mat.ANORTHOSITE;
        layer = null; layerDepth = 0; layerThickness = 0; liquid = null; liquidLevel = 0; kreep = 0; highTi = 0; iron = 0; ice = 0; color = 0x808080;
    }
}
