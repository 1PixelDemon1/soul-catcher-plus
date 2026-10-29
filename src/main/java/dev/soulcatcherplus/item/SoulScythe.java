// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.world.World;

public final class SoulScythe extends SwordItem {
    private final ScytheTier scytheTier;
    public SoulScythe(ScytheTier tier) {
        super(tier.material, tier.damageModifier, tier.speedModifier,
            tier == ScytheTier.NETHERITE ? new Item.Properties().tab(net.minecraft.item.ItemGroup.TAB_COMBAT).fireResistant() : new Item.Properties().tab(net.minecraft.item.ItemGroup.TAB_COMBAT));
        this.scytheTier = tier;
    }
    public int summonLimit() { return scytheTier.summonLimit; }
    /** Main-hand scythes take precedence; another weapon does not revoke existing summons. */
    public static SoulScythe heldBy(PlayerEntity player) {
        if (player.getMainHandItem().getItem() instanceof SoulScythe scythe) return scythe;
        return player.getOffhandItem().getItem() instanceof SoulScythe scythe ? scythe : null;
    }
    @Override public ActionResultType interactLivingEntity(ItemStack stack, PlayerEntity player, LivingEntity target, Hand hand) {
        if (!(target instanceof MobEntity mob)) return ActionResultType.PASS;
        if (player instanceof ServerPlayerEntity server) {
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
                    SoulService.message(server, "cage_needed"); return ActionResultType.FAIL;
                }
                if (!SoulProfiles.get(mob).enabled() || !SoulAdapters.get(mob).canCapture(mob, server)) {
                    SoulService.message(server, "unsupported"); return ActionResultType.FAIL;
                }
                CompoundNBT data = mob.getPersistentData();
                long now = server.server.overworld().getGameTime();
                if (data.hasUUID("scp_mark_owner") && data.getLong("scp_mark_until") > now
                        && !data.getUUID("scp_mark_owner").equals(server.getUUID())) {
                    SoulService.message(server, "marked_other"); return ActionResultType.FAIL;
                }
                data.putUUID("scp_mark_owner", server.getUUID());
                data.putLong("scp_mark_until", now + 20 * 180);
                SoulService.message(server, "marked", mob.getName());
            }
            player.getCooldowns().addCooldown(this, 5);
        }
        return ActionResultType.sidedSuccess(player.level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, World level, java.util.List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new net.minecraft.util.text.TranslationTextComponent("item.soulcatcherplus.scythe_limit", summonLimit()));
    }
}
