package com.heliosphere.industry.borer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The tunnel borer (「穿山甲」盾构采矿机): a 3×3×3 machine that bores a 3×3 tunnel ahead of it, block by block, driven
 * by a player sitting inside or on its own (auto mode), and keeps what it digs in a 54-slot collection bin.
 *
 * <p>Frame: the entity position is the centre of the bottom of its 3×3×3 cube (x, z on the middle column, y on the
 * floor). +Z ahead in the machine's own frame; {@link #facing()} is always one of the four horizontal directions.
 *
 * <p>The synchronised state below is the contract with the client (renderer, HUD, control panel): keep the
 * accessors and their meaning. Movement is decided on the server only; clients interpolate.
 *
 * <p>Server side: a step takes the blocks of the cube moved one block ahead (and one up or down on a slope) that the
 * cube does not already hold, after a cutting time that grows with their hardness; their drops and the contents of
 * any container among them go into the bin, the machine glides into the new place, seals water and lava off around
 * it, lines gaps in the tunnel wall with segments, sets a lamp into the roof now and then and mines ore veins it
 * passes. Energy comes from its store, topped up by burning fuel from the bin. The driver's keys come from
 * {@link ServerPlayer#getLastClientInput()}; the control panel's orders from {@link BorerOrders}.
 */
public class TunnelBorer extends Entity {
    // ---- synchronised state ----
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOPE = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BIN = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AUTO_LEFT = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATUS = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DUG = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORES = SynchedEntityData.defineId(TunnelBorer.class, EntityDataSerializers.INT);

    /** Flag bits ({@link #flag}). AUTO: auto mode on. BORING: the cutter head is cutting right now. MOVING: stepping or turning. */
    public static final int AUTO = 1, BORING = 2, DISCARD_JUNK = 4, LINING = 8, VEINS = 16, LIGHTS = 32, CREATIVE = 64, MOVING = 128;
    public static final int CAPACITY = 200_000;
    public static final int BIN_SIZE = 54;
    /** Lengths of an auto run, in blocks; -1 is unlimited. */
    public static final int[] DISTANCES = {16, 32, 64, 128, 256, -1};

    /** Status codes ({@link #status}); the HUD and panel show {@code status.heliosphere.borer.<STATUS_KEYS[code]>}. */
    public static final int S_IDLE = 0, S_BORING = 1, S_NO_ENERGY = 2, S_BIN_FULL = 3, S_UNBREAKABLE = 4, S_OBSTRUCTED = 5,
            S_DONE = 6, S_WORLD_LIMIT = 7, S_TURNING = 8, S_REVERSING = 9, S_NO_ROOM = 10;
    public static final String[] STATUS_KEYS = {"idle", "boring", "no_energy", "bin_full", "unbreakable", "obstructed",
            "done", "world_limit", "turning", "reversing", "no_room"};

    /** Charge of a newly built machine, in FE. */
    public static final int START_ENERGY = 20_000;
    /** Junk items the bin keeps as lining material while junk is thrown away. */
    public static final int JUNK_KEEP = 64;
    /** Ticks of the glide into the next block, and of a quarter turn. */
    public static final int GLIDE_TICKS = 6, TURN_TICKS = 8;
    /** The cutter hurts a creature in its way this much every {@link #GRIND_INTERVAL} ticks. */
    public static final float GRIND_DAMAGE = 6f;
    public static final int GRIND_INTERVAL = 10;
    /** Flag bits that are settings of the control panel: kept when the machine is packed up. */
    public static final int SETTINGS = DISCARD_JUNK | LINING | VEINS | LIGHTS;
    /** How near (from the middle of the machine) a player must be to give orders or use the bin, unless riding it. */
    public static final double REACH = 8;
    /** Ore blocks side-mined per step at most, and how far from the cube a vein is followed. */
    public static final int VEIN_BUDGET = 48, VEIN_RANGE = 6;
    /** A lamp goes into the roof every this many steps. */
    public static final int LAMP_EVERY = 6;
    /** Key of the machine's state in an item's custom data. */
    public static final String ITEM_TAG = "tunnel_borer";

    /**
     * Chunk ticket of a machine in auto mode, renewed every second and lapsing three seconds after the run ends or
     * the machine is packed up. At this radius the machine's own chunk and the eight around it tick entities (so it
     * keeps running when it crosses into the next chunk) and the 5×5 chunks around it run; it also keeps its
     * dimension running with no player in it.
     */
    private static final TicketType TICKET = new TicketType(60L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final int TICKET_RADIUS = 3;

    /** Outcomes of an attempt besides the status codes: wait quietly (a chunk still loading), or the cutter is grinding a creature. */
    private static final int WAIT = -1, GRINDING = -2;
    /** What the driver last asked for, so that a refused order is retried calmly but a new one is tried at once. */
    private static final int ORDER_NONE = 0, ORDER_FORWARD = 1, ORDER_BACK = 2;

    private enum Op { NONE, CUT, GLIDE, TURN }

    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

    /** Client only: the cutter head's angle, for the renderer. */
    private float spin, spinO;
    /** Client only: the machine's yaw when the rider was last placed, to turn the rider's view with the machine. */
    private float riderYaw = Float.NaN;

    // ---- server state ----
    /** The bottom middle block of the machine's 3×3×3 cube: the entity stands at the centre of its floor. */
    private BlockPos base = BlockPos.ZERO;
    private boolean placed;
    /** Facing for stepping; during a turn already the new one (the yaw catches up). */
    private Direction dir = Direction.SOUTH;
    private UUID owner;
    /** The selected length of an auto run: an entry of {@link #DISTANCES}. */
    private int distance = 32;
    private final Bin bin = new Bin();

    private Op op = Op.NONE;
    private int opTick, opLength, opWait;
    private List<BlockPos> cutting;
    private BlockPos cutTarget;
    private int crackStage = -1;
    private Vec3 glideFrom, glideTo;
    private float turnFrom, turnTo;

    private boolean lastLeft, lastRight;
    private int queuedTurn, retry, lastOrder, grindCooldown, ticketClock, suckClock;

    public TunnelBorer(EntityType<? extends TunnelBorer> type, Level level) {
        super(type, level);
        blocksBuilding = true;                          // nobody builds into the machine
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(ENERGY, 0); b.define(FLAGS, DISCARD_JUNK | LINING | VEINS | LIGHTS); b.define(SLOPE, 0); b.define(BIN, 0);
        b.define(AUTO_LEFT, 32); b.define(STATUS, S_IDLE); b.define(DUG, 0); b.define(ORES, 0);
    }

    // ---- read by the client ----
    public int energy() { return entityData.get(ENERGY); }
    public int capacity() { return CAPACITY; }
    public boolean flag(int bit) { return (entityData.get(FLAGS) & bit) != 0; }
    public int flags() { return entityData.get(FLAGS); }
    public boolean auto() { return flag(AUTO); }
    public boolean boring() { return flag(BORING); }
    public boolean moving() { return flag(MOVING); }
    /** Slope of the auto run (and of a manual step without Space/Ctrl): -1 down, 0 level, 1 up. */
    public int slope() { return entityData.get(SLOPE); }
    public int binUsed() { return entityData.get(BIN); }
    public int binSize() { return BIN_SIZE; }
    /** Blocks left in the auto run; -1 unlimited. */
    public int autoLeft() { return entityData.get(AUTO_LEFT); }
    public int status() { return entityData.get(STATUS); }
    public String statusKey() { int s = status(); return "status.heliosphere.borer." + STATUS_KEYS[s >= 0 && s < STATUS_KEYS.length ? s : 0]; }
    /** Blocks bored since the machine was built (kept when packed up). */
    public int dug() { return entityData.get(DUG); }
    /** Ore blocks collected since the machine was built (kept when packed up). */
    public int ores() { return entityData.get(ORES); }
    public Direction facing() { return Direction.fromYRot(getYRot()); }
    /** The cutter head's angle in radians, interpolated (client). */
    public float cutterAngle(float partial) { return Mth.lerp(partial, spinO, spin); }

    // ---- written by the server ----
    protected void setEnergy(int e) { entityData.set(ENERGY, Math.max(0, Math.min(CAPACITY, e))); }
    protected void setFlag(int bit, boolean on) { int f = entityData.get(FLAGS); entityData.set(FLAGS, on ? f | bit : f & ~bit); }
    protected void setSlope(int s) { entityData.set(SLOPE, Math.max(-1, Math.min(1, s))); }
    protected void setBinUsed(int n) { entityData.set(BIN, n); }
    protected void setAutoLeft(int n) { entityData.set(AUTO_LEFT, n); }
    protected void setStatus(int s) { entityData.set(STATUS, s); }
    protected void setDug(int n) { entityData.set(DUG, n); }
    protected void setOres(int n) { entityData.set(ORES, n); }

    // ---- server API (orders, the item, game tests) ----

    /** The bottom middle block of the machine's cube. */
    public BlockPos base() { return base; }
    /** The direction the machine bores in (during a turn already the new one). */
    public Direction direction() { return dir; }
    public UUID owner() { return owner; }
    /** The selected length of an auto run: an entry of {@link #DISTANCES}, -1 unlimited. */
    public int distance() { return distance; }
    /** The collection bin (54 slots). */
    public Container bin() { return bin; }
    /** Whether a step, glide or turn is under way. */
    public boolean busy() { return op != Op.NONE; }
    public void setStoredEnergy(int fe) { setEnergy(fe); }

    /** How many of {@code item} the bin holds. */
    public int countInBin(Item item) {
        int n = 0;
        for (ItemStack s : bin.getItems()) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** All items in the bin. */
    public int itemsInBin() {
        int n = 0;
        for (ItemStack s : bin.getItems()) n += s.getCount();
        return n;
    }

    /** Fuel items in the bin, for the control panel. */
    public int fuelCount() {
        FuelValues fuel = level().fuelValues();
        int n = 0;
        for (ItemStack s : bin.getItems()) if (!s.isEmpty() && fuel.isFuel(s)) n += s.getCount();
        return n;
    }

    /** Whether {@code p} may pack the machine up: its owner, a creative player or an operator (anyone, if it has no owner). */
    public boolean mayPack(Player p) {
        if (owner == null || owner.equals(p.getUUID()) || p.isCreative()) return true;
        return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Whether {@code p} is aboard or near enough to give orders and use the bin. */
    public boolean inReach(Player p) {
        if (isRemoved() || !p.isAlive() || p.level() != level()) return false;
        return hasPassenger(p) || p.distanceToSqr(getX(), getY() + 1.5, getZ()) <= REACH * REACH;
    }

    public void setSetting(int bit, boolean on) { if ((bit & SETTINGS) != 0) setFlag(bit & SETTINGS, on); }
    public void toggleSetting(int bit) { if ((bit & SETTINGS) != 0) setFlag(bit & SETTINGS, !flag(bit & SETTINGS)); }

    /** The slope of the auto run: 0 → up → down → level. */
    public void cycleSlope() { setSlope(slope() == 0 ? 1 : slope() == 1 ? -1 : 0); }
    public void setSlopeSetting(int s) { setSlope(s); }

    /** The next length of an auto run; a run under way goes on for the new length from here. */
    public void cycleDistance() {
        int i = 0;
        while (i < DISTANCES.length && DISTANCES[i] != distance) i++;
        setDistance(i >= DISTANCES.length ? DISTANCES[0] : DISTANCES[(i + 1) % DISTANCES.length]);
    }

    public void setDistance(int blocks) {
        distance = blocks < 0 ? -1 : Math.max(1, blocks);
        setAutoLeft(distance);
    }

    public void toggleAuto() { if (auto()) cancelAuto(); else startAuto(); }

    /** Auto mode: bores the selected distance along its facing and the slope setting, with or without a driver. */
    public void startAuto() {
        if (auto() || isRemoved()) return;
        setAutoLeft(distance);
        setFlag(AUTO, true);
        retry = 0;
        ticketClock = 0;
        if (op == Op.NONE) setStatus(S_IDLE);
    }

    /** Auto mode taken back by the driver or the panel: a cut in progress is dropped. */
    public void cancelAuto() {
        if (!auto()) return;
        setFlag(AUTO, false);
        if (op == Op.CUT && level() instanceof ServerLevel sl) abortCut(sl);
        if (op == Op.NONE) setStatus(S_IDLE);
    }

    /**
     * Test hook: one whole step at once, the same as a timed one but without the cutting time and the glide.
     * S_BORING when the machine moved; otherwise why it did not (S_OBSTRUCTED also while a creature is in the face,
     * which the cutter hurts as usual, or while a chunk is still loading).
     */
    public int stepNow(int slope) {
        if (!(level() instanceof ServerLevel level) || op != Op.NONE) return status();
        int r = beginStep(level, slope);
        if (r == S_BORING) {
            opTick = opLength;
            r = finishCut(level);
            if (op == Op.CUT) abortCut(level);
            else if (op == Op.GLIDE) { opTick = opLength - 1; tickGlide(level); }
        }
        if (r == GRINDING || r == WAIT) r = S_OBSTRUCTED;
        setFlag(BORING, false);
        if (r != S_BORING) setStatus(r);
        else if (!auto()) setStatus(S_IDLE);
        return r;
    }

    /** Test hook: a quarter turn at once, right (clockwise seen from above) or left. */
    public void turnNow(boolean right) {
        if (op != Op.NONE) return;
        dir = right ? dir.getClockWise() : dir.getCounterClockWise();
        setYRot(dir.toYRot());
        yRotO = getYRot();
    }

    /** Test hook: one block back at once. S_REVERSING when the machine moved, S_NO_ROOM when it could not. */
    public int reverseNow() {
        if (!(level() instanceof ServerLevel level) || op != Op.NONE) return status();
        int r = beginReverse(level);
        if (op == Op.GLIDE) { opTick = opLength - 1; tickGlide(level); }
        return r;
    }

    /** The control panel's picture of the machine for {@code viewer}. */
    public BorerNet.Panel panel(Player viewer) {
        return new BorerNet.Panel(getId(), energy(), CAPACITY, flags(), slope(), distance, binUsed(), BIN_SIZE, status(),
                dug(), ores(), fuelCount(), mayPack(viewer));
    }

    public void sendPanel(ServerPlayer p) { ServerPlayNetworking.send(p, panel(p)); }

    /** Seats {@code p} when the seat is free. */
    public boolean board(ServerPlayer p) {
        if (isRemoved() || p.isSpectator() || !p.isAlive()) return false;
        Entity on = getFirstPassenger();
        if (on == p) return true;
        if (on != null) return false;
        if (p.isPassenger()) p.stopRiding();
        return p.startRiding(this, true, true);
    }

    /** Opens the bin as a chest of six rows, titled with the machine's name. */
    public boolean openBin(ServerPlayer p) {
        if (!inReach(p) || p.isSpectator()) return false;
        p.openMenu(new SimpleMenuProvider((id, inv, who) -> ChestMenu.sixRows(id, inv, bin), getName()));
        return true;
    }

    /**
     * Packs the machine up into its item, with the bin, charge, settings and odometers, and gives it to {@code p}
     * (or drops it). Anyone looking into the bin has it closed first, so nothing can be taken out twice.
     */
    public ItemStack pack(Player p) {
        if (!(level() instanceof ServerLevel level) || isRemoved()) return ItemStack.EMPTY;
        closeBinMenus(level);
        if (op == Op.CUT) abortCut(level);
        int items = itemsInBin();
        ItemStack out = toItem();
        bin.clearContent();
        ejectPassengers();
        discard();
        if (p != null) {
            p.displayClientMessage(Component.translatable("message.heliosphere.borer.packed", items).withStyle(ChatFormatting.GREEN), true);
            if (!p.getInventory().add(out)) p.drop(out, false);
        } else spawnAtLocation(level, out);
        level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.3f);
        return out;
    }

    /** The machine as an item: the bin in {@code minecraft:container}, the rest in custom data. */
    public ItemStack toItem() {
        ItemStack out = new ItemStack(Borers.ITEM);
        List<ItemStack> items = new ArrayList<>(BIN_SIZE);
        for (ItemStack s : bin.getItems()) items.add(s.copy());
        out.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        CompoundTag t = new CompoundTag();
        t.putInt("energy", flag(CREATIVE) ? Math.min(energy(), START_ENERGY) : energy());
        t.putInt("settings", flags() & SETTINGS);
        t.putInt("slope", slope());
        t.putInt("distance", distance);
        t.putInt("dug", dug());
        t.putInt("ores", ores());
        CompoundTag root = new CompoundTag();
        root.put(ITEM_TAG, t);
        out.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        if (hasCustomName()) out.set(DataComponents.CUSTOM_NAME, getCustomName());
        return out;
    }

    /** What a packed machine carried: bin, charge, settings, odometers, name. A plain item makes a new machine. */
    void restoreFrom(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) { bin.clearContent(); contents.copyInto(bin.getItems()); bin.setChanged(); }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = data == null ? null : data.copyTag().getCompound(ITEM_TAG).orElse(null);
        if (t == null) setEnergy(START_ENERGY);
        else {
            setEnergy(t.getIntOr("energy", START_ENERGY));
            int f = flags() & ~SETTINGS;
            entityData.set(FLAGS, f | (t.getIntOr("settings", DISCARD_JUNK | LINING | VEINS | LIGHTS) & SETTINGS));
            setSlope(t.getIntOr("slope", 0));
            setDistance(t.getIntOr("distance", 32));
            setDug(Math.max(0, t.getIntOr("dug", 0)));
            setOres(Math.max(0, t.getIntOr("ores", 0)));
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) setCustomName(name);
    }

    // ---- placing ----

    /**
     * Sets a machine down with its cube's bottom middle block at {@code base}, facing {@code facing} (horizontal),
     * owned by {@code owner}. {@code from}, if given, is the item it comes from (bin, charge, settings, odometers);
     * otherwise it is a new machine with {@link #START_ENERGY}. The place is not checked: see {@link #findPlace} and {@link #roomFor}.
     */
    public static TunnelBorer place(ServerLevel level, BlockPos base, Direction facing, Player owner, boolean creative, ItemStack from) {
        TunnelBorer b = Borers.ENTITY.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (b == null) return null;
        b.setup(base, facing, owner == null ? null : owner.getUUID(), creative, from);
        level.addFreshEntity(b);
        return b;
    }

    void setup(BlockPos at, Direction facing, UUID owner, boolean creative, ItemStack from) {
        base = at.immutable();
        dir = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        this.owner = owner;
        placed = true;
        snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, dir.toYRot(), 0);
        if (from != null && !from.isEmpty()) restoreFrom(from); else setEnergy(START_ENERGY);
        setFlag(CREATIVE, creative);
        if (creative) setEnergy(CAPACITY);
    }

    /** The cube of a machine whose bottom middle block is {@code base}. */
    public static AABB cubeBox(BlockPos base) {
        return AABB.encapsulatingFullBlocks(base.offset(-1, 0, -1), base.offset(1, 2, 1));
    }

    /** Whether a machine fits with its bottom middle block at {@code base}: clear blocks, inside the world, no other machine. */
    public static boolean roomFor(Level level, BlockPos base) {
        if (base.getY() < level.getMinY() || base.getY() + 2 > level.getMaxY()) return false;
        if (!level.getWorldBorder().isWithinBounds(cubeBox(base))) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos q = base.offset(dx, dy, dz);
            if (!level.isLoaded(q) || !BorerMining.clearForPlacing(level.getBlockState(q))) return false;
        }
        return level.getEntitiesOfClass(TunnelBorer.class, cubeBox(base).deflate(0.05), e -> !e.isRemoved()).isEmpty();
    }

    /**
     * Where to set a machine down when {@code clicked} is right-clicked on {@code face} by a player looking
     * {@code facing}: on the ground the cube stands just beyond the clicked spot (or on it, or beside it); against a
     * wall it stands in front of the wall on the floor; under a ceiling it hangs below. Prefers a place that does not
     * take in the player and has a floor. Null when nothing fits.
     */
    public static BlockPos findPlace(Level level, BlockPos clicked, Direction face, Direction facing, Player p) {
        boolean into = level.getBlockState(clicked).canBeReplaced();
        BlockPos adj = into ? clicked : clicked.relative(face);
        Direction right = facing.getClockWise();
        List<BlockPos> tries = new ArrayList<>();
        if (!into && face.getAxis().isHorizontal()) {
            BlockPos c = adj.relative(face);                                // front slice against the wall
            for (int dy : new int[]{0, -1, -2, 1}) {
                tries.add(c.above(dy));
                tries.add(c.relative(face.getClockWise()).above(dy));
                tries.add(c.relative(face.getCounterClockWise()).above(dy));
            }
        } else if (!into && face == Direction.DOWN) {
            BlockPos c = adj.below(2);
            tries.add(c.relative(facing)); tries.add(c); tries.add(c.relative(facing, 2));
        } else {
            tries.add(adj.relative(facing)); tries.add(adj); tries.add(adj.relative(facing, 2));
            tries.add(adj.relative(facing).relative(right)); tries.add(adj.relative(facing).relative(right.getOpposite()));
            tries.add(adj.relative(right)); tries.add(adj.relative(right.getOpposite()));
        }
        BlockPos clearOfPlayer = null, any = null;
        for (BlockPos c : tries) {
            if (!roomFor(level, c)) continue;
            boolean free = p == null || !cubeBox(c).intersects(p.getBoundingBox());
            boolean floor = !level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty();
            if (free && floor) return c;
            if (free && clearOfPlayer == null) clearOfPlayer = c;
            if (any == null) any = c;
        }
        return clearOfPlayer != null ? clearOfPlayer : any;
    }

    // ---- an entity that is a vehicle ----
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith(Entity other) { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
    @Override public boolean ignoreExplosion(Explosion explosion) { return true; }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public boolean canFreeze() { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }
    @Override public ItemStack getPickResult() { return new ItemStack(Borers.ITEM); }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty() && e instanceof Player; }
    /** Nobody steers the entity directly: the server reads the rider's keys and moves the machine. */
    @Override public LivingEntity getControllingPassenger() { return null; }

    /** The driver sits in the middle of the machine; the eye ends up about 1.85 blocks above its floor. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float scale) {
        return new Vec3(0, 0.25, -0.1).yRot((float) Math.toRadians(-getYRot()));
    }

    /** On the client the rider's view turns with the machine (the server turns the machine; the client owns the view). */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (!level().isClientSide()) return;
        float yaw = getYRot();
        if (!Float.isNaN(riderYaw)) {
            float d = Mth.wrapDegrees(yaw - riderYaw);
            if (d != 0 && Math.abs(d) < 45) {
                passenger.setYRot(passenger.getYRot() + d);
                passenger.setYHeadRot(passenger.getYHeadRot() + d);
            }
        }
        riderYaw = yaw;
    }

    @Override
    protected void addPassenger(Entity e) {
        super.addPassenger(e);
        riderYaw = Float.NaN;
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        riderYaw = Float.NaN;
    }

    /** Out of the hatch: set down behind the machine, in the tunnel; walled in all round, on the machine's own floor. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity who) {
        Direction back = dir.getOpposite(), right = dir.getClockWise();
        for (int dy : new int[]{0, 1, -1, 2, -2})
            for (int l : new int[]{0, -1, 1}) {
                BlockPos at = base.relative(back, 2).relative(right, l).above(dy);
                Vec3 v = DismountHelper.findSafeDismountLocation(who.getType(), level(), at, true);
                if (v != null) return v;
            }
        return new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
    }

    /** Right-click: sit in. Sneak + right-click (or the driver's own click): the control panel. */
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(p instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (p.isSecondaryUseActive() || hasPassenger(p)) sendPanel(sp);
        else board(sp);
        return InteractionResult.SUCCESS;
    }

    /** Sneak + left-click by the owner, a creative player or an operator packs the machine up; nothing else hurts it. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof ServerPlayer sp && sp.isShiftKeyDown() && !hasPassenger(sp) && !isRemoved()) {
            if (mayPack(sp)) pack(sp);
            else sp.displayClientMessage(Component.translatable("message.heliosphere.borer.not_owner").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && !isRemoved()) {
            clearCracks(sl);
            if (reason.shouldDestroy()) {                     // killed by a command: what the bin held falls out
                closeBinMenus(sl);
                Containers.dropContents(sl, this, bin);
            }
        }
        super.remove(reason);
    }

    // ---- tick ----
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            interpolation.interpolate();
            spinO = spin;
            if (boring()) spin += 0.45f;
            return;
        }
        serverTick((ServerLevel) level());
    }

    /** Everything the machine does (server). */
    protected void serverTick(ServerLevel level) {
        if (!placed || op == Op.NONE && !atBase()) settle();          // summoned or moved by a command: onto the grid where it is
        if (flag(CREATIVE) && energy() != CAPACITY) setEnergy(CAPACITY);
        if (grindCooldown > 0) grindCooldown--;

        ServerPlayer rider = rider();
        Input in = rider == null ? Input.EMPTY : rider.getLastClientInput();
        if (in.left() && !lastLeft) queuedTurn = -1;
        if (in.right() && !lastRight) queuedTurn = 1;
        lastLeft = in.left();
        lastRight = in.right();
        if (rider == null) queuedTurn = 0;
        if (auto() && rider != null && (in.backward() || in.left() || in.right())) cancelAuto();   // S, A or D take it back
        else if (!auto() && op == Op.CUT && (rider == null || in.backward() || in.left() || in.right()))
            abortCut(level);                                            // driving by hand: the driver left, or wants something else

        if (auto() && --ticketClock <= 0) {
            ticketClock = 20;
            level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(base), TICKET_RADIUS);
        }

        switch (op) {
            case CUT -> tickCut(level);
            case GLIDE -> tickGlide(level);
            case TURN -> tickTurn();
            case NONE -> {}
        }
        if (op == Op.NONE) idle(level, rider, in);
        if (op == Op.NONE && --suckClock <= 0) { suckClock = 40; suck(level, cubeBox(base).inflate(1)); }
    }

    private ServerPlayer rider() { return getFirstPassenger() instanceof ServerPlayer sp ? sp : null; }

    private boolean atBase() {
        return Math.abs(getX() - (base.getX() + 0.5)) < 1e-4 && Math.abs(getY() - base.getY()) < 1e-4 && Math.abs(getZ() - (base.getZ() + 0.5)) < 1e-4;
    }

    private void settle() {
        base = BlockPos.containing(getX(), getY() + 1e-3, getZ());
        if (!placed) dir = Direction.fromYRot(getYRot());
        placed = true;
        snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, dir.toYRot(), 0);
    }

    /** Nothing under way: the driver's keys, or the auto run. */
    private void idle(ServerLevel level, ServerPlayer rider, Input in) {
        if (retry > 0) retry--;
        if (auto()) {
            if (retry == 0) afterAttempt(level, beginStep(level, slope()), true);
            return;
        }
        if (rider == null) { lastOrder = ORDER_NONE; queuedTurn = 0; relax(); return; }
        int turn = queuedTurn != 0 ? queuedTurn : in.left() == in.right() ? 0 : in.left() ? -1 : 1;
        queuedTurn = 0;
        if (turn != 0) { lastOrder = ORDER_NONE; beginTurn(turn); return; }
        int order = in.backward() ? ORDER_BACK : in.forward() ? ORDER_FORWARD : ORDER_NONE;
        if (order != lastOrder) retry = 0;
        lastOrder = order;
        if (order == ORDER_NONE) { relax(); return; }
        if (retry > 0) return;
        if (order == ORDER_BACK) afterAttempt(level, beginReverse(level), false);
        else afterAttempt(level, beginStep(level, in.jump() ? 1 : in.sprint() ? -1 : slope()), false);
    }

    /** No orders: what the machine was doing is over; a reason it stopped stays on show until the next order. */
    private void relax() {
        int s = status();
        if (s == S_BORING || s == S_TURNING || s == S_REVERSING || s == S_OBSTRUCTED) setStatus(S_IDLE);
        if (boring()) setFlag(BORING, false);
    }

    private void afterAttempt(ServerLevel level, int r, boolean auto) {
        switch (r) {
            case S_BORING, S_REVERSING -> retry = 0;
            case WAIT -> retry = 5;
            case GRINDING -> { setFlag(BORING, true); setStatus(S_BORING); retry = 2; }
            case S_OBSTRUCTED -> { setFlag(BORING, false); setStatus(S_OBSTRUCTED); retry = 5; }
            default -> {                                                  // a stop condition
                setFlag(BORING, false);
                if (auto) stopAuto(level, r);
                else { setStatus(r); retry = 10; }
            }
        }
    }

    /** The auto run ends: why, told to the driver and the owner. */
    private void stopAuto(ServerLevel level, int reason) {
        setFlag(AUTO, false);
        setStatus(reason);
        retry = 10;
        MutableComponent msg = Component.translatable("message.heliosphere.borer.stopped",
                Component.translatable("status.heliosphere.borer." + STATUS_KEYS[reason]), base.getX(), base.getY(), base.getZ())
                .withStyle(reason == S_DONE ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        ServerPlayer rider = rider();
        if (rider != null) rider.displayClientMessage(msg, true);
        if (owner != null && level.getServer() != null) {
            ServerPlayer o = level.getServer().getPlayerList().getPlayer(owner);
            if (o != null && o != rider) o.sendSystemMessage(msg);
        }
    }

    // ---- stepping ----

    /** The blocks of the cube at {@code target} that the cube at {@code from} does not hold. */
    private static List<BlockPos> newBlocks(BlockPos from, BlockPos target) {
        List<BlockPos> out = new ArrayList<>(15);
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = target.offset(dx, dy, dz);
            if (!inCube(from, p)) out.add(p);
        }
        return out;
    }

    private static boolean inCube(BlockPos base, BlockPos p) {
        return Math.abs(p.getX() - base.getX()) <= 1 && Math.abs(p.getZ() - base.getZ()) <= 1 && p.getY() >= base.getY() && p.getY() <= base.getY() + 2;
    }

    /** Inside the world border, and a block clear of the bottom and the top of the world. */
    private static boolean withinLimits(Level level, BlockPos target) {
        if (target.getY() < level.getMinY() + 1 || target.getY() + 2 > level.getMaxY() - 1) return false;
        return level.getWorldBorder().isWithinBounds(cubeBox(target));
    }

    /** Starts the cutter on the next face. S_BORING when it started; otherwise what stands in the way. */
    private int beginStep(ServerLevel level, int slope) {
        BlockPos target = base.relative(dir).offset(0, Mth.clamp(slope, -1, 1), 0);
        if (!withinLimits(level, target)) return S_WORLD_LIMIT;
        List<BlockPos> face = newBlocks(base, target);
        for (BlockPos p : face) if (!level.isLoaded(p)) return WAIT;
        float hard = 0;
        int cost = 0;
        for (BlockPos p : face) {
            BlockState s = level.getBlockState(p);
            if (BorerMining.unbreakable(s, level, p)) return S_UNBREAKABLE;
            float h = BorerMining.hardness(s, level, p);
            hard += h;
            cost += BorerMining.cost(h);
        }
        int way = clearWay(level, face);
        if (way != 0) return way;
        if (!fits(gather(level, face))) return S_BIN_FULL;
        if (!ensureEnergy(level, cost)) return S_NO_ENERGY;
        op = Op.CUT;
        opTick = 0;
        opWait = 0;
        opLength = BorerMining.cuttingTicks(hard);
        cutting = face;
        cutTarget = target;
        crackStage = -1;
        setFlag(BORING, true);
        setStatus(S_BORING);
        return S_BORING;
    }

    private void tickCut(ServerLevel level) {
        if (opTick < opLength) {
            opTick++;
            int stage = Math.min(9, opTick * 10 / Math.max(1, opLength));
            if (stage != crackStage) { crackStage = stage; crack(level, stage); }    // the chips and the grinding are the client's
            if (opTick % 10 == 0) suck(level, cubeBox(base).minmax(cubeBox(cutTarget)).inflate(1));
            if (opTick < opLength) return;
        }
        if (opWait > 0) { opWait--; return; }
        int r = finishCut(level);
        if (r == S_BORING) return;
        if (r == S_OBSTRUCTED || r == GRINDING || r == WAIT) {        // something walked in: wait for it at the face
            setFlag(BORING, true);
            setStatus(r == S_OBSTRUCTED ? S_OBSTRUCTED : S_BORING);
            opWait = r == GRINDING ? 2 : 5;
            return;
        }
        abortCut(level);
        afterAttempt(level, r, auto());
    }

    /**
     * The cutter is through: checks the face again (it may have changed meanwhile), takes the energy, removes the
     * blocks into the bin and starts the glide. S_BORING when done; otherwise why not (the cut stays open).
     */
    private int finishCut(ServerLevel level) {
        int cost = 0;
        for (BlockPos p : cutting) {
            if (!level.isLoaded(p)) return WAIT;
            BlockState s = level.getBlockState(p);
            if (BorerMining.unbreakable(s, level, p)) return S_UNBREAKABLE;
            cost += BorerMining.cost(BorerMining.hardness(s, level, p));
        }
        int way = clearWay(level, cutting);
        if (way != 0) return way;
        if (!fits(gather(level, cutting))) return S_BIN_FULL;
        if (!ensureEnergy(level, cost)) return S_NO_ENERGY;
        spend(cost);
        clearCracks(level);
        int ores = 0;
        for (BlockPos p : cutting) {
            // each block's drops are taken as it goes: one that dropped off with its support (a torch, the other half
            // of a door or bed) went out as an item entity and is not counted twice (the bin picks it up afterwards)
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (BorerMining.isOre(s)) ores++;
            List<ItemStack> loot = gather(level, List.of(p));
            removeBlock(level, p, s, true);
            for (ItemStack it : loot) {
                ItemStack left = store(it);                       // a luckier roll than the check's can leave a little over
                if (!left.isEmpty()) Block.popResource(level, p, left);
            }
        }
        Set<BlockPos> mined = new HashSet<>(cutting);
        base = cutTarget;
        cutting = null;
        setDug(dug() + 1);
        if (ores > 0) setOres(ores() + ores);
        if (auto() && autoLeft() > 0) setAutoLeft(autoLeft() - 1);
        if (flag(VEINS)) mineVeins(level);
        if (flag(LIGHTS) && dug() % LAMP_EVERY == 0) setLamp(level);
        seal(level, mined);
        setFlag(BORING, false);
        beginGlide(base);
        return S_BORING;
    }

    private void abortCut(ServerLevel level) {
        clearCracks(level);
        cutting = null;
        op = Op.NONE;
        opWait = 0;
        setFlag(BORING, false);
    }

    private void beginGlide(BlockPos to) {
        glideFrom = position();
        glideTo = new Vec3(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        op = Op.GLIDE;
        opTick = 0;
        opLength = GLIDE_TICKS;
        setFlag(MOVING, true);
    }

    private void tickGlide(ServerLevel level) {
        opTick++;
        Vec3 at = opTick >= opLength ? glideTo : glideFrom.lerp(glideTo, opTick / (double) opLength);
        setPos(at.x, at.y, at.z);
        if (opTick < opLength) return;
        op = Op.NONE;
        setFlag(MOVING, false);
        suck(level, cubeBox(base).inflate(1.5));
        if (auto() && autoLeft() == 0) stopAuto(level, S_DONE);
    }

    private void beginTurn(int turn) {
        turnFrom = getYRot();
        turnTo = turnFrom + 90f * turn;
        dir = turn > 0 ? dir.getClockWise() : dir.getCounterClockWise();
        op = Op.TURN;
        opTick = 0;
        opLength = TURN_TICKS;
        setFlag(MOVING, true);
        setStatus(S_TURNING);
    }

    private void tickTurn() {
        opTick++;
        float t = Math.min(1f, opTick / (float) opLength);
        t = t * t * (3 - 2 * t);
        setYRot(Mth.lerp(t, turnFrom, turnTo));
        if (opTick < opLength) return;
        setYRot(dir.toYRot());
        op = Op.NONE;
        setFlag(MOVING, false);
        setStatus(S_IDLE);
    }

    /** One block back through free space, level or following a slope; no digging. */
    private int beginReverse(ServerLevel level) {
        BlockPos behind = base.relative(dir.getOpposite());
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos target = behind.above(dy);
            if (!withinLimits(level, target)) continue;
            List<BlockPos> room = newBlocks(base, target);
            boolean free = true;
            for (BlockPos p : room)
                if (!level.isLoaded(p) || !BorerMining.passable(level.getBlockState(p), level, p)) { free = false; break; }
            if (!free || occupied(level, room)) continue;
            base = target;
            beginGlide(target);
            setStatus(S_REVERSING);
            return S_REVERSING;
        }
        return S_NO_ROOM;
    }

    private static AABB boxOf(List<BlockPos> blocks) {
        AABB all = new AABB(blocks.get(0));
        for (BlockPos p : blocks) all = all.minmax(new AABB(p));
        return all;
    }

    private static boolean overlaps(AABB box, List<BlockPos> blocks) {
        for (BlockPos p : blocks) if (box.intersects(new AABB(p))) return true;
        return false;
    }

    /** Any creature (but our driver) or another machine in these blocks. */
    private boolean occupied(ServerLevel level, List<BlockPos> blocks) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, boxOf(blocks), e -> e.isAlive() && !e.isSpectator() && !hasPassenger(e)))
            if (overlaps(e.getBoundingBox(), blocks)) return true;
        return otherMachine(level, blocks);
    }

    /** Another tunnel borer standing in these blocks. */
    private boolean otherMachine(ServerLevel level, List<BlockPos> blocks) {
        for (TunnelBorer b : level.getEntitiesOfClass(TunnelBorer.class, boxOf(blocks), b -> b != this && !b.isRemoved()))
            if (overlaps(b.getBoundingBox(), blocks)) return true;
        return false;
    }

    /**
     * The way through the face: 0 when clear; S_OBSTRUCTED while a player (or another machine) stands in it;
     * GRINDING while the cutter works on a creature in it (it is hurt every {@link #GRIND_INTERVAL} ticks until it
     * is gone). What the cutter cannot hurt (an armor stand, an invulnerable creature) does not hold it up for ever:
     * the machine passes over it.
     */
    private int clearWay(ServerLevel level, List<BlockPos> face) {
        if (otherMachine(level, face)) return S_OBSTRUCTED;
        List<LivingEntity> victims = null;
        DamageSource cut = damageSources().generic();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, boxOf(face), e -> e.isAlive() && !e.isSpectator() && !hasPassenger(e))) {
            if (!overlaps(e.getBoundingBox(), face)) continue;
            if (e instanceof Player) return S_OBSTRUCTED;
            if (e instanceof ArmorStand || e.isInvulnerableTo(level, cut)) continue;
            if (victims == null) victims = new ArrayList<>();
            victims.add(e);
        }
        if (victims == null) return 0;
        if (grindCooldown == 0) {
            grindCooldown = GRIND_INTERVAL;
            for (LivingEntity e : victims) e.hurtServer(level, cut, GRIND_DAMAGE);
            Vec3 c = boxOf(face).getCenter();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1f, 0.6f);
        }
        return GRINDING;
    }

    /** Breaker ids for the crack overlay: one per block of the face, negative so they never meet a player's. */
    private int crackId(int i) { return -((getId() & 0x3FFFFFF) << 5 | i) - 1; }

    private void crack(ServerLevel level, int stage) {
        if (cutting == null) return;
        for (int i = 0; i < cutting.size(); i++) {
            BlockPos p = cutting.get(i);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !BorerMining.isFluid(s)) level.destroyBlockProgress(crackId(i), p, stage);
        }
    }

    private void clearCracks(ServerLevel level) {
        if (cutting == null) return;
        for (int i = 0; i < cutting.size(); i++) level.destroyBlockProgress(crackId(i), cutting.get(i), -1);
    }

    /** Takes a block the bin has already been given everything of: break effects, no item entities. */
    private void removeBlock(ServerLevel level, BlockPos p, BlockState s, boolean effects) {
        if (s.isAir()) return;
        if (effects && !BorerMining.isFluid(s)) level.levelEvent(2001, p, Block.getId(s));
        BlockEntity be = s.hasBlockEntity() ? level.getBlockEntity(p) : null;
        if (be instanceof Container c && !(s.getBlock() instanceof ShulkerBoxBlock)) c.clearContent();
        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_DESTROY, p, GameEvent.Context.of(this, s));
        if (!BorerMining.isFluid(s)) s.spawnAfterBreak(level, p, tool(), true);
    }

    /** The cutter counts as a diamond pickaxe for loot. */
    private static ItemStack tool() { return new ItemStack(Items.DIAMOND_PICKAXE); }

    /** Everything these blocks would give: their drops, and the contents of containers (a shulker box keeps its own). */
    private List<ItemStack> gather(ServerLevel level, List<BlockPos> blocks) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack tool = tool();
        for (BlockPos p : blocks) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || BorerMining.isFluid(s)) continue;
            BlockEntity be = s.hasBlockEntity() ? level.getBlockEntity(p) : null;
            out.addAll(Block.getDrops(s, level, p, be, this, tool));
            if (be instanceof Container c && !(s.getBlock() instanceof ShulkerBoxBlock))
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack it = c.getItem(i);
                    if (!it.isEmpty()) out.add(it.copy());
                }
        }
        return out;
    }

    // ---- after a step: veins, lamp, sealing and lining ----

    /** Ore in the 7×7 cross-section of the new front slice, and the whole body it belongs to, into the bin. */
    private void mineVeins(ServerLevel level) {
        Direction right = dir.getClockWise();
        BlockPos front = base.relative(dir);
        Vec3 home = new Vec3(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5);
        Set<BlockPos> seen = new HashSet<>();
        int budget = VEIN_BUDGET;
        for (int l = -3; l <= 3; l++)
            for (int v = -2; v <= 4; v++) {
                if (budget <= 0) return;
                if (Math.abs(l) <= 1 && v >= 0 && v <= 2) continue;
                BlockPos p = front.relative(right, l).above(v);
                if (!seen.add(p) || !level.isLoaded(p) || !level.isInsideBuildHeight(p.getY())) continue;
                BlockState s = level.getBlockState(p);
                if (!BorerMining.isOre(s) || BorerMining.unbreakable(s, level, p)) continue;
                List<BlockPos> body = new ArrayList<>();
                ArrayDeque<BlockPos> open = new ArrayDeque<>();
                open.add(p);
                while (!open.isEmpty() && body.size() < budget) {
                    BlockPos q = open.poll();
                    body.add(q);
                    for (Direction d : Direction.values()) {
                        BlockPos n = q.relative(d);
                        if (inCube(base, n) || !nearCube(n) || !seen.add(n)) continue;
                        if (!level.isLoaded(n) || !level.isInsideBuildHeight(n.getY())) continue;
                        BlockState ns = level.getBlockState(n);
                        if (BorerMining.isOre(ns) && !BorerMining.unbreakable(ns, level, n)) open.add(n);
                    }
                }
                int got = mineBody(level, body, home);
                if (got < 0) return;                               // out of energy or room in the bin
                budget -= got;
            }
    }

    /** Within {@link #VEIN_RANGE} blocks of the cube. */
    private boolean nearCube(BlockPos p) {
        int dx = Math.max(0, Math.abs(p.getX() - base.getX()) - 1);
        int dz = Math.max(0, Math.abs(p.getZ() - base.getZ()) - 1);
        int dy = p.getY() < base.getY() ? base.getY() - p.getY() : Math.max(0, p.getY() - base.getY() - 2);
        return Math.max(dx, Math.max(dy, dz)) <= VEIN_RANGE;
    }

    /**
     * Mines one ore body into the bin, block by block, for energy like a cut. Returns how many blocks it took, or -1
     * when it had to stop for want of energy or room in the bin (what it took by then stays taken).
     */
    private int mineBody(ServerLevel level, List<BlockPos> body, Vec3 home) {
        int n = 0;
        boolean stalled = false;
        for (BlockPos q : body) {
            BlockState s = level.getBlockState(q);
            if (!BorerMining.isOre(s) || BorerMining.unbreakable(s, level, q)) continue;
            List<ItemStack> loot = gather(level, List.of(q));
            int cost = BorerMining.cost(BorerMining.hardness(s, level, q));
            if (!fits(loot) || !ensureEnergy(level, cost)) { stalled = true; break; }
            spend(cost);
            removeBlock(level, q, s, n < 6);
            for (ItemStack it : loot) store(it);
            n++;
        }
        if (n > 0) {
            setOres(ores() + n);
            trail(level, Vec3.atCenterOf(body.get(0)), home);
            level.playSound(null, home.x, home.y, home.z, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 0.7f + random.nextFloat() * 0.3f);
        }
        return stalled ? -1 : n;
    }

    private static final DustParticleOptions TRAIL = new DustParticleOptions(0xFFC24A, 1.1f);

    /** Particles along the way from an ore body to the machine. */
    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        double d = from.distanceTo(to);
        int steps = Math.max(2, Math.min(24, (int) Math.ceil(d * 2)));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(TRAIL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    /** Every few steps a lamp segment in the roof above the middle of the new front slice. */
    private void setLamp(ServerLevel level) {
        BlockPos p = base.relative(dir).above(3);
        if (!level.isLoaded(p)) return;
        BlockState s = level.getBlockState(p);
        if (s.is(Borers.LAMP) || BorerMining.unbreakable(s, level, p)) return;
        if (!s.isAir() && !s.canBeReplaced() && !BorerMining.isFluid(s)) {
            List<ItemStack> loot = gather(level, List.of(p));
            if (!fits(loot)) return;
            if (BorerMining.isOre(s)) setOres(ores() + 1);
            removeBlock(level, p, s, true);
            for (ItemStack it : loot) store(it);
        }
        level.setBlock(p, Borers.LAMP.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * The ring around each slice of the cube (its 5×5 cross-section less the bore): water and lava become segments,
     * always and free, and so does fluid in the face ahead. With lining on, the gaps beside the blocks just cut (air,
     * plants, snow, and gravel or sand, which is mined first) become segments too, each for one junk item from the
     * bin; with no junk left they stay open. Only ring places beside freshly cut blocks are lined, so the old tunnel
     * behind a turn, or one being crossed, is not walled up by the slices the machine merely stands in.
     */
    private void seal(ServerLevel level, Set<BlockPos> mined) {
        Direction right = dir.getClockWise();
        BlockState segment = Borers.SEGMENT.defaultBlockState();
        boolean lining = flag(LINING);
        for (int f = -1; f <= 1; f++) {
            BlockPos mid = base.relative(dir, f);
            for (int l = -2; l <= 2; l++)
                for (int v = -1; v <= 3; v++) {
                    if (Math.abs(l) <= 1 && v >= 0 && v <= 2) continue;
                    BlockPos p = mid.relative(right, l).above(v);
                    if (!level.isLoaded(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (!s.getFluidState().isEmpty()) { plug(level, p, s, segment); continue; }
                    if (!lining || !besideCut(p, mined, right)) continue;
                    if (s.isAir() || s.canBeReplaced()) {
                        if (takeJunk()) level.setBlock(p, segment, Block.UPDATE_ALL);
                    } else if (s.getBlock() instanceof FallingBlock && !BorerMining.unbreakable(s, level, p) && junkCount(bin.getItems()) > 0) {
                        List<ItemStack> loot = gather(level, List.of(p));
                        if (!fits(loot)) continue;
                        removeBlock(level, p, s, true);
                        for (ItemStack it : loot) store(it);
                        if (takeJunk()) level.setBlock(p, segment, Block.UPDATE_ALL);
                    }
                }
        }
        BlockPos ahead = base.relative(dir, 2);
        for (int l = -1; l <= 1; l++)
            for (int v = 0; v <= 2; v++) {
                BlockPos p = ahead.relative(right, l).above(v);
                if (!level.isLoaded(p)) continue;
                BlockState s = level.getBlockState(p);
                if (!s.getFluidState().isEmpty()) plug(level, p, s, segment);
            }
    }

    /** Whether ring place {@code p} touches (in its cross-section, corners included) a block cut in this step. */
    private static boolean besideCut(BlockPos p, Set<BlockPos> mined, Direction right) {
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++)
                if ((a != 0 || b != 0) && mined.contains(p.relative(right, a).above(b))) return true;
        return false;
    }

    /** Seals a place holding water or lava with a segment; a waterlogged block there goes into the bin first. */
    private void plug(ServerLevel level, BlockPos p, BlockState s, BlockState segment) {
        if (BorerMining.unbreakable(s, level, p)) return;
        if (!BorerMining.isFluid(s) && !s.canBeReplaced()) {
            List<ItemStack> loot = gather(level, List.of(p));
            removeBlock(level, p, s, false);
            for (ItemStack it : loot) {
                ItemStack left = store(it);
                if (!left.isEmpty()) Block.popResource(level, base.above(), left);
            }
        }
        level.setBlock(p, segment, Block.UPDATE_ALL);
    }

    // ---- the bin ----

    /** The collection bin: usable while the machine stands and the player is near it or aboard. */
    private final class Bin extends SimpleContainer {
        Bin() { super(BIN_SIZE); }
        @Override public boolean stillValid(Player p) { return inReach(p); }
        @Override public void setChanged() { super.setChanged(); binChanged(); }
    }

    private void binChanged() {
        if (level().isClientSide()) return;
        int used = 0;
        for (ItemStack s : bin.getItems()) if (!s.isEmpty()) used++;
        if (used != binUsed()) setBinUsed(used);
    }

    private void closeBinMenus(ServerLevel level) {
        if (level.getServer() == null) return;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
            if (p.containerMenu instanceof ChestMenu m && m.getContainer() == bin) p.closeContainer();
    }

    /** Puts {@code stack} into the bin (junk past the keep is thrown away); returns what did not fit. */
    private ItemStack store(ItemStack stack) {
        ItemStack left = insert(bin.getItems(), stack, flag(DISCARD_JUNK), bin.getMaxStackSize());
        bin.setChanged();
        return left;
    }

    /** Whether all of {@code loot} would go into the bin. */
    private boolean fits(List<ItemStack> loot) {
        if (loot.isEmpty()) return true;
        NonNullList<ItemStack> copy = NonNullList.withSize(BIN_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < BIN_SIZE; i++) copy.set(i, bin.getItem(i).copy());
        boolean discard = flag(DISCARD_JUNK);
        for (ItemStack s : loot) if (!insert(copy, s, discard, bin.getMaxStackSize()).isEmpty()) return false;
        return true;
    }

    /**
     * Puts a copy of {@code stack} into {@code slots}, onto like stacks first. While junk is thrown away, junk is
     * kept only up to {@link #JUNK_KEEP} items in all and the rest (or what finds no room) is gone; otherwise the
     * part that did not fit comes back.
     */
    private static ItemStack insert(NonNullList<ItemStack> slots, ItemStack stack, boolean discardJunk, int containerMax) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = stack.copy();
        boolean junk = discardJunk && BorerMining.isJunk(s);
        if (junk) {
            int room = JUNK_KEEP - junkCount(slots);
            if (room <= 0) return ItemStack.EMPTY;
            if (s.getCount() > room) s.setCount(room);
        }
        int max = Math.min(containerMax, s.getMaxStackSize());
        for (int i = 0; i < slots.size() && !s.isEmpty(); i++) {
            ItemStack t = slots.get(i);
            if (t.isEmpty() || !ItemStack.isSameItemSameComponents(t, s)) continue;
            int move = Math.min(s.getCount(), max - t.getCount());
            if (move > 0) { t.grow(move); s.shrink(move); }
        }
        for (int i = 0; i < slots.size() && !s.isEmpty(); i++)
            if (slots.get(i).isEmpty()) slots.set(i, s.split(Math.min(max, s.getCount())));
        return junk ? ItemStack.EMPTY : s;
    }

    private static int junkCount(List<ItemStack> slots) {
        int n = 0;
        for (ItemStack s : slots) if (BorerMining.isJunk(s)) n += s.getCount();
        return n;
    }

    /** Uses up one junk item from the bin as lining material. */
    private boolean takeJunk() {
        for (int i = 0; i < BIN_SIZE; i++) {
            ItemStack s = bin.getItem(i);
            if (!BorerMining.isJunk(s)) continue;
            s.shrink(1);
            if (s.isEmpty()) bin.setItem(i, ItemStack.EMPTY); else bin.setChanged();
            return true;
        }
        return false;
    }

    /** Item entities around the machine go into the bin, as far as there is room. */
    private void suck(ServerLevel level, AABB box) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty())) {
            ItemStack s = e.getItem();
            ItemStack left = store(s);
            if (left.isEmpty()) e.discard();
            else if (left.getCount() != s.getCount()) e.setItem(left);
        }
    }

    // ---- energy ----

    /** Tops the charge up to {@code need} by burning fuel from the bin, one item at a time. False when there is nothing left to burn. */
    private boolean ensureEnergy(ServerLevel level, int need) {
        if (flag(CREATIVE)) { setEnergy(CAPACITY); return true; }
        if (need > CAPACITY) return false;
        while (energy() < need) if (!burnOne(level)) return false;
        return true;
    }

    private void spend(int fe) { if (!flag(CREATIVE)) setEnergy(energy() - fe); }

    /** Burns one fuel item from the bin for {@code burn time × 10} FE; a crafting remainder (the empty bucket) goes back in. */
    private boolean burnOne(ServerLevel level) {
        FuelValues fuel = level.fuelValues();
        for (int i = 0; i < BIN_SIZE; i++) {
            ItemStack s = bin.getItem(i);
            if (s.isEmpty()) continue;
            int burn = fuel.burnDuration(s);
            if (burn <= 0) continue;
            ItemStack remainder = s.getItem().getCraftingRemainder().copy();
            s.shrink(1);
            if (s.isEmpty()) bin.setItem(i, ItemStack.EMPTY);
            setEnergy((int) Math.min(CAPACITY, energy() + burn * 10L));
            if (!remainder.isEmpty()) {
                ItemStack left = insert(bin.getItems(), remainder, false, bin.getMaxStackSize());
                if (!left.isEmpty()) spawnAtLocation(level, left);
            }
            bin.setChanged();
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 3.1, getZ(), 4, 0.3, 0.1, 0.3, 0.01);
            return true;
        }
        return false;
    }

    // ---- save ----
    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        base = in.read("Base", BlockPos.CODEC).orElse(BlockPos.containing(getX(), getY() + 1e-3, getZ()));
        int d = in.getIntOr("Facing", -1);
        dir = d >= 0 ? Direction.from2DDataValue(d) : Direction.fromYRot(getYRot());
        owner = in.read("Owner", UUIDUtil.CODEC).orElse(null);
        setEnergy(in.getIntOr("Energy", START_ENERGY));
        entityData.set(FLAGS, in.getIntOr("Flags", DISCARD_JUNK | LINING | VEINS | LIGHTS) & ~(BORING | MOVING));
        setSlope(in.getIntOr("Slope", 0));
        distance = in.getIntOr("Distance", 32);
        setAutoLeft(in.getIntOr("AutoLeft", distance));
        int st = in.getIntOr("Status", S_IDLE);
        setStatus(st == S_BORING || st == S_TURNING || st == S_REVERSING ? S_IDLE : Math.max(0, Math.min(STATUS_KEYS.length - 1, st)));
        setDug(in.getIntOr("Dug", 0));
        setOres(in.getIntOr("Ores", 0));
        bin.clearContent();
        ContainerHelper.loadAllItems(in, bin.getItems());
        bin.setChanged();
        op = Op.NONE;                                             // a step in progress is dropped
        cutting = null;
        placed = true;
        snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, dir.toYRot(), 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("Base", BlockPos.CODEC, base);
        out.putInt("Facing", dir.get2DDataValue());
        out.storeNullable("Owner", UUIDUtil.CODEC, owner);
        out.putInt("Energy", energy());
        out.putInt("Flags", flags() & ~(BORING | MOVING));
        out.putInt("Slope", slope());
        out.putInt("Distance", distance);
        out.putInt("AutoLeft", autoLeft());
        out.putInt("Status", status());
        out.putInt("Dug", dug());
        out.putInt("Ores", ores());
        ContainerHelper.saveAllItems(out, bin.getItems());
    }
}
