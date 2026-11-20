// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.data.PlayerSouls;
import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.data.SoulVariants;
import java.util.UUID;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.text.TranslationTextComponent;

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

    public static ForgottenForm from(String entityType, CompoundNBT snapshot) {
        if (LONG_TAILED_TIT.entityType.equals(entityType) && LONG_TAILED_TIT.isSavedIn(snapshot)) return LONG_TAILED_TIT;
        if (MIKU.entityType.equals(entityType) && MIKU.isSavedIn(snapshot)) return MIKU;
        return null;
    }

    boolean isSavedIn(CompoundNBT snapshot) { return id.equals(snapshot.getString(VARIANT)); }

    void save(CompoundNBT snapshot) { snapshot.putString(VARIANT, id); }

    ActionResultType interact(MobEntity mob, PlayerEntity player, Hand hand) {
        if (!mob.isAlive() || player.isSpectator() || player.getItemInHand(hand).getItem() != trigger)
            return ActionResultType.PASS;
        if (mob.level.isClientSide) return ActionResultType.SUCCESS;
        if (!(player instanceof ServerPlayerEntity owner) || !CompanionBrain.isSoul(mob)
                || !owner.getUUID().equals(CompanionBrain.owner(mob))) return ActionResultType.PASS;
        SoulEntry active = CompanionBrain.activeEntry(mob);
        if (active == null || !mob.getUUID().equals(active.activeEntity)) return ActionResultType.PASS;

        CompoundNBT snapshot = active.snapshot();
        save(snapshot);
        snapshot.remove("CustomName");
        snapshot.remove("CustomNameVisible");
        SoulEntry form = new SoulEntry(UUID.randomUUID(), active.entityType, nameKey(), snapshot, active.maxHealth);
        PlayerSouls collection = SoulService.data(owner);
        if (collection.entries.values().stream().anyMatch(entry -> SoulVariants.same(entry, form))) {
            SoulService.message(owner, "form_already_known", new TranslationTextComponent(nameKey()));
        } else if (collection.entries.size() >= PlayerSouls.MAX_ENTRIES) {
            SoulService.message(owner, "collection_full");
        } else {
            collection.entries.put(form.id, form);
            collection.selected = form.id;
            SoulService.changed(owner);
            SoulService.message(owner, "form_unlocked", new TranslationTextComponent(nameKey()));
        }
        return ActionResultType.CONSUME;
    }
}
