// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.network.SoulNetwork;
import dev.soulcatcherplus.item.SoulScythe;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulService {
    public static PlayerSouls data(ServerPlayer player) { return SoulLedger.get(player.server).player(player.getUUID()); }
    public static void changed(ServerPlayer player) { SoulLedger.get(player.server).setDirty(); }
    public static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.soulcatcherplus." + key, args), true);
    }
    public static boolean holdsScythe(ServerPlayer player) {
        return SoulScythe.heldBy(player) != null;
    }
    public static SummonRules.Context summonContext(ServerPlayer player) {
        SoulScythe scythe = SoulScythe.heldBy(player);
        return SummonRules.context(data(player), scythe == null ? 0 : scythe.summonLimit(), player.server.overworld().getGameTime());
    }
    public static void summon(ServerPlayer player, UUID id) {
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
                case LIMIT_REACHED -> message(player, "capacity", context.limit());
                case LOW_ENERGY -> message(player, "energy_needed", entry.cost);
                case READY -> { }
            }
            return;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entry.entityType));
        if (type == null) { message(player, "missing_mod"); return; }
        Entity created = type.create(player.serverLevel());
        if (!(created instanceof Mob mob)) { message(player, "unsupported"); return; }
        try {
            mob.load(SoulSnapshots.sanitize(entry.snapshot()));
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
            // Canonical identity must be visible to EntityJoinLevelEvent.
            entry.activeEntity = mob.getUUID();
            entry.dimension = player.level().dimension().location().toString();
            if (!player.serverLevel().addFreshEntity(mob)) {
                entry.activeEntity = null; entry.dimension = "";
                SoulMinions.dismiss(player.server, mob.getUUID());
                return;
            }
            data(player).energy -= entry.cost;
            changed(player);
            message(player, "summoned", entry.name);
        } catch (RuntimeException error) {
            entry.activeEntity = null; entry.dimension = "";
            if (created.isAddedToWorld()) created.discard();
            SoulMinions.dismiss(player.server, created.getUUID());
            com.mojang.logging.LogUtils.getLogger().error("Unable to summon {}", entry.entityType, error);
            message(player, "unsupported");
        }
    }
    private static boolean place(Mob mob, ServerPlayer player) {
        Vec3 ahead = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        for (int y = 0; y <= 2; y++) for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            double x = ahead.x + Math.cos(angle) * 2, z = ahead.z + Math.sin(angle) * 2;
            mob.moveTo(x, player.getY() + y, z, player.getYRot(), 0);
            BlockPos pos = mob.blockPosition();
            if (player.level().hasChunkAt(pos) && player.level().getWorldBorder().isWithinBounds(mob.getBoundingBox())
                && player.level().noCollision(mob) && !player.level().containsAnyLiquid(mob.getBoundingBox())
                && !player.level().getBlockState(pos.below()).getCollisionShape(player.level(), pos.below()).isEmpty()) return true;
        }
        return false;
    }
    public static void recall(MinecraftServer server, SoulEntry entry) {
        if (entry.activeEntity == null) return;
        SoulLedger ledger = SoulLedger.get(server);
        UUID active = entry.activeEntity;
        entry.activeEntity = null; entry.dimension = "";
        // Invalidate before adapter callbacks, so repeated/reentrant recalls cannot refund twice.
        ledger.setDirty();
        // Unloaded entities are invalidated now and discarded when their chunk loads.
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(active);
            if (!(entity instanceof Mob mob) || !CompanionBrain.isSoul(mob)
                    || !mob.getPersistentData().hasUUID(CompanionBrain.ENTRY)
                    || !entry.id.equals(mob.getPersistentData().getUUID(CompanionBrain.ENTRY))) continue;
            UUID ownerId = CompanionBrain.owner(mob);
            PlayerSouls collection = ledger.player(ownerId);
            if (collection.entries.get(entry.id) != entry) continue;
            // Capture current HP before an integration's cleanup can change the mob.
            int refund = mob.isAlive() ? SoulBalance.recallEnergy(mob.getHealth()) : 0;
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            try {
                if (owner != null) SoulAdapters.get(mob).onRecall(mob, owner);
            } catch (RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().warn("Failed soul recall cleanup: {}", entry.entityType, error);
            }
            SoulNetwork.dissolve(mob);
            mob.discard();
            collection.energy = (int)Math.min(Integer.MAX_VALUE, (long)collection.energy + refund);
            ledger.setDirty();
            break;
        }
        SoulMinions.dismiss(server, active);
    }
    public static void recallAll(ServerPlayer player) {
        data(player).entries.values().forEach(e -> recall(player.server, e));
        changed(player);
    }
    public static void command(ServerPlayer player, CompanionBrain.Mode mode) {
        PlayerSouls collection = data(player);
        collection.entries.values().stream().filter(e -> receivesCommand(collection, e))
            .forEach(e -> applyCommand(player, e, mode));
        changed(player);
    }
    /** Address the whole owned group without changing the collection's saved command scope. */
    public static void commandAll(ServerPlayer player, CompanionBrain.Mode mode) {
        data(player).entries.values().forEach(entry -> applyCommand(player, entry, mode));
        changed(player);
    }
    /** Address one owned entry without changing the collection's selected species or group scope. */
    public static void commandOne(ServerPlayer player, UUID id, CompanionBrain.Mode mode) {
        SoulEntry entry = data(player).entries.get(id);
        if (entry != null && applyCommand(player, entry, mode)) changed(player);
    }
    /** Resolve canonical ownership without loading a chunk for a menu request. */
    public static Mob activeMob(ServerPlayer player, SoulEntry entry) {
        Entity entity = entry.activeEntity == null ? null : player.serverLevel().getEntity(entry.activeEntity);
        if (!(entity instanceof Mob mob) || !mob.isAlive() || !CompanionBrain.isSoul(mob)
                || !player.getUUID().equals(CompanionBrain.owner(mob))
                || !mob.getPersistentData().hasUUID(CompanionBrain.ENTRY)
                || !entry.id.equals(mob.getPersistentData().getUUID(CompanionBrain.ENTRY))) return null;
        return mob;
    }
    private static boolean applyCommand(ServerPlayer player, SoulEntry entry, CompanionBrain.Mode mode) {
        Mob mob = activeMob(player, entry);
        if (mob == null) return false;
        mob.setTarget(null);
        mob.getPersistentData().putString(CompanionBrain.MODE, mode.name());
        mob.getPersistentData().putLong("scp_guard", player.blockPosition().asLong());
        mob.getNavigation().stop();
        return true;
    }
    public static void attack(ServerPlayer player, LivingEntity target) {
        PlayerSouls collection = data(player);
        collection.entries.values().stream().filter(e -> receivesCommand(collection, e))
            .filter(e -> e.activeEntity != null).forEach(e -> {
                Entity entity = player.serverLevel().getEntity(e.activeEntity);
                if (entity instanceof Mob mob && CompanionBrain.canAttack(mob, target)) {
                    mob.getPersistentData().putString(CompanionBrain.MODE, "FOLLOW"); mob.setTarget(target);
                }
            });
    }
    public static void open(ServerPlayer player) { SoulNetwork.open(player); }
    private static boolean receivesCommand(PlayerSouls collection, SoulEntry entry) {
        SoulEntry selected = collection.entries.get(collection.selected);
        return collection.commandGroup || (selected != null && selected.entityType.equals(entry.entityType));
    }
    private SoulService() {}
}
