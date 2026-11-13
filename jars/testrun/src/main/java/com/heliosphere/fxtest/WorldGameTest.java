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
                    case "rocket" -> rocket(ctx, sp, false);
                    case "room" -> rocket(ctx, sp, true);
                    case "giant" -> giant(ctx, sp, "jupiter");
                    case "sam" -> sam(ctx, sp);
                    case "nuke" -> nuke(ctx, sp);
                    case "dodge" -> dodge(ctx, sp);
                    case "spawn" -> spawn(ctx, sp);
                    case "armor" -> armor(ctx, sp);
                    case "neptune" -> giant(ctx, sp, "neptune");
                    case "collapse" -> collapse(ctx, sp);
                    case "battery" -> battery(ctx, sp);
                    case "ammo" -> ammo(ctx, sp);
                    case "airdef" -> airdef(ctx, sp, "buk");
                    case "airdef_samp" -> airdef(ctx, sp, "samp");
                    case "airdef_iris" -> airdef(ctx, sp, "iris");
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
        int k = 0;
        for (int tier : new int[]{4, 9, 14}) {
            int R = com.heliosphere.hostiles.fortress.FortressBuilder.radius(tier);
            cmd(sp, "tp @a ~ ~ ~" + (400 + k++ * 500));
            ctx.waitTicks(40);
            sp.getClientWorld().waitForChunksRender();
            cmd(sp, "execute as @p at @p run hsfortress " + tier);
            ctx.waitTicks(tier >= 13 ? 700 : 300);
            int[] c = new int[3];
            server(sp, s -> {
                ServerPlayer p = player(s);
                c[0] = p.getBlockX() + R + 24; c[1] = p.getBlockY(); c[2] = p.getBlockZ();
                p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(c[0] - R * 1.1, c[1] + R * 0.9 + 20, c[2] - R * 1.1);
            });
            look(ctx, -45, 35);
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(40);
            shot(ctx, "f" + tier + "_overview");
            server(sp, s -> {
                ServerPlayer p = player(s);
                p.teleportTo(c[0] + 0.5, c[1] + 6, c[2] + R + 14);
            });
            look(ctx, 180, 5);
            ctx.waitTicks(30);
            shot(ctx, "f" + tier + "_gate");
            server(sp, s -> {
                ServerPlayer p = player(s);
                p.teleportTo(c[0] + 6.5, c[1] + 22, c[2] + R / 2.0);
            });
            look(ctx, 160, 30);
            ctx.waitTicks(30);
            shot(ctx, "f" + tier + "_inside");
            server(sp, s -> {
                var l = player(s).level();
                int machines = l.getEntitiesOfClass(com.heliosphere.planets.tech.TechMob.class, new net.minecraft.world.phys.AABB(c[0] - R - 10, c[1] - 40, c[2] - R - 10, c[0] + R + 10, c[1] + 80, c[2] + R + 10)).size();
                var fab = com.heliosphere.core.Reg.getBlock("robot_fabricator");
                int fabs = 0;
                for (var b : net.minecraft.core.BlockPos.betweenClosed(c[0] - R - 2, c[1] - 30, c[2] - R - 2, c[0] + R + 2, c[1] + 60, c[2] + R + 2))
                    if (l.getBlockState(b).is(fab)) { fabs++; l.destroyBlock(b, false); }
                report.add("[info] fortress " + tier + " radius " + R + ": machines " + machines + ", fabricators " + fabs);
            });
            cmd(sp, "kill @e[type=!player]");
            ctx.waitTicks(60);
            shot(ctx, "f" + tier + "_conquered");
        }
    }

    /** Natural deployment after forty days on the world clock (the released code counted raw ticks). */
    private void spawn(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "gamerule doMobSpawning true");
        cmd(sp, "time add 960000");
        cmd(sp, "gamemode survival @a");
        cmd(sp, "effect give @a resistance infinite 4 true");
        cmd(sp, "effect give @a regeneration infinite 4 true");
        ctx.waitTicks(2600);
        server(sp, s -> {
            ServerPlayer p = player(s);
            int n = p.level().getEntitiesOfClass(com.heliosphere.planets.tech.TechMob.class, p.getBoundingBox().inflate(120)).size();
            report.add("[info] machines deployed near the player after 2 encounter rounds on day 40: " + n);
        });
        shot(ctx, "spawn_day40");
        cmd(sp, "gamemode creative @a");
        cmd(sp, "kill @e[type=!player]");
    }

    /** Every armour set on an armour stand. */
    private void armor(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = p.level();
            int bx = p.getBlockX(), bz = p.getBlockZ();
            int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz + 4);
            for (int dx = -11; dx <= 11; dx++) for (int dz = -2; dz <= 7; dz++) {      // a clean stage in front of the player
                l.setBlock(new net.minecraft.core.BlockPos(bx + dx, gy - 1, bz + dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int dy = 0; dy < 5; dy++) l.setBlock(new net.minecraft.core.BlockPos(bx + dx, gy + dy, bz + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            p.teleportTo(bx + 0.5, gy + 1.0, bz + 0.5);
            int i = 0;
            for (var set : com.heliosphere.planets.weapon.armor.ArmorSet.values()) {
                var stand = new net.minecraft.world.entity.decoration.ArmorStand(l, bx - 7.5 + i * 2, gy, bz + 4.5);
                stand.setYRot(180); stand.setYBodyRot(180);
                var items = com.heliosphere.planets.weapon.armor.Armory.PIECES.get(set);
                stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(items[0]));
                stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(items[1]));
                stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(items[2]));
                stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(items[3]));
                l.addFreshEntity(stand);
                i++;
            }
            p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(bx + 0.5, gy + 1.2, bz - 3.5);
        });
        look(ctx, 0, 12);
        ctx.waitTicks(30);
        shot(ctx, "armor_row");
        cmd(sp, "kill @e[type=armor_stand]");
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
    private void rocket(ClientGameTestContext ctx, TestSingleplayerContext sp, boolean quick) {
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
        java.util.function.Consumer<String> where = tag -> { server(sp, s -> {
            ServerPlayer p = player(s);
            var r = com.heliosphere.rocketry.cabin.FlightRoom.rocketOf(p);
            report.add("[info] " + tag + ": player " + p.level().dimension().identifier() + " " + String.format("%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ())
                    + " room " + com.heliosphere.rocketry.cabin.FlightRoom.inRoom(p) + " vehicle " + (p.getVehicle() == null ? "-" : p.getVehicle().getClass().getSimpleName())
                    + (r == null ? " rocket ?" : " rocket phase " + r.phase() + " " + r.level().dimension().identifier() + String.format(" y %.0f", r.getY()) + " tags " + r.getTags())
                    + " || " + com.heliosphere.rocketry.cabin.FlightRoom.debug(p) + " || " + fields(r));
        }); try { Files.writeString(Path.of("world-test-report.txt"), String.join("\n", report)); } catch (IOException ignored) {} };
        where.accept("boarded");
        ctx.runOnClient(mc -> {
            double t = mc.level.getGameTime() / 24000.0;
            var o = com.heliosphere.core.client.sky.ClientNav.observer(t);
            var e = com.heliosphere.core.celestial.OrbitMath.position(com.heliosphere.core.celestial.SolarSystem.get("earth"), t);
            report.add("[info] nav: observer-earth " + o.sub(e) + " |earth| " + e.length() + " focus " + (com.heliosphere.core.client.sky.ClientNav.focus() == null ? "-" : com.heliosphere.core.client.sky.ClientNav.focus().id)
                    + " room " + com.heliosphere.rocketry.cabin.client.FlightRoomClient.active());
        });
        look(ctx, 0, 5);
        shot(ctx, "r_room_board");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(15);
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        ctx.runOnClient(mc -> mc.options.keyJump.setDown(true));
        ctx.waitTicks(4);
        ctx.runOnClient(mc -> mc.options.keyJump.setDown(false));
        where.accept("walked");
        look(ctx, 180, 10);
        ctx.waitTicks(10);
        shot(ctx, "r_room_back");
        look(ctx, 0, 30);
        server(sp, s -> {                                   // pull the launch lever on the console, as a player would
            ServerPlayer p = player(s);
            var lever = com.heliosphere.rocketry.cabin.FlightRoom.lever(p);
            if (lever == null) { report.add("[FAIL] no lever"); return; }
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(lever), net.minecraft.core.Direction.UP, lever, false);
            var r = p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            report.add("[info] lever pulled: " + r + " state " + p.level().getBlockState(lever));
        });
        int[] times = quick ? new int[]{200, 700, 1200} : new int[]{200, 400, 600, 900, 1200, 1600};
        int last = 0;
        for (int i = 0; i < times.length; i++) {
            ctx.waitTicks(times[i] - last);
            last = times[i];
            where.accept("ascent t" + times[i]);
            if (i % 2 == 1) look(ctx, 180, 75); else look(ctx, 0, 8);     // out of the front window / down through the deck glass
            ctx.waitTicks(3);
            shot(ctx, "r_ascent_" + i);
        }
        if (quick) {
            server(sp, s -> { ServerPlayer p = player(s); var lv = com.heliosphere.rocketry.cabin.FlightRoom.lever(p);
                if (lv != null) p.teleportTo(lv.getX() + 0.5, lv.getY() - 1, lv.getZ() - 1.5); });
            ctx.runOnClient(mc -> {
                double t = mc.level.getGameTime() / 24000.0;
                var o = com.heliosphere.core.client.sky.ClientNav.observer(t);
                var e = com.heliosphere.core.celestial.OrbitMath.position(com.heliosphere.core.celestial.SolarSystem.get("earth"), t);
                report.add("[info] nav later: observer-earth " + o.sub(e) + " room " + com.heliosphere.rocketry.cabin.client.FlightRoomClient.active());
            });
            server(sp, s -> { ServerPlayer p = player(s); var lv = com.heliosphere.rocketry.cabin.FlightRoom.lever(p);
                if (lv != null) p.teleportTo(lv.getX() + 1.0, lv.getY() - 1, lv.getZ() - 3.0); });
            look(ctx, 0, 12); ctx.waitTicks(10); shot(ctx, "r_room_window_front");
            look(ctx, 0, 35); ctx.waitTicks(10); shot(ctx, "r_room_window_down");
            server(sp, s -> { ServerPlayer p = player(s); var lv = com.heliosphere.rocketry.cabin.FlightRoom.lever(p);
                if (lv != null) p.teleportTo(lv.getX() + 0.5, lv.getY() - 1, lv.getZ() - 3.6); });
            look(ctx, 180, 88); ctx.waitTicks(10); shot(ctx, "r_room_deck_glass");
            look(ctx, 90, 20); ctx.waitTicks(10); shot(ctx, "r_room_side_window");
            where.accept("end");
            return;
        }
        boolean landed = false, cruiseShot = false;
        int descentShots = 0;
        for (int i = 0; i < 70 && !landed; i++) {
            ctx.waitTicks(descentShots > 0 ? 100 : 200);
            int[] st = new int[3];
            server(sp, s -> {
                ServerPlayer p = player(s);
                var r = com.heliosphere.rocketry.cabin.FlightRoom.rocketOf(p);
                st[0] = r == null ? -1 : r.phase();
                st[1] = com.heliosphere.rocketry.cabin.FlightRoom.inRoom(p) ? 1 : 0;
                st[2] = p.level().dimension().identifier().getPath().equals("moon") ? 1 : 0;
            });
            where.accept("flight +" + i);
            if (st[0] == 5 && !cruiseShot) { shot(ctx, "r_cruise"); cruiseShot = true; }
            if (st[0] == 6 && st[1] == 1 && descentShots < 4) { look(ctx, descentShots * 90, 25); shot(ctx, "r_descent_" + descentShots++); }
            if (st[2] == 1 && st[1] == 0 && (st[0] == -1 || st[0] == 0)) { landed = true; ctx.waitTicks(40); shot(ctx, "r_landed"); }
        }
        server(sp, s -> {
            ServerPlayer p = player(s);
            var rocket = p.getVehicle();
            p.stopRiding();
            if (rocket != null) rocket.discard();
            p.teleportTo(s.overworld(), 300.5, 120, 300.5, java.util.Set.of(), 0, 0, true);
        });
        cmd(sp, "gamemode creative @a");
        ctx.waitTicks(40);
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

    /**
     * The nuke on natural ground. Every block around ground zero is recorded before the blast; afterwards any block
     * that appeared where there was air, with air under it, is debris left hanging (the old bowl).
     */
    private void nuke(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        final int R = 90, DOWN = 50, UP = 70;
        java.util.BitSet[] before = new java.util.BitSet[1];
        cmd(sp, "execute in minecraft:overworld run tp @p 1200 140 300");
        ctx.waitTicks(60);
        sp.getClientWorld().waitForChunksRender();
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            for (int cx = (x0 - R) >> 4; cx <= (x0 + R) >> 4; cx++) for (int cz = (z0 - R) >> 4; cz <= (z0 + R) >> 4; cz++) l.getChunk(cx, cz);
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            o[0] = x0; o[1] = g; o[2] = z0;
            java.util.BitSet air = new java.util.BitSet();
            int n = 0;
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) for (int dy = -DOWN; dy <= UP; dy++, n++)
                if (l.getBlockState(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz)).isAir()) air.set(n);
            before[0] = air;
            report.add("[info] nuke ground zero " + x0 + " " + g + " " + z0);
            p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(x0 - 160.5, g + 60, z0 + 0.5);
            var look = new net.minecraft.world.phys.Vec3(160, -60, 0).normalize();
            com.heliosphere.planets.defense.sam.GuidedMissile.launch(l, com.heliosphere.planets.defense.sam.MissileSpec.NUKE_ROCKET, p,
                    com.heliosphere.planets.defense.sam.GuidedMissile.Side.DEFENCE, null, p.getEyePosition().add(look.scale(2)), look, 0, 0);
        });
        cmd(sp, "effect give @a resistance infinite 4 true");
        look(ctx, -90, 20);
        ctx.waitTicks(25);
        shot(ctx, "nuke_flight");
        ctx.waitTicks(1500);
        server(sp, s -> {
            var l = s.overworld();
            int x0 = o[0], g = o[1], z0 = o[2];
            int n = 0, created = 0, hanging = 0, carved = 0;
            java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
            List<String> samples = new ArrayList<>();
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) for (int dy = -DOWN; dy <= UP; dy++, n++) {
                var pos = new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz);
                var st = l.getBlockState(pos);
                boolean wasAir = before[0].get(n);
                if (!wasAir && st.isAir()) carved++;
                if (!wasAir || st.isAir() || !st.getFluidState().isEmpty() || st.is(net.minecraft.tags.BlockTags.FIRE)) continue;
                created++;
                if (dy > -DOWN && l.getBlockState(pos.below()).isAir() && st.isSolid()) {
                    hanging++;
                    kinds.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), 1, Integer::sum);
                    if (samples.size() < 8) samples.add(dx + "," + dy + "," + dz);
                }
            }
            report.add("[info] nuke: blocks removed " + carved + ", blocks created in air " + created + ", of them hanging over air " + hanging + " " + kinds + " e.g. " + samples);
            ServerPlayer p = player(s);
            p.teleportTo(x0 - 70.5, g + 45, z0 + 0.5);
        });
        ctx.runOnClient(mc -> {                                     // clear the cloud so the ground can be seen
            mc.particleEngine.clearParticles();
            try {
                var f = com.heliosphere.core.client.fx.FxEngine.class.getDeclaredField("LIVE");
                f.setAccessible(true);
                ((java.util.List<?>) f.get(null)).clear();
            } catch (ReflectiveOperationException ignored) {}
        });
        look(ctx, -90, 30);
        ctx.waitTicks(80);
        sp.getClientWorld().waitForChunksRender();
        shot(ctx, "nuke_crater");
        look(ctx, -60, 40);
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

    /** Doomsday with a player in the Nether: warnings, the collapse, death; then portals shatter in the overworld. */
    private void collapse(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        var OBS = net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState();
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX() + 8, z0 = p.getBlockZ() + 8;
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x0, z0) + 1;
            o[0] = x0; o[1] = y; o[2] = z0;
            for (int f = 0; f < 2; f++) {                              // two obsidian frames, 4 wide and 5 tall, along X
                int fx = x0 + f * 8;
                for (int i = 0; i < 4; i++) for (int j = 0; j < 5; j++)
                    l.setBlock(new net.minecraft.core.BlockPos(fx + i, y + j, z0), (i == 0 || i == 3 || j == 0 || j == 4) ? OBS
                            : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            l.setBlock(new net.minecraft.core.BlockPos(x0 + 1, y + 1, z0), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
            report.add("[info] portal lit before the collapse: " + l.getBlockState(new net.minecraft.core.BlockPos(x0 + 1, y + 2, z0)).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL));
            // an End portal (3x3 sheet in a frame ring) further along
            int ex = x0 + 20;
            for (int i = -1; i <= 3; i++) for (int j = -1; j <= 3; j++) {
                boolean ring = (i == -1 || i == 3) != (j == -1 || j == 3);
                var pos = new net.minecraft.core.BlockPos(ex + i, y, z0 + j);
                if (ring) l.setBlock(pos, net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.EndPortalFrameBlock.HAS_EYE, true), 3);
                else if (i >= 0 && i <= 2 && j >= 0 && j <= 2) l.setBlock(pos, net.minecraft.world.level.block.Blocks.END_PORTAL.defaultBlockState(), 3);
            }
            // the asteroid is due in 70 seconds
            var story = s.getCommandStorage().get(Heliosphere.id("story"));
            story.putInt("asteroid", 1);
            story.putDouble("asteroid_due", (l.getGameTime() + 1400) / 24000.0);
            s.getCommandStorage().set(Heliosphere.id("story"), story);
            var nether = s.getLevel(net.minecraft.world.level.Level.NETHER);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                for (int yy = 101; yy <= 104; yy++) nether.setBlock(new net.minecraft.core.BlockPos(dx, yy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                nether.setBlock(new net.minecraft.core.BlockPos(dx, 100, dz), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), 3);
            }
            p.getAbilities().flying = false; p.onUpdateAbilities();
            p.teleportTo(nether, 0.5, 101, 0.5, java.util.Set.of(), 0, 0, true);
        });
        cmd(sp, "gamemode survival @a");
        ctx.waitTicks(60);
        sp.getClientWorld().waitForChunksRender();
        look(ctx, 0, 10);
        shot(ctx, "collapse_warning");
        ctx.waitTicks(1400 - 60 - 160);
        shot(ctx, "collapse_countdown");
        ctx.waitTicks(160 + 70);
        shot(ctx, "collapse_tearing");
        ctx.waitTicks(150);
        shot(ctx, "collapse_death");
        server(sp, s -> {
            ServerPlayer p = player(s);
            report.add("[info] player after the collapse: dead=" + p.isDeadOrDying() + " dim=" + p.level().dimension().identifier());
            var story = s.getCommandStorage().get(Heliosphere.id("story"));
            report.add("[info] story started=" + story.contains("impact_winter_started") + " collapse_at=" + story.contains("dimension_collapse_at"));
        });
        ctx.runOnClient(mc -> { if (mc.player != null) mc.player.respawn(); });
        ctx.waitTicks(60);
        cmd(sp, "gamemode creative @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            p.teleportTo(l, o[0] + 10.5, o[1] + 1, o[2] - 14.5, java.util.Set.of(), 0, 15, true);
            p.getAbilities().flying = true; p.onUpdateAbilities();
            // touch the old Nether portal and drop something into the End portal; light the second frame
            var pig = net.minecraft.world.entity.EntityType.PIG.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            pig.setNoAi(true); pig.setPos(o[0] + 1.5, o[1] + 1, o[2] + 0.5); l.addFreshEntity(pig);
            var pig2 = net.minecraft.world.entity.EntityType.PIG.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            pig2.setPos(o[0] + 21.5, o[1] + 1.2, o[2] + 1.5); l.addFreshEntity(pig2);
            l.setBlock(new net.minecraft.core.BlockPos(o[0] + 9, o[1] + 1, o[2]), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
        });
        ctx.waitTicks(30);
        server(sp, s -> {
            var l = s.overworld();
            int nether = 0, end = 0, crying = 0;
            for (int dx = -2; dx <= 26; dx++) for (int dy = -1; dy <= 6; dy++) for (int dz = -2; dz <= 4; dz++) {
                var st = l.getBlockState(new net.minecraft.core.BlockPos(o[0] + dx, o[1] + dy, o[2] + dz));
                if (st.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) nether++;
                if (st.is(net.minecraft.world.level.block.Blocks.END_PORTAL)) end++;
                if (st.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)) crying++;
            }
            report.add("[info] after the collapse: nether portal blocks=" + nether + " end portal blocks=" + end + " cracked obsidian=" + crying);
        });
        ctx.waitTicks(20);
        shot(ctx, "collapse_portals");
    }

    /** The air-defence battery pack powers a radar and a launcher nearby without cables, and keeps its charge when picked up. */
    private void battery(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 2400 140 2400");
        ctx.waitTicks(60);
        sp.getClientWorld().waitForChunksRender();
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0 + 10);
            for (int dx = -14; dx <= 14; dx++) for (int dz = -2; dz <= 22; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy < 8; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            var radar = new net.minecraft.core.BlockPos(x0 - 6, g, z0 + 12);
            var launcher = new net.minecraft.core.BlockPos(x0 + 6, g, z0 + 12);
            for (var e : java.util.Map.of(radar, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, launcher, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR).entrySet()) {
                var b = com.heliosphere.planets.defense.sam.SamSites.BLOCKS.get(e.getValue());
                var st = b.defaultBlockState().setValue(com.heliosphere.planets.defense.sam.SamBlock.FACING, net.minecraft.core.Direction.NORTH);
                l.setBlock(e.getKey(), st, 3);
                b.setPlacedBy(l, e.getKey(), st, p, new net.minecraft.world.item.ItemStack(b));
            }
            var bat = new net.minecraft.core.BlockPos(x0, g, z0 + 6);
            var bs = com.heliosphere.planets.defense.sam.SamSites.BATTERY.defaultBlockState();
            l.setBlock(bat, bs, 3);
            com.heliosphere.planets.defense.sam.SamSites.BATTERY.setPlacedBy(l, bat, bs, p, new net.minecraft.world.item.ItemStack(com.heliosphere.planets.defense.sam.SamSites.BATTERY));
            p.teleportTo(x0 + 0.5, g + 2.5, z0 - 3.5);
        });
        look(ctx, 0, 18);
        ctx.waitTicks(120);
        server(sp, s -> {
            var l = s.overworld();
            long r = ((com.heliosphere.core.machine.EnergyNode) l.getBlockEntity(new net.minecraft.core.BlockPos(o[0] - 6, o[1], o[2] + 12))).energy();
            long t = ((com.heliosphere.core.machine.EnergyNode) l.getBlockEntity(new net.minecraft.core.BlockPos(o[0] + 6, o[1], o[2] + 12))).energy();
            long b = ((com.heliosphere.core.machine.EnergyNode) l.getBlockEntity(new net.minecraft.core.BlockPos(o[0], o[1], o[2] + 6))).energy();
            var radarBe = (com.heliosphere.planets.defense.sam.SamBlockEntity) l.getBlockEntity(new net.minecraft.core.BlockPos(o[0] - 6, o[1], o[2] + 12));
            report.add("[info] battery after 6 s: radar " + r + " (on " + radarBe.active() + "), launcher " + t + ", battery " + b);
            ServerPlayer p = player(s);
            var bp = new net.minecraft.core.BlockPos(o[0], o[1], o[2] + 6);
            l.getBlockState(bp).useWithoutItem(l, p, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(bp), net.minecraft.core.Direction.UP, bp, false));
        });
        ctx.waitTicks(5);
        shot(ctx, "battery_field");
        look(ctx, 0, 40);
        server(sp, s -> player(s).teleportTo(o[0] + 0.5, o[1] + 1.2, o[2] + 3.6));
        ctx.waitTicks(10);
        shot(ctx, "battery_close");
        cmd(sp, "gamemode survival @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var pos = new net.minecraft.core.BlockPos(o[0], o[1], o[2] + 6);
            boolean broke = p.gameMode.destroyBlock(pos);
            var drops = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3));
            String what = drops.stream().map(e -> e.getItem().getItem() + " x" + e.getItem().getCount() + " energy "
                    + com.heliosphere.planets.defense.sam.FieldBatteryItem.stored(e.getItem())).toList().toString();
            report.add("[info] battery picked up: broke=" + broke + " drops " + what);
        });
        cmd(sp, "gamemode creative @a");
    }

    private static String fields(Object o) {
        if (o == null) return "-";
        StringBuilder b = new StringBuilder();
        for (String n : new String[]{"ticks", "failure", "failTick", "vy", "vx", "staged"}) {
            try { var f = o.getClass().getDeclaredField(n); f.setAccessible(true); b.append(n).append('=').append(f.get(o)).append(' '); }
            catch (ReflectiveOperationException e) { b.append(n).append("=? "); }
        }
        return b.toString();
    }

    /** An empty launcher next to a powered battery rearms from an ammo crate the player loads by hand. */
    private void ammo(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 2600 140 2600");
        ctx.waitTicks(60);
        sp.getClientWorld().waitForChunksRender();
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0 + 8);
            for (int dx = -10; dx <= 10; dx++) for (int dz = -2; dz <= 16; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy < 8; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            var launcher = new net.minecraft.core.BlockPos(x0 + 4, g, z0 + 10);
            var b = com.heliosphere.planets.defense.sam.SamSites.BLOCKS.get(com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR);
            var st = b.defaultBlockState().setValue(com.heliosphere.planets.defense.sam.SamBlock.FACING, net.minecraft.core.Direction.NORTH);
            l.setBlock(launcher, st, 3);
            b.setPlacedBy(l, launcher, st, p, new net.minecraft.world.item.ItemStack(b));
            var be = l.getBlockEntity(launcher);
            try { var f = be.getClass().getDeclaredField("ammo"); f.setAccessible(true); f.setInt(be, 0); } catch (ReflectiveOperationException e) { report.add("[FAIL] ammo field " + e); }
            var bat = new net.minecraft.core.BlockPos(x0 - 4, g, z0 + 6);
            l.setBlock(bat, com.heliosphere.planets.defense.sam.SamSites.BATTERY.defaultBlockState(), 3);
            com.heliosphere.planets.defense.sam.SamSites.BATTERY.setPlacedBy(l, bat, l.getBlockState(bat), p, new net.minecraft.world.item.ItemStack(com.heliosphere.planets.defense.sam.SamSites.BATTERY));
            var crate = new net.minecraft.core.BlockPos(x0, g, z0 + 5);
            l.setBlock(crate, com.heliosphere.planets.defense.sam.SamSites.CRATE.defaultBlockState(), 3);
            // the player loads it by hand: right-click with six Buk rounds and two IRIS-T rounds
            for (var spec : new Object[][]{{"missile_9m317ma", 6}, {"missile_iris_t_sl", 2}}) {
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get((String) spec[0]), (Integer) spec[1]));
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(crate), net.minecraft.core.Direction.UP, crate, false);
                var r = p.gameMode.useItemOn(p, l, p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                report.add("[info] loaded " + spec[0] + ": " + r + " hand now " + p.getMainHandItem().getCount());
            }
            p.teleportTo(x0 + 0.5, g + 2.5, z0 - 3.5);
        });
        look(ctx, 0, 22);
        ctx.waitTicks(20);
        shot(ctx, "ammo_crate_field");
        ctx.waitTicks(400);
        server(sp, s -> {
            var l = s.overworld();
            var be = (com.heliosphere.planets.defense.sam.SamBlockEntity) l.getBlockEntity(new net.minecraft.core.BlockPos(o[0] + 4, o[1], o[2] + 10));
            var crate = (com.heliosphere.planets.defense.sam.AmmoCrateBlockEntity) l.getBlockEntity(new net.minecraft.core.BlockPos(o[0], o[1], o[2] + 5));
            report.add("[info] after 20 s: launcher ammo " + be.ammo() + "/6, crate buk " + crate.count("buk") + " iris " + crate.count("iris") + " total " + crate.total());
            ServerPlayer p = player(s);
            p.teleportTo(o[0] + 0.5, o[1] + 1.4, o[2] + 2.6);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            var pos = new net.minecraft.core.BlockPos(o[0], o[1], o[2] + 5);
            l.getBlockState(pos).useWithoutItem(l, p, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        });
        look(ctx, 0, 35);
        ctx.waitTicks(10);
        shot(ctx, "ammo_crate_close");
    }

    /** A Buk battery against two drones, a zombie and a walking machine: only the drones, one missile each at a time. */
    private void airdef(ClientGameTestContext ctx, TestSingleplayerContext sp, String system) {
        int[] o = new int[3];
        java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
        int base = system.equals("buk") ? 3200 : system.equals("samp") ? 3800 : 4400;
        cmd(sp, "execute in minecraft:overworld run tp @p " + base + " 140 " + base);
        ctx.waitTicks(60);
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy < 8; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            var radarKind = system.equals("buk") ? com.heliosphere.planets.defense.sam.SamKind.BUK_TADS : system.equals("samp") ? com.heliosphere.planets.defense.sam.SamKind.SAMP_TADS : com.heliosphere.planets.defense.sam.SamKind.IRIS_TADS;
            var telKind = system.equals("buk") ? com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR : system.equals("samp") ? com.heliosphere.planets.defense.sam.SamKind.SAMP_TEL : com.heliosphere.planets.defense.sam.SamKind.IRIS_TEL;
            for (var e : java.util.Map.of(new net.minecraft.core.BlockPos(x0 - 6, g, z0), radarKind, new net.minecraft.core.BlockPos(x0 + 6, g, z0), telKind).entrySet()) {
                var b = com.heliosphere.planets.defense.sam.SamSites.BLOCKS.get(e.getValue());
                var st = b.defaultBlockState().setValue(com.heliosphere.planets.defense.sam.SamBlock.FACING, net.minecraft.core.Direction.NORTH);
                l.setBlock(e.getKey(), st, 3);
                b.setPlacedBy(l, e.getKey(), st, p, new net.minecraft.world.item.ItemStack(b));
            }
            var bat = new net.minecraft.core.BlockPos(x0, g, z0 + 6);
            l.setBlock(bat, com.heliosphere.planets.defense.sam.SamSites.BATTERY.defaultBlockState(), 3);
            com.heliosphere.planets.defense.sam.SamSites.BATTERY.setPlacedBy(l, bat, l.getBlockState(bat), p, new net.minecraft.world.item.ItemStack(com.heliosphere.planets.defense.sam.SamSites.BATTERY));
            // the targets, 60-80 blocks north: two drones in the air, a zombie and a walker on the ground
            for (String id : new String[]{"patrol_drone", "assault_drone", "security_walker"}) {
                double y = id.endsWith("drone") ? g + 45 : g + 1;
                var m = com.heliosphere.planets.tech.TechCombat.spawn(l, id, x0 + (ids.size() - 1) * 10, y, z0 - 70, null);
                if (m != null) { m.setNoAi(true); m.setPersistenceRequired(); ids.add(m.getUUID()); }
            }
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            z.setPos(x0 + 20, l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + 20, z0 - 60), z0 - 60);
            z.setNoAi(true); z.setPersistenceRequired(); l.addFreshEntity(z); ids.add(z.getUUID());
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                l.setBlock(new net.minecraft.core.BlockPos(x0 + 20 + dx, (int) z.getY() + 3, z0 - 60 + dz), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);   // daylight shade
            p.teleportTo(x0 + 0.5, g + 6, z0 + 14.5);
            p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        look(ctx, 180, 0);
        int maxPerTarget = 0, launched = 0;
        java.util.Set<java.util.UUID> missiles = new java.util.HashSet<>();
        for (int i = 0; i < 80; i++) {
            ctx.waitTicks(5);
            int[] mx = new int[1];
            server(sp, s -> {
                var l = s.overworld();
                java.util.Map<java.util.UUID, Integer> per = new java.util.HashMap<>();
                for (var m : l.getEntitiesOfClass(com.heliosphere.planets.defense.sam.GuidedMissile.class, new net.minecraft.world.phys.AABB(o[0] - 300, o[1] - 50, o[2] - 300, o[0] + 300, o[1] + 300, o[2] + 300))) {
                    missiles.add(m.getUUID());
                    if (m.target() != null) per.merge(m.target(), 1, Integer::sum);
                }
                for (int v : per.values()) mx[0] = Math.max(mx[0], v);
            });
            maxPerTarget = Math.max(maxPerTarget, mx[0]);
            if (i == 6) shot(ctx, "airdef_" + system + "_launch");
        }
        int maxPer = maxPerTarget;
        server(sp, s -> {
            var l = s.overworld();
            StringBuilder b = new StringBuilder();
            for (var id : ids) {
                var e = l.getEntity(id);
                b.append(e == null ? "gone" : e.getType().toShortString() + " " + (e.isAlive() ? "alive " + (int) ((net.minecraft.world.entity.LivingEntity) e).getHealth() : "dead")).append("; ");
            }
            report.add("[info] airdef " + system + ": missiles fired " + missiles.size() + ", most at one target at once " + maxPer + " | " + b);
        });
        shot(ctx, "airdef_" + system + "_after");
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
