package com.heliosphere.fxtest;

import com.heliosphere.core.Heliosphere;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** World features: machine fortresses, turrets, planets and the rocket cabin (-Ponly=world[:part,...]). */
public class WorldGameTest implements FabricClientGameTest {
    private final List<String> report = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String only = System.getProperty("heliosphere.test.only");
        if (only == null || !only.startsWith("world")) return;
        String parts = only.contains(":") ? only.substring(only.indexOf(':') + 1) : "fortress";
        ctx.runOnClient(mc -> { mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn"); mc.reloadResourcePacks(); });
        ctx.waitTicks(60);
        ResourceKey<WorldPreset> solar = ResourceKey.create(Registries.WORLD_PRESET, Heliosphere.id("solar_system"));
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            for (WorldCreationUiState.WorldTypeEntry e : s.getNormalPresetList())
                if (e.preset() != null && e.preset().is(solar)) s.setWorldType(e);
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        }).create()) {
            sp.getClientWorld().waitForChunksRender();
            for (String c : new String[]{"gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "weather clear", "time set 6000", "gamemode creative @a"}) sp.getServer().runCommand(c);
            for (String part : parts.split(",")) {
                switch (part) {
                    case "fortress" -> fortress(ctx, sp);
                    case "siege" -> siege(ctx, sp);
                    default -> extra(ctx, sp, part);
                }
            }
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            report.add("[FAIL] " + sw);
        }
        try { Files.writeString(Path.of("world-test-report.txt"), String.join("\n", report)); } catch (IOException ignored) {}
    }

    /** Hook for further scenarios. */
    private void extra(ClientGameTestContext ctx, TestSingleplayerContext sp, String part) { report.add("[skip] unknown part " + part); }

    private void fortress(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        for (int tier : new int[]{3, 12}) {
            cmd(sp, "tp @a ~ ~ ~" + (tier * 200));
            ctx.waitTicks(40);
            sp.getClientWorld().waitForChunksRender();
            cmd(sp, "hsfortress " + tier);
            ctx.waitTicks(20);
            server(sp, s -> {
                ServerPlayer p = player(s);
                p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(p.getX() + 40 - 46, p.getY() + 34, p.getZ() - 46);
            });
            look(ctx, -45, 28);
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(40);
            shot(ctx, "f" + tier + "_overview");
            server(sp, s -> {
                ServerPlayer p = player(s);
                p.teleportTo(p.getX() + 46, p.getY() - 26, p.getZ() + 46 + 9 + tier + 8);
            });
            look(ctx, 180, 10);
            ctx.waitTicks(30);
            shot(ctx, "f" + tier + "_gate");
            cmd(sp, "gamemode survival @a");
            cmd(sp, "effect give @a resistance infinite 4 true");
            ctx.waitTicks(200);
            shot(ctx, "f" + tier + "_defended");
            cmd(sp, "gamemode creative @a");
            server(sp, s -> {
                ServerPlayer p = player(s);
                var l = p.level();
                var fab = com.heliosphere.core.Reg.getBlock("robot_fabricator");
                int n = 0;
                for (var b : net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-40, -30, -70), p.blockPosition().offset(40, 40, 10)))
                    if (l.getBlockState(b).is(fab)) { l.destroyBlock(b, false); n++; }
                report.add("[info] fabricators destroyed: " + n);
            });
            cmd(sp, "kill @e[type=!player]");
            ctx.waitTicks(60);
            shot(ctx, "f" + tier + "_conquered");
        }
    }

    private void siege(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "gamemode survival @a");
        cmd(sp, "effect give @a resistance infinite 4 true");
        cmd(sp, "effect give @a regeneration infinite 4 true");
        for (String kind : new String[]{"assault", "bombard"}) {
            cmd(sp, "hssiege " + kind + " 7");
            ctx.waitTicks(40);
            shot(ctx, "s_" + kind + "_warn");
            ctx.waitTicks(600);
            look(ctx, 0, -20);
            shot(ctx, "s_" + kind + "_wave");
            ctx.waitTicks(100);
            shot(ctx, "s_" + kind + "_fight");
            cmd(sp, "kill @e[type=!player,type=!item,type=!experience_orb]");
            ctx.waitTicks(1000);
            shot(ctx, "s_" + kind + "_end");
        }
        cmd(sp, "gamemode creative @a");
    }

    private static void cmd(TestSingleplayerContext sp, String c) { sp.getServer().runCommand(c); }
    private static void server(TestSingleplayerContext sp, Consumer<MinecraftServer> r) { sp.getServer().runOnServer(r::accept); }
    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    private static void look(ClientGameTestContext ctx, float yaw, float pitch) {
        ctx.runOnClient(mc -> { if (mc.player != null) { mc.player.setYRot(yaw); mc.player.setXRot(pitch); } });
        ctx.waitTicks(2);
    }

    private void shot(ClientGameTestContext ctx, String name) {
        try { ctx.takeScreenshot(name); report.add("[ok] " + name); }
        catch (Throwable t) { report.add("[FAIL] " + name + " " + t); }
    }
}
