package com.heliosphere.rocketry.menu;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Planets;
import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.entity.RocketEntity;
import com.heliosphere.rocketry.entity.RocketStructure;
import com.heliosphere.rocketry.flight.LandingSites;
import com.heliosphere.rocketry.launch.LaunchConsoleBlockEntity;
import com.heliosphere.rocketry.launch.MissionControl;
import com.heliosphere.rocketry.launch.RocketScanner;
import com.heliosphere.rocketry.net.RocketNet;
import com.heliosphere.rocketry.part.PartBlockEntity;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Launch control: mission analysis, destination and landing site, refuelling, launch. */
public class RocketMenu extends AbstractContainerMenu {
    public record Data(BlockPos console, int entityId) {
        public static final StreamCodec<io.netty.buffer.ByteBuf, Data> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Data::console, ByteBufCodecs.VAR_INT, Data::entityId, Data::new);
    }

    public static ExtendedScreenHandlerType<RocketMenu, Data> TYPE;

    public static void init() {
        TYPE = Registry.register(BuiltInRegistries.MENU, Heliosphere.id("rocket"), new ExtendedScreenHandlerType<>(RocketMenu::new, Data.CODEC));
    }

    public final Data data;
    private final Container fuel;
    private int timer;

    public RocketMenu(int id, Inventory inv, Data d) { this(id, inv, new SimpleContainer(9), d); }

    public RocketMenu(int id, Inventory inv, Container fuel, Data d) {
        super(TYPE, id);
        this.data = d;
        this.fuel = fuel;
        for (int i = 0; i < 9; i++) addSlot(new Slot(fuel, i, 110 + i * 18, 110));
        addStandardInventorySlots(inv, 110, 136);
        if (!inv.player.level().isClientSide()) owners.add(inv.player);
    }

    public boolean isEntity() { return data.entityId() >= 0; }

    @Override
    public boolean stillValid(Player p) {
        if (isEntity()) {
            return p.level().getEntity(data.entityId()) instanceof RocketEntity r && r.isAlive() && r.distanceToSqr(p) < 400;
        }
        return p.level().getBlockState(data.console()).is(Rocketry.LAUNCH_CONSOLE) && p.distanceToSqr(data.console().getCenter()) < 64;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (isEntity() && !p.level().isClientSide()) clearContainer(p, fuel);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        ItemStack copy = st.copy();
        if (index < 9) { if (!moveItemStackTo(st, 9, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(st, 0, 9, false)) return ItemStack.EMPTY;
        if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    // ------------------------------------------------------------------ server side

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (++timer % 10 == 1) {
            for (Player p : List.copyOf(owners)) if (p instanceof ServerPlayer sp) sendReport(sp);
        }
    }

    private final List<Player> owners = new ArrayList<>();

    public void addOwner(Player p) { if (!owners.contains(p)) owners.add(p); }

    private record Target(RocketStructure structure, CelestialBody from, String dest, String site, boolean landed) {}

    private Target target(ServerPlayer p) {
        CelestialBody from = Planets.bodyOf(p.level());
        if (isEntity()) {
            if (!(p.level().getEntity(data.entityId()) instanceof RocketEntity r)) return null;
            return new Target(r.structure(), from, r.destination(), r.site(), true);
        }
        if (!(p.level().getBlockEntity(data.console()) instanceof LaunchConsoleBlockEntity be)) return null;
        RocketScanner.Scan scan = RocketScanner.scan(p.level(), data.console());
        if (be.destination().isEmpty()) be.setDestination(MissionControl.defaultDestination(from));
        return new Target(scan.structure(), from, be.destination(), be.site(), false);
    }

    public void sendReport(ServerPlayer p) {
        addOwner(p);
        Target t = target(p);
        if (t == null) return;
        List<String> lines = new ArrayList<>();
        List<Integer> sev = new ArrayList<>();
        List<String> stats = new ArrayList<>();
        boolean can = false;
        if (t.structure.parts().isEmpty()) {
            lines.add(isEntity() ? "火箭为空" : "发射台上没有火箭部件（部件需与发射台顶面相连）");
            sev.add(2);
        } else {
            RocketAnalyzer.Report r = MissionControl.analyze(t.structure, t.from, t.dest);
            for (RocketAnalyzer.Check c : r.checks) { lines.add(c.text()); sev.add(c.severity().ordinal()); }
            stats.add(String.format("部件 %d · 总质量 %.1f 吨 · %d 级", r.partCount, r.totalMass, r.stages.size()));
            stats.add(String.format("Δv %.0f / %.0f m/s · 推重比 %.2f", r.totalDeltaV, r.requiredDeltaV, r.twr));
            for (RocketAnalyzer.Stage s : r.stages)
                stats.add(String.format("第%d级 %.1ft 推进剂%.1ft 推力%.0fkN Isp%.0fs Δv%.0f", s.index, s.massFull, s.propellantUsable, s.thrustVac, s.ispVac, s.deltaV));
            can = !r.hasFail();
        }
        List<String> dests = new ArrayList<>();
        for (String d : MissionControl.destinations(t.from)) {
            CelestialBody b = SolarSystem.get(d);
            dests.add(d + "|" + (t.from == b ? b.nameZh + "（亚轨道）" : b.nameZh));
        }
        List<String> sites = new ArrayList<>();
        if (t.from == null || !t.dest.equals(t.from.id)) for (LandingSites.Site s : LandingSites.of(t.dest)) sites.add(s.id() + "|" + s.nameZh() + "|" + s.hintZh());
        String site = t.site.isEmpty() && !sites.isEmpty() ? sites.get(0).split("\\|")[0] : t.site;
        ServerPlayNetworking.send(p, new RocketNet.Report(lines, sev, stats, dests, t.dest, sites, site, MissionControl.fuelSummary(t.structure), t.landed, can));
    }

    public void handle(ServerPlayer p, int action, String arg) {
        ServerLevel level = p.level();
        switch (action) {
            case RocketNet.SELECT_DEST -> {
                if (SolarSystem.get(arg) == null) return;
                if (isEntity() && level.getEntity(data.entityId()) instanceof RocketEntity r) r.setDestination(arg);
                else if (level.getBlockEntity(data.console()) instanceof LaunchConsoleBlockEntity be) { be.setDestination(arg); be.setSite(""); }
            }
            case RocketNet.SELECT_SITE -> {
                if (isEntity() && level.getEntity(data.entityId()) instanceof RocketEntity r) r.setSite(arg);
                else if (level.getBlockEntity(data.console()) instanceof LaunchConsoleBlockEntity be) be.setSite(arg);
            }
            case RocketNet.FUEL -> refuel(p);
            case RocketNet.LAUNCH, RocketNet.FORCE_LAUNCH -> launch(p, action == RocketNet.FORCE_LAUNCH);
            case RocketNet.DISASSEMBLE -> {
                if (isEntity() && level.getEntity(data.entityId()) instanceof RocketEntity r && r.phase() == RocketEntity.LANDED) {
                    p.closeContainer();
                    r.disassemble(level);
                }
            }
            default -> {}
        }
        sendReport(p);
    }

    private void refuel(ServerPlayer p) {
        ServerLevel level = p.level();
        if (isEntity()) {
            if (level.getEntity(data.entityId()) instanceof RocketEntity r) r.setStructure(MissionControl.refuel(r.structure(), fuel, "empty_canister"));
            return;
        }
        RocketScanner.Scan scan = RocketScanner.scan(level, data.console());
        RocketStructure after = MissionControl.refuel(scan.structure(), fuel, "empty_canister");
        for (int i = 0; i < scan.parts().size(); i++) {
            if (level.getBlockEntity(scan.parts().get(i)) instanceof PartBlockEntity be) be.setFill(after.parts().get(i).fill());
        }
    }

    private void launch(ServerPlayer p, boolean force) {
        ServerLevel level = p.level();
        CelestialBody from = Planets.bodyOf(level);
        if (from == null) { p.displayClientMessage(Component.translatable("message.heliosphere.cannot_launch_here"), false); return; }
        Target t = target(p);
        if (t == null || t.structure.parts().isEmpty()) return;
        RocketAnalyzer.Report r = MissionControl.analyze(t.structure, from, t.dest);
        if (r.hasFail() && !force) { p.displayClientMessage(Component.translatable("message.heliosphere.checks_failed"), false); return; }
        String site = t.site.isEmpty() && !LandingSites.of(t.dest).isEmpty() ? LandingSites.of(t.dest).get(0).id() : t.site;
        RocketEntity rocket;
        if (isEntity()) {
            if (!(level.getEntity(data.entityId()) instanceof RocketEntity re)) return;
            rocket = re;
        } else {
            RocketScanner.Scan scan = RocketScanner.scan(level, data.console());
            rocket = Rocketry.ROCKET.create(level, EntitySpawnReason.TRIGGERED);
            if (rocket == null) return;
            rocket.setStructure(scan.structure());
            rocket.setPos(scan.origin().getX() + 0.5, scan.origin().getY(), scan.origin().getZ() + 0.5);
            for (BlockPos bp : scan.parts()) level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.addFreshEntity(rocket);
        }
        p.closeContainer();
        rocket.begin(p, from, t.dest, site, r, force);
    }
}
