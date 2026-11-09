// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.Level;

/** Inherits vanilla sword enchantment eligibility and material enchantability. */
public final class SoulScythe extends Item {
    private final ScytheTier scytheTier;
    public SoulScythe(ScytheTier tier, Item.Properties properties) {
        super(scytheProperties(tier,properties));
        this.scytheTier = tier;
    }
    private static Item.Properties scytheProperties(ScytheTier tier, Item.Properties properties) {
        properties.sword(tier.material,tier.damageModifier,tier.speedModifier);
        if (tier.material == ToolMaterial.NETHERITE) properties.fireResistant();
        if (tier == ScytheTier.SUPREME) properties.repairable(Items.BONE);
        return properties;
    }
    public int summonLimit() { return scytheTier.summonLimit; }
    /** Main-hand scythes take precedence; another weapon does not revoke existing summons. */
    public static SoulScythe heldBy(Player player) {
        if (player.getMainHandItem().getItem() instanceof SoulScythe scythe) return scythe;
        return player.getOffhandItem().getItem() instanceof SoulScythe scythe ? scythe : null;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // The porter's native interaction must remain usable while holding a command scythe.
        if (target instanceof dev.soulcatcherplus.entity.SoulCourierEntity) return InteractionResult.PASS;
        if (!(target instanceof Mob mob)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            SoulMinions.tryInherit(mob);
            PlayerSouls collection = SoulService.data(server);
            if (CompanionBrain.isSoul(mob)) {
                if (CompanionBrain.owner(mob).equals(server.getUUID())) {
                    collection.selected = dev.soulcatcherplus.data.SoulNbt.uuid(mob.getPersistentData(), CompanionBrain.ENTRY);
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
                long now = server.level().getServer().overworld().getGameTime();
                if (dev.soulcatcherplus.data.SoulNbt.hasUuid(data, "scp_mark_owner") && data.getLongOr("scp_mark_until", 0L) > now
                        && !dev.soulcatcherplus.data.SoulNbt.uuid(data, "scp_mark_owner").equals(server.getUUID())) {
                    SoulService.message(server, "marked_other"); return InteractionResult.FAIL;
                }
                dev.soulcatcherplus.data.SoulNbt.putUuid(data, "scp_mark_owner", server.getUUID());
                data.putLong("scp_mark_until", now + 20 * 180);
                SoulService.message(server, "marked", mob.getName());
            }
            player.getCooldowns().addCooldown(stack, 5);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("item.soulcatcherplus.scythe_limit", summonLimit()));
    }
}
