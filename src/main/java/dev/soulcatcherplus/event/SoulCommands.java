// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.SoulService;
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
                        SoulService.data(player).energy = IntegerArgumentType.getInteger(context, "amount");
                        SoulService.changed(player);
                        context.getSource().sendSuccess(() -> Component.literal("Soul energy: " + SoulService.data(player).energy), false);
                        return 1;
                    }))));
    }
}
