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
        ctx.runOnClient(mc -> {
            mc.options.languageCode = "zh_cn";
            mc.getLanguageManager().setSelected("zh_cn");
            mc.reloadResourcePacks();
        });
        ctx.waitTicks(100);
        ResourceKey<WorldPreset> solar = ResourceKey.create(Registries.WORLD_PRESET, Heliosphere.id("solar_system"));
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            for (WorldCreationUiState.WorldTypeEntry e : s.getNormalPresetList())
                if (e.preset() != null && e.preset().is(solar)) s.setWorldType(e);
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        }).create()) {
            sp.getClientWorld().waitForChunksRender();
            step(ctx, "00_intro", () -> {
                ctx.runOnClient(mc -> mc.setScreen(new com.heliosphere.guide.client.IntroScreen()));
                ctx.waitTicks(170);
            });
            ctx.runOnClient(mc -> mc.setScreen(null));
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
                cmd(sp, "execute as @p at @s run tp @s ~ ~45 ~");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 30, 30);
                ctx.waitTicks(20);
            });
            step(ctx, "05_mars_olympus", () -> {
                cmd(sp, "time set 129000");
                cmd(sp, "execute as @p run heliosphere goto mars 18.0 -129.0");
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~30 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 90, 12);
                ctx.waitTicks(60);
            });
            step(ctx, "06_mars_valles", () -> {
                cmd(sp, "execute as @p run heliosphere goto mars -10.0 -73.0");
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~40 ~");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 0, 25);
                ctx.waitTicks(20);
            });
            step(ctx, "06a_venus", () -> {
                cmd(sp, "execute as @p run heliosphere goto venus 65.2 3.3");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 0, 10);
            });
            step(ctx, "06b_io", () -> {
                cmd(sp, "execute as @p run heliosphere goto io 15 51");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 90, 15);
            });
            step(ctx, "06c_europa", () -> {
                cmd(sp, "execute as @p run heliosphere goto europa 9.7 -86.6");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 0, 20);
            });
            step(ctx, "06d_titan", () -> {
                cmd(sp, "execute as @p run heliosphere goto titan 62 -50");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 180, 15);
            });
            step(ctx, "06e_jupiter", () -> {
                cmd(sp, "execute as @p run heliosphere goto jupiter 0 0");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 0, -5);
            });
            step(ctx, "06f_saturn", () -> {
                cmd(sp, "execute as @p run heliosphere goto saturn 0 0");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 90, -25);
            });
            step(ctx, "06g_pluto", () -> {
                cmd(sp, "execute as @p run heliosphere goto pluto 25 175");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 0, 10);
            });
            step(ctx, "06h_miranda", () -> {
                cmd(sp, "execute as @p run heliosphere goto miranda -18 -12");
                ctx.waitTicks(40);
                sp.getClientWorld().waitForChunksRender();
                cmd(sp, "execute as @p at @s run tp @s ~ ~12 ~");
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                look(ctx, 90, 0);
            });
            step(ctx, "07_back_on_earth_rocket", () -> {
                cmd(sp, "time set 1000");
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    BlockPos spawn = s.overworld().getRespawnData().globalPos().pos();
                    p.teleportTo(s.overworld(), spawn.getX() + 0.5, 200, spawn.getZ() + 0.5, java.util.Set.of(), 0, 0, false);
                });
                ctx.waitTicks(20);
                sp.getServer().runOnServer(s -> note(buildPad(s)));
                ctx.waitTicks(20);
                sp.getClientWorld().waitForChunksRender();
                ctx.waitTicks(20);
            });
            step(ctx, "08_launch_console", () -> {
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    if (p.level().getBlockEntity(consolePos) instanceof LaunchConsoleBlockEntity be) {
                        p.openMenu(be);
                        if (p.containerMenu instanceof RocketMenu m) {
                            m.handle(p, RocketNet.SELECT_DEST, "moon");
                            m.handle(p, RocketNet.SELECT_SITE, "shackleton");
                        }
                        note("console menu: " + p.containerMenu.getClass().getSimpleName());
                    } else note("no console block entity at " + consolePos);
                });
                ctx.waitTicks(40);
            });
            step(ctx, "09_liftoff", () -> {
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    if (p.containerMenu instanceof RocketMenu m) m.handle(p, RocketNet.LAUNCH, "");
                    else note("menu closed before launch: " + p.containerMenu.getClass().getSimpleName());
                    note("after launch: vehicle=" + (p.getVehicle() == null ? "none" : p.getVehicle().getType().toShortString()));
                });
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                ctx.waitTicks(150);
            });
            step(ctx, "10_ascent", () -> {
                ctx.waitTicks(100);
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    if (p.getVehicle() instanceof com.heliosphere.rocketry.entity.RocketEntity r)
                        note("ascent: phase=" + r.phase() + " y=" + (int) r.getY());
                    else note("ascent: not riding, at " + p.blockPosition() + " in " + p.level().dimension().identifier());
                });
            });
            step(ctx, "11_space_transfer", () -> {
                sp.getServer().runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    note("before transfer wait: " + p.level().dimension().identifier() + " y=" + (int) p.getY()
                            + " vehicle=" + (p.getVehicle() instanceof com.heliosphere.rocketry.entity.RocketEntity r ? "rocket phase " + r.phase() : "none"));
                });
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

    private static void note(String line) {
        synchronized (NOTES) { NOTES.add(line); }
    }

    private static final List<String> NOTES = new ArrayList<>();

    /** Builds a stone platform with a launch pad, console and the Qiming-1 rocket next to the player. */
    private static String buildPad(MinecraftServer s) {
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.level();
        int x0 = p.getBlockX() - 6, z0 = p.getBlockZ();
        level.getChunk(new BlockPos(x0, 0, z0));
        int y = level.getMinY();
        for (int yy = level.getMaxY(); yy > level.getMinY(); yy--) {
            if (!level.getBlockState(new BlockPos(x0, yy, z0)).isAir()) { y = yy + 1; break; }
        }
        y = Math.max(y, level.getSeaLevel() + 1);
        for (int dx = -4; dx <= 10; dx++) for (int dz = -4; dz <= 4; dz++) {
            for (int dy = 1; dy < 40; dy++) level.setBlock(new BlockPos(x0 + dx, y + dy, z0 + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            boolean pad = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
            level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), pad ? Rocketry.LAUNCH_PAD.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        consolePos = new BlockPos(x0 + 3, y + 1, z0 + 2);
        level.setBlock(consolePos.below(), Rocketry.LAUNCH_PAD.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(consolePos, Rocketry.LAUNCH_CONSOLE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos base = new BlockPos(x0, y + 1, z0);
        int n = 0;
        for (RocketAnalyzer.Placed part : Blueprints.QIMING_1.parts(true)) {
            BlockPos at = base.offset(part.x(), part.y(), part.z());
            level.setBlock(at, Rocketry.PARTS.get(part.spec().id).defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof PartBlockEntity be) { be.setFill((float) part.fill()); be.setQuality(0.95f, true); n++; }
        }
        p.teleportTo(level, x0 + 7.5, y + 1, z0 + 0.5, java.util.Set.of(), 90, -25, false);
        return "pad at " + base + " parts=" + n + " console=" + consolePos;
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
            List<String> all = new ArrayList<>(report);
            all.add("--- notes ---");
            synchronized (NOTES) { all.addAll(NOTES); }
            Files.writeString(Path.of("heliosphere-test-report.txt"), String.join("\n", all));
        } catch (IOException ignored) {}
    }
}
