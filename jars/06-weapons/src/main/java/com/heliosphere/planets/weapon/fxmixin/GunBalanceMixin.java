package com.heliosphere.planets.weapon.fxmixin;

import com.heliosphere.planets.weapon.GunStats;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Damage balance. Weapons that fire slowly, hold few rounds and cost late-game materials hit far harder than
 * the automatics, so that a well-placed shot from one is worth the trouble of making it. Reference: tech
 * enemies have 24 (patrol drone) to 230 (rail sentinel) health and up to 18 armour; the Centaurian sentinel
 * has 320. Values are {damage, armour piercing 0..1}; everything else in the table is left as released.
 */
@Mixin(value = GunStats.class, remap = false)
public abstract class GunBalanceMixin {
    @Shadow @Final private static Map<String, GunStats> TABLE;

    private static final Object[][] BALANCE = {
            // marksman tier: reliable kills on early enemies, two to three shots on walkers
            {"heavy_pistol", 16f, 0.25f},
            {"battle_rifle", 14f, 0.32f},
            {"designated_marksman_rifle", 26f, 0.45f},
            {"bolt_rifle", 38f, 0.55f},
            {"gauss_pistol", 30f, 0.55f},
            {"coilgun", 34f, 0.65f},
            // heavy single shots: one-shot every drone, two on a siege walker, a headshot drops a sentinel
            {"sniper_rifle", 90f, 0.75f},
            {"railgun", 160f, 0.95f},
            {"antimatter_rifle", 130f, 0.90f},
            // launchers and exotics (their blast or field does more on top)
            {"grenade_launcher", 22f, 0.10f},
            {"smart_launcher", 70f, 0.30f},
            {"plasma_cannon", 60f, 0.50f},
            {"singularity_cannon", 90f, 1.00f},
    };

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void heliosphere$balance(CallbackInfo ci) {
        // the M-80 rotary cannon becomes the HM14.5/1930 heavy machine gun (14.5x130.5): no spin-up, heavier rounds,
        // a slower rate, a 60-round belt box, harder kick
        GunStats r = TABLE.get("rotary_cannon");
        if (r != null)
            TABLE.put("rotary_cannon", new GunStats(18f, r.headMul(), 0.55f, r.falloffStart() * 1.5f, r.falloffEnd() * 1.5f, r.minMul(),
                    Math.max(3, r.interval() + 1), r.burst(), r.burstDelay(), 0, r.hipSpread(), r.adsSpread() * 0.8f, r.moveSpread(), r.bloom(), r.bloomMax(),
                    r.recoilV() * 1.6f, r.recoilH() * 1.3f, r.kick() * 1.5f, r.shake() * 1.4f, r.adsZoom(), r.adsTicks(), 60, r.reloadTicks(), r.adsSpeed(),
                    r.knockback() * 1.5f, r.sound()));
        for (Object[] row : BALANCE) {
            GunStats g = TABLE.get((String) row[0]);
            if (g == null) continue;
            TABLE.put((String) row[0], new GunStats((Float) row[1], g.headMul(), (Float) row[2], g.falloffStart(), g.falloffEnd(), g.minMul(),
                    g.interval(), g.burst(), g.burstDelay(), g.spinup(), g.hipSpread(), g.adsSpread(), g.moveSpread(), g.bloom(), g.bloomMax(),
                    g.recoilV(), g.recoilH(), g.kick(), g.shake(), g.adsZoom(), g.adsTicks(), g.capacity(), g.reloadTicks(), g.adsSpeed(),
                    g.knockback(), g.sound()));
        }
    }
}
