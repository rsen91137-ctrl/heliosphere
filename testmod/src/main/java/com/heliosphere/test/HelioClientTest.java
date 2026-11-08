package com.heliosphere.test;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.guide.Guide;
import com.heliosphere.industry.Industry;
import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.launch.LaunchConsoleBlockEntity;
import com.heliosphere.rocketry.menu.RocketMenu;
import com.heliosphere.rocketry.net.RocketNet;
import com.heliosphere.rocketry.part.PartBlockEntity;
import com.heliosphere.rocketry.physics.Blueprints;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end client test run in CI under a virtual display: creates a Solar System world, visits the Moon and
 * Mars, builds and launches a rocket, opens every screen, and saves a screenshot of each step.
 */
public class HelioClientTest implements FabricClientGameTest {
    private final List<String> report = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ResourceKey<WorldPreset> solar = ResourceKey.create(Registries.WORLD_PRESET, Heliosphere.id("solar_system"));
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            for (WorldCreationUiState.WorldTypeEntry e : s.getNormalPresetList())
                if (e.preset() != null && e.preset().is(solar)) s.setWorldType(e);
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        }).create()) {
            sp.getClientWorld().waitForChunksRender();
            step(ctx, "01_earth_spawn", () -> {
                cmd(sp, "time set 1000");
                look(ctx, 0, 10);
                ctx.waitTicks(40);
            });
            step(ctx, "02_moon_tranquility", () -> {
                cmd(sp, "time set 120000");
                cmd(sp, "execute as @p run heliosphere goto moon 0.67 23.47");
                sp.getClientWorld().waitForChunksRender();
                look(ctx, -60, -12);
                ctx.waitTicks(60);
            });
            step(ctx, "03_moon_sky_earth", () -> look(ctx, 90, -55));
            step(ctx, "04_moon_from_above", () -> {
                cmd(sp, "execute as @p at @s run tp @s ~ ~120 ~");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 30, 55);
                ctx.waitTicks(20);
            });
            step(ctx, "05_mars_olympus", () -> {
                cmd(sp, "time set 129000");
                cmd(sp, "execute as @p run heliosphere goto mars 18.0 -129.0");
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 90, -15);
                ctx.waitTicks(60);
            });
            step(ctx, "06_mars_valles", () -> {
                cmd(sp, "execute as @p run heliosphere goto mars -10.0 -73.0");
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~150 ~");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 0, 40);
                ctx.waitTicks(20);
            });
            step(ctx, "07_back_on_earth_rocket", () -> {
                cmd(sp, "time set 1000");
                cmd(sp, "execute as @p in minecraft:overworld run tp @s 0 200 0");
                ctx.waitTicks(20);
                sp.getServer().runOnServer(HelioClientTest::buildPad);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, -90, -20);
                ctx.waitTicks(40);
            });
            step(ctx, "08_launch_console", () -> {
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    if (p.level().getBlockEntity(consolePos) instanceof LaunchConsoleBlockEntity be) p.openMenu(be);
                });
                ctx.waitTicks(40);
            });
            step(ctx, "09_liftoff", () -> {
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    if (p.containerMenu instanceof RocketMenu m) {
                        m.handle(p, RocketNet.SELECT_DEST, "moon");
                        m.handle(p, RocketNet.SELECT_SITE, "shackleton");
                        m.handle(p, RocketNet.LAUNCH, "");
                    }
                });
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                ctx.waitTicks(150);
            });
            step(ctx, "10_ascent", () -> ctx.waitTicks(100));
            step(ctx, "11_space_transfer", () -> {
                ctx.waitFor(mc -> mc.level != null && mc.level.dimension().identifier().getPath().equals("space"), 1200);
                ctx.waitTicks(200);
                look(ctx, 0, 0);
            });
            step(ctx, "12_moon_landing", () -> {
                ctx.waitFor(mc -> mc.level != null && mc.level.dimension().identifier().getPath().equals("moon"), 1200);
                ctx.waitTicks(240);
                sp.getClientWorld().waitForChunksRender();
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            step(ctx, "13_machine_screen", () -> {
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    p.stopRiding();
                    BlockPos at = p.blockPosition().above(3).relative(Direction.NORTH, 2);
                    p.level().setBlock(at, Industry.MACHINES.get("alloy_furnace").defaultBlockState(), Block.UPDATE_ALL);
                    if (p.level().getBlockEntity(at) instanceof net.minecraft.world.MenuProvider mp) p.openMenu(mp);
                });
                ctx.waitTicks(30);
            });
            step(ctx, "14_creative_rocketry_tab", () -> openTab(ctx, "rocketry"));
            step(ctx, "15_creative_industry_tab", () -> openTab(ctx, "industry"));
            step(ctx, "16_creative_planets_tab", () -> openTab(ctx, "planets"));
            step(ctx, "17_star_scroll", () -> {
                ctx.setScreen(() -> null);
                sp.getServer().runOnServer(s -> Guide.open(s.getPlayerList().getPlayers().get(0)));
                ctx.waitTicks(30);
            });
            ctx.setScreen(() -> null);
        }
        writeReport();
    }

    private static BlockPos consolePos = BlockPos.ZERO;

    private static void buildPad(MinecraftServer s) {
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.level();
        int x0 = p.getBlockX() - 14, z0 = p.getBlockZ() - 1;
        level.getChunk(new BlockPos(x0, 0, z0));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 1; dy < 24; dy++) level.setBlock(new BlockPos(x0 + dx, y + dy, z0 + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), Rocketry.LAUNCH_PAD.defaultBlockState(), Block.UPDATE_ALL);
        }
        consolePos = new BlockPos(x0 + 3, y, z0);
        level.setBlock(consolePos, Rocketry.LAUNCH_CONSOLE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos base = new BlockPos(x0, y + 1, z0);
        for (RocketAnalyzer.Placed part : Blueprints.QIMING_1.parts(true)) {
            BlockPos at = base.offset(part.x(), part.y(), part.z());
            level.setBlock(at, Rocketry.PARTS.get(part.spec().id).defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof PartBlockEntity be) { be.setFill((float) part.fill()); be.setQuality(0.95f, true); }
        }
        p.teleportTo(level, x0 + 10.5, y + 1, z0 + 0.5, java.util.Set.of(), 90, -20, false);
    }

    private void openTab(ClientGameTestContext ctx, String tab) {
        ctx.runOnClient(mc -> {
            CreativeModeTab t = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Heliosphere.id(tab));
            CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false);
            mc.setScreen(screen);
            if (t != null) screen.setSelectedItemGroup(t);
        });
        ctx.waitTicks(20);
    }

    private static void cmd(TestSingleplayerContext sp, String c) { sp.getServer().runCommand(c); }

    private static void look(ClientGameTestContext ctx, float yaw, float pitch) {
        ctx.runOnClient(mc -> { if (mc.player != null) { mc.player.setYRot(yaw); mc.player.setXRot(pitch); } });
        ctx.waitTicks(5);
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
            try { ctx.takeScreenshot(name + "_failed"); } catch (Throwable ignored) {}
        }
    }

    private void writeReport() {
        try {
            Files.writeString(Path.of("heliosphere-test-report.txt"), String.join("\n", report));
        } catch (IOException ignored) {}
    }
}
