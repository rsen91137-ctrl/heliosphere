package com.heliosphere.rocketry.part;

import com.heliosphere.core.Reg;
import com.heliosphere.rocketry.physics.PartSpec;
import com.heliosphere.rocketry.physics.Propellant;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A rocket component. Its physics come from {@link PartSpec}; tanks and solid segments hold propellant. */
public class RocketPartBlock extends BaseEntityBlock {
    private final String specId;
    private final VoxelShape shape;

    public RocketPartBlock(Properties p, String specId, String model) {
        super(p);
        this.specId = specId;
        this.shape = switch (model) {
            case "cone" -> Shapes.or(Block.box(1, 0, 1, 15, 6, 15), Block.box(3, 6, 3, 13, 11, 13), Block.box(6, 11, 6, 10, 16, 10));
            case "fin" -> Shapes.or(Block.box(7, 0, 0, 9, 14, 16), Block.box(0, 0, 7, 16, 14, 9));
            case "leg" -> Block.box(2, 0, 2, 14, 16, 14);
            case "tower" -> Block.box(5, 0, 5, 11, 16, 11);
            case "slab" -> Block.box(0, 0, 0, 16, 6, 16);
            case "truss" -> Shapes.block();
            default -> Shapes.block();
        };
    }

    public PartSpec spec() { return PartSpec.get(specId); }
    public String specId() { return specId; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new RocketPartBlock(p, specId, "box")); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PartBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return shape; }

    /** Which canister item refuels a tank holding this propellant. */
    public static String canisterFor(Propellant p) {
        return switch (p) {
            case RP1 -> "canister_rp1";
            case LOX -> "canister_lox";
            case LH2 -> "canister_lh2";
            case CH4 -> "canister_ch4";
            case ARGON -> "canister_argon";
            default -> null;
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        PartSpec s = spec();
        if (s.category != PartSpec.Category.TANK) return InteractionResult.TRY_WITH_EMPTY_HAND;
        String can = canisterFor(s.stores);
        if (can == null || !stack.is(Reg.get(can))) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof PartBlockEntity be)) return InteractionResult.PASS;
        if (be.fill() >= s.capacity - 1e-4) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("message.heliosphere.tank_full"), true);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            be.setFill((float) s.capacity);
            if (!player.isCreative()) {
                stack.shrink(1);
                ItemStack empty = new ItemStack(Reg.get("empty_canister"));
                if (!player.getInventory().add(empty)) player.drop(empty, false);
            }
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PartBlockEntity be) {
            PartSpec s = spec();
            String q = String.format("公差 %.0f%%%s", be.tolerance() * 100, be.tested() ? " · 已试车" : "");
            String f = s.capacity > 0 ? String.format(" · %s %.2f/%.2f 吨", s.stores.nameZh, be.fill(), s.capacity) : "";
            player.displayClientMessage(Component.literal(q + f), true);
        }
        return InteractionResult.SUCCESS;
    }
}
