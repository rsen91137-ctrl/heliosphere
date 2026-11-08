package com.heliosphere.core.machine;

import com.heliosphere.core.Heliosphere;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** One menu shared by every machine; the layout comes from the machine's {@link MachineType}. */
public class MachineMenu extends AbstractContainerMenu {
    public record Data(BlockPos pos, String type) {
        public static final StreamCodec<io.netty.buffer.ByteBuf, Data> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Data::pos, ByteBufCodecs.STRING_UTF8, Data::type, Data::new);
    }

    public static ExtendedScreenHandlerType<MachineMenu, Data> TYPE;

    public static void init() {
        TYPE = Registry.register(BuiltInRegistries.MENU, Heliosphere.id("machine"),
                new ExtendedScreenHandlerType<>(MachineMenu::new, Data.CODEC));
    }

    public final MachineType machine;
    public final BlockPos pos;
    private final Container container;
    private final ContainerData data;

    /** Client constructor. */
    public MachineMenu(int id, Inventory inv, Data d) {
        this(id, inv, new SimpleContainer(MachineType.get(d.type()).slots()), new SimpleContainerData(10), d);
    }

    public MachineMenu(int id, Inventory inv, Container container, ContainerData data, Data d) {
        super(TYPE, id);
        this.machine = MachineType.get(d.type());
        this.pos = d.pos();
        this.container = container;
        this.data = data;
        checkContainerSize(container, machine.slots());
        for (int i = 0; i < machine.inputs; i++) {
            int[] xy = inputPos(machine, i);
            addSlot(new Slot(container, i, xy[0], xy[1]));
        }
        for (int j = 0; j < machine.outputs; j++) {
            int[] xy = outputPos(machine, j);
            addSlot(new Slot(container, machine.inputs + j, xy[0], xy[1]) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
            });
        }
        addStandardInventorySlots(inv, 8, 84);
        addDataSlots(data);
    }

    public static int[] inputPos(MachineType t, int i) {
        int cols = Math.min(3, Math.max(1, t.inputs));
        int rows = (t.inputs + cols - 1) / cols;
        int y0 = 35 - (rows - 1) * 9;
        return new int[]{26 + (i % cols) * 18 + (3 - cols) * 9, y0 + (i / cols) * 18};
    }

    public static int[] outputPos(MachineType t, int j) {
        int cols = Math.min(2, Math.max(1, t.outputs));
        int rows = (t.outputs + cols - 1) / cols;
        int y0 = 35 - (rows - 1) * 9;
        return new int[]{116 + (j % cols) * 18, y0 + (j / cols) * 18};
    }

    public int progress() { return data.get(0); }
    public int maxProgress() { return data.get(1); }
    public long energy() { return (data.get(2) & 0xFFFFL) | ((data.get(3) & 0xFFFFL) << 16); }
    public long capacity() { return (data.get(4) & 0xFFFFL) | ((data.get(5) & 0xFFFFL) << 16); }
    public int burn() { return data.get(6); }
    public int burnMax() { return data.get(7); }
    public int status() { return data.get(8); }
    public int genRate() { return data.get(9); }

    @Override
    public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = machine.slots();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (machine.inputs == 0 || !moveItemStackTo(stack, 0, machine.inputs, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
