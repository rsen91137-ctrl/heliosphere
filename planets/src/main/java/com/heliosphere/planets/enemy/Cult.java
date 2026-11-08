package com.heliosphere.planets.enemy;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The Doomsday Cult: humans who believe leaving the Earth brings the Centaurians down on everyone. Raiding parties
 * hunt engineers once they start building rockets; later a fortified stronghold appears. Destroy its core (a nuke
 * works) to take it, with its stockpile of uranium, plutonium and explosives.
 */
public final class Cult {
    private static final Identifier KEY = Heliosphere.id("cult");

    private Cult() {}

    /** The stronghold's heart: breaking or blasting it captures the base. */
    public static class CoreBlock extends Block {
        public CoreBlock(Properties p) { super(p); }

        @Override
        public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (level instanceof ServerLevel sl) captured(sl, pos);
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
            captured(level, pos);
            super.wasExploded(level, pos, explosion);
        }
    }

    public static void init() { ServerTickEvents.END_SERVER_TICK.register(Cult::tick); }

    private static CompoundTag data(MinecraftServer s) { return s.getCommandStorage().get(KEY); }

    private static long signature(ServerPlayer p) { return p.getTags().stream().filter(t -> t.startsWith("heliosphere.got.")).count(); }

    private static void tick(MinecraftServer s) {
        if (s.getTickCount() % 200 != 7) return;
        ServerLevel l = s.overworld();
        if (l.getDifficulty() == Difficulty.PEACEFUL) return;
        CompoundTag d = data(s);
        for (ServerPlayer p : l.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            long sig = signature(p);
            if (sig >= 8 && l.getRandom().nextFloat() < 0.04f) raid(l, p);
            if (sig >= 18 && !d.getBooleanOr("built", false)) { build(l, p, d); s.getCommandStorage().set(KEY, d); }
        }
        // garrison: while the core stands, defenders keep coming when someone approaches
        if (d.getBooleanOr("built", false) && !d.getBooleanOr("taken", false)) {
            BlockPos c = BlockPos.of(d.getLongOr("core", 0));
            for (ServerPlayer p : l.players()) {
                if (p.blockPosition().distSqr(c) > 56 * 56) continue;
                int near = l.getEntitiesOfClass(Mob.class, new AABB(c).inflate(40), m -> m.getTags().contains("heliosphere.cult")).size();
                if (near < 12) for (int i = 0; i < 3; i++) spawnCultist(l, c.offset(l.getRandom().nextInt(21) - 10, 1, l.getRandom().nextInt(21) - 10), p, l.getRandom().nextInt(4));
            }
        }
    }

    private static void raid(ServerLevel l, ServerPlayer p) {
        var r = l.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        int x = (int) (p.getX() + Math.cos(a) * 30), z = (int) (p.getZ() + Math.sin(a) * 30);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int n = 3 + r.nextInt(4);
        for (int i = 0; i < n; i++) spawnCultist(l, new BlockPos(x + r.nextInt(5) - 2, y, z + r.nextInt(5) - 2), p, r.nextInt(5));
        p.sendSystemMessage(Component.literal("§c§l【末日教团】§r§c「天空属于神明，不属于你们！」一支教团突袭队正在逼近。"));
    }

    private static void spawnCultist(ServerLevel l, BlockPos at, ServerPlayer target, int kind) {
        EntityType<? extends Mob> type = kind == 0 ? EntityType.VINDICATOR : kind == 4 ? EntityType.EVOKER : EntityType.PILLAGER;
        Mob m = type.create(l, EntitySpawnReason.EVENT);
        if (m == null) return;
        BlockPos ground = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        m.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, l.getRandom().nextFloat() * 360, 0);
        m.finalizeSpawn(l, l.getCurrentDifficultyAt(ground), EntitySpawnReason.EVENT, null);
        m.setCustomName(Component.literal(kind == 0 ? "§4教团狂信徒" : kind == 4 ? "§5教团祭司" : "§c末日教徒"));
        m.addTag("heliosphere.cult");
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(kind == 0 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
        m.setPersistenceRequired();
        if (target != null) m.setTarget(target);
        l.addFreshEntity(m);
    }

    /** A walled compound with four towers and a keep around the core, far enough to need an expedition. */
    private static void build(ServerLevel l, ServerPlayer p, CompoundTag d) {
        var r = l.getRandom();
        double a = r.nextDouble() * Math.PI * 2, dist = 450 + r.nextInt(250);
        int cx = (int) (p.getX() + Math.cos(a) * dist), cz = (int) (p.getZ() + Math.sin(a) * dist);
        l.getChunk(cx >> 4, cz >> 4);
        int base = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), floor = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int dx = -15; dx <= 15; dx++) for (int dz = -15; dz <= 15; dz++) {
            int x = cx + dx, z = cz + dz;
            l.getChunk(x >> 4, z >> 4);
            for (int y = base - 3; y < base; y++) l.setBlock(new BlockPos(x, y, z), floor, Block.UPDATE_CLIENTS);
            for (int y = base; y < base + 14; y++) l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            boolean edge = Math.abs(dx) == 15 || Math.abs(dz) == 15;
            boolean gate = edge && Math.abs(dx) <= 1 && dz == 15;
            boolean tower = Math.abs(dx) >= 13 && Math.abs(dz) >= 13;
            boolean keep = Math.abs(dx) <= 4 && Math.abs(dz) <= 4 && (Math.abs(dx) == 4 || Math.abs(dz) == 4) && !(dx == 0 && dz == 4);
            int h = tower ? 12 : edge ? 6 : keep ? 8 : 0;
            if (gate) h = 0;
            for (int y = 0; y < h; y++) l.setBlock(new BlockPos(x, base + y, z), wall, Block.UPDATE_CLIENTS);
            if (edge && !gate && (dx + dz) % 2 == 0) l.setBlock(new BlockPos(x, base + h, z), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (tower && Math.abs(dx) == 14 && Math.abs(dz) == 14) l.setBlock(new BlockPos(x, base + 12, z), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos core = new BlockPos(cx, base + 1, cz);
        l.setBlock(core.below(), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        l.setBlock(core, Reg.getBlock("cult_core").defaultBlockState(), Block.UPDATE_ALL);
        for (int k = 0; k < 4; k++) {
            BlockPos cp = core.offset(k < 2 ? -3 : 3, 0, k % 2 == 0 ? -3 : 3);
            l.setBlock(cp, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (l.getBlockEntity(cp) instanceof ChestBlockEntity ch) {
                ch.setItem(0, new ItemStack(Reg.get("rdx"), 6 + r.nextInt(8)));
                ch.setItem(1, new ItemStack(Reg.get("enriched_uranium"), 1 + r.nextInt(3)));
                ch.setItem(2, new ItemStack(Reg.get("rifle_round"), 32));
                ch.setItem(3, new ItemStack(Items.DIAMOND, 2 + r.nextInt(4)));
                if (r.nextFloat() < 0.5f) ch.setItem(4, new ItemStack(Reg.get("plutonium_238"), 1));
                if (r.nextFloat() < 0.4f) ch.setItem(5, new ItemStack(Reg.get("assault_rifle")));
            }
        }
        d.putBoolean("built", true);
        d.putLong("core", core.asLong());
        p.sendSystemMessage(Component.literal("§6§l【情报】§r§6截获教团通讯：他们的据点在 §fX " + cx + "  Z " + cz + "§6。据点核心被毁，教团在这一带就会瓦解。"));
        p.sendSystemMessage(Component.literal("§7（据点有重兵把守。战术核弹发射器可以从 200 格外直接摧毁它。）"));
    }

    private static void captured(ServerLevel l, BlockPos pos) {
        MinecraftServer s = l.getServer();
        CompoundTag d = data(s);
        if (d.getBooleanOr("taken", false)) return;
        d.putBoolean("taken", true);
        s.getCommandStorage().set(KEY, d);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal("§a§l【据点攻占】§r§a末日教团的核心已被摧毁，残余教徒四散而逃。"));
        }
        Block.popResource(l, pos, new ItemStack(Reg.get("plutonium_238"), 2));
        Block.popResource(l, pos, new ItemStack(Reg.get("enriched_uranium"), 4));
        Block.popResource(l, pos, new ItemStack(Reg.get("alien_chip"), 2));
        for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(64), m -> m.getTags().contains("heliosphere.cult"))) m.discard();
    }
}
