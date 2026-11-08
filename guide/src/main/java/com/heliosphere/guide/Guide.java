package com.heliosphere.guide;

import com.heliosphere.core.CreativeTabs;
import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.Reg;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * The Star Scroll: a chaptered guide from the first ore to Mars. Objectives are tracked server-side
 * ("ever obtained" is remembered as player tags) and shown when the scroll is opened.
 */
public final class Guide implements ModInitializer {
    public static final String TAG_PREFIX = "heliosphere.got.";
    public static final String GIVEN_TAG = "heliosphere.scroll_given";

    public record OpenScroll(List<String> done) implements CustomPacketPayload {
        public static final Type<OpenScroll> TYPE = new Type<>(Heliosphere.id("open_scroll"));
        public static final StreamCodec<ByteBuf, OpenScroll> CODEC =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(OpenScroll::new, OpenScroll::done);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static Item SCROLL;

    @Override
    public void onInitialize() {
        Reg.tab("guide");
        SCROLL = Reg.item("star_scroll", ScrollItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        PayloadTypeRegistry.playS2C().register(OpenScroll.TYPE, OpenScroll.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer p = handler.getPlayer();
            if (!p.getTags().contains(GIVEN_TAG)) {
                p.addTag(GIVEN_TAG);
                ItemStack s = new ItemStack(SCROLL);
                if (!p.getInventory().add(s)) p.drop(s, false);
                p.displayClientMessage(Component.translatable("message.heliosphere.scroll_given"), false);
            }
        }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) track(p);
        });
        CreativeTabs.addExtra("rocketry", () -> new ItemStack(SCROLL));
    }

    /** Remembers every objective item the player has ever held. */
    public static void track(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            var key = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (!key.getNamespace().equals(Heliosphere.MOD_ID)) continue;
            String id = key.getPath();
            if (GenGuide.OBJECTIVES.contains(id) && !p.getTags().contains(TAG_PREFIX + id)) {
                p.addTag(TAG_PREFIX + id);
                p.displayClientMessage(Component.translatable("message.heliosphere.objective", s.getHoverName()), true);
            }
        }
    }

    public static void open(ServerPlayer p) {
        track(p);
        List<String> done = new ArrayList<>();
        for (String t : p.getTags()) if (t.startsWith(TAG_PREFIX)) done.add(t.substring(TAG_PREFIX.length()));
        ServerPlayNetworking.send(p, new OpenScroll(done));
    }
}
