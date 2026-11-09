// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Ordinary synced entity names expose ownership to other mods without client integrations. */
public final class SoulNames {
    private static final String BASE = "scp_name_base", OWNER_NAME = "scp_name_owner";
    // Server-thread-only; cached components never retain the entity or its owner.
    private static final Map<Mob, NameState> NAMES = new WeakHashMap<>();

    private record NameState(String ownerName, Component base, Component applied) {}

    public static void update(Mob mob) {
        if (mob.level().isClientSide() || !CompanionBrain.isSoul(mob) || mob.level().getServer() == null) return;
        ServerPlayer owner = mob.level().getServer().getPlayerList().getPlayer(CompanionBrain.owner(mob));
        if (owner != null) update(mob, owner);
    }

    public static void update(Mob mob, ServerPlayer owner) {
        if (mob.level().isClientSide() || !CompanionBrain.isSoul(mob)
                || !owner.getUUID().equals(CompanionBrain.owner(mob))) return;
        String nickname = owner.getGameProfile().name();
        Component actual = mob.getCustomName();
        NameState previous = NAMES.get(mob);
        if (previous != null && previous.ownerName().equals(nickname) && Objects.equals(previous.applied(), actual)) return;

        Component base = previous != null && Objects.equals(previous.applied(), actual)
            ? previous.base() : originalName(mob);
        Component named = named(nickname, base);
        mob.getPersistentData().putString(BASE, net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(mob.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),base).getOrThrow().toString());
        mob.getPersistentData().putString(OWNER_NAME, nickname);
        if (!named.equals(actual)) mob.setCustomName(named);
        NAMES.put(mob, new NameState(nickname, base, named));
    }

    private static Component originalName(Mob mob) {
        var tag = mob.getPersistentData();
        if (tag.contains(BASE)) {
            try {
                Component base = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(mob.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),com.google.gson.JsonParser.parseString(tag.getStringOr(BASE, ""))).getOrThrow();
                if (base != null && named(tag.getStringOr(OWNER_NAME, ""), base).equals(mob.getCustomName())) return base;
            } catch (RuntimeException invalid) {
                // A malformed saved component must not prevent a companion from loading.
                return mob.getType().getDescription().copy();
            }
        }
        // Preserve a captured custom name or a later rename instead of stacking prefixes.
        return mob.getName().copy();
    }

    private static Component named(String nickname, Component base) {
        return Component.literal(nickname + "'s ").append(base.copy());
    }

    private SoulNames() {}
}
