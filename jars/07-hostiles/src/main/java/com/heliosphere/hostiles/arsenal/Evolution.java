package com.heliosphere.hostiles.arsenal;

import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * The machines evolve over the 150 days before the impact, in fifteen stages of ten days. Each stage brings one or
 * two new machines (EnemyRoster's first days), makes every machine deployed on Earth a little tougher, and brings
 * the attacks closer together: sieges every 2.5-3.75 days at stage 1, every 0.5-0.75 days at stage 15 (0.6 times
 * that once air defence is built), and natural deployments from every minute to every fifteen seconds. Each new
 * stage is announced with the machines it brings.
 */
public final class Evolution {
    private Evolution() {}

    public static final int STAGES = 15, DAYS_PER_STAGE = 10;
    private static final Identifier TOUGHER = Identifier.fromNamespaceAndPath("heliosphere", "evolution");
    private static int announced = -1;

    public static int stage(long day) { return Mth.clamp(1 + (int) (day / DAYS_PER_STAGE), 1, STAGES); }

    public static int stage() { return stage(EnemyDays.day()); }

    private static float k(int stage) { return (stage - 1) / (float) (STAGES - 1); }

    /** Shortest wait between two sieges at this stage, in ticks (the actual wait is up to half as long again). */
    public static int siegeGap(int stage, boolean airDefence) {
        int gap = Math.round(Mth.lerp(k(stage), 60000, 12000));
        return airDefence ? Math.round(gap * 0.6f) : gap;
    }

    /** Ticks between two rounds of natural deployment. */
    public static long deploymentPace(int stage, boolean airDefence) {
        long pace = Math.round(Mth.lerp(k(stage), 1200, 300));
        return airDefence ? Math.max(200, pace / 2) : pace;
    }

    /** A machine deployed on Earth (not a fortress garrison, which has its own scaling): +5% health and +0.6 armour a stage. */
    public static void toughen(TechMob mob) {
        if (mob == null || mob.getTags().contains("heliosphere.fortress")) return;
        int st = stage();
        if (st <= 1) return;
        var hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getModifier(TOUGHER) == null) {
            hp.addPermanentModifier(new AttributeModifier(TOUGHER, 0.05 * (st - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            mob.setHealth(mob.getMaxHealth());
        }
        var armour = mob.getAttribute(Attributes.ARMOR);
        if (armour != null && armour.getModifier(TOUGHER) == null)
            armour.addPermanentModifier(new AttributeModifier(TOUGHER, 0.6 * (st - 1), AttributeModifier.Operation.ADD_VALUE));
    }

    /** The machines that first appear in this stage. */
    public static List<TechCombat.Role> newIn(int stage) {
        List<TechCombat.Role> out = new ArrayList<>();
        for (TechCombat.Role r : TechCombat.ROLES)
            if (r.day() < 100000 && stage(r.day()) == stage) out.add(r);
        return out;
    }

    /** Called every few seconds: announces a new stage to everyone once. */
    public static void tick(MinecraftServer server) {
        int st = stage(server.overworld().getDayTime() / 24000L);
        if (announced == -1) { announced = st; return; }                  // not on joining: only when it changes
        if (st == announced) return;
        announced = st;
        MutableComponent names = Component.empty();
        List<TechCombat.Role> fresh = newIn(st);
        for (int i = 0; i < fresh.size(); i++) {
            if (i > 0) names.append("、");
            names.append(Component.translatable("entity.heliosphere." + fresh.get(i).id()));
        }
        float days = siegeGap(st, false) / 24000f;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.translatable("event.heliosphere.evolution", st, STAGES).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            p.displayClientMessage(Component.translatable(fresh.isEmpty() ? "event.heliosphere.evolution.none" : "event.heliosphere.evolution.new", names,
                    String.format("%.1f", days), String.format("%.1f", days * 1.5f), 5 * (st - 1)).withStyle(ChatFormatting.RED), false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5f, 0.6f);
        }
    }
}
