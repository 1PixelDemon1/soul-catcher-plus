// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.companion.SoulCages;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTier;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.Items;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ActionResultType;

public final class SoulboundHoe extends HoeItem {
    public static final int COST = 5;
    public SoulboundHoe(Properties properties) { super(ItemTier.IRON, -2, -1, properties); }

    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.World world,
            java.util.List<net.minecraft.util.text.ITextComponent> tooltip, net.minecraft.client.util.ITooltipFlag flag) {
        SoulItem.costTooltip(stack, tooltip, COST);
    }

    @Override public ActionResultType useOn(ItemUseContext context) {
        var user = context.getPlayer();
        if (user == null || !user.isSecondaryUseActive()) return super.useOn(context);
        if (context.getLevel().isClientSide) return SoulCages.chargedEquipmentCage(user, COST).isEmpty()
            ? ActionResultType.FAIL : ActionResultType.SUCCESS;
        if (!(user instanceof ServerPlayerEntity player)) return ActionResultType.FAIL;
        ItemStack cage = SoulCages.spendEquipment(player, COST);
        if (cage.isEmpty()) return ActionResultType.FAIL;
        boolean applied = false;
        try {
            var pos = context.getClickedPos();
            var face = context.getClickedFace();
            ItemStack meal = new ItemStack(Items.BONE_MEAL);
            applied = BoneMealItem.applyBonemeal(meal, player.level, pos, player);
            if (!applied && player.level.getBlockState(pos).isFaceSturdy(player.level, pos, face)) {
                pos = pos.relative(face);
                applied = BoneMealItem.growWaterPlant(meal, player.level, pos, face);
            }
            if (!applied) return ActionResultType.FAIL;
            player.getLevel().sendParticles(ParticleTypes.SOUL, pos.getX() + .5, pos.getY() + .58, pos.getZ() + .5,
                9, .28, .22, .28, .025);
            context.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
            return ActionResultType.CONSUME;
        } finally {
            if (!applied) {
                SoulCage.setEnergy(cage, SoulCage.energy(cage) + COST);
                player.inventory.setChanged();
            }
        }
    }
}
