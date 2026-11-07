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
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;

/** Descendants share a root reservation, never a separate collection entry or refund. */
public final class SoulMinions {
    public static final String ROOT = "scp_minion_root";
    private static final int MAX_CREATOR_DEPTH = 32;
    // Server-thread-only, weak keys: configuration state must not survive an entity reload.
    private static final Set<MobEntity> CONFIGURED = Collections.newSetFromMap(new WeakHashMap<>());
    @com.github.bsideup.jabel.Desugar
    private record Bond(UUID owner, UUID entry, UUID root) {}

    public static boolean isMinion(Entity entity) { return entity.getPersistentData().hasUUID(ROOT); }

    /** Default discovery is based on explicit ownership, never proximity or spawn timing. */
    public static UUID ownerUuid(Entity entity) {
        if (entity instanceof net.minecraft.entity.passive.TameableEntity tame) return tame.getOwnerUUID();
        if (entity instanceof net.minecraft.entity.passive.horse.AbstractHorseEntity horse) return horse.getOwnerUUID();
        return null;
    }
    public static Entity defaultSummoner(Entity entity) {
        Entity owner = null;
        if (entity instanceof net.minecraft.entity.monster.VexEntity vex) owner = vex.getOwner();
        else if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity projectile) owner = projectile.getOwner();
        else if (entity instanceof net.minecraft.entity.projectile.EvokerFangsEntity fangs) owner = fangs.getOwner();
        else if (entity instanceof net.minecraft.entity.AreaEffectCloudEntity cloud) owner = cloud.getOwner();
        else if (entity instanceof net.minecraft.entity.item.TNTEntity tnt) owner = tnt.getOwner();
        if (owner != null) return owner;
        UUID id = ownerUuid(entity);
        return id != null && entity.level instanceof ServerWorld ? ((ServerWorld)entity.level).getEntity(id) : null;
    }
    private static Entity summoner(Entity entity) {
        return entity instanceof MobEntity mob ? SoulAdapters.get(mob).summoner(mob) : defaultSummoner(entity);
    }
    /** May be retried after spawn for mods that assign a creator after world insertion. */
    public static boolean tryInherit(MobEntity mob) {
        if (mob.level.isClientSide || CompanionBrain.isSoul(mob) || mob.removed) return false;
        return attach(mob, summoner(mob));
    }
    /** Fresh construction provenance is a fallback only; an explicitly foreign creator is authoritative. */
    public static boolean inheritOnJoin(MobEntity mob, boolean loadedFromDisk) {
        Entity constructedBy = SoulSpawnContext.consume(mob);
        if (mob.level.isClientSide || CompanionBrain.isSoul(mob) || mob.removed) return false;
        Entity creator = summoner(mob);
        if (creator != null) return attach(mob, creator);
        return !loadedFromDisk && attach(mob, constructedBy);
    }
    /** Server-only integration hook for summons that do not expose their creator. */
    public static boolean attach(MobEntity child, Entity creator) {
        if (creator == null || !(child.level instanceof ServerWorld level)
                || child.removed || CompanionBrain.isSoul(child)) return false;
        Bond bond = findBond(creator, level, child.getUUID());
        if (bond == null) return false;
        var tag = child.getPersistentData();
        tag.putUUID(CompanionBrain.OWNER, bond.owner());
        tag.putUUID(CompanionBrain.ENTRY, bond.entry());
        tag.putUUID(ROOT, bond.root());
        if (CompanionBrain.activeEntry(child) == null) { child.remove(); return false; }
        SoulNames.update(child);
        if (child.isAddedToWorld()) {
            SoulNetwork.ghost(child);
        }
        return true;
    }
    private static Bond findBond(Entity creator, ServerWorld level, UUID child) {
        Set<UUID> seen = new HashSet<>();
        seen.add(child);
        for (int depth = 0; creator != null && depth < MAX_CREATOR_DEPTH; depth++) {
            if (creator.level != level || !seen.add(creator.getUUID())) return null;
            if (creator instanceof MobEntity mob && CompanionBrain.isSoul(mob)) {
                if (!mob.getPersistentData().hasUUID(CompanionBrain.ENTRY)) return null;
                UUID root = isMinion(mob) ? mob.getPersistentData().getUUID(ROOT) : mob.getUUID();
                return new Bond(CompanionBrain.owner(mob), mob.getPersistentData().getUUID(CompanionBrain.ENTRY), root);
            }
            creator = summoner(creator);
        }
        return null;
    }
    /** Only call before AI iteration (join or living-tick), never from a target/damage callback. */
    public static void configure(MobEntity minion) {
        if (CONFIGURED.contains(minion)) return;
        minion.setTarget(null);
        minion.setCanPickUpLoot(false);
        SoulAdapters.get(minion).configureMinion(minion);
        SoulBossBars.suppress(minion);
        CONFIGURED.add(minion);
    }
    /** Before native AI. An unloaded root pauses the child instead of letting it find a different master. */
    public static boolean tick(MobEntity minion) {
        SoulEntry entry = CompanionBrain.activeEntry(minion);
        if (entry == null || !minion.isAlive()) { minion.remove(); return false; }
        Entity entity = ((ServerWorld)minion.level).getEntity(minion.getPersistentData().getUUID(ROOT));
        if (entity == null) {
            if (minion.getTarget() != null) minion.setTarget(null);
            return false;
        }
        if (!(entity instanceof MobEntity root) || !root.isAlive() || isMinion(root) || CompanionBrain.activeEntry(root) != entry) {
            dismissOne(minion); return false;
        }
        configure(minion);
        if (minion.tickCount % 20 == 0) SoulNames.update(minion);
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
        var minions = new ArrayList<MobEntity>();
        for (ServerWorld level : server.getAllLevels()) for (Entity entity : level.getAllEntities()) {
            if (entity instanceof MobEntity mob && isMinion(mob) && root.equals(mob.getPersistentData().getUUID(ROOT)))
                minions.add(mob);
        }
        // Removal changes the level's entity collection, so iterate a detached list.
        minions.forEach(SoulMinions::dismissOne);
    }
    private static void dismissOne(MobEntity mob) {
        SoulNetwork.dissolve(mob);
        mob.remove();
    }
    private SoulMinions() {}
}
