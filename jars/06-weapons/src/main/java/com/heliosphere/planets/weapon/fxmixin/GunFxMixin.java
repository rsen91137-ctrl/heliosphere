package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.GunItem;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import com.heliosphere.planets.weapon.fx.GunBurst;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the warheads their own effects. GunItem itself is still the released class (its source is not in the
 * repository), so the hooks sit around what it does: a shot is marked while it is being resolved, every
 * explosion during it also sends the matching effect (LevelExplodeFxMixin), and a singularity announces itself.
 *
 * <p>No @Redirect: the build writes its {@code at} as an array, which the MixinExtras bundled with Fabric
 * Loader 0.18 cannot read, and the game would not start.
 */
@Mixin(value = GunItem.class, remap = false)
public abstract class GunFxMixin {
    @Shadow @Final @Mutable private GunItem.Spec spec;

    /** The tactical nuke flies 5000 blocks: a shot that meets nothing on the way comes down at the end of its range. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void heliosphere$range(CallbackInfo ci) {
        GunItem.Spec s = spec;
        if (s.effect() == GunItem.Effect.NUKE && s.range() < 5000)
            spec = new GunItem.Spec(s.ammo(), s.damage(), s.pellets(), s.spreadDeg(), 5000, s.interval(), s.auto(), s.explosion(), s.effect(),
                    s.tracer(), s.sound(), s.pitch(), s.recoil(), s.ammoChance());
    }

    /** The guided missile launcher: lock-on or fire-and-forget (see SmartLauncher). */
    @Inject(method = "smartLaunch", at = @At("HEAD"), cancellable = true)
    private void heliosphere$smartLaunch(ServerLevel level, ServerPlayer player, CallbackInfo ci) {
        ci.cancel();
        com.heliosphere.planets.weapon.guided.SmartLauncher.launch(level, player, ((GunItem) (Object) this).stats().damage());
    }

    /** Marks the shot so that explosions it causes (see LevelExplodeFxMixin) also get their effect. */
    @Inject(method = "ballistics", at = @At("HEAD"))
    private void heliosphere$shotBegins(ServerLevel level, ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        GunBurst.begin(spec);
    }

    @Inject(method = "ballistics", at = @At("RETURN"))
    private void heliosphere$shotEnds(ServerLevel level, ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        GunBurst.end();
    }

    /**
     * The singularity tears at everything within 12 blocks: on top of the released pull (30 damage), up to 110
     * more at the centre, falling off with distance. Robots are not spared their armour, it is a gravity well.
     */
    @Inject(method = "singularity", at = @At("HEAD"))
    private static void heliosphere$singularity(ServerLevel level, Player player, Vec3 at, CallbackInfo ci) {
        Vec3 shown = at.add(player.getEyePosition().subtract(at).normalize().scale(2.5));
        CombatVfx.send(level, CombatVfx.SINGULARITY, 0x7030C0, shown, shown, 14f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(12), en -> en != player && en.isAlive())) {
            double d = e.position().distanceTo(at);
            if (d > 12) continue;
            e.invulnerableTime = 0;
            e.hurtServer(level, level.damageSources().indirectMagic(player, player), (float) (20 + 90 * (1 - d / 12)));
            e.invulnerableTime = 0;
        }
    }
}
