package com.heliosphere.core.env;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Reads the suit rating from worn armour (pieces carry a {@link HelioComponents.SuitRating}). */
public final class SpaceSuits {
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public record Protection(boolean sealed, int tier, int minTempC, int maxTempC) {
        public static final Protection NONE = new Protection(false, 0, -10, 45);
    }

    private SpaceSuits() {}

    public static Protection of(LivingEntity e) {
        int tier = Integer.MAX_VALUE, min = Integer.MIN_VALUE, max = Integer.MAX_VALUE;
        boolean sealed = true;
        for (EquipmentSlot s : SLOTS) {
            ItemStack st = e.getItemBySlot(s);
            HelioComponents.SuitRating r = st.get(HelioComponents.SUIT);
            if (r == null) return Protection.NONE;
            tier = Math.min(tier, r.tier());
            min = Math.max(min, r.minTempC());
            max = Math.min(max, r.maxTempC());
            sealed &= r.sealed();
        }
        return new Protection(sealed, tier, min, max);
    }

    public static ItemStack oxygenSource(LivingEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        return chest.has(HelioComponents.OXYGEN) ? chest : ItemStack.EMPTY;
    }
}
