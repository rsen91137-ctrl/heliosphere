package com.heliosphere.core.command;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.world.PlanetGeometry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;

/** /heliosphere info | where | goto <body> [lat lon] */
public final class HelioCommand {
    private HelioCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("heliosphere")
                .then(Commands.literal("info").executes(HelioCommand::info))
                .then(Commands.literal("where").executes(HelioCommand::info))
                .then(Commands.literal("goto").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("body", StringArgumentType.word())
                                .suggests((c, b) -> { for (CelestialBody x : Planets.landable()) b.suggest(x.id); return b.buildFuture(); })
                                .executes(c -> go(c, Double.NaN, Double.NaN))
                                .then(Commands.argument("lat", DoubleArgumentType.doubleArg(-90, 90))
                                        .then(Commands.argument("lon", DoubleArgumentType.doubleArg(-180, 180))
                                                .executes(c -> go(c, DoubleArgumentType.getDouble(c, "lat"), DoubleArgumentType.getDouble(c, "lon"))))))));
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        CelestialBody b = Planets.bodyOf(level);
        String name = b == null ? level.dimension().identifier().toString() : b.nameZh;
        StringBuilder sb = new StringBuilder();
        sb.append("§6").append(name).append("§r  重力 ").append(String.format("%.3f g", Environment.gravity(level)))
                .append("  气压 ").append(String.format("%.4f bar", Environment.pressureBar(level)))
                .append("  温度 ").append(String.format("%.0f °C", Environment.temperatureC(level, pos)))
                .append(Environment.breathable(level, pos) ? "  §a可呼吸" : "  §c不可呼吸");
        PlanetGeometry geo = Planets.geometry(level);
        if (geo != null) {
            double[] ll = geo.latLon(pos.getX(), pos.getZ());
            sb.append("\n§7位置 ").append(fmtLat(ll[0])).append(" ").append(fmtLon(ll[1]))
                    .append(String.format("  （周长 %.0f 格，直径 %.0f 格）", geo.circumference, geo.circumference / Math.PI));
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    public static String fmtLat(double lat) { return String.format("%.2f°%s", Math.abs(lat), lat >= 0 ? "N" : "S"); }
    public static String fmtLon(double lon) { return String.format("%.2f°%s", Math.abs(lon), lon >= 0 ? "E" : "W"); }

    private static int go(CommandContext<CommandSourceStack> c, double lat, double lon) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CelestialBody body = SolarSystem.get(StringArgumentType.getString(c, "body"));
        ResourceKey<Level> key = body == null ? null : Planets.dimensionOf(body);
        ServerLevel level = key == null ? null : c.getSource().getServer().getLevel(key);
        if (level == null) { c.getSource().sendFailure(Component.literal("无法前往该天体")); return 0; }
        double x = 0.5, z = 0.5;
        PlanetGeometry geo = Planets.geometry(level);
        if (geo != null && !Double.isNaN(lat)) { x = geo.lonToX(lon); z = geo.latToZ(lat); }
        BlockPos col = BlockPos.containing(x, 0, z);
        level.getChunk(col);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ()) + 1;
        p.teleportTo(level, x, y, z, Set.<Relative>of(), p.getYRot(), p.getXRot(), true);
        return 1;
    }
}
