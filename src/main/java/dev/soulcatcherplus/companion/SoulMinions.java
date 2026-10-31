// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;

/** Descendants share a root reservation, never a separate collection entry or refund. */
public final class SoulMinions {
    public static final String ROOT = "scp_minion_root";
    private static final int MAX_CREATOR_DEPTH = 32;
    // Server-thread-only, weak keys: configuration state must not survive an entity reload.
    private static final Set<Mob> CONFIGURED = Collections.newSetFromMap(new WeakHashMap<>());
    private record Bond(UUID owner, UUID entry, UUID root) {}

    public static boolean isMinion(Entity entity) { return entity.getPersistentData().hasUUID(ROOT); }

    /** Default discovery is based on explicit ownership, never proximity or spawn timing. */
    public static UUID ownerUuid(Entity entity) {
        return entity instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
    }
    public static Entity defaultSummoner(Entity entity) {
        if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            if (owner != null) return owner;
        }
        if (entity instanceof OwnableEntity ownable) {
            LivingEntity owner = ownable.getOwner();
            if (owner != null) return owner;
            UUID id = ownable.getOwnerUUID();
            if (id != null && entity.level() instanceof ServerLevel level) return level.getEntity(id);
        }
        return null;
    }
    private static Entity summoner(Entity entity) {
        return entity instanceof Mob mob ? SoulAdapters.get(mob).summoner(mob) : defaultSummoner(entity);
    }
    /** May be retried after spawn for mods that assign a creator after world insertion. */
    public static boolean tryInherit(Mob mob) {
        if (mob.level().isClientSide || CompanionBrain.isSoul(mob) || mob.isRemoved()) return false;
        return attach(mob, summoner(mob));
    }
    /** Fresh construction provenance is a fallback only; an explicitly foreign creator is authoritative. */
    public static boolean inheritOnJoin(Mob mob, boolean loadedFromDisk) {
        Entity constructedBy = SoulSpawnContext.consume(mob);
        if (mob.level().isClientSide || CompanionBrain.isSoul(mob) || mob.isRemoved()) return false;
        Entity creator = summoner(mob);
        if (creator != null) return attach(mob, creator);
        return !loadedFromDisk && attach(mob, constructedBy);
    }
    /** Server-only integration hook for summons that do not expose their creator. */
    public static boolean attach(Mob child, Entity creator) {
        if (creator == null || !(child.level() instanceof ServerLevel level)
                || child.isRemoved() || CompanionBrain.isSoul(child)) return false;
        Bond bond = findBond(creator, level, child.getUUID());
        if (bond == null) return false;
        var tag = child.getPersistentData();
        tag.putUUID(CompanionBrain.OWNER, bond.owner());
        tag.putUUID(CompanionBrain.ENTRY, bond.entry());
        tag.putUUID(ROOT, bond.root());
        if (CompanionBrain.activeEntry(child) == null) { child.discard(); return false; }
        if (child.isAddedToWorld()) {
            SoulNetwork.ghost(child);
        }
        return true;
    }
    private static Bond findBond(Entity creator, ServerLevel level, UUID child) {
        Set<UUID> seen = new HashSet<>();
        seen.add(child);
        for (int depth = 0; creator != null && depth < MAX_CREATOR_DEPTH; depth++) {
            if (creator.level() != level || !seen.add(creator.getUUID())) return null;
            if (creator instanceof Mob mob && CompanionBrain.isSoul(mob)) {
                if (!mob.getPersistentData().hasUUID(CompanionBrain.ENTRY)) return null;
                UUID root = isMinion(mob) ? mob.getPersistentData().getUUID(ROOT) : mob.getUUID();
                return new Bond(CompanionBrain.owner(mob), mob.getPersistentData().getUUID(CompanionBrain.ENTRY), root);
            }
            creator = summoner(creator);
        }
        return null;
    }
    /** Only call before AI iteration (join or living-tick), never from a target/damage callback. */
    public static void configure(Mob minion) {
        if (CONFIGURED.contains(minion)) return;
        minion.setTarget(null);
        minion.setCanPickUpLoot(false);
        SoulAdapters.get(minion).configureMinion(minion);
        SoulBossBars.suppress(minion);
        CONFIGURED.add(minion);
    }
    /** Before native AI. An unloaded root pauses the child instead of letting it find a different master. */
    public static boolean tick(Mob minion) {
        SoulEntry entry = CompanionBrain.activeEntry(minion);
        if (entry == null || !minion.isAlive()) { minion.discard(); return false; }
        Entity entity = ((ServerLevel)minion.level()).getEntity(minion.getPersistentData().getUUID(ROOT));
        if (entity == null) {
            if (minion.getTarget() != null) minion.setTarget(null);
            return false;
        }
        if (!(entity instanceof Mob root) || !root.isAlive() || isMinion(root) || CompanionBrain.activeEntry(root) != entry) {
            dismissOne(minion); return false;
        }
        configure(minion);
        var adapter = SoulAdapters.get(minion);
        adapter.restoreSummoner(minion, root);
        if (minion.tickCount % 5 == 0) {
            SoulBossBars.suppress(minion);
            LivingEntity target = CompanionBrain.mode(root) == CompanionBrain.Mode.WAIT ? null : root.getTarget();
            if (target != null && !CompanionBrain.canAttack(minion, target)) target = null;
            if (minion.getTarget() != target) minion.setTarget(target);
            adapter.tickMinion(minion, root);
        }
        return true;
    }
    /** Remove loaded descendants now; unloaded ones fail their saved reservation check on reload. */
    public static void dismiss(MinecraftServer server, UUID root) {
        var minions = new ArrayList<Mob>();
        for (ServerLevel level : server.getAllLevels()) for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && isMinion(mob) && root.equals(mob.getPersistentData().getUUID(ROOT)))
                minions.add(mob);
        }
        // Removal changes the level's entity collection, so iterate a detached list.
        minions.forEach(SoulMinions::dismissOne);
    }
    private static void dismissOne(Mob mob) {
        SoulNetwork.dissolve(mob);
        mob.discard();
    }
    private SoulMinions() {}
}
