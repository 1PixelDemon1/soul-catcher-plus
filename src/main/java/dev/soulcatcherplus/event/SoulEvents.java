// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.event;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.api.*;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof dev.soulcatcherplus.item.SoulScythe scythe)) return;
        net.minecraft.world.InteractionResult result;
        if (event.getEntity().isShiftKeyDown()) {
            if (event.getEntity() instanceof ServerPlayer player) SoulService.open(player);
            result = net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide);
        } else if (event.getTarget() instanceof LivingEntity living) {
            result = scythe.interactLivingEntity(event.getItemStack(), event.getEntity(), living, event.getHand());
        } else return;
        if (result != net.minecraft.world.InteractionResult.PASS) {
            event.setCancellationResult(result); event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new SoulProfiles()); }
    private static ServerPlayer creditedPlayer(Entity source) {
        if (source instanceof ServerPlayer player) return player;
        if (source != null && CompanionBrain.isSoul(source))
            return source.getServer().getPlayerList().getPlayer(CompanionBrain.owner(source));
        return null;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        if (CompanionBrain.isSoul(mob)) {
            SoulNetwork.dissolve(mob);
            SoulLedger ledger = SoulLedger.get(mob.getServer());
            SoulEntry entry = ledger.player(CompanionBrain.owner(mob)).entries.get(mob.getPersistentData().getUUID(CompanionBrain.ENTRY));
            if (entry != null && mob.getUUID().equals(entry.activeEntity)) {
                entry.activeEntity = null; entry.dimension = "";
                entry.readyAt = mob.getServer().overworld().getGameTime() + 20 * 60;
                ledger.setDirty();
            }
            return;
        }
        ServerPlayer player = creditedPlayer(event.getSource().getEntity());
        if (player == null) return;
        SoulProfiles.Profile profile = SoulProfiles.get(mob);
        PlayerSouls collection = SoulService.data(player);
        collection.energy = (int) Math.min(Integer.MAX_VALUE, (long) collection.energy + profile.energy());
        SoulService.changed(player);
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
            com.mojang.logging.LogUtils.getLogger().error("Failed soul capture: {}", mob.getType(), error);
            SoulService.message(player, "unsupported");
        }
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        if (CompanionBrain.isSoul(mob)) CompanionBrain.tick(mob);
        else if (mob.tickCount % 10 == 0 && mob.getPersistentData().hasUUID("scp_mark_owner")
                && mob.getPersistentData().getLong("scp_mark_until") >= mob.getServer().overworld().getGameTime())
            ((net.minecraft.server.level.ServerLevel) mob.level()).sendParticles(ParticleTypes.SOUL,
                mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 1, 0.1, 0.05, 0.1, 0);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide || !CompanionBrain.isSoul(mob)) return;
        SoulEntry entry = SoulLedger.get(mob.getServer()).player(CompanionBrain.owner(mob))
            .entries.get(mob.getPersistentData().getUUID(CompanionBrain.ENTRY));
        if (entry == null || !mob.getUUID().equals(entry.activeEntity)) { event.setCanceled(true); return; }
        if (event.loadedFromDisk()) SoulAdapters.get(mob).configureCompanion(mob);
        SoulBossBars.suppress(mob);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && CompanionBrain.isSoul(event.getTarget()))
            SoulNetwork.ghost(player, event.getTarget());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SoulService.recallAll(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SoulService.recallAll(player);
    }
    @SubscribeEvent public static void playerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SoulService.recallAll(player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Mob mob && CompanionBrain.isSoul(mob) && CompanionBrain.allied(mob, event.getEntity()))
            event.setCanceled(true);
        if (attacker != null && event.getEntity() instanceof Mob mob && CompanionBrain.isSoul(mob)
                && CompanionBrain.allied(mob, attacker)) event.setCanceled(true);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (CompanionBrain.isSoul(event.getEntity())) { event.getDrops().clear(); event.setCanceled(true); }
    }
    @SubscribeEvent public static void xp(LivingExperienceDropEvent event) {
        if (CompanionBrain.isSoul(event.getEntity())) { event.setDroppedExperience(0); event.setCanceled(true); }
    }
    @SubscribeEvent public static void conversion(LivingConversionEvent.Pre event) {
        if (CompanionBrain.isSoul(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && CompanionBrain.isSoul(mob)
                && event.getNewTarget() != null && !CompanionBrain.canAttack(mob, event.getNewTarget())) event.setCanceled(true);
    }
}
