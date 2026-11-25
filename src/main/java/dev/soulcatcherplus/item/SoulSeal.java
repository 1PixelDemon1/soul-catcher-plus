// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.data.SoulNbt;
import dev.soulcatcherplus.data.SoulSnapshots;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;

public final class SoulSeal extends Item {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    public SoulSeal(Properties properties) { super(properties); }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob) || !mob.isAlive() || player.isSpectator()) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer owner)) return InteractionResult.FAIL;
        var data = SoulService.data(owner);
        var tag = mob.getPersistentData();
        var entry = SoulNbt.hasUuid(tag, CompanionBrain.ENTRY) ? data.entries.get(SoulNbt.uuid(tag, CompanionBrain.ENTRY)) : null;
        if (entry == null || SoulService.activeMob(owner, entry) != mob) {
            SoulService.message(owner, "seal_requires_companion");
            return InteractionResult.FAIL;
        }
        try {
            var snapshot = SoulSnapshots.sanitize(SoulAdapters.get(mob).capture(mob));
            if (!entry.updateSnapshot(snapshot, mob.getMaxHealth())) {
                SoulService.message(owner, "seal_unchanged");
                return InteractionResult.FAIL;
            }
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to update soul snapshot for {}", mob.getType(), error);
            SoulService.message(owner, "unsupported");
            return InteractionResult.FAIL;
        }
        SoulService.changed(owner);
        if (!owner.isCreative()) stack.shrink(1);
        ((net.minecraft.server.level.ServerLevel) owner.level()).sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY(0.65), mob.getZ(), 12, .25, .25, .25, .02);
        SoulService.message(owner, "seal_applied");
        return InteractionResult.SUCCESS_SERVER;
    }
}
