package com.heliosphere.rocketry.launch;

import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.menu.RocketMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Launch console: holds refuelling canisters and the chosen destination / landing site. */
public class LaunchConsoleBlockEntity extends BaseContainerBlockEntity implements ExtendedScreenHandlerFactory<RocketMenu.Data> {
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    private String destination = "";
    private String site = "";

    public LaunchConsoleBlockEntity(BlockPos pos, BlockState state) { super(Rocketry.CONSOLE_BE, pos, state); }

    public String destination() { return destination; }
    public String site() { return site; }
    public void setDestination(String d) { destination = d; setChanged(); }
    public void setSite(String s) { site = s; setChanged(); }

    @Override protected Component getDefaultName() { return Component.translatable("block.heliosphere.launch_console"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> l) { items = l; }
    @Override public int getContainerSize() { return 9; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new RocketMenu(id, inv, this, new RocketMenu.Data(worldPosition, -1));
    }

    @Override
    public RocketMenu.Data getScreenOpeningData(ServerPlayer p) { return new RocketMenu.Data(worldPosition, -1); }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        destination = in.getStringOr("destination", "");
        site = in.getStringOr("site", "");
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putString("destination", destination);
        out.putString("site", site);
    }
}
