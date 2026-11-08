package com.heliosphere.rocketry.entity;

import com.heliosphere.rocketry.physics.PartSpec;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The blocks that make up a flying rocket, relative to its base centre, with each tank's load. */
public record RocketStructure(List<Part> parts) {
    public static final RocketStructure EMPTY = new RocketStructure(List.of());

    public record Part(int x, int y, int z, int stateId, String spec, float fill, float tolerance, boolean tested) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Part::x), Codec.INT.fieldOf("y").forGetter(Part::y), Codec.INT.fieldOf("z").forGetter(Part::z),
                Codec.INT.fieldOf("state").forGetter(Part::stateId), Codec.STRING.fieldOf("spec").forGetter(Part::spec),
                Codec.FLOAT.fieldOf("fill").forGetter(Part::fill), Codec.FLOAT.fieldOf("tol").forGetter(Part::tolerance),
                Codec.BOOL.fieldOf("tested").forGetter(Part::tested)).apply(i, Part::new));
        public static final StreamCodec<ByteBuf, Part> STREAM = new StreamCodec<>() {
            @Override public Part decode(ByteBuf b) {
                return new Part(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b),
                        ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.FLOAT.decode(b),
                        ByteBufCodecs.FLOAT.decode(b), ByteBufCodecs.BOOL.decode(b));
            }
            @Override public void encode(ByteBuf b, Part p) {
                ByteBufCodecs.VAR_INT.encode(b, p.x); ByteBufCodecs.VAR_INT.encode(b, p.y); ByteBufCodecs.VAR_INT.encode(b, p.z);
                ByteBufCodecs.VAR_INT.encode(b, p.stateId); ByteBufCodecs.STRING_UTF8.encode(b, p.spec); ByteBufCodecs.FLOAT.encode(b, p.fill);
                ByteBufCodecs.FLOAT.encode(b, p.tolerance); ByteBufCodecs.BOOL.encode(b, p.tested);
            }
        };

        public BlockState state() { return Block.stateById(stateId); }
        public PartSpec specObj() { return PartSpec.get(spec); }
        public Part withFill(float f) { return new Part(x, y, z, stateId, spec, f, tolerance, tested); }
    }

    public static final Codec<RocketStructure> CODEC = Part.CODEC.listOf().xmap(RocketStructure::new, RocketStructure::parts);
    public static final StreamCodec<ByteBuf, RocketStructure> STREAM =
            Part.STREAM.apply(ByteBufCodecs.list()).map(RocketStructure::new, RocketStructure::parts);

    public List<RocketAnalyzer.Placed> placed() {
        List<RocketAnalyzer.Placed> out = new ArrayList<>();
        for (Part p : parts) {
            PartSpec s = p.specObj();
            if (s != null) out.add(new RocketAnalyzer.Placed(p.x, p.y, p.z, s, p.fill, p.tested, p.tolerance));
        }
        return out;
    }

    public int minY() { int m = Integer.MAX_VALUE; for (Part p : parts) m = Math.min(m, p.y); return parts.isEmpty() ? 0 : m; }
    public int maxY() { int m = Integer.MIN_VALUE; for (Part p : parts) m = Math.max(m, p.y); return parts.isEmpty() ? 0 : m; }
    public int radius() { int r = 0; for (Part p : parts) r = Math.max(r, Math.max(Math.abs(p.x), Math.abs(p.z))); return r; }

    public double totalPropellant() { double t = 0; for (Part p : parts) { PartSpec s = p.specObj(); if (s != null && s.category == PartSpec.Category.TANK) t += p.fill; } return t; }

    /** Parts above the given layer (exclusive), i.e. what remains after jettisoning everything at or below it. */
    public RocketStructure above(int layer) {
        List<Part> l = new ArrayList<>();
        for (Part p : parts) if (p.y > layer) l.add(new Part(p.x, p.y - layer - 1, p.z, p.stateId, p.spec, p.fill, p.tolerance, p.tested));
        return new RocketStructure(l);
    }

    public RocketStructure atOrBelow(int layer) {
        List<Part> l = new ArrayList<>();
        for (Part p : parts) if (p.y <= layer) l.add(p);
        return new RocketStructure(l);
    }

    /** Seat offset: inside the crew capsule (the seated crew member is fully enclosed), or the top of the rocket. */
    public double[] seat() {
        for (Part p : parts) if ("crew_capsule".equals(p.spec)) return new double[]{p.x, p.y - 0.45, p.z};
        return new double[]{0, maxY() + 1, 0};
    }
}
