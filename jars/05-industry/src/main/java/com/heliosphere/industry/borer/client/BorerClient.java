package com.heliosphere.industry.borer.client;

import com.heliosphere.industry.borer.BorerNet;
import com.heliosphere.industry.borer.Borers;
import com.heliosphere.industry.borer.TunnelBorer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the tunnel borer: renderer, control panel, HUD and keys. While riding a borer the inventory key
 * opens its control panel instead of the inventory, and the auto key (G by default) switches the auto run on or off.
 */
public final class BorerClient implements ClientModInitializer {
    static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("heliosphere", "tunnel_borer"));
    static KeyMapping autoKey;

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(Borers.ENTITY, TunnelBorerRenderer::new);
        autoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.heliosphere.borer_auto", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
        ClientPlayNetworking.registerGlobalReceiver(BorerNet.Panel.TYPE, (p, ctx) -> ctx.client().execute(() -> BorerPanelScreen.receive(p)));
        ClientTickEvents.START_CLIENT_TICK.register(BorerClient::keys);
        ClientTickEvents.END_CLIENT_TICK.register(BorerEffects::tick);
        // under the action bar, titles and chat, so the machine's messages are never covered
        HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, Identifier.fromNamespaceAndPath("heliosphere", "tunnel_borer"), BorerHud::render);
    }

    /** The borer the local player is riding, or null. */
    static TunnelBorer riding(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof TunnelBorer b && !b.isRemoved() ? b : null;
    }

    /** Before vanilla handles the keys: take the inventory key and the auto key while riding a borer. */
    private static void keys(Minecraft mc) {
        TunnelBorer b = riding(mc);
        if (b == null) {
            while (autoKey.consumeClick()) { }
            return;
        }
        boolean panel = false;
        while (mc.options.keyInventory.consumeClick()) panel = true;
        if (panel) {
            BorerPanelScreen.requested();
            ClientPlayNetworking.send(new BorerNet.Action(BorerNet.PANEL, b.getId()));
        }
        while (autoKey.consumeClick()) ClientPlayNetworking.send(new BorerNet.Action(BorerNet.AUTO, b.getId()));
    }
}
