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
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/** Operator-only scroll grants from the live registry, including installed mods. */
public final class SoulScrollCommand {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id ->
        new TranslationTextComponent("command.soulcatcherplus.scroll.unknown", id));
    private static final DynamicCommandExceptionType UNSUPPORTED = new DynamicCommandExceptionType(id ->
        new TranslationTextComponent("command.soulcatcherplus.scroll.unsupported", id));
    private static final DynamicCommandExceptionType CREATE_FAILED = new DynamicCommandExceptionType(id ->
        new TranslationTextComponent("command.soulcatcherplus.scroll.create_failed", id));
    private static final SimpleCommandExceptionType USAGE = new SimpleCommandExceptionType(
        new TranslationTextComponent("command.soulcatcherplus.scroll.usage"));
    private static final SimpleCommandExceptionType FULL = new SimpleCommandExceptionType(
        new TranslationTextComponent("command.soulcatcherplus.scroll.full"));

    public static LiteralArgumentBuilder<CommandSource> branch() {
        // A built-in greedy argument accepts both namespace:mob and namespace@mob.
        // Parse the optional player with vanilla's selector argument; no custom wire type is needed.
        return Commands.literal("scroll").requires(source -> source.hasPermission(2))
            .then(Commands.argument("soul", StringArgumentType.greedyString())
                .suggests(SoulScrollCommand::suggest).executes(SoulScrollCommand::give));
    }

    private static String readType(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getRemaining());
        String type = readType(reader);
        if (reader.canRead()) {
            reader.skipWhitespace();
            return EntityArgument.player().listSuggestions(context, builder.createOffset(builder.getStart() + reader.getCursor()));
        }
        if (type.indexOf('@') >= 0) return ISuggestionProvider.suggest(ForgeRegistries.ENTITIES.getKeys().stream()
            .map(id -> id.toString().replace(':', '@')), builder);
        return ISuggestionProvider.suggestResource(ForgeRegistries.ENTITIES.getKeys(), builder);
    }

    private static int give(CommandContext<CommandSource> context) throws CommandSyntaxException {
        StringReader reader = new StringReader(StringArgumentType.getString(context, "soul"));
        String requested = readType(reader);
        ResourceLocation id = ResourceLocation.tryParse(requested.replace('@', ':'));
        if (id == null || !ForgeRegistries.ENTITIES.containsKey(id)) throw UNKNOWN.create(requested);
        reader.skipWhitespace();
        ServerPlayerEntity player = reader.canRead() ? EntityArgument.player().parse(reader).findSinglePlayer(context.getSource())
            : context.getSource().getPlayerOrException();
        reader.skipWhitespace();
        if (reader.canRead()) throw USAGE.createWithContext(reader);
        EntityType<?> type = ForgeRegistries.ENTITIES.getValue(id);
        ItemStack scroll = create(type, player, id);
        // Simulate and insert through the inventory wrapper: creative add() can silently delete overflow.
        PlayerMainInvWrapper inventory = new PlayerMainInvWrapper(player.inventory);
        if (!ItemHandlerHelper.insertItemStacked(inventory, scroll, true).isEmpty()) throw FULL.create();
        if (!ItemHandlerHelper.insertItemStacked(inventory, scroll, false).isEmpty()) throw FULL.create();
        player.inventoryMenu.broadcastChanges();
        context.getSource().sendSuccess(new TranslationTextComponent("command.soulcatcherplus.scroll.given",
            type.getDescription(), player.getDisplayName()), true);
        return 1;
    }

    private static ItemStack create(EntityType<?> type, ServerPlayerEntity player, ResourceLocation id) throws CommandSyntaxException {
        Entity sample = null;
        try {
            sample = type.create(player.getLevel());
            if (!(sample instanceof MobEntity mob)) throw UNSUPPORTED.create(id);
            return SoulScroll.forType(type, mob.getMaxHealth());
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to create soul scroll for {}", id, error);
            throw CREATE_FAILED.create(id);
        } finally {
            // A sample is never spawned, registered as a companion or added to the bestiary.
            if (sample != null) sample.remove();
        }
    }

    private SoulScrollCommand() {}
}
