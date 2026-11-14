package com.heliosphere.industry.fun;

import com.heliosphere.core.Reg;
import net.fabricmc.api.ModInitializer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/** 老八の午餐: made from leaves. Three haunches of food, and a long while of feeling sick. */
public final class LaoBaLunch implements ModInitializer {
    public static Item LUNCH;

    @Override
    public void onInitialize() {
        Reg.tab("food");
        LUNCH = Reg.item("laoba_lunch", Lunch::new, new Item.Properties().stacksTo(16)
                .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1f).alwaysEdible().build(),
                        Consumables.defaultFood().consumeSeconds(2.4f).sound(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SLIME_SQUISH_SMALL))
                                .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                                        new MobEffectInstance(MobEffects.NAUSEA, 400, 1),
                                        new MobEffectInstance(MobEffects.HUNGER, 300, 0),
                                        new MobEffectInstance(MobEffects.SLOWNESS, 200, 0))))
                                .build()));
    }

    static final class Lunch extends Item {
        Lunch(Properties p) { super(p); }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity who) {
            if (level instanceof ServerLevel sl && who instanceof Player p) {
                p.displayClientMessage(Component.translatable("message.heliosphere.laoba_lunch").withStyle(ChatFormatting.GOLD), true);
                sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1f, 0.6f);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE, p.getX(), p.getEyeY(), p.getZ(), 12, 0.2, 0.1, 0.2, 0.02);
            }
            return super.finishUsingItem(stack, level, who);
        }

        @Override
        public void appendHoverText(ItemStack st, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable("item.heliosphere.laoba_lunch.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
