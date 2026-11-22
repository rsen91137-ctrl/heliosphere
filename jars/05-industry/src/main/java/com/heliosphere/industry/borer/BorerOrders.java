package com.heliosphere.industry.borer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The control panel's orders (and the driver's auto and panel keys), checked on the server: the player must be
 * riding the machine or within {@link TunnelBorer#REACH} blocks of it; only the owner, a creative player or an
 * operator may pack it up; boarding takes the seat only when it is free. After every order the player gets a fresh
 * {@link BorerNet.Panel}, except when the order opened the bin (the chest takes the screen) or packed the machine up.
 */
public final class BorerOrders {
    private BorerOrders() {}

    public static void act(ServerPlayer p, BorerNet.Action a) {
        if (p.isSpectator() || !(p.level().getEntity(a.entity()) instanceof TunnelBorer b) || b.isRemoved() || !b.inReach(p)) return;
        boolean reply = true;
        switch (a.action()) {
            case BorerNet.AUTO -> b.toggleAuto();
            case BorerNet.SLOPE -> b.cycleSlope();
            case BorerNet.DISTANCE -> b.cycleDistance();
            case BorerNet.JUNK -> b.toggleSetting(TunnelBorer.DISCARD_JUNK);
            case BorerNet.LINING -> b.toggleSetting(TunnelBorer.LINING);
            case BorerNet.VEINS -> b.toggleSetting(TunnelBorer.VEINS);
            case BorerNet.LIGHTS -> b.toggleSetting(TunnelBorer.LIGHTS);
            case BorerNet.BIN -> reply = !b.openBin(p);
            case BorerNet.PACK -> {
                if (b.mayPack(p)) { b.pack(p); reply = false; }
                else p.displayClientMessage(Component.translatable("message.heliosphere.borer.not_owner").withStyle(ChatFormatting.RED), true);
            }
            case BorerNet.BOARD -> b.board(p);
            case BorerNet.PANEL -> {}
            default -> { return; }
        }
        if (reply && !b.isRemoved()) b.sendPanel(p);
    }
}
