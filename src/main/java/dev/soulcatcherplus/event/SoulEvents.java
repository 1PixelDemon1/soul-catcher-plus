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
import net.minecraft.particles.ParticleTypes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulEvents {
    /** Handle creative use before vanilla restores the old stack count after Item.use. */
    @SubscribeEvent public static void creativeScrollUse(PlayerInteractEvent.RightClickItem event) {
        if (!event.getPlayer().isCreative() || !(event.getItemStack().getItem() instanceof SoulScroll scroll)) return;
        event.setCancellationResult(scroll.use(event.getWorld(), event.getPlayer(), event.getHand()).getResult());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof dev.soulcatcherplus.item.SoulScythe scythe)) return;
        net.minecraft.util.ActionResultType result;
        Entity target = event.getTarget();
        if (target instanceof net.minecraft.entity.boss.dragon.EnderDragonPartEntity part) target = part.parentMob;
        if (target instanceof LivingEntity living) {
            result = scythe.interactLivingEntity(event.getItemStack(), event.getPlayer(), living, event.getHand());
        } else return;
        if (result != net.minecraft.util.ActionResultType.PASS) {
            event.setCancellationResult(result); event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new SoulProfiles()); }
    private static ServerPlayerEntity creditedPlayer(Entity source) {
        if (source instanceof ServerPlayerEntity player) return player;
        if (source instanceof MobEntity mob) SoulMinions.tryInherit(mob);
        if (source != null && CompanionBrain.isSoul(source))
            return source.getServer().getPlayerList().getPlayer(CompanionBrain.owner(source));
        return null;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof MobEntity mob) || mob.level.isClientSide) return;
        SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(mob)) {
            SoulNetwork.dissolve(mob);
            if (!SoulMinions.isMinion(mob)) endCompanion(mob);
            return;
        }
        ServerPlayerEntity player = creditedPlayer(event.getSource().getEntity());
        if (player == null) return;
        SoulProfiles.Profile profile = SoulProfiles.get(mob);
        SoulCages.credit(player, SoulEnchantments.victoryEnergy(player, profile.energy()));
        CompoundNBT tag = mob.getPersistentData();
        long now = player.server.overworld().getGameTime();
        if (!tag.hasUUID("scp_mark_owner") || !tag.getUUID("scp_mark_owner").equals(player.getUUID())
                || tag.getLong("scp_mark_until") < now || !profile.enabled()) return;
        SoulAdapter adapter = SoulAdapters.get(mob);
        if (!adapter.canCapture(mob, player) || MinecraftForge.EVENT_BUS.post(new SoulEvent.Capture(player, mob))) return;
        try {
            SoulEntry soul = new SoulEntry(UUID.randomUUID(), mob.getType().getRegistryName().toString(),
                mob.getName().getString(), SoulSnapshots.sanitize(adapter.capture(mob)), mob.getMaxHealth());
            SoulRemnant remnant = SoulCatcherPlus.REMNANT.get().create(player.getLevel());
            if (remnant != null) {
                remnant.initialize(player, soul); remnant.moveTo(mob.getX(), mob.getY() + 0.5, mob.getZ());
                player.getLevel().addFreshEntity(remnant);
            }
        } catch (RuntimeException error) {
            org.apache.logging.log4j.LogManager.getLogger().error("Failed soul capture: {}", mob.getType(), error);
            SoulService.message(player, "unsupported");
        }
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntity() instanceof MobEntity mob) || mob.level.isClientSide) return;
        if (mob.tickCount % 5 == 0) SoulMinions.tryInherit(mob);
        if (SoulMinions.isMinion(mob)) {
            if (!SoulMinions.tick(mob)) event.setCanceled(true);
        } else if (CompanionBrain.isSoul(mob)) CompanionBrain.tick(mob);
        else if (mob.tickCount % 10 == 0 && mob.getPersistentData().hasUUID("scp_mark_owner")
                && mob.getPersistentData().getLong("scp_mark_until") >= mob.getServer().overworld().getGameTime())
            ((net.minecraft.world.server.ServerWorld) mob.level).sendParticles(ParticleTypes.SOUL,
                mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 1, 0.1, 0.05, 0.1, 0);
    }
    @SubscribeEvent public static void constructing(EntityEvent.EntityConstructing event) {
        SoulSpawnContext.constructed(event.getEntity());
    }
    @SubscribeEvent public static void join(EntityJoinWorldEvent event) {
        if (!(event.getEntity() instanceof MobEntity mob) || event.getWorld().isClientSide) return;
        SoulMinions.inheritOnJoin(mob, SoulSpawnContext.wasLoaded(mob));
        if (!CompanionBrain.isSoul(mob)) return;
        if (CompanionBrain.activeEntry(mob) == null) {
            // Register a stale loaded raven's native passengers before the brain discards it.
            // Its normal remove path then releases the real cargo instead of losing saved loot.
            if (!(mob instanceof dev.soulcatcherplus.entity.SoulRavenEntity) || mob.getPassengers().isEmpty())
                event.setCanceled(true);
            return;
        }
        if (SoulMinions.isMinion(mob)) SoulMinions.configure(mob);
        else if (SoulSpawnContext.wasLoaded(mob)) SoulAdapters.get(mob).configureCompanion(mob);
        SoulNames.update(mob);
        SoulBossBars.suppress(mob);
    }
    @SubscribeEvent public static void leave(EntityLeaveWorldEvent event) {
        if (!(event.getEntity() instanceof MobEntity mob) || event.getWorld().isClientSide
                || !CompanionBrain.isSoul(mob) || SoulMinions.isMinion(mob)) return;
        if (SoulSpawnContext.wasDestroyed(mob)) endCompanion(mob);
    }
    private static void endCompanion(MobEntity mob) {
        SoulEntry entry = CompanionBrain.activeEntry(mob);
        if (entry == null) return;
        entry.activeEntity = null; entry.dimension = "";
        entry.readyAt = mob.getServer().overworld().getGameTime() + 20 * 60;
        SoulLedger.get(mob.getServer()).setDirty();
        SoulMinions.dismiss(mob.getServer(), mob.getUUID());
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof MobEntity mob && !mob.level.isClientSide) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof ServerPlayerEntity player && CompanionBrain.isSoul(event.getTarget()))
            SoulNetwork.ghost(player, event.getTarget());
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity player) SoulService.recallAll(player);
    }
    @SubscribeEvent public static void playerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity player) SoulService.recallAll(player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingAttackEvent event) {
        if (event.getEntity().level.isClientSide) return;
        if (event.getEntity() instanceof MobEntity soul) SoulMinions.tryInherit(soul);
        if (CompanionBrain.isSoul(event.getEntity()) && event.getSource() == net.minecraft.util.DamageSource.IN_WALL) {
            event.setCanceled(true);
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof MobEntity mob) SoulMinions.tryInherit(mob);
        if (attacker instanceof MobEntity mob && CompanionBrain.isSoul(mob) && CompanionBrain.allied(mob, event.getEntity()))
            event.setCanceled(true);
        if (attacker != null && event.getEntity() instanceof MobEntity mob && CompanionBrain.isSoul(mob)
                && CompanionBrain.allied(mob, attacker)) event.setCanceled(true);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof MobEntity mob && !mob.level.isClientSide) SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(event.getEntity())) { event.getDrops().clear(); event.setCanceled(true); }
    }
    @SubscribeEvent public static void xp(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof MobEntity mob && !mob.level.isClientSide) SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(event.getEntity())) { event.setDroppedExperience(0); event.setCanceled(true); }
    }
    @SubscribeEvent public static void conversion(LivingConversionEvent.Pre event) {
        if (CompanionBrain.isSoul(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void target(LivingSetAttackTargetEvent event) {
        if (event.getTarget() == null || event.getEntity().level.isClientSide) return;
        if (event.getEntity() instanceof MobEntity mob) SoulMinions.tryInherit(mob);
        if (event.getTarget() instanceof MobEntity mob) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof MobEntity mob && CompanionBrain.isSoul(mob)
                && event.getTarget() != null && !CompanionBrain.canAttack(mob, event.getTarget())) mob.setTarget(null);
    }
}
