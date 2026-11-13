package com.heliosphere.planets.weapon.ammo;

import com.heliosphere.core.Reg;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The creative ammo box: carried anywhere in the inventory, every gun reloads without ammunition or power cells,
 * as it would in creative mode. Magazines still empty and reload as usual.
 */
public final class AmmoBox {
    private AmmoBox() {}

    public static Item ITEM;
    /** The player whose reload is being handled right now while carrying a box (see the mixins). */
    public static final ThreadLocal<Player> RELOADING = new ThreadLocal<>();

    public static void init() {
        Reg.tab("weapons");
        ITEM = Reg.item("creative_ammo_box", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    public static boolean carries(Player p) {
        if (ITEM == null) return false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(ITEM)) return true;
        }
        return false;
    }
}
