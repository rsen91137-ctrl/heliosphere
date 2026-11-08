package com.heliosphere.rocketry.part;

import com.heliosphere.core.env.HelioComponents;
import com.heliosphere.rocketry.Rocketry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Stores a rocket part's propellant load (tonnes) and manufacturing quality. */
public class PartBlockEntity extends BlockEntity {
    private float fill;
    private float tolerance = 0.85f;
    private boolean tested;

    public PartBlockEntity(BlockPos pos, BlockState state) { super(Rocketry.PART_BE, pos, state); }

    public RocketPartBlock block() { return (RocketPartBlock) getBlockState().getBlock(); }
    public float fill() { return fill; }
    public void setFill(float f) { fill = Math.max(0, f); setChanged(); }
    public float tolerance() { return tolerance; }
    public boolean tested() { return tested; }

    public void setQuality(float tol, boolean t) { tolerance = tol; tested = t; setChanged(); }

    @Override
    protected void applyImplicitComponents(DataComponentGetter c) {
        super.applyImplicitComponents(c);
        HelioComponents.Quality q = c.get(HelioComponents.QUALITY);
        if (q != null) { tolerance = q.tolerance(); tested = q.tested(); }
        Integer f = c.get(HelioComponents.FILL);
        if (f != null) fill = f / 1000f;
        else if (block().spec().category == com.heliosphere.rocketry.physics.PartSpec.Category.SOLID_SEGMENT) fill = (float) block().spec().capacity;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder b) {
        super.collectImplicitComponents(b);
        b.set(HelioComponents.QUALITY, new HelioComponents.Quality(tolerance, tested));
        if (fill > 0) b.set(HelioComponents.FILL, Math.round(fill * 1000));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        fill = in.getFloatOr("fill", 0);
        tolerance = in.getFloatOr("tolerance", 0.85f);
        tested = in.getBooleanOr("tested", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putFloat("fill", fill);
        out.putFloat("tolerance", tolerance);
        out.putBoolean("tested", tested);
    }
}
