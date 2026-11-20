// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.entity.SoulBoltEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;

public final class SoulEnergyBow extends BowItem {
    public static final int COST = 10;
    public SoulEnergyBow(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        SoulItem.costTooltip(stack, tooltip, COST);
    }

    @Override public InteractionResult use(Level world, Player player, InteractionHand hand) {
        ItemStack bow = player.getItemInHand(hand);
        boolean hasEnergy = !SoulCages.chargedEquipmentCage(player, COST).isEmpty();
        InteractionResult event = ForgeEventFactory.onArrowNock(bow, world, player, hand, hasEnergy);
        if (event != null) return event;
        if (!hasEnergy) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public boolean releaseUsing(ItemStack bow, Level world, LivingEntity user, int timeLeft) {
        if (world.isClientSide()) return true;
        if (!(user instanceof ServerPlayer player)) return false;
        int charge = ForgeEventFactory.onArrowLoose(bow, world, player, getUseDuration(bow, user) - timeLeft,
            !SoulCages.chargedEquipmentCage(player, COST).isEmpty());
        if (charge < 0) return false;
        float power = getPowerForTime(charge);
        if (power < .1f || SoulCages.chargedEquipmentCage(player, COST).isEmpty()) return false;
        SoulBoltEntity bolt = new SoulBoltEntity(player, world, bow);
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, power * 3, 1);
        bolt.setCritArrow(power == 1);
        if (!world.addFreshEntity(bolt)) return false;
        // A Forge spawn callback can change the inventory.
        if (SoulCages.spendEquipment(player, COST).isEmpty()) {
            bolt.discard();
            return false;
        }
        bolt.applyOnProjectileSpawned((net.minecraft.server.level.ServerLevel) world, new ItemStack(net.minecraft.world.item.Items.ARROW));
        bow.hurtAndBreak(1, player, player.getUsedItemHand());
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
            SoundSource.PLAYERS, 1, 1 / (world.getRandom().nextFloat() * .4f + 1.2f) + power * .5f);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }
}
