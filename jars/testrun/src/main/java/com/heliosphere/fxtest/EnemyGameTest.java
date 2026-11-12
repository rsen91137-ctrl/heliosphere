package com.heliosphere.fxtest;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.planets.tech.TechCombat;
import com.heliosphere.planets.tech.TechMob;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lines up every machine for screenshots, lets the new ones attack, and opens the bestiary. */
public class EnemyGameTest implements FabricClientGameTest {
    private final List<String> report = new ArrayList<>();
    private static BlockPos origin = BlockPos.ZERO;

    private static final String[] LINEUP = {
            "patrol_drone", "assault_drone", "swarm_drone", "siege_drone", "missile_drone", "laser_drone", "hunter_drone",
            "security_walker", "stalker", "repair_automaton", "shield_bearer", "arc_hound", "emp_spider",
            "flame_walker", "mortar_walker", "siege_walker", "rail_sentinel", "titan_walker"};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String only = System.getProperty("heliosphere.test.only");
        if (only != null && (only.equals("guns") || only.equals("fx"))) return;
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
            sp.getServer().runOnServer(EnemyGameTest::arena);
            ctx.waitTicks(40);
            sp.getClientWorld().waitForChunksRender();
            boolean all = !"bestiary".equals(System.getProperty("heliosphere.test.only"));
            if (all) {

            // ---- the lineup, frozen, photographed in three stretches
            sp.getServer().runOnServer(s -> {
                ServerLevel l = player(s).level();
                for (int i = 0; i < LINEUP.length; i++) {
                    double x = origin.getX() + 4 + i * 4.2 + (LINEUP[i].equals("titan_walker") ? 2 : 0);
                    TechMob m = TechCombat.spawn(l, LINEUP[i], x, origin.getY(), origin.getZ() + 6, null);
                    if (m == null) { note("spawn failed: " + LINEUP[i]); continue; }
                    m.setNoAi(true);
                    m.setYRot(180); m.setYBodyRot(180); m.setYHeadRot(180);
                }
            });
            for (int part = 0; part < 4; part++) {
                int p = part;
                step(ctx, "0" + part + "_lineup", () -> {
                    hover(sp, 4 + p * 18 + 8, -4, 2.5);
                    ctx.waitTicks(20);
                    look(ctx, 0, 12);
                    ctx.waitTicks(30);
                });
            }
            step(ctx, "04_titan_close", () -> { hover(sp, 4 + 17 * 4.2 + 2, -2, 3.5); look(ctx, 0, -12); ctx.waitTicks(30); });
            sp.getServer().runCommand("kill @e[type=!player]");
            ctx.waitTicks(10);

            // ---- the new weapons, against a player who cannot be hurt
            sp.getServer().runCommand("gamemode survival @a");
            sp.getServer().runCommand("effect give @a resistance infinite 4 true");
            sp.getServer().runCommand("effect give @a fire_resistance infinite 0 true");
            sp.getServer().runCommand("effect give @a regeneration infinite 4 true");
            attack(ctx, sp, "flame_walker", 6, "10_flamethrower", 30);
            attack(ctx, sp, "mortar_walker", 26, "11_mortar_warning", 100);
            attack(ctx, sp, "laser_drone", 28, "12_lance_aim", 110);
            attack(ctx, sp, "swarm_drone", 16, "13_swarm", 60);
            attack(ctx, sp, "stalker", 5, "14_stalker", 40);
            sp.getServer().runOnServer(s -> {
                ServerLevel l = player(s).level();
                TechCombat.spawn(l, "shield_bearer", origin.getX() + 14, origin.getY(), origin.getZ(), null);
                TechCombat.spawn(l, "security_walker", origin.getX() + 15, origin.getY(), origin.getZ() + 3, null);
            });
            attack(ctx, sp, "siege_walker", 18, "15_shielded_group", 60);
            attack(ctx, sp, "titan_walker", 30, "16_titan", 140);
            sp.getServer().runCommand("kill @e[type=!player]");
            }

            // ---- the bestiary in the scroll
            sp.getServer().runCommand("gamemode creative @a");
            for (String family : new String[]{null, "地面机械", "末日教团"}) {
                step(ctx, "2" + (family == null ? "0" : family.equals("地面机械") ? "1" : "2") + "_bestiary", () -> {
                    ctx.runOnClient(mc -> {
                        try {
                            Class<?> c = Class.forName("com.heliosphere.guide.client.BestiaryScreen");
                            var ctor = c.getDeclaredConstructor(Screen.class);
                            ctor.setAccessible(true);
                            Screen screen = (Screen) ctor.newInstance((Screen) null);
                            if (family != null) {
                                var f = c.getDeclaredField("family");
                                f.setAccessible(true);
                                f.set(screen, family);
                            }
                            mc.setScreen(screen);
                        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                    });
                    ctx.waitTicks(20);
                });
            }
            ctx.runOnClient(mc -> mc.setScreen(null));
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            report.add("[FAIL] " + sw);
        }
        synchronized (NOTES) { report.addAll(NOTES); }
        try { Files.writeString(Path.of("enemy-test-report.txt"), String.join("\n", report)); } catch (IOException ignored) {}
    }

    private static final List<String> NOTES = new ArrayList<>();
    private static void note(String s) { synchronized (NOTES) { NOTES.add(s); } }

    /** Spawns one machine {@code distance} blocks east, lets it fight for a while and screenshots it mid-attack. */
    private void attack(ClientGameTestContext ctx, TestSingleplayerContext sp, String id, int distance, String name, int ticks) {
        step(ctx, name, () -> {
            hover(sp, 0, 0, 0);
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.getAbilities().flying = false; p.onUpdateAbilities();
                TechMob m = TechCombat.spawn(p.level(), id, origin.getX() + distance, origin.getY() + (id.contains("drone") ? 4 : 0), origin.getZ(), p);
                if (m == null) note("spawn failed: " + id);
                else m.setTarget(p);
            });
            look(ctx, -90, id.contains("drone") ? -8 : 2);
            for (int i = 0; i < ticks; i += 5) {
                ctx.waitTicks(5);
                look(ctx, -90, id.contains("drone") ? -8 : 2);
            }
        });
        sp.getServer().runCommand("kill @e[type=!player]");
        ctx.waitTicks(5);
    }

    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    private static void arena(MinecraftServer s) {
        ServerPlayer p = player(s);
        ServerLevel l = p.level();
        int x0 = p.getBlockX(), z0 = p.getBlockZ();
        l.getChunk(new BlockPos(x0, 0, z0));
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0) + 2;
        for (int dx = -10; dx <= 90; dx++) for (int dz = -14; dz <= 14; dz++) {
            l.setBlockAndUpdate(new BlockPos(x0 + dx, y - 1, z0 + dz), (dx / 6 + dz / 6 & 1) == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
            for (int dy = 0; dy < 14; dy++) l.setBlockAndUpdate(new BlockPos(x0 + dx, y + dy, z0 + dz), Blocks.AIR.defaultBlockState());
        }
        origin = new BlockPos(x0, y, z0);
        p.teleportTo(x0 + 0.5, y, z0 + 0.5);
    }

    private static void hover(TestSingleplayerContext sp, double dx, double dz, double up) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            p.getAbilities().flying = up > 0.5;
            p.onUpdateAbilities();
            p.teleportTo(origin.getX() + dx + 0.5, origin.getY() + up, origin.getZ() + dz + 0.5);
        });
    }

    private static void look(ClientGameTestContext ctx, float yaw, float pitch) {
        ctx.runOnClient(mc -> { if (mc.player != null) { mc.player.setYRot(yaw); mc.player.setXRot(pitch); } });
        ctx.waitTicks(2);
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
}
