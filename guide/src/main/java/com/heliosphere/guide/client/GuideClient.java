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
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.heliosphere.planets.enemy.Vanguard.SCOUT,
                ctx -> new net.minecraft.client.renderer.entity.VexRenderer(ctx) {
                    @Override public net.minecraft.resources.Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.VexRenderState s) { return com.heliosphere.core.Heliosphere.id("textures/entity/scout_drone.png"); }
                });
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.heliosphere.planets.enemy.Vanguard.CRAWLER,
                ctx -> new net.minecraft.client.renderer.entity.SilverfishRenderer(ctx) {
                    @Override public net.minecraft.resources.Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.LivingEntityRenderState s) { return com.heliosphere.core.Heliosphere.id("textures/entity/mech_crawler.png"); }
                });
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.heliosphere.planets.enemy.Vanguard.SENTINEL,
                ctx -> new net.minecraft.client.renderer.entity.RavagerRenderer(ctx) {
                    @Override public net.minecraft.resources.Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.RavagerRenderState s) { return com.heliosphere.core.Heliosphere.id("textures/entity/vanguard_sentinel.png"); }
                });
        ClientPlayNetworking.registerGlobalReceiver(com.heliosphere.planets.Stargate.Open.TYPE, (p, ctx) -> ctx.client().execute(() -> ctx.client().setScreen(new StargateScreen(p.bodies()))));
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
