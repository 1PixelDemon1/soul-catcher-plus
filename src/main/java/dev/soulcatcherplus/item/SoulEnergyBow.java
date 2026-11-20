// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.entity.SoulBoltEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;

public final class SoulEnergyBow extends BowItem {
    public static final int COST = 10;
    public SoulEnergyBow(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, Level world, java.util.List<net.minecraft.network.chat.Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        SoulItem.costTooltip(stack, tooltip, COST);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack bow = player.getItemInHand(hand);
        boolean hasEnergy = !SoulCages.chargedEquipmentCage(player, COST).isEmpty();
        InteractionResultHolder<ItemStack> event = ForgeEventFactory.onArrowNock(bow, world, player, hand, hasEnergy);
        if (event != null) return event;
        if (!hasEnergy) return InteractionResultHolder.fail(bow);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(bow);
    }

    @Override public void releaseUsing(ItemStack bow, Level world, LivingEntity user, int timeLeft) {
        if (!(user instanceof ServerPlayer player)) return;
        int charge = ForgeEventFactory.onArrowLoose(bow, world, player, getUseDuration(bow) - timeLeft,
            !SoulCages.chargedEquipmentCage(player, COST).isEmpty());
        if (charge < 0) return;
        float power = getPowerForTime(charge);
        if (power < .1f || SoulCages.chargedEquipmentCage(player, COST).isEmpty()) return;
        SoulBoltEntity bolt = new SoulBoltEntity(player, world);
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, power * 3, 1);
        bolt.setCritArrow(power == 1);
        int damage = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (damage > 0) bolt.setBaseDamage(bolt.getBaseDamage() + damage * .5 + .5);
        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punch > 0) bolt.setKnockback(punch);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) bolt.setSecondsOnFire(100);
        if (!world.addFreshEntity(bolt)) return;
        // A Forge spawn callback can change the inventory.
        if (SoulCages.spendEquipment(player, COST).isEmpty()) {
            bolt.discard();
            return;
        }
        bow.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
            SoundSource.PLAYERS, 1, 1 / (world.random.nextFloat() * .4f + 1.2f) + power * .5f);
        player.awardStat(Stats.ITEM_USED.get(this));
    }
}
