package com.heliosphere.industry.power;

import com.heliosphere.core.Reg;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.Map;

/** Registers the extra generators and batteries (see {@link PowerKind}). */
public final class Power implements ModInitializer {
    public static final Map<PowerKind, PowerBlock> BLOCKS = new EnumMap<>(PowerKind.class);
    public static BlockEntityType<PowerBlockEntity> BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        Reg.tab("industry");
        for (PowerKind k : PowerKind.values())
            BLOCKS.put(k, Reg.blockWithItem(k.id, p -> new PowerBlock(p, k), Reg.metal(k.battery ? MapColor.COLOR_GRAY : MapColor.METAL, 5f)
                    .lightLevel(s -> s.getValue(PowerBlock.LIT) && (k == PowerKind.FISSION || k == PowerKind.GEOTHERMAL) ? 9 : 0)));
        BLOCK_ENTITY = Reg.blockEntity("power_block", PowerBlockEntity::new, BLOCKS.values().toArray(Block[]::new));
    }
}
