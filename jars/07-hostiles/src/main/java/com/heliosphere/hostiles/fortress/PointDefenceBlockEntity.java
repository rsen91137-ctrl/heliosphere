package com.heliosphere.hostiles.fortress;

import com.heliosphere.planets.defense.sam.GuidedMissile;
import com.heliosphere.planets.defense.sam.MissileSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The point-defence launcher's fire control. Every other tick it looks for the players' missiles in its reach (the
 * nuke first, then the closest), and sends one interceptor at each (two at a nuke), as long as it has rounds; the
 * cells reload slowly. Reach, cells and reload grow with the fortress's difficulty.
 */
public class PointDefenceBlockEntity extends BlockEntity {
    private int tier = 7, rounds = -1, reload, cooldown;
    /** Target -> interceptors sent at it that are still flying. */
    private final Map<UUID, List<UUID>> engaged = new HashMap<>();
    private final Set<UUID> warned = new HashSet<>();

    public PointDefenceBlockEntity(BlockPos pos, BlockState state) { super(Fortresses.POINT_DEFENCE_BE, pos, state); }

    void setup(int tier) { this.tier = Math.max(7, tier); rounds = cells(); setChanged(); }

    /** How far it reaches: 200 blocks at difficulty 7, 440 at 15. */
    public static int reach(int tier) { return 200 + 30 * (Math.max(7, tier) - 7); }
    private int cells() { return 4 + (tier - 7) / 2; }
    private int reloadTicks() { return Math.max(40, 160 - 12 * (tier - 7)); }

    void tick(ServerLevel level) {
        if (rounds < 0) rounds = cells();
        if (rounds < cells() && ++reload >= reloadTicks()) { reload = 0; rounds++; setChanged(); }
        if (cooldown > 0) { cooldown--; return; }
        if (level.getGameTime() % 2 != 0 || rounds <= 0) return;
        var flying = GuidedMissile.FLYING.get(level);
        if (flying == null || flying.isEmpty()) return;
        Vec3 site = Vec3.atCenterOf(worldPosition).add(0, 1, 0);
        int reach = reach(tier);
        List<GuidedMissile> threats = new ArrayList<>();
        for (GuidedMissile m : new ArrayList<>(flying))
            if (m.isAlive() && m.side() == GuidedMissile.Side.DEFENCE && m.position().distanceTo(site) <= reach) threats.add(m);
        if (threats.isEmpty()) return;
        threats.sort(Comparator.<GuidedMissile>comparingInt(m -> m.spec() == MissileSpec.NUKE_ROCKET ? 0 : 1).thenComparingDouble(m -> m.position().distanceToSqr(site)));
        engaged.values().forEach(list -> list.removeIf(id -> { Entity e = level.getEntity(id); return e == null || !e.isAlive(); }));
        engaged.entrySet().removeIf(e -> e.getValue().isEmpty());
        for (GuidedMissile m : threats) {
            int want = m.spec() == MissileSpec.NUKE_ROCKET ? 2 : 1;
            List<UUID> sent = engaged.computeIfAbsent(m.getUUID(), u -> new ArrayList<>());
            if (sent.size() >= want || shared(level, m) >= want) continue;
            launch(level, m, site);
            sent.add(lastLaunched);
            return;
        }
    }

    private UUID lastLaunched;

    /** Interceptors from other launchers already after this missile. */
    private static int shared(ServerLevel level, GuidedMissile m) {
        int n = 0;
        for (var e : level.getEntitiesOfClass(GuidedMissile.class, m.getBoundingBox().inflate(500),
                x -> x.spec() == MissileSpec.INTERCEPTOR && m.getUUID().equals(x.target())))
            n++;
        return n;
    }

    private void launch(ServerLevel level, GuidedMissile m, Vec3 site) {
        Vec3 to = m.position().subtract(site).normalize();
        Vec3 dir = new Vec3(to.x * 0.4, 1.0, to.z * 0.4).normalize();          // up out of the cell, then over
        GuidedMissile i = GuidedMissile.launch(level, MissileSpec.INTERCEPTOR, null, GuidedMissile.Side.MACHINE, m, site, dir, 0f, tier);
        lastLaunched = i.getUUID();
        rounds--;
        cooldown = m.spec() == MissileSpec.NUKE_ROCKET ? 4 : 10;
        setChanged();
        level.playSound(null, site.x, site.y, site.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 4f, 0.8f);
        level.playSound(null, site.x, site.y, site.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.4f);
        if (m.owner() instanceof ServerPlayer sp && warned.add(m.getUUID()))
            sp.displayClientMessage(Component.translatable("message.heliosphere.point_defence_engaging", tier).withStyle(ChatFormatting.RED), true);
        if (warned.size() > 64) warned.clear();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tier = in.getIntOr("tier", 7);
        rounds = in.getIntOr("rounds", -1);
        reload = in.getIntOr("reload", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("tier", tier);
        out.putInt("rounds", rounds);
        out.putInt("reload", reload);
    }
}
