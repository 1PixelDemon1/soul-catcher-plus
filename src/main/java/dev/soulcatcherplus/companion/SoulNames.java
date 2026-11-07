// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/** Ordinary synced entity names expose ownership to other mods without client integrations. */
public final class SoulNames {
    private static final String BASE = "scp_name_base", OWNER_NAME = "scp_name_owner";
    // Server-thread-only; cached components never retain the entity or its owner.
    private static final Map<MobEntity, NameState> NAMES = new WeakHashMap<>();

    @com.github.bsideup.jabel.Desugar
    private record NameState(String ownerName, ITextComponent base, ITextComponent applied) {}

    public static void update(MobEntity mob) {
        if (mob.level.isClientSide || !CompanionBrain.isSoul(mob) || mob.getServer() == null) return;
        ServerPlayerEntity owner = mob.getServer().getPlayerList().getPlayer(CompanionBrain.owner(mob));
        if (owner != null) update(mob, owner);
    }

    public static void update(MobEntity mob, ServerPlayerEntity owner) {
        if (mob.level.isClientSide || !CompanionBrain.isSoul(mob)
                || !owner.getUUID().equals(CompanionBrain.owner(mob))) return;
        String nickname = owner.getGameProfile().getName();
        ITextComponent actual = mob.getCustomName();
        NameState previous = NAMES.get(mob);
        if (previous != null && previous.ownerName().equals(nickname) && Objects.equals(previous.applied(), actual)) return;

        ITextComponent base = previous != null && Objects.equals(previous.applied(), actual)
            ? previous.base() : originalName(mob);
        ITextComponent named = named(nickname, base);
        mob.getPersistentData().putString(BASE, ITextComponent.Serializer.toJson(base));
        mob.getPersistentData().putString(OWNER_NAME, nickname);
        if (!named.equals(actual)) mob.setCustomName(named);
        NAMES.put(mob, new NameState(nickname, base, named));
    }

    private static ITextComponent originalName(MobEntity mob) {
        var tag = mob.getPersistentData();
        if (tag.contains(BASE, net.minecraftforge.common.util.Constants.NBT.TAG_STRING)) {
            try {
                ITextComponent base = ITextComponent.Serializer.fromJson(tag.getString(BASE));
                if (base != null && named(tag.getString(OWNER_NAME), base).equals(mob.getCustomName())) return base;
            } catch (com.google.gson.JsonParseException invalid) {
                // A malformed saved component must not prevent a companion from loading.
                return mob.getType().getDescription().copy();
            }
        }
        // Preserve a captured custom name or a later rename instead of stacking prefixes.
        return mob.getName().copy();
    }

    private static ITextComponent named(String nickname, ITextComponent base) {
        return new StringTextComponent(nickname + "'s ").append(base.copy());
    }

    private SoulNames() {}
}
