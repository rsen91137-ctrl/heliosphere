package com.heliosphere.planets.gear;

import com.heliosphere.core.Reg;
import com.heliosphere.core.env.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registers the jetpacks and runs their server side: fuel used while the jump key is held in the air, refills from
 * hydrogen canisters carried, exhaust flames and sound for everyone around, no fall damage while braking on thrust,
 * and no "flying is not enabled" kick. The flight itself is computed by the wearer's game (see JetpackClient), like
 * any movement.
 */
public final class Jetpacks {
    private Jetpacks() {}

    public static final Map<JetpackKind, JetpackItem> ITEMS = new EnumMap<>(JetpackKind.class);
    /** Players thrusting this tick (the flight check leaves them alone). */
    public static final Set<UUID> THRUSTING = new HashSet<>();

    public static void init() {
        Reg.tab("tools");
        for (JetpackKind k : JetpackKind.values()) {
            Map<ArmorType, Integer> def = new EnumMap<>(ArmorType.class);
            for (ArmorType t : ArmorType.values()) def.put(t, k.armour);
            ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("heliosphere", k.id));
            TagKey<Item> repair = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("heliosphere", "repairs_jetpack"));
            Holder<SoundEvent> sound = SoundEvents.ARMOR_EQUIP_IRON;
            ArmorMaterial mat = new ArmorMaterial(400, def, 8, sound, k == JetpackKind.HEAVY || k == JetpackKind.ADAPTIVE ? 2f : 0f, 0f, repair, asset);
            ITEMS.put(k, Reg.item(k.id, p -> new JetpackItem(p, k), new Item.Properties().humanoidArmor(mat, ArmorType.CHESTPLATE).stacksTo(1)));
        }
        ServerTickEvents.END_SERVER_TICK.register(Jetpacks::tick);
    }

    public static JetpackItem worn(net.minecraft.world.entity.player.Player p) {
        return p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof JetpackItem j ? j : null;
    }

    private static void tick(MinecraftServer server) {
        THRUSTING.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JetpackItem j = worn(p);
            if (j == null || p.isSpectator() || p.getAbilities().flying || p.isPassenger()) continue;
            ItemStack pack = p.getItemBySlot(EquipmentSlot.CHEST);
            Input in = p.getLastClientInput();
            int fuel = JetpackItem.fuel(pack);
            if (fuel < JetpackItem.TANK - JetpackItem.CANISTER && refill(p)) { fuel += JetpackItem.CANISTER; JetpackItem.setFuel(pack, fuel); }
            boolean up = in.jump() && !p.onGround();
            boolean hold = j.kind.hold && in.shift() && !p.onGround();
            boolean side = j.kind.side > 0 && !p.onGround() && (in.forward() || in.backward() || in.left() || in.right());
            if (fuel <= 0 || !(up || hold || side)) continue;
            THRUSTING.add(p.getUUID());
            int use = (int) Math.ceil(j.kind.burn * 4 * (up ? 1 : 0.35));
            if (!p.isCreative()) JetpackItem.setFuel(pack, fuel - use);
            p.fallDistance = 0;
            ServerLevel l = p.level();
            double g = Environment.gravity(l);
            if (up && j.kind.thrust(g) < 0.08 * g && server.getTickCount() % 40 == 0)
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.heliosphere.jetpack_too_weak", String.format("%.2f", g), String.format("%.1f", j.kind.ceiling()))
                        .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            // exhaust from the two nozzles on the back
            float yaw = (float) Math.toRadians(p.yBodyRot);
            Vec3 back = new Vec3(Math.sin(yaw) * 0.35, 0, -Math.cos(yaw) * 0.35);
            Vec3 across = new Vec3(Math.cos(yaw) * 0.2, 0, Math.sin(yaw) * 0.2);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 n = p.position().add(back).add(across.scale(s)).add(0, 0.65, 0);
                if (up) l.sendParticles(j.kind == JetpackKind.LOW_G ? ParticleTypes.CLOUD : ParticleTypes.FLAME, n.x, n.y, n.z, 1, 0.03, 0.02, 0.03, 0.01);
                if (server.getTickCount() % 2 == 0) l.sendParticles(ParticleTypes.SMOKE, n.x, n.y - 0.2, n.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
            if (server.getTickCount() % 6 == 0)
                l.playSound(null, p.getX(), p.getY(), p.getZ(), j.kind == JetpackKind.LOW_G ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.BLAZE_SHOOT,
                        SoundSource.PLAYERS, up ? 0.35f : 0.15f, j.kind == JetpackKind.HEAVY ? 0.6f : 1.4f);
        }
    }

    /** Takes one hydrogen canister from the inventory into the pack's tank. */
    private static boolean refill(ServerPlayer p) {
        Item canister = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("heliosphere", "canister_hydrogen"));
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(canister)) { s.shrink(1); return true; }
        }
        return false;
    }
}
