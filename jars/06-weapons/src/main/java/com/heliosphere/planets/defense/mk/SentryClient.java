package com.heliosphere.planets.defense.mk;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class SentryClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tctx, ttype, lines) -> {
            long[] kept = SentryBlock.stored(stack);
            if (kept == null || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) || !(bi.getBlock() instanceof SentryBlock)) return;
            lines.add(net.minecraft.network.chat.Component.translatable("item.heliosphere.turret_kept", kept[0], kept[1]).withStyle(net.minecraft.ChatFormatting.AQUA));
        });
        BlockEntityRenderers.register(Sentries.BLOCK_ENTITY, SentryRenderer::new);
        com.heliosphere.planets.gear.client.ShoulderSentryClient.init();
    }
}
