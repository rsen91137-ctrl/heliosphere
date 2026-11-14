package com.heliosphere.planets.gear;

import com.heliosphere.core.machine.EnergyNode;
import com.heliosphere.planets.defense.mk.PortableSentryItem;
import com.heliosphere.planets.defense.mk.Sentries;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The portable sentry worn on the armour: dragged onto a chestplate in the inventory it clips onto the shoulder
 * (right-click the chestplate with an empty cursor to take it off again). While that chestplate is worn it shoots
 * every hostile creature and machine it can see within 16 blocks: 6 damage a shot every 0.7 seconds, 25 FE a shot
 * from its 8000 FE battery. The turret upgrade kits raise it to Mk I-V (up to 16 damage, 26 blocks, 0.4 s, 24000 FE):
 * click a kit onto the sentry, or onto armour that carries it. It charges while you stand next to a power block (generators, battery banks) and slowly
 * from its solar panel by day under the open sky. In creative mode it needs no charge.
 */
public final class ShoulderSentry {
    private ShoulderSentry() {}

    public static final String KEY = "shoulder_sentry", MK = "shoulder_sentry_mk";
    public static final long CAPACITY = 8000;
    public static final int COST = 25, INTERVAL = 14;
    public static final float DAMAGE = 6, RANGE = 16;

    /** Upgrades with the turret upgrade kits, Mk I to V (index 0 = as built). */
    private static final float[] DAMAGES = {6, 7.5f, 9, 11, 13, 16};
    private static final float[] RANGES = {16, 18, 20, 22, 24, 26};
    private static final int[] INTERVALS = {14, 13, 12, 11, 10, 8};
    private static final long[] CAPACITIES = {8000, 10000, 12000, 15000, 18000, 24000};

    private static int i(int mk) { return Mth.clamp(mk, 0, 5); }
    public static float damage(int mk) { return DAMAGES[i(mk)]; }
    public static float range(int mk) { return RANGES[i(mk)]; }
    public static int interval(int mk) { return INTERVALS[i(mk)]; }
    public static long capacity(int mk) { return CAPACITIES[i(mk)]; }

    /** Upgrade level of the sentry on this armour (0 as built). */
    public static int mk(ItemStack st) {
        var d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getIntOr(MK, 0);
    }

    public static void setMk(ItemStack st, int mk) { CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putInt(MK, i(mk))); }

    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    /** Shots fired since start (for tests). */
    public static int shots;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ShoulderSentry::tick);
    }

    /** Whether the stack can carry the sentry: anything worn on the chest. */
    public static boolean fits(ItemStack st) {
        var eq = st.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot() == EquipmentSlot.CHEST;
    }

    /** The charge of the sentry on this armour, or -1 if none is fitted. */
    public static long charge(ItemStack st) {
        if (st.isEmpty()) return -1;
        var d = st.get(DataComponents.CUSTOM_DATA);
        if (d == null) return -1;
        CompoundTag t = d.copyTag();
        return t.contains(KEY) ? t.getLongOr(KEY, 0) : -1;
    }

    public static void setCharge(ItemStack st, long e) {
        long cap = capacity(mk(st));
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putLong(KEY, Mth.clamp(e, 0, cap)));
    }

    public static void remove(ItemStack st) {
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> { t.remove(KEY); t.remove(MK); });
        var d = st.get(DataComponents.CUSTOM_DATA);
        if (d != null && d.isEmpty()) st.remove(DataComponents.CUSTOM_DATA);
    }

    /** The sentry item as it comes off the armour. */
    public static ItemStack takeOff(ItemStack armour) {
        long e = charge(armour);
        ItemStack s = new ItemStack(Sentries.PORTABLE_ITEM);
        PortableSentryItem.setMk(s, mk(armour));
        PortableSentryItem.setEnergy(s, Math.max(0, e));
        remove(armour);
        return s;
    }

    /** Where the gun sits: over the right shoulder. */
    public static Vec3 muzzle(net.minecraft.world.entity.player.Player p) {
        double yaw = Math.toRadians(p.yBodyRot);
        return p.position().add(-Math.cos(yaw) * 0.42, p.getBbHeight() * 0.86, -Math.sin(yaw) * 0.42);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
            long e = charge(chest);
            if (e < 0) continue;
            ServerLevel level = p.level();
            boolean free = p.isCreative();
            int mk = mk(chest);
            long cap = capacity(mk);
            float range = range(mk);
            if (p.tickCount % 10 == 0 && !free && e < cap) {
                long before = e;
                e = charge(level, p, e, cap);
                if (e != before) {
                    setCharge(chest, e);
                    if (e - before > 30 && p.tickCount % 40 == 0)
                        p.displayClientMessage(Component.translatable("message.heliosphere.shoulder_sentry_charging", e, cap).withStyle(ChatFormatting.AQUA), true);
                }
            }
            int cd = COOLDOWN.getOrDefault(p.getUUID(), 0);
            if (cd > 0) { COOLDOWN.put(p.getUUID(), cd - 1); continue; }
            if (!free && e < COST) continue;
            Vec3 muzzle = muzzle(p);
            Mob target = null;
            double best = range * range;
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(muzzle, muzzle).inflate(range))) {
                if (!(m instanceof Enemy) || !m.isAlive() || m instanceof OwnableEntity) continue;
                Vec3 c = m.getBoundingBox().getCenter();
                double d = c.distanceToSqr(muzzle);
                if (d > best) continue;
                if (level.clip(new ClipContext(muzzle, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
                best = d; target = m;
            }
            if (target == null) { COOLDOWN.put(p.getUUID(), 5); continue; }
            Vec3 c = target.getBoundingBox().getCenter();
            target.hurtServer(level, level.damageSources().playerAttack(p), damage(mk));
            target.invulnerableTime = 0;
            CombatVfx.send(level, CombatVfx.TRACER, mk >= 4 ? 0x80E0FF : 0xFFD070, muzzle, c, 0.8f + mk * 0.1f);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.7f, 1.7f);
            if (!free) setCharge(chest, e - COST);
            COOLDOWN.put(p.getUUID(), interval(mk));
            shots++;
        }
    }

    /** From power blocks within a block of the feet, and the sun. */
    private static long charge(ServerLevel level, ServerPlayer p, long e, long CAPACITY) {
        BlockPos feet = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            if (e >= CAPACITY) break;
            if (!(level.getBlockEntity(q) instanceof EnergyNode n) || !n.canExtract() || n.energy() <= 0) continue;
            long take = Math.min(Math.min(400, CAPACITY - e), n.energy());
            n.setEnergy(n.energy() - take);
            level.getBlockEntity(q).setChanged();
            e += take;
        }
        if (level.isBrightOutside() && level.canSeeSky(feet.above(2))) e = Math.min(CAPACITY, e + 20);
        return e;
    }
}
