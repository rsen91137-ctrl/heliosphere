package com.heliosphere.core.client.screen;

import com.heliosphere.core.machine.MachineBlockEntity;
import com.heliosphere.core.machine.MachineMenu;
import com.heliosphere.core.machine.MachineRecipe;
import com.heliosphere.core.machine.MachineType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Generic machine screen with energy gauge, progress arrow, status line and a scrollable recipe list. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int RECIPE_W = 132, ROW_H = 20;
    private int scroll;

    public MachineScreen(MachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    private boolean hasRecipes() { return !menu.machine.recipes().isEmpty(); }

    @Override
    protected void init() {
        super.init();
        if (hasRecipes()) leftPos = (width - imageWidth - RECIPE_W - 4) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        HelioGui.panel(g, x, y, imageWidth, imageHeight);
        for (Slot s : menu.slots) HelioGui.slot(g, x + s.x, y + s.y);
        MachineType t = menu.machine;
        // energy gauge
        long cap = Math.max(1, menu.capacity());
        HelioGui.bar(g, x + 8, y + 17, 10, 52, menu.energy() / (float) cap, 0xFFFFC23D);
        if (t.kind == MachineType.Kind.PROCESSOR) {
            float p = menu.maxProgress() <= 0 ? 0 : menu.progress() / (float) menu.maxProgress();
            HelioGui.arrow(g, x + 86, y + 34, p);
        } else if (t.kind == MachineType.Kind.FUEL_GENERATOR || t.kind == MachineType.Kind.OXYGEN_BUBBLE) {
            float b = menu.burnMax() <= 0 ? 0 : menu.burn() / (float) menu.burnMax();
            HelioGui.hbar(g, x + 60, y + 58, 60, 6, b, 0xFFFF7A3D);
        }
        if (hasRecipes()) renderRecipePanel(g, x + imageWidth + 4, y, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, HelioGui.TEXT, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, HelioGui.DIM, false);
        MachineType t = menu.machine;
        String status = switch (menu.status()) {
            case MachineBlockEntity.STATUS_OK -> t.producesEnergy() && t.kind != MachineType.Kind.BATTERY
                    ? "§a发电中 " + menu.genRate() + " HE/t" : "§a运行中";
            case MachineBlockEntity.STATUS_NO_POWER -> "§c电力不足";
            case MachineBlockEntity.STATUS_CONDITION -> "§e工作条件不满足";
            case MachineBlockEntity.STATUS_OUTPUT_FULL -> "§e输出已满";
            default -> "§7待机";
        };
        int sw = font.width(Component.literal(status));
        g.drawString(font, Component.literal(status), imageWidth - 8 - sw, inventoryLabelY, HelioGui.TEXT, false);
    }

    private List<MachineRecipe> recipes() { return menu.machine.recipes(); }

    private void renderRecipePanel(GuiGraphics g, int px, int py, int mx, int my) {
        HelioGui.panel(g, px, py, RECIPE_W, imageHeight);
        g.drawString(font, Component.translatable("gui.heliosphere.recipes"), px + 6, py + 6, HelioGui.ACCENT, false);
        List<MachineRecipe> rs = recipes();
        int visible = (imageHeight - 22) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rs.size() - visible)));
        for (int i = 0; i < visible && i + scroll < rs.size(); i++) {
            MachineRecipe r = rs.get(i + scroll);
            int ry = py + 18 + i * ROW_H;
            g.fill(px + 4, ry, px + RECIPE_W - 4, ry + ROW_H - 2, (i & 1) == 0 ? 0x22FFFFFF : 0x11FFFFFF);
            int ix = px + 6;
            List<ItemStack> hover = new ArrayList<>();
            for (MachineRecipe.Input in : r.inputs) {
                ItemStack s = in.display();
                g.renderItem(s, ix, ry + 1);
                g.renderItemDecorations(font, s, ix, ry + 1);
                if (mx >= ix && mx < ix + 16 && my >= ry && my < ry + 17) hover.add(s);
                ix += 16;
            }
            g.drawString(font, "→", ix + 1, ry + 5, HelioGui.ACCENT, false);
            ix += 9;
            for (MachineRecipe.Output o : r.outputs) {
                g.renderItem(o.stack(), ix, ry + 1);
                g.renderItemDecorations(font, o.stack(), ix, ry + 1);
                if (mx >= ix && mx < ix + 16 && my >= ry && my < ry + 17) hover.add(o.stack());
                ix += 16;
            }
            if (!hover.isEmpty()) g.setTooltipForNextFrame(font, hover.get(0), mx, my);
        }
        if (rs.size() > visible) g.drawString(font, (scroll + 1) + "/" + (rs.size() - visible + 1), px + RECIPE_W - 30, py + 6, HelioGui.DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (hasRecipes() && mx > leftPos + imageWidth) { scroll -= (int) Math.signum(dy); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
        if (mx >= leftPos + 8 && mx < leftPos + 18 && my >= topPos + 17 && my < topPos + 69)
            g.setTooltipForNextFrame(font, Component.literal(menu.energy() + " / " + menu.capacity() + " HE"), mx, my);
    }
}
