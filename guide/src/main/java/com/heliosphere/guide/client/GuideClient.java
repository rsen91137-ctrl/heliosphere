package com.heliosphere.guide.client;

import com.heliosphere.guide.Guide;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GuideClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Guide.OpenScroll.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ctx.client().setScreen(new ScrollScreen(p.done()))));
        net.minecraft.client.KeyMapping key = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.KeyMapping(
                "key.heliosphere.encyclopedia", org.lwjgl.glfw.GLFW.GLFW_KEY_H, net.minecraft.client.KeyMapping.Category.register(com.heliosphere.core.Heliosphere.id("main"))));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) if (mc.screen == null) mc.setScreen(new EncyclopediaScreen());
        });
        ClientPlayNetworking.registerGlobalReceiver(Guide.OpenIntro.TYPE, (p, ctx) -> ctx.client().execute(() -> ctx.client().setScreen(new IntroScreen())));
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
            net.minecraft.world.item.ItemStack st = player.getItemInHand(hand);
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("star_map")) return net.minecraft.world.InteractionResult.PASS;
            net.minecraft.client.Minecraft.getInstance().setScreen(new StarMapScreen());
            return net.minecraft.world.InteractionResult.SUCCESS;
        });
    }
}
