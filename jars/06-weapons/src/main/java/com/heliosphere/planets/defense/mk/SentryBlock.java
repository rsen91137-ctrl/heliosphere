package com.heliosphere.planets.defense.mk;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A defence turret. The block is drawn by SentryRenderer; it never hides the faces of the blocks around it. */
public class SentryBlock extends BaseEntityBlock {
    public final TurretKind kind;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public SentryBlock(Properties p, TurretKind kind) { super(p.noOcclusion()); this.kind = kind; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new SentryBlock(p, kind)); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override protected VoxelShape getOcclusionShape(BlockState s) { return Shapes.empty(); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SentryBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != Sentries.BLOCK_ENTITY ? null : (l, p, s, be) -> ((SentryBlockEntity) be).tick((ServerLevel) l);
    }

    /** Mined with a pickaxe, the turret drops with its upgrade and its charge, and keeps them when set down again. */
    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof SentryBlockEntity be
                && !be.creative() && (be.mk() > 0 || be.energy() > 0))
            for (ItemStack st : drops) if (st.getItem() == asItem()) store(st, be.mk(), be.energy());
        return drops;
    }

    public static final String MK = "turret_mk", POWER = "turret_power";

    static void store(ItemStack st, int mk, long power) {
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, st, t -> { t.putInt(MK, mk); t.putLong(POWER, power); });
    }

    /** {Mk, power} stored on a turret item, or null. */
    public static long[] stored(ItemStack st) {
        var d = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        var t = d.copyTag();
        return t.contains(MK) ? new long[]{t.getIntOr(MK, 0), t.getLongOr(POWER, 0)} : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        long[] kept = stored(stack);
        if (kept != null && level.getBlockEntity(pos) instanceof SentryBlockEntity be0)
            be0.adopt(kept[1], (int) kept[0], placer instanceof Player pp ? pp.getUUID() : null);
        if (level.getBlockEntity(pos) instanceof SentryBlockEntity be && placer instanceof Player p) {
            be.owner(p.getUUID());
            if (p.isCreative()) be.creative(true);                 // creative mode: fights at once, no power needed
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(com.heliosphere.core.Reg.get("steel_plate")) && level.getBlockEntity(pos) instanceof SentryBlockEntity hurt) {   // field repair
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!hurt.repair()) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (!player.isCreative()) stack.shrink(1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.4f);
            player.displayClientMessage(Component.translatable("message.heliosphere.turret_repaired", (int) hurt.integrity(), (int) hurt.maxIntegrity()), true);
            return InteractionResult.SUCCESS;
        }
        int kit = Sentries.kitLevel(stack);
        if (kit <= 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SentryBlockEntity be) {
            if (be.mk() >= kit) {
                player.displayClientMessage(Component.translatable("message.heliosphere.turret_has_mk", be.mk()), true);
                return InteractionResult.FAIL;
            }
            be.upgrade(kit);
            if (!player.isCreative()) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.heliosphere.turret_upgraded", kit), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SentryBlockEntity be) be.status(player);
        return InteractionResult.SUCCESS;
    }
}
