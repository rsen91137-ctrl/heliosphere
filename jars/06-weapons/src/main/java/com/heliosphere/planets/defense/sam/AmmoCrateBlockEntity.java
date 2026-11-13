package com.heliosphere.planets.defense.sam;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Rounds held by an ammunition crate, by system (buk, samp, iris). */
public class AmmoCrateBlockEntity extends BlockEntity {
    /** Launchers whose centre lies within this many blocks rearm from the crate. */
    public static final double RANGE = 16;
    public static final int CAPACITY = 48;
    private static final Map<Level, Set<AmmoCrateBlockEntity>> LOADED = new WeakHashMap<>();

    private final Map<String, Integer> rounds = new LinkedHashMap<>();

    public AmmoCrateBlockEntity(BlockPos pos, BlockState state) { super(SamSites.CRATE_ENTITY, pos, state); }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && LOADED.containsKey(level)) LOADED.get(level).remove(this);
    }

    public int total() { int n = 0; for (int v : rounds.values()) n += v; return n; }
    public int count(String system) { return rounds.getOrDefault(system, 0); }

    /** Loads up to {@code n} rounds of a system; returns how many went in. */
    int load(String system, int n) {
        int room = Math.max(0, CAPACITY - total());
        int put = Math.min(room, n);
        if (put > 0) { rounds.merge(system, put, Integer::sum); setChanged(); }
        return put;
    }

    /** Empties the crate without dropping anything (the wrench carries the rounds in the item). */
    void clearForPacking() { rounds.clear(); setChanged(); }

    /** Restores rounds carried in a packed crate item. */
    void unpack(CompoundTag t) {
        for (String s : SamSites.SYSTEMS) { int n = t.getIntOr("rounds_" + s, 0); if (n > 0) rounds.merge(s, n, Integer::sum); }
        setChanged();
    }

    /** Takes one round for a launcher, if the crate has one. */
    boolean take(String system) {
        int have = count(system);
        if (have <= 0) return false;
        if (have == 1) rounds.remove(system); else rounds.put(system, have - 1);
        setChanged();
        return true;
    }

    /** The nearest loaded crate within range of {@code c} holding rounds of this system, or null. */
    static AmmoCrateBlockEntity near(Level level, Vec3 c, String system) {
        Set<AmmoCrateBlockEntity> all = LOADED.get(level);
        if (all == null) return null;
        AmmoCrateBlockEntity best = null;
        double bestD = RANGE * RANGE;
        for (AmmoCrateBlockEntity e : new ArrayList<>(all)) {
            if (e.isRemoved() || e.count(system) <= 0) continue;
            double d = Vec3.atCenterOf(e.getBlockPos()).distanceToSqr(c);
            if (d <= bestD) { bestD = d; best = e; }
        }
        return best;
    }

    void status(Player p, int loaded) {
        List<Component> parts = new ArrayList<>();
        for (String s : SamSites.SYSTEMS)
            if (count(s) > 0) parts.add(Component.translatable("message.heliosphere.sam_crate_line", Component.translatable("message.heliosphere.sam_round." + s), count(s)));
        if (parts.isEmpty()) parts.add(Component.translatable("message.heliosphere.sam_crate_empty"));
        Component list = Component.empty();
        for (int i = 0; i < parts.size(); i++) list = ((net.minecraft.network.chat.MutableComponent) list).append(i == 0 ? Component.empty() : Component.literal(" · ")).append(parts.get(i));
        p.displayClientMessage(Component.translatable(loaded > 0 ? "message.heliosphere.sam_crate_loaded" : "message.heliosphere.sam_crate_status",
                loaded, total(), CAPACITY, list, (int) RANGE), true);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide()) return;
        for (Map.Entry<String, Integer> e : rounds.entrySet()) {
            int n = e.getValue();
            while (n > 0) {
                int k = Math.min(16, n);
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(com.heliosphere.core.Reg.get(SamSites.roundOf(e.getKey())), k));
                n -= k;
            }
        }
        rounds.clear();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        rounds.clear();
        for (String s : SamSites.SYSTEMS) { int n = in.getIntOr("rounds_" + s, 0); if (n > 0) rounds.put(s, n); }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        for (Map.Entry<String, Integer> e : rounds.entrySet()) out.putInt("rounds_" + e.getKey(), e.getValue());
    }
}
