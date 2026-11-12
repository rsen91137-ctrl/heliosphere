package com.heliosphere.planets.weapon.nuke;

import com.heliosphere.core.Reg;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** What a detonation leaves behind: the blocks of the crater floor, the fallout, and radiation sickness. */
public final class NukeContent {
    private NukeContent() {}

    /** Sand and rock fused to green glass at ground zero. Strongly radioactive. */
    public static Block TRINITITE;
    /** Soil baked black out to the edge of the fire. Mildly radioactive. */
    public static Block SCORCHED_EARTH;
    /** A thin drift of radioactive ash that settles downwind. */
    public static Block FALLOUT_ASH;
    public static Holder<MobEffect> RADIATION;
    public static SoundEvent GEIGER;

    private static boolean done;

    public static void init() {
        if (done) return;
        done = true;
        Reg.tab("weapons");
        TRINITITE = Reg.blockWithItem("trinitite", Block::new, Reg.stone(MapColor.COLOR_GREEN, 1.4f).sound(SoundType.GLASS).lightLevel(s -> 4));
        SCORCHED_EARTH = Reg.blockWithItem("scorched_earth", Block::new, Reg.soil(MapColor.COLOR_BLACK, 0.7f));
        FALLOUT_ASH = Reg.blockWithItem("fallout_ash", FalloutAshBlock::new,
                Reg.soil(MapColor.COLOR_LIGHT_GRAY, 0.1f).sound(SoundType.SNOW).noCollision().replaceable().noOcclusion());
        RADIATION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath("heliosphere", "radiation"), new RadiationEffect());
        Identifier geiger = Identifier.fromNamespaceAndPath("heliosphere", "geiger_click");
        GEIGER = Registry.register(BuiltInRegistries.SOUND_EVENT, geiger, SoundEvent.createVariableRangeEvent(geiger));
    }

    /** Radiation sickness. The amplifier is the severity: 0 a warning, 3 lethal within the minute. */
    static final class RadiationEffect extends MobEffect {
        RadiationEffect() { super(MobEffectCategory.HARMFUL, 0x9BE22D); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            int every = switch (Math.min(amplifier, 3)) { case 0 -> 80; case 1 -> 50; case 2 -> 30; default -> 16; };
            return duration % every == 0;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity e, int amplifier) {
            e.hurtServer(level, level.damageSources().magic(), 1f + 0.5f * Math.min(amplifier, 3));
            if (amplifier >= 1) e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, amplifier - 1, true, false, false));
            if (amplifier >= 2) e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0, true, false, false));
            if (amplifier >= 2) e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, amplifier - 1, true, false, false));
            return true;
        }
    }
}
