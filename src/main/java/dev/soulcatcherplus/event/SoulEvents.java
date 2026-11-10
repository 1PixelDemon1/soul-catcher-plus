// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.event;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.api.*;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.enchantment.SoulEnchantments;
import dev.soulcatcherplus.network.SoulNetwork;
import dev.soulcatcherplus.item.SoulScroll;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulEvents {
    /** Handle creative use before vanilla restores the old stack count after Item.use. */
    @SubscribeEvent public static void creativeScrollUse(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().isCreative() || !(event.getItemStack().getItem() instanceof SoulScroll scroll)) return;
        event.setCancellationResult(scroll.use(event.getLevel(), event.getEntity(), event.getHand()).getResult());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof dev.soulcatcherplus.item.SoulScythe scythe)) return;
        net.minecraft.world.InteractionResult result;
        Entity target = event.getTarget();
        if (target instanceof net.minecraft.world.entity.boss.EnderDragonPart part) target = part.parentMob;
        if (target instanceof LivingEntity living) {
            result = scythe.interactLivingEntity(event.getItemStack(), event.getEntity(), living, event.getHand());
        } else return;
        if (result != net.minecraft.world.InteractionResult.PASS) {
            event.setCancellationResult(result); event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new SoulProfiles()); }
    private static ServerPlayer creditedPlayer(Entity source) {
        if (source instanceof ServerPlayer player) return player;
        if (source instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (source != null && CompanionBrain.isSoul(source))
            return source.getServer().getPlayerList().getPlayer(CompanionBrain.owner(source));
        return null;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(mob)) {
            SoulNetwork.dissolve(mob);
            if (!SoulMinions.isMinion(mob)) endCompanion(mob);
            return;
        }
        ServerPlayer player = creditedPlayer(event.getSource().getEntity());
        if (player == null) return;
        SoulProfiles.Profile profile = SoulProfiles.get(mob);
        SoulCages.credit(player, SoulEnchantments.victoryEnergy(player, profile.energy()));
        CompoundTag tag = mob.getPersistentData();
        long now = player.server.overworld().getGameTime();
        if (!tag.hasUUID("scp_mark_owner") || !tag.getUUID("scp_mark_owner").equals(player.getUUID())
                || tag.getLong("scp_mark_until") < now || !profile.enabled()) return;
        SoulAdapter adapter = SoulAdapters.get(mob);
        if (!adapter.canCapture(mob, player) || MinecraftForge.EVENT_BUS.post(new SoulEvent.Capture(player, mob))) return;
        try {
            SoulEntry soul = new SoulEntry(UUID.randomUUID(), mob.getType().builtInRegistryHolder().key().location().toString(),
                mob.getName().getString(), SoulSnapshots.sanitize(adapter.capture(mob)), mob.getMaxHealth());
            SoulRemnant remnant = SoulCatcherPlus.REMNANT.get().create(player.serverLevel());
            if (remnant != null) {
                remnant.initialize(player, soul); remnant.moveTo(mob.getX(), mob.getY() + 0.5, mob.getZ());
                player.serverLevel().addFreshEntity(remnant);
            }
        } catch (RuntimeException error) {
            org.apache.logging.log4j.LogManager.getLogger().error("Failed soul capture: {}", mob.getType(), error);
            SoulService.message(player, "unsupported");
        }
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        if (mob.tickCount % 5 == 0) SoulMinions.tryInherit(mob);
        if (SoulMinions.isMinion(mob)) {
            if (!SoulMinions.tick(mob)) event.setCanceled(true);
        } else if (CompanionBrain.isSoul(mob)) CompanionBrain.tick(mob);
        else if (mob.tickCount % 10 == 0 && mob.getPersistentData().hasUUID("scp_mark_owner")
                && mob.getPersistentData().getLong("scp_mark_until") >= mob.getServer().overworld().getGameTime())
            ((net.minecraft.server.level.ServerLevel) mob.level()).sendParticles(ParticleTypes.SOUL,
                mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 1, 0.1, 0.05, 0.1, 0);
    }
    @SubscribeEvent public static void constructing(EntityEvent.EntityConstructing event) {
        SoulSpawnContext.constructed(event.getEntity());
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide) return;
        SoulMinions.inheritOnJoin(mob, event.loadedFromDisk());
        if (!CompanionBrain.isSoul(mob)) return;
        if (CompanionBrain.activeEntry(mob) == null) {
            // Register a stale loaded raven's native passengers before the brain discards it.
            // Its normal remove path then releases the real cargo instead of losing saved loot.
            if (!(mob instanceof dev.soulcatcherplus.entity.SoulRavenEntity) || mob.getPassengers().isEmpty())
                event.setCanceled(true);
            return;
        }
        if (SoulMinions.isMinion(mob)) SoulMinions.configure(mob);
        else if (event.loadedFromDisk()) SoulAdapters.get(mob).configureCompanion(mob);
        SoulNames.update(mob);
        SoulBossBars.suppress(mob);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide
                || !CompanionBrain.isSoul(mob) || SoulMinions.isMinion(mob)) return;
        if (SoulSpawnContext.wasDestroyed(mob)) endCompanion(mob);
    }
    private static void endCompanion(Mob mob) {
        SoulEntry entry = CompanionBrain.activeEntry(mob);
        if (entry == null) return;
        entry.activeEntity = null; entry.dimension = "";
        entry.readyAt = mob.getServer().overworld().getGameTime() + 20 * 60;
        SoulLedger.get(mob.getServer()).setDirty();
        SoulMinions.dismiss(mob.getServer(), mob.getUUID());
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Mob mob && !mob.level().isClientSide) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof ServerPlayer player && CompanionBrain.isSoul(event.getTarget()))
            SoulNetwork.ghost(player, event.getTarget());
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SoulService.recallAll(player);
    }
    @SubscribeEvent public static void playerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SoulService.recallAll(player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (attacker instanceof Mob mob && CompanionBrain.isSoul(mob) && CompanionBrain.allied(mob, event.getEntity()))
            event.setCanceled(true);
        if (attacker != null && event.getEntity() instanceof Mob mob && CompanionBrain.isSoul(mob)
                && CompanionBrain.allied(mob, attacker)) event.setCanceled(true);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(event.getEntity())) { event.getDrops().clear(); event.setCanceled(true); }
    }
    @SubscribeEvent public static void xp(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(event.getEntity())) { event.setDroppedExperience(0); event.setCanceled(true); }
    }
    @SubscribeEvent public static void conversion(LivingConversionEvent.Pre event) {
        if (CompanionBrain.isSoul(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (event.getNewTarget() == null || event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (event.getNewTarget() instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof Mob mob && CompanionBrain.isSoul(mob)
                && event.getNewTarget() != null && !CompanionBrain.canAttack(mob, event.getNewTarget())) event.setCanceled(true);
    }
}
