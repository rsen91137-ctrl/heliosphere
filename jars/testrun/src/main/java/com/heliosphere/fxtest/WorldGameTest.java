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
                    case "ride" -> ride(ctx, sp);
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
                    case "operate" -> operate(ctx, sp);
                    case "airdef_samp" -> airdef(ctx, sp, "samp");
                    case "airdef_iris" -> airdef(ctx, sp, "iris");
                    case "power" -> power(ctx, sp);
                    case "skins" -> skins(ctx, sp);
                    case "jetpack" -> jetpack(ctx, sp);
                    case "dump" -> dump(ctx, sp);
                    case "pointdef" -> pointDefence(ctx, sp);
                    case "bvr" -> bvr(ctx, sp);
                    case "crew" -> crew(ctx, sp);
                    case "missilecam" -> missileCam(ctx, sp);
                    case "prefabs" -> prefabs(ctx, sp);
                    case "underground" -> underground(ctx, sp);
                    case "portable" -> portable(ctx, sp);
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
        String only = System.getProperty("heliosphere.test.icons", "");
        for (var r : com.heliosphere.planets.tech.TechCombat.ROLES) if (only.isEmpty() || java.util.Arrays.asList(only.split("\\+")).contains(r.id())) ids.add(r.id());
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
            ctx.runOnClient(mc -> report.add("[info] deck: darken " + com.heliosphere.rocketry.cabin.client.DeckView.skyDarken() + " onDeck " + com.heliosphere.rocketry.cabin.client.DeckView.onDeck()
                    + " dim " + mc.level.dimension().identifier() + " sky live " + com.heliosphere.core.client.sky.SkyHooks.live()));
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
                    sam((net.minecraft.server.level.ServerLevel) l, com.heliosphere.planets.defense.sam.SamKind.byId(batteries[i][j]), pos, p, null);
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

    /** A difficulty-9 fortress shoots down a tactical nuke and a guided missile fired at it from 350 blocks away. */
    private void pointDefence(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 9000 240 9000");
        cmd(sp, "effect give @a resistance infinite 4 true");
        ctx.waitTicks(60);
        server(sp, s -> {
            ServerPlayer p = player(s);
            o[0] = p.getBlockX() + 60; o[2] = p.getBlockZ();
            var l = s.overworld();
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            for (int dx = -70; dx <= 70; dx++) for (int dz = -70; dz <= 70; dz++)        // open ground up in the air: no sea, no hills
                l.setBlock(new net.minecraft.core.BlockPos(o[0] + dx, 230, o[2] + dz), stone, 2);
            com.heliosphere.hostiles.fortress.Fortresses.forceAt(l, o[0], o[2], 9);
            p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        ctx.waitTicks(400);                                                        // built over a few seconds
        java.util.List<net.minecraft.core.BlockPos> aa = new java.util.ArrayList<>();
        server(sp, s -> {
            var l = s.overworld();
            o[1] = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o[0], o[2]);
            o[1] = 231;
            for (var q : net.minecraft.core.BlockPos.betweenClosed(o[0] - 50, o[1] - 5, o[2] - 50, o[0] + 50, o[1] + 70, o[2] + 50))
                if (l.getBlockState(q).is(com.heliosphere.hostiles.fortress.Fortresses.POINT_DEFENCE)) aa.add(q.immutable());
            report.add("[info] point defence launchers in the difficulty-9 fortress: " + aa.size() + " at " + aa + ", keep top y " + o[1]);
            ServerPlayer p = player(s);
            p.teleportTo(o[0] + 380.5, o[1] + 70, o[2] + 0.5);                 // well away: the fortress is not kept loaded by the player
            p.setYRot(90); p.setXRot(4);
        });
        look(ctx, 90, 4);
        ctx.waitTicks(40);
        int[] seen = new int[2];
        java.util.UUID[] nuke = new java.util.UUID[2];
        server(sp, s -> {
            var l = s.overworld();
            ServerPlayer p = player(s);
            net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(o[0] + 340, o[1] + 40, o[2] + 0.5);
            net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(o[0] + 0.5, o[1] + 4, o[2] + 0.5);
            var n = com.heliosphere.planets.defense.sam.GuidedMissile.launch(l, com.heliosphere.planets.defense.sam.MissileSpec.NUKE_ROCKET, p,
                    com.heliosphere.planets.defense.sam.GuidedMissile.Side.DEFENCE, null, from, at.subtract(from), 0f, 0);
            nuke[0] = n.getUUID();
            var dummy = net.minecraft.world.entity.EntityType.ZOMBIE.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            dummy.setPos(o[0] + 8, o[1] + 1, o[2] + 8); dummy.setNoAi(true); dummy.setPersistenceRequired(); l.addFreshEntity(dummy);
            var m = com.heliosphere.planets.defense.sam.GuidedMissile.launch(l, com.heliosphere.planets.defense.sam.MissileSpec.PLAYER, p,
                    com.heliosphere.planets.defense.sam.GuidedMissile.Side.DEFENCE, dummy, from.add(0, -10, 6), new net.minecraft.world.phys.Vec3(-1, 0.1, 0), 40f, 0);
            nuke[1] = m.getUUID();
        });
        int before = com.heliosphere.planets.defense.sam.GuidedMissile.shotDownCount;
        com.heliosphere.planets.defense.sam.GuidedMissile.closestMiss = Double.MAX_VALUE;
        boolean[] gone = new boolean[1];
        boolean shotTaken = false;
        for (int i = 0; i < 300 && !gone[0]; i++) {                              // until both are gone (the server may lag behind)
            ctx.waitTicks(2);
            server(sp, s -> {
                var l = s.overworld();
                var up = l.getEntitiesOfClass(com.heliosphere.planets.defense.sam.GuidedMissile.class, new net.minecraft.world.phys.AABB(o[0] - 500, o[1] - 50, o[2] - 500, o[0] + 500, o[1] + 400, o[2] + 500),
                        x -> x.spec() == com.heliosphere.planets.defense.sam.MissileSpec.INTERCEPTOR).size();
                seen[1] = up;
                seen[0] = Math.max(seen[0], up);
                gone[0] = l.getEntity(nuke[0]) == null && l.getEntity(nuke[1]) == null;
            });
            if (!shotTaken && seen[1] > 0) { shot(ctx, "pointdef_intercept"); shotTaken = true; }
        }
        server(sp, s -> {
            var l = s.overworld();
            var keep = l.getBlockState(new net.minecraft.core.BlockPos(o[0], o[1] - 1, o[2]));
            report.add("[info] point defence: shot down " + (com.heliosphere.planets.defense.sam.GuidedMissile.shotDownCount - before) + " of 2, closest miss "
                    + String.format("%.1f", com.heliosphere.planets.defense.sam.GuidedMissile.closestMiss) + ", interceptors in the air at once " + seen[0] + ", nuke still flying " + (l.getEntity(nuke[0]) != null)
                    + ", guided missile still flying " + (l.getEntity(nuke[1]) != null) + ", fortress centre block " + keep.getBlock());
        });
        shot(ctx, "pointdef_after");
    }

    /** Every machine recipe and every item of the mod, as the running game has them (for the guide's encyclopedia). */
    private void dump(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        server(sp, s -> {
            com.heliosphere.core.machine.MachineType.ensureLoaded();
            StringBuilder b = new StringBuilder("{\"machines\": [");
            boolean first = true;
            for (var t : com.heliosphere.core.machine.MachineType.all()) {
                for (var r : t.recipes()) {
                    if (!first) b.append(",\n");
                    first = false;
                    b.append("{\"m\": \"heliosphere:").append(t.id).append("\", \"i\": [");
                    for (int k = 0; k < r.inputs.size(); k++) {
                        var in = r.inputs.get(k);
                        String id = in.item() != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(in.item()).toString() : "#" + in.tag().location();
                        b.append(k > 0 ? ", " : "").append("[\"").append(id).append("\", ").append(in.count()).append("]");
                    }
                    b.append("], \"o\": [");
                    for (int k = 0; k < r.outputs.size(); k++) {
                        var o = r.outputs.get(k);
                        try {
                            var f = o.getClass().getDeclaredField("stack"); f.setAccessible(true);
                            var st = (net.minecraft.world.item.ItemStack) f.get(o);
                            var c = o.getClass().getDeclaredField("chance"); c.setAccessible(true);
                            b.append(k > 0 ? ", " : "").append("[\"").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem())).append("\", ").append(st.getCount()).append(", ").append(c.getFloat(o)).append("]");
                        } catch (ReflectiveOperationException e) { b.append("[]"); }
                    }
                    b.append("], \"t\": ").append(r.ticks / 20.0).append(", \"p\": ").append(r.power).append("}");
                }
            }
            b.append("],\n\"items\": [");
            first = true;
            for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
                if (!id.getNamespace().equals("heliosphere")) continue;
                b.append(first ? "" : ", ").append("\"").append(id).append("\"");
                first = false;
            }
            b.append("]}");
            try { Files.writeString(Path.of("game-recipes.json"), b.toString()); } catch (IOException e) { report.add("[FAIL] dump " + e); }
            report.add("[info] dumped machine recipes and items");
        });
    }

    /** Each jetpack, jump held for two seconds: on Earth the standard and heavy packs climb, the low-g pack cannot; on the Moon it can. */
    private void jetpack(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "gamemode survival @a");
        cmd(sp, "effect give @a resistance infinite 4 true");
        String[][] runs = {{"jetpack", "minecraft:overworld"}, {"heavy_jetpack", "minecraft:overworld"}, {"rcs_pack", "minecraft:overworld"}, {"rcs_pack", "heliosphere:moon"}, {"adaptive_jetpack", "heliosphere:moon"}};
        for (String[] run : runs) {
            cmd(sp, "execute in " + run[1] + " run tp @p 7600 200 7600");
            ctx.waitTicks(40);
            double[] y0 = new double[1];
            server(sp, s -> {
                ServerPlayer p = player(s);
                var l = p.level();
                int g = Math.max(170, l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ()) + 3);   // a stone platform in the open air (not the sea)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    l.setBlock(new net.minecraft.core.BlockPos(p.getBlockX() + dx, g - 1, p.getBlockZ() + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                p.teleportTo(p.getBlockX() + 0.5, g + 0.1, p.getBlockZ() + 0.5);
                p.getAbilities().flying = false; p.onUpdateAbilities();
                var pack = new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get(run[0]));
                com.heliosphere.planets.gear.JetpackItem.setFuel(pack, com.heliosphere.planets.gear.JetpackItem.TANK);
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, pack);
                y0[0] = g;
            });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.options.keyJump.setDown(true));
            ctx.waitTicks(40);
            if (run[0].equals("jetpack")) { ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK)); ctx.waitTicks(2); shot(ctx, "jetpack_flight"); ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON)); }
            server(sp, s -> {
                ServerPlayer p = player(s);
                report.add("[info] " + run[0] + " in " + run[1] + " (g " + String.format("%.2f", com.heliosphere.core.env.Environment.gravity(p.level())) + "): risen "
                        + String.format("%.1f", p.getY() - y0[0]) + " blocks in 2 s, fuel left " + com.heliosphere.planets.gear.JetpackItem.fuel(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) + ", kicked " + !p.connection.isAcceptingMessages());
            });
            ctx.runOnClient(mc -> mc.options.keyJump.setDown(false));
            if (run[0].equals("rcs_pack") && run[1].contains("moon")) {           // sneak holds still in the air
                ctx.runOnClient(mc -> mc.options.keyShift.setDown(true));
                double[] yh = new double[1];
                server(sp, s -> yh[0] = player(s).getY());
                ctx.waitTicks(30);
                server(sp, s -> report.add("[info] rcs hold on the Moon: moved " + String.format("%.2f", player(s).getY() - yh[0]) + " blocks in 1.5 s"));
                ctx.runOnClient(mc -> mc.options.keyShift.setDown(false));
            }
            ctx.waitTicks(60);
        }
        cmd(sp, "gamemode creative @a");
        server(sp, s -> player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY));
    }

    /** The guns rebuilt after the sketches, in hand: hip, aimed down the sights for two of them, and seen from outside. */
    private void skins(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "execute in minecraft:overworld run tp @p 7000 140 7000");
        cmd(sp, "time set 6000");
        ctx.waitTicks(60);
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 30; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 8; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            p.teleportTo(x0 + 0.5, g, z0 + 0.5);
            p.getAbilities().flying = false; p.onUpdateAbilities();
        });
        look(ctx, 0, 0);
        String[] guns = {"assault_rifle", "battle_rifle", "burst_carbine", "compact_smg", "smg", "machine_pistol", "designated_marksman_rifle", "bolt_rifle", "sniper_rifle", "light_machine_gun", "rotary_cannon"};
        for (String id : guns) {
            server(sp, s -> player(s).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get(id))));
            ctx.waitTicks(12);
            shot(ctx, "skin_" + id);
        }
        for (String id : new String[]{"assault_rifle", "bolt_rifle"}) {
            server(sp, s -> player(s).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get(id))));
            ctx.runOnClient(mc -> mc.options.keyUse.setDown(true));
            ctx.waitTicks(25);
            shot(ctx, "skin_aim_" + id);
            ctx.runOnClient(mc -> mc.options.keyUse.setDown(false));
            ctx.waitTicks(5);
        }
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        for (String id : new String[]{"rotary_cannon", "battle_rifle"}) {
            server(sp, s -> player(s).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get(id))));
            ctx.waitTicks(12);
            shot(ctx, "skin_third_" + id);
        }
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
    }

    /** The grid: an RTG feeds a battery bank 300 blocks of cable away; a wind turbine up on a mast; a lithium cabinet. */
    private void power(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 6400 140 6400");
        ctx.waitTicks(60);
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0) + 3;
            o[0] = x0; o[1] = g; o[2] = z0;
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            var cable = com.heliosphere.core.Reg.getBlock("cable").defaultBlockState();
            for (int cx = (x0 >> 4) - 1; cx <= ((x0 + 302) >> 4) + 1; cx++) l.setChunkForced(cx, z0 >> 4, true);   // the whole line stays loaded
            for (int i = 0; i <= 302; i++) {                             // a long line on a stone causeway
                l.setBlock(new net.minecraft.core.BlockPos(x0 + i, g - 1, z0), stone, 2);
                if (i >= 1 && i <= 300) l.setBlock(new net.minecraft.core.BlockPos(x0 + i, g, z0), cable, 3);
            }
            var rtg = new net.minecraft.core.BlockPos(x0, g, z0);
            l.setBlock(rtg, com.heliosphere.core.Reg.getBlock("rtg").defaultBlockState(), 3);
            l.setBlock(new net.minecraft.core.BlockPos(x0 + 301, g, z0), com.heliosphere.core.Reg.getBlock("battery_bank").defaultBlockState(), 3);
            l.setBlock(new net.minecraft.core.BlockPos(x0, g, z0 + 1), com.heliosphere.core.Reg.getBlock("lithium_battery_cabinet").defaultBlockState(), 3);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get("plutonium_238"), 2));
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(rtg), net.minecraft.core.Direction.UP, rtg, false);
            report.add("[info] rtg fuelled: " + p.gameMode.useItemOn(p, l, p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit) + " hand " + p.getMainHandItem().getCount());
            // a wind turbine on a 30-block mast
            for (int dy = 0; dy < 30; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 - 6, g + dy, z0 - 4), net.minecraft.world.level.block.Blocks.IRON_BARS.defaultBlockState(), 3);
            l.setBlock(new net.minecraft.core.BlockPos(x0 - 6, g + 30, z0 - 4), com.heliosphere.core.Reg.getBlock("wind_turbine").defaultBlockState(), 3);
            p.teleportTo(x0 - 6.5, g + 28, z0 + 4.5);
            p.getAbilities().flying = true; p.onUpdateAbilities();
        });
        look(ctx, 180, -20);
        ctx.waitTicks(200);
        server(sp, s -> {
            var l = s.overworld();
            var bank = l.getBlockEntity(new net.minecraft.core.BlockPos(o[0] + 301, o[1], o[2]));
            var cab = l.getBlockEntity(new net.minecraft.core.BlockPos(o[0], o[1], o[2] + 1));
            var rtg = l.getBlockEntity(new net.minecraft.core.BlockPos(o[0], o[1], o[2]));
            var wind = l.getBlockEntity(new net.minecraft.core.BlockPos(o[0] - 6, o[1] + 30, o[2] - 4));
            report.add("[info] grid: sinks found from the RTG " + com.heliosphere.core.machine.GridNet.sinks(l, new net.minecraft.core.BlockPos(o[0], o[1], o[2])));
            report.add("[info] power after 10 s: bank 300 blocks away " + ((com.heliosphere.core.machine.EnergyNode) bank).energy()
                    + ", lithium cabinet " + ((com.heliosphere.core.machine.EnergyNode) cab).energy() + ", rtg " + ((com.heliosphere.core.machine.EnergyNode) rtg).energy()
                    + ", wind rate " + ((com.heliosphere.industry.power.PowerBlockEntity) wind).rate());
        });
        shot(ctx, "power_turbine");
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
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, radar, p, null).setEnergy(0);
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, launcher, p, null).setEnergy(0);
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
            var radarBe = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, o[0], o[1], o[2]);
            long r = radarBe.energy();
            long t = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2]).energy();
            long b = ((com.heliosphere.core.machine.EnergyNode) l.getBlockEntity(new net.minecraft.core.BlockPos(o[0], o[1], o[2] + 6))).energy();
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

    /** Sets an air-defence vehicle down facing north, fully charged; {@code ammo} null for a full magazine. */
    private static com.heliosphere.planets.defense.sam.SamVehicle sam(net.minecraft.server.level.ServerLevel l, com.heliosphere.planets.defense.sam.SamKind k, net.minecraft.core.BlockPos at, ServerPlayer owner, Integer ammo) {
        var t = new net.minecraft.nbt.CompoundTag();
        if (ammo != null) t.putInt("sam_ammo", ammo);
        var v = com.heliosphere.planets.defense.sam.SamVehicle.place(l, k, net.minecraft.world.phys.Vec3.atBottomCenterOf(at), 180f, owner == null ? null : owner.getUUID(), t, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        v.setEnergy(v.capacity());
        return v;
    }

    private static com.heliosphere.planets.defense.sam.SamVehicle samNear(net.minecraft.server.level.ServerLevel l, com.heliosphere.planets.defense.sam.SamKind k, int x, int y, int z) {
        for (var v : l.getEntitiesOfClass(com.heliosphere.planets.defense.sam.SamVehicle.class, new net.minecraft.world.phys.AABB(x - 64, y - 16, z - 64, x + 64, y + 16, z + 64)))
            if (v.kind() == k) return v;
        return null;
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
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, launcher, p, 0).setEnergy(0);
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
            var be = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2]);
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
            for (int dx = -24; dx <= 24; dx++) for (int dz = -40; dz <= 16; dz++) {           // a wide clearing: trees must not hide the targets
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy < 30; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            var radarKind = system.equals("buk") ? com.heliosphere.planets.defense.sam.SamKind.BUK_TADS : system.equals("samp") ? com.heliosphere.planets.defense.sam.SamKind.SAMP_TADS : com.heliosphere.planets.defense.sam.SamKind.IRIS_TADS;
            var telKind = system.equals("buk") ? com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR : system.equals("samp") ? com.heliosphere.planets.defense.sam.SamKind.SAMP_TEL : com.heliosphere.planets.defense.sam.SamKind.IRIS_TEL;
            sam(l, radarKind, new net.minecraft.core.BlockPos(x0 - 6, g, z0), p, null);
            sam(l, telKind, new net.minecraft.core.BlockPos(x0 + 6, g, z0), p, null);
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

    /** Manual operation: the panel, boarding, target boxes, R/F, driving off and deploying again, the wrench. */
    private void operate(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 5000 140 5000");
        ctx.waitTicks(60);
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            for (int dx = -16; dx <= 16; dx++) for (int dz = -24; dz <= 16; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy < 10; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, new net.minecraft.core.BlockPos(x0 - 8, g, z0), p, null);
            var tel = sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, new net.minecraft.core.BlockPos(x0 + 4, g, z0), p, null);
            var bat = new net.minecraft.core.BlockPos(x0 - 2, g, z0 + 8);
            l.setBlock(bat, com.heliosphere.planets.defense.sam.SamSites.BATTERY.defaultBlockState(), 3);
            com.heliosphere.planets.defense.sam.SamSites.BATTERY.setPlacedBy(l, bat, l.getBlockState(bat), p, new net.minecraft.world.item.ItemStack(com.heliosphere.planets.defense.sam.SamSites.BATTERY));
            tel.setManual(true);
            for (String id : new String[]{"patrol_drone", "assault_drone"}) {
                var m = com.heliosphere.planets.tech.TechCombat.spawn(l, id, x0 + (id.startsWith("p") ? -15 : 15), g + 40, z0 - 60, null);
                if (m != null) { m.setNoAi(true); m.setPersistenceRequired(); }
            }
            // the control panel, as a right-click would open it
            p.teleportTo(x0 + 4.5, g + 1, z0 + 6.5);
            tel.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        ctx.waitTicks(30);
        shot(ctx, "operate_panel");
        ctx.runOnClient(mc -> mc.setScreen(null));
        server(sp, s -> com.heliosphere.planets.defense.sam.SamOperation.board(player(s), samNear(s.overworld(), com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2])));
        ctx.waitTicks(40);
        look(ctx, 180, -25);
        ctx.waitTicks(10);
        shot(ctx, "operate_boxes");
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.heliosphere.planets.defense.sam.SamNet.Action(com.heliosphere.planets.defense.sam.SamNet.NEXT, 0)));
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.heliosphere.planets.defense.sam.SamNet.Action(com.heliosphere.planets.defense.sam.SamNet.FIRE, 0)));
        ctx.waitTicks(30);
        shot(ctx, "operate_fired");
        ctx.waitTicks(100);
        server(sp, s -> {
            var l = s.overworld();
            var be = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2]);
            int drones = l.getEntitiesOfClass(com.heliosphere.planets.tech.TechMob.class, new net.minecraft.world.phys.AABB(o[0] - 60, o[1], o[2] - 120, o[0] + 60, o[1] + 80, o[2] + 20)).size();
            report.add("[info] operate: manual " + be.manual() + " ammo " + be.ammo() + "/6, drones left " + drones + ", vehicle " + player(s).getVehicle());
        });
        // drive off
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(60);
        server(sp, s -> report.add("[info] driving: vehicle " + player(s).getVehicle() + " moved " + String.format("%.1f", player(s).getVehicle() == null ? -1 : player(s).getVehicle().position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(new net.minecraft.core.BlockPos(o[0] + 4, o[1], o[2]))))));
        look(ctx, 180, 20);
        shot(ctx, "operate_drive");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        ctx.waitTicks(80);
        server(sp, s -> {
            var l = s.overworld();
            var v = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2]);
            String where = v == null ? "none" : String.format("%.1f %.1f %.1f on ground %s", v.getX(), v.getY(), v.getZ(), v.onGround()) + " manual " + v.manual() + " ammo " + v.ammo() + " driving " + v.driving();
            int found = v == null ? 0 : 1;
            report.add("[info] deployed again: " + found + " at " + where + ", player vehicle " + player(s).getVehicle());
        });
        shot(ctx, "operate_deployed");
        // the wrench packs the radar up
        cmd(sp, "gamemode survival @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            p.stopRiding();
            var l = s.overworld();
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get("sam_wrench")));
            var radar = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, o[0], o[1], o[2]);
            var r = radar.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
            int n = 0; for (var st : p.getInventory().getNonEquipmentItems()) if (st.getItem() == com.heliosphere.planets.defense.sam.SamSites.VEHICLE_ITEMS.get(com.heliosphere.planets.defense.sam.SamKind.BUK_TADS)) n += st.getCount();
            report.add("[info] wrench: " + r + ", radar removed " + radar.isRemoved() + ", wrench carries " + com.heliosphere.planets.defense.sam.SamWrenchItem.stored(p.getMainHandItem()) + ", radar items in inventory " + n);
        });
        cmd(sp, "gamemode creative @a");
    }



    /** The flight aboard: boarded through the cabin, the countdown started, the ascent seen from the walkable cabin. */
    private void ride(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "execute in minecraft:overworld run tp @p 300 120 300");
        ctx.waitTicks(40);
        sp.getClientWorld().waitForChunksRender();
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = p.level();
            int x = p.getBlockX() + 12, z = p.getBlockZ();
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {           // a clear pad with a console
                l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y - 1, z + dz), Math.abs(dx) <= 2 && Math.abs(dz) <= 2
                        ? com.heliosphere.core.Reg.getBlock("launch_pad").defaultBlockState() : net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy < 60; dy++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y + dy, z + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            var console = new net.minecraft.core.BlockPos(x - 4, y, z);
            l.setBlockAndUpdate(console, com.heliosphere.core.Reg.getBlock("launch_console").defaultBlockState());
            var stack = new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get("rocket_qiming_1"));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5), net.minecraft.core.Direction.UP, new net.minecraft.core.BlockPos(x, y - 1, z), false);
            report.add("[info] place: " + stack.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit)));
            var list = l.getEntitiesOfClass(com.heliosphere.rocketry.entity.RocketEntity.class, new net.minecraft.world.phys.AABB(x - 10, y - 4, z - 10, x + 10, y + 60, z + 10));
            if (list.isEmpty()) { report.add("[FAIL] no rocket"); return; }
            var r = list.get(0);
            report.add("[info] right-click (menu): " + r.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND) + " screen menu " + p.containerMenu.getClass().getSimpleName());
            p.closeContainer();
            var ch = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(console), net.minecraft.core.Direction.UP, console, false);
            report.add("[info] console: " + l.getBlockState(console).useWithoutItem(l, p, ch) + " menu " + p.containerMenu.getClass().getSimpleName()
                    + " rocket entity menu? " + (p.containerMenu instanceof com.heliosphere.rocketry.menu.RocketMenu));
            p.closeContainer();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.teleportTo(r.getX() + 3, r.getY(), r.getZ());
            report.add("[info] boarded: " + com.heliosphere.rocketry.cabin.Boarding.board(r, p) + " vehicle " + p.getVehicle());
        });
        cmd(sp, "effect give @a resistance infinite 4 true");
        ctx.waitTicks(40);
        shot(ctx, "ride_board");
        cmd(sp, "execute as @a run hsrocket go");
        int last = 0;
        for (int t : new int[]{200, 360, 600, 900, 1300}) {
            ctx.waitTicks(t - last); last = t;
            server(sp, s -> { var p = player(s); report.add("[info] t" + 0 + ": vehicle " + p.getVehicle() + " y " + (int) p.getY()); });
            shot(ctx, "ride_" + t);
        }
    }

    // ---- strike aircraft: beyond-visual-range attacks, and air defence against them --------------------------

    private static double[] yawPitch(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        var d = to.subtract(from);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
        double pitch = -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new double[]{yaw, pitch};
    }

    private void bvr(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 6400 160 6400");
        ctx.waitTicks(60);
        cmd(sp, "gamemode creative @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy < 40; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            p.teleportTo(x0 + 0.5, g, z0 + 0.5);
        });
        // 1. a close look: both aircraft loitering near a creative player (nothing to hunt), banking round
        java.util.UUID[] show = new java.util.UUID[2];
        server(sp, s -> {
            var l = s.overworld();
            var jet = com.heliosphere.planets.tech.TechCombat.spawn(l, "strike_jet", o[0] + 26, o[1] + 14, o[2] + 4, null);
            var drone = com.heliosphere.planets.tech.TechCombat.spawn(l, "strike_drone", o[0] - 18, o[1] + 10, o[2] + 14, null);
            show[0] = jet == null ? null : jet.getUUID(); show[1] = drone == null ? null : drone.getUUID();
            report.add("[info] bvr spawn: jet " + (jet != null) + " drone " + (drone != null));
        });
        ctx.runOnClient(mc -> { mc.options.hideGui = true; mc.gui.getChat().clearMessages(false); });
        for (int k = 0; k < 2; k++) for (int n = 0; n < 2; n++) {
            final int kk = k;
            ctx.waitTicks(n == 0 ? 30 : 45);
            double[] yp = new double[2];
            float[] info = new float[3];
            server(sp, s -> {
                var e = show[kk] == null ? null : s.overworld().getEntity(show[kk]);
                if (e == null) return;
                ServerPlayer p = player(s);
                var at = e.position().add(0, e.getBbHeight() / 2, 0);
                var dir = at.subtract(p.getEyePosition()).normalize();
                var side = new net.minecraft.world.phys.Vec3(-dir.z, 0, dir.x).normalize();                // stand off to the side and a little below, sun behind
                var cam = at.add(side.scale(9 + kk * -2)).add(e.getDeltaMovement().scale(6)).add(0, -3, 0);
                p.teleportTo(cam.x, cam.y - p.getEyeHeight(), cam.z);
                at = e.position().add(e.getDeltaMovement().scale(4)).add(0, e.getBbHeight() / 2, 0);
                p.getAbilities().flying = true; p.onUpdateAbilities();
                double[] r = yawPitch(p.getEyePosition(), at);
                yp[0] = r[0]; yp[1] = r[1];
                info[0] = (float) e.getDeltaMovement().horizontalDistance(); info[1] = (float) (e.getY() - o[1]);
            });
            look(ctx, (float) yp[0], (float) yp[1]);
            ctx.waitTicks(3);
            report.add(String.format("[info] bvr look %s: speed %.2f, height %.0f", k == 0 ? "jet" : "drone", info[0], info[1]));
            shot(ctx, "bvr_" + (k == 0 ? "jet" : "drone") + "_" + n);
        }
        ctx.runOnClient(mc -> mc.options.hideGui = false);
        server(sp, s -> { for (var id : show) { var e = id == null ? null : s.overworld().getEntity(id); if (e != null) e.discard(); } });

        // 2. the strike: a survival player out in the open, the jet 250 blocks east, the drone 230 west
        java.util.List<String> blasts = new java.util.concurrent.CopyOnWriteArrayList<>();
        com.heliosphere.planets.defense.sam.GuidedMissile.BLAST_HOOKS.add((level, missile, at) -> {
            if (missile.spec() != com.heliosphere.planets.defense.sam.MissileSpec.STANDOFF || level.players().isEmpty()) return;
            blasts.add(String.format("%.1f", at.distanceTo(level.players().get(0).position())));
        });
        java.util.UUID[] craft = new java.util.UUID[2];
        cmd(sp, "gamemode survival @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            p.teleportTo(o[0] + 0.5, o[1], o[2] + 0.5);
            p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            com.heliosphere.hostiles.arsenal.StrikeCraft.launched = 0;
            var jet = com.heliosphere.planets.tech.TechCombat.spawn(l, "strike_jet", o[0] + 250, o[1] + 50, o[2], null);
            var drone = com.heliosphere.planets.tech.TechCombat.spawn(l, "strike_drone", o[0] - 230, o[1] + 60, o[2] + 40, null);
            craft[0] = jet == null ? null : jet.getUUID(); craft[1] = drone == null ? null : drone.getUUID();
        });
        java.util.List<String> launches = new java.util.ArrayList<>();
        double[] closest = {1e9, 1e9};
        boolean shotIncoming = false;
        int lastLaunched = 0;
        for (int i = 0; i < 160; i++) {
            ctx.waitTicks(10);
            int[] st = new int[2];
            double[] inc = new double[4];
            server(sp, s -> {
                var l = s.overworld();
                ServerPlayer p = player(s);
                for (int c = 0; c < 2; c++) {
                    var e = craft[c] == null ? null : l.getEntity(craft[c]);
                    if (e != null) closest[c] = Math.min(closest[c], e.distanceTo(p));
                }
                st[0] = com.heliosphere.hostiles.arsenal.StrikeCraft.launched;
                double best = 1e9;
                for (var m : l.getEntitiesOfClass(com.heliosphere.planets.defense.sam.GuidedMissile.class, p.getBoundingBox().inflate(120))) {
                    if (m.spec() != com.heliosphere.planets.defense.sam.MissileSpec.STANDOFF) continue;
                    double d = m.distanceTo(p);
                    if (d < best && d > 25) { best = d; double[] r = yawPitch(p.getEyePosition(), m.position()); inc[0] = r[0]; inc[1] = r[1]; inc[2] = d; inc[3] = 1; }
                }
            });
            if (st[0] > lastLaunched) {
                lastLaunched = st[0];
                launches.add(String.format("%.0f", com.heliosphere.hostiles.arsenal.StrikeCraft.lastLaunchRange));
            }
            if (!shotIncoming && inc[3] > 0 && inc[2] < 90) {
                look(ctx, (float) inc[0], (float) inc[1]);
                shot(ctx, "bvr_incoming");
                shotIncoming = true;
            }
            if (st[0] >= 5 && blasts.size() >= 3) break;
        }
        server(sp, s -> {
            var l = s.overworld();
            ServerPlayer p = player(s);
            StringBuilder b = new StringBuilder();
            for (int c = 0; c < 2; c++) {
                var e = craft[c] == null ? null : l.getEntity(craft[c]);
                b.append(c == 0 ? "jet " : "drone ").append(e == null ? "gone" : String.format("at %.0f blocks, %.0f up", e.distanceTo(p), e.getY() - o[1])).append(String.format(" (closest %.0f); ", closest[c]));
            }
            report.add("[info] bvr strike: launches " + com.heliosphere.hostiles.arsenal.StrikeCraft.launched + " from ranges " + launches + "; blasts at " + blasts + " blocks from the player; " + b);
        });

        // 3. under a roof: no launches
        server(sp, s -> {
            var l = s.overworld();
            for (var m : l.getEntitiesOfClass(com.heliosphere.planets.defense.sam.GuidedMissile.class, new net.minecraft.world.phys.AABB(o[0] - 400, o[1] - 60, o[2] - 400, o[0] + 400, o[1] + 300, o[2] + 400)))
                m.discard();                                                                          // missiles still in the air would blow the hut open
            int hx = o[0] + 30, hy = o[1] + 12, hz = o[2] + 30;                                   // a closed hut with a floor, clear of the craters
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 3; dy++)
                l.setBlock(new net.minecraft.core.BlockPos(hx + dx, hy + dy, hz + dz), Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 3 || dy == -1
                        ? net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            ServerPlayer p = player(s);
            p.teleportTo(hx + 0.5, hy, hz + 0.5);
            report.add("[info] hut: open above the eye " + (p.getEyeY() >= l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ())));
            com.heliosphere.hostiles.arsenal.StrikeCraft.launched = 0;
        });
        ctx.waitTicks(500);
        server(sp, s -> report.add("[info] bvr inside a closed hut: launches in 25 s " + com.heliosphere.hostiles.arsenal.StrikeCraft.launched + " last check: " + com.heliosphere.hostiles.arsenal.StrikeCraft.lastReason
                + " player at " + player(s).blockPosition().toShortString()));
        server(sp, s -> {
            var l = s.overworld();
            player(s).teleportTo(o[0] + 0.5, o[1] + 1, o[2] + 0.5);
        });

        // 4. air defence: a BUK battery beside the player; new aircraft come in, missiles are intercepted, aircraft shot down
        server(sp, s -> {
            var l = s.overworld();
            ServerPlayer p = player(s);
            for (var id : craft) { var e = id == null ? null : l.getEntity(id); if (e != null) e.discard(); }
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, new net.minecraft.core.BlockPos(o[0] - 8, o[1], o[2] - 6), p, null);
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, new net.minecraft.core.BlockPos(o[0] + 8, o[1], o[2] - 6), p, null);
            var bat = new net.minecraft.core.BlockPos(o[0], o[1], o[2] - 12);
            l.setBlock(bat, com.heliosphere.planets.defense.sam.SamSites.BATTERY.defaultBlockState(), 3);
            com.heliosphere.planets.defense.sam.SamSites.BATTERY.setPlacedBy(l, bat, l.getBlockState(bat), p, new net.minecraft.world.item.ItemStack(com.heliosphere.planets.defense.sam.SamSites.BATTERY));
            com.heliosphere.planets.defense.sam.GuidedMissile.shotDownCount = 0;
            com.heliosphere.hostiles.arsenal.StrikeCraft.launched = 0;
            var jet = com.heliosphere.planets.tech.TechCombat.spawn(l, "strike_jet", o[0] + 30, o[1] + 50, o[2] - 300, null);
            var drone = com.heliosphere.planets.tech.TechCombat.spawn(l, "strike_drone", o[0] - 40, o[1] + 60, o[2] - 280, null);
            craft[0] = jet == null ? null : jet.getUUID(); craft[1] = drone == null ? null : drone.getUUID();
        });
        java.util.Set<java.util.UUID> sams = new java.util.HashSet<>();
        boolean[] kills = new boolean[2];
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((dead, src) -> {
            for (int c = 0; c < 2; c++) if (dead.getUUID().equals(craft[c])) kills[c] = true;
        });
        int tick = 0;
        boolean shotEngage = false;
        for (; tick < 2400; tick += 10) {
            ctx.waitTicks(10);
            boolean[] dead = new boolean[2];
            server(sp, s -> {
                var l = s.overworld();
                for (int c = 0; c < 2; c++) { var e = craft[c] == null ? null : l.getEntity(craft[c]); dead[c] = e != null && !e.isAlive() || e == null && kills[c]; if (e != null && !e.isAlive()) kills[c] = true; }
                for (var m : l.getEntitiesOfClass(com.heliosphere.planets.defense.sam.GuidedMissile.class, new net.minecraft.world.phys.AABB(o[0] - 500, o[1] - 50, o[2] - 500, o[0] + 500, o[1] + 300, o[2] + 500)))
                    if (m.side() == com.heliosphere.planets.defense.sam.GuidedMissile.Side.DEFENCE) sams.add(m.getUUID());
            });
            if (!shotEngage && !sams.isEmpty()) { look(ctx, 180, -20); ctx.waitTicks(6); shot(ctx, "bvr_sam_engage"); shotEngage = true; }
            if (dead[0] && dead[1]) break;
        }
        int t = tick;
        server(sp, s -> {
            var l = s.overworld();
            ServerPlayer p = player(s);
            StringBuilder b = new StringBuilder();
            for (int c = 0; c < 2; c++) {
                var e = craft[c] == null ? null : l.getEntity(craft[c]);
                b.append(c == 0 ? "jet " : "drone ").append(kills[c] ? "shot down" : e == null ? "not loaded" : String.format("alive %.0f hp at %.0f blocks", ((net.minecraft.world.entity.LivingEntity) e).getHealth(), e.distanceTo(p))).append("; ");
            }
            report.add("[info] bvr vs air defence after " + t + " ticks: SAMs fired " + sams.size() + ", enemy missiles launched " + com.heliosphere.hostiles.arsenal.StrikeCraft.launched
                    + ", intercepted " + com.heliosphere.planets.defense.sam.GuidedMissile.shotDownCount + "; " + b);
            p.getAbilities().invulnerable = false; p.onUpdateAbilities();
        });
        shot(ctx, "bvr_after");
        cmd(sp, "gamemode creative @a");
    }

    // ---- the crew of an air-defence vehicle: engine sound, no player on the roof, the wrench carries the vehicle --

    private void crew(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 5600 140 5600");
        ctx.waitTicks(60);
        cmd(sp, "gamemode creative @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            for (int dx = -20; dx <= 20; dx++) for (int dz = -40; dz <= 20; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy < 12; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, new net.minecraft.core.BlockPos(x0, g, z0), p, 4);
            sam(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, new net.minecraft.core.BlockPos(x0 + 10, g, z0), p, null);
            p.teleportTo(x0 + 3.5, g, z0 + 6.5);
        });
        ctx.waitTicks(10);
        server(sp, s -> com.heliosphere.planets.defense.sam.SamOperation.board(player(s), samNear(s.overworld(), com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2])));
        for (int i = 0; i < 20; i++) { ctx.runOnClient(mc -> com.heliosphere.planets.defense.sam.client.SamEngineSound.stepForTest()); ctx.waitTicks(1); }
        float[] idle = new float[3];
        ctx.runOnClient(mc -> report.add("[info] engine sound file: " + mc.getResourceManager().getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath("heliosphere", "sounds/sam_engine.ogg")).isPresent()
                + ", start: " + mc.getResourceManager().getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath("heliosphere", "sounds/sam_engine_start.ogg")).isPresent()
                + ", event registered: " + (mc.getSoundManager().getSoundEvent(net.minecraft.resources.Identifier.fromNamespaceAndPath("heliosphere", "sam_engine")) != null)));
        ctx.runOnClient(mc -> { var r = com.heliosphere.planets.defense.sam.client.SamEngineSound.state(); idle[0] = r[0]; idle[1] = r[1]; idle[2] = r[2]; });
        report.add(String.format("[info] crew idle: engines running %.0f, volume %.2f, pitch %.2f", idle[0], idle[1], idle[2]));
        ctx.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.options.keyUp.setDown(true); });
        for (int i = 0; i < 50; i++) { ctx.runOnClient(mc -> com.heliosphere.planets.defense.sam.client.SamEngineSound.stepForTest()); ctx.waitTicks(1); }
        float[] run = new float[3];
        ctx.runOnClient(mc -> { var r = com.heliosphere.planets.defense.sam.client.SamEngineSound.state(); run[0] = r[0]; run[1] = r[1]; run[2] = r[2]; });
        report.add(String.format("[info] crew driving: engines running %.0f, volume %.2f, pitch %.2f", run[0], run[1], run[2]));
        look(ctx, 150, 15);
        ctx.waitTicks(4);
        shot(ctx, "crew_drive_3p");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        ctx.waitTicks(30);
        // a second player's view: stand beside it and look at the crewed vehicle (the crew is not drawn)
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        int[] vis = new int[2];
        ctx.runOnClient(mc -> {
            var p = mc.player;
            var v = p.getVehicle();
            var all = new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(), new org.joml.Matrix4f()) {
                @Override public boolean isVisible(net.minecraft.world.phys.AABB box) { return true; }
            };
            vis[0] = v == null ? -1 : (mc.getEntityRenderDispatcher().shouldRender(p, all, p.getX() + 6, p.getY(), p.getZ()) ? 1 : 0);
            vis[1] = v != null && mc.getEntityRenderDispatcher().shouldRender(v, all, p.getX() + 6, p.getY(), p.getZ()) ? 1 : 0;
        });
        report.add("[info] crew drawn while aboard: " + (vis[0] == 1 ? "yes (bad)" : vis[0] == 0 ? "no" : "not aboard") + ", vehicle drawn " + (vis[1] == 1));
        server(sp, s -> player(s).stopRiding());
        for (int i = 0; i < 60; i++) { ctx.runOnClient(mc -> com.heliosphere.planets.defense.sam.client.SamEngineSound.stepForTest()); ctx.waitTicks(1); }
        float[] off = new float[3];
        ctx.runOnClient(mc -> { var r = com.heliosphere.planets.defense.sam.client.SamEngineSound.state(); off[0] = r[0]; off[1] = r[1]; off[2] = r[2]; });
        report.add(String.format("[info] crew out: engines running %.0f", off[0]));

        // the wrench: the launcher goes into it, the radar (wrench full) into its item, the launcher rolls out again
        cmd(sp, "gamemode survival @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            var wrench = new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get("sam_wrench"));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench);
            var tel = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2]);
            int ammo = tel.ammo();
            tel.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
            var held = p.getMainHandItem();
            report.add("[info] wrench stow: launcher removed " + tel.isRemoved() + ", wrench carries " + com.heliosphere.planets.defense.sam.SamWrenchItem.stored(held)
                    + " (ammo was " + ammo + "), glint " + held.hasFoil());
            var radar = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TADS, o[0], o[1], o[2]);
            radar.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
            int n = 0; for (var st : p.getInventory().getNonEquipmentItems()) if (st.getItem() == com.heliosphere.planets.defense.sam.SamSites.VEHICLE_ITEMS.get(com.heliosphere.planets.defense.sam.SamKind.BUK_TADS)) n += st.getCount();
            report.add("[info] wrench full: radar removed " + radar.isRemoved() + ", radar items " + n);
            var at = new net.minecraft.core.BlockPos(o[0] - 6, o[1] - 1, o[2] - 12);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0, 0.5, 0), net.minecraft.core.Direction.UP, at, false);
            var r = held.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            var back = samNear(l, com.heliosphere.planets.defense.sam.SamKind.BUK_TELAR, o[0], o[1], o[2]);
            report.add("[info] wrench deploy: " + r + ", launcher " + (back == null ? "missing" : "back with ammo " + back.ammo()) + ", wrench now carries " + com.heliosphere.planets.defense.sam.SamWrenchItem.stored(p.getMainHandItem()));
            p.teleportTo(o[0] - 6.5, o[1], o[2] - 1.5);
        });
        look(ctx, 180, 10);
        ctx.waitTicks(10);
        shot(ctx, "crew_wrench_deployed");
        cmd(sp, "gamemode creative @a");
    }

    // ---- the missile camera: hold U to ride behind the last missile you fired, drag to look round it ------------

    private void missileCam(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        int[] ids = {-1, -1};
        int[] tgt = {-1};
        cmd(sp, "execute in minecraft:overworld run tp @p 7200 160 7200");
        ctx.waitTicks(60);
        cmd(sp, "gamemode creative @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = Math.max(l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0), 200) + 20;   // open sky
            o[0] = x0; o[1] = g; o[2] = z0;
            p.teleportTo(x0 + 0.5, g, z0 + 0.5);
            p.getAbilities().flying = true; p.onUpdateAbilities();
            var m = com.heliosphere.planets.tech.TechCombat.spawn(l, "assault_drone", x0 + 20, g + 25, z0 - 150, null);
            if (m != null) { m.setNoAi(true); m.setPersistenceRequired(); tgt[0] = m.getId(); }
            report.add("[info] missile cam target spawned " + (m != null));
        });
        look(ctx, 180, -12);
        ctx.waitTicks(20);
        // two missiles, half a second apart: the camera follows the second
        for (int i = 0; i < 2; i++) {
            final int n = i;
            server(sp, s -> {
                ServerPlayer p = player(s);
                var l = s.overworld();
                var target = l.getEntity(tgt[0]);
                var from = p.getEyePosition().add(0, -0.2, 0);
                var m = com.heliosphere.planets.defense.sam.GuidedMissile.launch(l, com.heliosphere.planets.defense.sam.MissileSpec.PLAYER, p,
                        com.heliosphere.planets.defense.sam.GuidedMissile.Side.DEFENCE, target, from, p.getLookAngle().add(n * 0.15, 0.08, 0), 40, 0);
                ids[n] = m.getId();
            });
            ctx.waitTicks(10);
        }
        ctx.runOnClient(mc -> com.heliosphere.planets.defense.sam.client.MissileCamera.key().setDown(true));
        ctx.waitTicks(12);
        float[] yaw0 = new float[1];
        ctx.runOnClient(mc -> {
            var cam = mc.gameRenderer.getMainCamera();
            var m = mc.level.getEntity(ids[1]);
            yaw0[0] = mc.player.getYRot();
            report.add(String.format("[info] missile cam: active %s, follows the second missile %s (first %d, second %d), camera %.1f blocks from it, player %.0f blocks away",
                    com.heliosphere.planets.defense.sam.client.MissileCamera.active(), com.heliosphere.planets.defense.sam.client.MissileCamera.tracked() == ids[1], ids[0], ids[1],
                    m == null ? -1 : cam.position().distanceTo(m.position()), m == null ? -1 : mc.player.distanceTo(m)));
        });
        shot(ctx, "missilecam_follow");
        // drag the mouse: the view swings round the missile; the player does not turn
        ctx.runOnClient(mc -> mc.player.turn(600, -150));
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> {
            var st = com.heliosphere.planets.defense.sam.client.MissileCamera.state();
            report.add(String.format("[info] missile cam drag: view %.0f deg round, %.0f deg up; player turned %.1f deg", st[1], -st[2], mc.player.getYRot() - yaw0[0]));
        });
        shot(ctx, "missilecam_drag");
        ctx.runOnClient(mc -> { com.heliosphere.planets.defense.sam.client.MissileCamera.zoom(-6); mc.player.turn(-600, 150); });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> report.add(String.format("[info] missile cam zoom: distance %.1f", com.heliosphere.planets.defense.sam.client.MissileCamera.state()[0])));
        shot(ctx, "missilecam_zoom");
        // until it goes off: the view stays over the spot, then comes home although U is still held
        boolean gone = false;
        for (int i = 0; i < 60 && !gone; i++) {
            ctx.waitTicks(5);
            boolean[] g = new boolean[1];
            ctx.runOnClient(mc -> { var m = mc.level.getEntity(ids[1]); g[0] = m == null || m.isRemoved(); });
            gone = g[0];
        }
        ctx.waitTicks(6);
        ctx.runOnClient(mc -> report.add("[info] missile cam after impact: still watching " + com.heliosphere.planets.defense.sam.client.MissileCamera.active()));
        shot(ctx, "missilecam_impact");
        ctx.waitTicks(40);
        ctx.runOnClient(mc -> {
            var cam = mc.gameRenderer.getMainCamera();
            report.add(String.format("[info] missile cam later: active %s, camera %.1f blocks from the player", com.heliosphere.planets.defense.sam.client.MissileCamera.active(), cam.position().distanceTo(mc.player.getEyePosition())));
        });
        ctx.runOnClient(mc -> com.heliosphere.planets.defense.sam.client.MissileCamera.key().setDown(false));
        server(sp, s -> {
            var l = s.overworld();
            for (var m : l.getEntitiesOfClass(com.heliosphere.planets.tech.TechMob.class, new net.minecraft.world.phys.AABB(o[0] - 60, o[1], o[2] - 220, o[0] + 60, o[1] + 120, o[2] - 80)))
                report.add("[info] missile cam target: " + m.getType().toShortString() + " alive " + m.isAlive());
        });
    }

    /** Places every pre-assembled rocket and writes the parts each one is made of (run-fx/prefab-parts.json). */
    private void prefabs(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String only = System.getProperty("heliosphere.test.prefab", "");
        String[] all = {"rocket_sounding", "rocket_qiming_1", "rocket_qiming_2", "rocket_dawn_3", "rocket_prospector", "rocket_europa_surveyor",
                "rocket_titan_explorer", "rocket_horizon_x", "rocket_phobos_clipper", "rocket_enceladus_pioneer"};
        String[] ids = only.isEmpty() ? all : only.split("\\+");
        cmd(sp, "execute in minecraft:overworld run tp @p 8000 200 8000");
        ctx.waitTicks(60);
        StringBuilder json = new StringBuilder("{");
        for (int n = 0; n < ids.length; n++) {
            final String id = ids[n];
            final int k = n;
            server(sp, s -> {
                ServerPlayer p = player(s);
                var l = p.level();
                int x = 8000 + (k % 5) * 32, z = 8000 + (k / 5) * 32, y = 200;
                for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                    for (int dy = 0; dy < 60; dy++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y + dy, z + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y - 1, z + dz), Math.abs(dx) <= 4 && Math.abs(dz) <= 4
                            ? com.heliosphere.core.Reg.getBlock("launch_pad").defaultBlockState() : net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                }
                var stack = new net.minecraft.world.item.ItemStack(com.heliosphere.core.Reg.get(id));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5), net.minecraft.core.Direction.UP, new net.minecraft.core.BlockPos(x, y - 1, z), false);
                report.add("[info] place " + id + ": " + stack.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit)));
            });
            ctx.waitTicks(20);
            server(sp, s -> {
                ServerPlayer p = player(s);
                var l = p.level();
                int x = 8000 + (k % 5) * 32, z = 8000 + (k / 5) * 32, y = 200;
                var list = l.getEntitiesOfClass(com.heliosphere.rocketry.entity.RocketEntity.class, new net.minecraft.world.phys.AABB(x - 6, y - 4, z - 6, x + 6, y + 80, z + 6));
                if (list.isEmpty()) { report.add("[FAIL] prefab " + id + " not placed: " + p.getMainHandItem()); return; }
                var r = list.get(0);
                java.util.Map<String, Integer> c = new java.util.TreeMap<>();
                for (var part : r.structure().parts()) c.merge(part.spec(), 1, Integer::sum);
                report.add("[info] prefab " + id + ": " + c);
                if (json.length() > 1) json.append(',');
                json.append('"').append(id).append("\":{");
                boolean first = true;
                for (var e : c.entrySet()) { if (!first) json.append(','); first = false; json.append('"').append(e.getKey()).append("\":").append(e.getValue()); }
                json.append('}');
                for (var e : list) e.discard();
            });
            ctx.waitTicks(5);
        }
        json.append('}');
        try { Files.writeString(Path.of("prefab-parts.json"), json.toString()); } catch (IOException e) { report.add("[FAIL] " + e); }
    }

    /** A player sealed in a room 14 blocks underground: diggers, the drill walker, a bunker buster, a siege's cave infiltrators, sabotage. */
    private void underground(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 9000 120 9000");
        ctx.waitTicks(60);
        cmd(sp, "gamemode survival @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            int y0 = g - 14;
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -1; dy <= 4; dy++) {
                boolean shell = Math.abs(dx) == 4 || Math.abs(dz) == 4 || dy == -1 || dy == 4;
                l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x0 + dx, y0 + dy, z0 + dz), shell ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                        : Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && dy <= 2 ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            p.teleportTo(x0 + 0.5, y0, z0 + 0.5);
            p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.heliosphere.hostiles.arsenal.Underground.blocksDug = 0;
            report.add("[info] underground: surface y " + g + ", room y " + y0 + ", covered " + com.heliosphere.hostiles.arsenal.Underground.covered(l, p) + ", deep " + com.heliosphere.hostiles.arsenal.Underground.deep(l, p));
        });
        // 1. an ordinary walker on the surface, 12 blocks off
        java.util.UUID[] id = new java.util.UUID[1];
        for (String kind : new String[]{"security_walker", "drill_walker"}) {
            server(sp, s -> {
                var l = s.overworld();
                ServerPlayer p = player(s);
                int x = o[0] + (kind.startsWith("s") ? 12 : -12);
                int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, o[2]);
                var m = com.heliosphere.planets.tech.TechCombat.spawn(l, kind, x + 0.5, y, o[2] + 0.5, p);
                m.setPersistenceRequired(); m.setTarget(p);
                id[0] = m.getUUID();
                com.heliosphere.hostiles.arsenal.Underground.blocksDug = 0;
            });
            StringBuilder trace = new StringBuilder();
            int limit = kind.startsWith("s") ? 1600 : 800;
            for (int t = 0; t < limit; t += 100) {
                ctx.waitTicks(100);
                server(sp, s -> {
                    var e = s.overworld().getEntity(id[0]);
                    if (e != null) { var m = (com.heliosphere.planets.tech.TechMob) e; m.setTarget(player(s)); trace.append(String.format("%.0f/%d ", e.distanceTo(player(s)), e.getBlockY())); }
                });
            }
            server(sp, s -> {
                var e = s.overworld().getEntity(id[0]);
                report.add("[info] underground " + kind + ": distance/y every 5 s " + trace + "; blocks dug " + com.heliosphere.hostiles.arsenal.Underground.blocksDug);
                if (e != null) {
                    ServerPlayer p = player(s);
                    var at = e.position().add(0, 1, 0);
                    var dir = at.subtract(p.getEyePosition());
                    p.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                    p.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
                }
            });
            ctx.waitTicks(5);
            double[] yp = new double[2];
            server(sp, s -> { yp[0] = player(s).getYRot(); yp[1] = player(s).getXRot(); });
            look(ctx, (float) yp[0], (float) yp[1]);
            shot(ctx, "underground_" + kind);
            server(sp, s -> { var e = s.overworld().getEntity(id[0]); if (e != null) e.discard(); });
        }
        // 2. a bunker buster from 60 blocks up
        java.util.List<String> blasts = new java.util.concurrent.CopyOnWriteArrayList<>();
        com.heliosphere.planets.defense.sam.GuidedMissile.BLAST_HOOKS.add((level, missile, at) -> {
            if (missile.spec() != com.heliosphere.planets.defense.sam.MissileSpec.PENETRATOR || level.players().isEmpty()) return;
            blasts.add(String.format("y %.0f (bored %d, %.1f from player)", at.y, missile.bored, at.distanceTo(level.players().get(0).position())));
        });
        server(sp, s -> {
            ServerPlayer p = player(s);
            var from = new net.minecraft.world.phys.Vec3(o[0] + 25, o[1] + 60, o[2]);
            com.heliosphere.hostiles.arsenal.Underground.penetrator(s.overworld(), null, p, from, p.position().subtract(from).normalize(), 20, 8);
        });
        for (int i = 0; i < 40 && blasts.isEmpty(); i++) ctx.waitTicks(10);
        report.add("[info] underground bunker buster: " + blasts);
        // 3. a siege on the buried home: cave infiltrators, drills, bunker busters
        cmd(sp, "execute as @p run hssiege infiltration 6");
        ctx.waitTicks(420);
        server(sp, s -> {
            var l = s.overworld();
            int under = 0, top = 0;
            StringBuilder kinds = new StringBuilder();
            for (var m : l.getEntitiesOfClass(com.heliosphere.planets.tech.TechMob.class, new net.minecraft.world.phys.AABB(o[0] - 80, o[1] - 40, o[2] - 80, o[0] + 80, o[1] + 60, o[2] + 80))) {
                if (!m.getTags().contains("heliosphere.siege")) continue;
                int h = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, m.getBlockX(), m.getBlockZ());
                if (m.getY() < h - 4) under++; else top++;
                kinds.append(m.role().id()).append(m.getY() < h - 4 ? "(cave) " : " ");
            }
            report.add("[info] underground siege: " + under + " came out of caves, " + top + " on the surface: " + kinds + " (no place found: " + com.heliosphere.hostiles.siege.Sieges.spawnFails + ")");
            report.add("[info] timeline: asteroid in " + String.format("%.1f", com.heliosphere.core.story.Story.asteroidDays(s)) + " days, evolution stage " + com.heliosphere.hostiles.arsenal.Evolution.stage()
                    + ", siege gap at stages 1/8/15: " + com.heliosphere.hostiles.arsenal.Evolution.siegeGap(1, false) / 24000f + "/" + com.heliosphere.hostiles.arsenal.Evolution.siegeGap(8, false) / 24000f + "/" + com.heliosphere.hostiles.arsenal.Evolution.siegeGap(15, false) / 24000f + " days");
        });
        ctx.waitTicks(200);
        report.add("[info] underground siege bunker busters so far: " + blasts.size());
        // 4. sabotage: battery packs on the surface by the home; a siege that cannot reach the player turns on them
        server(sp, s -> {
            var l = s.overworld();
            ServerPlayer p = player(s);
            com.heliosphere.hostiles.arsenal.Underground.sabotaged = 0;
            for (int i = 0; i < 3; i++) {
                int x = o[0] + 6 + i * 3, z = o[2] + 4;
                int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                var at = new net.minecraft.core.BlockPos(x, y, z);
                l.setBlock(at, com.heliosphere.planets.defense.sam.SamSites.BATTERY.defaultBlockState(), 3);
                com.heliosphere.planets.defense.sam.SamSites.BATTERY.setPlacedBy(l, at, l.getBlockState(at), p, new net.minecraft.world.item.ItemStack(com.heliosphere.planets.defense.sam.SamSites.BATTERY));
            }
        });
        cmd(sp, "execute as @p run hssiege assault 3");
        for (int i = 0; i < 90; i++) {
            ctx.waitTicks(20);
            int[] n = new int[1];
            server(sp, s -> n[0] = com.heliosphere.hostiles.arsenal.Underground.sabotaged);
            if (n[0] >= 1) break;
        }
        server(sp, s -> {
            report.add("[info] underground sabotage: surface machines wrecked " + com.heliosphere.hostiles.arsenal.Underground.sabotaged + ", blocks dug in total " + com.heliosphere.hostiles.arsenal.Underground.blocksDug);
            ServerPlayer p = player(s);
            p.getAbilities().invulnerable = false; p.onUpdateAbilities();
        });
        cmd(sp, "gamemode creative @a");
    }

    /** The shoulder sentry: clipped onto an iron chestplate in the inventory, worn, shoots husks, charges by a battery cabinet, taken off with its charge. */
    private void portable(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        int[] o = new int[3];
        cmd(sp, "execute in minecraft:overworld run tp @p 9600 120 9600");
        ctx.waitTicks(60);
        cmd(sp, "gamemode survival @a");
        server(sp, s -> {
            ServerPlayer p = player(s);
            var l = s.overworld();
            int x0 = p.getBlockX(), z0 = p.getBlockZ();
            int g = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            for (int dx = -14; dx <= 14; dx++) for (int dz = -8; dz <= 22; dz++) {
                l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g - 1, z0 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy < 8; dy++) l.setBlock(new net.minecraft.core.BlockPos(x0 + dx, g + dy, z0 + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            o[0] = x0; o[1] = g; o[2] = z0;
            var cab = new net.minecraft.core.BlockPos(x0 + 1, g, z0);
            l.setBlockAndUpdate(cab, com.heliosphere.core.Reg.getBlock("lithium_battery_cabinet").defaultBlockState());
            if (l.getBlockEntity(cab) instanceof com.heliosphere.core.machine.EnergyNode n) n.setEnergy(200000);
            // in the inventory: the chestplate in slot 9, the sentry on the cursor clicked onto it
            p.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
            var sentry = new net.minecraft.world.item.ItemStack(com.heliosphere.planets.defense.mk.Sentries.PORTABLE_ITEM);
            com.heliosphere.planets.defense.mk.PortableSentryItem.setEnergy(sentry, 1000);
            net.minecraft.world.inventory.Slot slot = null;
            for (var sl : p.inventoryMenu.slots) if (sl.container == p.getInventory() && sl.getContainerSlot() == 9) slot = sl;
            boolean done = sentry.overrideStackedOnOther(slot, net.minecraft.world.inventory.ClickAction.PRIMARY, p);
            var chest = p.getInventory().getItem(9);
            report.add("[info] shoulder fit: " + done + ", sentry left " + sentry.getCount() + ", chestplate carries " + com.heliosphere.planets.gear.ShoulderSentry.charge(chest) + " FE");
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest.copy());
            p.getInventory().setItem(9, net.minecraft.world.item.ItemStack.EMPTY);
            p.teleportTo(x0 + 0.5, g, z0 + 0.5);
            p.setYRot(0); p.setYBodyRot(0); p.setYHeadRot(0);
            for (int i = 0; i < 3; i++) {
                var h = net.minecraft.world.entity.EntityType.HUSK.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                h.setPos(x0 - 4 + i * 4 + 0.5, g, z0 + 12.5); h.setNoAi(true); h.setPersistenceRequired(); l.addFreshEntity(h);
            }
            com.heliosphere.planets.gear.ShoulderSentry.shots = 0;
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        look(ctx, 0, 15);
        ctx.waitTicks(12);
        shot(ctx, "shoulder_front");
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        look(ctx, 30, 10);
        ctx.waitTicks(6);
        shot(ctx, "shoulder_back");
        ctx.waitTicks(200);
        server(sp, s -> {
            var l = s.overworld();
            ServerPlayer p = player(s);
            int husks = l.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, new net.minecraft.world.phys.AABB(o[0] - 20, o[1] - 4, o[2] - 4, o[0] + 20, o[1] + 8, o[2] + 30)).size();
            var chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            report.add("[info] shoulder: shots " + com.heliosphere.planets.gear.ShoulderSentry.shots + ", husks left " + husks + "/3, charge after 10 s next to a battery cabinet " + com.heliosphere.planets.gear.ShoulderSentry.charge(chest));
            // take it off: the chestplate back in the inventory, right-clicked with an empty cursor
            p.getInventory().setItem(9, chest.copy());
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
            var inv = p.getInventory().getItem(9);
            net.minecraft.world.inventory.Slot slot = null;
            for (var sl : p.inventoryMenu.slots) if (sl.container == p.getInventory() && sl.getContainerSlot() == 9) slot = sl;
            net.minecraft.world.item.ItemStack[] cursor = {net.minecraft.world.item.ItemStack.EMPTY};
            var access = new net.minecraft.world.entity.SlotAccess() {
                @Override public net.minecraft.world.item.ItemStack get() { return cursor[0]; }
                @Override public boolean set(net.minecraft.world.item.ItemStack st) { cursor[0] = st; return true; }
            };
            boolean off = inv.overrideOtherStackedOnMe(net.minecraft.world.item.ItemStack.EMPTY, slot, net.minecraft.world.inventory.ClickAction.SECONDARY, p, access);
            report.add("[info] shoulder take off: " + off + ", cursor " + cursor[0] + " with " + com.heliosphere.planets.defense.mk.PortableSentryItem.energy(cursor[0]) + " FE, chestplate still carries " + com.heliosphere.planets.gear.ShoulderSentry.charge(inv));
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
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
