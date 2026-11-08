package com.heliosphere.core.block;

import com.heliosphere.core.Reg;
import com.heliosphere.core.machine.CableBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class CoreBlocks {
    public static StellarPlasmaBlock STELLAR_PLASMA;
    public static BlockEntityType<StellarPlasmaBlock.Tile> STELLAR_PLASMA_BE;
    public static CableBlock CABLE;

    private CoreBlocks() {}

    public static void init() {
        STELLAR_PLASMA = Reg.blockWithItem("stellar_plasma", StellarPlasmaBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).lightLevel(s -> 15).strength(-1f, 3_600_000f)
                        .noLootTable().pushReaction(PushReaction.BLOCK).sound(SoundType.WOOL).emissiveRendering((s, l, p) -> true)
                        .hasPostProcess((s, l, p) -> true),
                new Item.Properties().rarity(Rarity.EPIC).fireResistant());
        STELLAR_PLASMA_BE = Reg.blockEntity("stellar_plasma", StellarPlasmaBlock.Tile::new, STELLAR_PLASMA);
        CABLE = Reg.blockWithItem("cable", CableBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.WOOL).noOcclusion());
    }
}
