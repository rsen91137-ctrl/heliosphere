package com.heliosphere.rocketry.client;

import com.heliosphere.core.Heliosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The flight model of crewed rockets: the players' own rocket design, captured block by block from their world
 * (surface blocks only) and drawn at full size: 121 blocks tall. The booster below the copper interstage and the ship
 * above it are drawn separately so staging looks right.
 */
public final class RocketSkin {
    public record Voxel(float x, int y, float z, BlockState state) {}

    public static final float SCALE = 1.0f;
    private static List<Voxel> voxels;
    private static int height = 1, split = 0;

    private RocketSkin() {}

    public static synchronized List<Voxel> get() {
        if (voxels != null) return voxels;
        voxels = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(Heliosphere.id("rocket_skins/default.txt")).openAsReader())) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) {
                    for (String tok : line.split(" ")) {
                        if (tok.startsWith("height=")) height = Integer.parseInt(tok.substring(7));
                        if (tok.startsWith("split=")) split = Integer.parseInt(tok.substring(6));
                    }
                    continue;
                }
                String[] p = line.split(" ", 4);
                if (p.length < 4) continue;
                try {
                    BlockState st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, p[3], false).blockState();
                    voxels.add(new Voxel(Float.parseFloat(p[0]), Integer.parseInt(p[1]), Float.parseFloat(p[2]), st));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ex) {
            Heliosphere.LOG.warn("Rocket skin unavailable: {}", ex.toString());
        }
        return voxels;
    }

    public static int height() { get(); return height; }
    public static int split() { get(); return split; }
}
