package com.heliosphere.planets.enemy;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Centaurian vanguard: dormant machines on Earth that wake as human industry grows. Scout drones come first,
 * then crawler swarms, then a sentinel walker with a boss bar. Their wreckage yields alien alloy and chips.
 */
public final class Vanguard {
    public static EntityType<ScoutDrone> SCOUT;
    public static EntityType<MechCrawler> CRAWLER;
    public static EntityType<Sentinel> SENTINEL;
    private static final String WOKE = "heliosphere.vanguard_woke";

    private Vanguard() {}

    private static void loot(LivingDrops self, ServerLevel l, int alloy, int chip) {
        if (alloy > 0) self.spawn(new ItemStack(Reg.get("alien_alloy"), alloy));
        if (chip > 0 && l.getRandom().nextFloat() < 0.5f + chip * 0.25f) self.spawn(new ItemStack(Reg.get("alien_chip"), chip));
    }

    interface LivingDrops { void spawn(ItemStack s); }

    public static class ScoutDrone extends Vex {
        public ScoutDrone(EntityType<? extends Vex> t, Level l) { super(t, l); }
        @Override protected void dropCustomDeathLoot(ServerLevel l, DamageSource src, boolean hit) {
            super.dropCustomDeathLoot(l, src, hit);
            loot(s -> spawnAtLocation(l, s), l, 1, 0);
        }
    }

    public static class MechCrawler extends Silverfish {
        public MechCrawler(EntityType<? extends Silverfish> t, Level l) { super(t, l); }
        @Override protected void dropCustomDeathLoot(ServerLevel l, DamageSource src, boolean hit) {
            super.dropCustomDeathLoot(l, src, hit);
            loot(s -> spawnAtLocation(l, s), l, l.getRandom().nextInt(2), 0);
        }
    }

    public static class Sentinel extends Ravager {
        private final ServerBossEvent bar = new ServerBossEvent(Component.literal("§b半人马先遣守卫"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
        public Sentinel(EntityType<? extends Ravager> t, Level l) { super(t, l); }
        @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }
        @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }
        @Override protected void customServerAiStep(ServerLevel l) { super.customServerAiStep(l); bar.setProgress(getHealth() / getMaxHealth()); }
        @Override protected void dropCustomDeathLoot(ServerLevel l, DamageSource src, boolean hit) {
            super.dropCustomDeathLoot(l, src, hit);
            loot(s -> spawnAtLocation(l, s), l, 10, 3);
            for (ServerPlayer p : l.players()) if (p.distanceTo(this) < 64)
                p.sendSystemMessage(Component.literal("§b先遣守卫被摧毁。残骸里的芯片还在向比邻星发送信号……"));
        }
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> reg(String id, EntityType.EntityFactory<T> f, float w, float h) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Heliosphere.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(f, MobCategory.MONSTER).sized(w, h).clientTrackingRange(10).build(key));
    }

    public static void init() {
        SCOUT = reg("scout_drone", ScoutDrone::new, 0.5f, 0.9f);
        CRAWLER = reg("mech_crawler", MechCrawler::new, 0.5f, 0.35f);
        SENTINEL = reg("vanguard_sentinel", Sentinel::new, 1.95f, 2.2f);
        FabricDefaultAttributeRegistry.register(SCOUT, Vex.createAttributes().add(Attributes.MAX_HEALTH, 18).add(Attributes.ATTACK_DAMAGE, 4));
        FabricDefaultAttributeRegistry.register(CRAWLER, Silverfish.createAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.MOVEMENT_SPEED, 0.3));
        FabricDefaultAttributeRegistry.register(SENTINEL, Ravager.createAttributes().add(Attributes.MAX_HEALTH, 320).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.ARMOR, 10));
        ServerTickEvents.END_SERVER_TICK.register(Vanguard::tick);
    }

    /** Industrial signature: how many of the scroll's key items the player has made. */
    private static long signature(ServerPlayer p) { return p.getTags().stream().filter(t -> t.startsWith("heliosphere.got.")).count(); }

    private static void tick(MinecraftServer s) {
        if (s.getTickCount() % 200 != 0) return;
        ServerLevel l = s.overworld();
        if (l.getDifficulty() == Difficulty.PEACEFUL) return;
        boolean night = l.getSkyDarken() > 3 || l.isDarkOutside();
        for (ServerPlayer p : l.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            long sig = signature(p);
            var r = l.getRandom();
            boolean dark = night || l.getMaxLocalRawBrightness(p.blockPosition()) < 6;
            if (!dark || sig < 5) continue;
            if (!p.getTags().contains(WOKE)) {
                p.addTag(WOKE);
                p.sendSystemMessage(Component.literal("§c§l【警报】§r§c你的工业信号唤醒了埋在地下的东西……半人马先遣机群开始活动。"));
            }
            if (r.nextFloat() < 0.25f) spawnNear(l, p, SCOUT, 1 + r.nextInt(2), 8);
            if (sig >= 15 && r.nextFloat() < 0.15f) spawnNear(l, p, CRAWLER, 3 + r.nextInt(3), 0);
            if (sig >= 30 && r.nextFloat() < 0.02f && l.getEntities(SENTINEL, p.getBoundingBox().inflate(128), e -> true).isEmpty()) {
                spawnNear(l, p, SENTINEL, 1, 0);
                p.sendSystemMessage(Component.literal("§4大地在震动——一台先遣守卫正向你逼近！"));
            }
        }
    }

    private static <T extends net.minecraft.world.entity.Mob> void spawnNear(ServerLevel l, ServerPlayer p, EntityType<T> type, int n, int up) {
        var r = l.getRandom();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 18 + r.nextInt(16);
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + up;
            if (Math.abs(y - p.getY()) > 30) y = (int) p.getY() + up;
            T e = type.create(l, EntitySpawnReason.EVENT);
            if (e == null) continue;
            e.snapTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
            e.setTarget(p);
            l.addFreshEntity(e);
        }
    }
}
