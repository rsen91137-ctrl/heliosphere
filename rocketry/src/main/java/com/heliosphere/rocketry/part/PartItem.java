package com.heliosphere.rocketry.part;

import com.heliosphere.core.env.HelioComponents;
import com.heliosphere.rocketry.physics.PartSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Block item for rocket parts that lists their engineering data in the tooltip. */
public class PartItem extends BlockItem {
    public PartItem(Block block, Properties p) { super(block, p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        PartSpec s = ((RocketPartBlock) getBlock()).spec();
        if (s == null) return;
        out.accept(Component.literal(String.format("质量 %.2f 吨 · 结构强度 %.0f 吨 · T%d", s.dryMass, s.strength, s.tier)).withStyle(ChatFormatting.GRAY));
        if (s.isEngine()) {
            out.accept(Component.literal(String.format("推力 %.0f kN（真空） · 比冲 %.0f/%.0f s", s.thrustVac, s.ispSea, s.ispVac)).withStyle(ChatFormatting.AQUA));
            if (s.oxidizer != com.heliosphere.rocketry.physics.Propellant.NONE)
                out.accept(Component.literal(String.format("推进剂 %s + %s · 混合比 %.1f", s.fuel.nameZh, s.oxidizer.nameZh, s.mixtureRatio)).withStyle(ChatFormatting.AQUA));
        }
        if (s.capacity > 0) {
            Integer f = stack.get(HelioComponents.FILL);
            out.accept(Component.literal(String.format("容量 %.2f 吨 %s%s", s.capacity, s.stores.nameZh,
                    f != null ? String.format("（已装 %.2f）", f / 1000f) : "")).withStyle(ChatFormatting.YELLOW));
        }
        if (s.navTier > 0) out.accept(Component.literal(s.navTier == 1 ? "导航范围：地月系统" : "导航范围：内太阳系").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (s.lifeSupportDays > 0) out.accept(Component.literal(String.format("生命维持 %.0f 天", s.lifeSupportDays)).withStyle(ChatFormatting.GREEN));
        if (s.shieldRatingKmS > 0) out.accept(Component.literal(String.format("可承受再入速度 %.1f km/s", s.shieldRatingKmS)).withStyle(ChatFormatting.GOLD));
        HelioComponents.Quality q = stack.get(HelioComponents.QUALITY);
        if (q != null) out.accept(Component.literal(String.format("制造公差 %.0f%% · %s", q.tolerance() * 100, q.tested() ? "已试车" : "未试车"))
                .withStyle(q.tested() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
