// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Inherits vanilla sword enchantment eligibility and material enchantability. */
public final class SoulScythe extends SwordItem {
    private final ScytheTier scytheTier;
    public SoulScythe(ScytheTier tier) {
        super(tier.material, tier.damageModifier, tier.speedModifier,
            tier.material == Tiers.NETHERITE ? new Item.Properties().fireResistant() : new Item.Properties());
        this.scytheTier = tier;
    }
    public int summonLimit() { return scytheTier.summonLimit; }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        return scytheTier == ScytheTier.SUPREME ? ingredient.getItem() == Items.BONE : super.isValidRepairItem(stack, ingredient);
    }
    /** Main-hand scythes take precedence; another weapon does not revoke existing summons. */
    public static SoulScythe heldBy(Player player) {
        if (player.getMainHandItem().getItem() instanceof SoulScythe scythe) return scythe;
        return player.getOffhandItem().getItem() instanceof SoulScythe scythe ? scythe : null;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            SoulMinions.tryInherit(mob);
            PlayerSouls collection = SoulService.data(server);
            if (CompanionBrain.isSoul(mob)) {
                if (CompanionBrain.owner(mob).equals(server.getUUID())) {
                    collection.selected = mob.getPersistentData().getUUID(CompanionBrain.ENTRY);
                    collection.commandGroup = false;
                    SoulService.changed(server);
                    SoulService.message(server, "selected", mob.getName());
                }
            } else if (collection.commandMode) {
                SoulService.attack(server, mob);
                SoulService.message(server, "attack", mob.getName());
            } else {
                if (!SoulCages.active(server).present()) {
                    SoulService.message(server, "cage_needed"); return InteractionResult.FAIL;
                }
                if (!SoulProfiles.get(mob).enabled() || !SoulAdapters.get(mob).canCapture(mob, server)) {
                    SoulService.message(server, "unsupported"); return InteractionResult.FAIL;
                }
                CompoundTag data = mob.getPersistentData();
                long now = server.server.overworld().getGameTime();
                if (data.hasUUID("scp_mark_owner") && data.getLong("scp_mark_until") > now
                        && !data.getUUID("scp_mark_owner").equals(server.getUUID())) {
                    SoulService.message(server, "marked_other"); return InteractionResult.FAIL;
                }
                data.putUUID("scp_mark_owner", server.getUUID());
                data.putLong("scp_mark_until", now + 20 * 180);
                SoulService.message(server, "marked", mob.getName());
            }
            player.getCooldowns().addCooldown(this, 5);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.soulcatcherplus.scythe_limit", summonLimit()));
    }
}
