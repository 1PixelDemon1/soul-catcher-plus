// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.advancement;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.data.SoulBalance;
import dev.soulcatcherplus.companion.SoulService;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned milestones; join and item acquisition use vanilla JSON criteria. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulAdvancements {
    private static final Identifier FIRST_SOUL = id("first_soul");
    private static final Identifier BOSS_SOUL = id("boss_soul");
    private static final Identifier SOUL_MASTER = id("soul_master");
    // Current maximum: supreme scythe (4) and four pieces of Soul Slot armor (+4).
    private static final int MAX_SOUL_SLOTS = 8;

    /** Called only after an owned remnant has actually been collected, including a known form. */
    public static void captured(ServerPlayer player, double maxHealth) {
        award(player, FIRST_SOUL);
        if (SoulBalance.isBossSoul(maxHealth)) award(player, BOSS_SOUL);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) checkSlots(player);
    }

    @SubscribeEvent public static void equipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) checkSlots(player);
    }

    private static void checkSlots(ServerPlayer player) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(SOUL_MASTER);
        if (advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone()) return;
        if (SoulService.summonLimit(player) >= MAX_SOUL_SLOTS)
            player.getAdvancements().award(advancement, "complete");
    }

    private static void award(ServerPlayer player, Identifier id) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(id);
        if (advancement != null) player.getAdvancements().award(advancement, "complete");
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(SoulCatcherPlus.ID, path); }
    private SoulAdvancements() {}
}
