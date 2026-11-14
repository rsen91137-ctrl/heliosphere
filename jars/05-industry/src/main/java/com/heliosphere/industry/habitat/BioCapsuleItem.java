package com.heliosphere.industry.habitat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.function.Consumer;

/**
 * The bio-containment capsule, for moving species between worlds: right-click a creature to take it in (it keeps its
 * health, name, age, collar, everything), right-click the ground to let it out again, on any world. Players, bosses
 * and the machines cannot be taken. Earth animals let out on a world without air need a habitat field to live in.
 */
public class BioCapsuleItem extends Item {
    private static final String KEY = "creature", NAME = "creature_name", TYPE = "creature_type";

    public BioCapsuleItem(Properties p) { super(p); }

    public static boolean full(ItemStack st) {
        var d = st.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().contains(KEY);
    }

    private static boolean takeable(LivingEntity e) {
        if (e instanceof Player || e instanceof EnderDragon || e instanceof WitherBoss || !e.isAlive()) return false;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return !(key.getNamespace().equals("heliosphere") && e.getType().getCategory() == MobCategory.MONSTER);   // the machines stay out
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);                          // in creative the stack passed in is a copy
        if (full(held)) {
            player.displayClientMessage(Component.translatable("message.heliosphere.bio_capsule_full").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        if (!takeable(target)) {
            player.displayClientMessage(Component.translatable("message.heliosphere.bio_capsule_refused").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        target.ejectPassengers();
        target.stopRiding();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        if (!target.save(out)) return InteractionResult.FAIL;
        CompoundTag creature = out.buildResult();
        String name = target.getName().getString();
        CustomData.update(DataComponents.CUSTOM_DATA, held, t -> {
            t.put(KEY, creature);
            t.putString(NAME, name);
            t.putString(TYPE, target.getType().getDescriptionId());
        });
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        target.discard();
        player.displayClientMessage(Component.translatable("message.heliosphere.bio_capsule_taken", name).withStyle(ChatFormatting.GREEN), true);
        captured++;
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack held = ctx.getItemInHand();
        if (!full(held)) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos at = level.getBlockState(ctx.getClickedPos()).canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        CompoundTag t = held.get(DataComponents.CUSTOM_DATA).copyTag();
        CompoundTag creature = t.getCompoundOrEmpty(KEY);
        float yaw = ctx.getPlayer() == null ? 0 : ctx.getPlayer().getYRot() + 180;
        Entity e = net.minecraft.world.entity.EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), creature),
                level, EntitySpawnReason.BUCKET, x -> { x.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0); return x; });
        if (e == null) return InteractionResult.FAIL;
        if (level.getEntity(e.getUUID()) != null) e.setUUID(net.minecraft.util.Mth.createInsecureUUID(level.random));
        level.addFreshEntityWithPassengers(e);
        CustomData.update(DataComponents.CUSTOM_DATA, held, c -> { c.remove(KEY); c.remove(NAME); c.remove(TYPE); });
        if (held.get(DataComponents.CUSTOM_DATA) != null && held.get(DataComponents.CUSTOM_DATA).isEmpty()) held.remove(DataComponents.CUSTOM_DATA);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("message.heliosphere.bio_capsule_released", t.getStringOr(NAME, "?")).withStyle(ChatFormatting.GREEN), true);
            if (LifeSupport.needsAir(e) && !com.heliosphere.core.env.Environment.breathable(level, BlockPos.containing(e.getEyePosition())))
                ctx.getPlayer().displayClientMessage(Component.translatable("message.heliosphere.bio_capsule_no_air").withStyle(ChatFormatting.GOLD), false);
        }
        released++;
        return InteractionResult.SUCCESS;
    }

    /** For tests. */
    public static int captured, released;

    @Override public boolean isFoil(ItemStack st) { return full(st); }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var d = st.get(DataComponents.CUSTOM_DATA);
        if (d != null && d.copyTag().contains(KEY)) {
            CompoundTag t = d.copyTag();
            out.accept(Component.translatable("item.heliosphere.bio_capsule.holds", t.getStringOr(NAME, "?"), Component.translatable(t.getStringOr(TYPE, ""))).withStyle(ChatFormatting.AQUA));
            out.accept(Component.translatable("item.heliosphere.bio_capsule.release").withStyle(ChatFormatting.GRAY));
        } else out.accept(Component.translatable("item.heliosphere.bio_capsule.hint").withStyle(ChatFormatting.GRAY));
    }
}
