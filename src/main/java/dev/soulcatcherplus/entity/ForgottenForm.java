// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.data.PlayerSouls;
import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.data.SoulVariants;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;

/** Alternate appearances of the existing companions, stored as ordinary collection forms. */
public enum ForgottenForm {
    LONG_TAILED_TIT("soulcatcherplus:soul_raven", "long_tailed_tit", Items.WHEAT_SEEDS),
    MIKU("soulcatcherplus:soul_shaman", "miku", Items.NOTE_BLOCK);

    private static final String VARIANT = "Variant";
    private final String entityType;
    private final String id;
    private final Item trigger;

    ForgottenForm(String entityType, String id, Item trigger) {
        this.entityType = entityType;
        this.id = id;
        this.trigger = trigger;
    }

    public String nameKey() { return "entity.soulcatcherplus.form." + id; }

    public static ForgottenForm from(String entityType, CompoundTag snapshot) {
        if (LONG_TAILED_TIT.entityType.equals(entityType) && LONG_TAILED_TIT.isSavedIn(snapshot)) return LONG_TAILED_TIT;
        if (MIKU.entityType.equals(entityType) && MIKU.isSavedIn(snapshot)) return MIKU;
        return null;
    }

    boolean isSavedIn(CompoundTag snapshot) { return id.equals(snapshot.getString(VARIANT)); }

    void save(CompoundTag snapshot) { snapshot.putString(VARIANT, id); }

    InteractionResult interact(Mob mob, Player player, InteractionHand hand) {
        if (!mob.isAlive() || player.isSpectator() || player.getItemInHand(hand).getItem() != trigger)
            return InteractionResult.PASS;
        if (mob.level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer owner) || !CompanionBrain.isSoul(mob)
                || !owner.getUUID().equals(CompanionBrain.owner(mob))) return InteractionResult.PASS;
        SoulEntry active = CompanionBrain.activeEntry(mob);
        if (active == null || !mob.getUUID().equals(active.activeEntity)) return InteractionResult.PASS;

        CompoundTag snapshot = active.snapshot();
        save(snapshot);
        snapshot.remove("CustomName");
        snapshot.remove("CustomNameVisible");
        SoulEntry form = new SoulEntry(UUID.randomUUID(), active.entityType, nameKey(), snapshot, active.maxHealth);
        PlayerSouls collection = SoulService.data(owner);
        if (collection.entries.values().stream().anyMatch(entry -> SoulVariants.same(entry, form))) {
            SoulService.message(owner, "form_already_known", Component.translatable(nameKey()));
        } else if (collection.entries.size() >= PlayerSouls.MAX_ENTRIES) {
            SoulService.message(owner, "collection_full");
        } else {
            collection.entries.put(form.id, form);
            collection.selected = form.id;
            SoulService.changed(owner);
            SoulService.message(owner, "form_unlocked", Component.translatable(nameKey()));
        }
        return InteractionResult.CONSUME;
    }
}
