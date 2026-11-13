package com.heliosphere.rocketry.cabin.client;

import com.heliosphere.rocketry.cabin.CabinLayout;
import com.heliosphere.rocketry.entity.RocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * The cabin you walk in during launch and landing: a room of real blocks (floor, walls, glass windows, seats,
 * consoles, lockers, lamps) drawn where the rocket's crew deck is, instead of the old drawn cockpit. You look out
 * through real glass at the real world as the rocket climbs or comes down, with your own eyes and your own mouse;
 * the room matches the deck the server walks you on (LaunchCabin: x -2..2, z -7.1..1, its furniture), so walls and
 * furniture stop you where you see them.
 *
 * <p>Room frame: x across, z along the deck (+z towards the front window), y up from the deck floor; it turns
 * with the rocket's heading like the deck does.
 */
public final class RealCabin {
    private RealCabin() {}

    /** Whether the local player is walking a rocket's crew deck right now (first person). */
    /** Set while the HUD draws and the cabin is active (see CabinHudMixin). */
    public static final ThreadLocal<Boolean> HUD_PASS = ThreadLocal.withInitial(() -> false);

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof RocketEntity r)) return false;
        int phase = r.phase();
        if (phase != RocketEntity.COUNTDOWN && phase != RocketEntity.ASCENT && phase != RocketEntity.DESCENT && phase != RocketEntity.SUBORBITAL
                && phase != RocketEntity.TRANSIT) return false;
        if (!r.structure().parts().stream().anyMatch(p -> "crew_capsule".equals(p.spec()))) return false;
        // in the cabin you are always your own eyes: no outside camera, no cutscene
        if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        return true;
    }

    public static RocketEntity rocket() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof RocketEntity r ? r : null;
    }

    public static List<CabinLayout.Part> parts() { return CabinLayout.parts(); }
}
