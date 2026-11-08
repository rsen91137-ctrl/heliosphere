package com.heliosphere.rocketry.item;

import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.part.PartBlockEntity;
import com.heliosphere.rocketry.physics.Blueprints;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Creative-tab rocket: places a complete, fuelled, test-fired rocket from a blueprint. */
public class PrebuiltRocketItem extends Item {
    private final String blueprint;

    public PrebuiltRocketItem(Properties p, String blueprint) { super(p); this.blueprint = blueprint; }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Blueprints.Blueprint bp = Blueprints.get(blueprint);
        if (bp == null) return InteractionResult.FAIL;
        BlockPos base = ctx.getClickedPos().above();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        for (RocketAnalyzer.Placed p : bp.parts(true)) {
            if (!level.getBlockState(base.offset(p.x(), p.y(), p.z())).canBeReplaced()) {
                if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.heliosphere.no_room"), true);
                return InteractionResult.FAIL;
            }
        }
        for (RocketAnalyzer.Placed p : bp.parts(true)) {
            BlockPos at = base.offset(p.x(), p.y(), p.z());
            Block b = Rocketry.PARTS.get(p.spec().id);
            level.setBlock(at, b.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof PartBlockEntity be) {
                be.setFill((float) p.fill());
                be.setQuality(0.95f, true);
            }
        }
        if (ctx.getPlayer() != null && !ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag flag) {
        Blueprints.Blueprint bp = Blueprints.get(blueprint);
        if (bp == null) return;
        out.accept(Component.literal(bp.descZh()).withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal(String.format("%d 个部件 · 高 %d 格", bp.parts(true).size(), bp.layers().length)).withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable("tooltip.heliosphere.prebuilt").withStyle(ChatFormatting.YELLOW));
    }
}
