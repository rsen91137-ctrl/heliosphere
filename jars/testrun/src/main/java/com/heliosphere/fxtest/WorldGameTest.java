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
                    case "icons" -> icons(ctx, sp);
                    case "rocket" -> rocket(ctx, sp);
                    case "giant" -> giant(ctx, sp, "jupiter");
                    case "sam" -> sam(ctx, sp);
                    case "nuke" -> nuke(ctx, sp);
                    case "dodge" -> dodge(ctx, sp);
                    case "neptune" -> giant(ctx, sp, "neptune");
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
            cmd(sp, "execute as @p at @p run hsfortress " + tier);
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
            cmd(sp, "execute as @p at @p run hssiege " + kind + " 7");
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

    /**
     * Photographs every machine against a white and then a black backdrop with the game frozen; the pair gives
     * the colour and the transparency of each pixel (tools/gen/deployer_icons.py makes the item icons).
     */
    private void icons(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] base = new int[3];
        server(sp, s -> {
            ServerPlayer p = player(s);
            base[0] = p.getBlockX(); base[1] = 230; base[2] = p.getBlockZ();
        });
        ctx.runOnClient(mc -> { mc.options.hideGui = true; mc.options.fov().set(30); });
        List<String> ids = new ArrayList<>();
        for (var r : com.heliosphere.planets.tech.TechCombat.ROLES) ids.add(r.id());
        report.add("[info] roles " + ids);
        server(sp, s2 -> backdrop(player(s2).level(), base, true));
        server(sp, s2 -> { ServerPlayer p = player(s2); p.teleportTo(base[0] + 0.5, base[1] + 3, base[2] - 12); });
        ctx.waitTicks(40);
        sp.getClientWorld().waitForChunksRender();
        for (String id : ids) {
            double[] box = new double[6];
            server(sp, s -> {
                ServerPlayer p = player(s);
                var l = p.level();
                var m = com.heliosphere.planets.tech.TechCombat.spawn(l, id, base[0] + 0.5, base[1] + (id.contains("drone") || id.contains("carrier") ? 1.0 : 0), base[2] + 0.5, null);
                if (m == null) { report.add("[FAIL] spawn " + id); return; }
                m.setNoAi(true);
                m.setNoGravity(true);
                m.setYRot(215); m.setYBodyRot(215); m.setYHeadRot(215);
                m.addTag("icon");
            });
            ctx.waitTicks(45);                                   // spawn sparks die away
            server(sp, s -> {
                ServerPlayer p = player(s);
                for (var e : p.level().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(base[0] - 8, base[1] - 2, base[2] - 8, base[0] + 8, base[1] + 14, base[2] + 8), e -> e.getTags().contains("icon"))) {
                    var bb = e.getBoundingBox();
                    box[0] = bb.minX; box[1] = bb.minY; box[2] = bb.minZ; box[3] = bb.maxX; box[4] = bb.maxY; box[5] = bb.maxZ;
                }
            });
            double cx = (box[0] + box[3]) / 2, cy = (box[1] + box[4]) / 2, cz = (box[2] + box[5]) / 2;
            double h = box[4] - box[1], w = Math.max(box[3] - box[0], box[5] - box[2]);
            double dist = Math.max(h * 1.3, w * 1.6) / 0.536 + 2;
            double pitch = 14;
            server(sp, s -> {
                ServerPlayer p = player(s);
                p.getAbilities().flying = true; p.onUpdateAbilities();
                double eye = cy + Math.sin(Math.toRadians(pitch)) * dist;
                p.teleportTo(cx, eye - p.getEyeHeight(), cz - Math.cos(Math.toRadians(pitch)) * dist);
            });
            look(ctx, 0, (float) pitch);
            ctx.waitTicks(4);
            cmd(sp, "tick freeze");
            for (int pass = 0; pass < 2; pass++) {
                boolean white = pass == 0;
                server(sp, s -> backdrop(player(s).level(), base, white));
                ctx.waitTicks(4);
                sp.getClientWorld().waitForChunksRender();
                ctx.waitTicks(4);
                shot(ctx, "icon_" + id + (white ? "_w" : "_b"));
            }
            cmd(sp, "tick unfreeze");
            server(sp, s -> {
                for (var e : player(s).level().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(base[0] - 30, base[1] - 5, base[2] - 30, base[0] + 30, base[1] + 30, base[2] + 30), e -> e.getTags().contains("icon")))
                    e.discard();
            });
            ctx.waitTicks(3);
        }
        ctx.runOnClient(mc -> { mc.options.hideGui = false; mc.options.fov().set(70); });
    }

    private static void backdrop(net.minecraft.server.level.ServerLevel l, int[] b, boolean white) {
        var block = (white ? net.minecraft.world.level.block.Blocks.WHITE_CONCRETE : net.minecraft.world.level.block.Blocks.BLACK_CONCRETE).defaultBlockState();
        var m = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -40; dz <= 8; dz++) l.setBlock(m.set(b[0] + dx, b[1] - 1, b[2] + dz), block, 2);
            for (int dy = 0; dy < 26; dy++) l.setBlock(m.set(b[0] + dx, b[1] + dy, b[2] + 8), block, 2);
        }
    }

    /** Places a prebuilt rocket, photographs it, then launches it with the player aboard and records the view. */
    private void rocket(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String item = System.getProperty("heliosphere.test.rocket", "rocket_qiming_1");
        cmd(sp, "execute in minecraft:overworld run tp @p 300 120 300");
        cmd(sp, "gamemode creative @a");
        ctx.waitTicks(40);
        sp.getClientWorld().waitForChunksRender();
        int[] at = new int[3];
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = p.level();
            int x = p.getBlockX() + 12, z = p.getBlockZ();
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y - 1, z + dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy < 60; dy++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y + dy, z + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            at[0] = x; at[1] = y; at[2] = z;
            var stack = new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get(item));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5), net.minecraft.core.Direction.UP,
                    new net.minecraft.core.BlockPos(x, y - 1, z), false);
            var r = stack.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            report.add("[info] place rocket: " + r);
        });
        ctx.waitTicks(20);
        for (int i = 0; i < 3; i++) {
            int k = i;
            server(sp, s -> {
                ServerPlayer p = player(s);
                p.getAbilities().flying = true; p.onUpdateAbilities();
                double a = Math.toRadians(30 + k * 120), d = 14 + k * 6;
                p.teleportTo(at[0] + 0.5 + Math.sin(a) * d, at[1] + 4 + k * 6, at[2] + 0.5 - Math.cos(a) * d);
                p.setYRot((float) (Math.toDegrees(-a)) + 180 - 180);
            });
            float yaw = (float) -(30 + k * 120) + 180;
            look(ctx, yaw - 180, 8 - k * 4);
            ctx.waitTicks(20);
            sp.getClientWorld().waitForChunksRender();
            shot(ctx, "r_exterior_" + k);
        }
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = p.level();
            var list = l.getEntitiesOfClass(com.heliosphere.rocketry.entity.RocketEntity.class, new net.minecraft.world.phys.AABB(at[0] - 10, at[1] - 4, at[2] - 10, at[0] + 10, at[1] + 60, at[2] + 10));
            report.add("[info] rockets: " + list.size());
            if (list.isEmpty()) return;
            var rocket = list.get(0);
            report.add("[info] parts: " + rocket.structure().parts().stream().map(com.heliosphere.rocketry.entity.RocketStructure.Part::spec).toList());
            p.getAbilities().flying = false; p.onUpdateAbilities();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.teleportTo(at[0] + 3.5, at[1], at[2] + 0.5);
            var res = rocket.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
            report.add("[info] right-click: " + res + " tags " + rocket.getTags());
            report.add("[info] launched, phase " + rocket.phase() + ", vehicle " + p.getVehicle());
        });
        cmd(sp, "effect give @a resistance infinite 4 true");
        ctx.waitTicks(60);
        shot(ctx, "r_boarded_hold");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(30);
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        ctx.runOnClient(mc -> { if (mc.player != null) mc.player.setYRot(mc.player.getYRot() + 120); });
        ctx.waitTicks(100);
        shot(ctx, "r_boarded_walk");
        cmd(sp, "execute as @p run hsrocket go");
        int[] times = {20, 60, 120, 200, 300, 420, 560, 760};
        int last = 0;
        for (int i = 0; i < times.length; i++) {
            ctx.waitTicks(times[i] - last);
            last = times[i];
            if (i == 3) ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
            if (i == 4) ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
            int k = i;
            if (i % 2 == 1) ctx.runOnClient(mc -> { if (mc.player != null) mc.player.setYRot(mc.player.getYRot() + 70); });
            server(sp, s -> {
                ServerPlayer p = player(s);
                report.add("[info] t" + k + " pos " + p.position() + " vehicle " + p.getVehicle());
            });
            shot(ctx, "r_launch_" + k);
        }
    }

    /** Visits a giant planet from the top haze down to the ocean. */
    private void giant(ClientGameTestContext ctx, TestSingleplayerContext sp, String planet) {
        cmd(sp, "gamemode creative @a");
        cmd(sp, "effect give @a fire_resistance infinite 0 true");
        cmd(sp, "effect give @a resistance infinite 4 true");
        int[][] stops = {{1160, 20}, {700, -30}, {330, 10}, {262, -5}, {-100, 10}, {-560, 0}, {-660, 30}, {-1960, 45}};
        for (int[] st : stops) {
            cmd(sp, "execute in heliosphere:" + planet + " run tp @p 40 " + st[0] + " 40");
            server(sp, s -> { ServerPlayer p = player(s); p.getAbilities().flying = true; p.onUpdateAbilities(); });
            look(ctx, 30, st[1]);
            ctx.waitTicks(60);
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(20);
            shot(ctx, "g_" + planet + "_" + st[0]);
            server(sp, s -> {
                ServerPlayer p = player(s);
                report.add("[info] " + planet + " y " + p.getY() + " T " + com.heliosphere.core.env.Environment.temperatureC(p.level(), p.blockPosition())
                        + " block below " + p.level().getBlockState(p.blockPosition().below(3)));
            });
        }
    }

    /** Three batteries (radar + launcher each) on a flat range; drones come in from the north. */
    private void sam(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = p.level();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            for (int dx = -30; dx <= 30; dx++) for (int dz = -20; dz <= 20; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, y - 1, z0 + dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 20; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, y + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            o[0] = x0; o[1] = y; o[2] = z0;
            String[][] batteries = {{"buk_tads", "buk_telar"}, {"samp_tads", "samp_tel"}, {"iris_tads", "iris_tel"}};
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 2; j++) {
                    var pos = new net.minecraft.core.BlockPos(x0 - 18 + i * 18 + (j == 0 ? -3 : 3), y, z0 + 6);
                    var block = com.heliosphere.core.Reg.getBlock(batteries[i][j]);
                    var state = block.defaultBlockState().setValue(com.heliosphere.planets.defense.sam.SamBlock.FACING, net.minecraft.core.Direction.NORTH);
                    l.setBlock(pos, state, 3);
                    block.setPlacedBy(l, pos, state, p, new net.minecraft.world.item.ItemStack(block.asItem()));
                    if (l.getBlockEntity(pos) instanceof com.heliosphere.planets.defense.sam.SamBlockEntity be) be.setEnergy(be.capacity());
                }
            }
            p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(x0 + 0.5, y + 7, z0 + 24.5);
        });
        look(ctx, 180, 12);
        ctx.waitTicks(40);
        sp.getClientWorld().waitForChunksRender();
        shot(ctx, "sam_batteries");
        server(sp, s -> {
            ServerPlayer p = player(s);
            p.teleportTo(o[0] + 9.5, o[1] + 3, o[2] + 14.5);
        });
        look(ctx, 160, 8);
        ctx.waitTicks(10);
        shot(ctx, "sam_close");
        // targets: drones approaching from the north at altitude
        server(sp, s -> {
            var l = player(s).level();
            String[] ids = {"plasma_drone", "hunter_drone", "missile_drone", "plasma_drone", "assault_drone", "hunter_drone"};
            for (int i = 0; i < ids.length; i++) {
                var m = com.heliosphere.planets.tech.TechCombat.spawn(l, ids[i], o[0] - 40 + i * 16, o[1] + 30 + (i % 3) * 8, o[2] - 90 - (i % 2) * 20, null);
                if (m != null) { m.setNoAi(false); m.setDeltaMovement(0, 0, 0.4); }
            }
        });
        server(sp, s -> {
            ServerPlayer p = player(s);
            p.teleportTo(o[0] + 0.5, o[1] + 10, o[2] + 30.5);
        });
        look(ctx, 180, -12);
        int[] waits = {30, 20, 20, 30, 40, 60, 80};
        for (int i = 0; i < waits.length; i++) {
            ctx.waitTicks(waits[i]);
            shot(ctx, "sam_engage_" + i);
        }
        server(sp, s -> {
            var l = player(s).level();
            int alive = 0;
            for (var m : l.getEntitiesOfClass(com.heliosphere.planets.tech.TechMob.class, new net.minecraft.world.phys.AABB(o[0] - 200, o[1] - 50, o[2] - 250, o[0] + 200, o[1] + 150, o[2] + 100))) alive++;
            report.add("[info] drones left after engagement: " + alive);
        });
        cmd(sp, "kill @e[type=!player]");
    }

    /** The nuke against a hillside: the crater must follow the ground and leave nothing hanging in the air. */
    private void nuke(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = p.level();
            int x0 = p.getBlockX() + 400, z0 = p.getBlockZ();
            l.getChunk(x0 >> 4, z0 >> 4);
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            o[0] = x0; o[1] = y; o[2] = z0;
            p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(x0 - 160.5, y + 60, z0 + 0.5);
            var look = new net.minecraft.world.phys.Vec3(160, -60, 0).normalize();
            com.heliosphere.planets.defense.sam.GuidedMissile.launch(l, com.heliosphere.planets.defense.sam.MissileSpec.NUKE_ROCKET, p,
                    com.heliosphere.planets.defense.sam.GuidedMissile.Side.DEFENCE, null, p.getEyePosition().add(look.scale(2)), look, 0, 0);
        });
        cmd(sp, "effect give @a resistance infinite 4 true");
        cmd(sp, "gamemode creative @a");
        look(ctx, -90, 20);
        ctx.waitTicks(25);
        shot(ctx, "nuke_flight");
        ctx.waitTicks(1200);
        server(sp, s -> {
            ServerPlayer p = player(s);
            p.teleportTo(o[0] - 110.5, o[1] + 70, o[2] + 0.5);
            // count blocks with air under them inside the crater area: floating debris
            var l = p.level();
            int floating = 0;
            for (int dx = -80; dx <= 80; dx += 2) for (int dz = -80; dz <= 80; dz += 2) {
                int x = o[0] + dx, z = o[2] + dz;
                int top = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                for (int yy = top - 1; yy > top - 6; yy--) {
                    var pos = new net.minecraft.core.BlockPos(x, yy, z);
                    if (l.getBlockState(pos).isAir() && !l.getBlockState(pos.above()).isAir() && l.getBlockState(pos.above()).isSolid()) { floating++; break; }
                }
            }
            report.add("[info] nuke crater columns with a hanging block: " + floating);
        });
        look(ctx, -90, 30);
        ctx.waitTicks(80);
        sp.getClientWorld().waitForChunksRender();
        shot(ctx, "nuke_crater");
        look(ctx, -60, 45);
        ctx.waitTicks(20);
        shot(ctx, "nuke_crater2");
    }

    /** Machine missiles of three levels against a player standing in the open: they hit, but cover saves you. */
    private void dodge(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "gamemode survival @a");
        cmd(sp, "effect give @a resistance infinite 3 true");
        cmd(sp, "effect give @a regeneration infinite 4 true");
        for (int tier : new int[]{2, 6, 10}) {
            server(sp, s -> {
                ServerPlayer p = player(s);
                var l = p.level();
                p.getAbilities().flying = false; p.onUpdateAbilities();
                var mob = com.heliosphere.planets.tech.TechCombat.spawn(l, tier >= 9 ? "colossus" : tier >= 5 ? "hunter_drone" : "missile_drone",
                        p.getX() + 30, p.getY() + 12, p.getZ(), null);
                if (mob != null) {
                    mob.setNoAi(true);
                    var dir = p.getEyePosition().subtract(mob.getEyePosition()).normalize().add(0, 0.5, 0);
                    com.heliosphere.planets.defense.sam.GuidedMissile.launch(l, com.heliosphere.planets.defense.sam.MissileSpec.machine(tier), mob,
                            com.heliosphere.planets.defense.sam.GuidedMissile.Side.MACHINE, p, mob.getEyePosition(), dir, 10, tier);
                }
            });
            look(ctx, -90, -10);
            ctx.waitTicks(12);
            shot(ctx, "dodge_t" + tier + "_flight");
            ctx.waitTicks(40);
            look(ctx, -90, 30);
            shot(ctx, "dodge_t" + tier + "_after");
            cmd(sp, "kill @e[type=!player,type=!item]");
            ctx.waitTicks(10);
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
