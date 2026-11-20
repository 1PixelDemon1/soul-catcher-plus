// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.companion.SoulCages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;

public final class SoulboundHoe extends Item {
    public static final int COST = 5;
    public SoulboundHoe(Properties properties) { super(properties.hoe(ToolMaterial.IRON, -2, -1)); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        SoulItem.costTooltip(stack, tooltip, COST);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var user = context.getPlayer();
        if (user == null || !user.isSecondaryUseActive()) return super.useOn(context);
        if (context.getLevel().isClientSide()) return SoulCages.chargedEquipmentCage(user, COST).isEmpty()
            ? InteractionResult.FAIL : InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer player)) return InteractionResult.FAIL;
        ItemStack cage = SoulCages.spendEquipment(player, COST);
        if (cage.isEmpty()) return InteractionResult.FAIL;
        boolean applied = false;
        try {
            var pos = context.getClickedPos();
            var face = context.getClickedFace();
            ItemStack meal = new ItemStack(Items.BONE_MEAL);
            applied = BoneMealItem.applyBonemeal(meal, player.level(), pos, player);
            if (!applied && player.level().getBlockState(pos).isFaceSturdy(player.level(), pos, face)) {
                pos = pos.relative(face);
                applied = BoneMealItem.growWaterPlant(meal, player.level(), pos, face);
            }
            if (!applied) return InteractionResult.FAIL;
            ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(ParticleTypes.SOUL, pos.getX() + .5, pos.getY() + .58, pos.getZ() + .5,
                9, .28, .22, .28, .025);
            context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            return InteractionResult.CONSUME;
        } finally {
            if (!applied) {
                SoulCage.setEnergy(cage, SoulCage.energy(cage) + COST);
                player.getInventory().setChanged();
            }
        }
    }
}
