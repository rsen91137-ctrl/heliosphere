package com.heliosphere.rocketry.cabin;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.env.Planets;
import com.heliosphere.rocketry.entity.RocketEntity;
import com.heliosphere.rocketry.flight.LandingSites;
import com.heliosphere.rocketry.launch.MissionControl;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Right-clicking a rocket on the pad takes you straight into its cabin: you board, the cabin is the same walkable
 * one you fly in, and the countdown waits until you start it. Shift + right-click still opens the rocket menu.
 */
public final class Boarding implements ModInitializer {
    public static final String HOLD = "heliosphere.cabin_hold";

    @Override
    public void onInitialize() {
        FlightRoom.init();
        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(Commands.literal("hsrocket")
                .then(Commands.argument("action", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String a = StringArgumentType.getString(c, "action");
                    RocketEntity rocket = p.getVehicle() instanceof RocketEntity r ? r : FlightRoom.rocketOf(p);
                    if (rocket == null) return 0;
                    if (a.equals("go")) go(rocket, p);
                    else if (a.equals("leave")) leave(rocket, p);
                    return 1;
                }))));
    }

    /** Boards the player; returns false when the rocket cannot take crew yet (the menu opens instead). */
    public static boolean board(RocketEntity rocket, ServerPlayer p) {
        if (rocket.phase() != 0 || rocket.structure().parts().stream().noneMatch(x -> "crew_capsule".equals(x.spec()))) return false;
        CelestialBody here = Planets.bodyOf(p.level());
        if (here == null) return false;
        String dest = rocket.destination();
        if (dest == null || dest.isEmpty()) dest = MissionControl.defaultDestination(here);
        if (dest == null || dest.isEmpty()) return false;
        RocketAnalyzer.Report report = MissionControl.analyze(rocket.structure(), here, dest);
        if (report.hasFail()) {
            p.displayClientMessage(Component.translatable("message.heliosphere.cabin_checks").withStyle(ChatFormatting.RED), false);
            return false;
        }
        String site = rocket.site();
        if (site == null || site.isEmpty()) {
            var sites = LandingSites.of(dest);
            site = sites.isEmpty() ? "" : sites.get(0).id();
        }
        com.heliosphere.core.progress.Achievements.grant(p, "cabin");
        rocket.addTag(HOLD);
        rocket.begin(p, here, dest, site, report, false);
        FlightRoom.enter(rocket, p);
        MutableComponent go = Component.translatable("message.heliosphere.cabin_go").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/hsrocket go")).withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.heliosphere.cabin_go_hint"))));
        MutableComponent leave = Component.translatable("message.heliosphere.cabin_leave").withStyle(s -> s.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/hsrocket leave")));
        p.displayClientMessage(Component.translatable("message.heliosphere.cabin_boarded", Component.translatable("dimension.heliosphere." + dest))
                .withStyle(ChatFormatting.AQUA).append(" ").append(go).append(" ").append(leave), false);
        return true;
    }

    static void go(RocketEntity rocket, ServerPlayer p) {
        if (!rocket.getTags().contains(HOLD)) return;
        rocket.removeTag(HOLD);
        p.displayClientMessage(Component.translatable("message.heliosphere.cabin_counting").withStyle(ChatFormatting.GOLD), false);
    }

    static void leave(RocketEntity rocket, ServerPlayer p) {
        if (!rocket.getTags().contains(HOLD)) return;
        rocket.removeTag(HOLD);
        ((RocketPhaseAccess) rocket).heliosphere$stand();
        if (FlightRoom.inRoom(p)) FlightRoom.abort(p);
        else {
            p.stopRiding();
            p.teleportTo(rocket.getX() + 2.5, rocket.getY(), rocket.getZ());
        }
        p.displayClientMessage(Component.translatable("message.heliosphere.cabin_left").withStyle(ChatFormatting.GRAY), true);
    }

    /** Implemented on RocketEntity by RocketHoldMixin. */
    public interface RocketPhaseAccess { void heliosphere$stand(); }
}
