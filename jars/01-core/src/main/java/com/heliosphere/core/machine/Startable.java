package com.heliosphere.core.machine;

/**
 * Machines that wait for their start button (the assembly station): nothing is made until the player has put
 * everything in and pressed start. The recipe that matches at that moment is locked in and made again and again
 * while the materials last; then the machine stops and waits for the next press. Implemented on MachineBlockEntity.
 */
public interface Startable {
    /** Machine types with a start button. */
    static boolean gated(MachineType t) { return t != null && "assembly_station".equals(t.id); }

    boolean heliosphere$armed();

    void heliosphere$arm(boolean on);

    /** Index of the recipe locked in by the last press, or -1. */
    int heliosphere$locked();

    void heliosphere$lock(int index);
}
