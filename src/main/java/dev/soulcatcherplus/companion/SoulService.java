// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.api.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulService {
    public static final int CAPACITY = 6;
    public static PlayerSouls data(ServerPlayer player) { return SoulLedger.get(player.server).player(player.getUUID()); }
    public static void changed(ServerPlayer player) { SoulLedger.get(player.server).setDirty(); }
    public static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.soulcatcherplus." + key, args), true);
    }
    public static boolean holdsScythe(ServerPlayer player) {
        return player.getMainHandItem().is(SoulCatcherPlus.SCYTHE.get()) || player.getOffhandItem().is(SoulCatcherPlus.SCYTHE.get());
    }
    public static void summon(ServerPlayer player, UUID id) {
        SoulEntry entry = data(player).entries.get(id);
        if (entry == null || entry.activeEntity != null) return;
        long time = player.server.overworld().getGameTime();
        if (entry.readyAt > time) { message(player, "cooldown", (entry.readyAt - time + 19) / 20); return; }
        if (data(player).energy < entry.cost) { message(player, "energy_needed", entry.cost); return; }
        if (data(player).usedSlots() + entry.slots > CAPACITY) { message(player, "capacity", CAPACITY); return; }
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
            if (!place(mob, player)) { message(player, "no_space"); return; }
            if (MinecraftForge.EVENT_BUS.post(new SoulEvent.Summon(player, mob))) return;
            // Canonical identity must be visible to EntityJoinLevelEvent.
            entry.activeEntity = mob.getUUID();
            entry.dimension = player.level().dimension().location().toString();
            if (!player.serverLevel().addFreshEntity(mob)) {
                entry.activeEntity = null; entry.dimension = ""; return;
            }
            data(player).energy -= entry.cost;
            changed(player);
            message(player, "summoned", entry.name);
        } catch (RuntimeException error) {
            if (created.isAddedToWorld()) created.discard();
            entry.activeEntity = null; entry.dimension = "";
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
        UUID active = entry.activeEntity;
        entry.activeEntity = null; entry.dimension = "";
        // Unloaded entities are invalidated now and discarded when their chunk loads.
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(active);
            if (entity instanceof Mob mob) {
                ServerPlayer owner = server.getPlayerList().getPlayer(CompanionBrain.owner(mob));
                if (owner != null) SoulAdapters.get(mob).onRecall(mob, owner);
                mob.discard();
            }
        }
        SoulLedger.get(server).setDirty();
    }
    public static void recallAll(ServerPlayer player) {
        data(player).entries.values().forEach(e -> recall(player.server, e));
        changed(player);
    }
    public static void command(ServerPlayer player, CompanionBrain.Mode mode) {
        PlayerSouls collection = data(player);
        collection.entries.values().stream().filter(e -> collection.commandGroup || e.id.equals(collection.selected))
            .filter(e -> e.activeEntity != null).forEach(e -> {
                Entity entity = player.serverLevel().getEntity(e.activeEntity);
                if (entity instanceof Mob mob) {
                    mob.setTarget(null);
                    mob.getPersistentData().putString(CompanionBrain.MODE, mode.name());
                    mob.getPersistentData().putLong("scp_guard", player.blockPosition().asLong());
                    mob.getNavigation().stop();
                }
            });
        changed(player);
    }
    public static void attack(ServerPlayer player, LivingEntity target) {
        PlayerSouls collection = data(player);
        collection.entries.values().stream().filter(e -> collection.commandGroup || e.id.equals(collection.selected))
            .filter(e -> e.activeEntity != null).forEach(e -> {
                Entity entity = player.serverLevel().getEntity(e.activeEntity);
                if (entity instanceof Mob mob && CompanionBrain.canAttack(mob, target)) {
                    mob.getPersistentData().putString(CompanionBrain.MODE, "FOLLOW"); mob.setTarget(target);
                }
            });
    }
    public static void open(ServerPlayer player) { SoulNetwork.open(player); }
    private SoulService() {}
}
