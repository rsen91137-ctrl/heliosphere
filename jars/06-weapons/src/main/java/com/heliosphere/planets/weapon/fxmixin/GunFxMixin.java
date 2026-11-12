package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.GunItem;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the warheads their own effects. GunItem itself is still the released class (its source is not in the
 * repository), so the hooks sit on the calls it makes: every explosion a gun causes also sends the matching
 * effect, and a singularity announces itself.
 */
@Mixin(value = GunItem.class, remap = false)
public abstract class GunFxMixin {
    @Shadow @Final private GunItem.Spec spec;

    @Redirect(method = "ballistics", remap = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"))
    private void heliosphere$burst(ServerLevel level, Entity source, double x, double y, double z, float power, Level.ExplosionInteraction mode) {
        level.explode(source, x, y, z, power, mode);
        Vec3 at = new Vec3(x, y, z);
        switch (spec.effect()) {
            case ANTIMATTER -> CombatVfx.send(level, CombatVfx.ANTIMATTER, 0xC8A0FF, at, at, power);
            case FIRE -> CombatVfx.send(level, CombatVfx.PLASMA, spec.ammo().contains("plasma") ? 0x78E6FF : 0xFFA050, at, at, power);
            default -> CombatVfx.send(level, CombatVfx.EXPLOSION, 0xFFB060, at, at, power);
        }
    }

    /**
     * The singularity tears at everything within 12 blocks: on top of the released pull (30 damage), up to 110
     * more at the centre, falling off with distance. Robots are not spared their armour, it is a gravity well.
     */
    @Inject(method = "singularity", at = @At("HEAD"))
    private static void heliosphere$singularity(ServerLevel level, Player player, Vec3 at, CallbackInfo ci) {
        CombatVfx.send(level, CombatVfx.SINGULARITY, 0x7030C0, at, at, 10f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(12), en -> en != player && en.isAlive())) {
            double d = e.position().distanceTo(at);
            if (d > 12) continue;
            e.invulnerableTime = 0;
            e.hurtServer(level, level.damageSources().indirectMagic(player, player), (float) (20 + 90 * (1 - d / 12)));
            e.invulnerableTime = 0;
        }
    }
}
