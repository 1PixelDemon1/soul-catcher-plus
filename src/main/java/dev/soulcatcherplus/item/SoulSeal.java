// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.data.SoulSnapshots;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;

public final class SoulSeal extends Item {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    public SoulSeal(Properties properties) { super(properties); }

    @Override public ActionResultType interactLivingEntity(ItemStack stack, PlayerEntity player, LivingEntity target, Hand hand) {
        if (!(target instanceof MobEntity mob) || !mob.isAlive() || player.isSpectator()) return ActionResultType.PASS;
        if (player.level.isClientSide) return ActionResultType.SUCCESS;
        if (!(player instanceof ServerPlayerEntity owner)) return ActionResultType.FAIL;
        var data = SoulService.data(owner);
        var tag = mob.getPersistentData();
        var entry = tag.hasUUID(CompanionBrain.ENTRY) ? data.entries.get(tag.getUUID(CompanionBrain.ENTRY)) : null;
        if (entry == null || SoulService.activeMob(owner, entry) != mob) {
            SoulService.message(owner, "seal_requires_companion");
            return ActionResultType.FAIL;
        }
        try {
            var snapshot = SoulSnapshots.sanitize(SoulAdapters.get(mob).capture(mob));
            if (!entry.updateSnapshot(snapshot, mob.getMaxHealth())) {
                SoulService.message(owner, "seal_unchanged");
                return ActionResultType.FAIL;
            }
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to update soul snapshot for {}", mob.getType(), error);
            SoulService.message(owner, "unsupported");
            return ActionResultType.FAIL;
        }
        SoulService.changed(owner);
        if (!owner.isCreative()) stack.shrink(1);
        owner.getLevel().sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY(0.65), mob.getZ(), 12, .25, .25, .25, .02);
        SoulService.message(owner, "seal_applied");
        return ActionResultType.CONSUME;
    }
}
