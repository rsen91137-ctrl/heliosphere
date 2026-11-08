package com.heliosphere.core.machine;

/** Anything that stores Heliosphere energy (HE). */
public interface EnergyNode {
    long energy();
    long capacity();
    void setEnergy(long e);
    boolean canReceive();
    boolean canExtract();

    default long receive(long max) {
        if (!canReceive()) return 0;
        long acc = Math.min(max, capacity() - energy());
        if (acc > 0) setEnergy(energy() + acc);
        return Math.max(0, acc);
    }
}
