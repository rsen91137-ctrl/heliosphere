package com.heliosphere.industry.power;

/**
 * The generators and storage of the power grid beyond the coal generator, solar panel and battery bank. Every one
 * of them is joined to machines, turrets and air defence by ordinary cable.
 *
 * <ul>
 *   <li>Wind turbine: free power where there is air, more the higher it stands and the worse the weather.</li>
 *   <li>Geothermal generator: steady and strong, set over lava or magma.</li>
 *   <li>Radioisotope thermoelectric generator: a plutonium-238 heat source runs it day and night for days, anywhere
 *       (the long lunar night, the dark beyond Jupiter).</li>
 *   <li>Hydrogen fuel cell: burns hydrogen canisters, only when power is wanted.</li>
 *   <li>Small fission reactor: enriched uranium, a great deal of power; it needs water next to it for cooling.</li>
 *   <li>Lithium battery cabinet, supercapacitor bank, grid storage array: from fast and small to slow and huge.</li>
 * </ul>
 */
public enum PowerKind {
    //            id                         battery  capacity     io      gen  fuel                 fuelTicks  maxFuel
    WIND_TURBINE("wind_turbine",              false,      40_000,     0,    90, null,                       0,  0),
    GEOTHERMAL  ("geothermal_generator",      false,      80_000,     0,   160, null,                       0,  0),
    RTG         ("rtg",                       false,      60_000,     0,    90, "plutonium_238",      120_000,  4),
    FUEL_CELL   ("hydrogen_fuel_cell",        false,     100_000,     0,   240, "canister_hydrogen",    1_200, 16),
    FISSION     ("fission_reactor",           false,     400_000,     0,   640, "enriched_uranium",    24_000,  8),
    LITHIUM     ("lithium_battery_cabinet",   true,    8_000_000, 1_000,     0, null,                       0,  0),
    SUPERCAP    ("supercapacitor_bank",       true,    1_500_000, 6_000,     0, null,                       0,  0),
    GRID        ("grid_storage_array",        true,   50_000_000, 3_000,     0, null,                       0,  0);

    public final String id, fuel;
    public final boolean battery;
    public final long capacity;
    public final int io, gen, fuelTicks, maxFuel;

    PowerKind(String id, boolean battery, long capacity, int io, int gen, String fuel, int fuelTicks, int maxFuel) {
        this.id = id; this.battery = battery; this.capacity = capacity; this.io = io; this.gen = gen;
        this.fuel = fuel; this.fuelTicks = fuelTicks; this.maxFuel = maxFuel;
    }
}
