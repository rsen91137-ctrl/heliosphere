package com.heliosphere.planets.weapon;

import com.heliosphere.core.Reg;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Hitscan firearm: ammo from the inventory, spread, piercing, explosions, tracers, recoil. */
public class GunItem extends Item {
    public enum Effect { NONE, FIRE, PIERCE, EXPLODE, NUKE, ANTIMATTER, SINGULARITY }

    public record Spec(String ammo, float damage, int pellets, float spreadDeg, double range, int interval, boolean auto,
                       float explosion, Effect effect, ParticleOptions tracer, SoundEvent sound, float pitch, float recoil, float ammoChance) {}

    private final Spec spec;

    public GunItem(Properties p, Spec spec) { super(p); this.spec = spec; }

    public Spec spec() { return spec; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (spec.auto) { player.startUsingItem(hand); return InteractionResult.CONSUME; }
        if (player.getCooldowns().isOnCooldown(st)) return InteractionResult.FAIL;
        fire(level, player, st);
        player.getCooldowns().addCooldown(st, spec.interval);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack st, int remaining) {
        if (!(e instanceof Player p)) return;
        int used = getUseDuration(st, e) - remaining;
        if (used % Math.max(1, spec.interval) == 0) fire(level, p, st);
    }

    @Override public int getUseDuration(ItemStack st, LivingEntity e) { return spec.auto ? 72000 : 0; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack st) { return ItemUseAnimation.NONE; }

    private boolean takeAmmo(Player p, Level level) {
        if (p.isCreative()) return true;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(Reg.get(spec.ammo))) {
                if (level.getRandom().nextFloat() < spec.ammoChance) s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private void fire(Level level, Player p, ItemStack gun) {
        if (level.isClientSide()) {
            p.setXRot(p.getXRot() - spec.recoil);
            p.setYRot(p.getYRot() + (level.getRandom().nextFloat() - 0.5f) * spec.recoil * 0.4f);
            return;
        }
        ServerLevel sl = (ServerLevel) level;
        if (!takeAmmo(p, level)) {
            sl.playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8f, 1.6f);
            p.displayClientMessage(Component.translatable("message.heliosphere.no_ammo", Reg.get(spec.ammo).getName()), true);
            if (spec.auto) p.stopUsingItem();
            return;
        }
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.8)).add(0, -0.15, 0);
        for (int k = 0; k < spec.pellets; k++) {
            Vec3 dir = spread(look, spec.spreadDeg, sl);
            Vec3 end = eye.add(dir.scale(spec.range));
            BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 stop = bh.getType() != HitResult.Type.MISS ? bh.getLocation() : end;
            final Vec3 segEnd = stop;
            List<Entity> cands = level.getEntities(p, new AABB(eye, segEnd).inflate(1.0), en -> en instanceof LivingEntity && en.isAlive() && en != p);
            cands.sort(Comparator.comparingDouble(en -> en.distanceToSqr(eye)));
            int pierced = 0;
            for (Entity en : cands) {
                Optional<Vec3> hit = en.getBoundingBox().inflate(0.25).clip(eye, segEnd);
                if (hit.isEmpty()) continue;
                en.hurtServer(sl, sl.damageSources().playerAttack(p), spec.damage);
                if (spec.effect == Effect.FIRE) en.igniteForSeconds(4);
                if (en instanceof LivingEntity le) le.knockback(0.15 + spec.damage * 0.01, -dir.x, -dir.z);
                pierced++;
                if (spec.effect != Effect.PIERCE && spec.effect != Effect.ANTIMATTER) { stop = hit.get(); break; }
                if (pierced >= 6) break;
            }
            tracer(sl, muzzle, stop);
            switch (spec.effect) {
                case EXPLODE -> sl.explode(p, stop.x, stop.y, stop.z, spec.explosion, Level.ExplosionInteraction.TNT);
                case ANTIMATTER -> sl.explode(p, stop.x, stop.y, stop.z, spec.explosion, Level.ExplosionInteraction.NONE);
                case NUKE -> Nuke.detonate(sl, p, stop);
                case SINGULARITY -> singularity(sl, p, stop);
                case FIRE -> { if (spec.explosion > 0) sl.explode(p, stop.x, stop.y, stop.z, spec.explosion, Level.ExplosionInteraction.NONE); }
                default -> {}
            }
        }
        sl.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01);
        sl.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.005);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), spec.sound, SoundSource.PLAYERS, 1.2f, spec.pitch * (0.95f + sl.getRandom().nextFloat() * 0.1f));
    }

    private static Vec3 spread(Vec3 look, float deg, ServerLevel l) {
        if (deg <= 0) return look;
        double r = Math.toRadians(deg);
        return look.add(l.getRandom().nextGaussian() * r * 0.5, l.getRandom().nextGaussian() * r * 0.5, l.getRandom().nextGaussian() * r * 0.5).normalize();
    }

    private void tracer(ServerLevel l, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        int n = (int) Math.min(80, len / 1.2);
        for (int i = 1; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            l.sendParticles(spec.tracer, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    private static void singularity(ServerLevel l, Player p, Vec3 c) {
        for (Entity e : l.getEntities(p, new AABB(c, c).inflate(10), en -> en instanceof LivingEntity && en != p)) {
            Vec3 pull = c.subtract(e.position()).normalize().scale(1.4);
            e.setDeltaMovement(e.getDeltaMovement().add(pull));
            e.hurtMarked = true;
            e.hurtServer(l, l.damageSources().magic(), 30f);
            if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
        }
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 400, 3, 3, 3, 0.4);
        l.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 0.5f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(String.format("§7伤害 %s%s · 射程 %.0f 格 · %s", trim(spec.damage), spec.pellets > 1 ? "×" + spec.pellets : "",
                spec.range, spec.auto ? "全自动 " + String.format("%.0f 发/秒", 20.0 / Math.max(1, spec.interval)) : "间隔 " + trim(spec.interval / 20f) + " 秒")));
        out.accept(Component.literal("§8弹药：").append(Reg.get(spec.ammo).getName()));
        String fx = switch (spec.effect) {
            case FIRE -> "命中点燃"; case PIERCE -> "穿透多个目标"; case EXPLODE -> "爆炸（会破坏方块）"; case NUKE -> "§c核爆：毁灭性范围破坏与辐射";
            case ANTIMATTER -> "湮灭：穿透 + 不破坏方块的爆炸"; case SINGULARITY -> "奇点：吸入并撕裂周围敌人"; default -> "";
        };
        if (!fx.isEmpty()) out.accept(Component.literal("§6" + fx));
    }

    private static String trim(float f) { return f == (int) f ? String.valueOf((int) f) : String.format("%.1f", f); }
}
