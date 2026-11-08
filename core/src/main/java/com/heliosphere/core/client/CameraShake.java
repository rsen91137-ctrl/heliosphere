package com.heliosphere.core.client;

/** Camera shake amount in degrees, set each tick by whatever is rumbling (rocket engines, impacts). */
public final class CameraShake {
    public static volatile float amount;
    private CameraShake() {}
}
