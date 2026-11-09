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
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulEvents {
    /** Handle creative use before vanilla restores the old stack count after Item.use. */
    @SubscribeEvent public static boolean creativeScrollUse(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().isCreative() || !(event.getItemStack().getItem() instanceof SoulScroll scroll)) return false;
        event.setCancellationResult(scroll.use(event.getLevel(), event.getEntity(), event.getHand()));
        return true;
    }

    @SubscribeEvent(priority = Priority.HIGHEST)
    public static boolean interact(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getItemStack().getItem() instanceof dev.soulcatcherplus.item.SoulScythe scythe)) return false;
        net.minecraft.world.InteractionResult result;
        if (event.getTarget() instanceof LivingEntity living) {
            result = scythe.interactLivingEntity(event.getItemStack(), event.getEntity(), living, event.getHand());
        } else return false;
        if (result != net.minecraft.world.InteractionResult.PASS) {
            event.setCancellationResult(result); return true;
        }
            return false;
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new SoulProfiles()); }
    private static ServerPlayer creditedPlayer(Entity source) {
        if (source instanceof ServerPlayer player) return player;
        if (source instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (source != null && CompanionBrain.isSoul(source))
            return source.level().getServer().getPlayerList().getPlayer(CompanionBrain.owner(source));
        return null;
    }
    @SubscribeEvent(priority = Priority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
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
        long now = player.level().getServer().overworld().getGameTime();
        if (!dev.soulcatcherplus.data.SoulNbt.hasUuid(tag, "scp_mark_owner") || !dev.soulcatcherplus.data.SoulNbt.uuid(tag, "scp_mark_owner").equals(player.getUUID())
                || tag.getLongOr("scp_mark_until", 0L) < now || !profile.enabled()) return;
        SoulAdapter adapter = SoulAdapters.get(mob);
        if (!adapter.canCapture(mob, player) || SoulEvent.Capture.BUS.post(new SoulEvent.Capture(player, mob))) return;
        try {
            SoulEntry soul = new SoulEntry(UUID.randomUUID(), mob.getType().builtInRegistryHolder().key().identifier().toString(),
                mob.getName().getString(), SoulSnapshots.sanitize(adapter.capture(mob)), mob.getMaxHealth());
            SoulRemnant remnant = SoulCatcherPlus.REMNANT.get().create(player.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (remnant != null) {
                remnant.initialize(player, soul); remnant.snapTo(mob.getX(), mob.getY() + 0.5, mob.getZ());
                player.level().addFreshEntity(remnant);
            }
        } catch (RuntimeException error) {
            org.apache.logging.log4j.LogManager.getLogger().error("Failed soul capture: {}", mob.getType(), error);
            SoulService.message(player, "unsupported");
        }
    }
    @SubscribeEvent public static boolean tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return false;
        if (mob.tickCount % 5 == 0) SoulMinions.tryInherit(mob);
        if (SoulMinions.isMinion(mob)) {
            if (!SoulMinions.tick(mob)) return true;
        } else if (CompanionBrain.isSoul(mob)) CompanionBrain.tick(mob);
        else if (mob.tickCount % 10 == 0 && dev.soulcatcherplus.data.SoulNbt.hasUuid(mob.getPersistentData(), "scp_mark_owner")
                && mob.getPersistentData().getLongOr("scp_mark_until", 0L) >= mob.level().getServer().overworld().getGameTime())
            ((net.minecraft.server.level.ServerLevel) mob.level()).sendParticles(ParticleTypes.SOUL,
                mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 1, 0.1, 0.05, 0.1, 0);
            return false;
    }
    @SubscribeEvent public static void constructing(EntityEvent.EntityConstructing event) {
        SoulSpawnContext.constructed(event.getEntity());
    }
    @SubscribeEvent public static boolean join(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide()) return false;
        SoulMinions.inheritOnJoin(mob, event.loadedFromDisk());
        if (!CompanionBrain.isSoul(mob)) return false;
        if (CompanionBrain.activeEntry(mob) == null) {
            // Register a stale loaded raven's native passengers before the brain discards it.
            // Its normal remove path then releases the real cargo instead of losing saved loot.
            if (!(mob instanceof dev.soulcatcherplus.entity.SoulRavenEntity) || mob.getPassengers().isEmpty())
                return true;
            return false;
        }
        if (SoulMinions.isMinion(mob)) SoulMinions.configure(mob);
        else if (event.loadedFromDisk()) SoulAdapters.get(mob).configureCompanion(mob);
        SoulNames.update(mob);
        SoulBossBars.suppress(mob);
            return false;
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide()
                || !CompanionBrain.isSoul(mob) || SoulMinions.isMinion(mob)) return;
        if (SoulSpawnContext.wasDestroyed(mob)) endCompanion(mob);
    }
    private static void endCompanion(Mob mob) {
        SoulEntry entry = CompanionBrain.activeEntry(mob);
        if (entry == null) return;
        entry.activeEntity = null; entry.dimension = "";
        entry.readyAt = mob.level().getServer().overworld().getGameTime() + 20 * 60;
        SoulLedger.get(mob.level().getServer()).setDirty();
        SoulMinions.dismiss(mob.level().getServer(), mob.getUUID());
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Mob mob && !mob.level().isClientSide()) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof ServerPlayer player && CompanionBrain.isSoul(event.getTarget()))
            SoulNetwork.ghost(player, event.getTarget());
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SoulService.recallAll(player);
    }
    @SubscribeEvent public static void playerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SoulService.recallAll(player);
    }
    @SubscribeEvent(priority = Priority.HIGHEST)
    public static boolean damage(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return false;
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (attacker instanceof Mob mob && CompanionBrain.isSoul(mob) && CompanionBrain.allied(mob, event.getEntity()))
            return true;
        if (attacker != null && event.getEntity() instanceof Mob mob && CompanionBrain.isSoul(mob)
                && CompanionBrain.allied(mob, attacker)) return true;
            return false;
    }
    @SubscribeEvent public static boolean drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(event.getEntity())) { event.getDrops().clear(); return true; }
            return false;
    }
    @SubscribeEvent public static boolean xp(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) SoulMinions.tryInherit(mob);
        if (CompanionBrain.isSoul(event.getEntity())) { event.setDroppedExperience(0); return true; }
            return false;
    }
    @SubscribeEvent public static boolean conversion(LivingConversionEvent.Pre event) {
        if (CompanionBrain.isSoul(event.getEntity())) return true;
            return false;
    }
    @SubscribeEvent public static boolean target(LivingChangeTargetEvent event) {
        if (event.getNewTarget() == null || event.getEntity().level().isClientSide()) return false;
        if (event.getEntity() instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (event.getNewTarget() instanceof Mob mob) SoulMinions.tryInherit(mob);
        if (event.getEntity() instanceof Mob mob && CompanionBrain.isSoul(mob)
                && event.getNewTarget() != null && !CompanionBrain.canAttack(mob, event.getNewTarget())) event.setNewTarget(null);
        return false;
    }
}
