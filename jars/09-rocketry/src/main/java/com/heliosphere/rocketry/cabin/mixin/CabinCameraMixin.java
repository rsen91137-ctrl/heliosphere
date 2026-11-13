package com.heliosphere.rocketry.cabin.mixin;

import com.heliosphere.rocketry.cabin.client.RealCabin;
import com.heliosphere.rocketry.entity.RocketEntity;
import com.heliosphere.rocketry.flight.LaunchCabin;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the cabin the camera is your own eyes: where you stand on the deck, turned the way you look. Runs after the
 * released flight camera (which still works out the sky and the planet below), and puts the view back on you.
 *
 * <p>The eye is placed from the rocket's own render position plus your place on the deck, exactly as the cabin
 * blocks are, not from your passenger position: that one trails the rocket by a tick, which at launch speed is
 * many blocks and would leave the camera hanging under the floor. Your place on the deck is smoothed between
 * ticks so walking stays fluid.
 */
@Mixin(value = Camera.class, priority = 1500)
public abstract class CabinCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean detached;

    @Unique private static long heliosphere$tick = Long.MIN_VALUE;
    @Unique private static Vec3 heliosphere$prev = Vec3.ZERO, heliosphere$cur = Vec3.ZERO;

    @Inject(method = "setup", at = @At("TAIL"))
    private void heliosphere$ownEyes(Level level, Entity entity, boolean thirdPerson, boolean mirrored, float partial, CallbackInfo ci) {
        if (thirdPerson || !RealCabin.active()) { heliosphere$tick = Long.MIN_VALUE; return; }
        var p = Minecraft.getInstance().player;
        if (p == null || entity != p) return;
        RocketEntity rocket = RealCabin.rocket();
        if (rocket == null) return;
        long t = level.getGameTime();
        if (t != heliosphere$tick) {
            Vec3 now = LaunchCabin.offset(rocket, p);
            heliosphere$prev = t == heliosphere$tick + 1 ? heliosphere$cur : now;
            heliosphere$cur = now;
            heliosphere$tick = t;
        }
        Vec3 local = heliosphere$prev.lerp(heliosphere$cur, partial);              // deck frame, turned like the passenger seat
        double r = Math.toRadians(rocket.getYRot(partial)), c = Math.cos(r), s = Math.sin(r);
        Vec3 off = new Vec3(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
        setPosition(rocket.getPosition(partial).add(off).add(0, p.getEyeHeight(), 0));
        setRotation(p.getViewYRot(partial), p.getViewXRot(partial));
        this.detached = false;                // the released descent camera shows the rider; in the cabin you are the eyes
    }
}
