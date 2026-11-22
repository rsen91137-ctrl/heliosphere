package com.heliosphere.core.machine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Machines that wait for their start button (the assembly station, the food synthesizer): nothing is made until the
 * player has put everything in and pressed start. The recipe that matches at that moment is locked in and made again
 * and again while the materials last; then the machine stops and waits for the next press. A recipe can also be
 * chosen by clicking it in the screen's recipe list: the machine then makes exactly that one, waits when the
 * materials run short and goes on when they come back, until the stop button. Implemented on MachineBlockEntity.
 */
public interface Startable {
    /** Machine types with a start button besides the assembly station; modules add theirs while starting up ({@link #gate}). */
    Set<String> GATED = ConcurrentHashMap.newKeySet();
    /** Added to the index of a recipe chosen from the list ({@link #heliosphere$locked}). */
    int CHOSEN = 10000;
    /** Button id of the recipe list's first entry ({@code clickMenuButton}); entry {@code i} is {@code FIRST_RECIPE + i}. */
    int FIRST_RECIPE = 100;

    /** Machine types with a start button. */
    static boolean gated(MachineType t) { return t != null && ("assembly_station".equals(t.id) || GATED.contains(t.id)); }

    /** Gives the machine type {@code id} a start button and a choosable recipe list (both sides, while starting up). */
    static void gate(String id) { GATED.add(id); }

    boolean heliosphere$armed();

    void heliosphere$arm(boolean on);

    /** Index of the recipe locked in by the last press, {@link #CHOSEN} + the index of one chosen from the list, or -1. */
    int heliosphere$locked();

    void heliosphere$lock(int index);
}
