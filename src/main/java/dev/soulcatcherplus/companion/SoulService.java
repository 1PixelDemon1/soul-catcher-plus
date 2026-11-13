// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.network.SoulNetwork;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.enchantment.SoulEnchantments;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulService {
    public static PlayerSouls data(ServerPlayerEntity player) { return SoulLedger.get(player.server).player(player.getUUID()); }
    public static void changed(ServerPlayerEntity player) { SoulLedger.get(player.server).setDirty(); }
    public static void message(ServerPlayerEntity player, String key, Object... args) {
        player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("message.soulcatcherplus." + key, args), true);
    }
    public static boolean holdsScythe(ServerPlayerEntity player) {
        return SoulScythe.heldBy(player) != null;
    }
    /** Shared equipment-only limit; querying it does not scan the player's soul collection. */
    public static int summonLimit(ServerPlayerEntity player) {
        SoulScythe scythe = SoulScythe.heldBy(player);
        return scythe == null ? 0 : scythe.summonLimit() + SoulEnchantments.additionalSlots(player);
    }
    public static SummonRules.Context summonContext(ServerPlayerEntity player) {
        SoulCages.Active cage = SoulCages.active(player);
        return SummonRules.context(data(player), summonLimit(player),
            cage.energy(), cage.capacity(), player.server.overworld().getGameTime());
    }
    public static void summon(ServerPlayerEntity player, UUID id) {
        SoulEntry entry = data(player).entries.get(id);
        if (entry == null) return;
        SummonRules.Context context = summonContext(player);
        SummonRules.Availability availability = SummonRules.inspect(context, entry);
        if (availability.status() != SummonRules.Status.READY) {
            switch (availability.status()) {
                case ACTIVE -> { }
                case OTHER_FORM_ACTIVE -> message(player, "variant_active");
                case RECOVERING -> message(player, "cooldown", availability.recoverySeconds());
                case NO_SCYTHE -> message(player, "scythe_needed");
                case NO_CAGE -> message(player, "cage_needed");
                case CAGE_TOO_SMALL -> message(player, "cage_too_small", entry.cost);
                case LIMIT_REACHED -> message(player, "capacity", context.limit());
                case LOW_ENERGY -> message(player, "energy_needed", entry.cost);
                case READY -> { }
            }
            return;
        }
        EntityType<?> type = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(entry.entityType));
        if (type == null) { message(player, "missing_mod"); return; }
        Entity created = type.create(player.getLevel());
        if (!(created instanceof MobEntity mob)) { message(player, "unsupported"); return; }
        ItemStack paidCage = ItemStack.EMPTY;
        boolean completed = false;
        try {
            mob.load(SoulSnapshots.sanitize(entry.snapshot()));
            SoulSpawnContext.markFresh(mob);
            mob.setUUID(UUID.randomUUID());
            mob.setHealth(mob.getMaxHealth()); mob.deathTime = 0;
            mob.setPersistenceRequired(); mob.setCanPickUpLoot(false);
            mob.getPersistentData().putUUID(CompanionBrain.OWNER, player.getUUID());
            mob.getPersistentData().putUUID(CompanionBrain.ENTRY, entry.id);
            mob.getPersistentData().putString(CompanionBrain.MODE, "FOLLOW");
            SoulAdapters.get(mob).prepareSummon(mob, player);
            SoulAdapters.get(mob).configureCompanion(mob);
            SoulBossBars.suppress(mob);
            if (!place(mob, player)) { message(player, "no_space"); return; }
            if (MinecraftForge.EVENT_BUS.post(new SoulEvent.Summon(player, mob))) return;
            SoulNames.update(mob, player);
            // Charge only after integrations approve, before join callbacks can use the companion.
            SoulCages.Active cage = SoulCages.active(player);
            if (!SoulCages.spend(player, entry.cost)) {
                message(player, cage.present() ? "energy_needed" : "cage_needed", entry.cost);
                return;
            }
            paidCage = cage.stack();
            // Canonical identity must be visible to EntityJoinWorldEvent.
            entry.activeEntity = mob.getUUID();
            entry.dimension = player.level.dimension().location().toString();
            if (!player.getLevel().addFreshEntity(mob)) {
                entry.activeEntity = null; entry.dimension = "";
                SoulMinions.dismiss(player.server, mob.getUUID());
                return;
            }
            changed(player);
            message(player, "summoned", entry.name);
            completed = true;
        } catch (RuntimeException error) {
            entry.activeEntity = null; entry.dimension = "";
            if (created.isAddedToWorld()) created.remove();
            SoulMinions.dismiss(player.server, created.getUUID());
            org.apache.logging.log4j.LogManager.getLogger().error("Unable to summon {}", entry.entityType, error);
            message(player, "unsupported");
        } finally {
            if (!completed && !paidCage.isEmpty()) {
                // Roll back to the exact charged item, even if a callback moved it to another slot.
                SoulCage.setEnergy(paidCage, SoulCage.energy(paidCage) + entry.cost);
                player.inventory.setChanged();
            }
        }
    }
    private static boolean place(MobEntity mob, ServerPlayerEntity player) {
        Vector3d ahead = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        Vector3d destination = SoulPlacement.findNear(mob, player, ahead);
        if (destination == null) return false;
        mob.moveTo(destination.x, destination.y, destination.z, player.yRot, 0);
        return true;
    }

    public static void recall(MinecraftServer server, SoulEntry entry) {
        recall(server, entry, null);
    }
    private static void recall(MinecraftServer server, SoulEntry entry, ServerPlayerEntity ownerHint) {
        if (entry.activeEntity == null) return;
        SoulLedger ledger = SoulLedger.get(server);
        UUID active = entry.activeEntity;
        entry.activeEntity = null; entry.dimension = "";
        // Invalidate before adapter callbacks, so repeated/reentrant recalls cannot refund twice.
        ledger.setDirty();
        // Unloaded entities are invalidated now and discarded when their chunk loads.
        for (ServerWorld level : server.getAllLevels()) {
            Entity entity = level.getEntity(active);
            if (!(entity instanceof MobEntity mob) || !CompanionBrain.isSoul(mob)
                    || !mob.getPersistentData().hasUUID(CompanionBrain.ENTRY)
                    || !entry.id.equals(mob.getPersistentData().getUUID(CompanionBrain.ENTRY))) continue;
            UUID ownerId = CompanionBrain.owner(mob);
            PlayerSouls collection = ledger.player(ownerId);
            if (collection.entries.get(entry.id) != entry) continue;
            // Capture current HP before an integration's cleanup can change the mob.
            int refund = mob.isAlive() ? SoulBalance.recallEnergy(mob.getHealth()) : 0;
            ServerPlayerEntity owner = ownerHint != null && ownerHint.getUUID().equals(ownerId)
                ? ownerHint : server.getPlayerList().getPlayer(ownerId);
            try {
                if (owner != null) SoulAdapters.get(mob).onRecall(mob, owner);
            } catch (RuntimeException error) {
                org.apache.logging.log4j.LogManager.getLogger().warn("Failed soul recall cleanup: {}", entry.entityType, error);
            }
            SoulNetwork.dissolve(mob);
            mob.remove();
            if (owner != null) SoulCages.credit(owner, refund);
            break;
        }
        SoulMinions.dismiss(server, active);
    }
    public static void recallAll(ServerPlayerEntity player) {
        data(player).entries.values().forEach(e -> recall(player.server, e, player));
        changed(player);
    }
    /** The supplied ID is resolved only in this player's collection, never in another player's data. */
    public static void forgetForm(ServerPlayerEntity player, UUID id) {
        if (!holdsScythe(player)) { message(player, "forget_scythe_needed"); return; }
        PlayerSouls collection = data(player);
        SoulEntry target = collection.entries.get(id);
        if (target == null) return;
        // Keep the entry valid during native onRecall callbacks and the normal energy refund.
        recall(player.server, target, player);
        collection.forget(id);
        changed(player);
        message(player, "forgotten");
    }
    public static void command(ServerPlayerEntity player, CompanionBrain.Mode mode) {
        PlayerSouls collection = data(player);
        collection.entries.values().stream().filter(e -> receivesCommand(collection, e))
            .forEach(e -> applyCommand(player, e, mode));
        changed(player);
    }
    /** Address the whole owned group without changing the collection's saved command scope. */
    public static void commandAll(ServerPlayerEntity player, CompanionBrain.Mode mode) {
        data(player).entries.values().forEach(entry -> applyCommand(player, entry, mode));
        changed(player);
    }
    /** Address one owned entry without changing the collection's selected species or group scope. */
    public static void commandOne(ServerPlayerEntity player, UUID id, CompanionBrain.Mode mode) {
        SoulEntry entry = data(player).entries.get(id);
        if (entry != null && applyCommand(player, entry, mode)) changed(player);
    }
    /** Collection orders follow the selected-species / whole-group scope. */
    public static void teleport(ServerPlayerEntity player) {
        PlayerSouls collection = data(player);
        teleportEntries(player, entry -> receivesCommand(collection, entry));
    }
    /** Wheel orders never alter the saved collection selection or scope. */
    public static void teleportAll(ServerPlayerEntity player) {
        teleportEntries(player, entry -> true);
    }
    public static void teleportOne(ServerPlayerEntity player, UUID id) {
        teleportEntries(player, entry -> entry.id.equals(id));
    }
    private static void teleportEntries(ServerPlayerEntity player, java.util.function.Predicate<SoulEntry> selected) {
        if (!holdsScythe(player)) { message(player, "teleport_scythe_needed"); return; }
        int moved = 0, failed = 0;
        for (SoulEntry entry : data(player).entries.values()) {
            if (entry.activeEntity == null || !selected.test(entry)) continue;
            MobEntity mob = activeMob(player, entry);
            if (mob == null || !CompanionBrain.teleportToOwner(mob, player)) { failed++; continue; }
            mob.setTarget(null);
            mob.getNavigation().stop();
            if (CompanionBrain.mode(mob) == CompanionBrain.Mode.GUARD)
                mob.getPersistentData().putLong("scp_guard", mob.blockPosition().asLong());
            moved++;
        }
        if (failed > 0) message(player, "teleport_partial", moved, failed);
        else if (moved > 0) message(player, "teleported", moved);
    }
    /** Resolve canonical ownership without loading a chunk for a menu request. */
    public static MobEntity activeMob(ServerPlayerEntity player, SoulEntry entry) {
        Entity entity = entry.activeEntity == null ? null : player.getLevel().getEntity(entry.activeEntity);
        if (!(entity instanceof MobEntity mob) || !mob.isAlive() || !CompanionBrain.isSoul(mob)
                || !player.getUUID().equals(CompanionBrain.owner(mob))
                || !mob.getPersistentData().hasUUID(CompanionBrain.ENTRY)
                || !entry.id.equals(mob.getPersistentData().getUUID(CompanionBrain.ENTRY))) return null;
        return mob;
    }
    private static boolean applyCommand(ServerPlayerEntity player, SoulEntry entry, CompanionBrain.Mode mode) {
        MobEntity mob = activeMob(player, entry);
        if (mob == null) return false;
        if (!SoulAdapters.get(mob).usesStandardBrain(mob)) return false;
        CompanionBrain.resetReturn(mob);
        mob.setTarget(null);
        mob.getPersistentData().putString(CompanionBrain.MODE, mode.name());
        mob.getPersistentData().putLong("scp_guard", player.blockPosition().asLong());
        mob.getNavigation().stop();
        return true;
    }
    public static void attack(ServerPlayerEntity player, LivingEntity target) {
        PlayerSouls collection = data(player);
        collection.entries.values().stream().filter(e -> receivesCommand(collection, e))
            .filter(e -> e.activeEntity != null).forEach(e -> {
                Entity entity = player.getLevel().getEntity(e.activeEntity);
                if (entity instanceof MobEntity mob && CompanionBrain.canAttack(mob, target)) {
                    mob.getPersistentData().putString(CompanionBrain.MODE, "FOLLOW"); mob.setTarget(target);
                }
            });
    }
    public static void open(ServerPlayerEntity player) { SoulNetwork.open(player); }
    private static boolean receivesCommand(PlayerSouls collection, SoulEntry entry) {
        SoulEntry selected = collection.entries.get(collection.selected);
        return collection.commandGroup || (selected != null && selected.entityType.equals(entry.entityType));
    }
    private SoulService() {}
}
