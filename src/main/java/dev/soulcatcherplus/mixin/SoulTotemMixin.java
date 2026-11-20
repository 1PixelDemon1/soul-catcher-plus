// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.item.SoulItem;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.stats.Stats;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class SoulTotemMixin {
    @Inject(method = {"checkTotemDeathProtection", "func_190628_d"}, at = @At("RETURN"), cancellable = true, remap = false)
    private void soulcatcherplus$trySoulTotem(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() || source.isBypassInvul() || !((Object)this instanceof ServerPlayerEntity player)) return;
        for (Hand hand : Hand.values()) {
            ItemStack totem = player.getItemInHand(hand);
            if (totem.getItem() != SoulCatcherPlus.SOUL_SACRIFICE_TOTEM.get()) continue;
            if (SoulCages.spendEquipment(player, SoulItem.SACRIFICE_COST).isEmpty()) return;
            player.setHealth(1);
            player.removeAllEffects();
            player.addEffect(new EffectInstance(Effects.REGENERATION, 900, 1));
            player.addEffect(new EffectInstance(Effects.ABSORPTION, 100, 1));
            player.addEffect(new EffectInstance(Effects.FIRE_RESISTANCE, 800));
            player.awardStat(Stats.ITEM_USED.get(totem.getItem()));
            CriteriaTriggers.USED_TOTEM.trigger(player, totem);
            player.getLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1,
                player.getZ(), 30, .35, .5, .35, .15);
            player.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE,
                SoundCategory.PLAYERS, 1, 1);
            SoulNetwork.showSacrificeTotem(player);
            callback.setReturnValue(true);
            return;
        }
    }
}
