package com.heliosphere.rocketry.cabin;

import com.heliosphere.rocketry.part.PartBlockEntity;
import com.heliosphere.rocketry.part.RocketPartBlock;
import com.heliosphere.rocketry.physics.Blueprints;
import com.heliosphere.rocketry.physics.RocketAnalyzer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A whole rocket packed up by the recovery wrench: every part with its place, propellant, build quality and test
 * status. Used on the ground (a launch pad) it stands the rocket back up exactly as it was.
 */
public class PackedRocketItem extends Item {
    public static PackedRocketItem ITEM;
    private static final int MAX_PARTS = 2048;

    public PackedRocketItem(Properties p) { super(p); }

    /** One part: offset from the bottom centre, block state, fill, tolerance, tested. */
    private record Part(int x, int y, int z, BlockState state, float fill, float tol, boolean tested) {}

    // ---- packing (the wrench) ---------------------------------------------------------------------------------

    /** Takes the whole rocket the clicked part belongs to off the world into one packed item; null if not a rocket. */
    public static ItemStack pack(Level level, BlockPos start) {
        if (!(level.getBlockState(start).getBlock() instanceof RocketPartBlock)) return null;
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start); seen.add(start);
        while (!q.isEmpty() && found.size() < MAX_PARTS) {
            BlockPos p = q.poll();
            found.add(p);
            for (Direction d : Direction.values()) {
                BlockPos nb = p.relative(d);
                if (seen.add(nb) && level.getBlockState(nb).getBlock() instanceof RocketPartBlock) q.add(nb);
            }
        }
        List<Part> parts = new ArrayList<>();
        for (BlockPos p : found) {
            float fill = 0, tol = 0.85f; boolean tested = false;
            if (level.getBlockEntity(p) instanceof PartBlockEntity be) { fill = be.fill(); tol = be.tolerance(); tested = be.tested(); }
            parts.add(new Part(p.getX(), p.getY(), p.getZ(), level.getBlockState(p), fill, tol, tested));
        }
        for (BlockPos p : found) level.removeBlock(p, false);
        return build(parts);
    }

    /** Packs a landed rocket (the entity form) the same way. */
    public static ItemStack pack(com.heliosphere.rocketry.entity.RocketStructure structure) {
        List<Part> parts = new ArrayList<>();
        for (var p : structure.parts()) parts.add(new Part(p.x(), p.y(), p.z(), p.state(), p.fill(), p.tolerance(), p.tested()));
        return parts.isEmpty() ? null : build(parts);
    }

    /** The packed item for these parts (any coordinates: they are stored from the bottom centre). */
    private static ItemStack build(List<Part> found) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Part p : found) {
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
        }
        int cx = Math.floorDiv(minX + maxX, 2), cz = Math.floorDiv(minZ + maxZ, 2);
        ListTag list = new ListTag();
        Map<String, Integer> counts = new HashMap<>();
        double fillSum = 0;
        for (Part p : found) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", p.x - cx); t.putInt("y", p.y - minY); t.putInt("z", p.z - cz);
            t.put("state", NbtUtils.writeBlockState(p.state));
            t.putFloat("fill", p.fill); t.putFloat("tol", p.tol); t.putBoolean("tested", p.tested);
            fillSum += p.fill;
            list.add(t);
            if (p.state.getBlock() instanceof RocketPartBlock rp) counts.merge(rp.specId(), 1, Integer::sum);
        }
        ItemStack stack = new ItemStack(ITEM);
        CompoundTag root = new CompoundTag();
        root.put("parts", list);
        String bp = blueprint(counts);
        if (bp != null) root.putString("blueprint", bp);
        root.putInt("height", maxY - minY + 1);
        root.putBoolean("fuelled", fillSum > 0.01);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        Blueprints.Blueprint b = bp == null ? null : Blueprints.get(bp);
        stack.set(DataComponents.ITEM_NAME, b != null
                ? Component.translatable("item.heliosphere.packed_rocket.named", b.nameZh())
                : Component.translatable("item.heliosphere.packed_rocket.custom", found.size()));
        return stack;
    }

    /** The blueprint whose parts are exactly these, if any (the rocket is then shown by its name). */
    private static String blueprint(Map<String, Integer> counts) {
        for (Blueprints.Blueprint b : Blueprints.all()) {
            Map<String, Integer> want = new HashMap<>();
            for (RocketAnalyzer.Placed p : b.parts(true)) want.merge(p.spec().id, 1, Integer::sum);
            if (want.equals(counts)) return b.id();
        }
        return null;
    }

    // ---- unpacking ------------------------------------------------------------------------------------------------

    private static List<Part> parts(Level level, ItemStack stack) {
        List<Part> out = new ArrayList<>();
        var d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return out;
        CompoundTag root = d.copyTag();
        for (var e : root.getListOrEmpty("parts")) {
            if (!(e instanceof CompoundTag t)) continue;
            BlockState st = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), t.getCompoundOrEmpty("state"));
            if (!(st.getBlock() instanceof RocketPartBlock)) continue;
            out.add(new Part(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0), st,
                    t.getFloatOr("fill", 0f), t.getFloatOr("tol", 0.85f), t.getBooleanOr("tested", false)));
        }
        return out;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos base = level.getBlockState(ctx.getClickedPos()).canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().above();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        List<Part> parts = parts(level, ctx.getItemInHand());
        if (parts.isEmpty()) return InteractionResult.FAIL;
        for (Part p : parts) {
            BlockPos at = base.offset(p.x, p.y, p.z);
            if (!level.getBlockState(at).canBeReplaced() || level.isOutsideBuildHeight(at)) {
                if (player != null) player.displayClientMessage(Component.translatable("message.heliosphere.no_room"), true);
                return InteractionResult.FAIL;
            }
        }
        for (Part p : parts) {
            BlockPos at = base.offset(p.x, p.y, p.z);
            level.setBlock(at, p.state, Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof PartBlockEntity be) {
                be.setFill(p.fill);
                be.setQuality(p.tol, p.tested);
            }
        }
        level.playSound(null, base, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
        if (player != null) player.displayClientMessage(Component.translatable("message.heliosphere.packed_rocket_placed", parts.size()), true);
        if (player == null || !player.isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        CompoundTag root = d.copyTag();
        Blueprints.Blueprint b = Blueprints.get(root.getStringOr("blueprint", ""));
        if (b != null) out.accept(Component.literal(b.descZh()).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.heliosphere.packed_rocket.size", root.getListOrEmpty("parts").size(), root.getIntOr("height", 0)).withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable(root.getBooleanOr("fuelled", false) ? "tooltip.heliosphere.packed_rocket.fuelled" : "tooltip.heliosphere.packed_rocket.empty").withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("tooltip.heliosphere.packed_rocket.use").withStyle(ChatFormatting.YELLOW));
    }
}
