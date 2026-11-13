package com.heliosphere.hostiles.arsenal.mixin;

import com.heliosphere.hostiles.arsenal.EnemyDays;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fixes to the natural machine deployment.
 * <ul>
 *   <li>Machines unlock by the day the player sees: the released code counted raw ticks, which sleeping and
 *       /time do not advance, so in a world where people sleep the machines arrived far later than the scroll says.</li>
 *   <li>Fortress garrisons, fabricator output and siege attackers no longer count towards the 60-machine cap, which
 *       near fortresses could stop all other deployments.</li>
 *   <li>Once air defence has been built anywhere, deployments come twice as often.</li>
 * </ul>
 */
@Mixin(value = TechCombat.class, remap = false)
public abstract class TechDeploymentMixin {
    @Shadow @Final private static Map<ServerLevel, Set<TechMob>> ACTIVE;

    private static final ThreadLocal<List<TechMob>> HELD = new ThreadLocal<>();
    private static long pace = 1200;

    /** Once a player has built air defence the machines take notice: deployments are weighed twice as often. */
    @ModifyConstant(method = "encounters", constant = @Constant(longValue = 1200L), remap = false)
    private static long heliosphere$pace(long every) { return pace; }

    @ModifyVariable(method = "available", at = @At("HEAD"), argsOnly = true, remap = false)
    private static long heliosphere$day(long ticksDay) { return Math.max(ticksDay, EnemyDays.day()); }

    @Inject(method = "encounters", at = @At("HEAD"), remap = false)
    private static void heliosphere$exempt(ServerLevel level, CallbackInfo ci) {
        pace = com.heliosphere.planets.defense.sam.SamSites.airDefenceBuilt(level.getServer()) ? 600 : 1200;
        Set<TechMob> set = ACTIVE.get(level);
        if (set == null) return;
        List<TechMob> held = new ArrayList<>();
        for (TechMob m : set) if (EnemyDays.exempt(m)) held.add(m);
        held.forEach(set::remove);
        HELD.set(held);
    }

    @Inject(method = "encounters", at = @At("RETURN"), remap = false)
    private static void heliosphere$restore(ServerLevel level, CallbackInfo ci) {
        List<TechMob> held = HELD.get();
        HELD.remove();
        if (held == null || held.isEmpty()) return;
        Set<TechMob> set = ACTIVE.get(level);
        if (set != null) for (TechMob m : held) if (!m.isRemoved()) set.add(m);
    }
}
