package com.heliosphere.core.machine;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.OrbitMath;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Environment;
import com.heliosphere.core.env.HelioComponents;
import com.heliosphere.core.env.Planets;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/** Generic machine: processes {@link MachineRecipe}s, generates or stores energy, or projects an oxygen bubble. */
public class MachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, EnergyNode,
        ExtendedScreenHandlerFactory<MachineMenu.Data> {
    public static final int STATUS_OK = 0, STATUS_NO_POWER = 1, STATUS_CONDITION = 2, STATUS_OUTPUT_FULL = 3, STATUS_IDLE = 4;

    private NonNullList<ItemStack> items;
    private long energy;
    private int progress, maxProgress, burn, burnMax, status = STATUS_IDLE, genRate;
    private String conditionKey = "";
    private int recipeIndex = -1;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            MachineType t = type();
            long cap = t == null ? 0 : t.capacity;
            return switch (i) {
                case 0 -> progress; case 1 -> maxProgress;
                case 2 -> (int) (energy & 0xFFFF); case 3 -> (int) ((energy >> 16) & 0xFFFF);
                case 4 -> (int) (cap & 0xFFFF); case 5 -> (int) ((cap >> 16) & 0xFFFF);
                case 6 -> burn; case 7 -> burnMax; case 8 -> status; case 9 -> genRate;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 10; }
    };

    public MachineBlockEntity(BlockEntityType<MachineBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        MachineType t = type();
        items = NonNullList.withSize(t == null ? 0 : t.slots(), ItemStack.EMPTY);
    }

    public MachineType type() {
        return getBlockState().getBlock() instanceof MachineBlock mb ? mb.type() : null;
    }

    // ------------------------------------------------------------------ ticking

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        MachineType t = type();
        if (t == null) return;
        boolean active = switch (t.kind) {
            case PROCESSOR -> tickProcessor(level, t);
            case FUEL_GENERATOR -> tickFuel(level, t);
            case SOLAR -> tickSolar(level, pos, t);
            case BATTERY -> energy > 0;
            case OXYGEN_BUBBLE -> tickBubble(level, pos, t);
        };
        if (t.producesEnergy() && level.getGameTime() % 10 == (pos.asLong() & 7)) {
            EnergyNet.push(level, pos, this, t.kind == MachineType.Kind.BATTERY ? 2_000 : Math.max(200, t.generation * 10));
        }
        if (state.getValue(MachineBlock.LIT) != active) level.setBlock(pos, state.setValue(MachineBlock.LIT, active), 3);
    }

    private boolean tickProcessor(Level level, MachineType t) {
        String cond = t.condition.check(level, worldPosition);
        if (cond != null) { status = STATUS_CONDITION; conditionKey = cond; progress = 0; return false; }
        MachineRecipe r = findRecipe(t);
        if (r == null) { status = STATUS_IDLE; progress = 0; recipeIndex = -1; return false; }
        if (r.cond != null) {
            String rc = r.cond.check(level, worldPosition);
            if (rc != null) { status = STATUS_CONDITION; conditionKey = rc; progress = 0; return false; }
        }
        int idx = t.recipes().indexOf(r);
        if (idx != recipeIndex) { recipeIndex = idx; progress = 0; }
        if (!outputsFit(t, r)) { status = STATUS_OUTPUT_FULL; return false; }
        if (energy < r.power) { status = STATUS_NO_POWER; return false; }
        energy -= r.power;
        status = STATUS_OK;
        maxProgress = r.ticks;
        if (++progress >= r.ticks) {
            progress = 0;
            craft(level, t, r);
        }
        setChanged();
        return true;
    }

    private boolean tickFuel(Level level, MachineType t) {
        String cond = t.condition.check(level, worldPosition);
        if (cond != null) { status = STATUS_CONDITION; conditionKey = cond; genRate = 0; return false; }
        if (burn <= 0) {
            ItemStack fuel = items.get(0);
            int dur = fuel.isEmpty() ? 0 : ((ServerLevel) level).fuelValues().burnDuration(fuel);
            if (dur > 0 && energy < t.capacity) {
                burn = burnMax = dur;
                ItemStack rem = fuel.getItem().getCraftingRemainder();
                fuel.shrink(1);
                if (fuel.isEmpty() && !rem.isEmpty()) items.set(0, rem.copy());
                setChanged();
            }
        }
        if (burn > 0) {
            burn--;
            genRate = t.generation;
            energy = Math.min(t.capacity, energy + t.generation);
            status = STATUS_OK;
            return true;
        }
        genRate = 0;
        status = STATUS_IDLE;
        return false;
    }

    private boolean tickSolar(Level level, BlockPos pos, MachineType t) {
        double light = 0;
        if (level.canSeeSky(pos.above())) {
            light = Environment.daylight(level, pos);
            CelestialBody b = Planets.bodyOf(level);
            double au = 1.0;
            if (b != null) {
                double days = level.getGameTime() / 24000.0;
                au = OrbitMath.position(b, days).length() / CelestialBody.AU_KM;
            }
            light *= 1.0 / (au * au);                     // inverse-square sunlight
            if (b == SolarSystem.EARTH) light *= 0.75;    // atmospheric losses
            if (b != null && b.pressureBar > 0.001 && b != SolarSystem.EARTH) light *= 0.8;
        }
        genRate = (int) Math.round(t.generation * light);
        energy = Math.min(t.capacity, energy + genRate);
        status = genRate > 0 ? STATUS_OK : STATUS_IDLE;
        return genRate > 0;
    }

    private boolean tickBubble(Level level, BlockPos pos, MachineType t) {
        // Consumes breathing-oxygen canisters (slot 0) and power to hold a sealed bubble
        boolean on = energy >= 10 && (burn > 0 || consumeCanister());
        if (on) {
            energy -= 10;
            if (--burn < 0) burn = 0;
            Environment.addBubble(level, pos, 12);
            status = STATUS_OK;
        } else {
            Environment.removeBubble(level, pos);
            status = energy < 10 ? STATUS_NO_POWER : STATUS_IDLE;
        }
        return on;
    }

    private boolean consumeCanister() {
        ItemStack s = items.get(0);
        if (s.isEmpty() || !s.getItem().builtInRegistryHolder().key().identifier().getPath().equals("canister_oxygen")) return false;
        s.shrink(1);
        burn = burnMax = 20 * 60 * 5; // one canister keeps a 12 m bubble for 5 minutes
        setChanged();
        return true;
    }

    @Override
    public void setRemoved() {
        if (level != null) Environment.removeBubble(level, worldPosition);
        super.setRemoved();
    }

    private MachineRecipe findRecipe(MachineType t) {
        for (MachineRecipe r : t.recipes()) if (matches(r, t)) return r;
        return null;
    }

    private boolean matches(MachineRecipe r, MachineType t) {
        int[] avail = new int[t.inputs];
        for (int i = 0; i < t.inputs; i++) avail[i] = items.get(i).getCount();
        for (MachineRecipe.Input in : r.inputs) {
            int need = in.count();
            for (int i = 0; i < t.inputs && need > 0; i++) {
                if (in.test(items.get(i))) { int take = Math.min(need, avail[i]); avail[i] -= take; need -= take; }
            }
            if (need > 0) return false;
        }
        return true;
    }

    private boolean outputsFit(MachineType t, MachineRecipe r) {
        List<ItemStack> sim = new ArrayList<>();
        for (int i = 0; i < t.outputs; i++) sim.add(items.get(t.inputs + i).copy());
        for (MachineRecipe.Output o : r.outputs) {
            ItemStack s = o.stack().copy();
            boolean placed = false;
            for (int i = 0; i < sim.size(); i++) {
                ItemStack slot = sim.get(i);
                if (slot.isEmpty()) { sim.set(i, s.copy()); placed = true; break; }
                if (ItemStack.isSameItemSameComponents(slot, s) && slot.getCount() + s.getCount() <= slot.getMaxStackSize()) {
                    slot.grow(s.getCount()); placed = true; break;
                }
            }
            if (!placed) return false;
        }
        return true;
    }

    private void craft(Level level, MachineType t, MachineRecipe r) {
        RandomSource rand = level.getRandom();
        ItemStack firstConsumed = ItemStack.EMPTY;
        for (MachineRecipe.Input in : r.inputs) {
            int need = in.count();
            for (int i = 0; i < t.inputs && need > 0; i++) {
                ItemStack s = items.get(i);
                if (in.test(s)) {
                    if (firstConsumed.isEmpty()) firstConsumed = s.copyWithCount(1);
                    int take = Math.min(need, s.getCount());
                    ItemStack rem = s.getItem().getCraftingRemainder();
                    s.shrink(take);
                    need -= take;
                    if (s.isEmpty() && !rem.isEmpty()) items.set(i, rem.copyWithCount(take));
                }
            }
        }
        if (r.failChance > 0 && rand.nextFloat() < effectiveFail(r, firstConsumed)) {
            level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, 2.0f, Level.ExplosionInteraction.NONE);
            for (ServerPlayer p : ((ServerLevel) level).players())
                if (p.distanceToSqr(worldPosition.getCenter()) < 400)
                    p.displayClientMessage(Component.translatable("message.heliosphere.test_failed"), false);
            return;
        }
        for (MachineRecipe.Output o : r.outputs) {
            if (o.chance() < 1 && rand.nextFloat() > o.chance()) continue;
            ItemStack out = o.stack().copy();
            if (r.keepComponents && !firstConsumed.isEmpty()) out.applyComponents(firstConsumed.getComponentsPatch());
            if (r.precision) out.set(HelioComponents.QUALITY, new HelioComponents.Quality(0.75f + rand.nextFloat() * 0.25f, false));
            if (r.markTested) {
                HelioComponents.Quality q = out.get(HelioComponents.QUALITY);
                out.set(HelioComponents.QUALITY, new HelioComponents.Quality(q == null ? 0.85f : q.tolerance(), true));
            }
            insertOutput(t, out);
        }
    }

    /** Engines machined to a tighter tolerance are less likely to blow up on the stand. */
    private static float effectiveFail(MachineRecipe r, ItemStack part) {
        HelioComponents.Quality q = part.get(HelioComponents.QUALITY);
        float tol = q == null ? 0.8f : q.tolerance();
        return r.failChance * (1.6f - tol);
    }

    private void insertOutput(MachineType t, ItemStack s) {
        for (int i = t.inputs; i < t.slots() && !s.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) { items.set(i, s.copy()); s.setCount(0); }
            else if (ItemStack.isSameItemSameComponents(slot, s)) {
                int move = Math.min(s.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(move); s.shrink(move);
            }
        }
    }

    // ------------------------------------------------------------------ energy

    @Override public long energy() { return energy; }
    @Override public long capacity() { MachineType t = type(); return t == null ? 0 : t.capacity; }
    @Override public void setEnergy(long e) { energy = Math.max(0, Math.min(capacity(), e)); setChanged(); }
    @Override public boolean canReceive() { MachineType t = type(); return t != null && t.acceptsEnergy(); }
    @Override public boolean canExtract() { MachineType t = type(); return t != null && t.producesEnergy(); }

    public String conditionKey() { return conditionKey; }

    // ------------------------------------------------------------------ container plumbing

    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items = list; }
    @Override public int getContainerSize() { return items.size(); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new MachineMenu(id, inv, this, data, new MachineMenu.Data(worldPosition, type().id));
    }

    @Override
    public MachineMenu.Data getScreenOpeningData(ServerPlayer player) { return new MachineMenu.Data(worldPosition, type().id); }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] s = new int[items.size()];
        for (int i = 0; i < s.length; i++) s[i] = i;
        return s;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) { MachineType t = type(); return t != null && slot < t.inputs; }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) { MachineType t = type(); return t != null && slot >= t.inputs; }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        MachineType t = type();
        items = NonNullList.withSize(t == null ? items.size() : t.slots(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        energy = in.getLongOr("energy", 0);
        progress = in.getIntOr("progress", 0);
        burn = in.getIntOr("burn", 0);
        burnMax = in.getIntOr("burn_max", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putLong("energy", energy);
        out.putInt("progress", progress);
        out.putInt("burn", burn);
        out.putInt("burn_max", burnMax);
    }
}
