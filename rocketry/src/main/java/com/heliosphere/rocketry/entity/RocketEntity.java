package com.heliosphere.rocketry.entity;

import com.heliosphere.core.Reg;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.world.PlanetGeometry;
import com.heliosphere.rocketry.Rocketry;
import com.heliosphere.rocketry.flight.LandingSites;
import com.heliosphere.rocketry.launch.MissionControl;
import com.heliosphere.rocketry.menu.RocketMenu;
import com.heliosphere.rocketry.net.RocketNet;
import com.heliosphere.rocketry.physics.PartSpec;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * A flying (or landed) rocket. It carries the captured block structure and runs the mission:
 * countdown, ascent with staging, failures, transfer through space, entry, descent and landing.
 */
public class RocketEntity extends Entity implements ExtendedScreenHandlerFactory<RocketMenu.Data> {
    public static final EntityDataSerializer<RocketStructure> STRUCTURE_SERIALIZER = EntityDataSerializer.forValueType(RocketStructure.STREAM);
    private static final EntityDataAccessor<RocketStructure> STRUCTURE = SynchedEntityData.defineId(RocketEntity.class, STRUCTURE_SERIALIZER);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);

    public static final int LANDED = 0, COUNTDOWN = 1, ASCENT = 2, TUMBLE = 3, FALLING = 4, TRANSIT = 5, DESCENT = 6, DEBRIS = 7, SUBORBITAL = 8;

    private int ticks;
    private String from = "earth", to = "moon", site = "";
    private RocketAnalyzer.Failure failure = RocketAnalyzer.Failure.NONE;
    private int failTick = -1;
    private boolean escapeTower, parachute, staged;
    private double missionDv;
    private int transitTicks = 400;
    private double transitDays;
    private BlockPos home;
    private String homeDim = "minecraft:overworld";
    private UUID crew;
    private boolean scienceGiven;
    private double vx, vy;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STRUCTURE, RocketStructure.EMPTY);
        b.define(PHASE, LANDED);
        b.define(PITCH, 0f);
        b.define(THROTTLE, 0f);
    }

    public RocketStructure structure() { return entityData.get(STRUCTURE); }
    public void setStructure(RocketStructure s) { entityData.set(STRUCTURE, s); refreshDimensions(); }
    public int phase() { return entityData.get(PHASE); }
    void setPhase(int p) { entityData.set(PHASE, p); ticks = 0; }
    public float pitchDeg() { return entityData.get(PITCH); }
    public float throttle() { return entityData.get(THROTTLE); }
    public String from() { return from; }
    public String destination() { return to; }
    public String site() { return site; }
    public void setDestination(String d) { to = d; site = ""; }
    public void setSite(String s) { site = s; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STRUCTURE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        RocketStructure s = structure();
        float w = Math.max(1, s.radius() * 2 + 1);
        float h = Math.max(1, s.maxY() - Math.min(0, s.minY()) + 1);
        return EntityDimensions.fixed(w, h);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        in.read("structure", RocketStructure.CODEC).ifPresent(s -> entityData.set(STRUCTURE, s));
        entityData.set(PHASE, in.getIntOr("phase", LANDED));
        ticks = in.getIntOr("ticks", 0);
        from = in.getStringOr("from", "earth");
        to = in.getStringOr("to", "moon");
        site = in.getStringOr("site", "");
        failure = RocketAnalyzer.Failure.values()[Math.max(0, Math.min(RocketAnalyzer.Failure.values().length - 1, in.getIntOr("failure", 0)))];
        failTick = in.getIntOr("fail_tick", -1);
        escapeTower = in.getBooleanOr("escape", false);
        parachute = in.getBooleanOr("chute", false);
        staged = in.getBooleanOr("staged", false);
        missionDv = in.getDoubleOr("mission_dv", 0);
        transitTicks = in.getIntOr("transit", 400);
        transitDays = in.getDoubleOr("tdays", 0);
        in.read("home", BlockPos.CODEC).ifPresent(p -> home = p);
        homeDim = in.getStringOr("home_dim", "minecraft:overworld");
        in.getString("crew").ifPresent(u -> crew = UUID.fromString(u));
        scienceGiven = in.getBooleanOr("science", false);
        vx = in.getDoubleOr("vx", 0);
        vy = in.getDoubleOr("vy", 0);
        entityData.set(PITCH, in.getFloatOr("pitch", 0));
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("structure", RocketStructure.CODEC, structure());
        out.putInt("phase", phase());
        out.putInt("ticks", ticks);
        out.putString("from", from);
        out.putString("to", to);
        out.putString("site", site);
        out.putInt("failure", failure.ordinal());
        out.putInt("fail_tick", failTick);
        out.putBoolean("escape", escapeTower);
        out.putBoolean("chute", parachute);
        out.putBoolean("staged", staged);
        out.putDouble("mission_dv", missionDv);
        out.putInt("transit", transitTicks);
        out.putDouble("tdays", transitDays);
        if (home != null) out.store("home", BlockPos.CODEC, home);
        out.putString("home_dim", homeDim);
        if (crew != null) out.putString("crew", crew.toString());
        out.putBoolean("science", scienceGiven);
        out.putDouble("vx", vx);
        out.putDouble("vy", vy);
        out.putFloat("pitch", pitchDeg());
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource src, float amount) { return false; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 512 * 512; }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        double[] s = structure().seat();
        return new Vec3(s[0], s[1], s[2]);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (phase() != LANDED) return InteractionResult.PASS;
        if (!level().isClientSide()) player.openMenu(this);
        return InteractionResult.SUCCESS;
    }

    @Override public Component getDisplayName() { return Component.translatable("entity.heliosphere.rocket"); }

    @Override
    public RocketMenu.Data getScreenOpeningData(ServerPlayer player) { return new RocketMenu.Data(BlockPos.ZERO, getId()); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new RocketMenu(id, inv, new net.minecraft.world.SimpleContainer(9), new RocketMenu.Data(BlockPos.ZERO, getId()));
    }

    // ------------------------------------------------------------------ mission start

    /** Called by mission control once the player confirmed the launch. */
    public void begin(ServerPlayer pilot, CelestialBody fromBody, String dest, String siteId, RocketAnalyzer.Report report, boolean forced) {
        from = fromBody.id;
        to = dest;
        site = siteId;
        crew = pilot.getUUID();
        escapeTower = report.hasEscapeTower;
        parachute = structure().parts().stream().anyMatch(p -> "parachute_pack".equals(p.spec()));
        missionDv = report.requiredDeltaV;
        failure = forced ? report.predicted : RocketAnalyzer.Failure.NONE;
        // No random accidents: a rocket that passes every check always flies. Only forced launches can fail.
        double days = report.mission == null ? 0 : report.mission.transferDays();
        transitDays = days;
        transitTicks = transitTicksFor(days);
        if (fromBody == SolarSystem.EARTH && level().dimension() == Level.OVERWORLD) { home = blockPosition(); homeDim = "minecraft:overworld"; }
        scienceGiven = false;
        staged = false;
        vx = 0; vy = 0;
        entityData.set(PITCH, 0f);
        pilot.startRiding(this, true, false);
        setPhase(COUNTDOWN);
        say(Component.translatable("message.heliosphere.countdown"));
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) { clientEffects(); return; }
        ServerLevel level = (ServerLevel) level();
        ticks++;
        keepCrewAboard();
        switch (phase()) {
            case COUNTDOWN -> countdown();
            case ASCENT -> ascent(level);
            case TUMBLE -> tumble(level);
            case FALLING, DEBRIS -> falling(level);
            case TRANSIT -> transit(level);
            case DESCENT -> descent(level);
            case SUBORBITAL -> suborbitalReturn(level);
            default -> entityData.set(THROTTLE, 0f);
        }
    }

    private void keepCrewAboard() {
        if (crew == null || phase() == LANDED || phase() == DEBRIS) return;
        if (level().getPlayerByUUID(crew) instanceof ServerPlayer p && p.getVehicle() != this && p.isAlive()
                && p.level() == level() && p.distanceToSqr(this) < 64 * 64) p.startRiding(this, true, false);
    }

    /** Ten-second count with titles for the crew; engines light at T-3 and throttle up before release. */
    private void countdown() {
        int t = ticks;
        entityData.set(THROTTLE, t < 140 ? 0f : Math.min(0.75f, (t - 140) / 60f * 0.75f));
        if (t % 20 == 0 && t <= 180) title("§fT-" + (10 - t / 20), t >= 140 ? "§6主发动机点火" : t == 0 ? "§7发射程序启动 · 加注完毕 · 塔架撤离" : "");
        if (t == 140) level().playSound(null, blockPosition(), Rocketry.IGNITION_SOUND, SoundSource.NEUTRAL, 4f, 1f);
        if (t >= 200) {
            setPhase(ASCENT);
            title("§e起飞！", "§7" + body(to).nameZh + " · 祝一路顺风");
            say(Component.translatable("message.heliosphere.liftoff"));
            if (failure == RocketAnalyzer.Failure.PAD_EXPLOSION && failTick < 0) failTick = 8;
        }
    }

    private void title(String main, String sub) {
        for (Entity e : getPassengers()) if (e instanceof ServerPlayer p) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 30, 6));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(sub)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(main)));
        }
    }

    private void ascent(ServerLevel level) {
        entityData.set(THROTTLE, 1f);
        if (failure == RocketAnalyzer.Failure.NO_IGNITION) { say(Component.translatable("message.heliosphere.no_ignition")); land(level, false); return; }
        if (failure == RocketAnalyzer.Failure.NO_LIFTOFF) {
            if (ticks > 100) {
                say(Component.translatable("message.heliosphere.no_liftoff"));
                setStructure(MissionControl.burn(structure(), body(from), 2000).remaining());
                setPhase(LANDED);
            }
            return;
        }
        if (failTick >= 0 && ticks == failTick) { catastrophe(level, failure == RocketAnalyzer.Failure.PAD_EXPLOSION ? "message.heliosphere.pad_explosion" : "message.heliosphere.breakup"); return; }
        if (failure == RocketAnalyzer.Failure.TUMBLE && ticks == 50) { say(Component.translatable("message.heliosphere.tumble")); setPhase(TUMBLE); return; }
        if (failure == RocketAnalyzer.Failure.BREAKUP && ticks == 110) { catastrophe(level, "message.heliosphere.breakup"); return; }
        if (failure == RocketAnalyzer.Failure.FALL_BACK && ticks == 150) { say(Component.translatable("message.heliosphere.out_of_fuel")); setPhase(FALLING); return; }

        // real launches crawl off the pad and keep accelerating as the propellant burns away
        entityData.set(THROTTLE, Math.min(1f, 0.75f + ticks / 200f));
        vy = Math.min(3.2, vy + 0.0004 + 0.0000085 * ticks);
        if (ticks > 200) {
            float pitch = Math.min(22f, (ticks - 200) * 0.06f);
            entityData.set(PITCH, pitch);
            vx = vy * Math.tan(Math.toRadians(pitch));
        }
        setPos(getX() + vx, getY() + vy, getZ());
        if (!staged && ticks == 430) jettisonFirstStage(level);
        if (ticks == 300) title("§fMax-Q", "§7最大动压");
        if (getY() > level.getMaxY() - 8 || ticks > 900) reachSpace(level);
    }

    private void jettisonFirstStage(ServerLevel level) {
        staged = true;
        RocketAnalyzer.Report r = RocketAnalyzer.analyze(structure().placed(), body(from), body(from));
        if (r.stages.size() < 2) return;
        int top = Integer.MIN_VALUE;
        for (RocketAnalyzer.Placed p : r.stages.get(0).parts) top = Math.max(top, p.y());
        RocketStructure lower = structure().atOrBelow(top);
        RocketEntity debris = Rocketry.ROCKET.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (debris != null) {
            debris.setStructure(drained(lower));
            debris.setPos(getX(), getY(), getZ());
            debris.vy = vy * 0.3;
            debris.vx = vx;
            debris.entityData.set(PITCH, pitchDeg());
            debris.setPhase(DEBRIS);
            level.addFreshEntity(debris);
        }
        setStructure(structure().above(top));
        setPos(getX(), getY() + top + 1, getZ());
        level.playSound(null, blockPosition(), Rocketry.SEPARATION_SOUND, SoundSource.NEUTRAL, 4f, 1f);
        title("§f一级分离", "§7二级发动机点火");
        say(Component.translatable("message.heliosphere.staging"));
    }

    private static RocketStructure drained(RocketStructure s) {
        java.util.List<RocketStructure.Part> l = new java.util.ArrayList<>();
        for (RocketStructure.Part p : s.parts()) l.add(p.withFill(p.fill() * 0.05f));
        return new RocketStructure(l);
    }

    private void reachSpace(ServerLevel level) {
        CelestialBody fromBody = body(from);
        if (from.equals(to)) {
            say(Component.translatable("message.heliosphere.karman"));
            giveScience("science_suborbital");
            setStructure(MissionControl.burn(structure(), fromBody, missionDv).remaining());
            entityData.set(PITCH, 0f);
            vx = 0;
            vy = 0;
            setPhase(SUBORBITAL);
            return;
        }
        if (failure == RocketAnalyzer.Failure.ABORT_RETURN) {
            say(Component.translatable("message.heliosphere.abort"));
            to = from;
            site = "";
        }
        MissionControl.Burn burn = MissionControl.burn(structure(), fromBody, missionDv);
        setStructure(burn.remaining());
        ServerLevel space = level.getServer().getLevel(Planets.SPACE);
        if (space == null) { say(Component.literal("太空维度缺失")); land(level, false); return; }
        double slot = (Math.abs(getUUID().getLeastSignificantBits()) % 4096) * 1024.0;
        entityData.set(PITCH, 0f);
        Entity moved = teleport(new TeleportTransition(space, new Vec3(slot, 120, 0), Vec3.ZERO, getYRot(), 0, TeleportTransition.DO_NOTHING));
        if (moved instanceof RocketEntity r) {
            r.setPhase(TRANSIT);
            r.notifyTransit(true);
            r.say(Component.translatable("message.heliosphere.transfer", body(r.to).nameZh, String.format("%.0f", r.transitTicks / 20.0)));
        }
    }

    private void notifyTransit(boolean start) {
        if (crew != null && level().getPlayerByUUID(crew) instanceof ServerPlayer p)
            RocketNet.sendTransit(p, from, to, level().getGameTime(), start ? transitTicks : 0);
    }

    private void transit(ServerLevel level) {
        entityData.set(THROTTLE, ticks < 40 || ticks > transitTicks - 40 ? 0.6f : 0f);
        if (failure == RocketAnalyzer.Failure.SUFFOCATION && ticks % 40 == 0 && crew != null && level.getPlayerByUUID(crew) instanceof ServerPlayer p) {
            p.hurtServer(level, level.damageSources().drown(), 3f);
            p.displayClientMessage(Component.translatable("message.heliosphere.life_support_failed"), true);
        }
        if (ticks < transitTicks) return;
        if (transitDays > 0) { com.heliosphere.core.story.Story.addTravelDays(level.getServer(), transitDays); transitDays = 0; }
        CelestialBody dest = body(to);
        ResourceKey<Level> dim = Planets.dimensionOf(dest);
        ServerLevel target = dim == null ? null : level.getServer().getLevel(dim);
        if (target == null) target = level.getServer().overworld();
        BlockPos col = landingColumn(target, dest);
        target.getChunk(col);
        int ground = target.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ());
        double y = Math.min(target.getMaxY() - structure().maxY() - 4, ground + 160);
        notifyTransit(false);
        Entity moved = teleport(new TeleportTransition(target, new Vec3(col.getX() + 0.5, y, col.getZ() + 0.5), Vec3.ZERO, getYRot(), 0, TeleportTransition.DO_NOTHING));
        if (moved instanceof RocketEntity r) {
            r.setPhase(DESCENT);
            r.vy = -2.2;
            r.say(Component.translatable("message.heliosphere.arrival", dest.nameZh));
        }
    }

    private BlockPos landingColumn(ServerLevel target, CelestialBody dest) {
        LandingSites.Site s = LandingSites.get(dest.id, site);
        if (dest == SolarSystem.EARTH || s == null || Double.isNaN(s.lat())) {
            if (home != null && target.dimension().identifier().toString().equals(homeDim)) return home.offset(12, 0, 0);
            return target.getRespawnData().globalPos().pos();
        }
        PlanetGeometry geo = PlanetGeometry.of(dest);
        double x = geo.lonToX(s.lon()) + random.nextInt(40) - 20, z = geo.latToZ(s.lat()) + random.nextInt(40) - 20;
        return BlockPos.containing(x, 0, z);
    }

    private void descent(ServerLevel level) {
        CelestialBody here = body(to);
        boolean air = here != null && here.pressureBar >= 0.001;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        double alt = getY() - ground;
        if (failure == RocketAnalyzer.Failure.BURN_UP && ticks == 30) { catastrophe(level, "message.heliosphere.burn_up"); return; }
        boolean crash = failure == RocketAnalyzer.Failure.CRASH_LANDING;
        if (air && parachute && alt < 90 && alt > 80 && ticks % 5 == 0) say(Component.translatable("message.heliosphere.parachute"));
        double target = crash ? -2.0 : alt > 60 ? -1.6 : alt > 20 ? -0.6 : -0.18;
        vy += (target - vy) * (crash ? 0.02 : 0.15);
        entityData.set(THROTTLE, !crash && alt < 40 && !(air && parachute) ? 0.7f : 0f);
        setPos(getX(), getY() + vy, getZ());
        if (getY() <= ground + 0.05) {
            setPos(getX(), ground, getZ());
            if (crash) { catastrophe(level, "message.heliosphere.crash"); return; }
            if (failure == RocketAnalyzer.Failure.TIP_OVER) {
                level.explode(this, getX(), getY() + 1, getZ(), 2.5f, Level.ExplosionInteraction.TNT);
                say(Component.translatable("message.heliosphere.tip_over"));
                eject(false);
                discard();
                return;
            }
            land(level, true);
        }
    }

    private void suborbitalReturn(ServerLevel level) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        double alt = getY() - ground;
        if (ticks < 30) { vy = Math.max(-0.5, vy - 0.03); }
        else vy += ((alt > 40 ? -1.4 : -0.2) - vy) * 0.1;
        if (ticks == 40) say(Component.translatable("message.heliosphere.parachute"));
        setPos(getX(), getY() + vy, getZ());
        if (getY() <= ground + 0.05) { setPos(getX(), ground, getZ()); land(level, true); }
    }

    private void tumble(ServerLevel level) {
        entityData.set(PITCH, Math.min(120f, pitchDeg() + 4f));
        vy -= 0.08;
        vx = Math.min(1.5, vx + 0.05);
        setPos(getX() + vx, getY() + vy, getZ());
        if (hitGround(level) || ticks > 200) catastrophe(level, "message.heliosphere.crash");
    }

    private void falling(ServerLevel level) {
        entityData.set(THROTTLE, 0f);
        vy = Math.max(phase() == DEBRIS ? -0.6 : -3.0, vy - 0.08);
        if (phase() == DEBRIS) { vx *= 0.98; entityData.set(PITCH, Math.max(0f, pitchDeg() - 0.5f)); }
        setPos(getX() + vx, getY() + vy, getZ());
        if (hitGround(level) || ticks > 1200) {
            if (phase() == DEBRIS) {
                // spent stages come down under drogue chutes and wait to be recovered with the wrench
                BlockPos ground = BlockPos.containing(getX(), getY(), getZ());
                while (ground.getY() < level.getMaxY() && !level.getBlockState(ground).isAir()) ground = ground.above();
                setPos(getX(), ground.getY(), getZ());
                entityData.set(PITCH, 0f);
                vx = 0; vy = 0;
                setPhase(LANDED);
                level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.NEUTRAL, 1.5f, 0.5f);
            } else catastrophe(level, "message.heliosphere.crash");
        }
    }

    private boolean hitGround(ServerLevel level) {
        BlockPos below = BlockPos.containing(getX(), getY() - 0.2, getZ());
        return getY() < level.getMinY() + 2 || !level.getBlockState(below).isAir() && !level.getBlockState(below).liquid();
    }

    private void land(ServerLevel level, boolean success) {
        setPhase(LANDED);
        entityData.set(THROTTLE, 0f);
        entityData.set(PITCH, 0f);
        vx = 0; vy = 0;
        if (success) {
            CelestialBody here = Planets.bodyOf(level);
            from = here == null ? from : here.id;
            to = MissionControl.defaultDestination(here);
            site = "";
            say(Component.translatable("message.heliosphere.landed", here == null ? "?" : here.nameZh));
            if (here == SolarSystem.MOON) giveScience("science_moon");
            else if (here == SolarSystem.MARS) giveScience("science_mars");
        }
        eject(false);
    }

    /**
     * Real-time length of a coast: compressed from the true transfer time so the Moon takes about 10 minutes,
     * Mars about 35 minutes and nothing takes longer than an hour.
     */
    public static int transitTicksFor(double days) {
        if (days <= 0 || Boolean.getBoolean("heliosphere.fastTransit")) return 400;
        double minutes = Math.min(60, 10 * Math.pow(Math.max(days, 0.5) / 3.0, 0.28));
        return (int) Math.round(Math.max(1, minutes) * 60 * 20);
    }

    private void giveScience(String item) {
        if (scienceGiven || crew == null || !(level().getPlayerByUUID(crew) instanceof ServerPlayer p)) return;
        scienceGiven = true;
        ItemStack st = new ItemStack(Reg.get(item));
        Component name = st.getHoverName();
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.displayClientMessage(Component.translatable("message.heliosphere.science", name), false);
    }

    /** Engines or structure fail violently. The escape tower, if fitted, saves the crew. */
    private void catastrophe(ServerLevel level, String key) {
        say(Component.translatable(key));
        eject(escapeTower);
        float power = (float) Math.min(8, 3 + structure().totalPropellant() / 4.0);
        level.explode(this, getX(), getY() + 1, getZ(), power, Level.ExplosionInteraction.TNT);
        discard();
    }

    private void eject(boolean tower) {
        for (Entity e : getPassengers()) {
            e.stopRiding();
            if (tower && e instanceof ServerPlayer p) {
                p.setDeltaMovement(0.6, 1.8, 0);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 40, 0));
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 5, 4));
                p.displayClientMessage(Component.translatable("message.heliosphere.escape_tower"), false);
            }
        }
        crew = phase() == LANDED ? null : crew;
    }

    private void say(Component c) {
        for (Entity e : getPassengers()) if (e instanceof ServerPlayer p) p.displayClientMessage(c, true);
        if (crew != null && level().getPlayerByUUID(crew) instanceof ServerPlayer p && p.getVehicle() != this) p.displayClientMessage(c, true);
    }

    private static CelestialBody body(String id) { CelestialBody b = SolarSystem.get(id); return b == null ? SolarSystem.EARTH : b; }

    // ------------------------------------------------------------------ disassembly

    /** Turns a landed rocket back into blocks (keeping fuel and quality); parts that don't fit drop as items. */
    /** Recovery wrench: the whole vehicle comes back as part items. */
    public int recover(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        int n = 0;
        for (RocketStructure.Part p : structure().parts()) {
            ItemStack st = new ItemStack(p.state().getBlock());
            if (!player.getInventory().add(st)) Block.popResource(level, blockPosition(), st);
            n++;
        }
        eject(false);
        discard();
        return n;
    }

    public void disassemble(ServerLevel level) {
        BlockPos base = blockPosition();
        for (RocketStructure.Part p : structure().parts()) {
            BlockPos at = base.offset(p.x(), p.y(), p.z());
            BlockState st = p.state();
            if (level.getBlockState(at).canBeReplaced()) {
                level.setBlock(at, st, Block.UPDATE_ALL);
                if (level.getBlockEntity(at) instanceof com.heliosphere.rocketry.part.PartBlockEntity be) {
                    be.setFill(p.fill());
                    be.setQuality(p.tolerance(), p.tested());
                }
            } else {
                ItemStack drop = new ItemStack(st.getBlock());
                Block.popResource(level, at, drop);
            }
        }
        discard();
    }

    // ------------------------------------------------------------------ client visuals

    private void clientEffects() {
        float t = throttle();
        if (t <= 0.01f) return;
        RocketStructure s = structure();
        boolean capsule = false, srb = false;
        for (RocketStructure.Part p : s.parts()) { if ("crew_capsule".equals(p.spec())) capsule = true; if ("srb_segment".equals(p.spec())) srb = true; }
        if (capsule && !srb && level().dimension() != Planets.SPACE) {
            // flight model: a wide engine cluster under the 7x7 booster, a billowing ground cloud near the pad
            double pr = Math.toRadians(pitchDeg());
            int n = (int) (26 * t);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 3.2;
                double lx = Math.cos(a) * r, lz = Math.sin(a) * r, ly = -0.4 - random.nextDouble() * 1.5;
                double wx = getX() + lx * Math.cos(pr) + ly * Math.sin(pr), wy = getY() + ly * Math.cos(pr) - lx * Math.sin(pr);
                level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, wx, wy, getZ() + lz,
                        (random.nextDouble() - 0.5) * 0.3, -1.2 - random.nextDouble() * 1.2, (random.nextDouble() - 0.5) * 0.3);
            }
            for (int i = 0; i < 6; i++)
                level().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX() + (random.nextDouble() - 0.5) * 6, getY() - 2 - random.nextDouble() * 4, getZ() + (random.nextDouble() - 0.5) * 6,
                        (random.nextDouble() - 0.5) * 0.1, -0.05, (random.nextDouble() - 0.5) * 0.1);
            int ground = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) getX(), (int) getZ());
            if (getY() - ground < 40) {
                for (int i = 0; i < 14; i++) {
                    double a = random.nextDouble() * Math.PI * 2, sp = 0.25 + random.nextDouble() * 0.5;
                    level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * 4, ground + 1 + random.nextDouble() * 2, getZ() + Math.sin(a) * 4,
                            Math.cos(a) * sp, 0.02 + random.nextDouble() * 0.05, Math.sin(a) * sp);
                }
            }
            return;
        }
        double pr = Math.toRadians(pitchDeg());
        for (RocketStructure.Part p : s.parts()) {
            PartSpec spec = p.specObj();
            if (spec == null || !spec.isEngine()) continue;
            if (random.nextFloat() > t) continue;
            double lx = p.x() + 0.5 - 0.5, ly = p.y() - 0.3;
            double wx = getX() + lx * Math.cos(pr) + ly * Math.sin(pr), wy = getY() + ly * Math.cos(pr) - lx * Math.sin(pr);
            double wz = getZ() + p.z();
            level().addParticle(ParticleTypes.FLAME, wx, wy, wz, (random.nextDouble() - 0.5) * 0.2, -0.6 - random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.2);
            if (random.nextInt(3) == 0)
                level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, wx, wy - 0.5, wz, (random.nextDouble() - 0.5) * 0.3, -0.2, (random.nextDouble() - 0.5) * 0.3);
        }
    }

    public Optional<UUID> crew() { return Optional.ofNullable(crew); }
}
