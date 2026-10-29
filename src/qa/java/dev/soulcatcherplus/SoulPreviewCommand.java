// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.item.SoulCage;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.monster.SkeletonEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Compiled only with -PgameTests. Populates a local QA player's collection. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class SoulPreviewCommand {
    private static final int TYPES_PER_TICK = 8;
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    // Server-thread-only jobs, removed on completion, logout or server stop.
    private static final Map<MinecraftServer, Map<UUID, FillJob>> FILLS = new IdentityHashMap<>();

    private enum Result { ADDED, KNOWN, SKIPPED, FAILED, FULL }
    private static final class Counts {
        int added, known, skipped, failed;
        boolean full;
        void record(Result result) {
            switch (result) {
                case ADDED -> added++;
                case KNOWN -> known++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
                case FULL -> full = true;
            }
        }
    }
    private static final class FillJob {
        final List<EntityType<?>> types = ForgeRegistries.ENTITIES.getValues().stream()
            .sorted(Comparator.comparing(type -> ForgeRegistries.ENTITIES.getKey(type).toString())).collect(com.google.common.collect.ImmutableList.toImmutableList());
        final Counts counts = new Counts();
        int next;
        boolean advance(ServerPlayerEntity player) {
            for (int i = 0; i < TYPES_PER_TICK && next < types.size(); i++) {
                if (SoulService.data(player).entries.size() >= PlayerSouls.MAX_ENTRIES) {
                    counts.full = true;
                    break;
                }
                counts.record(sample(player, types.get(next++), mob -> {}));
                if (counts.full) break;
            }
            return counts.full || next == types.size();
        }
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("soulcatcherqa").requires(s -> s.hasPermission(2))
            .executes(c -> populate(c.getSource(), false))
            .then(Commands.literal("--all").executes(c -> populate(c.getSource(), true))));
    }
    private static int populate(CommandSource source, boolean all) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        Map<UUID, FillJob> jobs = FILLS.get(player.server);
        if (jobs != null && jobs.containsKey(player.getUUID())) {
            source.sendFailure(new net.minecraft.util.text.TranslationTextComponent("command.soulcatcherplus.qa.running"));
            return 0;
        }
        replaceItem(player, player.inventory.selected, new ItemStack(SoulCatcherPlus.NETHERITE_SCYTHE.get()));
        for (var item : com.google.common.collect.ImmutableList.of(SoulCatcherPlus.IRON_SCYTHE, SoulCatcherPlus.DIAMOND_SCYTHE)) {
            ItemStack stack = new ItemStack(item.get());
            if (!player.inventory.contains(stack)) player.inventory.placeItemBackInInventory(player.level, stack);
        }
        prepareCage(player);
        SoulService.changed(player);
        if (all) {
            FillJob job = new FillJob();
            FILLS.computeIfAbsent(player.server, server -> new LinkedHashMap<>()).put(player.getUUID(), job);
            source.sendSuccess(new net.minecraft.util.text.TranslationTextComponent("command.soulcatcherplus.qa.started", job.types.size()), false);
        } else {
            Counts counts = new Counts();
            for (EntityType<?> type : com.google.common.collect.ImmutableList.of(EntityType.ZOMBIE, EntityType.SKELETON))
                counts.record(sample(player, type, mob -> {}));
            for (DyeColor color : com.google.common.collect.ImmutableList.of(DyeColor.CYAN, DyeColor.PURPLE))
                counts.record(sample(player, EntityType.SHEEP, mob -> { if (mob instanceof SheepEntity sheep) sheep.setColor(color); }));
            if (net.minecraftforge.fml.ModList.get().isLoaded("mowziesmobs"))
                counts.record(sample(player, ForgeRegistries.ENTITIES.getValue(
                    new ResourceLocation("mowziesmobs", "ferrous_wroughtnaut")), mob -> {}));
            finish(player, counts, 0);
        }
        return 1;
    }
    private static void prepareCage(ServerPlayerEntity player) {
        int slot = player.getOffhandItem().getItem() instanceof SoulCage ? 40 : -1;
        for (int i = 0; slot < 0 && i < 9; i++) {
            ItemStack stack = player.inventory.getItem(i);
            if (stack.isEmpty() || stack.getItem() instanceof SoulCage) { slot = i; break; }
        }
        if (slot < 0) slot = (player.inventory.selected + 1) % 9;
        if (player.inventory.getItem(slot).getItem() != SoulCatcherPlus.SUPREME_CAGE.get())
            replaceItem(player, slot, new ItemStack(SoulCatcherPlus.SUPREME_CAGE.get()));
        SoulCages.set(player, 1000);
    }
    private static void replaceItem(ServerPlayerEntity player, int slot, ItemStack replacement) {
        ItemStack previous = player.inventory.getItem(slot);
        player.inventory.setItem(slot, replacement);
        if (!previous.isEmpty()) player.inventory.placeItemBackInInventory(player.level, previous);
        player.inventory.setChanged();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
        Map<UUID, FillJob> jobs = FILLS.get(server);
        if (jobs == null) return;
        var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            ServerPlayerEntity player = server.getPlayerList().getPlayer(pending.getKey());
            if (player == null) { iterator.remove(); continue; }
            FillJob job = pending.getValue();
            if (job.advance(player)) {
                iterator.remove();
                finish(player, job.counts, job.types.size() - job.next);
            }
        }
        if (jobs.isEmpty()) FILLS.remove(server);
    }
    @SubscribeEvent public static void stopped(FMLServerStoppedEvent event) { FILLS.remove(event.getServer()); }

    private static Result sample(ServerPlayerEntity player, EntityType<?> type, Consumer<MobEntity> configure) {
        if (type == null) return Result.SKIPPED;
        Entity sample = null;
        try {
            sample = type.create(player.getLevel());
            if (!(sample instanceof MobEntity mob)) return Result.SKIPPED;
            if (mob instanceof SkeletonEntity) mob.setItemSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.BOW));
            configure.accept(mob);
            var adapter = SoulAdapters.get(mob);
            if (!SoulProfiles.get(mob).enabled() || !adapter.canCapture(mob, player)) return Result.SKIPPED;
            var entry = new SoulEntry(UUID.randomUUID(), ForgeRegistries.ENTITIES.getKey(mob.getType()).toString(),
                mob.getName().getString(), SoulSnapshots.sanitize(adapter.capture(mob)), mob.getMaxHealth());
            var data = SoulService.data(player);
            if (data.entries.values().stream().anyMatch(existing -> SoulVariants.same(existing, entry))) return Result.KNOWN;
            if (data.entries.size() >= PlayerSouls.MAX_ENTRIES) return Result.FULL;
            data.entries.put(entry.id, entry);
            if (data.selected == null) data.selected = entry.id;
            SoulService.changed(player);
            return Result.ADDED;
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Unable to create QA soul sample for {}", ForgeRegistries.ENTITIES.getKey(type), error);
            return Result.FAILED;
        } finally {
            if (sample != null) {
                try { sample.remove(); }
                catch (RuntimeException | LinkageError error) {
                    LOGGER.warn("Unable to discard QA sample for {}", ForgeRegistries.ENTITIES.getKey(type), error);
                }
            }
        }
    }
    private static void finish(ServerPlayerEntity player, Counts counts, int remaining) {
        player.sendMessage(new net.minecraft.util.text.TranslationTextComponent("command.soulcatcherplus.qa.finished",
            counts.added, counts.known, counts.skipped, counts.failed), player.getUUID());
        if (counts.full) player.sendMessage(new net.minecraft.util.text.TranslationTextComponent("command.soulcatcherplus.qa.full", PlayerSouls.MAX_ENTRIES, remaining), player.getUUID());
        SoulService.open(player);
    }
}
