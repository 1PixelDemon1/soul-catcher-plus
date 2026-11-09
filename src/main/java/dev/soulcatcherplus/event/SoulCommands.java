// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.SoulCages;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Development/admin resource grant; ordinary gameplay earns energy from victories. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("soulcatcher")
            .then(Commands.literal("energy").requires(source -> source.hasPermission(2))
                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000000))
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        SoulCages.Active cage = SoulCages.active(player);
                        if (!cage.present()) {
                            context.getSource().sendFailure(net.minecraft.network.chat.Component.translatable("message.soulcatcherplus.cage_needed"));
                            return 0;
                        }
                        int stored = SoulCages.set(player, IntegerArgumentType.getInteger(context, "amount"));
                        context.getSource().sendSuccess( net.minecraft.network.chat.Component.translatable("command.soulcatcherplus.energy",
                            SoulCages.location(cage.slot()), stored, cage.capacity()), false);
                        return 1;
                    }))));
    }
}
