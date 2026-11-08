package com.heliosphere.core.client.hud;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.client.screen.HelioGui;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.env.HelioComponents;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.env.SpaceSuits;
import com.heliosphere.core.world.PlanetGeometry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Suit telemetry in the top-left corner while off-world: body, gravity, temperature, oxygen, position. */
public final class EnvironmentHud {
    private EnvironmentHud() {}

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui) return;
        boolean helio = Planets.isHeliosphereDimension(mc.level);
        ItemStack ox = SpaceSuits.oxygenSource(p);
        HelioComponents.Oxygen o = ox.get(HelioComponents.OXYGEN);
        if (!helio && o == null) return;
        Font font = mc.font;
        BlockPos pos = p.blockPosition();
        CelestialBody b = Planets.bodyOf(mc.level);
        int x = 6, y = 6, w = 132;
        int lines = helio ? 4 : 1;
        HelioGui.panel(g, x - 3, y - 3, w, 12 + lines * 10 + (o != null ? 8 : 0));
        if (helio) {
            String name = b == null ? "近地轨道 · 太空" : b.nameZh;
            g.drawString(font, "§b" + name, x + 2, y + 1, HelioGui.TEXT, false);
            y += 11;
            double t = Environment.temperatureC(mc.level, pos);
            int tc = t > 60 || t < -60 ? HelioGui.BAD : HelioGui.TEXT;
            g.drawString(font, String.format("重力 %.2fg  温度 %.0f°C", Environment.gravity(mc.level), t), x + 2, y, tc, false);
            y += 10;
            boolean air = Environment.breathable(mc.level, BlockPos.containing(p.getEyePosition()));
            g.drawString(font, String.format("气压 %.3f bar  %s", Environment.pressureBar(mc.level), air ? "§a有氧" : "§c真空/无氧"), x + 2, y, HelioGui.TEXT, false);
            y += 10;
            PlanetGeometry geo = b == null ? null : PlanetGeometry.of(b);
            if (geo != null) {
                double[] ll = geo.latLon(p.getX(), p.getZ());
                g.drawString(font, String.format("%.2f°%s %.2f°%s", Math.abs(ll[0]), ll[0] >= 0 ? "N" : "S", Math.abs(ll[1]), ll[1] >= 0 ? "E" : "W"),
                        x + 2, y, HelioGui.DIM, false);
            }
            y += 10;
        }
        if (o != null) {
            float f = o.fraction();
            g.drawString(font, String.format("O₂ %d:%02d", o.amount() / 1200, (o.amount() / 20) % 60), x + 2, y, f < 0.2f ? HelioGui.BAD : HelioGui.TEXT, false);
            HelioGui.hbar(g, x + 50, y, w - 58, 7, f, f < 0.2f ? HelioGui.BAD : 0xFF4FC3F7);
        }
    }
}
