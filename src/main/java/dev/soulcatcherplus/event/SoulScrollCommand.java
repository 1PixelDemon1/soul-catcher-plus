// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.event;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.soulcatcherplus.item.SoulScroll;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/** Operator-only scroll grants from the live registry, including installed mods. */
public final class SoulScrollCommand {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id ->
        Component.translatable("command.soulcatcherplus.scroll.unknown", id));
    private static final DynamicCommandExceptionType UNSUPPORTED = new DynamicCommandExceptionType(id ->
        Component.translatable("command.soulcatcherplus.scroll.unsupported", id));
    private static final DynamicCommandExceptionType CREATE_FAILED = new DynamicCommandExceptionType(id ->
        Component.translatable("command.soulcatcherplus.scroll.create_failed", id));
    private static final SimpleCommandExceptionType USAGE = new SimpleCommandExceptionType(
        Component.translatable("command.soulcatcherplus.scroll.usage"));
    private static final SimpleCommandExceptionType FULL = new SimpleCommandExceptionType(
        Component.translatable("command.soulcatcherplus.scroll.full"));

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        // A built-in greedy argument accepts both namespace:mob and namespace@mob.
        // Parse the optional player with vanilla's selector argument; no custom wire type is needed.
        return Commands.literal("scroll").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("soul", StringArgumentType.greedyString())
                .suggests(SoulScrollCommand::suggest).executes(SoulScrollCommand::give));
    }

    private static String readType(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getRemaining());
        String type = readType(reader);
        if (reader.canRead()) {
            reader.skipWhitespace();
            return EntityArgument.player().listSuggestions(context, builder.createOffset(builder.getStart() + reader.getCursor()));
        }
        if (type.indexOf('@') >= 0) return SharedSuggestionProvider.suggest(ForgeRegistries.ENTITY_TYPES.getKeys().stream()
            .map(id -> id.toString().replace(':', '@')), builder);
        return SharedSuggestionProvider.suggestResource(ForgeRegistries.ENTITY_TYPES.getKeys(), builder);
    }

    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        StringReader reader = new StringReader(StringArgumentType.getString(context, "soul"));
        String requested = readType(reader);
        Identifier id = Identifier.tryParse(requested.replace('@', ':'));
        if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) throw UNKNOWN.create(requested);
        reader.skipWhitespace();
        ServerPlayer player = reader.canRead() ? EntityArgument.player().parse(reader).findSinglePlayer(context.getSource())
            : context.getSource().getPlayerOrException();
        reader.skipWhitespace();
        if (reader.canRead()) throw USAGE.createWithContext(reader);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        ItemStack scroll = create(type, player, id);
        // Simulate and insert through the inventory wrapper: creative add() can silently delete overflow.
        PlayerMainInvWrapper inventory = new PlayerMainInvWrapper(player.getInventory());
        if (!ItemHandlerHelper.insertItemStacked(inventory, scroll, true).isEmpty()) throw FULL.create();
        if (!ItemHandlerHelper.insertItemStacked(inventory, scroll, false).isEmpty()) throw FULL.create();
        player.inventoryMenu.broadcastChanges();
        context.getSource().sendSuccess(() -> Component.translatable("command.soulcatcherplus.scroll.given",
            type.getDescription(), player.getDisplayName()), true);
        return 1;
    }

    private static ItemStack create(EntityType<?> type, ServerPlayer player, Identifier id) throws CommandSyntaxException {
        Entity sample = null;
        try {
            sample = type.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (!(sample instanceof Mob mob)) throw UNSUPPORTED.create(id);
            return SoulScroll.forType(type, mob.getMaxHealth());
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to create soul scroll for {}", id, error);
            throw CREATE_FAILED.create(id);
        } finally {
            // A sample is never spawned, registered as a companion or added to the bestiary.
            if (sample != null) sample.discard();
        }
    }

    private SoulScrollCommand() {}
}
