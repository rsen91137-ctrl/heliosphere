package com.heliosphere.fxtest;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.disaster.ImpactExpansionLedger;
import com.heliosphere.planets.weapon.GunItem;
import com.heliosphere.planets.weapon.Nuke;
import com.heliosphere.planets.weapon.fx.CombatVfx;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fires the guns, sets off a tactical nuke and an asteroid, and screenshots the effects. */
public class FxGameTest implements FabricClientGameTest {
    private final List<String> report = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> { mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn"); mc.reloadResourcePacks(); });
        ctx.waitTicks(60);
        ResourceKey<WorldPreset> solar = ResourceKey.create(Registries.WORLD_PRESET, Heliosphere.id("solar_system"));
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            for (WorldCreationUiState.WorldTypeEntry e : s.getNormalPresetList())
                if (e.preset() != null && e.preset().is(solar)) s.setWorldType(e);
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        }).create()) {
            sp.getClientWorld().waitForChunksRender();
            cmd(sp, "gamerule doDaylightCycle false");
            cmd(sp, "gamerule doWeatherCycle false");
            cmd(sp, "weather clear");
            cmd(sp, "time set 6000");
            cmd(sp, "gamemode creative @a");
            // a flat stone pad to stand on, and a target wall 24 blocks east
            sp.getServer().runOnServer(FxGameTest::stage);
            ctx.waitTicks(40);
            sp.getClientWorld().waitForChunksRender();

            gun(ctx, sp, "assault_rifle", "01_rifle_tracers", 6, 3);
            gun(ctx, sp, "sniper_rifle", "02_sniper", 1, 2);
            gun(ctx, sp, "laser_rifle", "03_laser", 4, 2);
            gun(ctx, sp, "grenade_launcher", "04_grenade", 1, 6);
            gun(ctx, sp, "plasma_cannon", "05_plasma", 1, 5);
            gun(ctx, sp, "antimatter_rifle", "06_antimatter", 1, 4);
            gun(ctx, sp, "singularity_cannon", "07_singularity", 1, 14);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            gun(ctx, sp, "assault_rifle", "08_rifle_third_person", 5, 3);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            // ---- tactical nuke 260 blocks east, watched from the pad
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                ServerLevel l = p.level();
                origin = p.blockPosition();
                int x = origin.getX() + 260, z = origin.getZ();
                Nuke.detonate(l, p, new Vec3(x + 0.5, surface(l, x, z), z + 0.5));
            });
            look(ctx, -90, -14);
            int waited = 0;
            for (int at : new int[]{4, 20, 50, 110, 220, 420, 700, 1100}) {
                ctx.waitTicks(at - waited);
                waited = at;
                shot(ctx, String.format("1%d_nuke_t%04.1f", at < 100 ? 0 : 1, at / 20f));
            }
            // the crater from above its rim, then from its floor
            step(ctx, "19_nuke_crater", () -> {
                hover(sp, 260 - 95, -95, 75);
                ctx.waitTicks(80);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, -45, 32);
                ctx.waitTicks(40);
            });
            step(ctx, "19b_nuke_crater_floor", () -> {
                hover(sp, 260 + 30, 0, 3);
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 90, 18);
                ctx.waitTicks(20);
            });

            // ---- asteroid impact 7000 blocks north, watched from 40 blocks above the pad
            hover(sp, -40, 0, 40);
            ctx.waitTicks(60);
            sp.getClientWorld().waitForChunksRender();
            sp.getServer().runOnServer(s -> {
                ServerLevel l = player(s).level();
                int x = origin.getX(), z = origin.getZ() - 7000;
                ImpactExpansionLedger.record(l, x, surface(l, x, z), z, l.getGameTime());
            });
            look(ctx, 180, -6);
            waited = 0;
            for (int at : new int[]{3, 40, 160, 500, 1000, 1900, 3200}) {
                ctx.waitTicks(at - waited);
                waited = at;
                shot(ctx, String.format("2%d_impact_t%05.1f", at < 1000 ? 0 : 1, at / 20f));
            }
            // an older impact 18000 blocks east, five days ago: the magma tide has had time to spread here
            sp.getServer().runOnServer(s -> {
                ServerLevel l = player(s).level();
                ImpactExpansionLedger.record(l, origin.getX() + 18000, 64, origin.getZ(), l.getGameTime() - 5 * 24000L);
            });
            hover(sp, 0, 0, 55);
            for (int i = 0; i < 4; i++) {
                look(ctx, 90 * i, 50);
                ctx.waitTicks(400);
                shot(ctx, "3" + i + "_magma_tide_from_above");
            }
            step(ctx, "35_magma_tide_ground", () -> {
                hover(sp, 0, 0, 2);
                ctx.waitTicks(40);
                look(ctx, 30, 12);
                ctx.waitTicks(20);
            });
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            report.add("[FAIL] " + sw);
        }
        writeReport();
    }

    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    private static BlockPos origin = BlockPos.ZERO;

    /** Height of the ground (ignoring leaves) at x, z, loading the chunk if need be. */
    private static int surface(ServerLevel l, int x, int z) {
        l.getChunk(new BlockPos(x, 0, z));
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Puts the player, flying, {@code up} blocks above the ground at origin + (dx, dz). */
    private static void hover(TestSingleplayerContext sp, int dx, int dz, int up) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            int x = origin.getX() + dx, z = origin.getZ() + dz;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(x + 0.5, surface(p.level(), x, z) + up, z + 0.5);
        });
    }

    private static void stage(MinecraftServer s) {
        ServerPlayer p = player(s);
        ServerLevel l = p.level();
        int x0 = p.getBlockX(), z0 = p.getBlockZ();
        l.getChunk(new BlockPos(x0, 0, z0));
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0) + 3;
        for (int dx = -4; dx <= 34; dx++) for (int dz = -8; dz <= 8; dz++) {
            l.setBlockAndUpdate(new BlockPos(x0 + dx, y - 1, z0 + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for (int dy = 0; dy < 12; dy++) l.setBlockAndUpdate(new BlockPos(x0 + dx, y + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        for (int dz = -8; dz <= 8; dz++) for (int dy = 0; dy < 8; dy++)
            l.setBlockAndUpdate(new BlockPos(x0 + 28, y + dy, z0 + dz), (dy + dz & 1) == 0 ? net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()
                    : net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        p.teleportTo(x0 + 0.5, y, z0 + 0.5);
        p.setYRot(-90); p.setXRot(0);
    }

    /** Puts the gun in hand, aims at the wall and pulls the trigger {@code shots} times. */
    private void gun(ClientGameTestContext ctx, TestSingleplayerContext sp, String id, String name, int shots, int settle) {
        step(ctx, name, () -> {
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("heliosphere", id)));
                p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                com.heliosphere.planets.tech.Attachments.reload(p, p.getMainHandItem());   // a fresh gun comes with an empty magazine
            });
            look(ctx, -90, 2);
            ctx.waitTicks(120);
            for (int i = 0; i < shots; i++) {
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = player(s);
                    p.setYRot(-90 + (p.getRandom().nextFloat() - 0.5f) * 6); p.setXRot(2 + (p.getRandom().nextFloat() - 0.5f) * 4);
                    p.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()));
                    GunItem.handleShoot(p);
                });
                ctx.waitTicks(1);
            }
            ctx.waitTicks(settle);
        });
    }

    private void shot(ClientGameTestContext ctx, String name) { step(ctx, name, () -> { }); }

    private static void cmd(TestSingleplayerContext sp, String c) { sp.getServer().runCommand(c); }

    private static void look(ClientGameTestContext ctx, float yaw, float pitch) {
        ctx.runOnClient(mc -> { if (mc.player != null) { mc.player.setYRot(yaw); mc.player.setXRot(pitch); } });
        ctx.waitTicks(3);
    }

    private void step(ClientGameTestContext ctx, String name, Runnable r) {
        try {
            r.run();
            ctx.takeScreenshot(name);
            report.add("[ok] " + name);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            report.add("[FAIL] " + name + "\n" + sw);
        }
    }

    private void writeReport() {
        try { Files.writeString(Path.of("fx-test-report.txt"), String.join("\n", report)); } catch (IOException ignored) {}
    }
}
