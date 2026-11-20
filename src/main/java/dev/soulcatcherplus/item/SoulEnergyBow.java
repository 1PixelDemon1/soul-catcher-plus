// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.entity.SoulBoltEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

public final class SoulEnergyBow extends BowItem {
    public static final int COST = 10;
    public SoulEnergyBow(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, World world, java.util.List<net.minecraft.util.text.ITextComponent> tooltip,
            net.minecraft.client.util.ITooltipFlag flag) {
        SoulItem.costTooltip(stack, tooltip, COST);
    }

    @Override public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack bow = player.getItemInHand(hand);
        boolean hasEnergy = !SoulCages.chargedEquipmentCage(player, COST).isEmpty();
        ActionResult<ItemStack> event = ForgeEventFactory.onArrowNock(bow, world, player, hand, hasEnergy);
        if (event != null) return event;
        if (!hasEnergy) return ActionResult.fail(bow);
        player.startUsingItem(hand);
        return ActionResult.consume(bow);
    }

    @Override public void releaseUsing(ItemStack bow, World world, LivingEntity user, int timeLeft) {
        if (!(user instanceof ServerPlayerEntity player)) return;
        int charge = ForgeEventFactory.onArrowLoose(bow, world, player, getUseDuration(bow) - timeLeft,
            !SoulCages.chargedEquipmentCage(player, COST).isEmpty());
        if (charge < 0) return;
        float power = getPowerForTime(charge);
        if (power < .1f || SoulCages.chargedEquipmentCage(player, COST).isEmpty()) return;
        SoulBoltEntity bolt = new SoulBoltEntity(player, world);
        bolt.shootFromRotation(player, player.xRot, player.yRot, 0, power * 3, 1);
        bolt.setCritArrow(power == 1);
        int damage = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (damage > 0) bolt.setBaseDamage(bolt.getBaseDamage() + damage * .5 + .5);
        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punch > 0) bolt.setKnockback(punch);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) bolt.setSecondsOnFire(100);
        if (!world.addFreshEntity(bolt)) return;
        // A Forge spawn callback can change the inventory.
        if (SoulCages.spendEquipment(player, COST).isEmpty()) {
            bolt.remove();
            return;
        }
        bow.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
            SoundCategory.PLAYERS, 1, 1 / (random.nextFloat() * .4f + 1.2f) + power * .5f);
        player.awardStat(Stats.ITEM_USED.get(this));
    }
}
