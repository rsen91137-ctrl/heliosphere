package com.heliosphere.hostiles.arsenal;

/** How dangerous each world is: the usual fortress difficulty (1-15) there. Earth also scales with distance from spawn. */
public final class WorldTiers {
    private WorldTiers() {}

    public static int base(String body) {
        return switch (body == null ? "" : body) {
            case "earth" -> 1;
            case "moon" -> 4;
            case "mars", "phobos", "deimos" -> 6;
            case "mercury", "venus" -> 8;
            case "ceres", "vesta", "io", "europa", "ganymede", "callisto" -> 9;
            case "titan", "enceladus" -> 11;
            case "miranda", "triton", "pluto", "charon", "eris" -> 13;
            default -> 5;
        };
    }

    /** Spread around the base value on each world. */
    public static int spread(String body) {
        return switch (body == null ? "" : body) { case "earth" -> 0; case "moon" -> 4; default -> 4; };
    }
}
