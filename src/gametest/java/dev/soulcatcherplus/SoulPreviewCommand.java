// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import dev.soulcatcherplus.companion.SoulService;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Compiled only with -PgameTests. Populates a local QA player's collection. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulPreviewCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("soulcatcherqa").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SoulCatcherPlus.SCYTHE.get()));
            for (EntityType<?> type : java.util.List.of(EntityType.ZOMBIE, EntityType.SKELETON)) {
                Mob mob = (Mob) type.create(player.serverLevel());
                if (mob instanceof net.minecraft.world.entity.monster.Skeleton)
                    mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                add(player, mob, 25, 1);
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("mowziesmobs")) {
                EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                    new net.minecraft.resources.ResourceLocation("mowziesmobs", "ferrous_wroughtnaut"));
                add(player, (Mob) type.create(player.serverLevel()), 200, 4);
            }
            SoulService.data(player).energy = 1000; SoulService.changed(player); SoulService.open(player);
            return 1;
        }));
    }
    private static void add(ServerPlayer player, Mob mob, int cost, int slots) {
        var data = SoulService.data(player);
        String type = mob.getType().builtInRegistryHolder().key().location().toString();
        if (data.entries.values().stream().anyMatch(e -> e.entityType.equals(type))) return;
        var entry = new dev.soulcatcherplus.data.SoulEntry(java.util.UUID.randomUUID(), type,
            mob.getName().getString(), dev.soulcatcherplus.api.SoulAdapters.get(mob).capture(mob), cost, slots);
        data.entries.put(entry.id, entry); data.selected = entry.id;
    }
}
