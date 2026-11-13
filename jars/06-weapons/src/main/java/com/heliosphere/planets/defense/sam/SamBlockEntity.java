package com.heliosphere.planets.defense.sam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * An air-defence vehicle as it was saved before vehicles became entities: on its first tick it turns into a
 * {@link SamVehicle} where it stands, with its missiles, charge, mode and owner, and the old blocks go away.
 */
public class SamBlockEntity extends BlockEntity {
    private final CompoundTag carry = new CompoundTag();
    private UUID owner;

    public SamBlockEntity(BlockPos pos, BlockState state) { super(SamSites.BLOCK_ENTITY, pos, state); }

    void tick(ServerLevel level) {
        if (!(getBlockState().getBlock() instanceof SamBlock b)) return;
        Direction f = getBlockState().getValue(SamBlock.FACING);
        if (SamVehicle.place(level, b.kind, Vec3.atBottomCenterOf(worldPosition), f.toYRot(), owner, carry.isEmpty() ? null : carry, EntitySpawnReason.LOAD) == null) return;
        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);      // the hull goes with it
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int ammo = in.getIntOr("ammo", -1);
        if (ammo >= 0) carry.putInt("sam_ammo", ammo);
        carry.putLong("sam_power", in.getLongOr("power", 0));
        carry.putBoolean("sam_manual", in.getBooleanOr("manual", false));
        carry.putBoolean("sam_iff", in.getBooleanOr("iff", true));
        String o = in.getStringOr("owner", "");
        if (!o.isEmpty()) try { owner = UUID.fromString(o); } catch (IllegalArgumentException ignored) {}
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (carry.contains("sam_ammo")) out.putInt("ammo", carry.getIntOr("sam_ammo", 0));
        out.putLong("power", carry.getLongOr("sam_power", 0));
        out.putBoolean("manual", carry.getBooleanOr("sam_manual", false));
        out.putBoolean("iff", carry.getBooleanOr("sam_iff", true));
        if (owner != null) out.putString("owner", owner.toString());
    }

    /**
     * Line of sight over up to 500 blocks without loading anything: a vanilla ray cast would load (or generate) every
     * chunk on the way and stall the server. The ray is marched in half-block steps through chunks already in memory;
     * a chunk that is not in memory does not block.
     */
    static boolean clear(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1) return true;
        int n = (int) (len * 2);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        net.minecraft.world.level.chunk.LevelChunk chunk = null;
        int ccx = Integer.MIN_VALUE, ccz = Integer.MIN_VALUE, lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            at.set(a.x + d.x * t, a.y + d.y * t, a.z + d.z * t);
            if (at.getX() == lx && at.getY() == ly && at.getZ() == lz) continue;
            lx = at.getX(); ly = at.getY(); lz = at.getZ();
            if (level.isOutsideBuildHeight(ly)) continue;
            int cx = lx >> 4, cz = lz >> 4;
            if (cx != ccx || cz != ccz) { chunk = level.getChunkSource().getChunkNow(cx, cz); ccx = cx; ccz = cz; }
            if (chunk == null) continue;                                      // not in memory: nothing known to block
            BlockState s = chunk.getBlockState(at);
            if (!s.isAir() && !s.getCollisionShape(level, at).isEmpty()) return false;
        }
        return true;
    }
}
